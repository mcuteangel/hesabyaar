# Plan 023: Consolidate AiResultCard, Extract MarkdownText, and Single-Screen Budget Advice

- **Issue:** #356
- **PR:** —
- **Created:** 2026-10-24
- **Author:** Claude Code
- **Status:** IN PROGRESS

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

## 1. Problem Statement

Issue #356 and the UI/UX audit (`hesabyar-ux-audit.md` items A10, H5, and C3) identify four architectural and UI duplication problems in the AI presentation layer:

1. **Duplicated AI Loading/Error/Success State Rendering:**
   - Loading, Error (with retry), and Success states for AI results are implemented separately across 4 locations:
     - `ReportsScreen.kt:383-500` (budget advice card)
     - `SmartAssistantScreen.kt:487-660` (budget advice tab)
     - `SmartForecastCard.kt:84-135` (dashboard forecast preview)
     - `ForecastDetailDialog.kt:47-121` (forecast detail dialog)
   - Each implements its own `CircularProgressIndicator`, error message text, retry button styling, and layout wrappers, leading to visual inconsistency and code duplication.

2. **`MarkdownText` Defined in a Screen File Instead of Design System Components:**
   - `MarkdownText` (and its helper `parseBoldMarkdown`) is defined at the bottom of `ui/screens/SmartAssistantScreen.kt:720-805`.
   - It is imported and used by 3 different files across screens and dialogs:
     - `ui/screens/SmartAssistantScreen.kt`
     - `ui/screens/ReportsScreen.kt`
     - `ui/screens/dashboard/dialogs/ForecastDetailDialog.kt`
   - Placing a shared presentation component inside a specific screen violates modular design and component boundaries.

3. **Duplicate "Smart Analysis" (Advice) Screen Presence:**
   - Full budget advice (`advisorState`) is rendered in both `ReportsScreen.kt` and `SmartAssistantScreen.kt` with different layouts but identical underlying data and state.
   - Users encounter two disparate presentations of the same feature. State changes (refreshing or clearing advice) in one screen cause unexpected side effects in the other.
   - Product decision: Consolidate full budget advice rendering in `ReportsScreen.kt` (the analytical hub of the app). `SmartAssistantScreen.kt` will display a clear referral banner/card directing the user to Reports via `onNavigateToReports()`.

4. **Non-Reactive Direct `.value` Reads from ViewModels in Composables:**
   - In `ReportsScreen.kt:400-405,464-469,512-517`, `SmartAssistantScreen.kt:534-539,606-611,642-647,693-698`, and `DashboardScreen.kt:174-179,344-349`:
     Composables directly access `.value` properties on `dashboardViewModel` (`transactions.value`, `loans.value`, `installments.value`, `categories.value`, `bankLoans.value`) and `aiAssistantViewModel.isOnlineMode.value` to pass them into `fetchBudgetAdvice(...)` and `fetchBudgetForecast(...)`.
   - In `SmartAssistantScreen.kt:96,283,353`, `aiAssistantViewModel.isOnlineMode.value` is repeatedly read directly in composable event handlers.
   - Reading `.value` in composables bypasses Compose state observation, introduces race conditions, and forces the UI layer to orchestrate cross-ViewModel data dependencies that belong in the ViewModel/UseCase layer.

---

## 2. Goals & Acceptance Criteria

- [ ] **Criterion 1 (`MarkdownText` in Components):** `MarkdownText` and `parseBoldMarkdown` are extracted to `ui/components/MarkdownText.kt` with support for `#`/`##`/`###` headers, bullet lists (`-`, `*`), numbered lists, bold markdown, and theme styling. Callers in `SmartAssistantScreen.kt`, `ReportsScreen.kt`, and `ForecastDetailDialog.kt` import it from `ui/components/`.
- [ ] **Criterion 2 (Reusable `AiResultCard` and `AiResultContent`):** A shared `AiResultCard<T>` (and inner `AiResultContent<T>`) composable is created in `ui/components/AiResultCard.kt` handling `UiResult.Idle`, `UiResult.Loading`, `UiResult.Success<T>`, and `UiResult.Error` with standard tokens, retry support, and slot customization.
- [ ] **Criterion 3 (Adoption of Shared AI Component Across 4 Sites):**
  - `ReportsScreen.kt` uses `AiResultCard` for the budget advice card.
  - `ForecastDetailDialog.kt` uses `AiResultContent` / `AiResultCard` for forecast results.
  - `SmartForecastCard.kt` adopts standardized loading, error, and preview states.
  - `SmartAssistantScreen.kt` uses the shared component / standardized state rendering.
