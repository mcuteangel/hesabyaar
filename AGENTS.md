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

## Rust Changes Require Binding Regeneration

The Kotlin side talks to the Rust core (`rust/hesabyar-core`) through UniFFI bindings
generated into `app/src/main/java/io/github/mojri/hesabyar/rust/hesabyar_core.kt`.

- After **any change to Rust source** (`rust/**`), the Kotlin FFI bindings and the
  host library must be regenerated, otherwise the build/FFI calls won't reflect the change.
- Run: `./gradlew :app:generateAndFixBindings --no-daemon`
  (alias `:app:generateRustBindings` skips the package-patch/install step).
- Append `--no-daemon` to every `./gradlew` command unless the user explicitly asks
  for a daemonized run.
- Do not manually edit the generated `hesabyar_core.kt`; it is overwritten by the task.

## Rust Core Versioning

The core is bundled with the app (not published separately), so it has its own
versioning scheme, independent from the Android app version (root `VERSION` file).

- **Base version** (`MAJOR.MINOR.PATCH`): lives in `rust/Cargo.toml`
  `[workspace.package].version`. Bump it manually per SemVer:
  - MAJOR — breaking change to the FFI surface or backup schema (`BackupPayload.version`).
  - MINOR — backward-compatible feature/category added to the core API.
  - PATCH — bug fix with no API/schema change.
- **Build metadata** (`+<hash>`): auto-derived by the Gradle `:app:syncCoreVersion`
  task from a SHA-256 of the `rust/hesabyar-core/src` tree. It is written to the
  gitignored `rust/hesabyar-core/src/generated/core_version.rs` and embedded via
  `build.rs` into the `CORE_VERSION` env, exposed at runtime through
   `get_core_version()` (UniFFI). The metadata changes whenever the core source
   changes, so the bundled core version reflects the exact build.
 - Do not hand-edit `src/generated/core_version.rs`; it is regenerated on every
   binding/NDK build. `cargo build`/`cargo test` outside Gradle fall back to the
   Cargo package version.

### Backup schema version (`version` / `appVersion`)

The backup envelope carries two version fields, kept independent from both the
app `VERSION` file and the core `CORE_VERSION`:

- `version` — backup **format/schema** version. Single source of truth is the
  Rust const `BACKUP_SCHEMA_VERSION` in `hesabyar-core/src/models/mod.rs`; the
  Kotlin side derives `BuildConfig.BACKUP_SCHEMA_VERSION` from it at build time
  (see `app/build.gradle.kts`), so they cannot drift. Bump it **only** on a
  breaking change to the serialized backup structure.
- `appVersion` — the **app version** that produced the backup, written at export
  time as `BuildConfig.VERSION_NAME` (Kotlin) / `env!("CORE_VERSION")` (Rust
   default). Do not hardcode a placeholder like `"1.0"`.

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

## 🛡️ Mandatory Post-Modification Verification Workflow

Every time you modify, refactor, or introduce new code in the codebase, you **MUST** execute the following verification steps before marking the task as complete or asking for user feedback. Do not skip this under any circumstances, except when the user explicitly overrides this workflow or the change is a trivial documentation/edit with no behavioral impact.

### 1. Static Analysis & Linting (Detekt & ktlint)

Run the linting and static analysis checks to ensure no code-style or cognitive-complexity regressions were introduced:

```bash
./gradlew ktlintCheck detekt --no-daemon
```

### 2. Unit Testing Suite

Run the local testing suite to ensure all components and boundaries function properly:

**Kotlin/Android Tests:**

```bash
./gradlew test --no-daemon
```

**Rust Core Tests (If Rust modules were touched):**

```bash
cargo test
```

### 3. Critical Process Isolation Flag (`--no-daemon`)

- **Rule:** You MUST append the `--no-daemon` flag to every single `./gradlew` command you execute, unless the user explicitly requests a daemonized run or the command is a non-build utility that has no daemon.
- **Reason:** Running Gradle in-process or leaving background compiler daemons alive will cause the agent environment to freeze, hang, or lock file descriptors, breaking the execution loop. Forcing `--no-daemon` ensures the process terminates cleanly after compilation/testing finishes.

### 4. Debugging & Auto-Correction

If detekt or ktlint fails due to formatting issues, you may attempt an auto-fix using:

```bash
./gradlew ktlintFormat --no-daemon
```

If compilation or tests fail, analyze the logs immediately, debug the root cause, apply the fix, and re-run the full verification loop until all checks are 100% green.

### Constraints

- Keep this workflow readable and well-structured in `AGENTS.md`.
- Do not overwrite existing instructions; only append or integrate this verification lifecycle cleanly.
