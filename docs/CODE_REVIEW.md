# Code Review — حسابیار (Hesabyaar)

**Review date:** 2026-06-25
**Branch:** `feature/phase-0-setup`
**Scope:** The whole `app/` module — the `data/`, `api/`, `ui/`, `reminder/` layers

> This document is the result of a review of the current code. It is the base of the project improvement phases (Phase 1–4). Each item has a priority label and a proposed phase.

---

## 1. Executive Summary

| Area | Overall status | Key notes |
|------|-----------|------------|
| **Money model** | ✅ good | `Long` (Rial) everywhere, no `Float/Double` in the data layer |
| **Coroutines** | ✅ good | No `GlobalScope`, correct use of `viewModelScope`/`CoroutineWorker` |
| **Key security** | ✅ good | No hardcoded keys, encrypted storage in `EncryptedSharedPreferences` |
| **Room migration** | ✅ non-destructive | Explicit migration, no `fallbackToDestructiveMigration` |
| **Transaction atomicity** | 🔴 critical | Multi-step financial operations without `@Transaction` |
| **Parser calculation accuracy** | 🔴 critical | The parse path passes through `Double` |
| **AI output validation** | 🟡 weak | No validation on type/hour/amount/confidence |
| **ViewModel testability** | 🟡 weak | No DI, the ViewModels are not unit-testable |
| **Documentation** | ✅ strong | 9 files in `docs/` |
| **Tests** | 🟡 medium | 14 files of pure logic tests, no ViewModel/UI tests |

**Note:** The foundations (coroutines, the money type, key security) are solid. The work of the later phases is mostly **hardening and testability**, not the correction of fundamental bugs.

---

## 2. Critical Findings (CRITICAL)

### 2.1. Multi-step financial operations are not atomic
**File:** `data/HesabyarRepository.kt` (lines 74–110, 117–129, 136–168)
**Proposed phase:** Phase 1 (architecture)

Several operations that must be atomic run in separate calls. No operation is wrapped in `@Transaction` or `db.withTransaction { }`:

- `addPaymentToLoan`: `updateLoan` + `insertPayment` + `insertTransaction` as three separate calls. If the process stops in the middle, the `remainingAmount` of the loan decreases without a `PaymentHistory` record (or the opposite).
- `updateInstallment`: besides the atomicity problem, **every update of an already paid installment inserts a duplicate transaction** (no idempotency check).
- `importBackup` / `replaceAllFromBackup`: `deleteAll` then a loop of inserts. A crash inside the loop leaves the database half-empty, with no rollback.

> There is no use of `@Transaction` / `withTransaction` anywhere in the codebase.

**Action:**
```kotlin
// Add to AppDatabase or Repository
suspend fun <R> withTransaction(block: suspend () -> R): R =
    db.withTransaction { block() }
```
Then wrap all multi-step financial operations and all backup/restore operations in it.

---

### 2.2. Money calculation accuracy in the parser (Double → Long)
**File:** `api/PersianAmountParser.kt:6`, `api/GeminiParser.kt:75,253,303,490`
**Proposed phase:** Phase 2 (AI)

The data model is fully `Long`, but the parse path passes through `Double`, which is unsafe for money:

- `Token.Number(val value: Double)` — it must be `Long`.
- `interpretWithUnits`: `total += (currentNum * token.type.multiplier).toLong()` — a `Double × Long → Double` product, then a truncate.
- `parseSentenceOffline`: `var amountToman = 0.0`, then `(amountToman * 1000).toLong()`.
- `parseJsonResult`: `(json.optDouble("amount", 0.0) * 1000).toLong()`.

Risk: `.toLong()` on `Double` loses accuracy for large values (for example 19 digits). The unit multipliers (`UnitType.multiplier`) are already `Long`, so the complete removal of `Double` from `Token.Number` is possible.

**Action:** Change `Token.Number` to `Long`, and remove all `.toDouble()/.toLong()` in the parse path.

---

## 3. High-Priority Findings (HIGH)

### 3.1. Missing foreign keys and indexes
**File:** `data/Entities.kt`, `data/Daos.kt`
**Proposed phase:** Phase 1 (architecture)

No `ForeignKey` and no `indices` are defined in any Entity:

- Logical relations are not enforced: `Transaction.categoryId → Category.id`, `Transaction.installmentId → Installment.id`, `PaymentHistory.loanId → Loan.id`.
- **Consequence:** The deletion of a `Category`/`Loan`/`Installment` can create orphan rows in `Transaction`/`PaymentHistory`, and a crash in the UI on a null lookup.
- Frequently used columns have no index: `payment_history.loanId` (a per-loan execution in Flow), `transactions.date` (a range scan `WHERE date BETWEEN`).

**Action:** Add a `ForeignKey` with the correct `onDelete` + an `Index` on at least `loanId`, `categoryId`, and `date`. This needs a new Room migration (version 4).

---

### 3.2. Weak validation of AI output
**File:** `api/GeminiParser.kt:69–91` (`parseJsonResult`)
**Proposed phase:** Phase 2 (AI)

