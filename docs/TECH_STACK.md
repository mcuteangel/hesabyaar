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
- Rust (Cargo) — all business logic computation
- UniFFI — Kotlin ↔ Rust FFI bindings (generated, not hand-maintained)
- cargo-ndk — cross-compilation to Android ABIs

Minimum SDK:
- Android 8+

Target SDK:
- Latest Stable Android SDK
