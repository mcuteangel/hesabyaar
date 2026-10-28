# Plan 025: Refactor Category Data Layer to use CategoryType End-to-End

- **Issue:** #71
- **PR:** —
- **Created:** 2026-10-28
- **Author:** Claude Code
- **Status:** IN PROGRESS

## 1. Problem Statement

In the category data flow, `CategoryType` type safety currently stops at the use case boundary. While `ManageCategoryUseCase.addCategory(...)` accepts `CategoryType`, `ManageCategoryUseCase.getCategoriesByType(...)` was previously adapted to accept `CategoryType` only by converting it to `type.name` (a raw `String`) before passing it down:

```kotlin
// app/src/main/java/io/github/mojri/hesabyar/domain/usecase/ManageCategoryUseCase.kt:13
fun getCategoriesByType(type: CategoryType): Flow<List<Category>> = repository.getCategoriesByType(type.name)
```

The underlying repository interface (`CategoryOps`), its implementation (`CategoryDelegate`), the Room DAO (`CategoryDao`), and all test fakes still accept a raw `String`:

```kotlin
// app/src/main/java/io/github/mojri/hesabyar/data/Daos.kt:17
@Query("SELECT * FROM categories WHERE type = :type OR type = 'BOTH' ORDER BY isDefault DESC, name ASC")
fun getCategoriesByType(type: String): Flow<List<Category>>
```

This raw `String` boundary introduces several issues:
1. **Lack of Type Safety:** Any caller with access to repository operations could pass arbitrary or invalid strings (e.g. typos, lowercase names) without compile-time validation.
2. **Artificial Serialization at Boundary:** Forcing the use case to call `type.name` is redundant because Room already registers `@TypeConverter` functions for `CategoryType` (`TypeConverters.categoryTypeToString` and `stringToCategoryType`), and the `Category` entity itself declares `val type: CategoryType`.
3. **Inconsistent Architecture:** Different category methods handle typing inconsistently (`addCategory` and `Category` entity use `CategoryType`, whereas `getCategoriesByType` drops to `String` in the data layer).

### In-Scope Files

| File | Current Signature | Target Signature |
|---|---|---|
| `app/src/main/java/io/github/mojri/hesabyar/data/Daos.kt:17` | `fun getCategoriesByType(type: String): Flow<List<Category>>` | `fun getCategoriesByType(type: CategoryType): Flow<List<Category>>` |
| `app/src/main/java/io/github/mojri/hesabyar/data/RepositoryOps.kt:48` | `fun getCategoriesByType(type: String): Flow<List<Category>>` | `fun getCategoriesByType(type: CategoryType): Flow<List<Category>>` |
| `app/src/main/java/io/github/mojri/hesabyar/data/CategoryDelegate.kt:13` | `fun getCategoriesByType(type: String): Flow<List<Category>>` | `fun getCategoriesByType(type: CategoryType): Flow<List<Category>>` |
| `app/src/main/java/io/github/mojri/hesabyar/domain/usecase/ManageCategoryUseCase.kt:13` | Calls `repository.getCategoriesByType(type.name)` | Calls `repository.getCategoriesByType(type)` |
| `app/src/test/java/io/github/mojri/hesabyar/ui/FakeRepository.kt:73` | `override fun getCategoriesByType(type: String)` | `override fun getCategoriesByType(type: CategoryType)` |
| `app/src/test/java/io/github/mojri/hesabyar/domain/usecase/FakeRepository.kt:62` | `override fun getCategoriesByType(type: String)` | `override fun getCategoriesByType(type: CategoryType)` |
| `app/src/test/java/io/github/mojri/hesabyar/ui/InstallmentViewModelTest.kt:99` | `override fun getCategoriesByType(type: String)` | `override fun getCategoriesByType(type: CategoryType)` |

### Files to Create / Expand for Testing

| File | Purpose |
|---|---|
| `app/src/test/java/io/github/mojri/hesabyar/domain/usecase/ManageCategoryUseCaseTest.kt` <!-- check-docs: ignore --> | New unit test suite verifying `ManageCategoryUseCase` delegates `getCategoriesByType(CategoryType)` and all category operations |
| `app/src/test/java/io/github/mojri/hesabyar/RepositoryLogicTest.kt` | Add in-memory Room test verifying `getCategoriesByType(CategoryType)` query binding and `BOTH` matching |

## 2. Goals & Acceptance Criteria

- [ ] **Criterion 1 (Type-Safe Data Layer):**
  - `CategoryDao.getCategoriesByType` accepts `CategoryType`.
  - `CategoryOps.getCategoriesByType` accepts `CategoryType`.
  - `CategoryDelegate.getCategoriesByType` accepts `CategoryType`.
  - No raw `String` parameter remains for `getCategoriesByType` anywhere in production code.

- [ ] **Criterion 2 (Direct Forwarding at Use Case Layer):**
  - `ManageCategoryUseCase.getCategoriesByType(type: CategoryType)` passes `type` directly to `repository.getCategoriesByType(type)` without `.name` string conversion.

- [ ] **Criterion 3 (Test Fakes & Suite Consistency):**
  - All repository test fakes (`ui/FakeRepository.kt`, `domain/usecase/FakeRepository.kt`, and `InstallmentViewModelTest.kt`) implement the updated `CategoryType` signature.
  - New comprehensive unit test suite in `ManageCategoryUseCaseTest.kt` covering all methods of `ManageCategoryUseCase`.
  - Integration/logic test in `RepositoryLogicTest.kt` verifying Room query execution with `CategoryType.EXPENSE`, `CategoryType.INCOME`, and `CategoryType.BOTH`.

