# Phase 1: Extract the Domain/Data Layer

## Prerequisites

- **Previous phase:** Phase 0 must be complete (4 bugs fixed)
- **Pending decisions:**
  - Decision #1 (name uniqueness) — resolve it before you write `AccountValidator`
  - Decision #2 (FK constraint) — resolve it before you write the Room migration (if it is selected)
  - Decision #4 (color) — it must already be confirmed in Phase 0

## Context

The current files `Daos.kt`, `HesabyarRepository.kt`, and `HesabyarRepositoryInterface.kt` hold all the DAOs and all the Repositories of the app. `AccountViewModel` runs business logic directly, with no intermediate use case. This phase introduces the domain layer, and it separates account CRUD from the rest of the app.

## Exact Goal of This Phase

Build an independent domain and data layer for accounts. Add a separate `AccountDao`, an `AccountRepository` interface and implementation, five use cases, and `AccountValidator`. At the end of this phase, `AccountViewModel` must use the use cases (not the Repository directly).

## Files Involved

### New files (create them)
| File | Description |
|---|---|
| `app/src/main/java/io/github/mojri/hesabyar/data/account/AccountDao.kt` | Extract from `Daos.kt` |
| `app/src/main/java/io/github/mojri/hesabyar/data/account/AccountRepository.kt` | Interface |
| `app/src/main/java/io/github/mojri/hesabyar/data/account/AccountRepositoryImpl.kt` | Implementation |
| `app/src/main/java/io/github/mojri/hesabyar/domain/usecase/account/AddAccountUseCase.kt` | Add an account |
| `app/src/main/java/io/github/mojri/hesabyar/domain/usecase/account/UpdateAccountUseCase.kt` | Edit an account |
| `app/src/main/java/io/github/mojri/hesabyar/domain/usecase/account/DeleteAccountUseCase.kt` | Delete an account |
| `app/src/main/java/io/github/mojri/hesabyar/domain/usecase/account/ArchiveAccountUseCase.kt` | Archive |
| `app/src/main/java/io/github/mojri/hesabyar/domain/usecase/account/GetAccountsUseCase.kt` | Account list |
| `app/src/main/java/io/github/mojri/hesabyar/domain/validation/AccountValidator.kt` | Validation rules |
| `app/src/main/java/io/github/mojri/hesabyar/domain/model/AccountFormModel.kt` | Form model shared by the validator and `AddAccountUseCase` |
| `app/src/main/java/io/github/mojri/hesabyar/domain/validation/ValidationResult.kt` | Validation result type |

### Files to edit
| File | Change |
|---|---|
| `app/src/main/java/io/github/mojri/hesabyar/data/Daos.kt` | Remove `AccountDao` (move it to a separate file) |
| `app/src/main/java/io/github/mojri/hesabyar/data/HesabyarRepository.kt` | No change needed — the account methods already live in `AccountDelegate` |
| `app/src/main/java/io/github/mojri/hesabyar/data/HesabyarRepositoryInterface.kt` | Keep the account-related methods (deprecated until all consumers migrate) — see Special Notes (line 153) |
| `app/src/main/java/io/github/mojri/hesabyar/di/RepositoryModule.kt` | Add the `AccountRepository` binding |
| `app/src/main/java/io/github/mojri/hesabyar/di/DatabaseModule.kt` | Add the `AccountDao` provision |
| `app/src/main/java/io/github/mojri/hesabyar/ui/AccountViewModel.kt` | Change from Repository to use cases |

### New files (tests)
| File | Description |
|---|---|
| `app/src/test/java/io/github/mojri/hesabyar/domain/validation/AccountValidatorTest.kt` | Test the validator |
| `app/src/test/java/io/github/mojri/hesabyar/domain/usecase/account/AddAccountUseCaseTest.kt` | Test the use case |
| `app/src/test/java/io/github/mojri/hesabyar/domain/usecase/account/DeleteAccountUseCaseTest.kt` | Test the use case |

## Execution Steps

### Step 1.1: Extract AccountDao

