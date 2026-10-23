# Plan 022: [A11y] HesabyarTopBar for Persons & PersonDetailSheet, Heading Semantics, and Emoji Removal

- **Issue:** #361
- **PR:** —
- **Created:** 2026-10-23
- **Author:** Claude Code
- **Status:** TODO

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

## 1. Problem Statement

Issue #361 and the UI/UX audit (`hesabyar-ux-audit.md` findings E4, G7, Step 3.1) identify three interrelated accessibility and design-system issues:

1. **Lack of Shared `HesabyarTopBar` Component & Heading Navigation in Persons Surfaces:**
   - Step 3.1 of `plans/013-ui-ux-overhaul.md` specifies a shared `HesabyarTopBar` (standardized on M3 `TopAppBar` with token styling and RTL-correct back navigation).
   - `PersonsScreen.kt` (embedded inside `DebtHubScreen.kt:85` as the Persons tab) and `PersonDetailSheet.kt:269` (`PersonHeader`) use custom, hand-built headers without standard top-bar or header styling.
   - Per plan 013 Step 3.1 guidance, `PersonsScreen` is embedded under the DebtHub tab row and should not have a duplicate outer `TopAppBar`, while `PersonDetailSheet` requires the shared header treatment with proper heading accessibility.

2. **Missing `heading()` Semantics Across Section Headers and Screen Titles:**
   - In Jetpack Compose, screen readers (such as TalkBack) navigate content hierarchically using the `heading()` semantics property (`Modifier.semantics { heading() }`).
   - In the current codebase, exactly **1** component declares `heading()`: `EmptyState.kt:45` (`Text(..., modifier = Modifier.semantics { heading() })`).
   - `SectionHeader.kt:28` omits `heading()` semantics entirely. Because `SectionHeader` is used across screens (`CategoryManagementScreen.kt:176,190`, `DashboardScreen.kt:207,245`, `ReportsScreen.kt:146,530,640`), TalkBack users cannot jump between sections using standard heading navigation.
   - Standalone screen and section titles in `AnalyticsScreen.kt:64`, `SettingsScreen.kt:343,458,468,518,801`, `SmartAssistantScreen.kt:254,592`, and `PersonDetailSheet.kt:251,294` lack `heading()` semantics.

