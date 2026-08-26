> **Executor instructions**: Follow this plan phase by phase. Run every
> verification command and confirm the expected result before moving to the
> next step. If anything in the "STOP conditions" section occurs, stop and
> report — do not improvise. When done, update the status row for this plan
> in `plans/README.md`.
>
> **Drift check (run first)**: `git diff --stat f425bd0..HEAD -- app/src/main/java/io/github/mojri/hesabyar/data/HesabyarRepository.kt app/src/main/java/io/github/mojri/hesabyar/data/Entities.kt rust/hesabyar-core/src/models/mod.rs`
> If any in-scope file changed since this plan was written, compare the
> excerpts against the live code before proceeding; on a mismatch, treat it
> as a STOP condition.
>
> Supersedes the clamp-based approach of plan 001: overpayments are now
> rejected at the repository boundary instead of being clamped silently.

## Status

- Priority: P1
- Effort: XL
- Risk: HIGH — Room migration, backup schema round-trip, FFI signature changes, and financial KPI semantics all change.
- Depends on: none hard. Phase 2 and Phase 3 both need Phase 1; they are independent of each other.
- Category: feature + bug
- Planned at: commit `f425bd0`, branch `feature/person-loan-ledger`

## Overview

Redesign the personal loan section ("قرض و طلب شخصی") around a person ledger:

1. A `Person` entity replaces free-text person names as the source of identity.
2. Each loan record is either ledger-only (no account impact) or tracked and
   linked to a chosen account.
3. Transaction forms get a shared person picker with inline create.
4. Known loan bugs stay fixed (Phase 0 is already done on this branch).

## Resolved design decisions

### D1 — Initial-leg transaction vs pre-existing manual entry

`Transaction` has no link column for loans (only `installmentId`,
`Entities.kt`). Attach-to-existing matching would be fuzzy and fragile. The
loan form therefore offers an explicit tri-state when tracked mode is on:

| User choice | Behavior |
|---|---|
| Post initial leg now (default ON for new loans) | Create the initial INCOME/EXPENSE transaction |
| Already recorded manually | No initial leg; only future repayments post |
| Tracked off | Ledger-only record, never touches accounts |

Double counting is prevented by explicit user choice plus helper text under
the checkbox. No extra schema column is needed; the choice happens at create
time only.

### D2 — Loans & Debt category exclusion from KPI aggregates

Raw ledgers keep every transaction. Only aggregate KPIs exclude the category
resolved by key `"Loans"` (ids differ per install, so Kotlin resolves ids and
passes them down). Full consumer inventory:

Rust core (all currently sum without category filter):
- `dashboard.rs` compute_dashboard monthly income/expenses + savings rate,
  and per-account summaries with month-over-month deltas.
- `analytics.rs` monthly trend series (all-accounts and per-account paths).
- `advisory/budget.rs` offline budget advice totals + saving rate, forecast
  income/expense estimates, and `monthly_income_baseline` which feeds
  `calculate_debt_to_income_ratio` and `calculate_financial_health_score`.

Kotlin mirrors that must stay consistent:
- `GetDashboardDataUseCase.computeFallbackDashboardData` +
  `computeAccountSummaries`
- `GetAnalyticsUseCase` fallback path
- `BudgetAdvisor.kt` local totals, local debt-to-income, local health score
- `BudgetAdviceGenerator.kt` prompt summary (audit during implementation)

Kept untouched on purpose: `ExcelExporter` sheets and the transaction list —
they are cash-movement ledgers, not KPIs.

Implementation shape: add `excludedCategoryIds: List<i64>` parameters to the
affected FFI functions, one shared helper in Rust applied by all three
modules, mirrored by the Kotlin fallbacks. Signature changes require updating
`app/buildSrc/template/HesabyarCore.template.kt`, then running
`:app:generateAndFixBindings --rerun-tasks`.

### D3 — `loans.personName` is sync-on-rename

`renamePerson(personId, newName)` runs in one `withTransaction`: update
`Person.name`, then update `loans.personName` and `transactions.personName`
where `personId = ?`. This needs the additive `personId` column on
`transactions` too (Phase 1). Display rule: UI always reads the denormalized
`personName` cache on Loan/Transaction rows; never joins `Person` for display.
Legacy rows with `personId = NULL` stay stale after renames — documented
limitation.

### D4 — Person-name normalization before dedup (backfill and runtime)

Dedup key = normalize(name): trim; collapse internal whitespace; remove
zero-width characters; fold Arabic variants to Persian (`ي→ی`, `ك→ک`, `ة→ه`);
lowercase the Latin part. Store the first trimmed original as the display
name. The same util lives in `domain/utils` (Room migrations cannot call Rust;
this is data hygiene/mapping per ADR-001 exceptions) and is reused whenever a
person is created at runtime, so duplicates cannot reappear later.

### D5 — Backup compatibility (verified, not assumed)

The primary parse path is Rust serde (`BackupJsonParser.kt` prefers
`RustBridge.parseBackupJsonSync`; `lib.rs parse_backup_json`). Verified:
`BackupPayload` in `rust/hesabyar-core/src/models/mod.rs` marks every list
with `#[serde(default)]` and no struct uses `deny_unknown_fields`. Consequences:

- Old backup → new app: safe once `persons` has `#[serde(default)]` and new
  Loan fields have `#[serde(default, alias = ...)]`.
