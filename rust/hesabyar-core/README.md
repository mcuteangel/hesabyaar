# Hesabyar Core (Rust)

Shared native core for the Hesabyar Android application.

## Overview

This crate contains platform-independent business logic extracted from the Kotlin codebase:

- **Persian NLP** — Text preprocessing, amount parsing, category inference
- **Jalali Calendar** — Gregorian ↔ Jalali date conversion
- **Currency Formatting** — Rial/Toman conversion and formatting
- **Financial Advisory** — Offline budget advice, forecasting, health scoring
- **Analytics** — Transaction aggregation, category breakdown
- **Backup** — JSON backup parsing and validation

## Building

### Prerequisites

- Rust toolchain (1.75.0+)
- Android NDK (for cross-compilation)

### Android Targets

```bash
rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android
```

### Build Commands

```bash
# Check (syntax + types)
cargo check

# Build for Android
cargo build --target aarch64-linux-android --release

# Run tests
cargo test

# Run benchmarks
cargo bench
```

## Project Structure

```
hesabyar-core/
  src/
    lib.rs              — Public API re-exports
    calendar.rs          — Jalali ↔ Gregorian conversion
    currency.rs          — Rial/Toman formatting
    analytics.rs         — Transaction analytics
    dashboard.rs         — Dashboard data computation
    models/
      mod.rs             — Domain model structs
    parser/
      mod.rs             — Parser module re-exports
      money_detector.rs  — Money keyword detection
      text_preprocessor.rs — Persian text normalization
      amount.rs          — Amount tokenizer + interpreter
    advisory/
      mod.rs             — Advisory module re-exports
      budget.rs          — Budget advice + forecasting
    ffi/
      mod.rs             — UniFFI bridge (future)
  tests/
    golden/
      persian_parse_cases.json — Golden test data
  benches/
    parser_bench.rs      — Criterion benchmarks
```

## Testing

```bash
# Unit tests
cargo test

# With output
cargo test -- --nocapture

# Specific test
cargo test test_known_jalali_date

# Benchmarks
cargo bench
```

## Architecture Decisions

- **i64 for amounts** — Matches Room's Long type (Rial)
- **Single crate** — Will split to workspace when Desktop/iOS targets are added
- **UniFFI** — Auto-generated Kotlin/Swift bindings, no manual JNI
- **No unsafe code** — Memory safety guaranteed by Rust's type system
- **Serde for serialization** — Backup JSON handling
