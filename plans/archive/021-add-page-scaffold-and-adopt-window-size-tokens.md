# Plan 021: [Design System] Add Page-Scaffold Standard & Adopt WindowSizeTokens

- **Issue:** #458
- **PR:** —
- **Created:** 2026-10-19
- **Author:** Claude Code
- **Status:** DONE

## 1. Problem Statement

Every full-screen or sub-page in Hesabyar manages its own layout shell by hand, creating visual and structural layout drift across the application:

1. **Drifting Scaffold Implementations:**
   - `CategoryManagementScreen.kt:118`: Instantiates a raw Material 3 `Scaffold`, duplicates a private `TopAppBar` with `ArrowForward` back navigation and `containerColor = surface`, and manually applies `innerPadding` to `LazyColumn` with `contentPadding = PaddingValues(bottom = Dimens.FabClearance)`.
   - `AccountManagementScreen.kt:170`: Instantiates a raw Material 3 `Scaffold`, duplicates the identical `TopAppBar` configuration, and passes `innerPadding` down to `AccountManagementContent` with `bottom = Dimens.FabClearance`.
   - `BankLoanScreen.kt:62`: Instantiates a raw `Scaffold` solely to host a `HesabyarFab`, with manual `innerPadding` handling.
   - `DashboardScreen.kt:99`: Bypasses `Scaffold` altogether; instead, it uses an outer `Box`, manually overlays `HesabyarFab` with `.align(Alignment.BottomEnd).padding(SpacingTokens.lg)`, and manually sets `contentPadding = PaddingValues(top = SpacingTokens.sm, bottom = Dimens.FabClearance)`.

2. **Near-Zero Adaptive Layout Adoption (`WindowSizeTokens.ContentMaxWidth`):**
   - `WindowSizeTokens.ContentMaxWidth` (`840.dp`, defined in `ui/designsystem/WindowSizeTokens.kt:8`) is designed to keep line lengths readable on Medium and Expanded window classes (tablets, foldables, Chromebooks, desktop).
   - Currently, it is used in exactly **one** place in the entire app: `BankLoanScreen.kt:81` (`.widthIn(max = WindowSizeTokens.ContentMaxWidth)`).
   - All other screens (`DashboardScreen`, `AccountManagementScreen`, `CategoryManagementScreen`, `AnalyticsScreen`, `ReportsScreen`, `SettingsScreen`, `SmartAssistantScreen`, `DebtHubScreen`) stretch unconstrained across wide displays up to 1200dp+, producing excessively wide cards and poor reading ergonomics.
   - In `MainNavigationBars.kt:61` (`ExpandedMainContent`), the content box aligns to `TopCenter` but does not enforce `ContentMaxWidth`.

3. **Repetitive Bottom Clearance Wiring:**
   - Every screen with a Floating Action Button manually wires `PaddingValues(bottom = Dimens.FabClearance)` (80.dp) to ensure list items are not obscured by the FAB.
   - There is no central container that encapsulates this standard FAB clearance.

## 2. Goals & Acceptance Criteria

- [x] **Criterion 1 (Shared Component):** A reusable `HesabyarScaffold` composable is created in `ui/components/HesabyarScaffold.kt`. It encapsulates:
  - Standard top app bar support via `title: String? = null` and `onBack: (() -> Unit)? = null` (rendering M3 `TopAppBar` with RTL-correct back navigation `Icons.Filled.ArrowForward` and `surface` container color), or a customizable `topBar: (@Composable () -> Unit)? = null` slot.
  - Floating action button slot (`floatingActionButton: @Composable () -> Unit = {}`, `floatingActionButtonPosition: FabPosition = FabPosition.End`).
  - Automatic adaptive content centering using `WindowSizeTokens.ContentMaxWidth` (`840.dp`), enabled by default via `applyContentMaxWidth: Boolean = true` (overridable when a full-width canvas is needed).
  - Lambda slot `content: @Composable (contentPadding: PaddingValues) -> Unit` that provides standard content padding (including `bottom = Dimens.FabClearance` when a FAB is present) while consuming Scaffold `innerPadding` so child layouts never have to manage system or top-bar offsets manually.
- [x] **Criterion 2 (Migrate Overlay Screens):** `CategoryManagementScreen.kt` and `AccountManagementScreen.kt` migrate from raw `Scaffold` + duplicated `TopAppBar` to `HesabyarScaffold(title = ..., onBack = onBack, floatingActionButton = { ... })`:
  - Duplicated `TopAppBar` declarations are deleted.
  - Manual `innerPadding` handling is replaced by `contentPadding`.
  - Content on both screens automatically gains `WindowSizeTokens.ContentMaxWidth` centering on large screens.
