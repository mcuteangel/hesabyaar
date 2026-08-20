# Hesabyar Architecture

## Vision

Hesabyar is a Persian-first intelligent accounting assistant.

The application enables users to manage personal finances using:

- Manual Entry
- Text Commands
- Voice Commands
- AI Assistance

Primary goals:

- Fast financial recording
- Natural Persian interaction
- Offline-first experience
- Reliable local storage
- Scalable architecture
- Modern Material 3 user experience

---

# Core Principles

## Offline First

All critical functionality must work without internet access.

The local database is the primary source of truth.

---

## User Ownership

User data belongs to the user.

Cloud services are optional enhancements.

---

## AI Assisted, Not AI Controlled

AI helps users create and categorize records.

AI never modifies financial data without confirmation.

---

## Source of Truth

Room Database is the single source of truth for **stored data**.

Rust Core (`rust/hesabyar-core`) is the single source of truth for **business logic**, calculations, and validation. All reports and calculations must derive from stored records and compute through the Rust core.

## Business Logic Policy

Rust Core (`rust/hesabyar-core`) is the canonical implementation for new business logic. Kotlin fallbacks exist only for the pre-approved exception list. See `docs/architecture/ADR-001-rust-sole-implementation.md` (`## Decision` and `## Permanent Kotlin Fallbacks`) for the full policy, exception list, and rationale.

---

# Domain Model

## Account

Represents where money exists.

Examples:

- Cash
- Bank Account
- Credit Card
- Wallet

Fields:

```text
id
name
type
createdAt
```

---

## Category

Represents transaction classification.

Examples:

- Food
- Transportation
- Salary
- Shopping

Fields:

```text
id
name
icon
type
createdAt
```

---

## Transaction

Represents:

- Expense
- Income

Fields:

```text
id
accountId
categoryId
amount
note
date
createdAt
```

---

## Loan

Represents:

- Debt
- Credit

Fields:

```text
id
personName
amount
status
createdAt
```

---

## Installment

Represents scheduled or recurring payments.

Fields:

```text
id
title
amount
dueDate
paid
createdAt
```

---

# Architecture Style

Feature-Based Architecture

Current repository uses:

- **Rust** — canonical implementation for business logic, calculations, and validation (`rust/hesabyar-core`)
- Kotlin — UI (Jetpack Compose, Material 3), persistence orchestration (Room), and FFI bridge via UniFFI
- Navigation Compose
- Firebase AI (optional, for online natural language parsing)
- Hilt (Dependency Injection)

Kotlin package layout:

- `ui/` — Screens, ViewModels, Theme
- `api/` — AI providers (GeminiParser, BudgetAdvisor, AiProvider interface)
- `data/` — Room entities, DAOs, Repository, ExcelExporter, BackupModels
- `domain/` — UseCases (thin wrappers), DTO mappers
- `rust/` — UniFFI bridge (RustBridge.kt, RustMappers.kt), generated bindings
- `auth/` — AuthManager, BiometricHelper, Pin/LockScreen
- `core/` — AppLogger
- `di/` — Hilt modules (AiModule, DatabaseModule, RepositoryModule, UseCaseModule)
- `reminder/` — WorkManager workers, notification helpers

The project should remain modular and scalable.

---

# Project Structure

