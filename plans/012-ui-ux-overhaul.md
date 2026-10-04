# Plan 012: UI/UX overhaul — fix review findings in prioritized phases

> **Executor instructions**: Follow this plan phase by phase, in order.
> Phases are priority-ordered (P1 first). Do not start a later phase
> before the earlier one is merged. Run every verification command and
> confirm the expected result before moving to the next step. If anything
> in the "STOP conditions" section occurs, stop and report — do not
> improvise. When a phase is done, update the status row for this plan in
> `plans/README.md`.
>
> **Drift check (run first)**: `git diff --stat ad0504c..HEAD -- app/src/main/java/io/github/mojri/hesabyar/ui/`
> If any in-scope file changed since this plan was written, compare the
> "Current state" excerpts against the live code before proceeding; on a
> mismatch, treat it as a STOP condition.

## Status

- Priority: P1
- Effort: XL (Phase 0 docs + 7 implementation phases, each independently shippable)
- Risk: LOW-MED — UI-layer changes only; no database, backup, or Rust-core changes.
- Depends on: none
- Category: ui/ux
- Planned at: commit `ad0504c`, 2026-10-04

## Why this matters

A UI review of the app (2026-10-04) found that the design system is
healthy (tokens, shared components, RTL, Vazirmatn, Persian digits) but
screens were built without shared policies. The result: broken dialog
button layouts, full forms crammed into dialogs, Toast-only validation,
no back handling on overlay screens, missing numeric keyboards, weak
contrast on financial text, and LTR charts in an RTL-first app. Each
finding is small; together they make the app feel unpolished. This plan
fixes them in priority order, establishing shared policies (dialog,
header, feedback) that later phases reuse. That is the integration
approach: fix the foundations once, then apply them everywhere.

## Product decisions (all decided 2026-10-04 — recorded for the record)

1. **Bottom navigation**: DECIDED 2026-10-04 — keep the bottom bar,
   modernized as a floating pill bar: detached rounded container, 4
   destinations (داشبورد، دستیار هوشمند، بدهی‌ها، گزارش‌ها); the "بیشتر"
   sheet keeps تحلیل و آمار، مدیریت حساب‌ها، تنظیمات. Scroll behavior:
   labels fade out and the bar slims to icons-only on scroll down;
   restores on scroll up. The dashboard FAB stays separate, floating
   above the bar at bottom-end.
2. **Dashboard density**: DECIDED 2026-10-04 — keep the long scroll
   (everything visible at first glance), but curate it: audit the ~13
   sections, merge the near-identical 2-up card pairs
   (IncomeExpense/KPI/DebtorCreditor), make cards more compact, and let
   the user choose which sections appear on the dashboard (show/hide
   toggles in Settings, persisted in preferences). See Phase 4, Step 4.4.
3. **Dynamic color / themes**: DECIDED 2026-10-04 — dynamic color stays
   ON (default). Financial colors are fixed, explicitly documented roles
   (see Phase 5.1 + ARCHITECTURE.md) that must read correctly over any
   scheme. NEW: a theme picker in Settings — Dynamic (system wallpaper),
   Hesabyar brand (fixed blue identity), plus curated color themes
   (green, purple, ...). See Phase 5, Step 5.5.
4. **Feedback channel**: DECIDED 2026-10-04 — Snackbar replaces the
   Toast pipeline. Top placement (current trend), heads-up slide-down
   presentation, Dynamic Island-inspired expanding pill. Undo ("برگردان")
   action on destructive/reversible operations. See Phase 6, Step 6.1.

Do not block P1 phases on these. Record the decisions in this file when
made; Phases 3-6 consume them.

## Out of scope

- Rust core, Room schema, backup format, repository logic.
- New features, except the two approved in this plan: dashboard section
  toggles (Phase 4, Step 4.4) and the theme picker (Phase 5, Step 5.5).
  New screens. Visual redesign of the dashboard.
- `docs/ROADMAP.md` changes (update separately if scope shifts).

---

## Phase 0 — Documentation baseline (P1, docs-only)

**Goal**: make the docs match the code before any UI change lands, and
give every later phase a doc home for the policies it creates. The
`file:line` references in this plan were checked against `ad0504c`
(2026-10-04), but line numbers drift — executors must re-verify each
reference with grep before editing, and the STOP condition below is
symbol-based, not line-based.

**Global documentation rule for Phases 1–6**: each implementation phase
must update the matching doc section as part of its done criteria —
dialog policy (Phase 2) → `ARCHITECTURE.md` Design System section;
header policy (Phase 3) → same section; feedback policy (Phase 6) →
same section; roadmap checkboxes flip when a phase merges. Docs stay in
English per `AGENTS.md`.

**Screenshot-test note**: the Roborazzi Gradle plugin and dependencies
are applied (`app/build.gradle.kts:169,620-622`), but zero screenshot
tests exist in `app/src/test`. Where later phases say "update screenshot
tests", read it as "add new Roborazzi tests".

### Step 0.1: Fix `docs/TECH_STACK.md`

1. **Navigation**: "Navigation Compose" is wrong. Zero hits for
   `NavController`/`NavHost`/`navigation-compose` in `app/src/main`.
   Replace with: "Hand-rolled tab navigation (`MainActivity`; string tab
   IDs + boolean overlays; no Navigation Compose dependency)".
2. **Testing**: remove "MockK" — zero hits in `app/src/test`. Replace
   "Compose UI Test" with "Robolectric (unit) + Roborazzi (screenshot,
   infra applied, no tests yet)". `app/src/androidTest` is empty.
3. **Add missing entries**: Detekt, ktlint (static analysis —
   `app/build.gradle.kts:171,173`; `detekt {}` config at `:643-650`,
   `ktlint {}` at `:657-661`), Robolectric.
4. Keep "Minimum SDK: Android 8+" — `minSdk = 26`
   (`app/build.gradle.kts:215`) is Android 8.0, so the entry is correct.

### Step 0.2: Fix `docs/ROADMAP.md`

1. "Room Database (v3)" → "(v9)" — `AppDatabase.kt:31` says
   `version = 9`.
2. Analytics "Charts (visual graphs)" `[ ]` → `[x]` —
   `AnalyticsScreen.kt` has custom Canvas charts (`BarChart:351`,
   `CombinedLineChartCard:225`, `DonutChart:460`).
3. Security "Biometric Auth" `[ ]` → `[x]` — `BiometricHelper` is wired
   through `AuthManager` and surfaced in
   `SettingsScreen.kt:563,613`.
4. Testing "UI Tests (Compose)" stays `[ ]` but reword to "UI screenshot
   tests (Roborazzi)" — infra applied, no tests written yet.
5. Add a "UI/UX" category listing this plan's phases as `[ ]` items with
   a link to `plans/012-ui-ux-overhaul.md`, so the overhaul is visible
   on the roadmap.
6. `docs/DATABASE_SCHEMA.md:5` says "Current schema version: **3**" —
   fix to **9** (same stale claim as ROADMAP; `AppDatabase.kt:31`).
7. `docs/MIGRATION_NOTES.md` stops at "v2 → v3". Add a gap note that
   migration notes for v4–v9 are missing, or state explicitly that they
   are out of scope — do not leave the doc silently contradicting the
   schema version.

### Step 0.3: Fix `docs/architecture/ARCHITECTURE.md`

1. "Navigation Compose" (`:175`) → the hand-rolled navigation
   description from Step 0.1.
2. Replace the aspirational Design System section (`:382-467`) with the
   actual inventory: `SpacingTokens`, `Dimens`, `ShapeTokens`/`AppShapes`,
   `ElevationTokens`, `FinancialColors`, `WindowSizeTokens`;
   `ui/theme/` (`Color.kt`, `Theme.kt`, `Type.kt`, Vazirmatn downloadable
   font); `ui/components/` inventory (`HesabyarButton`, `HesabyarCard`,
   `HesabyarChip`, `HesabyarDialog`, `HesabyarInputField`, `EmptyState`,
   `ConfirmDialog`, ...); Persian-first RTL rules (full-RTL default,
   LTR-forced only for signed amounts/phone numbers per `AGENTS.md`).
   Factual, no marketing language.

## Phase 0 done criteria

- [ ] `TECH_STACK.md`: navigation, testing, and static-analysis entries
  match grep-verified reality
- [ ] `ROADMAP.md`: 4 status corrections + new UI/UX category
- [ ] `DATABASE_SCHEMA.md`: schema version corrected; `MIGRATION_NOTES.md`
  gap noted or declared out of scope
- [ ] `ARCHITECTURE.md`: navigation fixed; design system section factual
- [ ] All touched docs in English; no Persian strings added to docs
- [ ] `git diff --stat` shows docs-only changes

---

## Phase 1 — Fix broken dialogs and form input (P1)

**Goal**: repair the dialog layouts that are visibly broken and the form
inputs that reject no invalid data silently.

**Findings addressed**: #1, #6, #7, #8 (screen audit), financial-text
contrast is Phase 5.

### Step 1.1: Fix ConfirmationDialog button layout

File: `ui/screens/SmartAssistantScreen.kt:1240-1270`.

Current state: `confirmButton` and `dismissButton` both use
`Modifier.fillMaxWidth()`. M3 lays dialog buttons in a Row, so the
dismiss button is pushed out / cramped.

Change: remove `fillMaxWidth()` from both buttons. Let the M3 dialog
button row size them. Keep the existing labels and order.

**Verify**: Roborazzi screenshot of the dialog, or manual open on device.
Both buttons fully visible, no clipping.

### Step 1.2: Fix AccountDialog buried save button

File: `ui/screens/account/AccountManagementScreen.kt:532-665`.

Current state: raw `AlertDialog` with `confirmButton = {}` empty
(`:541`). The primary "ذخیره" action sits at the bottom of a
`heightIn(max = 500.dp)` scrollable body (`:568, ~640-665`).

Change: move "ذخیره" into `confirmButton` and "انصراف" into
`dismissButton`. Keep the scrollable body for the form fields only.
Do not change field order or validation logic.

**Verify**: save/cancel always visible without scrolling. Existing
`AccountManagementScreen` tests pass.

### Step 1.3: Add numeric keyboards to money fields (IBAN is the exception)

