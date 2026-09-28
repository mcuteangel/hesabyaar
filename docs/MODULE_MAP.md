# Hesabyar — Module Map

Package root: `io.github.mojri.hesabyar` under `app/src/main/java/`.

## Android app (`app/src/main/java/io/github/mojri/hesabyar/`)

| Package | Role | Example files |
|---------|------|---------------|
| `.` (root) | App entry point | `HesabyarApp.kt` (Application), `MainActivity.kt` |
| `api/` | AI providers and budget advice | `AiProvider.kt`, `GeminiParser.kt`, `BudgetAdvisor.kt`, `LocalBudgetAdvice.kt` |
| `auth/` | App lock: PIN, biometrics, backup cipher | `PinScreen.kt`, `BiometricHelper.kt`, `BackupCipher.kt`, `LockScreen.kt` |
| `core/` | Shared utilities | `AppLogger.kt`, `MathUtils.kt` |
| `data/` | Data layer: Room, repository, delegates | `AppDatabase.kt`, `HesabyarRepository.kt`, `Daos.kt`, `Entities.kt`, `ExcelExporter.kt` |
| `data/` delegates | Per-entity logic, split from the repository | `TransactionDelegate.kt`, `LoanDelegate.kt`, `PersonDelegate.kt`, `BackupDelegate.kt`, `AccountDelegate.kt`, `InstallmentDelegate.kt`, `BankLoanDelegate.kt`, `CategoryDelegate.kt` |
| `di/` | Hilt dependency-injection modules | `DatabaseModule.kt`, `RepositoryModule.kt`, `UseCaseModule.kt`, `AiModule.kt` |
| `domain/usecase/` | Domain use cases | `ManageTransactionUseCase.kt`, `ParseTransactionUseCase.kt` (Persian parsing), `GetDashboardDataUseCase.kt`, `ManageBackupUseCase.kt`, `GetForecastUseCase.kt` |
| `domain/utils/` | Domain helpers | `PersonNameNormalizer.kt`, `TransactionAmountResolver.kt`, `LoanEditCalculator.kt` |
| `domain/exception/` | Domain exceptions | `CannotDeleteLastActiveAccountException.kt` |
| `reminder/` | Installment and loan reminders | `InstallmentReminderWorker.kt`, `LoanReminderWorker.kt`, `ReminderScheduler.kt`, `BootReceiver.kt` |
| `rust/` | UniFFI bridge to the Rust core | `RustBridge.kt`, `RustBridgeParser.kt`, `RustBridgeCurrency.kt`, `RustBridgeAnalytics.kt`, `RustBridgeBudget.kt`, `RustBridgeBackup.kt`, `RustBridgeValidation.kt`, `RustBridgeSearch.kt`, `RustMappers.kt` |
| `ui/` | ViewModels and top-level UI helpers | `DashboardViewModel.kt`, `TransactionViewModel.kt`, `LoanViewModel.kt`, `BackupViewModel.kt`, `AiAssistantViewModel.kt` |
| `ui/screens/` | Main screens (Compose) | `DashboardScreen.kt`, `LoanManagementScreen.kt`, `DebtHubScreen.kt`, `AnalyticsScreen.kt`, `SmartAssistantScreen.kt`, `BankLoanScreen.kt`, `account/AccountManagementScreen.kt` |
| `ui/components/` | Shared components | `TransactionItem.kt`, `LoanItem.kt`, `HesabyarCard.kt`, `JalaliDateTimePicker.kt` |
| `ui/designsystem/` | Design tokens | `ColorExtensions.kt`, `SpacingTokens.kt`, `ShapeTokens.kt`, `FinancialColors.kt` |
| `ui/theme/` | App theme | `Color.kt`, `Theme.kt`, `Type.kt` |

## Rust core (`rust/hesabyar-core/src/`)

| Module | Responsibility |
|--------|----------------|
| `parser/nlp.rs` | Persian natural-language parsing of transaction text |
| `parser/amount.rs` | Amount extraction from text |
| `parser/money_detector.rs` | Currency unit detection (Toman/Rial) |
| `parser/text_preprocessor.rs` | Persian text preprocessing |
| `calendar.rs` | Jalali/Gregorian calendar conversion |
| `currency.rs` | Money formatting (Rial base, Toman display) |
| `models/mod.rs` | Core data models, `BACKUP_SCHEMA_VERSION` |
| `advisory/budget.rs` | Budget advice engine |
| `ai_validation.rs` | Validation of AI output |
| `analytics.rs` | Analytics calculations |
| `dashboard.rs` | Dashboard data aggregation |
| `crypto.rs` | Cryptography |
| `excel.rs` | Excel export |
| `search.rs` | Search logic |
| `validation.rs` | Domain validation |
| `ffi/mod.rs` | UniFFI bindings for Kotlin |

## Tests and benchmarks

| Path | Type |
|------|------|
| `app/src/test/` | JVM unit tests (JUnit, MockK, Robolectric) |
| `rust/hesabyar-core/tests/` (incl. `golden/`) | Rust tests, golden tests for parsers |
| `rust/hesabyar-core/benches/` | Criterion benchmarks |

## Scripts and CI

| Path | Role |
|------|------|
| `scripts/check-android.sh` | ktlint, detekt, non-Rust unit tests, Android lint |
| `scripts/check-rust.sh` | Rust clippy and unit tests |
| `scripts/check-rust-bridge.sh` | Isolated Rust-bridge JVM tests |
| `scripts/bump-version.sh`, `scripts/inject-version.sh` | Version handling |
| `scripts/generate-release-notes.sh`, `scripts/update-changelog.py` | Release notes |
| `scripts/github-actions-pin-manager.mjs` | Third-party Action SHA pinning |
| `.github/workflows/` | `android-ci.yml`, `release.yml`, `lint.yml`, `ocr-review.yml`, and others |

## Where is a feature?

| Goal | Start here |
|------|-----------|
| Add or edit a transaction | `domain/usecase/ManageTransactionUseCase.kt` → `data/TransactionDelegate.kt` → `ui/TransactionViewModel.kt` |
| Persian parse («دیروز ۵۰ هزارتومن ناهار») | `domain/usecase/ParseTransactionUseCase.kt` → `rust/RustBridgeParser.kt` → `rust/hesabyar-core/src/parser/` |
| Person loans and debts | `domain/usecase/ManageLoanUseCase.kt` → `data/LoanDelegate.kt` + `PersonDelegate.kt` → `ui/screens/DebtHubScreen.kt` |
| Installments and reminders | `domain/usecase/ManageInstallmentUseCase.kt` → `reminder/InstallmentReminderWorker.kt` |
| Backup and restore | `domain/usecase/ManageBackupUseCase.kt` → `data/BackupDelegate.kt` → `auth/BackupCipher.kt` |
| Smart budget advice | `api/BudgetAdvisor.kt` → `domain/usecase/GetBudgetAdviceUseCase.kt` → `rust/hesabyar-core/src/advisory/budget.rs` |
| Dashboard | `domain/usecase/GetDashboardDataUseCase.kt` → `ui/DashboardViewModel.kt` → `ui/screens/DashboardScreen.kt` |