3. **Literal Emojis in UI Titles and Text:**
   - There are over 20 literal emoji characters in UI strings and titles across the app:
     - `AnalyticsScreen.kt:86,96,126,137,242,337,536,625`: 8 emoji literals in card and section titles (📈, 💰, 👥, 🏦, 📊, 🍩, 📊, 🏦).
     - `SettingsScreen.kt:344,459,469,519,801,1397,1439`: 7 emoji literals in section titles and model lists (⚙️, 🤖, 💾, 📊, 🔍, 📁).
     - `ReportsScreen.kt:233,531,640`: 3 emoji literals (📈, 📊, 📜).
     - `SmartAssistantScreen.kt:254,592`: 2 emoji literals (💡).
     - `InstallmentScreen.kt:423`: 1 emoji literal (📝).
     - `JalaliDateTimePicker.kt:96`: 1 emoji literal (📅).
     - `LoanManagementScreen.kt:442`: 1 emoji literal (📝) (dead code per issue #340).
   - **Impact:** TalkBack pronounces the literal unicode emoji names in English or broken translations (e.g., "chart increasing", "money bag", "gear"), which disrupts Persian screen-reader navigation and clashes with the Material 3 design system where icons should be decorative `Icon` elements (`contentDescription = null`).

---

## 2. Goals & Acceptance Criteria

- [ ] **Criterion 1 (Shared `HesabyarTopBar` Component):** A reusable `HesabyarTopBar` composable is created in `ui/components/HesabyarTopBar.kt`.
  - Built on M3 `TopAppBar` with theme tokens (`surface` container color, `FontWeight.Bold` title, `Icons.Filled.ArrowForward` for RTL back navigation when `onBack != null`).
  - Automatically attaches `Modifier.semantics { heading() }` to the top-bar title `Text`.
  - Exposes an optional `actions: @Composable RowScope.() -> Unit = {}` slot.
- [ ] **Criterion 2 (`SectionHeader` Heading Semantics):** `ui/components/SectionHeader.kt` applies `Modifier.semantics { heading() }` to its title `Text`. All call sites (`CategoryManagementScreen`, `DashboardScreen`, `ReportsScreen`, etc.) immediately become accessible heading stops for TalkBack.
- [ ] **Criterion 3 (Persons & PersonDetailSheet Heading Integration):**
  - `PersonDetailSheet.kt` person title (`personName`) in `PersonHeader` and section title (`"تاریخچه تراکنش‌ها و وام‌ها"`) expose `heading()` semantics.
  - `PersonsScreen.kt` embedded layout conforms to Step 3.1 architecture (no duplicate top app bar below DebtHub tabs; empty state and filter headers exposed with proper accessibility).
- [ ] **Criterion 4 (Heading Semantics Across Core Screens):** Screen titles and section headers in `AnalyticsScreen.kt`, `SettingsScreen.kt`, and `SmartAssistantScreen.kt` expose `heading()` semantics for TalkBack navigation.
- [ ] **Criterion 5 (Emoji Removal):** All 22+ literal emoji characters in `ui/` are removed from titles and labels. Where visual accents are needed, Compose `Icon` or `IconCircle` components with `contentDescription = null` are used.
  - Documented exception: conversational markdown templates in `api/` (`BudgetAdviceGenerator.kt`, `BudgetAdvisor.kt`, `LocalBudgetAdvice.kt`, `LocalBudgetForecast.kt`) generate chat-style markdown advice and are preserved or documented.
- [ ] **Criterion 6 (Automated Verification & Tests):**
  - `HesabyarTopBarTest.kt` verifies title rendering, heading semantics, back button click behavior, and action slots.
  - `SectionHeaderTest.kt` verifies title rendering and heading semantics.
  - An automated test (or static check) confirms `rg '[\x{1F300}-\x{1FAFF}]' app/src/main/java/io/github/mojri/hesabyar/ui` returns 0 matches.
- [ ] **Criterion 7 (Static Checks & Verification):**
  - `./gradlew ktlintCheck detekt --no-daemon` passes with 0 findings; no detekt baseline modifications.
  - `./gradlew testDebugUnitTest --no-daemon` passes with 0 failures.
  - `python3 scripts/check_docs.py` reports 0 broken path references.

---

## 3. Implementation Steps

### Phase 1: Create `HesabyarTopBar` and Add `heading()` to `SectionHeader` <!-- check-docs: planned -->

**Files:**
- Create: `app/src/main/java/io/github/mojri/hesabyar/ui/components/HesabyarTopBar.kt`
- Modify: `app/src/main/java/io/github/mojri/hesabyar/ui/components/SectionHeader.kt:28-33`
- Create: `app/src/test/java/io/github/mojri/hesabyar/ui/components/HesabyarTopBarTest.kt`
- Create: `app/src/test/java/io/github/mojri/hesabyar/ui/components/SectionHeaderTest.kt`

**Step Details:**
1. Create `ui/components/HesabyarTopBar.kt`:
   ```kotlin
   package io.github.mojri.hesabyar.ui.components

   import androidx.compose.foundation.layout.RowScope
   import androidx.compose.material.icons.Icons
   import androidx.compose.material.icons.filled.ArrowForward
   import androidx.compose.material3.ExperimentalMaterial3Api
   import androidx.compose.material3.Icon
   import androidx.compose.material3.IconButton
   import androidx.compose.material3.MaterialTheme
   import androidx.compose.material3.Text
   import androidx.compose.material3.TopAppBar
   import androidx.compose.material3.TopAppBarColors
   import androidx.compose.material3.TopAppBarDefaults
   import androidx.compose.material3.TopAppBarScrollBehavior
   import androidx.compose.runtime.Composable
   import androidx.compose.ui.Modifier
   import androidx.compose.ui.res.stringResource
   import androidx.compose.ui.semantics.heading
   import androidx.compose.ui.semantics.semantics
   import androidx.compose.ui.text.font.FontWeight
   import io.github.mojri.hesabyar.R

   /**
    * Standard top app bar for Hesabyar screens and dialog sheets.
    *
    * Provides consistent typography, RTL navigation icon, and marks the title
    * with [heading] semantics for TalkBack accessibility.
    */
   @OptIn(ExperimentalMaterial3Api::class)
   @Composable
   fun HesabyarTopBar(
     title: String,
     modifier: Modifier = Modifier,
     onBack: (() -> Unit)? = null,
     actions: @Composable RowScope.() -> Unit = {},
     colors: TopAppBarColors = TopAppBarDefaults.topAppBarColors(
       containerColor = MaterialTheme.colorScheme.surface,
       titleContentColor = MaterialTheme.colorScheme.onSurface,
     ),
     scrollBehavior: TopAppBarScrollBehavior? = null,
   ) {
     TopAppBar(
       title = {
         Text(
           text = title,
           style = MaterialTheme.typography.titleLarge,
           fontWeight = FontWeight.Bold,
           modifier = Modifier.semantics { heading() },
         )
       },
       modifier = modifier,
       navigationIcon = {
         if (onBack != null) {
           IconButton(onClick = onBack) {
             Icon(
               imageVector = Icons.Filled.ArrowForward,
               contentDescription = stringResource(R.string.back_navigation_description),
             )
           }
         }
       },
       actions = actions,
       colors = colors,
       scrollBehavior = scrollBehavior,
     )
   }
   ```

2. Update `SectionHeader.kt`:
   Add `Modifier.semantics { heading() }` to the title `Text`:
   ```kotlin
   Text(
     text = title,
     style = MaterialTheme.typography.titleMedium,
     color = MaterialTheme.colorScheme.onSurface,
     modifier = (if (action != null) Modifier.weight(1f) else Modifier).semantics { heading() },
   )
   ```

3. Write Robolectric tests:
   - `HesabyarTopBarTest.kt`: Test title rendering, heading semantics verified via `SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading)`, back navigation button click, and action slot rendering.
   - `SectionHeaderTest.kt`: Test title text, heading semantics, and action button click.

**Done Criteria:**
- `./gradlew testDebugUnitTest --tests "*HesabyarTopBarTest" --tests "*SectionHeaderTest"` passes.

---

### Phase 2: Heading Semantics in `PersonsScreen` and `PersonDetailSheet` <!-- check-docs: planned -->

**Files:**
- Modify: `app/src/main/java/io/github/mojri/hesabyar/ui/screens/PersonDetailSheet.kt:251-256,294-298`
- Modify: `app/src/main/java/io/github/mojri/hesabyar/ui/screens/PersonsScreen.kt:77-100`
- Create/Modify: tests for `PersonDetailSheet` and `PersonsScreen`

**Step Details:**
1. In `PersonDetailSheet.kt`:
   - In `PersonHeader`:
     ```kotlin
     Text(
       text = personName,
       style = MaterialTheme.typography.titleMedium,
       fontWeight = FontWeight.Bold,
       modifier = Modifier.semantics { heading() },
     )
     ```
   - In `PersonSheetContent`: Replace raw `Text("تاریخچه تراکنش‌ها و وام‌ها")` with `SectionHeader(title = "تاریخچه تراکنش‌ها و وام‌ها")`, or apply `Modifier.semantics { heading() }`.
2. In `PersonsScreen.kt`:
   - Verify that the embedded Persons tab does not declare a redundant second `TopAppBar` (respecting plan 013 Step 3.1).
   - Ensure the empty state (`PersonEmptyState`) delegates to `EmptyState` (which already has `heading()`).
3. Add or update tests verifying that TalkBack finds headings for person name and timeline history in `PersonDetailSheet`.

**Done Criteria:**
- `PersonDetailSheet` exposes `heading()` semantics on person name and timeline section.
- Existing tests pass.

---

### Phase 3: Screen and Section Heading Semantics Across Core Screens <!-- check-docs: planned -->

**Files:**
- Modify: `app/src/main/java/io/github/mojri/hesabyar/ui/screens/AnalyticsScreen.kt:64-70,85,95,125,136,241,336,535,624`
- Modify: `app/src/main/java/io/github/mojri/hesabyar/ui/screens/SettingsScreen.kt:343-348,458,468,518,801`
- Modify: `app/src/main/java/io/github/mojri/hesabyar/ui/screens/SmartAssistantScreen.kt:254,592`
- Modify: `app/src/main/java/io/github/mojri/hesabyar/ui/screens/ReportsScreen.kt:233,530`

**Step Details:**
1. `AnalyticsScreen.kt`:
   - Page title `Text("تحلیل و آمار پیشرفته", ...)` at line 64: Add `Modifier.semantics { heading() }`.
   - Card titles (`MonthlyTrendCard`, `CombinedLineChartCard`, `CategoryBreakdownCard`, `DebtorCreditorSummaryCard`, `LoanStatusCard`, `BankLoanStatusCard`): Ensure their titles have `Modifier.semantics { heading() }`.
2. `SettingsScreen.kt`:
   - Migrate section header text nodes (`"تنظیمات عمومی"`, `"تنظیمات هوش مصنوعی (API Settings)"`, `"پشتیبان‌گیری و بازیابی داده‌ها (آفلاین)"`, `"خروجی اکسل (Excel)"`, `"لاگ‌های دیباگ (Debug Logs)"`) to `SectionHeader(title = ...)` or add `Modifier.semantics { heading() }`.
3. `SmartAssistantScreen.kt`:
   - Add `Modifier.semantics { heading() }` to section titles at lines 254 and 592.
4. `ReportsScreen.kt`:
   - Ensure lines 233 and 531 use `SectionHeader` or have `Modifier.semantics { heading() }`. Note that lines 146 and 640 already use `SectionHeader` and will automatically gain `heading()` from Phase 1.

**Done Criteria:**
- All major section headers and screen titles declare `heading()` semantics.
- Verified in Compose tests.

---

### Phase 4: Remove Literal Emojis from UI Strings and Titles <!-- check-docs: planned -->

**Files:**
- Modify: `app/src/main/java/io/github/mojri/hesabyar/ui/screens/AnalyticsScreen.kt`
- Modify: `app/src/main/java/io/github/mojri/hesabyar/ui/screens/SettingsScreen.kt`
- Modify: `app/src/main/java/io/github/mojri/hesabyar/ui/screens/ReportsScreen.kt`
- Modify: `app/src/main/java/io/github/mojri/hesabyar/ui/screens/SmartAssistantScreen.kt`
- Modify: `app/src/main/java/io/github/mojri/hesabyar/ui/screens/InstallmentScreen.kt`
- Modify: `app/src/main/java/io/github/mojri/hesabyar/ui/components/JalaliDateTimePicker.kt`
- Modify: `app/src/main/java/io/github/mojri/hesabyar/ui/screens/LoanManagementScreen.kt`
- Create: `app/src/test/java/io/github/mojri/hesabyar/ui/UiEmojiIntegrityTest.kt`

**Step Details:**
1. Clean emojis in `AnalyticsScreen.kt`:
   - Line 86: `"📈 روند هزینه‌های ماهانه"` → `"روند هزینه‌های ماهانه"`
   - Line 96: `"💰 روند درآمدهای ماهانه"` → `"روند درآمدهای ماهانه"`
   - Line 126: `"👥 خلاصه بدهکاران"` → `"خلاصه بدهکاران"`
   - Line 137: `"🏦 خلاصه طلبکاران"` → `"خلاصه طلبکاران"`
   - Line 242: `"📊 مقایسه درآمد و هزینه"` → `"مقایسه درآمد و هزینه"`
   - Line 337: `"🍩 توزیع هزینه‌ها بر اساس دسته‌بندی"` → `"توزیع هزینه‌ها بر اساس دسته‌بندی"`
   - Line 536: `"📊 وضعیت وام‌ها"` → `"وضعیت وام‌ها"`
   - Line 625: `"🏦 وضعیت وام‌های بانکی"` → `"وضعیت وام‌های بانکی"`
2. Clean emojis in `SettingsScreen.kt`:
   - Line 344: `"⚙️ تنظیمات عمومی"` → `"تنظیمات عمومی"`
   - Line 459: `"🤖 تنظیمات هوش مصنوعی (API Settings)"` → `"تنظیمات هوش مصنوعی (API Settings)"`
   - Line 469: `"💾 پشتیبان‌گیری و بازیابی داده‌ها (آفلاین)"` → `"پشتیبان‌گیری و بازیابی داده‌ها (آفلاین)"`
   - Line 519: `"📊 خروجی اکسل (Excel)"` → `"خروجی اکسل (Excel)"`
   - Line 801: `"🔍 لاگ‌های دیباگ (Debug Logs)"` → `"لاگ‌های دیباگ (Debug Logs)"`
   - Line 1397: `"📁 $family ..."` → `"$family ..."`
   - Line 1439: `"📁 $provider ..."` → `"$provider ..."`
3. Clean emojis in `ReportsScreen.kt`:
   - Line 233: `"📈 تراز عملکرد سود و زیان دوره"` → `"تراز عملکرد سود و زیان دوره"`
   - Line 531: `"📊 سهم دسته‌بندی‌ها از کل هزینه‌ها"` → `"سهم دسته‌بندی‌ها از کل هزینه‌ها"`
   - Line 640: `"📜 تمامی تراکنش‌های ثبت شده (${displayList.size} مورد)"` → `"تمامی تراکنش‌های ثبت شده (${displayList.size} مورد)"`
4. Clean emojis in `SmartAssistantScreen.kt`:
   - Line 254: `"💡 عبارت‌های نمونه جهت آزمایش:"` → `"عبارت‌های نمونه جهت آزمایش:"`
   - Line 592: `"💡 نتایج مشاوره و توصیه‌های بودجه"` → `"نتایج مشاوره و توصیه‌های بودجه"`
5. Clean emojis in other UI files:
   - `InstallmentScreen.kt:423`: `"📝 یادداشت: ..."` → `"یادداشت: ..."`
   - `JalaliDateTimePicker.kt:96`: `"📅 تنظیم تاریخ و ساعت (شمسی):"` → `"تنظیم تاریخ و ساعت (شمسی):"`
   - `LoanManagementScreen.kt:442`: `"📝 تاریخچه بازپرداخت‌ها:"` → `"تاریخچه بازپرداخت‌ها:"`
6. Add `UiEmojiIntegrityTest.kt` in `app/src/test/java/io/github/mojri/hesabyar/ui/`:
   A fast unit test scanning `app/src/main/java/io/github/mojri/hesabyar/ui` to guarantee 0 literal emojis exist in UI code.
7. Document conversational AI markdown outputs in `api/` (`BudgetAdviceGenerator.kt`, `BudgetAdvisor.kt`, `LocalBudgetAdvice.kt`, `LocalBudgetForecast.kt`) as intentional user-facing advisor chat markdown (satisfies the "یا فقط موارد مستند" acceptance criterion).

**Done Criteria:**
- `rg '[\x{1F300}-\x{1FAFF}]' app/src/main/java/io/github/mojri/hesabyar/ui` returns 0 matches.
- `UiEmojiIntegrityTest` passes.

---

### Phase 5: Verification and Quality Gate <!-- check-docs: planned -->

**Files:**
- Run full verification checks on modified and created files.

**Step Details:**
1. Format and lint checks:
   ```bash
   ./gradlew ktlintFormat ktlintCheck detekt --no-daemon
   ```
2. Unit tests:
   ```bash
   ./gradlew testDebugUnitTest --no-daemon
   ```
3. Documentation references check:
   ```bash
   python3 scripts/check_docs.py
   ```

**Done Criteria:**
- 0 ktlint violations, 0 detekt findings.
- All unit tests pass.
- No broken doc links.

---

## 4. Evidence and Verification Plan

Per the repository Evidence Standard (`AGENTS.md` and `docs/agents/testing.md`):

1. **Static Analysis & Lint Evidence:**
   - Command: `./gradlew ktlintCheck detekt --no-daemon`
   - Required Output: `BUILD SUCCESSFUL` with 0 warnings/errors.
2. **Component & Semantics Unit Test Evidence:**
   - Command: `./gradlew testDebugUnitTest --tests "io.github.mojri.hesabyar.ui.components.HesabyarTopBarTest" --tests "io.github.mojri.hesabyar.ui.components.SectionHeaderTest" --tests "io.github.mojri.hesabyar.ui.UiEmojiIntegrityTest"`
   - Required Output: All test cases pass with named JUnit XML `<testcase>` records.
3. **Emoji Grep Evidence:**
   - Command: `python3 -c "import re, pathlib; emoji_re = re.compile(r'[\U0001F300-\U0001FAFF]'); print('Matches:', sum(len(emoji_re.findall(p.read_text(encoding='utf-8'))) for p in pathlib.Path('app/src/main/java/io/github/mojri/hesabyar/ui').rglob('*.kt')))"`
   - Required Output: `Matches: 0`
4. **Docs Verification:**
   - Command: `python3 scripts/check_docs.py`
   - Required Output: `Docs: no broken path references.`

---

## 5. Risks, Dependencies, and STOP Conditions

### Hard Constraints Check
- **Money representation:** No `Float` or `Double` used; money values in `CurrencyFormatter` and elsewhere remain `Long`.
- **Room migrations:** No Room schema changes or migrations involved.
- **Offline & Jalali:** No alterations to offline operation or Jalali calendar logic.
- **Business logic policy (ADR-001):** Pure UI layer (`ui/components/`, `ui/screens/`) enhancement; no new business logic introduced in Kotlin.
- **Coroutines:** No `GlobalScope` usage.

### Dependencies
- Independent of `feat/issue-458-page-scaffold` (Plan 021) and other active worktrees. `HesabyarTopBar` can be consumed either directly or within page scaffolds as needed.
- Aligned with `plans/013-ui-ux-overhaul.md` Step 3.1.

### STOP Conditions
- **STOP if** any existing test fails due to text changes without an obvious 1:1 string match.
- **STOP if** detekt flags cognitive complexity or line count violations that require restructuring beyond minimal helper extraction.
- **STOP if** `HesabyarTopBar` requires API changes conflicting with `HesabyarScaffold`'s `topBar` slot signature.
