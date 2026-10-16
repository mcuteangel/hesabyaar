# Plan 016: Shared Tab Component — TabRow / SecondaryScrollableTabRow used raw

- **Issue:** #452
- **PR:** —
- **Created:** 2026-10-16
- **Author:** Claude Code
- **Status:** IN PROGRESS

## 1. Problem Statement

Issue #452 identifies that the design system has no shared tab component. Two screens use raw Material 3 tab rows with separate, unshared styling:

1. **Raw TabRow in Smart Assistant:** `SmartAssistantScreen.kt:144` builds a raw `TabRow` with inline `HorizontalDivider`, `FontWeight.Bold` on both tabs, and a hardcoded `Modifier.size(20.dp)`.
2. **Raw SecondaryScrollableTabRow in Debt Hub:** `DebtHubScreen.kt:121` builds a raw `SecondaryScrollableTabRow` with a private `sectionTint` helper and no divider.
3. **Unused design token:** `Dimens.TabHeight` (48.dp in `ui/designsystem/Dimens.kt:29`) has zero usages anywhere in the codebase.

## 2. Goals & Acceptance Criteria

- [x] **Criterion 1 (Shared component):** A `HesabyarTabRow` composable exists in `ui/components/` using `Dimens.TabHeight`. It supports both fixed (`TabRow`) and scrollable (`SecondaryScrollableTabRow`) modes with theme-driven selected/unselected text style and indicator.
- [x] **Criterion 2 (Migration):** Both raw tab-row call sites migrate to `HesabyarTabRow`. No raw `TabRow` or `SecondaryScrollableTabRow` remains in app source.
- [x] **Criterion 3 (DebtHub API stable):** `internal fun DebtHubTabBar(section, onSectionChange)` keeps its signature. `DebtHubTabBarTest` passes unchanged.
- [x] **Criterion 4 (Tests):** New `HesabyarTabRowTest` covers the fixed branch, the scrollable branch, and selection state. All existing tab tests keep passing.
- [x] **Criterion 5 (Static checks):** `ktlintCheck` and `detekt` report 0 findings.

## 3. Implementation Steps

### Phase 1: Create `HesabyarTabRow`

Create `app/src/main/java/io/github/mojri/hesabyar/ui/components/HesabyarTabRow.kt`. Follow the `HesabyarChip.kt` minimal style. Use English KDoc with short sentences.

Signature:

```kotlin
@Composable
fun HesabyarTabRow(
  selectedTabIndex: Int,
  modifier: Modifier = Modifier,
  scrollable: Boolean = false,
  edgePadding: Dp = SpacingTokens.md,
  containerColor: Color = MaterialTheme.colorScheme.surface,
  contentColor: Color = MaterialTheme.colorScheme.primary,
  showDivider: Boolean = true,
  tabs: @Composable RowScope.() -> Unit
)
```

Behavior:

- When `scrollable = false`, call M3 `TabRow` with `selectedTabIndex`, `containerColor`, `contentColor`, the default primary indicator, the divider slot, and `tabs`.
- When `scrollable = true`, call M3 `SecondaryScrollableTabRow` with `selectedTabIndex`, `edgePadding`, the same colors, the default secondary indicator, the divider slot, and `tabs`.
- Height: apply `modifier.heightIn(min = Dimens.TabHeight)` on the row. This enforces `TabHeight` (48.dp) as the minimum height for single-line / horizontal tabs while allowing taller vertically-stacked icon-plus-text tabs (e.g., M3 72.dp tabs in SmartAssistant) to render without vertical clipping.
- Divider: single spec. When `showDivider = true`, emit `HorizontalDivider(color = MaterialTheme.colorScheme.surfaceContainer, thickness = Dimens.DividerThickness)`. When false, emit an empty `divider = {}`. Do not expose a raw divider slot.
- Indicator: do not expose an indicator parameter. Use the M3 default per branch. Both render in `contentColor` (primary). Rationale: scrollable rows use the shorter secondary pill per the M3 idiom. Unify the color from the theme, keep the shape per variant.
- `edgePadding` applies only in the scrollable branch. Document this in KDoc. The default `SpacingTokens.md` matches the current DebtHub call.
- Do not hardcode colors. Do not use magic alphas.

Shared tab content spec, applied at the call sites:

- Text style `MaterialTheme.typography.bodyMedium` in both states.
- Selected: `fontWeight = FontWeight.Bold`, `selectedContentColor = MaterialTheme.colorScheme.primary`.
- Unselected: `fontWeight = FontWeight.Normal`, `unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant`.
- Icon size `Dimens.IconAvatarMedium` (20.dp). The icon tint inherits the tab content color. Do not pass a separate tint. This deletes DebtHub `sectionTint`.

Visual changes to state in the PR:

- The SmartAssistant unselected tab changes from Bold to Normal. This is the consistency fix.
- DebtHub gains a 1.dp `surfaceContainer` divider, per the user decision for a divider everywhere.
- DebtHub tab text gains an explicit `bodyMedium` style. DebtHub tab icons gain an explicit 20.dp size.

### Phase 2: Migrate DebtHubScreen

In `app/src/main/java/io/github/mojri/hesabyar/ui/screens/DebtHubScreen.kt`, keep the `internal fun DebtHubTabBar(section: DebtSection, onSectionChange: (DebtSection) -> Unit)` signature exactly. `DebtHubTabBarTest` and `DebtHubScreen` call it.

New body sketch:

```kotlin
HesabyarTabRow(
  selectedTabIndex = DebtSection.entries.indexOf(section),
  scrollable = true,
  edgePadding = SpacingTokens.md,
  showDivider = true
) {
  DebtSection.entries.forEach { s ->
    val selected = s == section
    LeadingIconTab(
      selected = selected,
      onClick = { onSectionChange(s) },
      text = { Text(s.label, fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal, style = MaterialTheme.typography.bodyMedium) },
      icon = { Icon(s.icon, null, Modifier.size(Dimens.IconAvatarMedium)) },
      selectedContentColor = MaterialTheme.colorScheme.primary,
      unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
    )
  }
}
```

- Delete the private `sectionTint` function (lines 27-33). Grep shows no other caller.
- Remove the imports for `SecondaryScrollableTabRow` and `Color`. Add the imports for `HesabyarTabRow`, `Dimens`, and `size`.

### Phase 3: Migrate SmartAssistantScreen

In `app/src/main/java/io/github/mojri/hesabyar/ui/screens/SmartAssistantScreen.kt`, keep the local `activeTab` state. Make no signature change.

New call sketch:

```kotlin
HesabyarTabRow(selectedTabIndex = activeTab) {
  Tab(
    selected = activeTab == 0,
    onClick = { activeTab = 0 },
    text = { Text("ثبت هوشمند تراکنش", fontWeight = if (activeTab == 0) FontWeight.Bold else FontWeight.Normal, style = MaterialTheme.typography.bodyMedium) },
    icon = { Icon(Icons.Filled.ReceiptLong, null, Modifier.size(Dimens.IconAvatarMedium)) },
    selectedContentColor = MaterialTheme.colorScheme.primary,
    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
  )
  // second Tab, same pattern with activeTab == 1 and AutoAwesome icon
}
```

- `Dimens` is already imported at line 55. Remove the imports for `TabRow` and `HorizontalDivider` if they are unused elsewhere. Add the import for `HesabyarTabRow`.
- Replace the `Modifier.size(20.dp)` magic number with `Dimens.IconAvatarMedium`. Check whether the `dp` import becomes unused.
- The Persian labels stay hardcoded, out of scope per the user decision.

### Phase 4: Unit and UI Tests

Create `app/src/test/java/io/github/mojri/hesabyar/ui/components/HesabyarTabRowTest.kt`. Use a PascalCase file name and camelCase test names. Follow the Robolectric pattern from `HesabyarButtonTest` (`@RunWith(RobolectricTestRunner::class)`, `@Config(manifest = Config.NONE, sdk = [34])`, `createComposeRule()`).

Tests to write:

1. `fixedTabRowRendersTabsAndEmitsClick` — two `Tab` items, assert displayed, click the second, assert the callback with message-first `assertEquals`.
2. `scrollableTabRowRendersTabsAndEmitsClick` — the same with `scrollable = true` to cover the `SecondaryScrollableTabRow` branch.
3. `selectedTabUpdatesSelectionState` — stateful index, click the second tab, `waitForIdle`, `onNodeWithText(...).assertIsSelected()`. This mirrors `DebtHubTabBarTest`.

Do not assert pixel height in Robolectric. Verify the height by code review (the `Dimens.TabHeight` reference) plus a manual light and dark screenshot.

Existing tests that must keep passing, run unchanged:

- `app/src/test/java/io/github/mojri/hesabyar/ui/screens/DebtHubTabBarTest.kt` — all 5 tests.
- `app/src/test/java/io/github/mojri/hesabyar/ui/screens/DebtSectionTest.kt` — sanity check.

## 4. Verification

Run from the repository root. Use `gradlew.bat` on Windows.

```bash
./gradlew.bat ktlintFormat --no-daemon
./gradlew.bat ktlintCheck detekt --no-daemon
./gradlew.bat testDebugUnitTest --tests "io.github.mojri.hesabyar.ui.components.HesabyarTabRowTest" --tests "io.github.mojri.hesabyar.ui.screens.DebtHubTabBarTest" --tests "io.github.mojri.hesabyar.ui.screens.DebtSectionTest"
./gradlew.bat testDebugUnitTest
```

1. `ktlintFormat`, then `ktlintCheck detekt`. Expect BUILD SUCCESSFUL and 0 findings. Fix on touch, no re-baseline.
2. Targeted tests. Expect BUILD SUCCESSFUL. Collect the raw `<testcase>` lines for the new tests plus the 5 DebtHub names.
3. Full fast suite. Expect BUILD SUCCESSFUL with 0 failures. This is needed because both screens are touched.
4. Manual check: a light and dark theme screenshot of both tab bars, the smallest width, and the RTL order. Confirm there is no clipping at 48.dp.

Evidence standard per `AGENTS.md`: paste the exact changed blocks, the raw `<testcase>` lines with test names, and the exact file paths with line numbers. Do not report "passed" without them.

## 5. Edge Cases

- RTL: both branches inherit RTL from the composition. `SecondaryScrollableTabRow` scroll direction flips automatically. Verify the Persian label order on a device. Do not force LTR.
- Fixed versus scrollable: a fixed `TabRow` distributes the two SmartAssistant tabs equally. The scrollable DebtHub row keeps `edgePadding = SpacingTokens.md`. Do not use fixed mode for DebtHub. Three icon tabs may overflow narrow screens.
- Height clipping: icon-plus-text tabs at 48.dp are tight versus the M3 default. If they clip, use the `heightIn(min)` fallback and note it in the PR.
- Accessibility: `Tab` and `LeadingIconTab` publish the selected state. The `assertIsSelected` tests cover this. The icon `contentDescription = null` stays because the text labels the tab.