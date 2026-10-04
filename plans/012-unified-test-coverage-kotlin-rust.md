# Plan 012 — Unified Test Coverage for Kotlin and Rust Core

- **Issue:** #231
- **PR:** #301
- **Created:** 2026-10-02
- **Author:** Claude Code
- **Status:** IMPLEMENTATION COMPLETE (PR #301 open, CI pending)

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

### 3.1 Gradle JaCoCo Configuration (`gradle/jacoco-coverage.gradle.kts`)

All JaCoCo coverage configuration is extracted to `gradle/jacoco-coverage.gradle.kts`
and applied from `app/build.gradle.kts` with `apply(from = "$rootDir/gradle/jacoco-coverage.gradle.kts")`.
This keeps `app/build.gradle.kts` concise.

The file configures `jacocoTestReport` with two complementary filters:
1. `jacocoReportExcludes` pattern list for static boilerplate:
    - `**/R.class` and `**/R\$*.class`
    - `**/BuildConfig.*`
    - `**/Manifest*.*`
    - `**/*_Impl.class` and `**/*_Impl\$*.class` (Room generated classes)
    - `**/hilt_aggregated_deps/**`, `**/dagger/**`, `**/*_HiltModules*.*`, `**/*_Factory*.*`, `**/*_MembersInjector*.*`, `**/Hilt_*.*`, `**/DaggerHesabyarApp*.*`, `**/HesabyarApp_HiltComponents*.*`
    - `**/hesabyar_core*.*`, `**/Hesabyar_core*.*`, `**/Uniffi*.*`, `**/FfiConverter*.*`
2. Dynamic `handWrittenClassSpec` filter on `io/github/mojri/hesabyar/rust/`:
    UniFFI emits ~40 top-level data classes into package `io.github.mojri.hesabyar.rust`.
    A hard-coded class-name list drifts whenever the FFI surface changes.
    The dynamic spec keeps only files whose names start with `RustBridge` or `RustMappers`.
    All generated FFI classes are excluded automatically.
    A dedicated `checkRustBridgeCoverageScope` task walks both source roots recursively and fails fast on unrecognized hand-written files.

### 3.2 Android CI Workflow Optimization (`.github/workflows/android-ci.yml`)

The task `jacocoTestReport` depends on `checkRustBridgeCoverageScope`, `testDebugUnitTest`, and `testDebugUnitTestRust`.
The scope guard runs before both test suites (the test tasks declare an explicit `dependsOn(checkRustBridgeCoverageScope)`) and fails fast on unrecognized files.
The workflow executes `./gradlew :app:testDebugUnitTest :app:testDebugUnitTestRust :app:jacocoTestReport` in a single step, then asserts the XML report exists and is non-empty.
Naming the test tasks alongside `jacocoTestReport` ensures both suites run if dependency wiring changes. The `test -s` step verifies that the report exists and is non-empty.

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