- [ ] **Criterion 4 (Single-Screen Advice Rendering):** Full interactive budget advice is rendered solely in `ReportsScreen.kt`. `SmartAssistantScreen.kt` Tab 1 provides an informative referral card with navigation to Reports (`onNavigateToReports()`), avoiding dual-screen state synchronization issues.
- [ ] **Criterion 5 (Reactive ViewModels & Zero `.value` in Screens):**
  - `AiAssistantViewModel` injects `GetDashboardDataUseCase` to obtain financial data reactively inside its own `viewModelScope.launch` without requiring the UI to pass 6 parameters.
  - `fetchBudgetAdvice(forceRefresh: Boolean = false)` and `fetchBudgetForecast(forceRefresh: Boolean = false)` require no parameter passing from UI composables.
  - `parseSmartSentence(sentence: String)` defaults `isOnlineMode` internally to its own configured state.
  - Verification check `git grep -E 'ViewModel\.[a-zA-Z]+\.value' app/src/main/java/io/github/mojri/hesabyar/ui/screens/ReportsScreen.kt app/src/main/java/io/github/mojri/hesabyar/ui/screens/SmartAssistantScreen.kt` returns exactly 0 matches.
- [ ] **Criterion 6 (Unit Tests & Quality Gates):**
  - Unit tests for `MarkdownTextTest.kt` verifying markdown syntax parsing and formatting.
  - Unit tests for `AiResultCardTest.kt` verifying Idle, Loading, Error (retry callback), and Success states.
  - Unit tests for `AiAssistantViewModel` reactive data fetching in `AiAssistantParseRaceTest.kt` or new `AiAssistantViewModelTest.kt`.
  - Static checks: `./gradlew ktlintCheck detekt --no-daemon` passes with 0 findings (no baseline modifications).
  - Tests: `./gradlew testDebugUnitTest --no-daemon` passes with 0 failures.
  - Docs: `python3 scripts/check_docs.py` passes with 0 broken links.

---

## 3. Implementation Steps

### Phase 1: Extract `MarkdownText` to `ui/components/MarkdownText.kt` <!-- check-docs: planned -->

**Files:**
- Create: `app/src/main/java/io/github/mojri/hesabyar/ui/components/MarkdownText.kt`
- Create: `app/src/test/java/io/github/mojri/hesabyar/ui/components/MarkdownTextTest.kt`
- Modify: `app/src/main/java/io/github/mojri/hesabyar/ui/screens/SmartAssistantScreen.kt`
- Modify: `app/src/main/java/io/github/mojri/hesabyar/ui/screens/ReportsScreen.kt`
- Modify: `app/src/main/java/io/github/mojri/hesabyar/ui/screens/dashboard/dialogs/ForecastDetailDialog.kt`

**Steps:**
- [ ] Move `MarkdownText` and `parseBoldMarkdown` from `SmartAssistantScreen.kt:720-805` into `ui/components/MarkdownText.kt`.
- [ ] Enhance list parsing to support numbered lists (`1. `, `2. `) alongside bullet lists (`-`, `*`) per plan 013 step 7.3 recommendations.
- [ ] Ensure correct RTL alignment (`TextAlign.Start`), typography styles, and theme colors.
- [ ] Update imports in `SmartAssistantScreen.kt`, `ReportsScreen.kt`, and `ForecastDetailDialog.kt`.
- [ ] Write unit tests in `MarkdownTextTest.kt` covering header hierarchy, bullets, numbered lists, and bold parsing.

**Done Criteria:**
- `MarkdownText` lives in `ui/components/`.
- No references to `MarkdownText` remain defined in `SmartAssistantScreen.kt`.
- `MarkdownTextTest` passes.

---

### Phase 2: Create Reusable `AiResultCard` and `AiResultContent` <!-- check-docs: planned -->

**Files:**
- Create: `app/src/main/java/io/github/mojri/hesabyar/ui/components/AiResultCard.kt`
- Create: `app/src/test/java/io/github/mojri/hesabyar/ui/components/AiResultCardTest.kt`

**Steps:**
- [ ] Define `AiResultCard<T>`:
  ```kotlin
  @Composable
  fun <T> AiResultCard(
    state: UiResult<T>,
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    icon: ImageVector = Icons.Filled.AutoAwesome,
    onRetry: (() -> Unit)? = null,
    onRefresh: (() -> Unit)? = null,
    onClose: (() -> Unit)? = null,
    onFetch: (() -> Unit)? = null,
    idleDescription: String? = null,
    idleActionText: String? = null,
    loadingText: String? = null,
    errorTitle: String? = null,
    idleContent: (@Composable () -> Unit)? = null,
    loadingContent: (@Composable () -> Unit)? = null,
    errorContent: (@Composable (String) -> Unit)? = null,
    content: @Composable (data: T) -> Unit
  )
  ```
