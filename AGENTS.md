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

<!-- headroom:rtk-instructions -->
# RTK (Rust Token Killer) - Token-Optimized Commands

When running shell commands, **always prefix with `rtk`**. This reduces context
usage by 60-90% with zero behavior change. If rtk has no filter for a command,
it passes through unchanged — so it is always safe to use.

## Key Commands

```bash
# Git (59-80% savings)
rtk git status          rtk git diff            rtk git log

# Files & Search (60-75% savings)
rtk ls <path>           rtk read <file>         rtk grep <pattern>
rtk find <pattern>      rtk diff <file>

# Test (90-99% savings) — shows failures only
rtk pytest tests/       rtk cargo test          rtk test <cmd>

# Build & Lint (80-90% savings) — shows errors only
rtk tsc                 rtk lint                rtk cargo build
rtk prettier --check    rtk mypy                rtk ruff check

# Analysis (70-90% savings)
rtk err <cmd>           rtk log <file>          rtk json <file>
rtk summary <cmd>       rtk deps                rtk env

# GitHub (26-87% savings)
rtk gh pr view <n>      rtk gh run list         rtk gh issue list

# Infrastructure (85% savings)
rtk docker ps           rtk kubectl get         rtk docker logs <c>

# Package managers (70-90% savings)
rtk pip list            rtk pnpm install        rtk npm run <script>
```

## Rules

- In command chains, prefix each segment: `rtk git add . && rtk git commit -m "msg"`
- For debugging, use raw command without rtk prefix
- `rtk proxy <cmd>` runs command without filtering but tracks usage
<!-- /headroom:rtk-instructions -->
