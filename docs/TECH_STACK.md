# Official Tech Stack

Language:
- Kotlin

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
- Rust Core (`hesabyar-core`) — sole location for business logic, calculations, and validation, exposed to Kotlin via UniFFI

Calendar:
- Jalali Calendar Support

Native (Core):
- Rust (Cargo) — new business logic computation (Kotlin retains only ADR-001 exception fallbacks)
- UniFFI — Kotlin ↔ Rust FFI bindings: `hesabyar_core.kt` is UniFFI-generated; a small hand-maintained compat wrapper (`HesabyarCore.template.kt`) is appended during `:app:generateAndFixBindings` — see AGENTS.md "Rust Changes Require Binding Regeneration" for the regeneration workflow
- cargo-ndk — cross-compilation to Android ABIs

Minimum SDK:
- Android 8+

Target SDK:
- Latest Stable Android SDK
