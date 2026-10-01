# Current Progress

> This file tracks the current working state for AI coding agents.
> It is not an architecture specification, changelog, or source of truth.
> Always verify important claims against the current repository and `AGENTS.md`.

## Active Task

plans/011 Phase 3 — Persons ledger UI.

## Goal

Redesign the personal loan ledger so loans attach to durable person records
and the debts hub shows a per-person net position.

## Completed

- **Task:** plans/011 Phase 0-2 (person records, tracked/untracked repayment, KPI exclusion)
- **Status:** Completed and merged to `main`
- **Completed Date:** before 2026-09-27
- **Deliverables:** person table + MIGRATION_7_8, `loans.tracked` / `loans.accountId`, D2 KPI exclusion parity, template and binding regeneration

- **Task:** Vibe Coding preparation
- **Status:** Completed with known pre-existing verification failure
- **Completed Date:** 2026-09-25
- **Deliverables:**
  - [x] `AGENTS.md` additions (Architecture Pattern, UI Constraints, Room Migration Checklist, Progress Tracking, Quick-Check Scripts)
  - [x] `rust/hesabyar-core/README.md` (UniFFI procedural macro architecture, canonical money rules, method lifecycle, quick checks)
  - [x] `scripts/check-rust.sh` (clippy + workspace tests)
  - [x] `scripts/check-android.sh` (ktlintCheck + detekt + testDebugUnitTest + lintDebug)
  - [x] `scripts/check-rust-bridge.sh` (testDebugUnitTestRust without `--rerun-tasks` per user decision)
  - [x] Prompt templates in `.github/prompts/` (`new-compose-screen.md`, `new-rust-method.md`, `bugfix-or-db-optimization.md`)
  - [x] Pull request template in `.github/pull_request_template.md`
  - [x] `progress.md` lifecycle documentation and baseline

## In Progress

plans/011 Phase 3 on branch `feature/person-loan-ledger` (not merged, not pushed).

| Item | File | State |
|---|---|---|
| Rust `compute_person_balances` + 5 unit tests | `rust/hesabyar-core/src/models/mod.rs:430` | done |
| Rust FFI wrapper | `rust/hesabyar-core/src/ffi/mod.rs` | done |
| UniFFI template line | `app/buildSrc/template/HesabyarCore.template.kt` | done |
| Bridge façade `RustBridgePersons` | `app/src/main/java/io/github/mojri/hesabyar/rust/RustBridgePersons.kt` | done |
| Kotlin fallback mirror | `app/src/main/java/io/github/mojri/hesabyar/domain/utils/PersonBalanceCalculator.kt` | done |
| Use case + Hilt provider | `domain/usecase/GetPersonBalancesUseCase.kt`, `di/UseCaseModule.kt` | done |
| ViewModel | `app/src/main/java/io/github/mojri/hesabyar/ui/PersonViewModel.kt` | done |
| Persons list screen | `app/src/main/java/io/github/mojri/hesabyar/ui/screens/PersonsScreen.kt` | done |
| Person detail sheet | `app/src/main/java/io/github/mojri/hesabyar/ui/screens/PersonDetailSheet.kt` | done |
| Debts hub third tab | `ui/screens/DebtSection.kt`, `ui/screens/DebtHubScreen.kt` | done |
| Dashboard direction-filtered links | `ui/screens/DashboardScreen.kt`, `ui/screens/dashboard/components/DebtorCreditorCards.kt`, `MainActivity.kt` | done |
| Kotlin/Rust parity test | `app/src/test/java/io/github/mojri/hesabyar/rust/PersonBalanceParityTest.kt` | done |

## Blocked

None.

## Findings

