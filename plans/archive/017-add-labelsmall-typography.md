# Plan 017: Add labelSmall to Type.kt

- **Issue:** #454
- **PR:** —
- **Created:** 2026-10-16
- **Author:** Claude Code
- **Status:** DONE

## 1. Problem Statement

Issue #454 identifies that `theme/Type.kt` defines 14 of the 15 Material 3 typography styles. `labelSmall` is omitted from the `Typography(...)` constructor in `app/src/main/java/io/github/mojri/hesabyar/ui/theme/Type.kt:25-139`.

Consequently, any UI element referencing `MaterialTheme.typography.labelSmall` falls back to the default Material 3 typography definition (Roboto / system font) instead of the project's Persian typeface (`VazirmatnFontFamily`).

Across the application, 58 call sites in 11 files currently reference `MaterialTheme.typography.labelSmall`:
- `MainNavigationBars.kt` (navigation bar item labels)
- `AmountQuickFillButtons.kt` (quick fill amount chip labels)
- `CategoryChip.kt` (category chip text)
- `AccountManagementScreen.kt` (account type preview badges and sub-labels)
- `AddPersonLoanDialog.kt` (dialog subtext and helper labels)
- `AnalyticsScreen.kt` (chart axis labels, category breakdown legends, stats badges)
- `CategoryManagementScreen.kt` (category list metadata and badges)
- `CustomTimePickerDialog.kt` (time picker period and unit labels)
- `DashboardHeader.kt` (dashboard greeting sub-label)
- `DebtorCreditorCards.kt` (debtor/creditor card metric captions)
- `TransactionItemSignPlacementTest.kt` (test reference)

All 58 locations render with an inconsistent system fallback typeface instead of Vazirmatn.

## 2. Goals & Acceptance Criteria

- [x] **Criterion 1 (Style defined):** `Type.kt` defines `labelSmall` with `fontFamily = VazirmatnFontFamily`, `fontWeight = FontWeight.Medium`, `fontSize = 11.sp`, `lineHeight = 16.sp`, and `letterSpacing = 0.5.sp`.
- [x] **Criterion 2 (Full M3 coverage):** All 15 Material 3 typography styles in `Typography` use `VazirmatnFontFamily`. No style falls back to system font.
- [x] **Criterion 3 (Automated verification):** A unit test class `app/src/test/java/io/github/mojri/hesabyar/ui/theme/TypographyTest.kt` asserts all 15 typography styles explicitly, verifying `labelSmall` attributes and checking that no style uses the system font fallback.
- [x] **Criterion 4 (Static analysis):** `ktlintCheck` and `detekt` report 0 findings.
- [x] **Criterion 5 (Doc sync):** `python scripts/check_docs.py` passes with 0 broken references.

## 3. Implementation Steps

### Phase 1: Define `labelSmall` in `Type.kt`

In `app/src/main/java/io/github/mojri/hesabyar/ui/theme/Type.kt`:
Add `labelSmall` specification to the `Typography` configuration block, immediately following `labelMedium`:

```kotlin
    labelSmall =
      TextStyle(
        fontFamily = VazirmatnFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 11.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.5.sp
      )
```

Values match standard Material 3 scale:
- `fontSize = 11.sp`
- `lineHeight = 16.sp`
- `letterSpacing = 0.5.sp`
- `fontWeight = FontWeight.Medium` (consistent with `labelLarge` and `labelMedium`)
- `fontFamily = VazirmatnFontFamily`

### Phase 2: Add Comprehensive Typography Unit Tests

Create `app/src/test/java/io/github/mojri/hesabyar/ui/theme/TypographyTest.kt`:
Use PascalCase for the test file name and camelCase for test method names per repo guidelines. Follow the test runner configuration used in UI unit tests (Robolectric with `@RunWith(RobolectricTestRunner::class)` and `@Config(manifest = Config.NONE, sdk = [34])` to safely resolve font provider resources).

Tests to implement:
1. `labelSmallStyleConfiguredWithVazirmatn`: Asserts that `Typography.labelSmall` has `fontFamily == VazirmatnFontFamily`, `fontSize == 11.sp`, `lineHeight == 16.sp`, `fontWeight == FontWeight.Medium`, and `letterSpacing == 0.5.sp`.
2. `allFifteenMaterialTypographyStylesUseVazirmatn`: Iterates or asserts all 15 Material 3 typography styles in `Typography`:
   - `displayLarge`, `displayMedium`, `displaySmall`
   - `headlineLarge`, `headlineMedium`, `headlineSmall`
   - `titleLarge`, `titleMedium`, `titleSmall`
   - `bodyLarge`, `bodyMedium`, `bodySmall`
   - `labelLarge`, `labelMedium`, `labelSmall`
   and verifies `fontFamily == VazirmatnFontFamily` for every style, preventing any future fallback regression.

### Phase 3: Verification & Evidence Collection

Run static checks and unit tests:
1. `./gradlew.bat ktlintFormat --no-daemon`
2. `./gradlew.bat ktlintCheck detekt --no-daemon`
3. `./gradlew.bat testDebugUnitTest --tests "io.github.mojri.hesabyar.ui.theme.TypographyTest"`
4. `python scripts/check_docs.py`

Collect raw outputs and testcase lines per `AGENTS.md` evidence standard.

## 4. Evidence and Verification Plan

```bash
# 1. Formatting and lint
./gradlew.bat ktlintFormat --no-daemon
./gradlew.bat ktlintCheck detekt --no-daemon

# 2. Targeted typography tests
./gradlew.bat testDebugUnitTest --tests "io.github.mojri.hesabyar.ui.theme.TypographyTest"

# 3. Full fast test suite
./gradlew.bat testDebugUnitTest --no-daemon

# 4. Documentation link check
python scripts/check_docs.py
```

Evidence requirements for completion report:
- Exact contents of the `labelSmall` block in `Type.kt`.
- Exact test code in `TypographyTest.kt`.
- Raw JUnit XML `<testcase>` entries from `app/build/test-results/testDebugUnitTest/` showing passed typography tests.
- Confirmation of 0 findings in `ktlintCheck` and `detekt`.

## 5. Risks, Dependencies, and STOP Conditions

- **Visual truncation risk:** Labels sized at 11sp with Vazirmatn could theoretically occupy slightly more or less horizontal width than Roboto fallback. However, Vazirmatn is already the intended project-wide font and all existing UI designs assume Vazirmatn metrics.
- **Migration & data risk:** 0 (pure UI theme configuration; no database, no schema, no serialization touched).
- **STOP conditions:**
  - If any existing UI layout visibly breaks or clips due to Vazirmatn font metrics in `labelSmall`.
  - If `GoogleFont.Provider` or font asset loading fails in the unit test environment, requiring Robolectric vs JVM test adjustments.
