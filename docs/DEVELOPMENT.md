# Hesabyar — Development Guide

## Prerequisites

1. Copy `.env.example` to `.env`.
2. Set `GEMINI_API_KEY` in `.env`. The core app works without it. AI features need it.
3. For release builds, also set `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`.
4. Install the Android SDK and the Rust toolchain with Android targets (`cargo-ndk`).

## Build and run

```bash
# Debug build and install on a connected device
./gradlew --no-daemon installDebug

# Release signing checks
./gradlew --no-daemon generateKeystore    # first time only
./gradlew --no-daemon checkSigningConfig  # verify signing config

# Release packaging
./gradlew --no-daemon assembleRelease
./gradlew --no-daemon bundleRelease
```

### Building from Iran (Maven mirrors)

`settings.gradle.kts` declares the en-mirror.ir and Tencent mirrors. These
mirrors are opt-in. A default build uses `google()` and `mavenCentral()` only.

Enable the mirrors with one of these values: `1`, `true`, or `yes`. The value is
not case-sensitive.

Linux and macOS:

```bash
HESABYAR_USE_MIRRORS=1 ./gradlew --no-daemon installDebug
```

Windows PowerShell:

```powershell
$env:HESABYAR_USE_MIRRORS = "1"
.\gradlew.bat --no-daemon installDebug
```

Windows Command Prompt:

```cmd
set HESABYAR_USE_MIRRORS=1
gradlew.bat --no-daemon installDebug
```

The variable applies to any Gradle task. The examples use `installDebug` to
match the build section above.

Use the `--no-daemon` flag every time. A running daemon keeps the environment
from its start. This means a later change to the variable has no effect.

Never enable this in CI: the mirrors can serve a POM without its JAR/AAR, and
Gradle then fails instead of falling back to the official repositories (#389).

## Tests

```bash
# All unit tests (non-Rust + Rust isolated)
./gradlew --no-daemon test

# Fast: non-Rust tests only (~2 min vs ~7 min)
./gradlew --no-daemon testDebugUnitTest

# Rust-bridge tests only (isolated JVM, forkEvery=1)
./gradlew --no-daemon testDebugUnitTestRust

# Single test class
./gradlew --no-daemon testDebugUnitTest --tests "io.github.mojri.hesabyar.TransactionTest"

# Rust core tests (run in rust/ directory or pass manifest path)
(cd rust && cargo test)
# Or from repository root:
cargo test --manifest-path rust/Cargo.toml
```

Notes:

- Rust tests carry `@Category(RustTest::class)`. They run in the isolated task. Never mix them into the fast task.
- Run `./gradlew --no-daemon compileDebugKotlin` before a broad test run. It finds Kotlin type errors early.
- After a Rust change, re-run with `--rerun-tasks`. Cached results can hide real failures.

## Lint and static analysis

```bash
./gradlew --no-daemon ktlintFormat        # auto-fix style
./gradlew --no-daemon ktlintCheck detekt  # verify
./gradlew --no-daemon lint                # Android lint
```

Quick-check scripts (read-only):

| Script | Checks |
|--------|--------|
| `scripts/check-android.sh` | ktlint, detekt, non-Rust tests, Android lint |
| `scripts/check-rust.sh` | Rust clippy and unit tests |
| `scripts/check-rust-bridge.sh` | Isolated Rust-bridge JVM tests |

## Rust FFI binding regeneration

Kotlin calls the Rust core through UniFFI bindings. The generated file is `app/src/main/java/io/github/mojri/hesabyar/rust/hesabyar_core.kt`. It is gitignored.

After any change under `rust/`, regenerate the bindings:

```bash
./gradlew --no-daemon :app:generateAndFixBindings --rerun-tasks
```

Rules:

- Never edit the generated `hesabyar_core.kt` by hand. The task overwrites it.
- A comment-only change on an exported (`#[uniffi::export]`) function still changes the UniFFI checksum. Regenerate anyway.
- If an FFI signature changes, update `app/buildSrc/template/HesabyarCore.template.kt` first. Then regenerate.

## Hard constraints

- Never use `Float` or `Double` for money. Use `Long` (Rial) or `BigDecimal`.
- Never use destructive Room migrations. Schema changes must preserve data.
- Never hardcode API keys. Use `.env` or the Keystore.
- Never remove the Jalali calendar or offline support.
- Never add new business logic in Kotlin. New rules, calculations, and validations go in `rust/hesabyar-core`. See [ADR-001](architecture/ADR-001-rust-sole-implementation.md).
- Never use `GlobalScope`. Use structured coroutine scopes.
- All UI is Jetpack Compose. XML layouts are forbidden for new features.
- UI text is Persian-first, RTL, Vazirmatn font. Use Material 3 design tokens.

## Before you change code

1. Does this break offline functionality?
2. Does this bypass the Jalali calendar?
3. Does this affect financial calculation accuracy?
4. Does this require a Room migration? (See the migration checklist in `docs/agents/room-migrations.md`.)
5. Do local backups stay compatible?
6. Does this add business logic in Kotlin that belongs in the Rust core?