1. **Room Schema Documentation Drift:** `AppDatabase.kt` defines schema `version = 9` with `exportSchema = false`. However, `docs/DATABASE_SCHEMA.md` lists schema version 3, and `docs/MIGRATION_NOTES.md` documents migrations only through v3.
2. **Missing MigrationTestHelper Test Infrastructure:** While JVM unit tests exist for migrations (e.g. `AppDatabaseMigrationTest.kt`, `AppDatabaseMigration7to8Test.kt`, `AppDatabaseMigration8to9Test.kt`), AndroidX `MigrationTestHelper` test suite using exported Room JSON schemas is not currently present in `app/src/test`.
3. **UniFFI Implementation:** UniFFI uses procedural macros (`#[uniffi::export]`) and scaffolding (`uniffi::setup_scaffolding!()`). No `.udl` interface definition files exist.
4. **Android Lint Failure in Pre-existing Code:** `scripts/check-android.sh` fails on task `:app:lintDebug` with 4 errors:
   - 1 local machine issue: `local.properties:8` (`PropertyEscape` on unescaped Windows backslashes in `sdk.dir`).
   - 3 pre-existing Compose issues: `ManualTransactionDialog.kt:132, 138, 149` (`LocalContextGetResourceValueCall` from querying resources using `LocalContext.current`).
   Per task rules, application code was left unchanged.
5. **Gradle dependency resolution is unreliable on this machine.** `dl.google.com` frequently terminates the TLS handshake, so `:app:compileDebugNavigationResources` may fail to fetch `com.android.tools.build:aapt2:9.4.1-15978811`. Retrying the task downloads it. Use `--offline` once the cache is warm.

## Decisions

- Retained existing `rust/hesabyar-core/README.md` technical build and pre-commit hook instructions while adding UniFFI architecture, canonical money rules, and new method lifecycle.
- Kept all check scripts strictly read-only (`check-android.sh` does not run `ktlintFormat`).
- Removed `--rerun-tasks` from `scripts/check-rust-bridge.sh` per direct user instruction to avoid 11-14 min NDK rebuilds and preserve incremental Gradle task checking.
- Phase 3 drops `LoanManagementScreen` from the hub tabs. `DebtSection.LOANS` was removed; the `"LOANS"` and `"PERSONS"` deep links now both open `DebtSection.PERSONS`. `LoanManagementScreen` currently has no callers (it is retained only for possible future deep-link support), and loan actions are handled by the `PersonDetailSheet` quick actions. This is a deliberate scope decision from plans/011 Phase 3 item 4 ("DebtHub third tab becomes this view").
- `MainActivity` no longer passes `loanViewModel` to `DebtHubScreen`, because the parameter became unused.

## Verification

Run on 2026-09-30, branch `feature/person-loan-ledger`:

| Check | Command | Result |
|---|---|---|
| Rust unit tests | `cargo test --manifest-path rust/Cargo.toml` | PASS (495 passed; `test_compute_person_balances_*` 5/5 ok) |
| Kotlin style | `./gradlew --no-daemon ktlintFormat` | PASS |
| Static analysis | `./gradlew --no-daemon ktlintCheck detekt` | PASS (BUILD SUCCESSFUL, 0 findings) |
| Kotlin compile | `./gradlew --no-daemon compileDebugKotlin` | PASS (BUILD SUCCESSFUL) |
| Kotlin unit tests | `./gradlew --no-daemon testDebugUnitTest` | PASS (BUILD SUCCESSFUL, 964 tests, 0 failures; suites: `DebtHubTabBarTest` 5/5, `AddPersonLoanDialogTest` 6/6, `PersonsScreenTest` 11/11, `PersonDetailSheetTest` 8/8, `PersonViewModelTest` 13/13, `PersonRowSemanticsTest` 3/3). |
| Rust-bridge JVM tests | `./gradlew --no-daemon testDebugUnitTestRust` | PASS (BUILD SUCCESSFUL, 206 tests, 0 failures). `PersonBalanceParityTest` (6/6 pass). |

`config/detekt/detekt-baseline.xml` shrank: 8 `MainActivity.kt` entries removed after the `onCreate` refactor (no entries added).

## Next Steps

1. Merge plans/011 Phase 3 (`feature/person-loan-ledger`) after review.
2. plans/011 Phase 4 — shared `PersonPicker` in `ui/components` and transaction-form integration.
3. Address pre-existing `LocalContextGetResourceValueCall` lint errors in `ManualTransactionDialog.kt` in a dedicated task.
4. Align `docs/DATABASE_SCHEMA.md` and `docs/MIGRATION_NOTES.md` with database version 9.
5. Configure Room schema export and add `MigrationTestHelper` integration tests.

## Last Updated

2026-09-30

