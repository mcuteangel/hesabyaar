# Current Progress

> This file tracks the current working state for AI coding agents.
> It is not an architecture specification, changelog, or source of truth.
> Always verify important claims against the current repository and `AGENTS.md`.

## Active Task

Issue #71 — Refactor Category Data Layer to use CategoryType End-to-End (Plan 025)

## Goal

- [x] Await user approval of Plan 025 before execution. (Approved by user)
- [x] Phase 0: Baseline verification & branch setup (`refactor/issue-71-category-type-end-to-end`).
- [x] Phase 1: Data layer refactoring (`CategoryDao`, `CategoryOps`, `CategoryDelegate`).
- [x] Phase 2: Domain layer update (`ManageCategoryUseCase`).
- [x] Phase 3: Test fakes and test suite updates (`FakeRepository` variants, `ManageCategoryUseCaseTest`, `RepositoryLogicTest`).
- [ ] Phase 4: Verification, static analysis (`ktlintCheck`, `detekt`, `testDebugUnitTest`), and evidence collection.

## Completed

- **Task:** Issue #340 — [Tech-debt] Dead code removal: LoanManagementScreen, LoanViewModel, LoanItem, InstallmentItem, and TransactionItem (Plan 024)
- **Status:** Completed and verified (branch `refactor/issue-340-dead-code-removal`)
- **Completed Date:** 2026-10-26
- **Deliverables:**
    - [x] Phase 0: Completed drift audit confirming zero production callers for the 5 dead files.
    - [x] Phase 1: Migrated signed-amount RTL placement tests to `TransactionMiniItemSignPlacementTest.kt` (4/4 tests passing).
    - [x] Phase 2: Deleted dead test files (`LoanManagementDialogTest`, `LoanEditRejectionScreenTest`, `LoanViewModelTest`, and legacy `TransactionItemSignPlacementTest`).
    - [x] Phase 3: Deleted 5 dead production files in `app/src/main` (~1,260 lines) + `LoanEditAmountPreservationTest.kt` in `app/src/test` and decoupled KDoc in `LoanEditCalculator.kt`.
    - [x] Phase 4: Removed 3 unused string keys (`loan_type_debtor`, `loan_type_creditor`, `loan_amount_too_large`) and pruned 16 stale detekt baseline entries.
    - [x] Phase 5: Synchronized `docs/MODULE_MAP.md` architecture table and feature paths.
    - [x] Phase 6: Verified full test suite (`testDebugUnitTest` — 1,244 tests passing, 0 failures), static analysis (`ktlintCheck`, `detekt` — 0 findings), and documentation links (`check_docs.py` — clean).

- **Task:** Issue #356 — Consolidate `AiResultCard`, extract `MarkdownText`, and single-screen budget advice (Plan 023)
- **Status:** Completed and verified (branch `feat/issue-356-ai-result-card-consolidation`)
- **Completed Date:** 2026-10-25
- **Deliverables:**
    - [x] Extracted `MarkdownText` and `parseBoldMarkdown` to `ui/components/MarkdownText.kt` with support for `#`/`##`/`###` headers, bullet lists, numbered lists, bold text, and RTL semantics. Added 7 unit tests in `MarkdownTextTest.kt`.
    - [x] Created reusable `AiResultCard` and `AiResultContent` in `ui/components/AiResultCard.kt` covering `UiResult.Idle`, `Loading`, `Success`, and `Error` with retry/refresh/close actions and M3 tokens. Added 6 unit tests in `AiResultCardTest.kt`.
    - [x] Injected `GetDashboardDataUseCase` into `AiAssistantViewModel`, decoupling ViewModel data loading from Compose `.value` reads. Added unit tests in `AiAssistantViewModelTest.kt` and updated `AiAssistantParseRaceTest.kt`.
    - [x] Verified zero `.value` reads from ViewModels across `ReportsScreen.kt` and `SmartAssistantScreen.kt`.
    - [x] Consolidated full budget advice in `ReportsScreen.kt` using `AiResultCard`. Replaced duplicate advice UI in `SmartAssistantScreen.kt` with a navigation referral card to Reports.
    - [x] Adopted `AiResultContent` in `ForecastDetailDialog.kt`.
    - [x] Verified full test suite (`testDebugUnitTest`), static analysis (`ktlintCheck`, `detekt`), and documentation links (`check_docs.py`) passing with zero errors.

