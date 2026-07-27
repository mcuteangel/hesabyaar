# 009 — Multi-Account / Multi-Wallet Support

**Status:** Proposed
**Created:** 2026-07-27
**Branch:** `feature/multi-account-wallet`

---

## Overview

Introduce first-class support for multiple financial accounts (bank accounts, cash wallets, savings/investment vehicles) into Hesabyaar. This feature enables users to:

1. **Categorize** their money across distinct accounts (bank, wallet, savings).
2. **Track** income, expenses, and transfers per account.
3. **View** per-account balances, analytics, and a consolidated net-worth summary.
4. **Transfer** funds between accounts with proper double-entry bookkeeping.

All monetary calculations remain strictly integer-based (`Long` in Kotlin / `i64` in Rust) — zero floating-point money math.

---

## Scope

| Layer | Changes |
|-------|---------|
| **Room DB** | New `accounts` table, `AccountEntity`, `AccountDao`, `AccountType` enum, `MIGRATION_5_6` |
| **Rust Core** | New `Account` struct, per-account filtering in `dashboard.rs` / `analytics.rs`, backup schema bump to v2 |
| **FFI Bridge** | Updated `RustBridge.kt`, `RustMappers.kt`, new `AccountMapper` |
| **Repository** | Account CRUD methods, account-scoped queries |
| **Domain** | Refactored `GetDashboardDataUseCase`, `SubmitManualTransactionUseCase` (transfer support) |
| **ViewModels** | New `AccountViewModel`, updated `DashboardViewModel` with account filtering |
| **UI** | `AccountManagementScreen`, `AccountSelector` component, updated `DashboardHeader`, updated `ManualTransactionDialog` |

---

## Phase 1 — Room Database

### 1.1 New Entity: `AccountEntity`

```kotlin
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val name: String,
    val type: AccountType,
    val bankName: String? = null,
    val cardNumber: String? = null,
    val accountNumber: String? = null,
    val iban: String? = null,
    val initialBalance: Long = 0L,
    val color: Long = 0xFF4CAF50,
    val icon: String? = null,
    val isArchived: Boolean = false,
    val displayOrder: Int = 0
)
```

### 1.2 Enum: `AccountType`

```kotlin
enum class AccountType {
    BANK,
    CASH_WALLET,
    SAVINGS_INVESTMENT,
    OTHER
}
```

### 1.3 Update `Transaction` Entity

Add two columns:

| Column | Type | Default | Purpose |
|--------|------|---------|---------|
| `accountId` | `Long` | `1` | Source account (FK → accounts.id) |
| `destinationAccountId` | `Long?` | `null` | Target account for transfers |

### 1.4 DAO: `AccountDao`

```kotlin
@Dao
interface AccountDao {
    @Query("SELECT * FROM accounts WHERE isArchived = 0 ORDER BY displayOrder, name")
    fun getActiveAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts ORDER BY displayOrder, name")
    fun getAllAccounts(): Flow<List<AccountEntity>>

    @Query("SELECT * FROM accounts WHERE id = :id")
    suspend fun getById(id: Long): AccountEntity?

    @Insert
    suspend fun insert(account: AccountEntity): Long

    @Update
    suspend fun update(account: AccountEntity)

    @Delete
    suspend fun delete(account: AccountEntity)

    @Query("SELECT COUNT(*) FROM transactions WHERE accountId = :accountId OR destinationAccountId = :accountId")
    suspend fun getTransactionCountForAccount(accountId: Long): Int
}
```

### 1.5 Migration: `MIGRATION_5_6`

