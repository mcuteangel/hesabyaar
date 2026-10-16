# Plan 015: [Design System] Add a shared HesabyarFab component & Migrate Raw Uses (Issue #451)

Status: IN PROGRESS

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a unified `HesabyarFab` component in `ui/components/` that consumes `Dimens.FABSize` and `Dimens.FABIconSize`, migrate all raw `FloatingActionButton` uses across the app to it, and wire `Dimens.FabClearance` for list bottom padding.

**Architecture:** A single reusable Compose component wrapping Material 3 `FloatingActionButton` with standardized tokens (`FABSize` 56dp, `FABIconSize` 24dp, `ShapeTokens.Large`, `primary` / `onPrimary` colors). Replaces all 4 raw FAB sites (`AccountManagementScreen`, `DashboardScreen`, `CategoryManagementScreen`, and `BankLoanScreen`), wiring the dead tokens and standardizing bottom clearance via `Dimens.FabClearance`.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Robolectric (Compose UI testing), Gradle, Detekt, ktlint.

## Global Constraints

- Android & Jetpack Compose Material 3 standards.
- Follow `docs/agents/code-guidelines.md` (DRY, semantic tokens, no magic numbers, PascalCase test classes, camelCase test methods).
- Follow `docs/agents/testing.md` and `docs/agents/detekt.md` (pass `./gradlew ktlintCheck detekt`, never re-baseline detekt).
- Evidence standard: provide exact file paths, real code, and raw test output for completion reports.

---

## File Structure <!-- check-docs: planned -->

- **Create:**
  - `app/src/main/java/io/github/mojri/hesabyar/ui/components/HesabyarFab.kt`
    - Shared `HesabyarFab` composable component and `@Preview` functions (Light & Dark theme).
  - `app/src/test/java/io/github/mojri/hesabyar/ui/components/HesabyarFabTest.kt`
    - Robolectric Compose UI unit tests verifying rendering, click events, semantics, and token bindings.
- **Modify:**
  - `app/src/main/java/io/github/mojri/hesabyar/ui/designsystem/Dimens.kt`
    - Add `FabClearance = 80.dp`, deprecate `BottomNavClearance` with fallback to `FabClearance`.
  - `app/src/main/java/io/github/mojri/hesabyar/ui/screens/account/AccountManagementScreen.kt`
    - Replace raw `FloatingActionButton` with `HesabyarFab`; replace hardcoded `80.dp` bottom padding with `Dimens.FabClearance`.
  - `app/src/main/java/io/github/mojri/hesabyar/ui/screens/DashboardScreen.kt`
    - Replace raw `FloatingActionButton` with `HesabyarFab`; replace hardcoded `80.dp` bottom padding with `Dimens.FabClearance`.
  - `app/src/main/java/io/github/mojri/hesabyar/ui/screens/CategoryManagementScreen.kt`
    - Replace raw `FloatingActionButton` with `HesabyarFab`; replace hardcoded `80.dp` bottom padding with `Dimens.FabClearance`.
  - `app/src/main/java/io/github/mojri/hesabyar/ui/screens/BankLoanScreen.kt`
    - Replace raw `FloatingActionButton` with `HesabyarFab`.

---

### Task 1: Update Dimens Tokens (`FabClearance` and Javadoc) <!-- check-docs: planned -->

**Files:**
- Modify: `app/src/main/java/io/github/mojri/hesabyar/ui/designsystem/Dimens.kt:21-27`

**Interfaces:**
- Produces: `Dimens.FabClearance = 80.dp`, `@Deprecated val BottomNavClearance = FabClearance`

- [ ] **Step 1: Write test checking Dimens tokens exist and match specifications**

In `app/src/test/java/io/github/mojri/hesabyar/ui/components/HesabyarFabTest.kt`:

```kotlin
package io.github.mojri.hesabyar.ui.components

import androidx.compose.ui.unit.dp
import io.github.mojri.hesabyar.ui.designsystem.Dimens
import org.junit.Assert.assertEquals
import org.junit.Test

class HesabyarFabTokenTest {
  @Test
  fun fabDimensionsTokensMatchSpec() {
    assertEquals("FAB size must be 56dp", 56.dp, Dimens.FABSize)
    assertEquals("FAB icon size must be 24dp", 24.dp, Dimens.FABIconSize)
    assertEquals("FAB clearance must be 80dp", 80.dp, Dimens.FabClearance)
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "io.github.mojri.hesabyar.ui.components.HesabyarFabTokenTest"`
Expected: FAIL (unresolved reference `FabClearance`)

- [ ] **Step 3: Update `Dimens.kt`**

In `app/src/main/java/io/github/mojri/hesabyar/ui/designsystem/Dimens.kt`:

```kotlin
  val ButtonHeight = 48.dp
  val FABSize = 56.dp
  val FABIconSize = 24.dp

  val DividerThickness = 1.dp

  /** Clearance at the bottom of scrollable lists so content is not obscured by a FAB. */
  val FabClearance = 80.dp

  @Deprecated("Use FabClearance instead — the app clearance is for the FAB.", ReplaceWith("FabClearance"))
  val BottomNavClearance = FabClearance
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "io.github.mojri.hesabyar.ui.components.HesabyarFabTokenTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/mojri/hesabyar/ui/designsystem/Dimens.kt app/src/test/java/io/github/mojri/hesabyar/ui/components/HesabyarFabTest.kt
git commit -m "refactor(designsystem): add Dimens.FabClearance and deprecate BottomNavClearance (#451)"
```

---

### Task 2: Implement `HesabyarFab` Component and Robolectric Tests <!-- check-docs: planned -->

**Files:**
- Create: `app/src/main/java/io/github/mojri/hesabyar/ui/components/HesabyarFab.kt`
- Modify: `app/src/test/java/io/github/mojri/hesabyar/ui/components/HesabyarFabTest.kt`

**Interfaces:**
- Produces:
  ```kotlin
  @Composable
  fun HesabyarFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector = Icons.Filled.Add,
    contentDescription: String? = null,
    containerColor: Color = MaterialTheme.colorScheme.primary,
    contentColor: Color = MaterialTheme.colorScheme.onPrimary,
    shape: Shape = ShapeTokens.Large,
  )
  ```

- [ ] **Step 1: Write failing Robolectric Compose UI tests for `HesabyarFab`**

In `app/src/test/java/io/github/mojri/hesabyar/ui/components/HesabyarFabTest.kt`:

```kotlin
package io.github.mojri.hesabyar.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Edit
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import io.github.mojri.hesabyar.ui.designsystem.Dimens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE, sdk = [34])
class HesabyarFabTest {
  @get:Rule
  val composeRule = createComposeRule()

  @Test
  fun fabDimensionsTokensMatchSpec() {
    assertEquals("FAB size must be 56dp", 56.dp, Dimens.FABSize)
    assertEquals("FAB icon size must be 24dp", 24.dp, Dimens.FABIconSize)
    assertEquals("FAB clearance must be 80dp", 80.dp, Dimens.FabClearance)
  }

  @Test
  fun fabRendersWithContentDescriptionAndTriggersClick() {
    var clicked = false
    val description = "افزودن حساب"

    composeRule.setContent {
      HesabyarFab(
        onClick = { clicked = true },
        contentDescription = description,
        icon = Icons.Filled.Add,
      )
    }

    composeRule
      .onNodeWithContentDescription(description)
      .assertIsDisplayed()
      .performClick()

    assertTrue("Click handler should be invoked", clicked)
  }

  @Test
  fun fabSupportsCustomModifierAndTestTag() {
    val testTag = "add_transaction_fab"

    composeRule.setContent {
      HesabyarFab(
        onClick = {},
        modifier = Modifier.testTag(testTag),
        icon = Icons.Filled.Edit,
        contentDescription = "ویرایش"
      )
    }

    composeRule
      .onNodeWithTag(testTag)
      .assertIsDisplayed()
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `./gradlew testDebugUnitTest --tests "io.github.mojri.hesabyar.ui.components.HesabyarFabTest"`
Expected: FAIL (unresolved reference `HesabyarFab`)

- [ ] **Step 3: Implement `HesabyarFab.kt`**

Create `app/src/main/java/io/github/mojri/hesabyar/ui/components/HesabyarFab.kt`:

```kotlin
package io.github.mojri.hesabyar.ui.components