- **Task:** Issue #361 — [A11y] `HesabyarTopBar` for Persons & `PersonDetailSheet`, `heading()` semantics, and emoji removal (Plan 022)
- **Status:** Completed and verified (branch `feat/issue-361-topbar-heading-semantics`, PR #540)
- **Completed Date:** 2026-10-23
- **Deliverables:**
    - [x] Created `HesabyarTopBar` in `ui/components/HesabyarTopBar.kt` with heading semantics, `Icons.AutoMirrored.Filled.ArrowBack`, and actions slot
    - [x] Added `Modifier.semantics { heading() }` to `SectionHeader.kt` and key screens
    - [x] Integrated `HesabyarTopBar` in `PersonDetailSheet` and `PersonsScreen`
    - [x] Removed literal emojis from `ui/` (both supplementary and BMP blocks) and added `UiEmojiIntegrityTest`
    - [x] Added component tests in `HesabyarTopBarTest.kt`, `SectionHeaderTest.kt`, and integration tests in `PersonsScreenTest.kt` and `PersonDetailSheetTest.kt`

- **Task:** Issue #458 — [Design System] Add a page-scaffold standard + adopt WindowSizeTokens (Plan 021)
- **Status:** Completed and verified (branch `feat/issue-458-page-scaffold`)
- **Completed Date:** 2026-10-23
- **Deliverables:**
    - [x] Created `HesabyarScaffold` in `ui/components/HesabyarScaffold.kt` with RTL TopAppBar, FAB slot, automatic bottom FAB clearance, and ContentMaxWidth (840.dp) centering
    - [x] Added Robolectric unit tests in `HesabyarScaffoldTest.kt` covering title, custom topBar, FAB, clearance calculation, and width clamping
    - [x] Migrated `CategoryManagementScreen.kt` and `AccountManagementScreen.kt` to `HesabyarScaffold`, deleting duplicated TopAppBar declarations
    - [x] Migrated `BankLoanScreen.kt` and `DashboardScreen.kt` to `HesabyarScaffold`, preserving `testTag("add_transaction_fab")`
    - [x] Adopted `WindowSizeTokens.ContentMaxWidth` in `ExpandedMainContent` and `CompactMainContent` (`MainNavigationBars.kt`) with tests in `MainNavigationComponentsTest.kt`
    - [x] Full test suite (1204 unit tests) passing, ktlint and detekt reporting zero findings

- **Task:** Issue #457 — Replace 16 raw OutlinedTextField uses with HesabyarInputField (Plan 020)
- **Status:** Implemented and verified (PR #517 open; 1180 tests passing, ktlint/detekt green)
- **Completed Date:** 2026-10-18
- **Deliverables:**
    - [x] Migrated all 16 raw `OutlinedTextField` call sites across 9 files to `HesabyarInputField`
    - [x] Extended `HesabyarInputField` with `leadingIcon`, `readOnly`, `colors`, `TextFieldValue` overload, and composable `supportingText`
    - [x] Converted existing 3 String-form `supportingText` callers to composable lambdas
    - [x] Verified zero raw `OutlinedTextField` usages in `app/src/main` outside `HesabyarInputField.kt`

- **Task:** Issue #455 — Complete icon-size and alpha tokens in Dimens.kt (Plan 018)
- **Status:** Completed and verified (PR #498)
- **Completed Date:** 2026-10-17
- **Deliverables:**
    - [x] `app/src/main/java/io/github/mojri/hesabyar/ui/designsystem/Dimens.kt`: added missing icon and container sizes (`IconXLarge` = 44.dp, `ContainerXSmall` = 28.dp, `ContainerSmall` = 36.dp, `ContainerMedium` = 44.dp), wired bypassed tokens (`IconMenu` = 20.dp, `IconAvatarMedium` = 20.dp), added semantic alpha tokens (`SUBTLE_BRAND_ALPHA` = 0.08f, `CARD_CONTAINER_ALPHA` = 0.10f, `CHIP_CONTAINER_ALPHA` = 0.12f, `BANNER_CONTAINER_ALPHA` = 0.30f, `MUTED_FOREGROUND_ALPHA` = 0.60f, `SELECTED_SURFACE_ALPHA` = 0.25f)
    - [x] Migrated 19 screens and components from raw `.dp` and alpha literals to design tokens
    - [x] `app/src/test/java/io/github/mojri/hesabyar/ui/designsystem/DimensTokenTest.kt`: 4 unit tests verifying tokens and ranges
    - [x] `plans/archive/018-complete-icon-size-and-alpha-tokens.md`: archived completed plan
- **Task:** Plan 017 — [Design System] Add chart system & migrate raw Canvas uses (Issue #453)
- **Status:** Completed and verified
- **Completed Date:** 2026-10-16
- **Deliverables:**
    - [x] `app/src/main/java/io/github/mojri/hesabyar/ui/charts/ChartTokens.kt`: central chart dimensions, stroke widths, corner radii, point radii, padding, and text sizing
    - [x] `app/src/main/java/io/github/mojri/hesabyar/ui/charts/ChartModel.kt`: strongly typed immutable models (`BarEntry`, `ChartPoint`, `ChartSeries`, `DonutSlice`) enforcing `Long` (Rial) for money values
    - [x] `app/src/main/java/io/github/mojri/hesabyar/ui/charts/HesabyarBarChart.kt`: rounded bar chart with automatic RTL coordinate mirroring
    - [x] `app/src/main/java/io/github/mojri/hesabyar/ui/charts/HesabyarLineChart.kt`: multi-series line chart with point markers and RTL coordinate inversion
    - [x] `app/src/main/java/io/github/mojri/hesabyar/ui/charts/HesabyarDonutChart.kt`: proportional arc segments with empty state and zero-total safety
    - [x] `app/src/main/java/io/github/mojri/hesabyar/ui/charts/HesabyarProgressRing.kt`: animated circular progress ring with clamped progress and accessibility semantics
    - [x] `app/src/main/java/io/github/mojri/hesabyar/ui/screens/AnalyticsScreen.kt`: migrated all 4 hand-drawn `Canvas` blocks (`MonthlyTrendCard`, `CombinedLineChartCard`, `CategoryBreakdownCard`, `InstallmentProgressCard`); removed ad-hoc `BarChart`, `DonutChart`, `CircularProgress`
    - [x] `app/src/main/java/io/github/mojri/hesabyar/ui/screens/dashboard/components/AccountBreakdownCard.kt`: migrated to `HesabyarDonutChart`
    - [x] `docs/architecture/ARCHITECTURE.md`: documented `ui/charts/` component family in architecture guide
    - [x] `plans/archive/017-design-system-chart-system.md`: completed architecture plan (DONE in `plans/README.md`)
    - [x] Unit test suites: `ChartTokensTest` (2 tests), `HesabyarBarChartTest` (6 tests), `HesabyarLineChartTest` (7 tests), `HesabyarDonutChartTest` (6 tests), `HesabyarProgressRingTest` (6 tests) — all passed
    - [x] New `AccountBreakdownCardTest` (2 tests: populated and empty breakdown) with `testTag`-capable `modifier` parameter added to `AccountBreakdownCard`
    - [x] Follow-up fixes: negative-value clamping in bar/line/donut charts; `toComposeColor()` ARGB conversion in `AccountBreakdownCard` and `AnalyticsScreen`; progress-ring label computed from `effectiveProgress` with `ProgressBarRangeInfo` semantics; hoisted `Paint` allocations; removed redundant ring size; default line-chart content description

- **Task:** Issue #454 — Add `labelSmall` to `Type.kt`
- **Status:** Completed (merged to `main`, plan archived)
- **Completed Date:** 2026-10-16
- **Deliverables:**
    - [x] `app/src/main/java/io/github/mojri/hesabyar/ui/theme/Type.kt` — added `labelSmall` (Vazirmatn, Medium, 11sp/16sp/0.5sp) after `labelMedium`; all 15 M3 styles now use `VazirmatnFontFamily`
    - [x] `app/src/test/java/io/github/mojri/hesabyar/ui/theme/TypographyTest.kt` — 2 new tests (both pass)
    - [x] `plans/archive/017-add-labelsmall-typography.md` — plan archived (DONE in `plans/README.md`)
    - [x] `.github/workflows/ai-issue-os.yml` — CI fix (commit `92c5bac`): write the 200-issue payload to `issues.json` on disk instead of the `ISSUES_JSON` env var, fixing the `ai_os_v2` E2BIG ("Argument list too long") failure

- **Task:** Issue #452 — Shared HesabyarTabRow
- **Status:** Merged to `main` via PR #483
- **Completed Date:** 2026-10-16
- **Deliverables:**
    - [x] `app/src/main/java/io/github/mojri/hesabyar/ui/components/HesabyarTabRow.kt` — new shared component (TabRow + SecondaryScrollableTabRow, Dimens.TabHeight, theme colors)
    - [x] `app/src/main/java/io/github/mojri/hesabyar/ui/screens/DebtHubScreen.kt` — migrated to HesabyarTabRow, deleted `sectionTint`
    - [x] `app/src/main/java/io/github/mojri/hesabyar/ui/screens/SmartAssistantScreen.kt` — migrated to HesabyarTabRow
    - [x] `app/src/test/java/io/github/mojri/hesabyar/ui/components/HesabyarTabRowTest.kt` — 3 new tests (all pass)
    - [x] `plans/016-shared-tab-row.md` registered in plans/README.md
    - [x] `ktlintCheck detekt` — BUILD SUCCESSFUL, 0 findings
    - [x] Targeted tests `HesabyarTabRowTest` + `DebtHubTabBarTest` + `DebtSectionTest` — 10/10 pass
    - [x] `testDebugUnitTest` full suite — 131 suites, 1136 tests, 0 failures
    - [x] `python scripts/check_docs.py` — no broken references


- **Task:** PR #304 — Plan 013 (UI/UX overhaul in prioritized phases)
- **Status:** Merged to `main`
- **Completed Date:** 2026-10-04
- **Deliverables:**
    - [x] Renamed plan to `plans/013-ui-ux-overhaul.md` and reconciled `plans/README.md`
    - [x] Addressed all review comments from review bots (Kilo, CodeAnt, Cubic, Sourcery)

- **Task:** Issue #231 — Unified test coverage (Kotlin/JaCoCo + Rust core to Codacy)
- **Status:** Merged into `main` via PR #301 (commit `b5b93a8`)
- **Completed Date:** 2026-10-04
- **Deliverables:**
    - [x] `gradle/jacoco-coverage.gradle.kts`: `jacocoReportExcludes` filters generated code; dynamic `handWrittenClassSpec` keeps only hand-written bridge classes in rust package
    - [x] `checkRustBridgeCoverageScope` task in `gradle/jacoco-coverage.gradle.kts`: recursive fail-fast guard over shared `jacocoSourceRoots`
    - [x] `.github/workflows/android-ci.yml`: explicit test tasks and JaCoCo generation
    - [x] `.github/workflows/rust-lint.yml`: Codacy reporter uploads `rust/lcov.prefixed.info`
    - [x] `docs/TEST_COVERAGE.md`: coverage scope documentation for Kotlin and Rust
    - [x] `plans/archive/012-unified-test-coverage-kotlin-rust.md`: completed architecture plan (DONE in `plans/README.md`)

- **Task:** plans/011 Phase 0-2 (person records, tracked/untracked repayment, KPI exclusion)
- **Status:** Completed and merged to `main`
- **Completed Date:** before 2026-09-27
- **Deliverables:** person table + MIGRATION_7_8, `loans.tracked` / `loans.accountId`, D2 KPI exclusion parity, template and binding regeneration

- **Task:** Vibe Coding preparation
- **Status:** Completed with known pre-existing verification failure
- **Completed Date:** 2026-09-25
- **Deliverables:**
    - [x] `AGENTS.md` additions (Architecture Pattern, UI Constraints, Room Migration Checklist, Progress Tracking, Quick-Check Scripts)
    - [x] `rust/hesabyar-core/README.md` (UniFFI procedural macro architecture, canonical money rules, method lifecycle, quick checks)
    - [x] `scripts/check-rust.sh` (clippy + workspace tests)
    - [x] `scripts/check-android.sh` (ktlintCheck + detekt + testDebugUnitTest + lintDebug)
    - [x] `scripts/check-rust-bridge.sh` (testDebugUnitTestRust without `--rerun-tasks` per user decision)
    - [x] Prompt templates in `.github/prompts/` (`new-compose-screen.md`, `new-rust-method.md`, `bugfix-or-db-optimization.md`)
    - [x] Pull request template in `.github/pull_request_template.md`
    - [x] `progress.md` lifecycle documentation and baseline

- **Task:** Issue #285 — JaCoCo 0% Composable Coverage
- **Status:** Completed and verified in CI
- **Completed Date:** 2026-10-02
- **Deliverables:**
    - [x] Root cause analysis: Robolectric sandbox classloader strips source-location metadata from Compose-generated bytecode; JaCoCo excludes classes without metadata by default (`inclnolocationclasses=false`)
    - [x] Gradle configuration: `isIncludeNoLocationClasses = true` configured via `JacocoTaskExtension` on `tasks.withType<Test>().configureEach` in `app/build.gradle.kts`
    - [x] Verified in CI (run 36971068107): overall coverage increased from 34.2% baseline to 44.347% (Coveralls job 188877781)
    - [x] Reverted broken CI-only `GRADLE_OPTS` attempt that crashed the Gradle daemon

**References:**
- [jacoco/jacoco#1208](https://github.com/jacoco/jacoco/issues/1208) (Closed)
- [jacoco/jacoco#1857](https://github.com/jacoco/jacoco/issues/1857) (Closed)
- [jacoco/jacoco#2210](https://github.com/jacoco/jacoco/issues/2210) (Open)
- [gcacace/android-signaturepad#193](https://github.com/gcacace/android-signaturepad/pull/193) (PR example)

- **Task:** plans/011 Phase 3 — Personal loan ledger on branch `feature/person-loan-ledger`
- **Status:** Completed and merged
- **Completed Date:** 2026-10-06
- **Deliverables:**
    - [x] Rust `compute_person_balances` + unit tests (`rust/hesabyar-core/src/models/mod.rs:430`)
    - [x] Rust FFI wrapper (`rust/hesabyar-core/src/ffi/mod.rs`) and UniFFI bindings
    - [x] Bridge façade `RustBridgePersons` and Kotlin fallback `PersonBalanceCalculator`
    - [x] UseCase `GetPersonBalancesUseCase` and ViewModel `PersonViewModel`
    - [x] UI: `PersonsScreen`, `PersonDetailSheet`, Debts hub tab integration, Dashboard links
    - [x] Parity test `PersonBalanceParityTest`

- **Task:** Plan 015 — [Design System] Add shared `HesabyarFab` component & migrate raw FAB uses (Issue #451)
- **Status:** Completed and verified
- **Completed Date:** 2026-10-16
- **Deliverables:**
    - [x] `app/src/main/java/io/github/mojri/hesabyar/ui/designsystem/Dimens.kt`: added `FabClearance = 80.dp`, deprecated `BottomNavClearance` with fallback to `FabClearance`
    - [x] `app/src/main/java/io/github/mojri/hesabyar/ui/components/HesabyarFab.kt`: created `HesabyarFab` composable consuming `Dimens.FABSize` (56dp) and `Dimens.FABIconSize` (24dp)
    - [x] `app/src/test/java/io/github/mojri/hesabyar/ui/components/HesabyarFabTokenTest.kt`: token specification tests
    - [x] `app/src/test/java/io/github/mojri/hesabyar/ui/components/HesabyarFabTest.kt`: Robolectric Compose UI tests for rendering, click events, and test tags
    - [x] `app/src/main/java/io/github/mojri/hesabyar/ui/screens/account/AccountManagementScreen.kt`: migrated raw FAB to `HesabyarFab` and `80.dp` bottom padding to `Dimens.FabClearance`
    - [x] `app/src/main/java/io/github/mojri/hesabyar/ui/screens/CategoryManagementScreen.kt`: migrated raw FAB to `HesabyarFab` and `80.dp` bottom padding to `Dimens.FabClearance`
    - [x] `app/src/main/java/io/github/mojri/hesabyar/ui/screens/DashboardScreen.kt`: migrated raw FAB to `HesabyarFab` and `80.dp` bottom padding to `Dimens.FabClearance`
    - [x] `app/src/main/java/io/github/mojri/hesabyar/ui/screens/BankLoanScreen.kt`: migrated raw FAB to `HesabyarFab`
    - [x] `plans/archive/015-hesabyar-fab-component.md`: completed architecture plan (DONE in `plans/README.md`)
    - [x] Knowledge graph: ran `graphify update .` to index `HesabyarFab` and its 4 screen callers

## In Progress

None. Main merged Issue #93 hardening via PR #462 and the back-navigation work from PR #385.

## Blocked

- GitHub Project board Status cannot be set: the `gh` token lacks `read:project` and `project` scopes, so the GraphQL item lookup fails with `INSUFFICIENT_SCOPES`.

## Plan 015 Findings

- Issue #451 and #456 claim "the app has NO BottomNav". This is incorrect: `MainActivity.kt:172` renders `MainBottomNavigation` in compact width (<600dp) and `MainNavigationRail` in expanded width. `Dimens.BottomNavClearance` itself had zero usages before Plan 015 — the three scrollable screens hardcoded `80.dp` padding instead. Plan 015 wires those call sites (plus `BankLoanScreen`, which had no bottom padding) to a new `Dimens.FabClearance` (80dp) and deprecates `BottomNavClearance` with a fallback alias rather than deleting it (deleting would break the token's public API for no gain, and the screens' clearance pads FAB overlap, not bottom-nav overlap).
- Issue #451 lists only 3 raw FAB uses. A 4th exists at `app/src/main/java/io/github/mojri/hesabyar/ui/screens/BankLoanScreen.kt:64`. Plan 015 migrates all 4.

1. **Room Schema Documentation Drift:** `AppDatabase.kt` defines schema `version = 9` with `exportSchema = false`. However, `docs/DATABASE_SCHEMA.md` lists schema version 3, and `docs/MIGRATION_NOTES.md` documents migrations only through v3.
2. **Missing MigrationTestHelper Test Infrastructure:** While JVM unit tests exist for migrations (e.g. `AppDatabaseMigrationTest.kt`, `AppDatabaseMigration7to8Test.kt`, `AppDatabaseMigration8to9Test.kt`), AndroidX `MigrationTestHelper` test suite using exported Room JSON schemas is not currently present in `app/src/test`.
3. **UniFFI Implementation:** UniFFI uses procedural macros (`#[uniffi::export]`) and scaffolding (`uniffi::setup_scaffolding!()`). No `.udl` interface definition files exist.
4. **Android Lint Failure in Pre-existing Code:** `scripts/check-android.sh` fails on task `:app:lintDebug` with 4 errors:
   - 1 local machine issue: `local.properties:8` (`PropertyEscape` on unescaped Windows backslashes in `sdk.dir`).
   - 3 pre-existing Compose issues: `ManualTransactionDialog.kt:132, 138, 149` (`LocalContextGetResourceValueCall` from querying resources using `LocalContext.current`).
   Per task rules, application code was left unchanged.
5. **JaCoCo 0% Composable Coverage (Resolved in main):** structural gap between the JaCoCo agent and Robolectric's sandbox classloader. Fix is `isIncludeNoLocationClasses = true` on Test tasks with `jdk.internal.*` exclusions (issue #285, PR #298, verified in CI run 36971068107).

## Decisions

- Retained existing `rust/hesabyar-core/README.md` technical build and pre-commit hook instructions while adding UniFFI architecture, canonical money rules, and new method lifecycle.
- Kept all check scripts strictly read-only (`check-android.sh` does not run `ktlintFormat`).
- Removed `--rerun-tasks` from `scripts/check-rust-bridge.sh` per direct user instruction to avoid 11-14 min NDK rebuilds and preserve incremental Gradle task checking.
- Phase 3 drops `LoanManagementScreen` from the hub tabs. `DebtSection.LOANS` was removed; the `"LOANS"` and `"PERSONS"` deep links now both open `DebtSection.PERSONS`. `LoanManagementScreen` currently has no callers (it is retained only for possible future deep-link support), and loan actions are handled by the `PersonDetailSheet` quick actions. This is a deliberate scope decision from plans/011 Phase 3 item 4 ("DebtHub third tab becomes this view").
- `MainActivity` no longer passes `loanViewModel` to `DebtHubScreen`, because the parameter became unused.
- Configure coverage through the Gradle `JacocoTaskExtension` on `tasks.withType<Test>().configureEach` in `app/build.gradle.kts`. This reaches all test tasks uniformly without call-site duplication.
- Exclude `jdk.internal.*` in `JacocoTaskExtension`. This prevents reflection and serialization accessor classloader conflicts on JDK 17+. CI run 36971068107 verified this configuration passes without errors.

## Verification

Run on 2026-10-09, branch `fix/back-navigation` (PR #385):

| Check | Command | Result |
|---|---|---|
| Navigation unit tests (incl. Plan 014 additions) | `./gradlew.bat testDebugUnitTest --tests "io.github.mojri.hesabyar.MainNavigationComponentsTest" --tests "io.github.mojri.hesabyar.MoreMenuSheetTest" --tests "io.github.mojri.hesabyar.MainNavigationCoordinatorTest" --tests "io.github.mojri.hesabyar.MainActivityBackNavigationTest" --tests "io.github.mojri.hesabyar.MainActivityNavigationTest"` | PASS (BUILD SUCCESSFUL, exit 0). `MainNavigationComponentsTest`: 11 tests, 0 failures (includes `navigationDestinationsHaveDistinctIcons`, `debtsTabLabelIsConcise`). |
| Kotlin style | `./gradlew.bat ktlintFormat --no-daemon` | PASS |
| Static analysis | `./gradlew.bat ktlintCheck detekt --no-daemon` | PASS (BUILD SUCCESSFUL, 0 findings) |
| Docs path references | `python scripts/check_docs.py` | PASS (no broken references) |
| GitHub CI | PR #393 actions | PASS (All required build and code quality checks green) |

### Plan 014 — Issue #360 Acceptance Criteria Resolution

- **Criterion 1 (BackHandler):** Implemented in PR #385 via the coordinator's management-state `BackHandler`s and `TabBackHandler`. Verified by existing tests.
- **Criterion 2 (Distinct Icons):** `MAIN_TABS.TAB_DASHBOARD.icon = Icons.Filled.Dashboard` (previously `AccountBalanceWallet`). Test `navigationDestinationsHaveDistinctIcons()` confirms no duplicates among `MAIN_TABS` or with Accounts management.
- **Criterion 3 (Debts Label):** `strings.xml:nav_tab_debts = "بدهی‌ها"` (previously `"مدیریت بدهی‌ها"`). Test `debtsTabLabelIsConcise()` verifies exact string.
- **Criterion 4 (Bottom Insets):** `MoreMenuSheet` uses `Spacer(modifier = Modifier.navigationBarsPadding())` (previously `height(32.dp)`). Verified by lint and manual testing on gesture/navigation devices.

### DeepSource TCV-001 — Root Cause and Fix

- **Root cause:** `MainActivity` is a Hilt `@AndroidEntryPoint` `FragmentActivity`. Unit tests never execute it, so every line inside it reported 0% coverage. PR #385 previously added the `MainBottomNavigation` / `MainNavigationRail` / `CompactMainContent` / `ExpandedMainContent` components as `private` members of `MainActivity`, leaving ~186 lines uncovered (DeepSource TCV-001 "Lines not covered in tests", Critical).
- **Fix:** Moved those 4 components plus `NavigationTabItem`, `MAIN_TABS`, `MORE_MENU_TABS` into a new, testable file `app/src/main/java/io/github/mojri/hesabyar/MainNavigationBars.kt` (re-scoped `internal`). `MainActivity.MainScreenScaffold` now calls them via the same-package `internal` symbols.
- **Coverage of moved lines:** `MainNavigationComponentsTest` (11 Robolectric Compose tests) covers every branch of all four components (tab render, tab click callback, more click callback, more-menu highlight), icon uniqueness verification, and debts tab label precision. JaCoCo now captures them via the `isIncludeNoLocationClasses = true` config (issue #285).

## Next Steps

None — Plan 016 (issue #453) completed and archived. Awaiting next task.

## Last Updated

2026-10-16