The model output is read with `optString`/`optDouble` and default values, and it is accepted without a check:

- `type` is not validated against the allowed enum values (any string is accepted, with a default of `"EXPENSE"`).
- There is no range check on `hour`/`minute` (the model can return `hour=99`).
- There is no check for `amount >= 0`, and no sanity check on `daysFromNow`/`dateOffsetDays`.
- `confidence` is parsed, but it is never used to reject low-confidence results.

**Action:** Add a `validateParsedResult()` function, as in Task 2-1 of the plan.

---

### 3.3. The ViewModels are not unit-testable (no DI)
**File:** All 9 ViewModels in `ui/`
**Proposed phase:** Phase 1 (architecture)

Each ViewModel builds its dependencies internally:
```kotlin
class DashboardViewModel(application: Application) : AndroidViewModel(application) {
    private val database = AppDatabase.getDatabase(application)
    private val repository = HesabyarRepository(database.transactionDao(), …)
```
```
For this reason, the tests only cover the pure logic helpers, not the ViewModels. `InstallmentReminderWorker` also calls `AppDatabase.getDatabase(applicationContext)` directly.

**Action:** Introduce Hilt (Task 1-2), and inject the Repository (as an interface) into the constructors of the ViewModels and the Workers.

---

## 4. Medium-Priority Findings (MEDIUM)

### 4.1. `exportSchema = false` in Room
**File:** `data/AppDatabase.kt:10–14`
**Proposed phase:** Phase 1

With `exportSchema = false`, no `schema/` JSON is produced for migration validation. Because the migrations use heavy manual DDL (including the rename of the column `category TEXT → categoryId INTEGER`), this is fragile.

**Action:** Set `exportSchema = true`, and commit the generated schemas.

---

### 4.2. Two different meanings of "monthly"
**File:** `ui/DashboardViewModel.kt:39` versus `ui/AnalyticsViewModel`
**Proposed phase:** Phase 1

`DashboardViewModel` calculates the income/expense of a "month" as a rolling 30-day window:
```kotlin
val oneMonthAgo = now - (30L * 24L * 60L * 60L * 1000L)
```
But `AnalyticsViewModel` groups correctly by Jalali month. The two screens will disagree on the concept of "this month".

**Action:** Use `JalaliCalendarHelper` in `DashboardViewModel` too.

---

### 4.3. The AI provider abstraction is broken
**File:** `api/AiProvider.kt`
**Proposed phase:** Phase 2

`AiProvider` is an `object` (singleton), not an interface. The provider selection is a `when` on an enum — the addition of a provider means an edit to the `when` (a violation of open/closed). The name `GeminiParser` is also misleading, because it works with any configured provider.

Also, the budget advice logic is implemented twice: `GeminiParser.getBudgetAdvice` and `BudgetAdvisor.getBudgetAdvice`.

**Action:** Extract an `interface AiProvider` (Task 1-1 / 2-2), and unify the budget logic.

---

### 4.4. Repeated day-to-millisecond literals and magic numbers
**File:** `DashboardViewModel.kt:39`, `ReportsScreen.kt:55,131`, `AiProviderConfig.kt:44`, `AiAssistantViewModel.kt:118`, `InstallmentReminderWorker.kt:36`
**Proposed phase:** Phase 1

`24*60*60*1000` is repeated in at least 5 places, with different styles. `-7` (the 7-day overdue window) is also hardcoded.

**Action:** Extract named constants, or use `kotlin.time.Duration`/`TimeUnit`.

---

### 4.5. Giant UI files and inline Persian strings
**File:** `ui/screens/DashboardScreen.kt` (1848 lines), `SettingsScreen.kt` (1439), `SmartAssistantScreen.kt` (1317)
**Proposed phase:** Phase 1 / 3

These files hold `CATEGORY_ICONS_MAP` (30 entries), `formatToman`, `formatPersianDate`, and all the card/dialog composables. They must move to `ui/components/` and `ui/util/`. `strings.xml` is not used — all the Persian strings are inline.

**Action:** Separate the composables, extract the formatters and the icon map, and move the user-facing strings to `res/values-fa/strings.xml`.

---

### 4.6. Inconsistent normalization of Persian digits
**File:** `api/GeminiParser.kt`, `api/PersianAmountParser.kt`
**Proposed phase:** Phase 2

`PersianAmountParser.normalizeText` and `GeminiParser.toArabicDigits` both normalize, but `parseJsonResult`, `parseSentenceOffline`, `extractJalaliDaysFromNow`, and `inferExpenseCategory` work on the **raw** (unnormalized) text. This makes the parse fragile.

**Action:** Normalize once at the input, not case by case in each function.

---

## 5. Low-Priority Findings (LOW)

| # | Problem | File | Phase |
|---|-------|------|-----|
| 5.1 | Magic values `categoryId = ... ?: 1L` (twice), and colors copied between `MIGRATION_2_3` and `Category.DEFAULTS` (two sources of truth) | `HesabyarRepository.kt:93,102,121,124` | P1 |
| 5.2 | The reminder Workers always return `Result.success()`, even on error — no `Result.retry()`/backoff | `reminder/InstallmentReminderWorker.kt` | P1 |
| 5.3 | PII (the full transaction description and `personName`) goes to the LLM without a filter | `api/GeminiParser.kt` | P2 |
| 5.4 | There is no retry/backoff on network errors, and errors go silently to the offline fallback | `api/AiProvider.kt` | P2 |
| 5.5 | Vazirmatn is loaded only from Google Fonts downloadable — there is no bundled `.ttf` fallback (a break on a device without Play Services, or offline on the first launch) | `ui/theme/Type.kt:12–19` | P3 |
| 5.6 | The notification request codes are fragile: `installmentId.toInt()` and `(loanId + 10000).toInt()` can collide or overflow for large IDs | `reminder/NotificationHelper.kt:55,115,135` | P3 |
| 5.7 | `isMinifyEnabled = false` in release (a security/size risk) | `app/build.gradle.kts:42` | P4 |
| 5.8 | Java 11 is active (`sourceCompatibility = VERSION_11`) — newer library versions usually target 17 | `app/build.gradle.kts:50–53` | P0/P1 |
| 5.9 | The default categories are stringly-typed (`type` has no enum and no CHECK constraint) | `data/Entities.kt` | P1 |
| 5.10 | `System.currentTimeMillis()` initializers on Entities make them non-deterministic | `data/Entities.kt:43,56,76` | P1 |

---

## 6. Test Quality

**Present:** 14 test files in `app/src/test/`:
```
AmountQuickFillTest, OfflineParserTest, BudgetAdvisorTest, JalaliCalendarTest,
AiConfigTest, RepositoryLogicTest, ExcelExporterTest, BackupValidationTest,
AiCacheTest, AnalyticsTest, ReminderTest, CategoryTest, TransactionTest,
LoanInstallmentTest
```

| Note | Status |
|------|-------|
| Pure logic coverage | ✅ good (Jalali, parser, repository logic, budget advisor, backup) |
| ViewModel test | ❌ missing (because there is no DI — §3.3) |
| UI test (Compose) | ❌ missing (androidTest is empty) |
| Coverage report | ❌ missing |
| Instrumentation test | ❌ `app/src/androidTest/` is empty | <!-- check-docs: ignore -->

**Phase 4 goal:** coverage above 80%, add ViewModel tests (after Hilt), and add Compose UI tests.

---

## 7. Phased Correction Roadmap

| Phase | Related items from this review |
|-----|----------------------------|
| **Phase 0** | (this document) + `.editorconfig`, ktlint, CI/CD |
| **Phase 1** | §2.1 atomicity, §3.1 FK/Index, §3.3 DI, §4.1 exportSchema, §4.2 monthly, §4.4 constants, §4.5 UI separation, §5.1, §5.2, §5.8, §5.9, §5.10 |
| **Phase 2** | §2.2 parser accuracy, §3.2 AI validation, §4.3 abstraction, §4.6 normalization, §5.3, §5.4 |
| **Phase 3** | §4.5 strings.xml, §5.5 font, §5.6 notification code |
| **Phase 4** | §5.7 minify, test coverage, release |

---

## 8. Code Strengths (keep them)

- ✅ **Unified money model** — `Long` (Rial) in all layers, and the historical migration already corrected the old `Double` mistake.
- ✅ **Structured concurrency** — no `GlobalScope`, and correct use of `viewModelScope`/`CoroutineWorker`/unique work.
- ✅ **Key security** — `EncryptedSharedPreferences`, only the key length is logged, and a sentinel guard is present.
- ✅ **Non-destructive migration** — explicit migration, no `fallbackToDestructiveMigration` (correct for a financial app).
- ✅ **Correct degradation** — every caller routes an AI error to the offline fallback.
- ✅ **Rich documentation** — 9 `docs/` files + README + AGENTS.md.
- ✅ **RTL and the Jalali calendar** — app-wide RTL, Vazirmatn, and use of the Jalali helper in analytics.

---

*This document is a living reference, and you must update it at the end of each phase.*

---

## 9. Rust-First Architecture Criteria

**Business logic policy:** The Rust core (`rust/hesabyar-core`) is the only place for business logic, calculations, rules, validation, and new rule-driven data transformations. Any pull request that contains business logic, calculations, or new validation directly in Kotlin must be flagged in review and routed to Rust. The list of the five permanent exceptions (the Jalali calendar, currency formatting, the offline NLP parser, backup JSON analysis/validation, and AI advice validation) is in [business logic policy (Rust-first)](architecture/ADR-001-rust-sole-implementation.md). These exceptions are permanent, and they are kept as Kotlin fallbacks, otherwise these implementations must be removed. The plan that removes the non-exception fallbacks is in `../plans/2026-08-19-rust-fallback-consolidation-plan.md`, the permanent-exception fallbacks are not in this plan.
