# Hesabyar – Agent Guide

> Keep this file short. Put detailed rules in `docs/agents/` and link them from `## Read Before You Work`.

## Project Identity

Hesabyar is a personal finance app for Android. It is Persian-first. It works offline. AI support (Gemini/OpenRouter) is optional. It is not part of the main app.

## Hard Constraints

The system rejects any change that breaks these constraints:

- Do not use `Float` or `Double` for money. Use `Long` (Rial) or `BigDecimal`.
- Do not use destructive Room migrations. A schema change must preserve existing data.
- Do not hardcode API keys. Use `.env` or Keystore for all secrets.
- Do not remove the Jalali calendar or offline support.
- Do not implement new feature business logic in Kotlin. New feature rules, calculations, validations, and rule-driven data transformations MUST go in the Rust core (`rust/hesabyar-core`). UI rendering, persistence, and adapter/mapping code (DTO conversion, entity mapping) stay normal Kotlin territory and need no exception-list justification. Kotlin fallbacks for business logic are permitted only for the pre-approved exceptions. See `## Business Logic Policy`.
- Do not use `GlobalScope`. Use structured coroutine scopes.

## Checklist Before You Change Code

1. Does this break offline functionality?
2. Does this bypass the Jalali calendar?
3. Does this affect financial calculation accuracy?
4. Does this require a Room migration?
5. Are local backups still compatible?
6. Does this introduce new business logic in Kotlin that should be in the Rust core instead? (See `## Business Logic Policy`.)

## Business Logic Policy

Rust Core (`rust/hesabyar-core`) is the sole location for new business logic. Kotlin fallbacks are permitted ONLY for the pre-approved exception list. See `docs/architecture/ADR-001-rust-sole-implementation.md` (`## Decision` and `### Permanent Kotlin Fallbacks (Exception List)`) for the full policy, exception list, and the phased removal plan for non-exception fallbacks.

## Read Before You Work

Read only the guides that match your task. Each one is short.

| When you… | Read |
|---|---|
| Change any code (before you report "done") | `docs/agents/testing.md` — verification workflow, Rust JNI test isolation |
| Write or refactor Kotlin / Compose code | `docs/agents/code-guidelines.md` — DRY, M3, RTL, Jalali, strings, data flow |
| Edit or refactor any Kotlin file | `docs/agents/detekt.md` — fix on touch, never re-baseline |
| Change `rust/**` or the FFI surface | `docs/agents/rust.md` — binding regeneration, core versioning |
| Change a Room entity or the schema | `docs/agents/room-migrations.md` |
| Start, continue, or finish any task | `docs/agents/progress-tracking.md` — `progress.md` rules |
| Search the codebase (Graphify, Serena) | `docs/agents/code-intelligence.md` |
| Execute a plan | `plans/README.md` and the plan file |

## Verification (summary)

Before you report a code change as done: run `./gradlew ktlintFormat ktlintCheck detekt --no-daemon`, the matching tests (`testDebugUnitTest`, `testDebugUnitTestRust`, or `cargo test --manifest-path rust/Cargo.toml`), and give the evidence below. Details: `docs/agents/testing.md`.

## Evidence Standard for Completion Reports

When you report that a task, fix, or test is "done," "fixed," "already passes," or "pre-existing," always include the evidence below without a prompt:

1. The exact current code for any changed logic. Paste the real file contents (or the relevant function/block) as it exists on disk now. Do not give a diff summary, a description of what changed, or a paraphrase.
2. Raw test-runner output identified by the exact test function name. For example, the JUnit XML `<testcase>` line or the cargo test per-test `test X ... ok` line. Aggregate counts like "all tests pass" or "39 suites, 0 failures" are not enough by themselves. Pair them with the specific named test(s) for the claim.
3. Exact file paths and line numbers for anything you reference.

This applies with extra weight to a claim that something was "pre-existing," "already fixed," or "already covered by a test." Back these claims with `git blame`, `git log`, or the actual pre-existing code. Do not use an assumption.

Do not summarize test or build success as "passed" without the underlying raw evidence. Include the evidence proactively for any non-trivial change (new tests, bug fixes, security/data-integrity changes).

> Reason: this project had many cases where a summary described work (specific test names, specific fixes) that did not exist in the committed code. Treat this as a standing requirement. Do not apply it only when asked.

## Language and Response Policy

Do not write replies in Chinese. Match the user language (Persian / English). Write code comments and documentation in English. Write commit messages and pull request descriptions in English.

## Documentation Style

Write all agent-facing documentation and code comments in ASD-STE100 (Simplified Technical English):

- Use short, clear sentences (maximum 20 words per sentence).
- Use active voice.
- Use one idea per sentence.
- Use consistent, precise terms — never switch between synonyms for the same concept.
- Avoid ambiguous words, jargon, and adverbial qualifiers.
- Follow this style in every agent contribution unless the Language and Response Policy specifies a different language.

## Build & Run