- Move the `AccountDao` interface from `Daos.kt` (lines 342-376) to the new file `data/account/AccountDao.kt`
- Confirm that the Room annotations (`@Dao`, `@Query`, `@Insert`, `@Update`, `@Delete`) are kept
- Remove `AccountDao` from the main `Daos.kt`
- In `DatabaseModule.kt`, confirm that `database.accountDao()` still works (Room builds DAOs from interfaces)
- **Rollback:** `git checkout HEAD -- app/src/main/java/.../data/Daos.kt`

### Step 1.2: Create the AccountRepository interface

- Create the new file `data/account/AccountRepository.kt`
- Include these methods:
  ```kotlin
  interface AccountRepository {
    val allAccounts: Flow<List<AccountEntity>>
    suspend fun getActiveAccounts(): List<AccountEntity>
    suspend fun getAllAccounts(): List<AccountEntity>
    suspend fun getAccountById(id: Long): AccountEntity?
    suspend fun insertAccount(account: AccountEntity): Long
    suspend fun updateAccount(account: AccountEntity)
    suspend fun deleteAccount(account: AccountEntity)
    suspend fun getTransactionCountForAccount(accountId: Long): Int
  }
  ```
- **Rollback:** Delete the file

### Step 1.3: Implement AccountRepositoryImpl

- The account data-access methods already live in the checked-in `AccountDelegate` (which implements `AccountOps`); there is nothing left to move out of `HesabyarRepository.kt`
- Create the new file `data/account/AccountRepositoryImpl.kt`
- Use `@Inject constructor(private val accountOps: AccountOps)` and delegate each `AccountRepository` method to it (this keeps the existing `database.withTransaction` in `AccountDelegate.deleteAccount`)
- In `RepositoryModule.kt`:
  - Bind `AccountRepository`: `@Binds abstract fun bindAccountRepository(impl: AccountRepositoryImpl): AccountRepository`
  - Or use `@Provides` with an `AccountOps` parameter
- **Rollback:** Delete the file and revert `RepositoryModule.kt`

### Step 1.4: Create AccountValidator

- File `domain/validation/ValidationResult.kt`:
  ```kotlin
  sealed interface ValidationResult {
    data object Valid : ValidationResult
    data class Invalid(val errors: Map<String, String>) : ValidationResult
    data class Warning(val warnings: Map<String, String>) : ValidationResult
  }
  ```