import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import io.github.mojri.hesabyar.ui.designsystem.Dimens
import io.github.mojri.hesabyar.ui.designsystem.ShapeTokens

/**
 * Standard Hesabyar Floating Action Button component wired to canonical design system tokens.
 *
 * Enforces [Dimens.FABSize] (56dp) container and [Dimens.FABIconSize] (24dp) icon size,
 * with standard Material 3 color defaults ([MaterialTheme.colorScheme.primary] on [onPrimary]).
 */
@Composable
fun HesabyarFab(
  onClick: () -> Unit,
  modifier: Modifier = Modifier,
  icon: ImageVector = Icons.Filled.Add,
  contentDescription: String? = null,
  containerColor: Color = MaterialTheme.colorScheme.primary,
  contentColor: Color = MaterialTheme.colorScheme.onPrimary,
  shape: Shape = ShapeTokens.Large,
) {
  FloatingActionButton(
    onClick = onClick,
    modifier = modifier.size(Dimens.FABSize),
    containerColor = containerColor,
    contentColor = contentColor,
    shape = shape,
  ) {
    Icon(
      imageVector = icon,
      contentDescription = contentDescription,
      modifier = Modifier.size(Dimens.FABIconSize),
    )
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `./gradlew testDebugUnitTest --tests "io.github.mojri.hesabyar.ui.components.HesabyarFabTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add app/src/main/java/io/github/mojri/hesabyar/ui/components/HesabyarFab.kt app/src/test/java/io/github/mojri/hesabyar/ui/components/HesabyarFabTest.kt
git commit -m "feat(ui): add HesabyarFab component with design system tokens (#451)"
```

---

### Task 3: Migrate `AccountManagementScreen` and `CategoryManagementScreen`

**Files:**
- Modify: `app/src/main/java/io/github/mojri/hesabyar/ui/screens/account/AccountManagementScreen.kt:184-195,370`
- Modify: `app/src/main/java/io/github/mojri/hesabyar/ui/screens/CategoryManagementScreen.kt:140-151,176`

**Changes:**
1. In `AccountManagementScreen.kt`:
   - Replace raw `FloatingActionButton` at line 185 with:
     ```kotlin
     floatingActionButton = {
       HesabyarFab(
         onClick = { dialogState = AccountDialogState.Add },
         contentDescription = stringResource(id = io.github.mojri.hesabyar.R.string.add_account)
       )
     }
     ```
   - Replace `contentPadding = PaddingValues(bottom = 80.dp)` at line 370 with:
     ```kotlin
     contentPadding = PaddingValues(bottom = Dimens.FabClearance),
     ```
   - Clean up unused imports (`FloatingActionButton`).

2. In `CategoryManagementScreen.kt`:
   - Replace raw `FloatingActionButton` at line 141 with:
     ```kotlin
     floatingActionButton = {
       HesabyarFab(
         onClick = { showAddDialog = true },
         contentDescription = "افزودن دسته‌بندی"
       )
     }
     ```
   - Replace `contentPadding = PaddingValues(bottom = 80.dp)` at line 176 with:
     ```kotlin
     contentPadding = PaddingValues(bottom = Dimens.FabClearance),
     ```
   - Clean up unused imports (`FloatingActionButton`).

- [ ] **Step 1: Apply migrations to `AccountManagementScreen.kt` and `CategoryManagementScreen.kt`**
- [ ] **Step 2: Run unit tests for these screens**

Run: `./gradlew testDebugUnitTest --tests "*Account*" --tests "*Category*"`
Expected: PASS

- [ ] **Step 3: Commit**

```bash
git add app/src/main/java/io/github/mojri/hesabyar/ui/screens/account/AccountManagementScreen.kt app/src/main/java/io/github/mojri/hesabyar/ui/screens/CategoryManagementScreen.kt
git commit -m "refactor(ui): migrate Account and Category screens to HesabyarFab (#451)"
```

---

### Task 4: Migrate `DashboardScreen` and `BankLoanScreen`

**Files:**
- Modify: `app/src/main/java/io/github/mojri/hesabyar/ui/screens/DashboardScreen.kt:107,272-287`
- Modify: `app/src/main/java/io/github/mojri/hesabyar/ui/screens/BankLoanScreen.kt:63-67`

**Changes:**
1. In `DashboardScreen.kt`:
   - Replace raw `FloatingActionButton` at line 272 with:
     ```kotlin
     HesabyarFab(
       onClick = { showManualAddDialog = true },
       modifier =
         Modifier
           .align(Alignment.BottomEnd)
           .padding(SpacingTokens.lg)
           .testTag("add_transaction_fab"),
       contentDescription = "ثبت تراکنش دستی"
     )
     ```
   - Replace `contentPadding = PaddingValues(top = SpacingTokens.sm, bottom = 80.dp)` at line 107 with:
     ```kotlin
     contentPadding = PaddingValues(top = SpacingTokens.sm, bottom = Dimens.FabClearance)
     ```
   - Clean up unused `FloatingActionButton` import.

2. In `BankLoanScreen.kt`:
   - Replace raw `FloatingActionButton` at line 64 with:
     ```kotlin
     floatingActionButton = {
       HesabyarFab(
         onClick = { showAddDialog = true },
         contentDescription = "افزودن وام بانکی"
       )
     }
     ```
   - Clean up unused `FloatingActionButton` import.

- [ ] **Step 1: Apply migrations to `DashboardScreen.kt` and `BankLoanScreen.kt`**
- [ ] **Step 2: Verify zero raw `FloatingActionButton(` remain in `ui/screens/`**

Run: `git grep "FloatingActionButton(" app/src/main/java/io/github/mojri/hesabyar/ui/screens`
Expected: 0 matches

- [ ] **Step 3: Run unit tests for Dashboard & Loan**

Run: `./gradlew testDebugUnitTest --tests "*Dashboard*" --tests "*Loan*"`
Expected: PASS

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/io/github/mojri/hesabyar/ui/screens/DashboardScreen.kt app/src/main/java/io/github/mojri/hesabyar/ui/screens/BankLoanScreen.kt
git commit -m "refactor(ui): migrate Dashboard and BankLoan screens to HesabyarFab (#451)"
```

---

### Task 5: Static Analysis, Full Verification & Docs Check

**Files:**
- Verify all modified and new files.

- [ ] **Step 1: Run ktlint check and auto-format**

Run: `./gradlew ktlintFormat ktlintCheck --no-daemon`
Expected: PASS with 0 violations

- [ ] **Step 2: Run Detekt static analysis**

Run: `./gradlew detekt --no-daemon`
Expected: BUILD SUCCESSFUL (0 new detekt findings)

- [ ] **Step 3: Run full non-Rust unit test suite**

Run: `./gradlew testDebugUnitTest --no-daemon`
Expected: BUILD SUCCESSFUL, all tests pass

- [ ] **Step 4: Run doc link checker**

Run: `python3 scripts/check_docs.py`
Expected: PASS with 0 broken references

- [ ] **Step 5: Verify Graphify knowledge graph reflects the new component**

Run: `graphify query "HesabyarFab"`
Expected: Node `HesabyarFab()` found in community.