```text
app/src/main/java/io/github/mojri/hesabyar/

├── api/
│   ├── AiProvider.kt              # Multi-provider AI client
│   ├── AiProviderConfig.kt        # Config management + EncryptedSharedPrefs
│   ├── BudgetAdvisor.kt           # AI + offline budget advice
│   ├── GeminiParser.kt            # Sentence parsing (online + offline)
│   ├── MoneyDetector.kt           # Money presence detection gate
│   └── PersianAmountParser.kt     # Token-based amount extraction
│
├── data/
│   ├── AppDatabase.kt             # Room database (v3)
│   ├── BackupModels.kt            # Backup payload + validation
│   ├── Daos.kt                    # Room DAOs (5 interfaces)
│   ├── Entities.kt                # Room entities (5 tables)
│   ├── ExcelExporter.kt           # .xlsx export (custom XML writer)
│   ├── HesabyarRepository.kt      # Repository implementation
│   └── HesabyarRepositoryInterface.kt
│
├── reminder/
│   ├── BootReceiver.kt            # Re-schedule alarms on boot
│   ├── InstallmentReminderWorker.kt
│   ├── LoanReminderWorker.kt
│   ├── MarkPaidReceiver.kt        # Notification action: mark paid
│   ├── NotificationHelper.kt      # Notification channel + builders
│   ├── ReminderScheduler.kt       # WorkManager scheduling
│   └── ReminderSettingsManager.kt # SharedPreferences config
│
└── ui/
    ├── AiAssistantViewModel.kt    # AI config + parser + advisor + cache
    ├── AnalyticsViewModel.kt      # Analytics data computation
    ├── AppLogger.kt               # In-memory log ring buffer
    ├── BackupViewModel.kt         # Backup/restore operations
    ├── CategoryViewModel.kt       # Category CRUD
    ├── DashboardViewModel.kt      # Dashboard data aggregation
    ├── ExportViewModel.kt         # Excel export orchestration
    ├── InstallmentViewModel.kt    # Installment CRUD
    ├── JalaliCalendarHelper.kt    # Gregorian ↔ Jalali conversion
    ├── LoanViewModel.kt           # Loan CRUD + payments
    ├── SettingsViewModel.kt       # App settings
    ├── TransactionViewModel.kt    # Transaction CRUD
    ├── UiState.kt                 # UI state sealed interfaces
    ├── screens/                   # Compose screens
    └── theme/                     # Material 3 theme
```

---

# Data Flow

```text
UI (Compose Screens)
 ↓
ViewModel (AndroidViewModel)
 ↓
UseCase (business logic orchestration)
 ↓
RustBridge → Rust Core (all calculations, validation, advisory)
 ↓
Repository ← → Room Database (AppDatabase)
 ↓
Flow<List<T>> emissions
 ↓
StateFlow / collectAsState()
 ↓
UI Recomposition
```

AI Flow:
```text
User Text Input
 ↓
AiAssistantViewModel.parseSmartSentence()
 ↓
GeminiParser.parseSentence()
 ├── Online: AiProvider.generateContent() → API → JSON parse
 └── Offline: RustBridge.parseSentenceOfflineSync() → Rust NLP core
 ↓
ParsedResult (type, amount, category, description, ...)
 ↓
User Confirmation Dialog
 ↓
Repository.insertTransaction/insertLoan/insertInstallment
 ↓
Room Database → Flow emission → UI update
```

---

# Storage Architecture

## Primary Storage

Room Database

Responsibilities:

- Accounts
- Categories
- Transactions
- Loans
- Installments
- Settings

---

## Future Storage Features

- Encrypted Backup
- Restore
- Cloud Sync
- Multi Device Support

---

# AI Architecture

AI functionality lives inside:

```text
api/
```

Structure:

```text
AiProvider.kt              # Multi-provider AI client
AiProviderConfig.kt        # Config management + EncryptedSharedPrefs
BudgetAdvisor.kt           # AI + offline budget advice
GeminiParser.kt            # Sentence parsing (online + offline)
MoneyDetector.kt           # Money presence detection gate
PersianAmountParser.kt     # Token-based amount extraction
```

> **Note:** paths above refer to `app/src/main/java/io/github/mojri/hesabyar/...` packages.

---

## Voice Flow

```text
Voice Input
 ↓
Speech To Text
 ↓
Text
 ↓
AI Parser
 ↓
Structured Result
 ↓
Confirmation Dialog
 ↓
Repository
 ↓
Database Save
```

---

## Text Flow

```text
User Text
 ↓
AI Parser
 ↓
Structured Result
 ↓
Confirmation Dialog
 ↓
Repository
 ↓
Database Save
```

