# Plan 016: [Design System] Add a Chart Component Family with Theme Tokens & RTL Support (Issue #453)

Status: DONE

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a standardized, theme-aware, and RTL-responsive chart component family (`HesabyarBarChart`, `HesabyarLineChart`, `HesabyarDonutChart`, `HesabyarProgressRing`) with central design tokens in `ui/charts/`, and migrate all 4 hand-drawn `Canvas` blocks in `AnalyticsScreen.kt` and `AccountBreakdownCard.kt` to the new components, resolving root cause #453 and symptom #447.

**Architecture:** A cohesive package `io.github.mojri.hesabyar.ui.charts` containing central design tokens (`ChartTokens.kt`), immutable data models (`ChartModel.kt`), and four reusable Jetpack Compose components. All components consume Material 3 theme colors (`primary`, `error`, `surfaceVariant`, `onSurfaceVariant`), respect `LocalLayoutDirection` for horizontal mirroring in Persian RTL layout (so chronological sequences flow right-to-left matching surrounding text), and maintain strict type safety with `Long` for monetary values.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Android Canvas / DrawScope, Robolectric 4.x, JUnit 4, Gradle, Detekt, ktlint.

---

## Global Constraints

- Android & Jetpack Compose Material 3 standards.
- Money values must use `Long` (Rial) or `BigDecimal`. Do not use `Float` or `Double` for financial amounts (ratios, sweep angles, and visual coordinates use `Float` as required by Compose graphics).
- Follow `docs/agents/code-guidelines.md` (DRY, semantic tokens, no magic numbers, PascalCase test classes, camelCase test methods).
- RTL-First: All chart horizontal axes must mirror when `LocalLayoutDirection.current == LayoutDirection.Rtl`, placing the earliest data item on the right and progressing leftwards to align with Persian text reading direction.
- Follow `docs/agents/testing.md` and `docs/agents/detekt.md` (pass `./gradlew ktlintCheck detekt`, never re-baseline detekt).
- Evidence standard: provide exact file paths, real code, and raw test output for completion reports.

---

## Problem Statement

Currently, the analytics screen (`app/src/main/java/io/github/mojri/hesabyar/ui/screens/AnalyticsScreen.kt`) relies on four independent, hardcoded `Canvas` blocks with no shared component, no centralized color/size tokens, and no RTL layout support:

1. `CombinedLineChartCard` (`AnalyticsScreen.kt:256-333`): Hand-drawn income vs expense line chart with hardcoded dimensions (160dp height, 4f stroke, 6f circle radius), hardcoded text paint (`android.graphics.Color.GRAY`, 24f size), and fixed LTR coordinate layout (`startX + idx * spacing`).
2. `BarChart` (`AnalyticsScreen.kt:364-395`): Hand-drawn bar chart with hardcoded nativeCanvas paint, hardcoded corner radius (8f), and fixed LTR bar positioning. Because the Compose `Row` legend below it (`AnalyticsScreen.kt:200-221`) lays out RTL (first month on the right), the chart bars and the legend text are visually inverted (reported in #447).
3. `DonutChart` (`AnalyticsScreen.kt:470-494`): Hand-drawn arc segments with hardcoded 40f stroke width. It is also directly imported and reused by `AccountBreakdownCard.kt:26,49` without theme-aware sizing or empty-state handling.
4. `CircularProgress` (`AnalyticsScreen.kt:884-914`): Hand-drawn animated circular progress ring with hardcoded 12f stroke width and hardcoded `Color.LightGray.copy(alpha = 0.3f)` track color.

There is no central chart design system or reusable chart component library in the codebase.

---

## Goals and Acceptance Criteria

- [ ] **AC-1 (Central Design Tokens):** Create `ChartTokens.kt` defining standardized dimensions (heights, stroke widths, corner radii, point radii, text sizes) and default theme color mappings.
- [ ] **AC-2 (Data Models):** Create `ChartModel.kt` with strongly-typed, immutable models (`BarEntry`, `ChartPoint`, `ChartSeries`, `DonutSlice`) enforcing `Long` for all monetary amounts.
- [ ] **AC-3 (RTL-Aware Bar Chart):** `HesabyarBarChart` renders bars with rounded corners, theme-aware axis labels, and automatic RTL layout mirroring (earliest item placed on the right when `LayoutDirection == Rtl`).
- [ ] **AC-4 (RTL-Aware Line Chart):** `HesabyarLineChart` renders single or multi-series lines with point markers, theme-aware axis labels, and automatic RTL layout mirroring.
- [ ] **AC-5 (Donut Chart Component):** `HesabyarDonutChart` renders proportional arcs with customizable stroke width, graceful empty/zero handling, and semantic content descriptions.
- [ ] **AC-6 (Progress Ring Component):** `HesabyarProgressRing` renders an animated circular progress ring using theme tokens (`primary` on `surfaceVariant` or subtle container), replacing `CircularProgress`.
- [ ] **AC-7 (Clean Migration):** All 4 hand-drawn `Canvas` blocks in `AnalyticsScreen.kt` and the `DonutChart` in `AccountBreakdownCard.kt` are migrated to the new components. Exactly 0 raw `Canvas` blocks remain in `AnalyticsScreen.kt`.
- [ ] **AC-8 (Zero Quality Regressions):** Full test suite (`testDebugUnitTest`), ktlint (`ktlintCheck`), detekt (`detekt`), and doc checks (`scripts/check_docs.py`) pass cleanly.

---

## File Structure <!-- check-docs: planned -->

- **Create:**
  - `app/src/main/java/io/github/mojri/hesabyar/ui/charts/ChartTokens.kt`
    - Design tokens for chart heights, stroke widths, corner radii, point radii, padding, and text styling.
  - `app/src/main/java/io/github/mojri/hesabyar/ui/charts/ChartModel.kt`
    - Core data classes: `BarEntry`, `ChartPoint`, `ChartSeries`, `DonutSlice`.
  - `app/src/main/java/io/github/mojri/hesabyar/ui/charts/HesabyarBarChart.kt`
    - RTL-aware bar chart component with X-axis labels and theme color integration.
  - `app/src/main/java/io/github/mojri/hesabyar/ui/charts/HesabyarLineChart.kt`
    - RTL-aware multi-series line chart with data points, connecting lines, and X-axis labels.
  - `app/src/main/java/io/github/mojri/hesabyar/ui/charts/HesabyarDonutChart.kt`
    - Theme-aware donut chart component with empty state support.
  - `app/src/main/java/io/github/mojri/hesabyar/ui/charts/HesabyarProgressRing.kt`
    - Theme-aware animated circular progress ring component.
  - `app/src/test/java/io/github/mojri/hesabyar/ui/charts/ChartTokensTest.kt`
    - Unit tests validating chart token values and constraints.
  - `app/src/test/java/io/github/mojri/hesabyar/ui/charts/HesabyarBarChartTest.kt`
    - Robolectric tests for bar chart rendering, RTL order inversion, and empty states.
  - `app/src/test/java/io/github/mojri/hesabyar/ui/charts/HesabyarLineChartTest.kt`
    - Robolectric tests for line chart rendering, multi-series data, and RTL point positioning.
  - `app/src/test/java/io/github/mojri/hesabyar/ui/charts/HesabyarDonutChartTest.kt`
    - Robolectric tests for donut chart arc sweeps and zero-total handling.
  - `app/src/test/java/io/github/mojri/hesabyar/ui/charts/HesabyarProgressRingTest.kt`
    - Robolectric tests for progress ring animations, values, and progress semantics.
- **Modify:**
  - `app/src/main/java/io/github/mojri/hesabyar/ui/screens/AnalyticsScreen.kt`
    - Replace the 4 `Canvas` blocks with `HesabyarBarChart`, `HesabyarLineChart`, `HesabyarDonutChart`, and `HesabyarProgressRing`.
  - `app/src/main/java/io/github/mojri/hesabyar/ui/screens/dashboard/components/AccountBreakdownCard.kt`
    - Migrate from `io.github.mojri.hesabyar.ui.screens.DonutChart` to `HesabyarDonutChart`.
  - `docs/architecture/ARCHITECTURE.md`
    - Add `ui/charts/` to the design system directory structure and list the chart components.

---

## Implementation Phases

### Phase 1: Foundation — Design Tokens and Data Models <!-- check-docs: planned -->

**Files:**
- Create: `app/src/main/java/io/github/mojri/hesabyar/ui/charts/ChartTokens.kt`
- Create: `app/src/main/java/io/github/mojri/hesabyar/ui/charts/ChartModel.kt`
- Create: `app/src/test/java/io/github/mojri/hesabyar/ui/charts/ChartTokensTest.kt`

**Interfaces:**

```kotlin
package io.github.mojri.hesabyar.ui.charts

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object ChartTokens {
  val DefaultHeight: Dp = 180.dp
  val LineChartHeight: Dp = 160.dp
  val DonutChartHeight: Dp = 200.dp
  val ProgressRingSize: Dp = 100.dp

  val BarCornerRadius: Dp = 4.dp
  val LineStrokeWidth: Dp = 2.dp
  val PointRadius: Dp = 3.dp
  val DonutStrokeWidth: Dp = 20.dp
  val ProgressStrokeWidth: Dp = 6.dp

  val AxisLabelSize: TextUnit = 11.sp
  val LabelPaddingBottom: Dp = 4.dp
  val ContentPadding: Dp = 12.dp
}

data class BarEntry(
  val label: String,
  val value: Long,
  val color: Color? = null,
)

data class ChartPoint(
  val label: String,
  val value: Long,
)

data class ChartSeries(
  val name: String,
  val color: Color,
  val points: List<ChartPoint>,
)

data class DonutSlice(
  val label: String,
  val value: Long,
  val color: Color,
  val percentage: Float = 0f,
)
```

- [ ] **Step 1:** Write `ChartTokensTest.kt` verifying tokens match specifications and values are positive.
- [ ] **Step 2:** Run `ChartTokensTest` to confirm failure (unresolved references).
- [ ] **Step 3:** Implement `ChartTokens.kt` and `ChartModel.kt`.
- [ ] **Step 4:** Re-run `ChartTokensTest` to confirm pass.
- [ ] **Step 5:** Commit: `feat(charts): add chart design tokens and data models (#453)`

---

### Phase 2: Bar & Line Chart Components with RTL Support <!-- check-docs: planned -->

**Files:**
- Create: `app/src/main/java/io/github/mojri/hesabyar/ui/charts/HesabyarBarChart.kt`
- Create: `app/src/main/java/io/github/mojri/hesabyar/ui/charts/HesabyarLineChart.kt`
- Create: `app/src/test/java/io/github/mojri/hesabyar/ui/charts/HesabyarBarChartTest.kt`
- Create: `app/src/test/java/io/github/mojri/hesabyar/ui/charts/HesabyarLineChartTest.kt`

**RTL Layout Logic:**
When `LocalLayoutDirection.current == LayoutDirection.Rtl`:
- The item at index 0 (earliest chronological item) is rendered on the right side of the canvas:
  `val effectiveIndex = if (isRtl) (count - 1 - index) else index`
- The X coordinate calculation:
  `val x = startPadding + effectiveIndex * (barWidth + spacing) + barWidth / 2`
- This ensures visual alignment with Compose `Row` elements that lay out children from right to left in Persian RTL locale.

**Interfaces:**

```kotlin
@Composable
fun HesabyarBarChart(
  entries: List<BarEntry>,
  modifier: Modifier = Modifier,
  defaultColor: Color = MaterialTheme.colorScheme.primary,
  labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
)

@Composable
fun HesabyarLineChart(
  series: List<ChartSeries>,
  modifier: Modifier = Modifier,
  labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
)
```

- [ ] **Step 1:** Write failing Robolectric tests in `HesabyarBarChartTest.kt` verifying:
  - Rendering with test tags and content descriptions.
  - LTR vs RTL bar position ordering (index 0 on right in RTL).
  - Empty entry list rendering without crash.
- [ ] **Step 2:** Write failing Robolectric tests in `HesabyarLineChartTest.kt` verifying:
  - Single and multi-series line rendering.
  - LTR vs RTL point coordinate order (index 0 on right in RTL).
  - Empty series rendering without crash.
- [ ] **Step 3:** Implement `HesabyarBarChart.kt` and `HesabyarLineChart.kt` consuming `ChartTokens`.
- [ ] **Step 4:** Run `./gradlew testDebugUnitTest --tests "*BarChartTest*" --tests "*LineChartTest*"` and confirm PASS.
- [ ] **Step 5:** Commit: `feat(charts): add RTL-aware HesabyarBarChart and HesabyarLineChart (#453)`

---

### Phase 3: Donut Chart & Progress Ring Components <!-- check-docs: planned -->

**Files:**
- Create: `app/src/main/java/io/github/mojri/hesabyar/ui/charts/HesabyarDonutChart.kt`
- Create: `app/src/main/java/io/github/mojri/hesabyar/ui/charts/HesabyarProgressRing.kt`
- Create: `app/src/test/java/io/github/mojri/hesabyar/ui/charts/HesabyarDonutChartTest.kt`
- Create: `app/src/test/java/io/github/mojri/hesabyar/ui/charts/HesabyarProgressRingTest.kt`

**Interfaces:**

```kotlin
@Composable
fun HesabyarDonutChart(
  slices: List<DonutSlice>,
  modifier: Modifier = Modifier,
  strokeWidth: Dp = ChartTokens.DonutStrokeWidth,
  emptyTrackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
)

@Composable
fun HesabyarProgressRing(
  progress: Float,
  modifier: Modifier = Modifier,
  color: Color = MaterialTheme.colorScheme.primary,
  trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
  strokeWidth: Dp = ChartTokens.ProgressStrokeWidth,
  animated: Boolean = true,
)
```

- [ ] **Step 1:** Write failing tests in `HesabyarDonutChartTest.kt` (arc sweeps, zero values, empty list).
- [ ] **Step 2:** Write failing tests in `HesabyarProgressRingTest.kt` (progress clamping between 0f and 1f, semantics value, animation configuration).
- [ ] **Step 3:** Implement `HesabyarDonutChart.kt` and `HesabyarProgressRing.kt`.
- [ ] **Step 4:** Run `./gradlew testDebugUnitTest --tests "*DonutChartTest*" --tests "*ProgressRingTest*"` and confirm PASS.
- [ ] **Step 5:** Commit: `feat(charts): add HesabyarDonutChart and HesabyarProgressRing (#453)`

---

### Phase 4: Migration of `AnalyticsScreen.kt` & `AccountBreakdownCard.kt`

**Files:**
- Modify: `app/src/main/java/io/github/mojri/hesabyar/ui/screens/AnalyticsScreen.kt`
- Modify: `app/src/main/java/io/github/mojri/hesabyar/ui/screens/dashboard/components/AccountBreakdownCard.kt`

**Changes:**
1. In `AnalyticsScreen.kt`:
   - In `CombinedLineChartCard`: Replace raw `Canvas(modifier = ... height(160.dp))` with `HesabyarLineChart` mapping `incomeValues` and `spendValues` to `ChartSeries`.
   - In `MonthlyTrendCard`: Replace private `BarChart` with `HesabyarBarChart` mapping `data` to `BarEntry`.
   - In `CategoryBreakdownCard`: Replace internal `DonutChart` with `HesabyarDonutChart` mapping `CategoryBreakdown` to `DonutSlice`.
   - In `InstallmentProgressCard`: Replace private `CircularProgress` with `HesabyarProgressRing`.
   - Delete obsolete private functions `BarChart` and `CircularProgress`.
   - Deprecate or remove private/internal `DonutChart` in `AnalyticsScreen.kt`.
   - Clean up imports (`androidx.compose.foundation.Canvas`, `android.graphics.Paint`, `nativeCanvas`).
2. In `AccountBreakdownCard.kt`:
   - Replace import `io.github.mojri.hesabyar.ui.screens.DonutChart` with `HesabyarDonutChart` from `io.github.mojri.hesabyar.ui.charts`.
   - Map `accountBreakdown` to `DonutSlice` list.

- [ ] **Step 1:** Migrate `CombinedLineChartCard`, `MonthlyTrendCard`, `CategoryBreakdownCard`, and `InstallmentProgressCard`.
- [ ] **Step 2:** Migrate `AccountBreakdownCard.kt`.
- [ ] **Step 3:** Verify 0 raw `Canvas(` calls remain in `AnalyticsScreen.kt`:
  Run: `git grep "Canvas(" app/src/main/java/io/github/mojri/hesabyar/ui/screens/AnalyticsScreen.kt`
  Expected: 0 matches.
- [ ] **Step 4:** Run all analytics and dashboard unit tests:
  Run: `./gradlew testDebugUnitTest --tests "*Analytics*" --tests "*Dashboard*"`
  Expected: PASS.
- [ ] **Step 5:** Commit: `refactor(ui): migrate AnalyticsScreen and AccountBreakdownCard to chart system (#453)`

---

### Phase 5: Verification, Static Analysis & Documentation Sync

**Files:**
- Modify: `docs/architecture/ARCHITECTURE.md`
- Verify all modified and created files.

- [ ] **Step 1:** Run ktlint check and auto-format:
  `./gradlew ktlintFormat ktlintCheck --no-daemon`
  Expected: PASS with 0 violations.
- [ ] **Step 2:** Run Detekt static analysis:
  `./gradlew detekt --no-daemon`
  Expected: BUILD SUCCESSFUL (0 new findings).
- [ ] **Step 3:** Run full unit test suite:
  `./gradlew testDebugUnitTest --no-daemon`
  Expected: BUILD SUCCESSFUL, all unit tests pass.
- [ ] **Step 4:** Run documentation path checker:
  `python3 scripts/check_docs.py`
  Expected: PASS with 0 broken references.
- [ ] **Step 5:** Update `docs/architecture/ARCHITECTURE.md` to document the `ui/charts/` package and its 4 components.
- [ ] **Step 6:** Commit: `docs: document chart component family in architecture guide (#453)`

---

## Evidence and Verification Plan

Per the project's evidence standard (`docs/agents/testing.md`), report completion with:
1. Exact file contents and lines for newly created components in `ui/charts/` and migrated cards in `AnalyticsScreen.kt`.
2. Raw command execution outputs for:
   - `./gradlew ktlintFormat ktlintCheck --no-daemon`
   - `./gradlew detekt --no-daemon`
   - `./gradlew testDebugUnitTest --tests "io.github.mojri.hesabyar.ui.charts.*"`
   - `python3 scripts/check_docs.py`
3. Verification that `git grep "Canvas(" app/src/main/java/io/github/mojri/hesabyar/ui/screens/AnalyticsScreen.kt` yields 0 occurrences.

---

## Risks, Dependencies, and STOP Conditions

### Risks
- **Data Truncation / Overflow:** Financial amounts in Rials can be large numbers (billions). Chart algorithms must compute max values safely using `Long` scaling to normalized floats `[0.0f, 1.0f]` without numerical overflow.
- **RTL Inversion Trap:** In RTL, x-coordinates must mirror so the timeline flows right-to-left. Text labels must remain upright and legible, not mirrored.
- **Zero / Negative Values:** Expense or income might be 0 for certain months. Division by zero must be guarded (`coerceAtLeast(1L)`).

### STOP Conditions
- **STOP if:** Any change requires altering Room entities or Room migrations. (Chart rendering is strictly a UI concern).
- **STOP if:** Money amounts are represented as `Float` or `Double` in public component signatures.
- **STOP if:** A third-party charting library (e.g. MPAndroidChart, Vico) is introduced. The goal is a clean, native Jetpack Compose M3 component family maintaining zero extra dependency bloat.
