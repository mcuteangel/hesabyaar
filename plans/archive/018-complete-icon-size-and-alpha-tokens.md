# Plan 018: Complete Icon-Size and Alpha Tokens in Dimens.kt

- **Issue:** #455
- **PR:** —
- **Created:** 2026-10-17
- **Author:** Claude Code
- **Status:** DONE

## 1. Problem Statement

The design system's token library (`ui/designsystem/Dimens.kt`) is inconsistent and incomplete:
1. **Missing icon and badge container size tokens:** Multiple screens hardcode raw `Dp` sizes that lack design tokens:
   - `28.dp`: Used for compact circular icon badges / container sizes (`KpiCards.kt:50, 88`, `IncomeExpenseCards.kt:54, 88`, `ReportsScreen.kt:364`, `AccountManagementScreen.kt:769`, `CategoryManagementScreen.kt:452`).
   - `36.dp`: Used for intermediate badge containers and action button boxes (`LoanManagementScreen.kt:364`, `CategoryManagementScreen.kt:313, 326, 488`, `ReportsScreen.kt:335`).
   - `44.dp`: Used for prominent header icon containers and display icons (`DashboardHeader.kt:49`, `SmartAssistantScreen.kt:669`).
2. **Existing size tokens bypassed:**
   - `Dimens.IconAvatarMedium` (`20.dp`): Bypassed with literal `20.dp` in `TransactionMiniItem.kt:189, 197`, `CategoryManagementScreen.kt:282, 502, 532`, `LoanManagementScreen.kt:365`, `ReportsScreen.kt:707`, `DashboardHeader.kt:76`, `SmartParsingBanner.kt:54`.
   - `Dimens.IconMenu` (`18.dp`): Bypassed with literal `18.dp` in `ReportsScreen.kt:343, 494, 734`, `CategoryManagementScreen.kt:318, 331`, `TransactionMiniItem.kt:131`, `InstallmentScreen.kt:444`, `InstallmentMiniItem.kt:53`, `SmartForecastCard.kt:72`.
3. **Missing semantic alpha tokens:** `Dimens` defines only `0.15f`, `0.2f`, and `0.7f`, while screens hardcode raw float alpha literals:
   - `0.08f`: Subtle brand card background tint in `SettingsScreen.kt:314`.
   - `0.10f`: Status badge / card tint in `SmartAssistantScreen.kt:910, 1316, 1375`, `LoanManagementScreen.kt:501`, `InstallmentScreen.kt:438`.
   - `0.12f`: Badge and pill container tint in `DebtorCreditorCards.kt:75, 125`, `ReportsScreen.kt:317`, `LoanManagementScreen.kt:414`.
   - `0.25f` / `0.28f` / `0.30f`: Accent container / banner tints in `SmartForecastCard.kt:61` (`0.28f`), `SmartParsingBanner.kt:37` (`0.30f`), `SettingsScreen.kt:1071` (`0.30f`), `ReportsScreen.kt:577` (`0.25f`), `JalaliDatePickerDialog.kt:325, 400` (`0.30f`), `LoanManagementScreen.kt:515` (`0.30f`).
   - `0.60f`: Muted foreground / icon tint in `SmartAssistantScreen.kt:292`, `CustomTimePickerDialog.kt:395`.
4. **Existing alpha tokens defined but bypassed:**
   - `Dimens.SURFACE_TINT_ALPHA` (`0.2f`): Defined in `Dimens.kt:42` with 0 call sites, while raw `alpha = 0.2f` appears in `SmartAssistantScreen.kt:207, 436`, `CategoryManagementScreen.kt:492`, `BalanceCard.kt:35`.
   - `Dimens.SECONDARY_TEXT_ALPHA` (`0.7f`): Defined in `Dimens.kt:45` with 0 call sites, while raw `alpha = 0.7f` appears in `SmartAssistantScreen.kt:238, 474`, `SettingsScreen.kt:926`, `ReportsScreen.kt:612, 733`, `TransactionMiniItem.kt:130`, `InstallmentScreen.kt:425`, `AccountBreakdownCard.kt:85`, `AnalyticsScreen.kt:453`, `CustomTimePickerDialog.kt:181, 235`.