- New backup → old app: restore succeeds but person links and tracked flags
  are dropped silently. Accepted limitation; document in BACKUP_FORMAT.md.
- Critical: because Rust parsing is primary, every new Loan column must land
  in the Rust `Loan` struct AND `RustMappers` AND the Kotlin fallback parser
  in the same change, or even a same-version round trip loses data silently.
- Add both directions of round-trip tests (JSON without `persons`, JSON with
  `persons`) in Phase 1.

`BACKUP_SCHEMA_VERSION` stays unchanged: additions are backward-compatible.

## Phase 0 — Loan bug fixes — DONE on this branch

1. Edit dialog preserves repaid money: `paidSoFar = original − remaining`;
   `newRemaining = max(0, newOriginal − paid)`; recompute `isSettled`.
2. Overpayment is rejected with a message; repository returns false instead
   of clamping (`addPaymentToLoan`).
3. Delete confirmation dialog; payment history rows removed in the same
   `withTransaction` as the loan (`deletePaymentHistoryForLoan`).
4. Typo fix «جديد» → «جدید».

Evidence: `ktlintCheck detekt`, `compileDebugKotlin`, `testDebugUnitTest` all
BUILD SUCCESSFUL; 63 suites with zero failures; new tests
`addPaymentToLoanOverpaymentIsRejectedWithoutSideEffects` and
`deleteLoanRemovesItsPaymentHistoriesInSameTransaction` pass by name.
Detekt findings introduced here were resolved by extracting
`LoanRepaymentDialog` + `RepaymentFormState` (no suppressions, no baseline).

## Phase 1 — Person model (schema + CRUD)

1. New table `persons(id, name unique-indexed, phone?, notes?, createdAt,
   isArchived)` via additive migration (CREATE TABLE + ALTER TABLE ADD COLUMN).
2. Additive columns: `loans.personId`, `transactions.personId` (both nullable).
3. Migration backfill builds persons from normalized distinct
   `loans.personName` (D4) and stamps `personId`.
4. `PersonDao`, `ManagePersonUseCase`, `PersonViewModel` following
   `ManageCategoryUseCase` patterns.
5. `renamePerson` sync per D3.
6. Backup: `persons` array in payload; export/import mirrors loans; merge
   dedups by name like `mergeCategories`. Apply D5 everywhere.
7. Tests: migration backfill (duplicate spacing/Arabic-variant names collapse),
   rename sync, backup round-trips both directions.

## Phase 2 — Ledger-only vs tracked mode

1. Additive columns `loans.tracked: Boolean = false`,
   `loans.accountId: Long? = null`. Default false matches the primary user
   scenario (record exists, money movement predates the app or stays private).
2. Repayment honors the loan's flag: tracked → INCOME/EXPENSE on
   `accountId`; untracked → balance-only, no transaction.
3. Loan form gains the tracked checkbox + account picker (active accounts
   only) + D1 tri-state initial-leg control.
4. Dashboard debtor/creditor totals count both modes (they are real claims).
5. Implement D2 exclusion so repayment legs do not distort monthly income,
   expenses, savings rate, forecasts, DTI, and health score.
6. Tests: tracked/untracked repayment matrix, KPI exclusion parity between
   Rust and each Kotlin mirror, template/bindings regeneration check.

## Phase 3 — Persons ledger UI

1. `PersonsScreen`: one row per person with net position
   (my receivables − my debts), direction color, search.
2. Net computation is business logic: new Rust core function (for example
   `compute_person_balances`) per ADR-001, Kotlin fallback within allowed
   exceptions, bindings regenerated.
3. Person detail sheet/screen: combined timeline of loans + payment history,
   quick actions (new receivable/debt, settle fully).
4. DebtHub third tab becomes this view; dashboard DebtorCreditorCards links
   into it filtered by direction.
5. Reuse shared components only (`HesabyarCard`, `CurrencyFormatter`,
   `formatPersianDate`); no local copies.

## Phase 4 — Transaction form integration

1. Shared `PersonPicker` composable in `ui/components`: dropdown of existing
   persons + inline "+ new person" using the D4 normalizer.
2. Wire into `ManualTransactionFormSections` and Smart Assistant paths for
   `LOAN_DEBTOR` / `LOAN_CREDITOR`: person field, tracked checkbox, account
   picker, initial-leg control.
3. Extend `SubmitManualTransactionUseCase` to create Loan (+optional
   Transaction) atomically through one use case.
4. Connect the smart parser route («از رضا ۳ میلیون طلب دارم») to the same
   use case with person match-or-create.

## Phase 5 — Cleanup and verification

1. Move hardcoded strings of this section to `strings.xml`.
2. Audit M3 token usage in loan components (manual colors/alphas).
3. Full workflow: `ktlintFormat` → `ktlintCheck detekt` →
   `compileDebugKotlin` → `testDebugUnitTest` → `cargo test` +
   `testDebugUnitTestRust` (after Rust changes) → final `test --rerun-tasks`.

## Risks

- Migration backfill must run before any new write path relies on `personId`.
- FFI signature changes without template updates break
  `uniffiCheckApiChecksums` at load time; every Rust-tagged test fails first.
- Old-app restores of new backups lose person links (accepted, documented).

## STOP conditions

- Any destructive migration step (DROP/RECREATE of populated tables).
- Round-trip test losing loan fields through the Rust parser path.
- Detekt finding resolved by suppression or baseline instead of refactor.
