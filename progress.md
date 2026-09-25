# Current Progress

> This file tracks the current working state for AI coding agents.
> It is not an architecture specification, changelog, or source of truth.
> Always verify important claims against the current repository and `AGENTS.md`.

## Active Task

None.

## Completed Tasks

- **Task:** Vibe Coding preparation
- **Status:** Completed
- **Completed Date:** 2026-09-25
- **Deliverables:**
  - [x] `AGENTS.md` additions (Architecture Pattern, UI Constraints, Room Migration Checklist, Progress Tracking, Quick-Check Scripts)
  - [x] `rust/hesabyar-core/README.md` (UniFFI procedural macro architecture, canonical money rules, method lifecycle, quick checks)
  - [x] `scripts/check-rust.sh` (clippy + workspace tests)
  - [x] `scripts/check-android.sh` (ktlintCheck + detekt + testDebugUnitTest + lintDebug)
  - [x] `scripts/check-rust-bridge.sh` (testDebugUnitTestRust without `--rerun-tasks` per user request)
  - [x] Prompt templates in `.github/prompts/` (`new-compose-screen.md`, `new-rust-method.md`, `bugfix-or-db-optimization.md`)
  - [x] `progress.md` lifecycle documentation and baseline

## Blocked

- None.

## Findings

1. **Room Schema Documentation Drift:** `AppDatabase.kt` defines schema `version = 9` with `exportSchema = false`. However, `docs/DATABASE_SCHEMA.md` lists schema version 3, and `docs/MIGRATION_NOTES.md` documents migrations only through v3.
2. **Missing Migration Test Infrastructure:** `MigrationTestHelper` test suite is not currently present in `app/src/test`.
3. **UniFFI Implementation:** UniFFI uses procedural macros (`#[uniffi::export]`) and scaffolding (`uniffi::setup_scaffolding!()`). No `.udl` interface definition files exist.
4. **Android Lint Failure in Pre-existing Code:** `scripts/check-android.sh` fails on task `:app:lintDebug` with 4 errors:
   - 1 local machine issue: `local.properties:8` (`PropertyEscape` on unescaped Windows backslashes in `sdk.dir`).
   - 3 pre-existing Compose issues: `ManualTransactionDialog.kt:132, 138, 149` (`LocalContextGetResourceValueCall` from querying resources using `LocalContext.current`).
   Per task rules, application code was left unchanged.

## Decisions

- Retained existing `rust/hesabyar-core/README.md` technical build and pre-commit hook instructions while adding UniFFI architecture, canonical money rules, and new method lifecycle.
- Kept all check scripts strictly read-only (`check-android.sh` does not run `ktlintFormat`).
- Removed `--rerun-tasks` from `scripts/check-rust-bridge.sh` per direct user instruction during execution.

## Verification

| Check | Command | Result |
|---|---|---|
| Rust | `./scripts/check-rust.sh` | PASS (490 unit tests passed; clippy clean; exit 0) |
| Android | `./scripts/check-android.sh` | FAILED at lintDebug (ktlintCheck UP-TO-DATE; detekt UP-TO-DATE; testDebugUnitTest PASS; lintDebug failed with 4 errors in pre-existing files; exit 1) |
| Rust Bridge | `./scripts/check-rust-bridge.sh` | PASS (45 actionable tasks executed; exit 0) |

## Next Steps

1. Address pre-existing `LocalContextGetResourceValueCall` lint errors in `ManualTransactionDialog.kt` in a dedicated task.
2. Align `docs/DATABASE_SCHEMA.md` and `docs/MIGRATION_NOTES.md` with database version 9.
3. Configure Room schema export and add `MigrationTestHelper` integration tests.

## Last Updated

2026-09-25
