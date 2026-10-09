# Plan 014 — Navigation Consistency: BackHandler for Overlays, Distinct Tab Icons, and Padding

- **Issue:** #360
- **PR:** #385
- **Created:** 2026-10-09
- **Author:** Claude Code
- **Status:** DONE

## 1. Problem Statement

Issue #360 identifies four navigation and UX consistency defects in `hesabyaar`:
1. **Overlay BackHandler:** The system Back button previously exited the app instead of closing overlay management screens (Accounts, Categories).
2. **Duplicate Navigation Icons:** `TAB_DASHBOARD` in `MAIN_TABS` uses `Icons.Filled.AccountBalanceWallet`, which is identical to the icon for `nav_tab_accounts` in `MoreMenuSheet`.
3. **Debts Tab Label Length:** The debts tab string resource `nav_tab_debts` is currently `"مدیریت بدهی‌ها"` instead of the concise `"بدهی‌ها"`, cluttering the bottom navigation bar.
4. **Hardcoded Bottom Clearance:** `MoreMenuSheet` uses a hardcoded `Spacer(modifier = Modifier.height(32.dp))` instead of `Modifier.navigationBarsPadding()`, causing incorrect insets across 3-button and gesture navigation devices.

## 2. Goals & Acceptance Criteria

- [x] **Criterion 1 (BackHandler):** System back on overlays dismisses the active overlay before exiting or changing tabs (addressed in PR #385 via `MainNavigationCoordinator`).
- [x] **Criterion 2 (Distinct Icons):** No two navigation destinations share the same icon. `TAB_DASHBOARD` uses `Icons.Filled.Dashboard`, leaving `Icons.Filled.AccountBalanceWallet` unique to Accounts management.
- [x] **Criterion 3 (Debts Label):** `nav_tab_debts` string resource updated to `"بدهی‌ها"`.
- [x] **Criterion 4 (Bottom Insets):** `MoreMenuSheet` uses `Modifier.navigationBarsPadding()` for dynamic window inset clearance.
- [x] **Criterion 5 (Automated Tests):** Automated unit tests verify unique icons across tabs, correct debts label, and regression safety.

## 3. Implementation Steps

### Phase 1: Icon Differentiation
- In `app/src/main/java/io/github/mojri/hesabyar/MainNavigationBars.kt`, update `MAIN_TABS`:
  - Change `TAB_DASHBOARD` icon from `Icons.Filled.AccountBalanceWallet` to `Icons.Filled.Dashboard`.

### Phase 2: String Resource Refinement
- In `app/src/main/res/values/strings.xml`, update line 174:
  - Change `<string name="nav_tab_debts">مدیریت بدهی‌ها</string>` to `<string name="nav_tab_debts">بدهی‌ها</string>`.

### Phase 3: Inset Padding Correction
- In `app/src/main/java/io/github/mojri/hesabyar/MainActivityNavigation.kt`, update `MoreMenuSheet`:
  - Replace `Spacer(modifier = Modifier.height(32.dp))` with `Spacer(modifier = Modifier.navigationBarsPadding())`.

### Phase 4: Unit & UI Tests
- In `app/src/test/java/io/github/mojri/hesabyar/MainNavigationComponentsTest.kt`:
  - Add test `navigationDestinationsHaveDistinctIcons` verifying no duplicates between `MAIN_TABS` icons and accounts icon.
  - Add test `debtsTabLabelIsConcise` asserting string resource is `"بدهی‌ها"`.
- Run all unit tests with `./gradlew.bat testDebugUnitTest` and static analysis checks (`ktlintCheck`, `detekt`).

## 4. Verification

Execute:
```bash
./gradlew.bat ktlintFormat --no-daemon
./gradlew.bat ktlintCheck detekt --no-daemon
./gradlew.bat testDebugUnitTest --tests "io.github.mojri.hesabyar.MainNavigationComponentsTest" --tests "io.github.mojri.hesabyar.MoreMenuSheetTest"
```
Ensure all tests pass and static checks report 0 findings.