- File `domain/model/AccountFormModel.kt` (define it here, in Phase 1): the form type shared by the validator and `AddAccountUseCase`, so the validator can feed the use case without conversion:
  ```kotlin
  data class AccountFormModel(
    val name: String,
    val type: AccountType,
    val bankName: String = "",
    val cardNumber: String = "",
    val accountNumber: String = "",
    val iban: String = "",
    val initialBalance: String = "0",
    val color: Long = AccountEntity.DEFAULT_COLOR,
  )
  ```
  (`AccountFormModel` is the domain-layer form type. Phase 2's `AccountFormState` is the UI-layer equivalent — it adds the `errors` map.)
- **Scope:** this validator covers form-input validation at the UI boundary. New business rules, calculations, and rule-driven validations go to the Rust core per ADR-001.
- File `domain/validation/AccountValidator.kt`:
  - `fun validate(form: AccountFormModel): ValidationResult`
  - Rules (as in decision #1):
    - `name`: not empty, at most 100 characters
    - `type`: valid
    - `cardNumber`: if it is filled, it must have 16 digits
    - `iban`: if it is filled, it must match the regex `^IR\d{24}$`
    - `initialBalance`: `toLongOrNull()` must succeed
  - Duplicate-name check (see decision #1 in `00-checklist.md` — resolve it before this phase):
    - **"strict" (reject):** `validate` returns `Invalid(mapOf("name" to "duplicate"))` when `getAllAccounts()` already contains the name
    - **"warning" (non-blocking):** `validate` returns `Warning(mapOf("name" to "duplicate"))`; the save proceeds and the warning is shown non-blockingly next to the form
  - Add `AccountValidatorTest` cases for the selected option: strict → a rejection test; warning → a save-allowed test
- **Rollback:** Delete the files

### Step 1.5: Create the use cases

Each use case is a separate file:

**AddAccountUseCase:**
- `suspend operator fun invoke(form: AccountFormModel): Long`
- Validate: proceed on `Valid` or `Warning` (surface warnings non-blockingly); abort on `Invalid` → insert → return the ID

**UpdateAccountUseCase:**
- `suspend operator fun invoke(account: AccountEntity)`
- `account.copy(updatedAt = System.currentTimeMillis())` → update

**DeleteAccountUseCase:**
- `suspend operator fun invoke(account: AccountEntity)`
- Delegate to the repository, which must retain the existing atomic deletion safeguards (as in the checked-in `AccountDelegate.deleteAccount`): inside a single transaction, reject when the account is the last active one (`CannotDeleteLastActiveAccountException`), reject when `getTransactionCountForAccount(account.id) > 0`, otherwise delete. Do not replace this with a separate count-then-delete sequence — the checks and the delete must stay atomic

**ArchiveAccountUseCase:**
- `suspend operator fun invoke(account: AccountEntity)`
- `account.copy(isArchived = true, updatedAt = System.currentTimeMillis())` → update

**GetAccountsUseCase:**
- `val allAccounts: Flow<List<AccountEntity>>` → repository.allAccounts
- `suspend fun getActiveAccounts(): List<AccountEntity>` → repository.getActiveAccounts()

### Step 1.6: Update AccountViewModel

- Change `AccountViewModel` to use the use cases instead of `HesabyarRepositoryInterface`
- Constructor: `@Inject constructor(private val addAccount: AddAccountUseCase, private val updateAccount: UpdateAccountUseCase, ...)`
- The `addAccount()` method: call `addAccount(form)`
- The `canDeleteAccount()` method: `deleteAccountUseCase.canDelete(accountId)` (or a separate method)
- **Important:** The external behavior must not change — only the internal dependency changes

### Step 1.7: Write the tests

- `AccountValidatorTest`: test all the validation rules (valid, empty name, duplicate name, invalid IBAN, invalid card number)
- `AddAccountUseCaseTest`: test with a FakeRepository — a successful insert, and a validation error
- `DeleteAccountUseCaseTest`: test a successful delete, and a delete that has transactions (it must throw an error)

## Special Notes for This Phase

- From the central checklist:
  - **R1** (Rust/Kotlin alignment): this phase makes no calculation change — only the structure changes
  - **R5** (JNI state): if no Rust code is touched, R5 does not need to run
- **Important:** `HesabyarRepositoryInterface` must still have the account methods, because the other ViewModels (Dashboard, Analytics, Backup) still use them. Only `AccountViewModel` switches to the use cases.
- If `HesabyarRepository` becomes empty of account methods, confirm that no other caller remains (grep!)

## Acceptance Criteria

- [ ] A separate `AccountDao.kt` exists, and `Daos.kt` no longer has `AccountDao`
- [ ] The `AccountRepository` interface and `AccountRepositoryImpl` exist
- [ ] `AccountValidator` covers all the validation rules
- [ ] `AccountValidatorTest` → all tests pass
- [ ] `AddAccountUseCaseTest` → all tests pass
- [ ] `DeleteAccountUseCaseTest` → all tests pass
- [ ] `AccountViewModel` uses the use cases (not the Repository directly)
- [ ] The `AccountViewModel` methods keep the previous behavior (the same signature)
- [ ] The DI modules are updated, and `./gradlew test --rerun-tasks --no-daemon` → BUILD SUCCESSFUL
- [ ] `./gradlew ktlintCheck detekt --no-daemon` → no errors
- [ ] A grep confirms: `AccountViewModel` no longer has a direct `repository.insertAccount`

## Rollback

```bash
# Roll back the whole Phase 1:
git log --oneline -10  # Identify the Phase 1 commits
git revert <last-phase-1-commit>..<first-phase-1-commit>

# Or roll back one specific step:
git checkout HEAD -- app/src/main/java/.../data/Daos.kt  # Roll back step 1.1
```
