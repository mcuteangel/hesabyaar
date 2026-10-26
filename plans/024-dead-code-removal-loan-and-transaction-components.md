# Plan 024: Dead Code Removal: LoanManagementScreen, LoanViewModel, LoanItem, InstallmentItem, and TransactionItem

- **Issue:** #340
- **PR:** —
- **Created:** 2026-10-26
- **Author:** Claude Code
- **Status:** IN PROGRESS (Branch `refactor/issue-340-dead-code-removal`)

## 1. Problem Statement

Across `app/src/main/java/io/github/mojri/hesabyar/ui/`, approximately 1,260 lines of composable screens, components, and a ViewModel are completely unused in production. They are retained only by legacy tests and historical references.

This dead code introduces substantial technical debt:
1. **Misleading planning & audit noise:** Previous plans (such as plan 013) originally planned migration steps around `LoanManagementScreen` until issue #339 explicitly excluded them.
2. **Duplicate implementations:** `TransactionItem.kt`, `LoanItem.kt`, and `InstallmentItem.kt` are redundant variants alongside live components (`TransactionMiniItem.kt`, `InstallmentMiniItem.kt`, `InstallmentListItem`, `PersonCard`).
3. **Detekt baseline pollution:** 14 stale entries in `config/detekt/detekt-baseline.xml` exist solely to suppress issues in these unused files.
4. **Maintenance overhead:** Any refactoring across theme tokens, dialog policies, or UI helpers must artificially touch or exclude these dead files.

Issue #340 explicitly requires deciding between deletion and deprecation. Per repo recommendation and user direction, the decision is **complete deletion** after preserving reusable logic context and migrating test coverage.

### Files In Scope for Deletion

| File | Lines | Call sites in production (`app/src/main`) | Sole Consumers |
|---|---|---|---|
| `ui/screens/LoanManagementScreen.kt` | 871 | 0 (only own file) | `LoanManagementDialogTest.kt`, `LoanEditRejectionScreenTest.kt` |
| `ui/LoanViewModel.kt` | 96 | 0 (not injected in `MainActivity.kt`) | `LoanManagementScreen.kt`, `LoanViewModelTest.kt` |
| `ui/components/LoanItem.kt` | 63 | 0 | None (pure dead code) |
| `ui/components/InstallmentItem.kt` | 63 | 0 | None (pure dead code) |
| `ui/components/TransactionItem.kt` | 168 | 0 | `TransactionItemSignPlacementTest.kt` |

### Dead Tests In Scope for Removal / Migration

| Test File | Lines | Action | Rationale |
|---|---|---|---|
| `ui/screens/LoanManagementDialogTest.kt` | 85 | Remove | Tests `AddLoanDialog` and `EditLoanDialog` in dead `LoanManagementScreen.kt`. |
| `ui/screens/LoanEditRejectionScreenTest.kt` | 162 | Remove | Tests `LoanManagementScreen` rejection flow; `ManageLoanUseCase` chronology rules are already tested in unit tests. |
| `ui/screens/LoanEditAmountPreservationTest.kt` | 180 | Remove | Tested internal `submitLoanEdit` in dead `LoanManagementScreen.kt`; `LoanEditCalculatorTest` covers core math. |
| `ui/LoanViewModelTest.kt` | 88 | Remove | Tests dead `LoanViewModel`. Live loan logic is handled by `PersonViewModel` and `ManageLoanUseCaseTest`. |
| `ui/TransactionItemSignPlacementTest.kt` | 187 | Migrate | Retain vital RTL signed-amount bidi placement assertions by migrating to `TransactionMiniItem` (or live RTL signed-amount composable). |

### Dead String Resources In Scope

| Key in `strings.xml` | Persian Text | Usage |
|---|---|---|
| `loan_type_debtor` | من قرض دادم | Used only in `LoanManagementScreen.kt` and `LoanManagementDialogTest.kt` |
| `loan_type_creditor` | من قرض گرفتم | Used only in `LoanManagementScreen.kt` and `LoanManagementDialogTest.kt` |
| `loan_amount_too_large` | مبلغ بیش از حد بزرگ است | Used only in `LoanManagementScreen.kt` and `LoanManagementDialogTest.kt` |

*(Note: `loan_party_debtor`, `loan_party_creditor`, and `loan_party_unknown` are actively used in `LoanTypeLabels.kt` and must NOT be removed).*

## 2. Goals & Acceptance Criteria

- [ ] **Criterion 1 (Complete Deletion of Dead Production Files):**
  - Delete `app/src/main/java/io/github/mojri/hesabyar/ui/screens/LoanManagementScreen.kt` <!-- check-docs: ignore -->
  - Delete `app/src/main/java/io/github/mojri/hesabyar/ui/LoanViewModel.kt` <!-- check-docs: ignore -->
  - Delete `app/src/main/java/io/github/mojri/hesabyar/ui/components/LoanItem.kt` <!-- check-docs: ignore -->
  - Delete `app/src/main/java/io/github/mojri/hesabyar/ui/components/InstallmentItem.kt` <!-- check-docs: ignore -->
  - Delete `app/src/main/java/io/github/mojri/hesabyar/ui/components/TransactionItem.kt` <!-- check-docs: ignore -->
  - `rg 'LoanManagementScreen|LoanViewModel|LoanItem\b|InstallmentItem\b|TransactionItem\b' app/src/main` returns **zero** matches (excluding legitimate kdoc reference in `LoanEditCalculator.kt`).