```sql
-- 1. Create accounts table
CREATE TABLE IF NOT EXISTS accounts (
    id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
    name TEXT NOT NULL,
    type TEXT NOT NULL,
    bankName TEXT,
    cardNumber TEXT,
    accountNumber TEXT,
    iban TEXT,
    initialBalance INTEGER NOT NULL DEFAULT 0,
    color INTEGER NOT NULL DEFAULT 4283214511,
    icon TEXT,
    isArchived INTEGER NOT NULL DEFAULT 0,
    displayOrder INTEGER NOT NULL DEFAULT 0
);

-- 2. Insert default main bank account
INSERT INTO accounts (id, name, type, initialBalance, displayOrder)
VALUES (1, 'حساب اصلی', 'BANK', 0, 0);

-- 3. Add account columns to transactions
ALTER TABLE transactions ADD COLUMN accountId INTEGER NOT NULL DEFAULT 1;
ALTER TABLE transactions ADD COLUMN destinationAccountId INTEGER DEFAULT NULL;
```

**Safety:** All legacy transactions automatically get `accountId = 1` (the default account). No data loss.

### 1.6 Update `AppDatabase`

- Bump `version` from 5 to 6.
- Add `AccountEntity::class` to `entities` array.
- Register `MIGRATION_5_6`.
- Add `@TypeConverter` for `AccountType` enum.

---

## Phase 2 — Rust Core

### 2.1 New Struct: `Account`

```rust
#[derive(Debug, Clone, Serialize, Deserialize, UniFFIRecord)]
#[serde(rename_all = "camelCase")]
pub struct Account {
    pub id: i64,
    pub name: String,
    pub account_type: String,
    pub bank_name: Option<String>,
    pub card_number: Option<String>,
    pub account_number: Option<String>,
    pub iban: Option<String>,
    pub initial_balance: i64,
    pub color: i64,
    pub icon: Option<String>,
    pub is_archived: bool,
    pub display_order: i32,
}
```

### 2.2 Update `Transaction` Struct

Add fields:

```rust
pub account_id: i64,
pub destination_account_id: Option<i64>,
```

### 2.3 Update `DashboardData`

Add:

```rust
pub accounts: Vec<AccountDashboardSummary>,
pub total_net_worth: i64,
```

New record type:

```rust
#[derive(Debug, Clone, Serialize, Deserialize, UniFFIRecord)]
pub struct AccountDashboardSummary {
    pub account_id: i64,
    pub account_name: String,
    pub account_type: String,
    pub balance: i64,
    pub monthly_income: i64,
    pub monthly_expenses: i64,
}
```

### 2.4 Update `AnalyticsData`

Add:

```rust
pub accounts: Vec<AccountAnalytics>,
```

New record type:

```rust
#[derive(Debug, Clone, Serialize, Deserialize, UniFFIRecord)]
pub struct AccountAnalytics {
    pub account_id: i64,
    pub account_name: String,
    pub monthly_data: Vec<MonthlyData>,
    pub category_breakdown: Vec<CategoryBreakdown>,
}
```

### 2.5 Update Calculation Functions

**`compute_dashboard_data`**: Add `account_id: Option<i64>` parameter. When `Some(id)`, filter transactions by that account. When `None`, compute across all accounts (aggregate).

**`compute_analytics`**: Same pattern — add optional account filter.

### 2.6 Update `BackupPayload`

```rust
pub struct BackupPayload {
    pub version: i32,  // Bump to 2
    pub accounts: Vec<Account>,  // NEW
    // ... existing fields with #[serde(default)]
}
```

Bump `BACKUP_SCHEMA_VERSION` from `1` to `2`.

---

## Phase 3 — FFI Bridge

### 3.1 New File: `AccountMapper.kt`

Maps between `AccountEntity` ↔ Rust `Account` over FFI.

### 3.2 Update `RustBridge.kt`

- Accept `account_id: Option<i64>` in `computeDashboardDataSync()` and `computeAnalyticsSync()`.
- Map `List<AccountEntity>` → `Array<Account>` for FFI calls.

### 3.3 Update `RustMappers.kt`

- Add `Account.toEntity()` extension.
- Add `AccountEntity.toRustAccount()` extension.
- Update `Transaction` mapping to include `accountId` and `destinationAccountId`.

---

## Phase 4 — Repository & Domain

### 4.1 Extend `HesabyarRepositoryInterface`

Add:

```kotlin
fun getAllAccounts(): Flow<List<AccountEntity>>
fun getActiveAccounts(): Flow<List<AccountEntity>>
suspend fun getAccountById(id: Long): AccountEntity?
suspend fun insertAccount(account: AccountEntity): Long
suspend fun updateAccount(account: AccountEntity)
suspend fun deleteAccount(account: AccountEntity)
suspend fun getTransactionCountForAccount(accountId: Long): Int
```

### 4.2 Update `HesabyarRepository`

Implement the new interface methods using `AccountDao`.

### 4.3 Refactor `GetDashboardDataUseCase`

- Accept optional `accountId: Long?` parameter.
- Pass `account_id` to Rust FFI call.
- When `accountId` is null, compute total net worth (sum of all account balances).
- Return `AccountDashboardSummary` list in `DashboardData`.

### 4.4 Refactor `SubmitManualTransactionUseCase`

- Add `accountId: Long` to `SubmitManualTransactionRequest`.
- Add optional `destinationAccountId: Long?` for transfer transactions.
- Validate that source ≠ destination for transfers.
- For transfers: create a pair of transactions (expense from source, income to destination) or a single transfer record.

---

## Phase 5 — ViewModels

### 5.1 New: `AccountViewModel.kt`

```kotlin
@HiltViewModel
class AccountViewModel @Inject constructor(
    private val repository: HesabyarRepositoryInterface
) : ViewModel() {
    val accounts: StateFlow<List<AccountEntity>>
    val activeAccounts: StateFlow<List<AccountEntity>>

    fun addAccount(account: AccountEntity)
    fun updateAccount(account: AccountEntity)
    fun archiveAccount(account: AccountEntity)
    fun deleteAccount(account: AccountEntity)
}
```

### 5.2 Update: `DashboardViewModel.kt`

- Add `selectedAccountId: MutableStateFlow<Long?>` (null = all accounts).
- Expose `accountSummaries: StateFlow<List<AccountDashboardSummary>>`.
- Expose `totalNetWorth: StateFlow<Long>`.
- Filter dashboard data based on `selectedAccountId`.

---

## Phase 6 — UI

### 6.1 New: `AccountManagementScreen.kt`

- List of accounts with type icons, balances, and edit/archive actions.
- FAB to add new account.
- Swipe-to-archive (soft delete).
- Tap to view account details and filtered transactions.

### 6.2 New: `AccountSelector.kt` (Reusable Component)

- Dropdown/chip selector showing active accounts.
- "All Accounts" option for aggregate view.
- Used in `DashboardHeader` and `ManualTransactionDialog`.

### 6.3 Update: `DashboardHeader.kt`

- Integrate `AccountSelector` at the top.
- Show per-account balance or total net worth based on selection.

### 6.4 Update: `ManualTransactionDialog.kt`

- Add account selector for source account.
- Add optional "Transfer to" account field (visible when type = transfer).
- Validate: source ≠ destination.

### 6.5 Update: `DashboardScreen.kt`

- Wire up account filtering from `DashboardViewModel`.

### 6.6 Update Navigation

Add routes:

```kotlin
object AccountManagement : Screen("accounts")
object AccountDetail : Screen("accounts/{accountId}")
```

---

## Phase 7 — Backup & Restore

### 7.1 Schema Version Bump

- `BACKUP_SCHEMA_VERSION = 2` (Rust) → `BuildConfig.BACKUP_SCHEMA_VERSION = 2` (Kotlin).

### 7.2 Export

- Include `accounts` list in backup payload.

### 7.3 Import

- Deserialize `accounts` (with `#[serde(default)]` for backward compat with v1 backups).
- Link imported transactions to their accounts.
- For v1 backups without accounts: create default account and link all transactions.

---

## Phase 8 — Testing

### 8.1 Unit Tests

| Test | Location |
|------|----------|
| `MIGRATION_5_6` correctness | `MigrationTest.kt` |
| Account CRUD operations | `AccountDaoTest.kt` |
| Per-account balance calculation | `DashboardCalculationTest.kt` |
| Transfer logic (double-entry) | `TransferLogicTest.kt` |
| Backup v2 export/import round-trip | `BackupV2Test.kt` |

