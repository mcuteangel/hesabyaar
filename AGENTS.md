# Hesabyar – Agent Guide

## Project Identity

Persian-first personal finance app (Android). Offline-first. AI (Gemini/OpenRouter) is optional enhancement, not core.

## Build & Run

```bash
# Debug build (only needs GEMINI_API_KEY in .env)
./gradlew --no-daemon installDebug

# Release signing (requires .env with KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD)
./gradlew --no-daemon generateKeystore   # first time only
./gradlew --no-daemon checkSigningConfig  # verify signing config

# Run all unit tests (non-Rust + Rust isolated)
./gradlew --no-daemon test

# Run fast (non-Rust) tests only — no JNI fork overhead (~2m vs ~7m)
./gradlew --no-daemon testDebugUnitTest

# Run Rust-bridge tests only — with JNI isolation (forkEvery=1)
./gradlew --no-daemon testDebugUnitTestRust

# Run single non-Rust test class (fast task — Rust-tagged classes are excluded here)
./gradlew --no-daemon testDebugUnitTest --tests "io.github.mojri.hesabyar.TransactionTest"

# Run single Rust-tagged test class (must use the isolated task)
./gradlew --no-daemon testDebugUnitTestRust --tests "io.github.mojri.hesabyar.rust.AiAdviceSanitizationTest"

# Lint / static analysis (no custom config, uses Android defaults)
./gradlew --no-daemon lint
```

⚠️ **Optional production-source check:** Run `./gradlew --no-daemon compileDebugKotlin` before a broad test run to catch production Kotlin type errors early. Test tasks compile test sources separately.

For release-variant verification (signing config, ProGuard, etc.):

```bash
./gradlew --no-daemon compileReleaseKotlin
```

## ⚠️ Test Reliability: Rust JNI State Leakage

The Rust native library (`hesabyar_core`) uses global mutable state that cannot be
reset between test classes sharing the same JVM. Tests touching the Rust bridge are
tagged with `@Category(RustTest::class)` and run in a separate Gradle task
(`testDebugUnitTestRust`) with `forkEvery=1` and `maxParallelForks=1`.

**Before merging any changes that touch Rust bridge code, Rust FFI tests, or test
infrastructure, always verify with a cache-busting run:**

```bash
# rerun-tasks (re-executes everything without deleting build artifacts)
# Do NOT use `clean` — it forces full binary/NDK rebuilds and can hit Windows
# file-lock failures on `app/build` (e.g. open R.jar) when a daemon lingers.
./gradlew --no-daemon test --rerun-tasks
```

A plain `./gradlew --no-daemon test` may report "BUILD SUCCESSFUL" based on stale cached results
even when tests would actually fail. This is especially dangerous after changes to
`RustIsolationRule`, `HesabyarApp`, or `RustBridge`.

## Environment Setup

1. Copy `.env.example` to `.env`
2. Set `GEMINI_API_KEY` (required for AI features, not for core app)
3. For release builds: set `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`
4. Secrets plugin maps `.env` → `BuildConfig` fields

## Hard Constraints (Breaking These = Rejected)

- **No `Float` or `Double` for money.** Use `Long` (Rial) or `BigDecimal`.
- **No destructive Room migrations.** Schema changes must preserve existing data.
- **No hardcoded API keys.** All secrets via `.env` or Keystore.
- **No removal of Jalali calendar or offline support.**
- **No `GlobalScope`.** Use structured coroutine scopes.

## Architecture

Single-module Android app. Package root: `io.github.mojri.hesabyar`

```
ui/          → Screens (Compose), ViewModels, Theme
api/         → AI providers (GeminiParser, BudgetAdvisor, AiProvider interface)
data/        → Room entities, DAOs, Repository, ExcelExporter, BackupModels
reminder/    → WorkManager workers, notification helpers
```

Data flow: `Screen → ViewModel → Repository → Room/Network`

## Key Patterns