- [ ] **Criterion 2 (Preservation of Domain & Business Logic Context):**
  - `LoanEditCalculator.kt` already encapsulates `submitLoanEdit` mathematical repayment preservation rules. Ensure comments accurately reflect history without broken file links.
  - Document any loan edit/repayment insights needed for issue #369 in issue #369 / execution report.
  - Verify `ManageLoanUseCase.kt` remains untouched and healthy for its live callers (`PersonViewModel`, `AnalyticsViewModel`, `SubmitManualTransactionUseCase`).

- [ ] **Criterion 3 (RTL Sign Placement Test Migration):**
  - Migrate `TransactionItemSignPlacementTest.kt` to target `TransactionMiniItem` (as `TransactionMiniItemSignPlacementTest.kt`), verifying that:
    - Minus sign renders to the left of amount in RTL layout without bidi reversal.
    - Plus sign renders to the left of amount in RTL layout.
    - Transfer transaction displays positive sign without expense styling.
    - Sign and amount nodes remain distinct semantics nodes.
  - All migrated assertions pass deterministically.

- [ ] **Criterion 4 (Dead Test Removal):**
  - Delete `LoanManagementDialogTest.kt`, `LoanEditRejectionScreenTest.kt`, and `LoanViewModelTest.kt`.
  - `./gradlew testDebugUnitTest --no-daemon` passes with 0 failures.

- [ ] **Criterion 5 (Resource and Detekt Baseline Hygiene):**
  - Remove unused string keys `loan_type_debtor`, `loan_type_creditor`, and `loan_amount_too_large` from `app/src/main/res/values/strings.xml`.
  - Remove 14 stale entries from `config/detekt/detekt-baseline.xml`.
  - `./gradlew ktlintCheck detekt --no-daemon` passes with 0 findings.

- [ ] **Criterion 6 (Documentation Sync):**
  - Update `docs/MODULE_MAP.md` to remove deleted paths and update line 74.
  - Annotate historical references in `plans/013-ui-ux-overhaul.md` with `<!-- check-docs: ignore -->` so `python scripts/check_docs.py` succeeds with zero errors.

## 3. Implementation Steps

### Phase 0: Pre-execution Drift Audit (Completed)
1. Run `git status` to ensure clean worktree. (Verified, branched `refactor/issue-340-dead-code-removal` from `origin/main`)
2. Verify zero production callers:
   ```bash
   git grep -E '\b(LoanManagementScreen|LoanViewModel|LoanItem|InstallmentItem|TransactionItem)\b' app/src/main
   ```
   Confirmed zero callers outside own definitions and `LoanEditCalculator.kt` KDoc.
3. Record existing test counts on `./gradlew testDebugUnitTest`. (Verified `TransactionItemSignPlacementTest` 4/4 passing)

### Phase 1: Test Migration (Preserving RTL Signed-Amount Assertions) (Completed)
1. Adapt `app/src/test/java/io/github/mojri/hesabyar/ui/TransactionItemSignPlacementTest.kt` <!-- check-docs: ignore --> to test `TransactionMiniItem` in `app/src/test/java/io/github/mojri/hesabyar/TransactionMiniItemSignPlacementTest.kt`.
2. Ensure it exercises:
   - Negative amount (expense) RTL sign placement.
   - Positive amount (income) RTL sign placement.
   - Transfer amount RTL rendering.
   - Semantics tree integrity (separate sign and amount nodes merged under card).
3. Run test class:
   ```bash
   ./gradlew testDebugUnitTest --tests "io.github.mojri.hesabyar.TransactionMiniItemSignPlacementTest"
   ```
   (Completed: all 4 tests passing)

### Phase 2: Dead Test Deletion (Completed)
1. Delete `app/src/test/java/io/github/mojri/hesabyar/ui/screens/LoanManagementDialogTest.kt`. <!-- check-docs: ignore -->
2. Delete `app/src/test/java/io/github/mojri/hesabyar/ui/screens/LoanEditRejectionScreenTest.kt`. <!-- check-docs: ignore -->
3. Delete `app/src/test/java/io/github/mojri/hesabyar/ui/LoanViewModelTest.kt`. <!-- check-docs: ignore -->
4. Delete original `app/src/test/java/io/github/mojri/hesabyar/ui/TransactionItemSignPlacementTest.kt` <!-- check-docs: ignore --> (superseded by Phase 1).
(Completed: all 4 files deleted)

