# Account Management Refactor Checklist

> **Start:** 2026-07-31  
> **Last update:** 2026-07-31  
> **Branch:** `feature/multi-account-wallet`

---

## Phase Status

- [ ] **Phase 0:** Fix real bugs (no architecture change)
- [ ] **Phase 1:** Extract the domain/data layer (Dao, Repository, UseCase, Validator)
- [ ] **Phase 2:** State architecture (Event, UiState, ViewModel refactor)
- [ ] **Phase 3:** Extract the UI components
- [ ] **Phase 4:** Rewrite the screen as shell + dialogs
- [ ] **Phase 5:** UX improvements (Undo, Unarchive, progressive disclosure)
- [ ] **Phase 6:** Tests and accessibility

---

## Pending Decisions

> Resolve each decision **before the phase that depends on it**. The status of each decision is marked with ✅ (resolved) or ⏳ (pending).

### 1. Account name uniqueness
- **Status:** ⏳ pending
- **Question:** Are duplicate account names allowed, or does strict validation apply?
- **Options:**
  - a) Strict validation: reject insert/update when a duplicate name exists
  - b) Non-blocking warning: show a warning message, but complete the operation
  - c) No restriction: the current behavior
- **Depends on:** Phase 1 (`AccountValidator`)
- **Effect:** If option a or b is selected, `AccountValidator` must read `getAllAccounts()`

### 2. FK constraint for account deletion
- **Status:** ⏳ pending
- **Question:** For deletion of an account that has transactions: keep only the app-level check (`canDeleteAccount`), or add a real FK constraint (`onDelete=RESTRICT`)?
- **Options:**
  - a) App-level check only (the current behavior) — simpler, but backup/restore can break data integrity
  - b) FK constraint `onDelete=RESTRICT` — safer, but it needs a Room migration
- **Depends on:** Phase 1 (Room schema)
- **Effect:** If option b is selected, a Room migration is necessary, and `HesabyarRepository.replaceAllFromBackup` must respect the deletion order

### 3. Test tooling
- **Status:** ⏳ pending
- **Question:** Are Roborazzi and Detekt really installed/configured in the project?
- **Action:** Confirm before Phase 6 with a grep in `build.gradle.kts`:
  ```bash
  grep -rn "roborazzi\|detekt" app/build.gradle.kts build.gradle.kts
  ```
- **Depends on:** Phase 6

### 4. DEFAULT_ACCOUNT_COLOR unification
- **Status:** ⏳ pending
- **Question:** The three `DEFAULT_ACCOUNT_COLOR` values must be the same — confirm the exact value:
  - `FinancialColors.kt:37` → `0xFF4CAF50L`
  - `ManageBackupUseCase.kt:30` → `0xFF4CAF50L`
  - Rust `default_color()` → `0xFF4CAF50`
- **Confirmation action:**
  ```bash
  grep -n "DEFAULT_ACCOUNT_COLOR\|default_color" app/src/main/java/.../FinancialColors.kt app/src/main/java/.../ManageBackupUseCase.kt rust/hesabyar-core/src/models/mod.rs
  ```
- **Depends on:** Phase 0 or Phase 1

---

## Project-Specific Risks

> Check these items **in every phase** — not only in the phase that they relate to.

### R1. Rust vs Kotlin fallback alignment
- [ ] Compare the Rust path and the Kotlin fallback wherever the balance (initial + calculated) is shown
- [ ] In particular, the archived-account filter must behave the same in both paths
- [ ] **Confirmation test:** `./gradlew test --tests "io.github.mojri.hesabyar.GetDashboardDataUseCaseTest.rustAndKotlinFallbackProduceSameResultWithFixedNow" --no-daemon`
- [ ] **New rule:** Apply this checklist only to existing fallback code. Never add new logic or calculations on the Kotlin fallback side — add them only in Rust. The plan that removes the non-permanent fallbacks is `plans/2026-08-19-rust-fallback-consolidation-plan.md`.

### R2. Negative number display (RTL/BIDI)
- [ ] Use the existing tested pattern wherever a negative amount is shown (especially the new «calculated balance» component in Phase 5):
  - LRM (`\u200E`) before the number
  - Separate the sign and the amount in different `Text` elements (as in `CurrencyFormatter.formatSignedParts`)
  - `LocalLayoutDirection.Ltr` on the number container
- [ ] **Refer to:** `CurrencyFormatter.kt:87` (the LRM pattern) and `AccountBalanceCard.kt` (the sign/amount separation pattern)

### R3. Test confirmation
- [ ] An agent's claim that work is "tested" must be verified with a **test count, the exact output of the run command, and a screenshot when necessary**
- [ ] Test command: `./gradlew test --rerun-tasks --no-daemon` (not a plain `./gradlew test` — the build cache can return old results)

### R4. Confirmation of "done everywhere"
- [ ] A claim that work is "complete everywhere" must be checked with an **independent grep** before you accept it
- [ ] Example: if `ACCOUNT_TYPE_ICONS` is to be removed, grep to confirm that no reference remains

### R5. Rust JNI state leakage
- [ ] If any phase touches Rust code (even mappers only), confirm it with `./gradlew clean test --no-daemon` before the merge
- [ ] `forkEvery = 1` must be enabled in `build.gradle.kts`

---

## Phase Completion Log

> Fill this in after each phase.

### Phase 0
- **Date:**
- **Summary of changes:**
- **Test result:**
- **Verification report link:**
- **Number of commits:**

### Phase 1
- **Date:**
- **Summary of changes:**
- **Test result:**
- **Verification report link:**

### Phase 2
- **Date:**
- **Summary of changes:**
- **Test result:**
- **Verification report link:**

### Phase 3
- **Date:**
- **Summary of changes:**
- **Test result:**
- **Verification report link:**

### Phase 4
- **Date:**
- **Summary of changes:**
- **Test result:**
- **Verification report link:**

### Phase 5
- **Date:**
- **Summary of changes:**
- **Test result:**
- **Verification report link:**

### Phase 6
- **Date:**
- **Summary of changes:**
- **Test result:**
- **Verification report link:**
