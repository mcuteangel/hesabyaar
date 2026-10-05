# Hesabyar — Project Overview

Hesabyar is a Persian-first personal finance app for Android. It works fully offline. It tracks transactions, loans, debts, and installments. It offers an optional AI assistant for smart text entry and budget advice.

## Key features

- **Transactions.** Record income and expenses. Categorize each record. Search and filter records.
- **Person loans and debts.** Track money you lend and money you borrow. Record partial repayments. Mark loans as settled.
- **Installments.** Schedule recurring payments. Receive reminders through WorkManager.
- **Accounts.** Manage multiple accounts with balances.
- **Dashboard.** View income, expenses, balances, and upcoming installments at a glance.
- **Smart text parsing.** Type Persian text such as «دیروز ۵۰ هزارتومن ناهار». The parser extracts the amount, date, and category. It works offline (Rust NLP parser) and online (Gemini).
- **AI providers.** Use Gemini, OpenRouter, or a custom endpoint. A rule-based offline fallback covers budget advice and forecasts when no provider is available.
- **Backup and restore.** Export all data as JSON. Restore with REPLACE or MERGE mode. Excel (.xlsx) export is available.
- **Security.** Lock the app with PIN or biometrics. Room database is encrypted on disk via SQLCipher. Passphrase-based AES-GCM protects sensitive person/account fields in backups (phone, notes, card/account numbers, IBAN); amounts and names remain plaintext. See [SECURITY](SECURITY.md).

> Planned, not yet done: CSV export, full backup payload encryption, certificate pinning, voice input, on-device local AI. See [ROADMAP](ROADMAP.md).

## Architecture

Hesabyar uses MVVM with Use Cases in a single Android module. The package root is `io.github.mojri.hesabyar`.

- **Room database** is the single source of truth for stored data (schema v9).
- **Rust core** (`rust/hesabyar-core`) is the single source of truth for new business logic. It holds calculations, validations, parsing, and advisory rules. Approved permanent Kotlin fallback exceptions are documented in [ADR-001](architecture/ADR-001-rust-sole-implementation.md).
- **Kotlin layer** handles UI, persistence, DI, and Android-specific concerns. New feature business logic in Kotlin is forbidden. See [ADR-001](architecture/ADR-001-rust-sole-implementation.md).
- **Data flow:** Screen → ViewModel → UseCase → RustBridge → Rust core. Persistence flows through UseCase → Repository → Room.

Core principles: offline first, user owns their data, Jalali calendar everywhere, Persian-first RTL UI, AI assists but never changes data without confirmation.

## Tech stack

- Languages: Kotlin, Rust
- UI: Jetpack Compose, Material 3, Navigation Compose
- Database: Room (SQLite), Hilt DI, Coroutines/Flow, WorkManager
- Networking: Retrofit, OkHttp, Moshi
- Native bridge: UniFFI, cargo-ndk
- Min SDK: Android 8+

Full list: [TECH_STACK](TECH_STACK.md). Build guide: [DEVELOPMENT](DEVELOPMENT.md).

## Repository layout

| Path | Contents |
|------|----------|
| `app/src/main/java/io/github/mojri/hesabyar/` | Kotlin source (ui, data, domain, api, rust bridge, reminder, auth, di) |
| `rust/hesabyar-core/` | Rust core crate (parser, models, advisory, ffi) |
| `docs/` | Project documentation (this hub) |
| `plans/` | Dated plans and refactor documents |
| `scripts/` | Check and release scripts |
| `.github/workflows/` | CI, lint, release, code-review workflows |
| `config/detekt/` | Static analysis config |