5. **Inconsistent typography weight:** `FontWeight.ExtraBold` is used in `DebtorCreditorCards.kt:90, 140`, but no `Type.kt` style uses `ExtraBold`. Other dashboard KPI cards (`KpiCards.kt`, `IncomeExpenseCards.kt`) use `FontWeight.Bold` on `titleMedium` / `titleLarge`.

---

## 2. Goals & Acceptance Criteria

- [x] **Criterion 1 (Tokens Added to Dimens.kt):** Add standardized, well-documented tokens in `ui/designsystem/Dimens.kt`:
  - Size tokens:
    - `IconXLarge = 44.dp` (for large standalone/feature icons, e.g. `SmartAssistantScreen`, `IconCircle` containers in `DashboardHeader`)
    - `ContainerXSmall = 28.dp` (compact circular badges and KPI card icon badge containers)
    - `ContainerSmall = 36.dp` (intermediate badge containers and small icon buttons)
    - `ContainerMedium = 44.dp` (prominent header badge containers)
  - Alpha tokens:
    - `SUBTLE_BRAND_ALPHA = 0.08f`
    - `CHIP_CONTAINER_ALPHA = 0.12f`
    - `CARD_CONTAINER_ALPHA = 0.10f`
    - `BANNER_CONTAINER_ALPHA = 0.30f`
    - `MUTED_FOREGROUND_ALPHA = 0.60f`
- [x] **Criterion 2 (Unused Tokens Wired):** Wire `Dimens.IconMenu` (`18.dp`), `Dimens.IconAvatarMedium` (`20.dp`), `Dimens.SURFACE_TINT_ALPHA` (`0.2f`), and `Dimens.SECONDARY_TEXT_ALPHA` (`0.7f`) across all bypassed locations identified in the codebase.
- [x] **Criterion 3 (Hardcoded Literals Replaced):** Replace all identified raw `28.dp`, `36.dp`, `44.dp` container/icon sizes and raw alpha floats (`0.08f`, `0.10f`, `0.12f`, `0.20f`, `0.28f/0.30f`, `0.60f`, `0.70f`) with their corresponding tokens in:
  - `KpiCards.kt`, `IncomeExpenseCards.kt`, `DebtorCreditorCards.kt`, `DashboardHeader.kt`, `SmartForecastCard.kt`, `SmartParsingBanner.kt`, `TransactionMiniItem.kt`
  - `LoanManagementScreen.kt`, `CategoryManagementScreen.kt`, `SettingsScreen.kt`, `SmartAssistantScreen.kt`, `ReportsScreen.kt`
- [x] **Criterion 4 (Typography Consistency):** Align `FontWeight.ExtraBold` in `DebtorCreditorCards.kt` to `FontWeight.Bold` matching all other KPI and dashboard metric cards (`KpiCards`, `IncomeExpenseCards`, `BalanceCard`).
- [x] **Criterion 5 (Automated Unit Tests):** Add a comprehensive `DimensTokenTest` in `app/src/test/java/io/github/mojri/hesabyar/ui/designsystem/DimensTokenTest.kt` <!-- check-docs: ignore --> verifying all new and existing size and alpha tokens, their values, and ranges `[0f..1f]`.
- [x] **Criterion 6 (Static Analysis & Doc Check):** Pass `./gradlew.bat ktlintCheck detekt --no-daemon` and `python scripts/check_docs.py` with 0 errors.

---

## 3. Implementation Steps

### Phase 1: Define New Tokens in `Dimens.kt`

In `app/src/main/java/io/github/mojri/hesabyar/ui/designsystem/Dimens.kt`:
1. Add size tokens:
   ```kotlin
   /** Large icon size for display and error states (44dp). */
   val IconXLarge = 44.dp

   /** Extra small container size for circular icon badges (e.g. KPI cards, color pickers, 28dp). */
   val ContainerXSmall = 28.dp

   /** Small container size for icon badges and compact action boxes (36dp). */
   val ContainerSmall = 36.dp

   /** Medium container size for prominent header icon badges (44dp). */
   val ContainerMedium = 44.dp
   ```
2. Add alpha constants:
   ```kotlin
   /** Very subtle background tint (e.g. settings branding card, 8%). */
   const val SUBTLE_BRAND_ALPHA = 0.08f

   /** Alpha for status/confidence background cards (10%). */
   const val CARD_CONTAINER_ALPHA = 0.10f

   /** Alpha for compact badges and pill indicators on surfaces (12%). */
   const val CHIP_CONTAINER_ALPHA = 0.12f

   /** Alpha for highlighted banners and alert containers (30%). */
   const val BANNER_CONTAINER_ALPHA = 0.30f

   /** Alpha for muted foreground icons and inactive indicators (60%). */
   const val MUTED_FOREGROUND_ALPHA = 0.60f
   ```

### Phase 2: Add Token Unit Test (`DimensTokenTest.kt`) <!-- check-docs: planned -->

Create `app/src/test/java/io/github/mojri/hesabyar/ui/designsystem/DimensTokenTest.kt`:
- Test that all size tokens match design specs (`IconSmall = 16.dp`, `IconMenu = 18.dp`, `IconAvatarMedium = 20.dp`, `IconMedium = 24.dp`, `IconLarge = 32.dp`, `IconXLarge = 44.dp`).
- Test that container sizes match specs (`ContainerXSmall = 28.dp`, `ContainerSmall = 36.dp`, `ContainerMedium = 44.dp`, `AvatarSmall = 32.dp`, `AvatarMedium = 40.dp`, `AvatarLarge = 56.dp`).
- Test that alpha tokens are valid float probabilities (`0f < alpha <= 1f`):
  `SUBTLE_BRAND_ALPHA == 0.08f`, `CARD_CONTAINER_ALPHA == 0.10f`, `CHIP_CONTAINER_ALPHA == 0.12f`, `ICON_CIRCLE_BACKGROUND_ALPHA == 0.15f`, `SURFACE_TINT_ALPHA == 0.20f`, `BANNER_CONTAINER_ALPHA == 0.30f`, `MUTED_FOREGROUND_ALPHA == 0.60f`, `SECONDARY_TEXT_ALPHA == 0.70f`.

### Phase 3: Migrate Dashboard Components

1. `KpiCards.kt`:
   - Replace `containerSize = 28.dp` with `containerSize = Dimens.ContainerXSmall` (lines 50, 88).
2. `IncomeExpenseCards.kt`:
   - Replace `containerSize = 28.dp` with `containerSize = Dimens.ContainerXSmall` (lines 54, 88).
3. `DashboardHeader.kt`:
   - Replace `containerSize = 44.dp` with `containerSize = Dimens.ContainerMedium` (line 49).
   - Replace `modifier = Modifier.size(20.dp)` with `Dimens.IconAvatarMedium` (line 76).
4. `DebtorCreditorCards.kt`:
   - Replace `alpha = 0.12f` with `Dimens.CHIP_CONTAINER_ALPHA` (lines 75, 125).
   - Replace `FontWeight.ExtraBold` with `FontWeight.Bold` (lines 90, 140).
5. `SmartForecastCard.kt`:
   - Replace `alpha = 0.28f` with `Dimens.BANNER_CONTAINER_ALPHA` (line 61).
   - Replace `iconSize = 18.dp` with `iconSize = Dimens.IconMenu` (line 72).
6. `SmartParsingBanner.kt`:
   - Replace `alpha = 0.3f` with `Dimens.BANNER_CONTAINER_ALPHA` (line 37).
   - Replace `iconSize = 20.dp` with `iconSize = Dimens.IconAvatarMedium` (line 54).
   - Replace `containerSize = 40.dp` with `containerSize = Dimens.AvatarMedium` (line 55).
7. `TransactionMiniItem.kt`:
   - Replace `containerSize = 20.dp` with `containerSize = Dimens.IconAvatarMedium` (lines 189, 197).
   - Replace `modifier = Modifier.size(18.dp)` with `Dimens.IconMenu` (line 131).
   - Replace `alpha = 0.7f` with `Dimens.SECONDARY_TEXT_ALPHA` (line 130).

### Phase 4: Migrate Screens

1. `CategoryManagementScreen.kt`:
   - Replace `iconSize = 20.dp` with `Dimens.IconAvatarMedium` (lines 282, 502, 532).
   - Replace `Modifier.size(36.dp)` with `Dimens.ContainerSmall` (lines 313, 326, 488).
   - Replace `Modifier.size(18.dp)` with `Dimens.IconMenu` (lines 318, 331).
   - Replace `Modifier.size(28.dp)` with `Dimens.ContainerXSmall` (line 452).
   - Replace `alpha = 0.2f` with `Dimens.SURFACE_TINT_ALPHA` (line 492).