- **MVVM + Use Cases** (though use cases aren't a separate layer yet — logic lives in ViewModels/Repository)
- **Jalali calendar** via `JalaliCalendarHelper.kt` — all dates use this, not `java.time.LocalDate` directly
- **AI abstraction**: `AiProvider` interface with `AiProviderConfig`. Business logic must not couple to a specific provider.
- **Persian-first UX**: Full RTL, Vazirmatn font, Persian terminology in UI strings

## Testing

- Unit tests: `app/src/test/` — JUnit + Robolectric + Roborazzi (screenshot testing)
- No Android instrumentation tests currently (`app/src/androidTest/` is empty)
- Test config in `app/build.gradle.kts`: `isIncludeAndroidResources = true`, `isReturnDefaultValues = true`

## Before Changing Code — Checklist

1. Does this break offline functionality?
2. Does this bypass Jalali calendar?
3. Does this affect financial calculation accuracy?
4. Does this require a Room migration?
5. Are local backups still compatible?

## Mandatory Development Guidelines

Before writing or refactoring any code, ALWAYS verify the implementation against the following core principles:

### 1. Modular & Reusable Architecture (DRY - Don't Repeat Yourself)
- **Global Reusability (when applicable):** Methods, utility functions, components, state formatters, and models that are genuinely shared across screens MUST be defined once in a shared package (e.g., `ui/components/`, `ui/utils/`, `core/`) and reused everywhere. One-off local logic, tests, documentation, and configuration changes are exempt.
- **No In-Screen Duplication:** Never duplicate shared helper functions or UI elements inside individual screens. If a logic or UI piece is needed in more than one place, extract it immediately into a shared reusable module.

### 2. Strict Material Design 3 (M3) Standards
- **Token-Based Design:** ALWAYS use semantic Material3 color and typography tokens (`MaterialTheme.colorScheme.onSurfaceVariant`, `surfaceContainerLowest`, `MaterialTheme.typography.*`, etc.).
- **No Hardcoded Values/Alphas:** Avoid hardcoding manual colors, magic numbers, or arbitrary color alphas (like `onSurface.copy(alpha = 0.5f)`). If a specific design token is missing, define it centrally in the design system / theme module (e.g., `Theme.kt` or `Color.kt`) so that all screens maintain absolute visual consistency across Light and Dark themes.

### 3. Minimalist Code & Zero Redundancy
- **Eliminate Boilerplate:** Keep implementations clean, minimal, and free of redundant wrapper code or dead logic.
- **Refactor On-The-Fly:** When modifying any existing file, actively scan for pre-existing code duplication or anti-patterns and refactor/optimize them as part of the task.

### 4. JUnit `assertEquals` Argument Order

The 3-argument `assertEquals` signature is `assertEquals(String message, expected, actual)`,
**not** `assertEquals(expected, actual, String message)`. Passing the message last causes a
compile-time type mismatch (`Int` vs `String`). Always put the message first:

```kotlin
// ✅ Correct
assertEquals("Should have 2 distinct orders", 2, orders.size)

// ❌ Wrong — compile error
assertEquals(2, orders.size, "Should have 2 distinct orders")
```

### 5. Test Naming Convention (Codacy Compliance)
- **No backtick-quoted test names.** Codacy flags `` `fun \`name with spaces\`` `` as violations of `[a-z][a-zA-Z0-9]*`. Use camelCase instead:
  - Bad: `` fun `putForecast then getForecast returns same value`() ``
  - Good: `fun putForecastThenGetForecastReturnsSameValue()`
- **When touching existing backtick tests:** rename them to camelCase as part of the change.
- **New test files:** always use camelCase names from the start.

## Rust Changes Require Binding Regeneration

The Kotlin side talks to the Rust core (`rust/hesabyar-core`) through UniFFI bindings
generated into `app/src/main/java/io/github/mojri/hesabyar/rust/hesabyar_core.kt`.

- After **any change to Rust source** (`rust/**`), the Kotlin FFI bindings and the
  host library must be regenerated, otherwise the build/FFI calls won't reflect the change.
- Run: `./gradlew --no-daemon :app:generateAndFixBindings`
  (alias `:app:generateRustBindings` skips the package-patch/install step).
- Do not manually edit the generated `hesabyar_core.kt`; it is overwritten by the task.

> ⚠️ **Hand-maintained compat object:** the task always appends
> `app/buildSrc/template/HesabyarCore.template.kt` to the generated bindings, but it does
> **not** patch that template's signatures. When a Rust FFI function's signature changes
> (new/removed/reordered parameters), you MUST update the matching line in that template
> (add defaults for any new trailing param) and re-run `:app:generateAndFixBindings` —
> otherwise the repo's `hesabyar_core.kt` ends up with a stale `HesabyarCore.xxx()` wrapper
> that calls the regenerated top-level function with the wrong argument count.

## Rust Core Versioning

The core is bundled with the app (not published separately), so it has its own
versioning scheme, independent from the Android app version (root `VERSION` file).

- **Base version** (`MAJOR.MINOR.PATCH`): lives in `rust/Cargo.toml`
  `[workspace.package].version`. Bump it manually per SemVer:
  - MAJOR — breaking change to the FFI surface or backup schema (`BackupPayload.version`).
  - MINOR — backward-compatible feature/category added to the core API.
  - PATCH — bug fix with no API/schema change.
- **Build metadata** (`+<hash>`): auto-derived by the Gradle `:app:syncCoreVersion`
  task from a SHA-256 of the `rust/hesabyar-core/src` tree. It is written to the
  gitignored `rust/hesabyar-core/src/generated/core_version.rs` and embedded via
  `build.rs` into the `CORE_VERSION` env, exposed at runtime through
   `get_core_version()` (UniFFI). The metadata changes whenever the core source
   changes, so the bundled core version reflects the exact build.
 - Do not hand-edit `src/generated/core_version.rs`; it is regenerated on every
   binding/NDK build. `cargo build`/`cargo test` outside Gradle fall back to the
   Cargo package version.

### Backup schema version (`version` / `appVersion`)

The backup envelope carries two version fields, kept independent from both the
app `VERSION` file and the core `CORE_VERSION`:

- `version` — backup **format/schema** version. Single source of truth is the
  Rust const `BACKUP_SCHEMA_VERSION` in `hesabyar-core/src/models/mod.rs`; the
  Kotlin side derives `BuildConfig.BACKUP_SCHEMA_VERSION` from it at build time
  (see `app/build.gradle.kts`), so they cannot drift. Bump it **only** on a
  breaking change to the serialized backup structure.
- `appVersion` — the **app version** that produced the backup, written at export
  time as `BuildConfig.VERSION_NAME` (Kotlin) / `env!("CORE_VERSION")` (Rust
   default). Do not hardcode a placeholder like `"1.0"`.

## Reference Docs

- `docs/TECH_STACK.md` — official dependency list
- `docs/ROADMAP.md` — feature status
- `docs/architecture/ARCHITECTURE.md` — full architecture guide

## 🛡️ Mandatory Post-Modification Verification Workflow

Every time you modify, refactor, or introduce new code in the codebase, you **MUST** execute the following verification steps before marking the task as complete or asking for user feedback. Do not skip this under any circumstances, except when the user explicitly overrides this workflow or the change is a trivial documentation/edit with no behavioral impact.

### 1. Static Analysis & Linting (Detekt & ktlint)

First, auto-fix any code-style violations (formatting, imports, etc.):

```bash
./gradlew ktlintFormat --no-daemon
```

Then run the linting and static analysis checks to ensure no cognitive-complexity or remaining style regressions:

```bash
./gradlew ktlintCheck detekt --no-daemon
```

### 2. Unit Testing Suite

Run the local testing suite to ensure all components and boundaries function properly:

**All Kotlin tests (non-Rust + Rust isolated):**

```bash
./gradlew test --no-daemon
```

**Fast iteration (non-Rust tests only — ~4m vs ~10m combined):**

```bash
./gradlew testDebugUnitTest --no-daemon
```

**Rust-bridge tests only (when Rust bridge code was touched):**

```bash
./gradlew testDebugUnitTestRust --no-daemon
```

**Rust Core Tests (If Rust modules were touched):**

```bash
cargo test
```

### 3. Debugging & Auto-Correction

If ktlint still fails after the initial `ktlintFormat`, you may attempt another auto-fix:

```bash
./gradlew ktlintFormat --no-daemon
```

If detekt fails, fix the findings manually — `ktlintFormat` does not resolve detekt issues.

If compilation or tests fail, analyze the logs immediately, debug the root cause, apply the fix, and re-run the full verification loop until all checks are 100% green.

### Constraints

- Keep this workflow readable and well-structured in `AGENTS.md`.
- Do not overwrite existing instructions; only append or integrate this verification lifecycle cleanly.

## 📋 Evidence Standard for Completion Reports

Whenever reporting that a task, fix, or test is "done," "fixed," "already passes,"
"pre-existing," or similar, always include, unprompted:

1. **The exact CURRENT code for any changed logic** — paste the real file contents
   (or the relevant function/block) as it exists on disk right now. Not a diff
   summary, not a description of what changed, not a paraphrase.
2. **Raw test-runner output identified by exact test function name** — e.g. the
   actual JUnit XML `<testcase>` line or cargo test's per-test
   `test X ... ok` line. Aggregate counts like "all tests pass" or "39 suites,
   0 failures" are not sufficient by themselves and must be paired with the
   specific named test(s) relevant to the claim.
3. **Exact file paths and line numbers** for anything referenced.

This applies with extra weight to any claim that something was "pre-existing,"
"already fixed," or "already covered by a test" — these claims must be backed by
`git blame`, `git log`, or the actual pre-existing code showing it was already
there, not an assumption.

Do not summarize test/build success as "✅ passed" without the underlying raw
evidence available on request or included proactively for anything non-trivial
(new tests, bug fixes, security/data-integrity changes).

> Reason: this project has had multiple instances where a summary described work
> (specific test names, specific fixes) that did not actually exist in the
> committed code. Treat this as a standing requirement, not something to apply
> only when asked.

## ⚠️ Detekt Findings: Fix, Never Suppress

All detekt findings must be resolved through proper refactoring — **never** by adding
`@Suppress` annotations without a documented, justified reason. The goal is a clean
codebase, not a silent one.

### Allowed Suppressions (with justification required)

- `@Suppress("LongMethod")` in **test files** — test functions are naturally longer
  due to Arrange-Act-Assert blocks, multiple assertions, and test data setup. This is
  the only acceptable context for this suppression.
- `@Suppress("TooGenericExceptionCaught")` — **only** in two cases:
  1. Rethrowing `CancellationException` in coroutine scopes (structured concurrency).
  2. Safety-net `catch (e: Exception)` blocks where the Rust FFI layer (`RustBridge.rustCallSync`) can rethrow unchecked `RuntimeException` (including NPE). Place the annotation on the **enclosing function**, not inside the catch body. Always add a justification comment (see `ExportViewModel.exportExcel()` and `ManageBackupUseCase.parseBackupJsonKotlin()` for the pattern).
- Naming: use camelCase test names per the [Test Naming Convention](#5-test-naming-convention-codacy-compliance)
  instead of backtick-quoted names.

### Forbidden Suppressions

- `@Suppress("MagicNumber")` — extract constants or use descriptive variables instead.
- `@Suppress("UnusedPrivateMember")` — remove dead code; don't hide it.
- `@Suppress("LongParameterList")` — refactor into data classes or builder patterns.
- `@Suppress("ComplexMethod")` / `@Suppress("CognitiveComplexMethod")` — decompose
  into smaller, named functions.
- Any suppression used to avoid fixing the underlying issue.

### Refactoring Strategy for Detekt Failures

1. **Long functions** → extract named helper functions until the main function reads
   as a high-level workflow.
2. **Magic numbers** → extract to `companion object` constants or named `val`s with
   descriptive names.
3. **Long parameter lists** → group related parameters into data classes or use a builder.
4. **Complex methods** → decompose conditional logic into small, well-named functions.
5. **Cognitive complexity** → restructure control flow; prefer early returns over deep nesting.

If a detekt rule genuinely doesn't apply to a specific file (e.g., test files with
naturally long functions), add the suppression with a comment explaining WHY:
