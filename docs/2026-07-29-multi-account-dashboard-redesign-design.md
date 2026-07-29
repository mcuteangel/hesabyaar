# Multi-Account Dashboard: Bug Fixes + Visual Redesign

## Overview

Fix data integrity issues with archived accounts leaking into dashboard totals, wire card selection end-to-end, improve scalability with LazyRow, and redesign account cards with icons, selection state, and trend indicators.

## Phase 0 — Data Integrity Fix

### Problem

`GetDashboardDataUseCase` uses `repository.allAccounts` (calls `getAllAccounts()`, includes archived). When Rust FFI returns null, the Kotlin fallback's `computeAccountSummaries` doesn't filter archived accounts — archived accounts' transactions leak into dashboard totals.

### Fix

1. **Rust path:** Already filters archived accounts (`!a.is_archived` in `compute_account_summaries`). No change needed.
2. **Kotlin fallback:** Add `account.isArchived` check in `computeAccountSummaries` to skip archived accounts.
3. **Data source:** Keep `allAccounts` in `GetDashboardDataUseCase` (Account Management screen needs it), but add explicit filtering at the dashboard computation call site.
4. **Regression test:** Create account → add transaction → archive account → assert `computeDashboardData(accountId = null)` excludes it.

### Files to modify

- `app/src/main/java/io/github/mojri/hesabyar/domain/usecase/GetDashboardDataUseCase.kt` — filter archived in `computeAccountSummaries`
- `app/src/test/java/io/github/mojri/hesabyar/GetDashboardDataUseCaseTest.kt` — add archived exclusion test

## Phase 1 — Selection Wiring

### Problem

`AccountBalanceCard` has no selection awareness — it's purely presentational. Card `onClick` exists but isn't wired in `DashboardScreen`. Chips and cards use independent state.

### Fix

1. **Add `selectedAccountId: Long?` parameter** to `AccountBalanceCard` composable.
2. **Wire `onClick` in DashboardScreen's LazyRow:** `onClick = { dashboardViewModel.selectAccount(summary.accountId) }`
3. **Single source of truth:** Both `AccountSelector` and `AccountBalanceCard` read `selectedAccountId` from the same `StateFlow`.
4. **Selection visual treatment:**
   - 2px border in account's color (with `animateColorAsState` transition)
   - Small check badge (✅ or `Icons.Filled.Check`) top-right corner
   - Subtle background tint (account color at 8% alpha)
5. **Test:** Compose UI test — tap card → verify `selectedAccountId` updates → verify chip and card show consistent state.

### Files to modify

- `app/src/main/java/io/github/mojri/hesabyar/ui/components/AccountBalanceCard.kt` — add selection params + visual treatment
- `app/src/main/java/io/github/mojri/hesabyar/ui/screens/DashboardScreen.kt` — wire onClick, pass selectedAccountId
- New test file for Compose UI selection consistency

## Phase 2 — Scalability (LazyRow)

### Problem

`AccountSelector` uses plain `Row` with `horizontalScroll` — renders all chips eagerly.

### Fix

1. **Convert to `LazyRow`** — only renders visible chips.
2. **First item:** "همه حساب‌ها" (selectedAccountId == null), followed by active accounts.
3. **No threshold** — LazyRow recycling handles 20+ accounts. Collapse affordance is a future enhancement.

### Files to modify

- `app/src/main/java/io/github/mojri/hesabyar/ui/screens/DashboardScreen.kt` — convert AccountSelector Row to LazyRow

## Phase 3 — Visual Redesign

### New Card Structure

```
┌─────────────────────────────────────────────┐
│  ┌──────┐  Account Name          1,234,567 │
│  │ Icon │  Account Type        +4%  ▲      │
│  └──────┘                                  │
└─────────────────────────────────────────────┘
```

- **Left:** Account type icon (Material Icons) in tinted circle (account color at 12% alpha bg, icon in account color)
- **Center:** Name (`titleSmall`), type displayName (`labelMedium`, `onSurfaceVariant`), trend (`+4%` green / `-12%` red)
- **Right:** Formatted balance (`bodyLarge`, `onSurface`)
- **Selected state:** 2px accent border, check badge top-right, background tint
- **Dimensions:** ~80-90dp height (down from 160dp), min 160dp width