2. `LoanManagementScreen.kt`:
   - Replace `containerSize = 36.dp` with `Dimens.ContainerSmall` (line 364).
   - Replace `iconSize = 20.dp` with `Dimens.IconAvatarMedium` (line 365).
   - Replace `alpha = 0.12f` with `Dimens.CHIP_CONTAINER_ALPHA` (line 414).
   - Replace `alpha = 0.1f` with `Dimens.CARD_CONTAINER_ALPHA` (line 501).
   - Replace `alpha = 0.3f` with `Dimens.BANNER_CONTAINER_ALPHA` (line 515).
3. `SettingsScreen.kt`:
   - Replace `alpha = 0.08f` with `Dimens.SUBTLE_BRAND_ALPHA` (line 314).
   - Replace `alpha = 0.7f` with `Dimens.SECONDARY_TEXT_ALPHA` (line 926).
   - Replace `alpha = 0.3f` with `Dimens.BANNER_CONTAINER_ALPHA` (line 1071).
4. `SmartAssistantScreen.kt`:
   - Replace `alpha = 0.2f` with `Dimens.SURFACE_TINT_ALPHA` (lines 207, 436).
   - Replace `alpha = 0.7f` with `Dimens.SECONDARY_TEXT_ALPHA` (lines 238, 474).
   - Replace `alpha = 0.6f` with `Dimens.MUTED_FOREGROUND_ALPHA` (line 292).
   - Replace `alpha = 0.1f` with `Dimens.CARD_CONTAINER_ALPHA` (lines 910, 1316, 1375).
   - Replace `Modifier.size(44.dp)` with `Dimens.IconXLarge` (line 669).
5. `ReportsScreen.kt`:
   - Replace `alpha = 0.12f` with `Dimens.CHIP_CONTAINER_ALPHA` (line 317).
   - Replace `Modifier.size(36.dp)` with `Dimens.ContainerSmall` (line 335).
   - Replace `Modifier.size(18.dp)` with `Dimens.IconMenu` (lines 343, 494, 734).
   - Replace `Modifier.size(28.dp)` with `Dimens.ContainerXSmall` (line 364).
   - Replace `containerSize = 20.dp` with `Dimens.IconAvatarMedium` (line 707).
   - Replace `alpha = 0.7f` with `Dimens.SECONDARY_TEXT_ALPHA` (lines 612, 733).
6. `AccountManagementScreen.kt`:
   - Replace `Modifier.size(28.dp)` with `Dimens.ContainerXSmall` (lines 769, 786).

### Phase 5: Verification & Full Evidence

1. Format and lint checks:
   ```bash
   ./gradlew.bat ktlintFormat --no-daemon
   ./gradlew.bat ktlintCheck detekt --no-daemon
   ```
2. Targeted unit tests:
   ```bash
   ./gradlew.bat testDebugUnitTest --tests "io.github.mojri.hesabyar.ui.designsystem.DimensTokenTest"
   ```
3. Full fast test suite:
   ```bash
   ./gradlew.bat testDebugUnitTest --no-daemon
   ```
4. Documentation check:
   ```bash
   python scripts/check_docs.py
   ```

---

## 4. Evidence and Verification Plan

- **Lint & format:** `ktlintCheck` and `detekt` report 0 errors/violations.
- **Unit test execution:**
  - Raw `<testcase>` execution for `DimensTokenTest` showing all size and alpha assertions pass.
  - Regression check on existing UI tests (`KpiCards`, `DashboardScreen`, `ReportsScreen`, etc.).
- **Doc verification:** `scripts/check_docs.py` reports 0 broken links.

---

## 5. Risks, Dependencies, and STOP Conditions

- **Visual risk:** Token values exactly match the replaced numeric constants (`18.dp`, `20.dp`, `28.dp`, `36.dp`, `44.dp`, `0.08f`, `0.10f`, `0.12f`, `0.20f`, `0.30f`, `0.60f`, `0.70f`), except `0.28f` in `SmartForecastCard` which maps to canonical `0.30f` banner alpha (a visually imperceptible 2% difference for token consolidation).
- **Data & architecture risk:** 0 (pure UI tokens; no schema, Room, or Rust core touched).
- **STOP conditions:**
  - If any existing UI test fails or layout truncation/overflow occurs when mapping literals to tokens.
  - If detekt flags any threshold violations on modified files.
