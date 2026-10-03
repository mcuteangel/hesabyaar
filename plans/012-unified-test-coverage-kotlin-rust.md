# Plan 012 — Unified Test Coverage for Kotlin and Rust Core

- **Issue:** #231
- **Created:** 2026-10-02
- **Author:** Claude Code
- **Status:** APPROVED

## 1. Problem Statement

Codacy receives only the JaCoCo XML report from the Android test suite.
The report path is `app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml`.
The Rust core (`rust/hesabyar-core`) executes tests in CI with `cargo test`.
However, Rust coverage reports do not reach Codacy.
Therefore, Codacy coverage shows only the JVM Android code.
It does not represent the whole production codebase.

In addition, JaCoCo includes generated UniFFI code (`hesabyar_core.kt`).
This generated file contains 10,414 instructions across 207 classes.
These generated classes distort the Kotlin coverage denominator.
Also, `android-ci.yml` executes `testDebugUnitTestRust` twice during CI runs.

## 2. Goals

1. Filter generated classes from the JaCoCo test report in `app/build.gradle.kts`.
2. Send Rust core LCOV coverage from `rust-lint.yml` to Codacy.
3. Remove redundant test executions from `android-ci.yml`.
4. Document the exact coverage scope for Kotlin and Rust components.

## 3. Detailed Design

### 3.1 Gradle JaCoCo Exclusion Filter (`app/build.gradle.kts`)

Configure `classDirectories` in task `jacocoTestReport` with an exclusion filter.
The filter excludes these file patterns:
- `**/R.class` and `**/R$*.class`
- `**/BuildConfig.*`
- `**/Manifest*.*`
- `**/*_Impl*.*` (Room generated classes)
- `**/hilt_aggregated_deps/**`
- `**/dagger/**`
- `**/hesabyar_core*.*` (UniFFI generated bindings)
- `**/Hesabyar_core*.*`
- `**/Uniffi*.*`
- `**/FfiConverter*.*`

Hand-written bridge classes like `RustBridge.kt` and `RustMappers.kt` remain included.

### 3.2 Android CI Workflow Optimization (`.github/workflows/android-ci.yml`)

The task `jacocoTestReport` depends on `testDebugUnitTest` and `testDebugUnitTestRust`.
Remove the redundant standalone step `Run Rust-bridge Tests (JNI isolated)`.
Execute `./gradlew testDebugUnitTest testDebugUnitTestRust jacocoTestReport` in one step.
This prevents running the 45 Rust-bridge tests twice.

### 3.3 Rust Coverage Upload to Codacy (`.github/workflows/rust-lint.yml`)

Add a Codacy coverage reporter step to `rust-lint.yml`.
This step uploads `rust/lcov.prefixed.info` using `codacy/codacy-coverage-reporter-action`.
The step sets `coverage-reports: rust/lcov.prefixed.info`.
The step uses the existing `CODACY_API_TOKEN` secret.

### 3.4 Documentation

- Created `docs/TEST_COVERAGE.md` describing Kotlin and Rust scope.
- Updated `progress.md` Active Task with Plan 012 status.

### 3.5 Verification Criteria

- Execute `./gradlew jacocoTestReport` locally.
- Verify `hesabyar_core.kt` is absent from the generated XML.
- Verify hand-written Kotlin files remain present in the generated XML.
- Verify GitHub Actions syntax and workflow integrity.
