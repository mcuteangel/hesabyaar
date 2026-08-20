# Official Tech Stack

Language:
- Kotlin
- Rust (business logic, calculations, validation)

UI:
- Jetpack Compose
- Material 3

Navigation:
- Navigation Compose

Database:
- Room

DI:
- Hilt

Networking:
- Retrofit
- OkHttp
- Moshi

Async:
- Kotlin Coroutines
- Flow

Background Tasks:
- WorkManager

Testing:
- JUnit
- MockK
- Compose UI Test

Architecture:
- MVVM + Use Cases
- Rust Core (`hesabyar-core`) — sole location for NEW business logic, calculations, and validation, exposed to Kotlin via UniFFI (permanent Kotlin fallbacks per ADR-001)

Calendar:
- Jalali Calendar Support

Native (Core):
- Rust (Cargo) — sole location for NEW business logic, calculations, and validation; Kotlin retains only ADR-001-approved permanent fallbacks (Jalali calendar, currency formatting, offline NLP parser, backup JSON parse/validate, AI advice validation)
- UniFFI — Kotlin ↔ Rust FFI bindings: `hesabyar_core.kt` is UniFFI-generated; a small hand-maintained compat wrapper (`HesabyarCore.template.kt`) is appended during `:app:generateAndFixBindings` — see AGENTS.md "Rust Changes Require Binding Regeneration" for the regeneration workflow
- cargo-ndk — cross-compilation to Android ABIs

Minimum SDK:
- Android 8+

Target SDK:
- Latest Stable Android SDK
