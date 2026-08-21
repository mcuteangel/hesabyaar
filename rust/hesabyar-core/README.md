# Hesabyar Core (Rust)

Shared native core for the Hesabyar Android application.

## Overview

This crate contains platform-independent business logic extracted from the Kotlin codebase:

- **Persian NLP** — Text preprocessing, amount parsing, category inference
- **Jalali Calendar** — Gregorian ↔ Jalali date conversion
- **Currency Formatting** — Rial/Toman conversion and formatting
- **Financial Advisory** — Offline budget advice, forecasting, health scoring
- **Analytics** — Transaction aggregation, category breakdown
- **Dashboard** — Dashboard data computation
- **Backup** — JSON backup parsing, validation, and AES-256-GCM encryption
- **Excel Export** — Excel report generation
- **Search** — Full-text transaction search with relevance scoring
- **Entity Validation** — Transaction, loan, installment validation
- **AI Validation** — AI output validation and sanitization

## Building

### Prerequisites

- Rust toolchain (1.75.0+)
- Android NDK (for cross-compilation)

### Android Targets

```bash
rustup target add aarch64-linux-android armv7-linux-androideabi i686-linux-android x86_64-linux-android
cargo install cargo-ndk
```

### Build Commands

```bash
# Check (syntax + types)
cargo check

# Build for Android (all ABIs via cargo-ndk)
cargo ndk -t arm64-v8a -t armeabi-v7a -t x86 -t x86_64 build --release

# Build for host (for UniFFI binding generation)
cargo build --release

# Run tests
cargo test

# Run benchmarks
cargo bench
```

### Generate Kotlin Bindings (UniFFI)

The `uniffi-gen` binary in the workspace root generates Kotlin bindings from the compiled host library:

```bash
# From workspace root
cargo run --package uniffi-gen -- \
  target/release/libhesabyar_core.so \
  ../app/src/main/java/io/github/mojri/hesabyar/rust
```

On Windows use `target/release/hesabyar_core.dll`, macOS use `target/release/libhesabyar_core.dylib`.

## Linting and Formatting

The workspace uses `cargo clippy` for static analysis. It is the Rust equivalent of detekt for Kotlin.
The `Makefile` in `rust/` defines the developer commands:

```bash
make -C rust lint        # clippy with CI flags; fails on any warning
make -C rust lint-fix    # clippy --fix for safe, automatic fixes
make -C rust fmt         # format all sources
make -C rust fmt-check   # verify formatting only
make -C rust check       # fast type check
make -C rust test        # unit tests
```

### Lint Policy

The policy lives in `[workspace.lints.clippy]` in `rust/Cargo.toml`. Each crate opts in through `[lints] workspace = true`.

- `clippy::correctness` is denied. These lints mark likely bugs.
- `clippy::all`, `suspicious`, `complexity`, `perf`, and `style` warn.
- `unwrap_used`, `expect_used`, and `panic` warn in production code.
- Test code and benchmarks may use `unwrap`, `expect`, and `panic!`. The allow rules sit in `lib.rs` and `benches/parser_bench.rs`.

CI runs `cargo clippy --workspace --all-targets --all-features -- -D warnings`.
Run `make -C rust lint` for the same check locally. A new warning fails the build.
Fix a finding by refactoring. Add a local `#[allow]` only with a comment that explains why.

### Toolchain

`rust/rust-toolchain.toml` pins the channel to `stable`. It also lists `clippy` and `rustfmt` as components.
rustup installs them on the first cargo command.

### Pre-Commit Hook

The pre-commit hook runs the Kotlin checks first. Then it runs the Rust checks:

1. `cargo fmt` — formats the staged Rust sources
2. `cargo clippy --workspace --all-targets --all-features -- -D warnings`

The Rust gate validates the commit candidate, not the whole worktree. It backs up every
Rust file it touches. Then it writes the staged (index) content into the worktree so
`cargo fmt` and `cargo clippy` see exactly what will be committed. After the gates, the
hook restores every file to its original worktree state. This keeps unrelated unstaged
edits out of the commit and stops Clippy from passing on worktree edits that the commit
does not contain. The hook re-stages only the formatting delta for staged Rust files.
It never stages untracked files and never stages unrelated unstaged edits.

A missing `cargo` command fails the commit. Install Rust with rustup and keep `cargo` on
PATH. A missing `rust/` workspace directory also fails the commit with a clear hint.
A Clippy warning fails the commit. Fix the code. Do not add an allow attribute without a
reason comment. The hook does not run the Rust tests. CI runs the full test suite
(`cargo test --workspace`). The Gradle task `copyGitHooks` installs the hook from
`scripts/pre-commit`.

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
      nlp.rs             — Persian NLP pipeline
      text_preprocessor.rs — Persian text normalization
      money_detector.rs  — Money keyword detection
      amount.rs          — Amount tokenizer + interpreter
    advisory/
      mod.rs             — Advisory module re-exports
      budget.rs          — Budget advice + forecasting
    ffi/
      mod.rs             — UniFFI bridge with 20+ exported functions
    validation.rs        — Entity validation
    search.rs            — Full-text search
    crypto.rs            — AES-256-GCM encryption
    ai_validation.rs     — AI output validation
    excel.rs             — Excel report generation
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
