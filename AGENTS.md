# Hesabyar – Comprehensive Agent & Developer Guide

## 1. Project Overview & Identity

### 1.1 Product Vision
Hesabyar is an open-source, AI-powered personal accounting and financial assistant application tailored specifically for Persian-speaking users. It is designed to allow users to manage personal finances using natural language, voice inputs, and traditional accounting workflows.

*   **Core Flow:** User Intention → Natural Language → AI Understanding → Structured Financial Records → Insights & Reports.
*   **Target Objective:** Users should spend less time managing their finances and more time understanding them.

### 1.2 Target Audience
*   Persian-speaking individuals and families.
*   Freelancers.
*   Small business owners.

### 1.3 Scope (What Hesabyar Is NOT)
*   **NOT** a cloud-first or cloud-dependent service.
*   **NOT** a banking application or cryptocurrency wallet.
*   **NOT** an enterprise-grade bookkeeping/ERP tool for large corporations.

---

## 2. Reference Documentation

The following documentation files contain detailed project guidelines. **All agents must consult these files when working on the project:**

*   `docs/TECH_STACK.md` — Official tech stack and library list. Always verify dependencies before adding new ones.
*   `docs/ROADMAP.md` — Feature roadmap with current status. Use to understand what is done, in progress, or planned.
*   `docs/architecture/ARCHITECTURE.md` — Full architecture guide including domain model, project structure, data flow, design system tokens, component library, navigation map, and security requirements.

---

## 3. Core Product & Architectural Principles

### 3.1 Offline-First Architecture
*   The application must remain fully functional without internet access.
*   Core accounting mechanisms (creating transactions, viewing reports, managing loans/installments) must never depend on cloud services.
*   AI capabilities are strictly optional enhancements, while accounting features are mandatory.

### 3.2 Persian-First UX
The application is a Persian-native product, not a translated English application. All UX and design decisions must prioritize:
*   Full Right-to-Left (RTL) layout support.
*   Native Persian text, typography, and financial terminology.
*   Jalali (Shamsi) calendar system integration.
*   Proper Persian number formatting where appropriate.

### 3.3 Data Ownership & Privacy
*   **Data Ownership:** Users own 100% of their financial data. The application must support seamless local storage, backup, restoration, and data export to avoid vendor lock-in.
*   **Privacy-First:** Financial data must never be uploaded to any server without explicit user consent.

### 3.4 Accounting Accuracy (Critical Guardrail)
*   Financial calculations must **never** use floating-point arithmetic.
*   **Forbidden:** `Float` and `Double`.
*   **Required:** `Long` (representing Rial) or `BigDecimal`. Financial accuracy takes precedence over convenience.

---

## 4. System Architecture & Technical Stack

### 4.1 Layered Architecture
The project enforces a strict unidirectional data flow and clean separation of concerns:
$$\text{UI (Compose)} \rightarrow \text{ViewModel} \rightarrow \text{Use Case} \rightarrow \text{Repository} \rightarrow \text{Data Source (Room / Network)}$$

*   Avoid placing business logic directly inside UI layers or Composables.
*   Avoid creating "God ViewModels" that handle too many responsibilities.

### 4.2 AI Philosophy & Multi-Provider Strategy
*   **Philosophy:** AI assists accounting; it does not replace it. The user must always be able to review, edit, and manually confirm any data parsed by AI before it is permanently saved.
*   **Abstraction:** Business logic must never couple to a specific AI provider. All providers must implement a common interface.
*   **Supported Roadmap:** 
    *   *Current:* Gemini
    *   *Future:* OpenAI, OpenRouter, and Local LLMs.

### 4.3 Storage & Security Rules
*   **Room Database:** Schema migrations are strictly required. Destructive migrations are completely forbidden to preserve local user data and backup compatibility.
*   **Security:** Never hardcode API keys, commit secrets to version control, or store sensitive tokens/data in plain text. Use `EncryptedSharedPreferences` and the Android Keystore system.

---

## 5. UI/UX & Design System Rules

### 5.1 Official Design System
*   Hesabyar utilizes **Material Design 3 (Material You)** via the latest stable Jetpack Compose and AndroidX libraries.
*   Do not introduce custom design languages or third-party UI frameworks that conflict with Material 3 standards.
*   Prefer official Google components (`MaterialTheme`, Material 3 Components, Navigation Compose, Material 3 Dialogs, Date Pickers, and Bottom Sheets) over third-party alternatives.

### 5.2 Consistency & Reusability
*   Every screen must adhere to the same unified typography, elevation, spacing, color, and shape systems. Avoid creating one-off styles.
*   Before creating a new custom UI component, verify existing design system files to maximize code reuse and prevent duplicated UI implementations.

### 5.3 Dynamic Color & Accessibility
*   Support Material You dynamic color themes on Android 12+ with a graceful, well-designed fallback for older Android versions.
*   Ensure full accessibility across all screens: robust support for TalkBack, scalable font sizes, Dark Mode, and RTL layouts.

---

## 6. Domain Concepts

*   **Transaction:** A financial record representing cash flow. It can be categorized as either an *Income* or an *Expense* (e.g., Salary, Fuel, Food, Bills).
*   **Loan:** Money borrowed from or lent to an external party (e.g., lending money to a friend, or borrowing from a lender).
*   **Installment:** A scheduled, recurring payment mapped to a specific financial commitment (e.g., car loan installment, mortgage, phone plan).
*   **Balance:** The net financial state. The balance is **always calculated** programmatically from transactions and states—it is never manually entered or overridden by the user.

---

## 7. Coding & Quality Standards

### 7.1 Coding Rules
*   **Kotlin:** Adhere to SOLID principles, Clean Code patterns, KISS (Keep It Simple, Stupid), and DRY (Don't Repeat Yourself).
*   **Jetpack Compose:** Ensure composables are stateless where possible, practice proper state hoisting, and optimize layouts to avoid unnecessary recompositions.
*   **Coroutines:** Practice structured concurrency. `GlobalScope` is strictly forbidden. Implement robust, explicit error handling.

### 7.2 Testing Requirements
Critical business logic requires unit/integration test coverage. Focus areas include:
*   Transaction and Balance calculations.
*   AI Natural Language Parsing engines.
*   Loan and Installment schedules/calculations.

---

## 8. Developer Workflow & Checklist

### 8.1 Pre-Change Impact Assessment
Before modifying code or introducing a feature, verify these five guardrails:
1. Does this break offline support?
2. Does this break or bypass Jalali calendar support?
3. Does this affect or compromise accounting accuracy?
4. Does this modification require a database migration?
5. Is backward compatibility for local user backups preserved?

> ⚠️ **Strictly Forbidden Changes:**
> * Removing Jalali or offline functionality.
> * Introducing `Double` or `Float` data types for monetary values.
> * Hardcoding API keys or storing secrets in plain text.
> * Executing destructive Room database migrations.

### 8.2 Definition of Done (DoD)
A feature or task is considered complete **only** when it meets the following criteria:
* [ ] Core logic and UI are fully implemented.
* [ ] Corresponding unit or integration tests are added/passing.
* [ ] Project documentation and internal guides are updated.
* [ ] Database migration is handled properly (if applicable).
* [ ] Local backup and restore compatibility is verified.
* [ ] Layout and components are explicitly verified in RTL mode.
* [ ] Visual appearance is explicitly verified in Dark Mode.
