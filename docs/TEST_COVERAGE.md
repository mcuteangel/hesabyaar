# Test Coverage Scope and Methodology

This document describes the test coverage architecture for Hesabyar.

## Kotlin and Android Coverage

Kotlin test coverage uses JaCoCo.
The report path is `app/build/reports/jacoco/jacocoTestReport/jacocoTestReport.xml`.
The task runs all unit tests and isolated Rust-bridge JNI tests.

### Excluded Classes

JaCoCo excludes generated code and build boilerplate from the report denominator:
- Android resource classes (`R.class`, `R$*.class`)
- Build configuration classes (`BuildConfig.*`)
- Android manifests (`Manifest*.*`)
- Room database generated implementations (`*/*_Impl*.*`)
- Hilt and Dagger generated dependency injection bindings (`*_HiltModules*.*`, `*_Factory*.*`, `*_MembersInjector*.*`)
- UniFFI generated bindings (`hesabyar_core*.*`, `Uniffi*.*`, `FfiConverter*.*`)

### Included Classes

Hand-written application source files remain included:
- Domain models and UseCases
- ViewModels and UI state formatters
- Repository implementations and Room DAOs
- Hand-written Rust bridge classes (`RustBridge.kt`, `RustMappers.kt`)

## Rust Core Coverage

Rust core coverage tests `rust/hesabyar-core`.
CI generates LCOV coverage with `cargo-llvm-cov nextest`.
The report path is `rust/lcov.info`.
The workflow prefixes source paths to `rust/hesabyar-core/`.
The resulting `rust/lcov.prefixed.info` uploads to Codacy, Codecov, Coveralls, and DeepSource.

## Metrics Interpretation

Codacy receives two distinct reports:
1. Kotlin/Android coverage from `jacocoTestReport.xml`.
2. Rust core coverage from `rust/lcov.prefixed.info`.

Neither report contaminates the other.
Generated UniFFI bindings do not artificially alter the Kotlin coverage denominator.
The percentage accurately reflects hand-written production logic across both languages.