### Account Type → Icon Mapping

| AccountType | Material Icon |
|---|---|
| BANK | `AccountBalance` |
| CASH_WALLET | `Wallet` |
| SAVINGS_INVESTMENT | `Savings` |
| OTHER | `MoreHoriz` |

### Trend Computation

**Data model change:** Add `monthlyDelta: Double` to `AccountDashboardSummary` (both Rust struct and Kotlin data class).

**Formula:**
```
currentNet = currentMonthIncome - currentMonthExpenses
previousNet = previousMonthIncome - previousMonthExpenses
monthlyDelta = (currentNet - previousNet) / max(abs(previousNet), 1)
```

- If previous month had zero activity: show `0.0` (no trend)
- Display: `+4%` in `FinancialColors.IncomeGreen`, `-12%` in `FinancialColors.ExpenseRed`

**Rust implementation:**
- Extend `compute_account_summaries` to accept previous month boundaries
- Add `previous_month_income` and `previous_month_expenses` fields to `AccountDashboardSummary`
- Compute delta in Rust, expose as `monthly_delta: f64`

**Kotlin fallback:**
- Mirror the same computation using `JalaliCalendarHelper` for previous month boundaries
- Filter transactions by previous month window per account

### Files to modify

- `rust/hesabyar-core/src/models/mod.rs` — add `monthly_delta` to `AccountDashboardSummary`
- `rust/hesabyar-core/src/dashboard.rs` — compute previous month + delta in `compute_account_summaries`
- `app/src/main/java/io/github/mojri/hesabyar/ui/UiState.kt` — add `monthlyDelta` to Kotlin `AccountDashboardSummary`
- `app/src/main/java/io/github/mojri/hesabyar/rust/RustMappers.kt` — map new field
- `app/src/main/java/io/github/mojri/hesabyar/domain/usecase/GetDashboardDataUseCase.kt` — compute delta in fallback
- `app/src/main/java/io/github/mojri/hesabyar/ui/components/AccountBalanceCard.kt` — redesign card layout
- New: `app/src/main/java/io/github/mojri/hesabyar/ui/components/AccountTypeIcon.kt` — icon mapping composable

## Phase 4 — Color Propagation

### Current Usage

- `AccountBalanceCard` (accent bar)
- `AccountSelector` (chip tint)
- `AccountManagementScreen` (color picker)
- `ManualTransactionFormSections` (account indicator)

### Propagation

1. **Transaction list rows:** `TransactionMiniItem` — small color dot or left border accent from source account color. For transfers, show both source and destination colors.
2. **Transaction detail:** Account color in account section header.
3. **Analytics charts:** Use account colors for per-account breakdowns.

### Files to modify

- `app/src/main/java/io/github/mojri/hesabyar/ui/screens/DashboardScreen.kt` — pass account color to TransactionMiniItem
- Transaction detail screen — add color indicator
- Analytics screen — use account colors in charts

## Phase 5 — Regression Pass

### Manual QA

1. Archive/unarchive account → verify dashboard updates
2. Switch accounts via chip AND card → verify totals update
3. Select "همه حساب‌ها" → verify totals include all active accounts
4. Verify balance computation matches manual calculation
5. Force Rust fallback → verify Kotlin produces same results
6. Verify trend percentages (no NaN, no Infinity)
7. RTL layout verification
8. Dark mode verification

### Automated Tests

- `GetDashboardDataUseCaseTest`: archived account exclusion
- `AccountBalanceCardTest`: selection state visual (Compose UI)
- `AccountSelectorTest`: chip-card selection consistency

## Implementation Order

1. Phase 0 (data integrity) — no UI changes, safe to merge first
2. Phase 1 + 2 (selection + LazyRow) — can be combined as they're closely related
3. Phase 3 (visual redesign + trend) — depends on Phase 1 for selection state
4. Phase 4 (color propagation) — depends on Phase 3 for card design
5. Phase 5 (regression) — after all phases complete
