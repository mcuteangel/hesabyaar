# Testing and Verification (agent guide)

> Part of the agent guide. Start at `AGENTS.md`; it says when to read this file.

## Compile Checks

Run `./gradlew --no-daemon compileDebugKotlin` before a broad test run. This check finds Kotlin type errors early. This check is optional. The test tasks compile test sources separately.

For release-variant Kotlin compilation, use this command. This type-check only. It does not use signing or ProGuard. Use the signing checks in `AGENTS.md` (`## Build & Run`) for those tasks.

```bash
./gradlew --no-daemon compileReleaseKotlin
```

For release packaging, signing, and ProGuard verification, run the configured release `assemble` or `bundle` task. For example, run `./gradlew assembleRelease` or `./gradlew bundleRelease`.

## Test Reliability: Rust JNI State Leakage

The Rust native library (`hesabyar_core`) uses global mutable state. You cannot reset this state between test classes that share the same JVM. Tests that use the Rust bridge include `@Category(RustTest::class)`. These tests run in a separate Gradle task (`testDebugUnitTestRust`) with `forkEvery=1` and `maxParallelForks=1`.

Before you merge a change to Rust bridge code, Rust FFI tests, or test infrastructure, always verify with a cache-busting run:

```bash
# rerun-tasks (re-executes tasks in the selected Gradle task graph without deleting build artifacts)
# Do NOT use `clean` — it forces full binary/NDK rebuilds and can hit Windows
# file-lock failures on `app/build` (e.g. open R.jar) when a daemon lingers.
./gradlew --no-daemon test --rerun-tasks
```

A plain `./gradlew --no-daemon test` can report "BUILD SUCCESSFUL" from stale cached results. The tests can still fail. This is dangerous after a change to `RustIsolationRule`, `HesabyarApp`, or `RustBridge`.

## Test Override Semantics: `setRustInitializedForTesting`

The Rust availability override comes from `HesabyarApp.setRustInitializedForTesting(value)`. It is a decision override. `ensureRustInitialized()` checks it before it tries to load the native library. It is not a memoization reset.

If you set it to `false`, every `RustBridge` caller uses the Kotlin fallback. This happens even when `hesabyar_core` is loadable. `ensureRustInitialized()` returns the override before it calls `System.loadLibrary`.

Before this change, `false` only reset the inited flag. The library was re-loaded on the next access. Then the "fallback" tests used the Rust path when the DLL was on `java.library.path`. Per `app/build.gradle.kts`, this is true for every test task.

Rule of thumb for test authors: a test that claims to cover the fallback or the Kotlin path must use one of these options:

- Call the Kotlin function directly.
- Pair `setRustInitializedForTesting(false)` with `RustIsolationRule`. This saves, clears, and restores the override per class.

`setRustInitializedForTesting(false)` alone forces Kotlin fallback execution for every caller. Still pair it with `RustIsolationRule` so the process-global override is saved, cleared, and restored after the test class — otherwise it leaks into every later test class.

## Test Layout

- Put unit tests in `app/src/test/`. Use JUnit, Robolectric, and Roborazzi (screenshot testing).
- There are no Android instrumentation tests.
- The test config is in `app/build.gradle.kts`. It uses `isIncludeAndroidResources = true` and `isReturnDefaultValues = true`.

## Mandatory Post-Modification Verification Workflow

Every time you modify, refactor, or introduce code, do the verification steps below. Do this before you mark the task complete or ask for feedback. Do not skip these steps. The user can override this workflow. A trivial documentation change or an edit with no behavior change is exempt.

### 1. Static Analysis and Linting (Detekt and ktlint)

First, fix the code-style violations (formatting, imports, and so on):

```bash
./gradlew ktlintFormat --no-daemon
```

Then run the linting and static analysis checks. This makes sure there are no cognitive-complexity or style regressions:

```bash
./gradlew ktlintCheck detekt --no-daemon
```

### 2. Unit Testing Suite

Run the local testing suite. This makes sure all components and boundaries work correctly.

**All Kotlin tests (non-Rust + Rust isolated):**

```bash
./gradlew test --no-daemon
```

**Fast iteration (non-Rust tests only — ~4m vs ~10m combined):**

```bash
./gradlew testDebugUnitTest --no-daemon
```

**Rust-bridge tests only (when you changed Rust bridge code):**

```bash
./gradlew testDebugUnitTestRust --no-daemon
```

**Rust Core Tests (if you changed Rust modules):**

```bash
cargo test --manifest-path rust/Cargo.toml
```


### 3. Debugging and Auto-Correction

If ktlint still fails after the initial `ktlintFormat`, try another auto-fix:

```bash
./gradlew ktlintFormat --no-daemon
```

If detekt fails, fix the findings manually. `ktlintFormat` does not resolve detekt issues.

If compilation or tests fail, analyze the logs immediately. Find the root cause. Apply the fix. Run the full verification loop again until all checks pass.

### 4. Quick-Check Scripts

Three convenience scripts automate the verification workflow. They are read-only checks:

- `scripts/check-rust.sh`: checks clippy and unit tests for the Rust workspace.
- `scripts/check-android.sh`: runs ktlint, detekt, non-Rust unit tests, and Android lint.
- `scripts/check-rust-bridge.sh`: runs isolated Rust-bridge JVM tests (`testDebugUnitTestRust`).

Use `check-rust.sh` for changes in `rust/**`. Use `check-android.sh` for Kotlin/UI changes. Use `check-rust-bridge.sh` (or run with `--rerun-tasks` to avoid stale Gradle UP-TO-DATE caches when native binaries change) whenever FFI signatures, bridge bindings, or native loaders change.