- [ ] **Criterion 4 (Clean Build & Static Analysis):**
  - `./gradlew testDebugUnitTest --no-daemon` passes with 0 failures.
  - `./gradlew ktlintCheck detekt --no-daemon` passes with 0 findings.
  - `python scripts/check_docs.py` succeeds with no broken references.

## 3. Implementation Steps

### Phase 0: Baseline & Branch Verification (Completed)
1. Ensure the working tree is clean. (Verified on branch `refactor/issue-71-category-type-end-to-end` from `origin/main`)
2. Check out a dedicated branch `refactor/issue-71-category-type-end-to-end`. (Completed)
3. Run baseline unit tests:
   ```bash
   ./gradlew testDebugUnitTest --no-daemon
   ```
4. Verify baseline passes before applying any edits. (Completed: BUILD SUCCESSFUL, exit code 0)

### Phase 1: Data Layer Refactoring (Completed)
1. Edit `app/src/main/java/io/github/mojri/hesabyar/data/Daos.kt`:
   - Updated `CategoryDao.getCategoriesByType` to accept `CategoryType`. (Completed)
2. Edit `app/src/main/java/io/github/mojri/hesabyar/data/RepositoryOps.kt`:
   - Updated `CategoryOps.getCategoriesByType` to accept `CategoryType`. (Completed)
3. Edit `app/src/main/java/io/github/mojri/hesabyar/data/CategoryDelegate.kt`:
   - Updated `CategoryDelegate.getCategoriesByType` to pass `type: CategoryType`. (Completed)

### Phase 2: Domain Layer Update (Completed)
1. Edit `app/src/main/java/io/github/mojri/hesabyar/domain/usecase/ManageCategoryUseCase.kt`:
   - Updated `getCategoriesByType` to forward `type: CategoryType` directly to `repository.getCategoriesByType(type)`. (Completed)

### Phase 3: Test Fakes and Test Suite Updates
1. Edit `app/src/test/java/io/github/mojri/hesabyar/ui/FakeRepository.kt`:
   - Update `getCategoriesByType` signature to `(type: CategoryType)`.
2. Edit `app/src/test/java/io/github/mojri/hesabyar/domain/usecase/FakeRepository.kt`:
   - Update `getCategoriesByType` signature to `(type: CategoryType)`.
3. Edit `app/src/test/java/io/github/mojri/hesabyar/ui/InstallmentViewModelTest.kt`:
   - Update `getCategoriesByType` signature to `(type: CategoryType)`.
4. Create `app/src/test/java/io/github/mojri/hesabyar/domain/usecase/ManageCategoryUseCaseTest.kt` <!-- check-docs: ignore -->:
   - Test `getCategoriesByType` passes `CategoryType` correctly and returns matching flow.
   - Test `addCategory` with `CategoryType`.
   - Test `getCategoryById`, `getCategoryByKey`, `deleteCategory`, and `allCategories` delegation.
5. Edit `app/src/test/java/io/github/mojri/hesabyar/RepositoryLogicTest.kt`:
   - Add test `getCategoriesByTypeFiltersCorrectlyWithBoth()` asserting that `repo.getCategoriesByType(CategoryType.EXPENSE)` retrieves both `EXPENSE` and `BOTH` categories, but excludes `INCOME` categories.

### Phase 4: Verification & Static Analysis
1. Run code formatting and static analysis:
   ```bash
   ./gradlew ktlintFormat ktlintCheck detekt --no-daemon
   ```
2. Run test suite:
   ```bash
   ./gradlew testDebugUnitTest --no-daemon
   ```
3. Run doc reference check:
   ```bash
   python scripts/check_docs.py
   ```

## 4. Evidence & Verification Plan

### Verification Commands

```bash
# 1. Format and static analysis
./gradlew ktlintFormat ktlintCheck detekt --no-daemon

# 2. Targeted test execution
./gradlew testDebugUnitTest --tests "io.github.mojri.hesabyar.domain.usecase.ManageCategoryUseCaseTest" --no-daemon
./gradlew testDebugUnitTest --tests "io.github.mojri.hesabyar.RepositoryLogicTest" --no-daemon

# 3. Full unit test suite
./gradlew testDebugUnitTest --no-daemon

# 4. Documentation link check
python scripts/check_docs.py
```

### Required Evidence
- Raw console output of `ktlintCheck` and `detekt` (0 errors).
- Raw test output with test case names from `ManageCategoryUseCaseTest` and `RepositoryLogicTest`.
- `git diff` showing the replacement of `String` with `CategoryType` across the 4 production files and 3 test fake files.

## 5. Risks, Dependencies & STOP Conditions

### Risks & Mitigations
- **Room SQLite Query Binding:** Room uses `TypeConverters` to bind parameters in `@Query`. Since `categoryTypeToString` converts `CategoryType` to `type?.name`, Room will bind `CategoryType.EXPENSE` as string `'EXPENSE'` to `:type`. The SQL clause `WHERE type = :type OR type = 'BOTH'` continues to work identically. Verified by in-memory Room test in `RepositoryLogicTest`.
- **Database Schema Invariance:** The database column `categories.type` remains text-based. No Room schema version bump or migration is needed.

### STOP Conditions
- **STOP if Room compilation fails on `@Query` parameter binding.** If Room requires explicit type mapping or fails annotation processing, stop and inspect Room type-converter registration before proceeding.
- **STOP if any unpredicted caller of `getCategoriesByType(String)` exists outside the identified files.**
