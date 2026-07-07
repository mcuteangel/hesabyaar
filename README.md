# حسابیار (Hesabyar)

[![CodSpeed](https://img.shields.io/endpoint?url=https://codspeed.io/badge.json)](https://app.codspeed.io/mcuteangel/hesabyaar?utm_source=badge)

Persian-first personal finance assistant for Android.

## Features

- **Transaction Management**: Track income and expenses with categories
- **Loan & Debt Tracking**: Manage debts and credits with people
- **Installment Management**: Track recurring payments with reminders
- **AI-Powered Parsing**: Natural language Persian input for quick entry
- **Budget Advisor**: AI-powered financial insights and recommendations
- **Analytics Dashboard**: Monthly spending, category breakdown, debt overview
- **Backup & Restore**: JSON-based backup with AES-256-GCM encryption
- **Excel Export**: Export reports to .xlsx format (Rust-powered)
- **Offline-First**: All core features work without internet
- **Jalali Calendar**: Full Persian calendar support

## Tech Stack

### Android (Kotlin)
- Kotlin, Jetpack Compose, Material 3
- Room Database (SQLite)
- Navigation Compose
- Kotlin Coroutines + Flow
- WorkManager (background reminders)
- OkHttp + Retrofit (networking)
- Firebase AI / OpenRouter / Custom AI providers
- Robolectric + Roborazzi (testing)

### Shared Core (Rust)
- Jalali/Gregorian calendar conversion
- Persian NLP parser (amount detection, category inference)
- Transaction search with relevance scoring
- Budget advisory and financial health scoring
- Excel report generation (`rust_xlsxwriter`)
- AES-256-GCM backup encryption
- All business logic testable outside Android

## Prerequisites

### For Android development
- JDK 17+
- Android SDK (compileSdk 37)
- Gradle 8.x

### For Rust core development
- [Rust toolchain](https://rustup.rs/) (stable)
- No additional system dependencies needed (pure Rust crates)

## Build

```bash
# One-click: build Rust core + generate Kotlin bindings + build Android
./gradlew installDebug

# Or step by step:
cargo test -p hesabyar-core          # Run Rust tests (270+ tests)
./gradlew :app:generateAndFixBindings  # Generate UniFFI Kotlin bindings
./gradlew installDebug                # Build and install debug APK

# Run all tests
./gradlew test                        # Kotlin unit tests
cargo test -p hesabyar-core          # Rust unit tests

# Lint check
./gradlew lint
```

## Environment Setup

1. Copy `.env.example` to `.env`
2. Set `GEMINI_API_KEY` for AI features (optional)
3. For release builds, set signing credentials in `.env`

## Project Structure

```
hesabyaar/
├── app/src/main/java/io/github/mojri/hesabyar/
│   ├── api/           # AI providers, parsers, budget advisor
│   ├── data/          # Room entities, DAOs, repository, backup, Excel export
│   ├── reminder/      # WorkManager workers, notification helpers
│   ├── rust/          # UniFFI bridge (RustBridge.kt, generated bindings)
│   └── ui/            # Compose screens, ViewModels, theme
├── rust/
│   ├── hesabyar-core/ # Shared Rust core library
│   │   ├── src/
│   │   │   ├── advisory/   # Budget advice, forecast, health score
│   │   │   ├── analytics.rs
│   │   │   ├── calendar.rs # Jalali/Gregorian conversion
│   │   │   ├── crypto.rs   # AES-256-GCM encryption
│   │   │   ├── currency.rs # Rial/Toman formatting
│   │   │   ├── dashboard.rs
│   │   │   ├── excel.rs    # XLSX generation
│   │   │   ├── ffi/mod.rs  # FFI wrappers
│   │   │   ├── models/mod.rs
│   │   │   ├── parser/     # Persian NLP
│   │   │   ├── search.rs   # Full-text search
│   │   │   └── validation.rs
│   │   ├── benches/         # Criterion benchmarks
│   │   └── tests/golden/    # Golden test data
│   └── uniffi-gen/          # Kotlin binding generator
└── .github/workflows/       # CI/CD pipelines
```

## Rust Core Architecture

The Rust core (`hesabyar-core`) is a shared library compiled as a C dynamic library (`cdylib`) and consumed by Android via UniFFI bindings. It provides:

- **Calendar**: Jalali ↔ Gregorian conversion (zero dependency on java.time)
- **Parser**: Persian NLP for extracting amounts, dates, categories from natural language
- **Search**: Full-text search with ZWNJ normalization and relevance scoring
- **Analytics**: Monthly aggregation, category breakdown, debt summary
- **Dashboard**: Balance, savings rate, debt-to-income ratio
- **Advisory**: Budget advice, forecast, financial health score (0-100)
- **Excel**: XLSX report generation with RTL layout and Persian formatting
- **Crypto**: AES-256-GCM backup encryption with SHA-256 integrity
- **Validation**: Centralized business rules for transactions, loans, installments

All monetary values use `i64` (Rial). **1 Toman = EXACTLY 10 Rials** (hardcoded, never changes).

## Testing

```bash
# Rust tests (270+ tests)
cargo test -p hesabyar-core

# Kotlin unit tests
./gradlew test

# Single test class
./gradlew test --tests "io.github.mojri.hesabyar.OfflineParserTest"

# Benchmarks (Criterion)
cargo bench -p hesabyar-core
```

## Benchmarks

Performance is tracked with [CodSpeed](https://codspeed.io) using JMH for Kotlin
and Criterion for Rust. Rust benchmarks cover parser, calendar, advisory, search,
crypto, and validation. They run automatically in CI on every pull request.

## CI/CD

GitHub Actions workflows:
- **android-ci.yml**: Runs on every push/PR — Rust tests, binding generation, Kotlin tests, lint, APK build
- **release.yml**: Auto-versioning and release on main branch pushes
- **codspeed.yml**: Performance benchmark tracking

## License

Private - All rights reserved.