---

# Reporting System

Reports are generated from source records.

Rules:

- Never store aggregated values
- Never duplicate balances
- Never persist calculated totals
- Always calculate from transactions

Supported Reports:

- Daily Summary
- Weekly Summary
- Monthly Summary
- Category Analysis
- Cash Flow
- Loan Overview
- Installment Tracking

---

# Design System Architecture

## Design Language

Hesabyar uses Material Design 3 (Material You).

The application should feel similar to modern Google applications.

Design Inspirations:

- Google Wallet
- Google Calendar
- Google Tasks
- Google Keep

Design Goals:

- Clean
- Minimal
- Friendly
- Financially Focused

---

# Theme Architecture

Theme defines:

- Color Scheme
- Typography
- Shapes
- Spacing
- Elevation

No hardcoded styling values are allowed inside screens.

---

# Design Tokens

## AppSpacing

```text
XS = 4dp
SM = 8dp
MD = 16dp
LG = 24dp
XL = 32dp
```

---

## AppShapes

```text
Small
Medium
Large
```

---

## AppTypography

```text
Display
Headline
Title
Body
Label
```

---

## AppElevation

```text
Level0
Level1
Level2
Level3
Level4
Level5
```

---

# Design System Structure

```text
ui/components/              # Shared Compose components
ui/utils/                   # Shared utility functions
ui/theme/                   # Material 3 theme (colors, typography, shapes)
```

---

# Reusable Components

Location:

```text
ui/components/
```

> **Note:** paths above refer to `app/src/main/java/io/github/mojri/hesabyar/...` packages.

Components:

- AppCard
- AppTopBar
- AppDialog
- AppScaffold
- AppBottomSheet
- AppTextField
- CurrencyInput
- CurrencyText
- PersianDateText
- BalanceCard
- SummaryCard
- EmptyState

---

# Screen Architecture

Example Dashboard:

```text
DashboardScreen

├── BalanceCard
├── IncomeExpenseSummary
├── UpcomingInstallments
└── RecentTransactions
```

---

# Navigation

```text
Dashboard

Transactions
 ├── Add Transaction
 └── Edit Transaction

Loans

Installments

Reports

Settings
```

---

# Accessibility

Requirements:

- RTL First Design
- Screen Reader Support
- Semantic Labels
- High Contrast Support
- Minimum Touch Targets

Accessibility is mandatory.

---

# Dark Mode

Dark Mode is required.

Requirements:

- Material 3 Color System
- Dynamic Colors
- Proper Contrast Ratios

Rules:

- No custom dark theme
- Theme-driven colors only

---

# Security

Requirements:

- Secure Local Storage
- Encrypted Secrets
- Secure Backup Files

Future:

- Database Encryption

---

# Performance

Requirements:

- Fast Startup
- Low Memory Usage
- Smooth Scrolling
- Efficient Queries
- Lazy Loading

---

# Reliability

Requirements:

- No Data Loss
- Safe Migrations
- Automated Testing
- Error Recovery

---

# Maintainability

Requirements:

- Modular Structure
- Reusable Components
- Testable Business Logic
- Clear Separation of Concerns

---

# Roadmap

## Phase 1 - MVP

- Accounts
- Categories
- Transactions
- Loans
- Installments
- Dashboard
- Basic Reports

---

## Phase 2 - Productivity

- Backup
- Restore
- Excel Export
- Notifications
- Search
- Filters

---

## Phase 3 - Intelligence

- Voice Entry
- AI Parsing
- AI Categorization
- Smart Reports
- Spending Insights

---

## Phase 4 - Ecosystem

- Cloud Sync
- Multi Device
- Shared Accounts
- Family Finance Management

---

# Success Criteria

A successful Hesabyar release should provide:

- Fast financial recording
- Natural Persian interaction
- Reliable local storage
- Useful financial insights
- Modern Material 3 experience
- Accessibility compliance
- Future-ready architecture
