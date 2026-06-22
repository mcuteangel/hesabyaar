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

Room Database is the single source of truth.

All reports and calculations must derive from stored records.

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

- Kotlin
- Jetpack Compose
- Material 3
- Room Database
- Navigation Compose
- Firebase AI

The project should remain modular and scalable.

---

# Project Structure

```text
app/

└── src/main/java/io/github/mojri/hesabyar

    core/

    ├── ai/
    ├── database/
    ├── designsystem/
    ├── navigation/
    ├── common/
    └── utils/

    features/

    ├── dashboard/
    │   ├── ui/
    │   ├── domain/
    │   └── data/
    │
    ├── transactions/
    │   ├── ui/
    │   ├── domain/
    │   └── data/
    │
    ├── loans/
    │   ├── ui/
    │   ├── domain/
    │   └── data/
    │
    ├── installments/
    │   ├── ui/
    │   ├── domain/
    │   └── data/
    │
    ├── reports/
    │   ├── ui/
    │   ├── domain/
    │   └── data/
    │
    ├── settings/
    │   ├── ui/
    │   └── data/
    │
    └── voice/
        ├── speech/
        ├── parser/
        └── ui/
```

---

# Data Flow

```text
UI
 ↓
ViewModel
 ↓
UseCase
 ↓
Repository
 ↓
Room Database
 ↓
State Update
 ↓
UI Refresh
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
core/ai
```

Structure:

```text
AiParser
AiPromptBuilder
AiResult
AiRepository
GeminiService
```

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
core/designsystem

├── theme/
├── tokens/
├── components/
└── icons/
```

---

# Reusable Components

Location:

```text
core/designsystem/components
```

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