```bash
# Debug build (only needs GEMINI_API_KEY in .env)
./gradlew --no-daemon installDebug

# Release signing (requires .env with KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD)
./gradlew --no-daemon generateKeystore   # first time only
./gradlew --no-daemon checkSigningConfig  # verify signing config

# Run all unit tests (non-Rust + Rust isolated)
./gradlew --no-daemon test

# Run fast (non-Rust) tests only — no JNI fork overhead (~2m vs ~7m)
./gradlew --no-daemon testDebugUnitTest

# Run Rust-bridge tests only — with JNI isolation (forkEvery=1)
./gradlew --no-daemon testDebugUnitTestRust

# Run single non-Rust test class (fast task — Rust-tagged classes are excluded here)
./gradlew --no-daemon testDebugUnitTest --tests "io.github.mojri.hesabyar.TransactionTest"

# Run single Rust-tagged test class (must use the isolated task)
./gradlew --no-daemon testDebugUnitTestRust --tests "io.github.mojri.hesabyar.rust.AiAdviceSanitizationTest"

# Lint / static analysis (no custom config, uses Android defaults)
./gradlew --no-daemon lint
```

Compile-only and release checks: `docs/agents/testing.md` (`## Compile Checks`).

## Environment Setup

1. Copy `.env.example` to `.env`.
2. Set `GEMINI_API_KEY`. This is required for AI features. It is not required for the core app.
3. For release builds, set `KEYSTORE_PASSWORD`, `KEY_ALIAS`, and `KEY_PASSWORD`.
4. The secrets plugin maps `.env` to `BuildConfig` fields.

## Architecture

This is a single-module Android app. The package root is `io.github.mojri.hesabyar`.

```
ui/           → Screens (Compose), ViewModels, Theme
api/          → AI providers (GeminiParser, BudgetAdvisor, AiProvider interface)
data/         → Room entities, DAOs, Repository, ExcelExporter, BackupModels
rust/         → UniFFI bridge (RustBridge.kt), generated bindings (hesabyar_core.kt)
reminder/     → WorkManager workers, notification helpers
rust/hesabyar-core/ → Rust core crate (all business logic, calculations, advisory)
```

The data flow is: `Screen → ViewModel → UseCase → RustBridge → Rust core (business logic)` alongside `ViewModel/UseCase → Repository → Room/Network (persistence)`. Some existing ViewModels (e.g., AccountViewModel, AnalyticsViewModel) call the Repository directly; new code should prefer the Use Case layer.

## Key Patterns

- Use MVVM and Use Cases. Business logic lives in the Rust core (`rust/hesabyar-core`); Kotlin ViewModels, UseCases, and the Repository orchestrate calls to Rust via `RustBridge` and handle Android-specific concerns (persistence, UI state, DI). They are NOT where new business logic should be added.
- Use the Jalali calendar through `JalaliCalendarHelper.kt`. All dates use it. Do not use `java.time.LocalDate` directly.
- Use the AI abstraction. `AiProvider` is the interface with `AiProviderConfig`. Business logic must not link to a specific provider.
- Use the Persian-first UX. Use full RTL, the Vazirmatn font, and Persian terms in the UI strings.

## CI Warnings Report

`.github/workflows/ci-warnings-report.yml` collects warnings and deprecations from every workflow run of a commit. It collects check-run annotations and raw job logs.
For pull requests, it maintains one sticky PR comment.
For non-PR runs on `main`, it updates the open "CI warnings on main" issue (#303). Other branches without a PR are skipped.
When you add or rename a workflow, add its exact `name:` to that file's `workflow_run.workflows` list. `.github/scripts/ci-warnings.test.mjs` fails until you do.
New warning kinds land in "Other". Teach the parser in `.github/scripts/ci-warnings.mjs` with a test instead of ignoring them.

## Keeping Docs in Sync

- When a change renames, moves, or deletes a file, update every doc that names it in the same PR. `python3 scripts/check_docs.py` lists broken path references; CI (`docs-check.yml`) fails on them.
- Before executing a plan, run the plan's drift check (if defined) and `scripts/check_docs.py`; fix stale references in that plan first.
- Only approved `plans/*.md` are executable. `plans/archive/` holds finished plans: never execute them or trust their file references.
- A plan that reaches DONE or REJECTED moves to `plans/archive/` in the same PR, with its row in `plans/README.md` updated.
- Reference suppression markers:
  - `<!-- check-docs: ignore -->` skips an intentional non-existent path on that line.
  - `<!-- check-docs: planned -->` in a heading suppresses code span checks in that section for paths planned in future phases.
  - Explicit new-file headings (such as `## Files to Create` or `### فایل‌های جدید`) suppress code span checks in that section.

## Reference Docs

- `docs/TECH_STACK.md` — the official dependency list
- `docs/ROADMAP.md` — the feature status
- `docs/architecture/ARCHITECTURE.md` — the full architecture guide
- `docs/architecture/ADR-001-rust-sole-implementation.md` — Rust-first business logic policy decision record
- `docs/LOCALIZATION.md` — String resources, localization, and key reusability guidelines