- [ ] Define inner/embedded `AiResultContent<T>` composable for use when the caller provides its own container or dialog wrapper (such as `ForecastDetailDialog`).
- [ ] Ensure M3 tokens (`ShapeTokens.XLarge`, `SpacingTokens`, `Dimens`, `MaterialTheme.colorScheme`) are used consistently.
- [ ] Write Robolectric unit tests in `AiResultCardTest.kt` asserting:
  - `UiResult.Idle` displays idle description and triggers `onFetch`.
  - `UiResult.Loading` displays indicator and loading message.
  - `UiResult.Error` displays error message and invokes `onRetry`.
  - `UiResult.Success` renders payload via `content` slot and wires `onRefresh`/`onClose`.

**Done Criteria:**
- `AiResultCard` and `AiResultContent` are in `ui/components/`.
- `AiResultCardTest` passes.

---

### Phase 3: Decouple ViewModel Data Access & Eliminate Non-Reactive `.value` Reads <!-- check-docs: planned -->

**Files:**
- Modify: `app/src/main/java/io/github/mojri/hesabyar/ui/AiAssistantViewModel.kt`
- Modify: `app/src/main/java/io/github/mojri/hesabyar/ui/screens/ReportsScreen.kt`
- Modify: `app/src/main/java/io/github/mojri/hesabyar/ui/screens/SmartAssistantScreen.kt`
- Modify: `app/src/main/java/io/github/mojri/hesabyar/ui/screens/DashboardScreen.kt`
- Modify: `app/src/test/java/io/github/mojri/hesabyar/ui/AiAssistantParseRaceTest.kt`

**Steps:**
- [ ] Inject `GetDashboardDataUseCase` into `AiAssistantViewModel` constructor (provided in `UseCaseModule.kt`).
- [ ] Refactor `fetchBudgetAdvice(forceRefresh: Boolean = false)`:
  - In `viewModelScope.launch`, collect current lists reactively from `getDashboardDataUseCase.transactions.first()`, `loans.first()`, `installments.first()`, `categories.first()`, and `bankLoans.first()`.
  - Read `isOnlineMode` directly from `manageAiConfigUseCase.isOnlineMode()`.
  - Retain the parameterized overload marked `@VisibleForTesting` or for specific custom invocations if needed.
- [ ] Refactor `fetchBudgetForecast(forceRefresh: Boolean = false)` similarly using `GetDashboardDataUseCase`.
- [ ] Default `isOnlineMode` in `parseSmartSentence(sentence: String, isOnlineMode: Boolean = this.isOnlineMode.value)`.
- [ ] In `ReportsScreen.kt`:
  - Replace calls passing 6 `dashboardViewModel.*.value` parameters with parameterless `aiAssistantViewModel.fetchBudgetAdvice()`.
- [ ] In `SmartAssistantScreen.kt`:
  - Replace `aiAssistantViewModel.isOnlineMode.value` reads with `aiAssistantViewModel.parseSmartSentence(text)`.
- [ ] In `DashboardScreen.kt`:
  - Replace `dashboardViewModel.*.value` parameter passing with `aiAssistantViewModel.fetchBudgetForecast(forceRefresh = true)`.
- [ ] Update `AiAssistantParseRaceTest.kt` to supply `GetDashboardDataUseCase` mock/fake.

**Done Criteria:**
- `git grep -E 'ViewModel\.[a-zA-Z]+\.value' app/src/main/java/io/github/mojri/hesabyar/ui/screens/ReportsScreen.kt app/src/main/java/io/github/mojri/hesabyar/ui/screens/SmartAssistantScreen.kt` returns 0 matches.
- All unit tests pass.

---

### Phase 4: Single-Screen Advice Rendering & Adopt `AiResultCard` Across Sites <!-- check-docs: planned -->

**Files:**
- Modify: `app/src/main/java/io/github/mojri/hesabyar/ui/screens/ReportsScreen.kt`
- Modify: `app/src/main/java/io/github/mojri/hesabyar/ui/screens/SmartAssistantScreen.kt`
- Modify: `app/src/main/java/io/github/mojri/hesabyar/ui/screens/dashboard/dialogs/ForecastDetailDialog.kt`
- Modify: `app/src/main/java/io/github/mojri/hesabyar/ui/screens/dashboard/components/SmartForecastCard.kt`
- Modify: `app/src/main/java/io/github/mojri/hesabyar/MainActivity.kt`