Files:
- `ui/screens/InstallmentScreen.kt:220-224` (amount field)
- `ui/screens/LoanManagementScreen.kt:765-769` (`LoanFormFields`,
  "مبلغ قرض") and `:791-795` (`RepaymentFormFields`, "مبلغ پرداختی") —
  two separate amount fields
- `ui/screens/account/AccountManagementScreen.kt:726-733` (IBAN field)

Current state: no `keyboardOptions`. `BankLoanScreen.kt:303,309,315`
already does it right — copy that pattern
(`keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)`).

Change: add `KeyboardType.Number` to the **three amount fields**
(Installment + the two LoanManagement fields). Amount fields stay
RTL-safe: amounts right-align today; keep that behavior, only change
the keyboard.

**Do NOT put a Number keyboard on the IBAN field**: an IBAN starts with
a 2-letter country code (`IR...` per the field's own placeholder), which
a numeric keyboard cannot type. Use `KeyboardType.Ascii` and force LTR
alignment for the IBAN (an `AGENTS.md`-sanctioned LTR-forced case, like
phone numbers).

**Verify**: open each form on device/emulator, tap the field, confirm
the numeric keyboard opens on the amount fields and an ASCII/LTR
keyboard opens on the IBAN field. Type `IR` into the IBAN field.

### Step 1.4: BackHandler on overlay screens

Files:
- `ui/screens/CategoryManagementScreen.kt` (opened via boolean in
  `MainActivity.kt:108-117`)
- `ui/screens/account/AccountManagementScreen.kt`
- `ui/screens/SmartAssistantScreen.kt:115-133` (`ParsedResultCard`
  full-screen form)

Current state: zero `BackHandler` registrations. System back exits the
app (or does nothing useful) instead of dismissing the overlay.

Change: add `BackHandler { onBack() }` (or the equivalent dismiss
callback) to each of the three. No navigation-library migration in this
phase — only make back behave.

**Verify**: manual back-press test on each screen. `./gradlew
ktlintCheck detekt --no-daemon` exits 0.

### Step 1.5: Inline validation for the worst Toast-only forms

Files: `ui/screens/dashboard/dialogs/ManualTransactionDialog.kt:131-153`
(validation block; `showToast` at `:132,138,147`, plus `:177`),
`ui/screens/InstallmentScreen.kt:259-266` (validation; `showMessage` at
`:265`).

Current state: validation failures surface only via Toast after submit.

Change: add `isError = true` + `supportingText` with the same Persian
message on the offending field(s). Keep the Toast as a secondary signal
if removal risks behavior change; do not remove the Toast pipeline in
this phase (Phase 6 owns that).

**Verify**: submit each form empty/invalid, confirm the inline error
appears on the field. Existing tests pass.

## Phase 1 done criteria

- [ ] Both dialog buttons visible in the assistant confirmation dialog
- [ ] Account save/cancel in the dialog footer on all screen sizes
- [ ] Numeric keyboard on the 3 amount fields; ASCII/LTR keyboard on IBAN
- [ ] Back dismisses the 3 overlay screens
- [ ] Inline errors on the 2 forms
- [ ] `./gradlew test --no-daemon` exits 0
- [ ] `./gradlew ktlintCheck detekt --no-daemon` exits 0

---

## Phase 2 — Unify the dialog system (P1)

**Goal**: one dialog policy. Today raw `AlertDialog` is used 16 times
with visibly different headers; `HesabyarDialog` 3 times;
`ConfirmDialog` 6 times (`ReportsScreen.kt:773`,
`DashboardScreen.kt:309`, `CategoryManagementScreen.kt:245`,
`BankLoanScreen.kt:114`, `AccountManagementScreen.kt:308` and `:335`);
`ModalBottomSheet` zero times in `ui/`
despite multi-field forms being the most common violation.

### Step 2.1: Write the dialog policy (code, not docs)

Extend `ui/components/HesabyarDialog.kt` so it covers every current use:
- `title`, `onDismiss`, scrollable body, action row (confirm/dismiss).
- `heightFraction` already exists (`:63`, applied at `:78-79`, documented
  at `:49`) but defaults to `null` (uncapped) — only
  `ForecastDetailDialog.kt:43` passes a value (`0.85f`). Change the
  default to `0.85f` so forms cannot grow unbounded
  (`ManualTransactionDialog`'s content region at `:195-276` has no cap
  today). Do not add a second mechanism.
- Fix `ConfirmDialog.kt:58-62`: do not render the dismiss slot when
  `dismissText` is empty (today `""` renders a blank clickable button —
  `AccountManagementScreen.kt:339`).

Do not invent a fourth dialog component.

### Step 2.2: Migrate full forms to ModalBottomSheet

Five form surfaces that outgrew dialogs (each has 5+ fields and/or
nested pickers):
- `ManualTransactionDialog` — 9 top-level blocks (`:196`
  `TransactionTypeSelector`, `:213` account `Column`+`AccountSelector`,
  `:226` `DestinationAccountSelector`, `:234` `TransactionAmountInput`,
  `:244` `TransactionCategorySelector`, `:252` `LoanPersonNameInput`,
  `:259` `InstallmentFormFields`, `:267` `JalaliDateTimePicker`, `:272`
  `TransactionDescriptionInput`) + nested Jalali pickers
- `InstallmentScreen.kt:192-279` add form (+ nested `JalaliDateTimePicker`)
- `BankLoanScreen.kt` add form (+ nested `JalaliDatePickerDialog`)
- `LoanManagementScreen.kt` add form
- `LoanManagementScreen.kt` edit form

Change: present each as a `ModalBottomSheet` with a drag handle,
scrollable content, and sticky footer actions. Keep all fields, order,
and validation logic identical. The nested Jalali pickers stay dialogs
— a picker over a sheet is the correct M3 pattern; dialog-over-dialog
was the problem.

**Verify**: each of the 5 forms opens as a sheet, scrolls, and submits
exactly as before. Update or add Roborazzi screenshots for all 5.

### Step 2.3: Migrate remaining raw AlertDialogs to HesabyarDialog

Remaining raw usages after 2.2: `LoanManagementScreen` delete/repay,
`CategoryManagementScreen` category dialog, `SettingsScreen`
restore/verify-PIN/set-PIN, `SmartAssistantScreen` confirmation,
`TransactionDetailDialog`, `ExportPassphraseDialog.kt:44`,
`ImportPassphraseDialog.kt:46`, and `ConfirmDialog.kt:43` itself
(fixing its blank dismiss button in Step 2.1 does not remove its
`AlertDialog` usage — migrate it to `HesabyarDialog` so the zero-raw
criterion below can pass).

Change: mechanical migration to `HesabyarDialog`. Unify the header:
title + close-X + divider (the `HesabyarDialog` treatment), replacing
per-screen header variants.

### Step 2.4: Fix dialog title alignment

Replace hardcoded `TextAlign.Right` with `TextAlign.Start` on dialog
titles (`InstallmentScreen.kt:225`, `LoanManagementScreen.kt` ×4,
`TransactionDetailDialog.kt:63`). In RTL, `Start` is right — same
visual result, correct semantics.

## Phase 2 done criteria

- [ ] Zero raw `AlertDialog` in `ui/` (grep proves it)
- [ ] 5 big forms open as bottom sheets with sticky footers
- [ ] `ConfirmDialog` with empty dismiss text renders no blank button
- [ ] Dialog headers visually identical across screens
- [ ] Screenshot tests updated; `./gradlew test --no-daemon` exits 0
- [ ] `./gradlew ktlintCheck detekt --no-daemon` exits 0

---

## Phase 3 — Navigation, headers, back stack (P2)

**Goal**: consistent app chrome. Depends on Phase 1.4 (back handling)
and consumes decided product decision #1 (floating pill nav bar; the
Snackbar work of decision #4 lives in Phase 6).

### Step 3.1: Shared screen header

Current state: only `CategoryManagementScreen` and
`AccountManagementScreen` use `TopAppBar`. Everything else hand-builds
headers; `BankLoanScreen` has none; `SectionHeader` is used by 2 of 10
screens; section titles use emoji prefixes (📈⚙️💡📊).

Change:
- Create `ui/components/HesabyarTopBar.kt`: title, optional back action,
  optional actions slot. Built on M3 `TopAppBar`, uses theme tokens only.
- Apply to: Analytics, Installment, LoanManagement, Reports,
  SmartAssistant, Settings, BankLoan (add the missing header),
  DebtHub (replace bare tabs with a titled screen).
- Keep `DashboardHeader` (it is a branded header, not a nav header).
- Remove emoji prefixes from section titles; use the icon system
  (`IconCircle`) where an icon is wanted.

### Step 3.2: Back-stack for overlay screens

Current state: `MainActivity.kt` drives `CategoryManagementScreen` and
`AccountManagementScreen` with booleans (`:108-117`).

Change: keep the no-Navigation-Compose architecture (no rewrite in this
plan), but centralize: a single `overlay: OverlayScreen?` state instead
of two booleans, with one `BackHandler`. State model and precedence,
stated explicitly so there is no ambiguity:
- Only one overlay is ever visible: `overlay` holds at most one
  `OverlayScreen` value (sealed: `CategoryManagement`, `Accounts`).
- If an overlay is requested while another is open, the new request
  REPLACES the current one (no stacking, no queue).
- System back always dismisses the current overlay (`overlay = null`);
  there is no deeper back-stack to walk.
Do not gold-plate beyond this.

### Step 3.3: FAB consistency in DebtHub

Current state: BankLoan has a FAB; Installment and LoanManagement use
header buttons.

Change: give Installment and LoanManagement the same FAB pattern
(primary container, add icon, bottom-end, testTag). Applies after 3.1
(the header buttons move into the FAB).

### Step 3.4: Modernize the bottom bar (decision #1, decided 2026-10-04)

Build the floating pill navigation bar:
- Detached rounded container (28dp corner radius, 16dp side margins,
  16dp bottom margin), 4 destinations: داشبورد (`AccountBalanceWallet`),
  دستیار هوشمند (`AutoAwesome`), بدهی‌ها (`AccountBalance`), گزارش‌ها
  (`Analytics`). The more-sheet keeps تحلیل و آمار، مدیریت حساب‌ها،
  تنظیمات.
- Selected indicator: `primaryContainer` pill at full opacity (M3
  default) — no custom alpha (see Phase 5.3 for the alpha-token cleanup).
- Scroll behavior: on scroll down, labels fade out and the bar animates
  to a slimmer icons-only height; on scroll up (or when idle at top), it
  restores to full height with labels. Drive it from the scrolled
  list's scroll direction with an `AnimatedVisibility`/size animation on
  the label row. Never fully hide the bar — navigation stays one tap
  away.
- Keep the existing tablet `NavigationRail` for `>= 600dp` widths
  (`MainActivity.kt:128`); the rail keeps its labels.
- The dashboard FAB (manual-transaction entry) stays a separate floating
  action, anchored bottom-end above the floating bar. Replace the
  hardcoded `80.dp` bottom content padding (`DashboardScreen.kt:113`)
  with a clearance token derived from the new bar geometry.
- Accessibility: every destination keeps a `contentDescription` (and
  matching semantics) carrying its Persian label. When the visible
  labels fade to icons-only, TalkBack must still announce and activate
  each destination — verify with TalkBack on device.

## Phase 3 done criteria

- [ ] Every screen except Dashboard shows `HesabyarTopBar`
- [ ] No emoji-prefixed titles (grep proves it)
- [ ] FAB on all three DebtHub tabs, same style and position
- [ ] Decision #1 recorded and applied
- [ ] `./gradlew test --no-daemon` exits 0; lint exits 0

---

## Phase 4 — Loading states, empty states, correctness (P2)

**Goal**: the app must distinguish "loading" from "empty", and empty
states must guide the user forward.

### Step 4.1: Dashboard loading state

Files: `ui/DashboardViewModel.kt:62`, `ui/screens/DashboardScreen.kt:83-91`.

Current state: `StateFlow<DashboardData>` renders immediately. Cold
launch shows "no transactions" / "no installments" identically for a
new user and a still-loading database.

Change: introduce a sealed UI state (`Loading` / `Loaded(DashboardData)`)
in `DashboardViewModel`. `Loading` shows until the first data emission;
`Loaded` renders after. Render M3 skeleton placeholders (`Card` +
shimmer-free tonal blocks — no new dependencies) while loading. Apply
the same pattern to `AnalyticsViewModel` (`AnalyticsScreen.kt:45`,
seeds empty `AnalyticsData()` today).

No `Error` state: the upstream repository Flows are seeded with empty
lists and surface no failure signal, so an error branch would be
unreachable. Adding one would require changing the repository/use-case
contract, which this UI plan forbids (see Out of scope). Keep the change
ViewModel-local. No repository changes.

### Step 4.2: Empty states with actions

Current state: `EmptyState` supports `actionText`/`onAction`, but
`DashboardScreen.kt:200-206, 228-234` pass icon+title only. Five other
screens hand-roll bare centered Texts
(`BankLoanScreen.kt:66`, `CategoryManagementScreen.kt:163-182`,
`AccountManagementScreen.kt:374-386`, `LoanManagementScreen.kt:175-205`,
`InstallmentScreen.kt:167-205`).

Change: replace all hand-rolled empties with `EmptyState`, always with
an action that leads somewhere ("ثبت اولین تراکنش", "ثبت وام", ...).
Wire the actions to the existing FAB/dialog entry points.

### Step 4.3: Correctness fixes

- `KpiCards.kt:61,88`: `"$savingsPct%"` uses Latin digits with no BIDI
  isolation. Route through a Persian-digits formatter with LRM isolation,
  like `CurrencyFormatter`.
- `ReportsScreen.kt:165`: "امروز" preset uses UTC midnight
  (`now - (now % 86400000)`), silently dropping 00:00–03:30 Tehran
  transactions. Anchor the preset to Asia/Tehran midnight. Note:
  `JalaliCalendarHelper` has no start-of-day API (its surface is
  month-granular: `gregorianToJalali`, `getJalaliMonthBoundaries`, ...),
  and it uses the device timezone — so add a small
  `startOfDay(timestamp, zoneId)` helper (accepting an explicit
  `ZoneId`, called with `ZoneId.of("Asia/Tehran")`) as a sub-task of
  this step, covered by the boundary unit test below. This is UI
  presentation logic; no Rust change.
- `SettingsScreen.kt:1605`: Latin-digit `String.format` for reminder
  time next to Persian-digit slider labels — unify on Persian digits.

**Verify**: unit tests for the Tehran-midnight preset boundary and the
sealed loading state. Manual empty-state walkthrough per screen.

### Step 4.4: Dashboard curation and customization (decision #2, decided 2026-10-04)

File: `ui/screens/DashboardScreen.kt:115-241`.

Current state: ~13 sections stacked with no prioritization. The three
2-up card pairs (`IncomeExpenseCards`, `KpiCards`, `DebtorCreditorCards`)
are near-identical visually (same `ShapeTokens.Large`, same title
rhythm), differing only in container tint.

Change:
- **Curate**: audit every section for glance value. Merge or
  differentiate the three 2-up pairs (merge into one "خلاصه" group or
  give each a distinct visual role — executor proposes, owner approves
  in the phase PR). Drop sections that duplicate other screens.
- **Compact**: tighten the cards (smaller paddings, denser rows) so more
  fits above the fold. Respect `SpacingTokens` — add a compact spacing
  ramp if needed, do not hardcode.
- **Customizable**: add show/hide toggles for each dashboard section in
  Settings (persisted in preferences via the existing settings store).
  v1 is show/hide only; reordering is out of scope.

**Verify**: all toggles off/on render correctly; no empty gaps when a
section is hidden; existing tests pass.

## Phase 4 done criteria

- [ ] Skeleton on dashboard + analytics first open (Loading until first
  emission; no unreachable Error state)
- [ ] All empty states use `EmptyState` with a working CTA
- [ ] Persian digits on KPI percentages; Tehran-midnight "today" preset
- [ ] Dashboard sections curated + compact; show/hide toggles work
- [ ] New unit tests pass; full suite exits 0; lint exits 0

---

## Phase 5 — Design-system hardening (P3)

**Goal**: close the token gaps the review found, so screens stop
inventing one-off values.

### Step 5.1: Financial colors with contrast-safe text roles

File: `ui/designsystem/FinancialColors.kt:41-43` (hex roles; dark
equivalents at `:50-52`; the mutable singleton lives at `:57-58`).

Current state: `#2ECC71` on light surface is 2.10:1 (needs 4.5);
`#E74C3C` is 3.82:1; `#F39C12` is 2.19:1. One color per role, no
container/on-container pairs, and a global mutable singleton outside
the composition contract.

Change:
- Add `onSurface` text variants that pass 4.5:1 on light surfaces
  (e.g. income `#1E7E34`, expense `#C0392B` — verify with a contrast
  check, do not guess).
- Add `incomeContainer`/`expenseContainer` (+ on-colors) for tinted
  backgrounds, replacing `color.copy(alpha = 0.1f)` call sites
  (`SmartAssistantScreen.kt:909,1306,1365`).
- Keep the `FinancialColors` object API shape; executors may refactor it
  into a `CompositionLocal` only if zero call-site churn results —
  otherwise leave the singleton and note it as tech debt.

### Step 5.2: Typography gaps

- `ui/theme/Type.kt`: add the missing `labelSmall` (11sp/Medium) in
  Vazirmatn. Today it falls back to the system font
  (`MainActivity.kt:266,293,327,354`,
  `AmountQuickFillButtons.kt:78`).
- `ui/theme/Type.kt:19-22`: the `VazirmatnFontFamily` registers a single
  **downloadable** font entry with no `weight` declared — so the
  `FontWeight.Bold`/`SemiBold`/`Medium` requests elsewhere in `Type.kt`
  are never actually fetched (the repo has no `res/font/` directory and
  no bundled `.ttf` at all; there is no "one Regular file with synthetic
  bolding"). Fix the weight mapping: declare per-weight `Font(...)`
  entries (downloadable preferred; bundle only if download proves
  unreliable). Verify Persian glyph rendering on device before/after.

### Step 5.3: Kill hardcoded alphas and dp

Work through this screen by screen (one file group at a time, verifying
each group with grep before moving on — do not batch dozens of unrelated
replacements into one unverifiable diff):

- Add the missing alpha tokens actually in use (0.1f container tint,
  0.3f container tint) as named constants next to the existing ones in
  `Dimens.kt` (`:32`, `:35`, `:38`; the file is 39 lines — insert at
  `:38`), then replace every raw `.copy(alpha = …)` call site in
  `ui/` (≈43 hits — re-grep at execution and replace every hit; list:
  `AnalyticsScreen.kt:450,905`, `ReportsScreen.kt:610`,
  `SmartAssistantScreen.kt:237,435,473,1306`,
  `LoanManagementScreen.kt:515`, `SettingsScreen.kt:1070`, ...).
  `AnalyticsScreen.kt:905` (`Color.LightGray`) and
  `CategoryChip.kt:43` (`Color.Gray` fallback) must become theme-aware.
- Replace the 109 raw `.dp` hits in `ui/screens` (165 across
  `app/src/main`) with `SpacingTokens`/`Dimens` (worst files:
  `CategoryManagementScreen` 14, `SmartAssistantScreen` 12,
  `ReportsScreen` 11). Add genuinely missing tokens; do not force-fit.
- Replace the 10 hardcoded `lineHeight` overrides with named styles in
  `Type.kt`.
- Unify circle shapes: standardize on `ShapeTokens.Full` (6 uses today;
  note `ShapeTokens.kt:12` defines it as `RoundedCornerShape(9999.dp)`,
  which approximates a circle only for aspect-square content) and
  replace the 41 raw `CircleShape` uses in `ui/`.
- `PinScreen.kt:155`: `fontSize = 24.sp` → theme typography style.
- `CategoryManagementScreen.kt:467`,
  `AccountBalanceCard.kt:211`: `Color.White` tints over accent swatches
  → reuse `CategoryChip.kt:24` luminance-based `textColorForBackground()`.

### Step 5.4: RTL charts

File: `ui/screens/AnalyticsScreen.kt` — `CombinedLineChartCard` at
`:225` (x-origin `val startX = 20f` at `:261`), `BarChart` at `:351`
(x calc `val x = index * (barWidth + spacing) + spacing / 2` at `:368`).

Current state: both hardcode LTR; time flows left→right in an RTL-first
app. Canvas does not auto-mirror.

Change: mirror the x-axis so the newest period is at the
inline-start (right in RTL): in `BarChart`, compute
`x = size.width - (index * (barWidth + spacing) + spacing / 2) - barWidth`;
in `CombinedLineChartCard`, mirror around `startX` similarly
(`x = size.width - startX - idx * spacing`). Keep the drawing code
otherwise identical. Verify against the Figma dashboard only for axis
direction.

### Step 5.5: Theme system — dynamic, brand, and curated themes (decision #3, decided 2026-10-04)

Current state: `HesabyarTheme(dynamicColor = true)` is a boolean switch
— either wallpaper-derived colors or the static brand palette. The
existing dark-mode toggle in Settings only flips light/dark.

Change:
- Keep dynamic color as the default.
- Add a theme picker in Settings (next to the dark-mode toggle) with:
  1. **پویا** — system wallpaper colors (Android 12+; falls back to
     brand below 12),
  2. **برند حسابیار** — the fixed blue identity (`Color.kt` schemes),
  3. **Curated themes** — 2–3 additional fixed palettes (e.g. green,
     purple), each with full light + dark schemes defined in
     `Color.kt` style.
- Persist the choice in the existing settings store; `HesabyarTheme`
  takes a `ThemeChoice` instead of a boolean.
- Financial colors stay fixed roles (Phase 5.1) and must be verified
  legible over every theme — document the roles explicitly in
  `ARCHITECTURE.md` (per the owner's requirement that financial colors
  live in the docs, not just in code).
- On theme switch, re-run `applyFinancialPalette` so income/expense
  roles stay consistent.

**Verify**: switch through every theme × light/dark on device; contrast
spot-check on financial text; preference survives process death.

## Phase 5 done criteria

- [ ] Contrast check passes 4.5:1 for financial text in BOTH light and
  dark modes (Phase 5.5 adds themes; every theme × mode must pass)
- [ ] `labelSmall` in Vazirmatn; real font weights on device
- [ ] Zero `.copy(alpha` in `ui/` outside the design system (grep)
- [ ] Charts read right-to-left; decision #3 recorded
- [ ] Full suite + lint exits 0

---

## Phase 6 — Feedback and polish (P3)

**Goal**: consistent, accessible feedback. Consumes decision #4.

### Step 6.1: Snackbar migration (decision #4, decided 2026-10-04)

Current state: `Toast.makeText` in `MainActivity.kt:79-81`
(viewmodel `showMessage` pipeline), `SmartAssistantScreen.kt:1176`,
`ManualTransactionDialog.kt:293`. Zero `SnackbarHost` in `ui/`.

Change:
- Add one `SnackbarHost` at the `MainActivity` scaffold level, anchored
  TOP (below the app-bar area), and route `settingsViewModel.uiMessage`
  through it.
- Presentation: heads-up style — slides down from the top when shown,
  auto-dismisses with slide-up. Shape: floating pill (Dynamic
  Island-inspired); expands vertically when message + action need two
  lines. It must never cover the floating nav bar or the FAB (top
  placement guarantees this).
- Give destructive or reversible actions (delete transaction,
  installment paid toggle — `InstallmentMiniItem.kt:65-78` has no
  confirm/undo today) an Undo ("برگردان") action. Each Undo must name
  its reversal operation in the phase PR: e.g. delete-transaction →
  re-insert the deleted row via the repository insert (keep the deleted
  entity in memory until the Snackbar dismisses); paid-toggle → toggle
  the flag back. No fire-and-forget change may offer Undo without a
  defined reversal.
- Route the direct `Toast.makeText` calls (`SmartAssistantScreen.kt:1176`,
  `ManualTransactionDialog.kt:293`) through the new Snackbar pipeline
  (as transient UI events, not Toasts) — otherwise validation failures
  in those forms keep bypassing Snackbar/Undo and the done criterion
  below cannot pass.
- Keep Toast only where a Snackbar cannot reach (pre-compose contexts).

### Step 6.2: Touch targets and input polish

- `CategoryManagementScreen.kt:318,331`: 36dp edit/delete IconButtons →
  48dp minimum (also matches `Dimens.ButtonHeight`).
- `HesabyarInputField.kt`: add `keyboardActions`/`imeAction` for
  next/done navigation across multi-field forms.
- `SettingsScreen.kt:1623-1674`: replace the four `+ ساعت / − ساعت /
  + دقیقه / − دقیقه` buttons with the existing
  `CustomTimePickerDialog`.

### Step 6.3: Motion and empty-state polish

- `EntranceCard.kt:10-15`: `AnimatedVisibility` re-runs on scroll as
  items enter composition. Key the animation to first composition only.
- `EmptyState.kt`: vertically center content in its slot; allow a
  non-Filled action variant.
- `SettingsScreen.kt:192,656,725` (done in Phase 2.3) — confirm the
  restore radio rows are fully clickable (`:219-261`).

## Phase 6 done criteria

- [ ] Zero user-facing Toasts for in-app validation/errors (grep proves
  it); every destructive action offers Undo with a defined reversal
- [ ] 48dp touch targets on category row actions; ime navigation works
- [ ] Entrance animations run once; empty states centered
- [ ] Full suite + lint exits 0

---

## Phase 7 — Screen structure refinement (P3)

**Goal**: fix per-screen information-architecture and density issues the
review found, after the shared policies (Phases 1–3) exist. Structure
first; two small behavior completions are explicitly allowed (assistant
parser retry, reports account filter) — anything beyond those is out of
scope.

### Step 7.1: Settings screen structure

File: `ui/screens/SettingsScreen.kt` (1784 lines).

Current state: one long `Column` + `verticalScroll` (`:320–322`); a
primary-tinted branding box dominates the top (`:324–366`); the General
card is overloaded (category nav, dark-mode switch, currency chips, a
~180-line reminder section, a ~230-line security section); no
collapsible sections.

Change:
- Extract each section into its own composable under
  `ui/screens/settings/sections/` (mirrors the
  `ui/screens/dashboard/components/` pattern). This also relieves
  detekt `LargeClass` pressure.
- Group sections into collapsible cards (expand/collapse, M3). No
  settings-search in this phase — keep scope tight.
- Tone down the branding box (smaller, less dominant) now that Phase
  3.1 gives the screen a proper `HesabyarTopBar`.
- Keep every setting, toggle, and behavior identical.

### Step 7.2: Analytics screen focus

File: `ui/screens/AnalyticsScreen.kt` (925 lines).

Current state: 5 custom Canvas charts + 5 list cards in one scroll
(`:77–154`); expense/income bar charts *and* a combined line chart of
the same data (3 cards for 2 series); no date-range control, fixed
`takeLast(6)` (`:242`), while `ReportsScreen` already has presets +
pickers; no chart drill-down.

Change:
- Merge the redundant series cards: one chart per data story.
- Reuse the `ReportsScreen` date-range preset control as a shared
  component (DRY — do not duplicate it).
- Keep the `CardEmptyHint` empty-state pattern.

### Step 7.3: SmartAssistant robustness

File: `ui/screens/SmartAssistantScreen.kt` (1402 lines).

Current state: the hand-rolled markdown renderer handles only
`#`/`##`/`###`, bullets, and bold (`:726–783`) — numbered lists and
tables from AI advice render as raw text; the parser error card has no
retry (`:392–410`); tapping an example fires an AI call immediately
(`:270–290`).

Change:
- Extend the markdown renderer to cover numbered lists and tables.
  (Adopting a library needs owner approval first — new-dependency STOP
  condition. Prefer extending the existing renderer.)
- Add a retry action to the parser error card.
- Keep the `TabRow` structure and the inline parse form as-is.

### Step 7.4: Reports screen minor items

File: `ui/screens/ReportsScreen.kt`.

- Preset buttons (`:162–175`) → `SegmentedButton`/`FilterChip`
  (borderless "filled" buttons with transparent unselected state today).
- Add an account filter for parity with the category filter.

## Phase 7 done criteria

- [ ] Settings split into section files; collapsible groups work
- [ ] Analytics shows one chart per data story + shared date presets
- [ ] Assistant renders numbered lists/tables; error card retries
- [ ] Reports presets are segmented; account filter present
- [ ] `./gradlew test --no-daemon` exits 0; lint exits 0

---

## Global verification (every phase)

```bash
./gradlew ktlintFormat --no-daemon
./gradlew ktlintCheck detekt --no-daemon
./gradlew test --no-daemon
```

UI phases must also update or add Roborazzi screenshot tests for
changed screens/dialogs. Follow `AGENTS.md` "Mandatory Post-Modification
Verification Workflow" and the evidence standard: paste exact code,
named test results, and file:line references in the phase report.

## STOP conditions

- A "Current state" excerpt does not match the live file. Match on the
  named symbol/property (e.g. "the `heightFraction` parameter of
  `HesabyarDialog`"), not on exact line numbers — line numbers drift;
  a drifted line range alone is not a STOP. Stop only when the cited
  symbol or behavior is genuinely absent or different.
- A phase requires a Room migration, a backup-schema change, or Rust
  business-logic changes — stop; those are out of scope for this plan.
- A phase requires a new dependency — stop and ask the owner first.
- `detekt` or `ktlintCheck` fails twice after a reasonable fix attempt.
- A screenshot test shows an unintended visual regression you cannot
  explain — stop and report with the diff image.

## Maintenance notes

- The dialog policy (Phase 2.1) is the contract for all future screens.
  New screens must use `HesabyarDialog`/`ModalBottomSheet` and
  `HesabyarTopBar`; reviewers should reject raw `AlertDialog` and
  hand-built headers.
- The financial color roles (Phase 5.1) are the only sanctioned
  income/expense colors. New tints must come from the container roles,
  never from `.copy(alpha)`.
- Keep this plan's status row in `plans/README.md` current per phase.