- [x] **Criterion 3 (Migrate BankLoanScreen):** `BankLoanScreen.kt` migrates from raw `Scaffold` to `HesabyarScaffold`:
  - Manual `.wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = WindowSizeTokens.ContentMaxWidth)` at lines 79–82 is removed in favor of `HesabyarScaffold`'s built-in centering.
- [x] **Criterion 4 (Migrate DashboardScreen):** `DashboardScreen.kt` migrates from ad-hoc `Box` + aligned FAB to `HesabyarScaffold`:
  - `HesabyarFab` is hosted in `HesabyarScaffold`'s `floatingActionButton` slot with `.testTag("add_transaction_fab")` preserved.
  - Content gains `WindowSizeTokens.ContentMaxWidth` centering on tablets/foldables.
- [x] **Criterion 5 (Expanded Window Adaptive Alignment):** In `MainNavigationBars.kt:61` (`ExpandedMainContent`), the content container applies `.widthIn(max = WindowSizeTokens.ContentMaxWidth)` to enforce readable line lengths for all main tabs in navigation rail mode.
- [x] **Criterion 6 (Unit Tests):** Automated Robolectric tests in `HesabyarScaffoldTest.kt` cover:
  - Title and back button rendering and click callback.
  - Custom `topBar` slot rendering.
  - Floating action button rendering and semantics.
  - Content centering and max-width constraint application.
  - `contentPadding` bottom clearance matches `Dimens.FabClearance` when a FAB is supplied, and `0.dp` when absent.
- [x] **Criterion 7 (Static Analysis & Tests Green):**
  - `./gradlew ktlintCheck detekt --no-daemon` reports 0 findings; no detekt baseline modifications.
  - `./gradlew testDebugUnitTest --no-daemon` reports 0 failures.

## 3. Implementation Steps

### Phase 1: Create `HesabyarScaffold` Component

Create `app/src/main/java/io/github/mojri/hesabyar/ui/components/HesabyarScaffold.kt`.

Signature:

```kotlin
package io.github.mojri.hesabyar.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.mojri.hesabyar.R
import io.github.mojri.hesabyar.ui.designsystem.Dimens
import io.github.mojri.hesabyar.ui.designsystem.WindowSizeTokens

/**
 * Standard page scaffold for Hesabyar screens.
 *
 * Provides consistent top app bar navigation, floating action button placement,
 * bottom FAB clearance in [contentPadding], and adaptive content centering constrained
 * to [WindowSizeTokens.ContentMaxWidth] on medium and expanded displays.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HesabyarScaffold(
  modifier: Modifier = Modifier,
  title: String? = null,
  onBack: (() -> Unit)? = null,
  topBar: (@Composable () -> Unit)? = null,
  floatingActionButton: (@Composable () -> Unit)? = null,
  floatingActionButtonPosition: FabPosition = FabPosition.End,
  bottomBar: @Composable () -> Unit = {},
  snackbarHost: @Composable () -> Unit = {},
  containerColor: Color = MaterialTheme.colorScheme.background,
  contentMaxWidth: Dp = WindowSizeTokens.ContentMaxWidth,
  applyContentMaxWidth: Boolean = true,
  content: @Composable (contentPadding: PaddingValues) -> Unit
) {
  val resolvedTopBar: @Composable () -> Unit = when {
    topBar != null -> topBar
    title != null -> {
      {
        TopAppBar(
          title = {
            Text(
              text = title,
              fontWeight = FontWeight.Bold
            )
          },
          navigationIcon = {
            if (onBack != null) {
              IconButton(onClick = onBack) {
                Icon(
                  imageVector = Icons.Filled.ArrowForward,
                  contentDescription = stringResource(R.string.cancel_label)
                )
              }
            }
          },
          colors =
            TopAppBarDefaults.topAppBarColors(
              containerColor = MaterialTheme.colorScheme.surface
            )
        )
      }
    }
    else -> { {} }
  }

  Scaffold(
    modifier = modifier,
    topBar = resolvedTopBar,
    bottomBar = bottomBar,
    snackbarHost = snackbarHost,
    floatingActionButton = floatingActionButton ?: {},
    floatingActionButtonPosition = floatingActionButtonPosition,
    containerColor = containerColor
  ) { innerPadding ->
    Box(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(innerPadding),
      contentAlignment = Alignment.TopCenter
    ) {
      Box(
        modifier =
          if (applyContentMaxWidth) {
            Modifier
              .fillMaxHeight()
              .fillMaxWidth()
              .widthIn(max = contentMaxWidth)
          } else {
            Modifier.fillMaxSize()
          },
        contentAlignment = Alignment.TopCenter
      ) {
        val scaffoldContentPadding =
          PaddingValues(
            bottom = if (floatingActionButton != null) Dimens.FabClearance else 0.dp
          )
        content(scaffoldContentPadding)
      }
    }
  }
}
```

- Note on string resource: Ensure a dedicated back content description string exists in `strings.xml` (e.g. `back_navigation_description = "بازگشت"`), reusing or adding cleanly.

### Phase 2: Create Robolectric Unit Tests

Create `app/src/test/java/io/github/mojri/hesabyar/ui/components/HesabyarScaffoldTest.kt`.

Cover:
1. `scaffoldRendersTitleAndTriggersBack`: Verify title displays with `FontWeight.Bold` and back icon click invokes `onBack`.
2. `scaffoldRendersCustomTopBar`: Verify explicit `topBar` slot overrides `title`.
3. `scaffoldRendersFab`: Verify `floatingActionButton` is placed and clickable.
4. `scaffoldProvidesFabClearanceWhenFabPresent`: Verify `contentPadding.calculateBottomPadding()` equals `Dimens.FabClearance` (80.dp) when FAB is provided.
5. `scaffoldProvidesZeroBottomPaddingWhenFabAbsent`: Verify `contentPadding.calculateBottomPadding()` equals `0.dp` when no FAB is provided.
6. `scaffoldConstrainsContentWidthToDefaultMax`: Verify content container has `widthIn(max = WindowSizeTokens.ContentMaxWidth)`.

### Phase 3: Migrate `CategoryManagementScreen` and `AccountManagementScreen`

1. **`CategoryManagementScreen.kt`:**
   - Replace outer M3 `Scaffold(...)` with `HesabyarScaffold(title = "مدیریت دسته‌بندی‌ها", onBack = onBack, floatingActionButton = { HesabyarFab(...) }, modifier = modifier)`.
   - Remove duplicate private `TopAppBar` and imports of `androidx.compose.material3.TopAppBar` and `TopAppBarDefaults`.
   - In `content = { contentPadding -> ... }`:
     - Empty state: `Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center)`.
     - `LazyColumn`: Pass `contentPadding = contentPadding` directly; remove `.padding(innerPadding)`.

2. **`AccountManagementScreen.kt`:**
   - Replace outer M3 `Scaffold(...)` with `HesabyarScaffold(title = "مدیریت حساب‌ها", onBack = onBack, floatingActionButton = { HesabyarFab(...) }, modifier = modifier)`.
   - Remove duplicate `TopAppBar` and unused imports.
   - In `AccountManagementContent`: Pass `contentPadding: PaddingValues` instead of `innerPadding: PaddingValues`, applying it to `LazyColumn(contentPadding = contentPadding)`.

### Phase 4: Migrate `BankLoanScreen` and `DashboardScreen`

1. **`BankLoanScreen.kt`:**
   - Replace outer M3 `Scaffold(...)` with `HesabyarScaffold(floatingActionButton = { HesabyarFab(...) }, modifier = modifier)`.
   - In `LazyColumn`:
     - Remove redundant manual `.wrapContentWidth(Alignment.CenterHorizontally).widthIn(max = WindowSizeTokens.ContentMaxWidth)`.
     - Pass `contentPadding = contentPadding`.
     - Remove manual `.padding(innerPadding)`.

2. **`DashboardScreen.kt`:**
   - Replace outer `Box(modifier = modifier.fillMaxSize())` with:
     ```kotlin
     HesabyarScaffold(
       modifier = modifier.fillMaxSize(),
       floatingActionButton = {
         HesabyarFab(
           onClick = { showManualAddDialog = true },
           modifier =
             Modifier
               .padding(SpacingTokens.lg)
               .testTag("add_transaction_fab"),
           contentDescription = "ثبت تراکنش دستی"
         )
       }
     ) { contentPadding ->
       LazyColumn(
         modifier =
           Modifier
             .fillMaxSize()
             .padding(horizontal = SpacingTokens.lg),
         verticalArrangement = Arrangement.spacedBy(SpacingTokens.lg),
         contentPadding =
           PaddingValues(
             top = SpacingTokens.sm,
             bottom = contentPadding.calculateBottomPadding()
           )
       ) {
         ...
       }
     }
     ```
   - Preserves `testTag("add_transaction_fab")` and existing behavior, while eliminating manual bottom-end box alignment.