**Steps:**
- [ ] In `ReportsScreen.kt`:
  - Replace custom advice card implementation (lines 317-500) with `AiResultCard`.
  - Pass `budgetState`, `onRefresh = { aiAssistantViewModel.fetchBudgetAdvice(forceRefresh = true) }`, `onClose = { aiAssistantViewModel.clearAdvisorState() }`, `onFetch = { aiAssistantViewModel.fetchBudgetAdvice() }`.
  - In `content`, render `MarkdownText(text = data)`.
- [ ] In `SmartAssistantScreen.kt`:
  - Remove duplicate full advice UI and state machine (lines 486-660).
  - Add `onNavigateToReports: () -> Unit` callback parameter to `SmartAssistantScreen`.
  - In Tab 1 ("مشاور بودجه"), render a referral card explaining that budget advice is integrated into Reports, featuring a "مشاهده گزارش و تحلیل بودجه" button calling `onNavigateToReports()`.
- [ ] In `MainActivity.kt`:
  - Wire `onNavigateToReports = { currentTab = TAB_REPORTS }` in `SmartAssistantScreen`.
- [ ] In `ForecastDetailDialog.kt`:
  - Adopt `AiResultContent` for Loading, Error (with retry), and Success states with `MarkdownText`.
- [ ] In `SmartForecastCard.kt`:
  - Ensure forecast preview aligns with `UiResult` and clean reactive calls.

**Done Criteria:**
- Full advice UI is rendered in exactly 1 screen (`ReportsScreen.kt`).
- `SmartAssistantScreen.kt` navigates to Reports when requested.
- `AiResultCard` / `AiResultContent` is used across all target sites.

---

### Phase 5: Verification, Documentation, and Review <!-- check-docs: planned -->

**Files:**
- Modify: `plans/README.md`
- Modify: `progress.md`

**Steps:**
- [ ] Run `./gradlew ktlintCheck detekt --no-daemon` and fix any formatting/lint issues.
- [ ] Run `./gradlew testDebugUnitTest --no-daemon` to ensure all tests pass.
- [ ] Run `python3 scripts/check_docs.py` to confirm zero broken doc references.
- [ ] Run `git grep -E 'ViewModel\.[a-zA-Z]+\.value' app/src/main/java/io/github/mojri/hesabyar/ui/screens/ReportsScreen.kt app/src/main/java/io/github/mojri/hesabyar/ui/screens/SmartAssistantScreen.kt` to ensure 0 matches.
- [ ] Update `progress.md` with results.

**Done Criteria:**
- All checks pass cleanly without regressions.

---

## 4. Evidence and Verification Plan

Per the repository's Evidence Standard:
1. **Static Analysis & Lint:**
   ```bash
   ./gradlew ktlintCheck detekt --no-daemon
   ```
   Must exit 0 with zero findings.
2. **Unit Tests:**
   ```bash
   ./gradlew testDebugUnitTest --no-daemon
   ```
   Must execute all tests with 0 failures, including new `MarkdownTextTest` and `AiResultCardTest`.
3. **Reactive Check:**
   ```bash
   git grep -E 'ViewModel\.[a-zA-Z]+\.value' app/src/main/java/io/github/mojri/hesabyar/ui/screens/ReportsScreen.kt app/src/main/java/io/github/mojri/hesabyar/ui/screens/SmartAssistantScreen.kt
   ```
   Must output 0 lines.
4. **Docs Drift Check:**
   ```bash
   python3 scripts/check_docs.py
   ```
   Must report `Docs: no broken path references.`.

---

## 5. Risks, Dependencies, and STOP Conditions

- **Risk 1 (Data Flow in ViewModels):** If `GetDashboardDataUseCase` emits empty lists initially on cold startup before Room delivers cached entries, `fetchBudgetAdvice` must wait or read the first non-empty emission or handle empty data gracefully.
  - *Mitigation:* `GetDashboardDataUseCase` flows are backed by Room Flow which emits the initial DB snapshot immediately.
- **Risk 2 (Navigation Between Tabs):** In `MainActivity.kt`, switching `currentTab = TAB_REPORTS` must maintain back-stack consistency or navigation callbacks.
  - *Mitigation:* `MainActivity.kt` manages `currentTab` as a mutable state; switching tab is already standard pattern used by `onNavigateToAssistant`.
- **STOP Condition 1:** If removing `.value` requires breaking public API of other modules or altering Room schemas, stop and report.
- **STOP Condition 2:** If third-party Markdown libraries are proposed, stop and report. Only extend the in-house `MarkdownText`.
- **STOP Condition 3:** If any business logic or calculation is moved into Kotlin instead of Rust Core, stop immediately (violates ADR-001).
