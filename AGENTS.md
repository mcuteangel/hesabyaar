# Hesabyar – Agent Guide

## Project Identity

Persian-first personal finance app (Android). Offline-first. AI (Gemini/OpenRouter) is optional enhancement, not core.

## Build & Run

```bash
# Debug build (only needs GEMINI_API_KEY in .env)
./gradlew installDebug

# Release signing (requires .env with KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD)
./gradlew generateKeystore   # first time only
./gradlew checkSigningConfig  # verify signing setup

# Run unit tests
./gradlew test

# Run single test class
./gradlew test --tests "io.github.mojri.hesabyar.TransactionTest"

# Lint / static analysis (no custom config, uses Android defaults)
./gradlew lint
```

**No CI/CD is configured.** No GitHub Actions workflows exist.

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

## Reference Docs

- `docs/TECH_STACK.md` — official dependency list
- `docs/ROADMAP.md` — feature status
- `docs/architecture/ARCHITECTURE.md` — full architecture guide
