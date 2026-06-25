# Build & Release Instructions

## Prerequisites

- Android Studio Hedgehog (2023.1) or later
- JDK 11+
- Android SDK 36
- Gradle 8.x

---

## Environment Setup

1. Copy `.env.example` to `.env`
2. Edit `.env` with your values:

```bash
# Required for AI features (optional for core app)
GEMINI_API_KEY=your_api_key_here

# Required for release builds only
KEYSTORE_PASSWORD=your_keystore_password
KEY_ALIAS=your_key_alias
KEY_PASSWORD=your_key_password
```

---

## Debug Build

```bash
# Install debug APK on connected device
./gradlew installDebug

# Build debug APK without installing
./gradlew assembleDebug
```

Debug builds:
- Use debug signing config
- Include debuggable flag
- Use `.env` for GEMINI_API_KEY only

---

## Release Build

### First-time Setup

```bash
# Generate release keystore (requires KEYSTORE_PASSWORD and KEY_PASSWORD in .env)
./gradlew generateKeystore

# Verify signing configuration
./gradlew checkSigningConfig
```

### Build Release APK

```bash
# Build release APK
./gradlew assembleRelease
```

Release builds:
- Signed with release keystore
- Minified (when enabled)
- Not debuggable
- Requires all signing credentials in `.env`

---

## Testing

```bash
# Run all unit tests
./gradlew test

# Run single test class
./gradlew test --tests "io.github.mojri.hesabyar.OfflineParserTest"

# Run lint checks
./gradlew lint
```

---

## Build Variants

| Variant    | Signing       | Debuggable | Minified |
|------------|---------------|------------|----------|
| debug      | Debug key     | Yes        | No       |
| release    | Release key   | No         | No*      |

*Minification can be enabled by setting `isMinifyEnabled = true` in `build.gradle.kts`.

---

## Secrets Management

- `.env` file is git-ignored (never committed)
- `.env.example` contains placeholder values
- Secrets Gradle Plugin maps `.env` → `BuildConfig` fields
- Keystore file (`my-upload-key.jks`) is git-ignored

---

## Troubleshooting

### Kotlin Daemon Issues

If Kotlin daemon fails to connect:
```bash
./gradlew --stop
./gradlew assembleDebug --rerun-tasks
```

### Missing .env

Build will use `.env.example` defaults. AI features will be unavailable.

### Keystore Not Found

Run `./gradlew generateKeystore` first. Requires `KEYSTORE_PASSWORD` and `KEY_PASSWORD` in `.env`.