### Phase 5: Adopt `ContentMaxWidth` in Navigation Rail Container

In `app/src/main/java/io/github/mojri/hesabyar/MainNavigationBars.kt:60` (`ExpandedMainContent`):
```kotlin
    Box(
      modifier =
        Modifier
          .fillMaxSize()
          .padding(innerPadding)
          .weight(1f),
      contentAlignment = Alignment.TopCenter
    ) {
      Box(
        modifier =
          Modifier
            .fillMaxHeight()
            .fillMaxWidth()
            .widthIn(max = WindowSizeTokens.ContentMaxWidth),
        contentAlignment = Alignment.TopCenter
      ) {
        content()
      }
    }
```
This ensures all 6 primary navigation tabs (`Dashboard`, `Assistant`, `Debts`, `Analytics`, `Reports`, `Settings`) automatically stay readable on wide displays (tablets/desktops) when navigation rail is active, completing the adaptive story app-wide.

## 4. Evidence and Verification Plan

### Commands to Run

```bash
# 1. Format and check Kotlin code style
./gradlew ktlintFormat --no-daemon
./gradlew ktlintCheck detekt --no-daemon

# 2. Run new and affected unit tests
./gradlew testDebugUnitTest --tests "io.github.mojri.hesabyar.ui.components.HesabyarScaffoldTest" --no-daemon
./gradlew testDebugUnitTest --tests "io.github.mojri.hesabyar.MainNavigationComponentsTest" --no-daemon
./gradlew testDebugUnitTest --tests "io.github.mojri.hesabyar.ui.components.HesabyarFabTest" --no-daemon
./gradlew testDebugUnitTest --tests "io.github.mojri.hesabyar.ui.screens.account.AccountManagementScreenDeleteDialogTest" --no-daemon

# 3. Full test suite verification
./gradlew testDebugUnitTest --no-daemon

# 4. Check documentation path references
python scripts/check_docs.py
```

### Expected Evidence

1. `HesabyarScaffoldTest` passes all tests with raw test name outputs (`scaffoldRendersTitleAndTriggersBack`, etc.).
2. Zero occurrences of raw `Scaffold(` in `CategoryManagementScreen.kt`, `AccountManagementScreen.kt`, and `BankLoanScreen.kt`.
3. `WindowSizeTokens.ContentMaxWidth` actively referenced by `HesabyarScaffold.kt`, `MainNavigationBars.kt`, and `WindowSizeTokens.kt`.
4. `scripts/check_docs.py` succeeds with "Docs: no broken path references".

## 5. Risks, Dependencies, and STOP Conditions

### Risks & Mitigations

- **Double Padding Risk:** If a screen applies `Modifier.padding(innerPadding)` when `HesabyarScaffold` already consumed it, items will be double-padded from the top.
  - *Mitigation:* `HesabyarScaffold` exposes `contentPadding: PaddingValues` (providing bottom clearance for FABs) rather than raw `innerPadding`. The container Box inside `HesabyarScaffold` consumes the top-bar and window insets.
- **TestTag Retention:** `DashboardScreen`'s FAB uses `testTag("add_transaction_fab")`, which is tested in `HesabyarFabTest`.
  - *Mitigation:* Preserve `.testTag("add_transaction_fab")` on the FAB composable passed into `floatingActionButton`.
- **RTL Back Arrow Direction:** `Icons.Filled.ArrowForward` is the established RTL back arrow in Hesabyar.
  - *Mitigation:* Use `Icons.Filled.ArrowForward` matching existing `CategoryManagementScreen` and `AccountManagementScreen` patterns.

### STOP Conditions

1. If any existing Compose UI test or Activity navigation test fails and requires changing external screen contracts: STOP and verify the root cause.
2. If detekt flags complexity or parameter limits on `HesabyarScaffold` that cannot be resolved without suppressing forbidden rules: STOP and refactor with helper data structures.
3. If an unresolvable layout clipping occurs on small (<360dp) screens: STOP and inspect padding calculation.