### Phase 3: Dead Code Deletion in `app/src/main` (Completed)
1. Delete `app/src/main/java/io/github/mojri/hesabyar/ui/screens/LoanManagementScreen.kt`. <!-- check-docs: ignore -->
2. Delete `app/src/main/java/io/github/mojri/hesabyar/ui/LoanViewModel.kt`. <!-- check-docs: ignore -->
3. Delete `app/src/main/java/io/github/mojri/hesabyar/ui/components/LoanItem.kt`. <!-- check-docs: ignore -->
4. Delete `app/src/main/java/io/github/mojri/hesabyar/ui/components/InstallmentItem.kt`. <!-- check-docs: ignore -->
5. Delete `app/src/main/java/io/github/mojri/hesabyar/ui/components/TransactionItem.kt`. <!-- check-docs: ignore -->
6. Delete `app/src/test/java/io/github/mojri/hesabyar/ui/screens/LoanEditAmountPreservationTest.kt`. <!-- check-docs: ignore -->
7. Update comment in `app/src/main/java/io/github/mojri/hesabyar/domain/utils/LoanEditCalculator.kt:10` to avoid broken path reference.
(Completed: 5 dead production files removed, 1 dead test file removed, KDoc updated)

### Phase 4: Strings and Detekt Baseline Cleanup (Completed)
1. In `app/src/main/res/values/strings.xml`, remove:
   - `loan_type_debtor`
   - `loan_type_creditor`
   - `loan_amount_too_large`
2. In `config/detekt/detekt-baseline.xml`, remove the stale entries referencing the deleted files:
   - `FunctionNaming:InstallmentItem.kt...`
   - `FunctionNaming:LoanItem.kt...`
   - `FunctionNaming:LoanManagementScreen.kt...` (3 entries)
   - `LongMethod:LoanManagementScreen.kt...` (2 entries)
   - `WildcardImport:InstallmentItem.kt...`
   - `WildcardImport:LoanItem.kt...`
   - `WildcardImport:LoanManagementScreen.kt...` (5 entries)
   - `WildcardImport:LoanViewModel.kt...`
   - `WildcardImport:TransactionItem.kt...`
(Completed: 3 string keys removed, 16 detekt baseline entries removed; ktlintCheck & detekt green)

### Phase 5: Documentation Synchronization
1. In `docs/MODULE_MAP.md`:
   - Remove `LoanViewModel.kt` from line 21.
   - Remove `LoanManagementScreen.kt` from line 22.
   - Remove `TransactionItem.kt` and `LoanItem.kt` from line 23.
   - Update line 74 to reflect `DebtHubScreen.kt → PersonViewModel.kt → ManageLoanUseCase.kt`.
2. In `plans/013-ui-ux-overhaul.md`:
   - Ensure occurrences of deleted filenames carry `<!-- check-docs: ignore -->` so `check_docs.py` does not flag them as missing live code.
3. Run verification:
   ```bash
   python scripts/check_docs.py
   ```

### Phase 6: Final Verification
1. Run static checks and formatting:
   ```bash
   ./gradlew ktlintFormat ktlintCheck detekt --no-daemon
   ```
2. Run all unit tests:
   ```bash
   ./gradlew testDebugUnitTest --no-daemon
   ```
3. Run docs check:
   ```bash
   python scripts/check_docs.py
   ```

## 4. Evidence and Verification Plan

Execution report must include:
1. Exact file deletion list and `git status` output.
2. Raw output of `rg 'LoanManagementScreen|LoanViewModel|LoanItem\b|InstallmentItem\b|TransactionItem\b' app/src/main`.
3. Raw output of `./gradlew testDebugUnitTest --tests "io.github.mojri.hesabyar.TransactionMiniItemSignPlacementTest"`.
4. Raw output of `./gradlew ktlintCheck detekt --no-daemon`.
5. Raw output of `python scripts/check_docs.py`.
6. Aggregate test summary verifying overall test health.

## 5. Risks, Dependencies, and STOP Conditions

### Risks & Mitigations
- **Risk: Breaking live loan management:**
  - *Mitigation:* `ManageLoanUseCase` and `PersonViewModel` are live and separate. They will not be touched. `LoanEditCalculator` handles repayment calculation logic.
- **Risk: Bidi regression on transaction sign placement:**
  - *Mitigation:* `TransactionItemSignPlacementTest` is not discarded; it is migrated directly to `TransactionMiniItem` before deleting `TransactionItem.kt`.
- **Risk: Broken doc links in CI:**
  - *Mitigation:* All deleted paths in `docs/MODULE_MAP.md` and `plans/013-ui-ux-overhaul.md` are audited and checked with `python scripts/check_docs.py`.

### STOP Conditions
- Stop and escalate if any production caller of `LoanManagementScreen`, `LoanViewModel`, `LoanItem`, `InstallmentItem`, or `TransactionItem` is discovered during Phase 0 drift check.
- Stop and escalate if `PersonViewModel` or `ManageLoanUseCase` has uncommitted dependencies on `LoanViewModel`.
- Stop and escalate if `./gradlew testDebugUnitTest` shows regressions in unrelated test suites after test removals.