### 8.2 Rust Tests

- Account filtering in `dashboard.rs`
- Per-account analytics in `analytics.rs`
- Net worth aggregation
- Backup serialization with accounts

---

## Migration Safety Checklist

- [x] Default account created before linking transactions
- [x] All legacy transactions get `accountId = 1` (no orphans)
- [x] Backup v1 still importable (serde defaults)
- [x] No destructive column changes (only additions)
- [x] SQLCipher encryption unaffected

---

## Files to Create

| File | Purpose |
|------|---------|
| `app/src/main/java/.../data/AccountEntity.kt` | Account entity + enum |
| `app/src/main/java/.../data/AccountDao.kt` | Account DAO interface |
| `app/src/main/java/.../di/AccountModule.kt` | Hilt module (if needed) |
| `app/src/main/java/.../ui/AccountViewModel.kt` | Account ViewModel |
| `app/src/main/java/.../ui/screens/AccountManagementScreen.kt` | Account list/management UI |
| `app/src/main/java/.../ui/screens/account/AccountSelector.kt` | Reusable selector component |
| `app/src/test/.../data/MigrationTest.kt` | Migration unit test |
| `app/src/test/.../data/AccountDaoTest.kt` | DAO unit test |

## Files to Modify

| File | Changes |
|------|---------|
| `app/src/main/java/.../data/Entities.kt` | Add `AccountEntity`, update `Transaction` |
| `app/src/main/java/.../data/AppDatabase.kt` | Bump version, add entity, register migration |
| `app/src/main/java/.../data/Daos.kt` | Add `AccountDao` |
| `app/src/main/java/.../data/TypeConverters.kt` | Add `AccountType` converter |
| `app/src/main/java/.../data/HesabyarRepositoryInterface.kt` | Add account methods |
| `app/src/main/java/.../data/HesabyarRepository.kt` | Implement account methods |
| `app/src/main/java/.../data/BackupModels.kt` | Update backup payload |
| `app/src/main/java/.../domain/usecase/GetDashboardDataUseCase.kt` | Account filtering |
| `app/src/main/java/.../domain/usecase/SubmitManualTransactionUseCase.kt` | Transfer support |
| `app/src/main/java/.../ui/DashboardViewModel.kt` | Account state management |
| `app/src/main/java/.../ui/screens/DashboardScreen.kt` | Wire account selector |
| `app/src/main/java/.../ui/screens/dashboard/components/DashboardHeader.kt` | Integrate selector |
| `app/src/main/java/.../ui/screens/dashboard/dialogs/ManualTransactionDialog.kt` | Account pickers |
| `app/src/main/java/.../ui/navigation/NavGraph.kt` | Add account routes |
| `rust/hesabyar-core/src/models/mod.rs` | Account struct, update Transaction |
| `rust/hesabyar-core/src/dashboard.rs` | Per-account filtering |
| `rust/hesabyar-core/src/analytics.rs` | Per-account analytics |
| `rust/hesabyar-core/src/lib.rs` | Updated UniFFI exports |
| `app/src/main/java/.../rust/RustBridge.kt` | Pass account_id to FFI |
| `app/src/main/java/.../rust/RustMappers.kt` | Account mapping |

---

## Estimated Effort

| Phase | Effort |
|-------|--------|
| Phase 1: Room DB | Medium |
| Phase 2: Rust Core | High |
| Phase 3: FFI Bridge | Medium |
| Phase 4: Repository & Domain | Medium |
| Phase 5: ViewModels | Medium |
| Phase 6: UI | High |
| Phase 7: Backup | Low |
| Phase 8: Testing | Medium |
| **Total** | **High** |

---

## References

- `docs/DATABASE_SCHEMA.md` — Current schema documentation
- `docs/TECH_STACK.md` — Dependency list
- `docs/architecture/ARCHITECTURE.md` — Architecture guide
- `AGENTS.md` — Agent development guidelines
