# Hesabyar Documentation Hub

Start here. This index groups every document in `docs/` and `plans/`. It is Obsidian-friendly: plain Markdown, relative links, no plugins needed.

## Start here

- [Project overview](PROJECT_OVERVIEW.md) — what Hesabyar is, features, architecture, tech stack, repo layout
- [Module map](MODULE_MAP.md) — packages, Rust modules, and where each feature lives
- [Domain glossary](GLOSSARY.md) — exact terms for money, loans, backup, AI, and architecture
- [Development guide](DEVELOPMENT.md) — build, test, lint, FFI regeneration, hard constraints
- [Roadmap](ROADMAP.md) — feature status: done, in progress, planned
- [Tech stack](TECH_STACK.md) — official dependency list

## Architecture and decisions

- [Architecture guide](architecture/ARCHITECTURE.md) — vision, principles, data flow
- [ADR-001: Rust sole implementation](architecture/ADR-001-rust-sole-implementation.md) — business logic lives in the Rust core; Kotlin fallback policy
- [Agent guide](../AGENTS.md) — repo house rules for coding agents (style, verification workflow, checklists)

## Database and backup

- [Database schema](DATABASE_SCHEMA.md) — tables, columns, migrations, relationships (schema baseline)
- [Migration notes](MIGRATION_NOTES.md) — Room migration history
- [Backup format](BACKUP_FORMAT.md) — JSON structure, restore modes (REPLACE/MERGE), validation rules

## Features

- [AI providers](AI_PROVIDERS.md) — Gemini, OpenRouter, custom endpoints, offline fallback
- [Security](SECURITY.md) — API-key storage, build secrets, and encryption boundaries
- [Build and release](BUILD_RELEASE.md) — signing, release packaging
- [Test coverage](TEST_COVERAGE.md) — Kotlin JaCoCo and Rust core coverage scopes

## CI and automation

- [GitHub Actions pinning](ci/github-actions-pinning.md) — third-party Action SHA pinning policy
- Workflows live in [.github/workflows/](../.github/workflows/) — `android-ci.yml`, `release.yml`, `lint.yml`, `ocr-review.yml`, and others

## Refactor and design documents

- [Account management refactor checklist](account-mgmt-refactor/00-checklist.md) — 7-phase refactor checklist (Phases 0–6)
  - [Phase 0: Bug fixes](account-mgmt-refactor/01-phase0-bugfixes.md)
  - [Phase 1: Foundation](account-mgmt-refactor/02-phase1-foundation.md)
  - [Phase 2: State](account-mgmt-refactor/03-phase2-state.md)
  - [Phase 3: Components](account-mgmt-refactor/04-phase3-components.md)
  - [Phase 4: Screen](account-mgmt-refactor/05-phase4-screen.md)
  - [Phase 5: UX](account-mgmt-refactor/06-phase5-ux.md)
  - [Phase 6: Tests](account-mgmt-refactor/07-phase6-tests.md)
- [Account management blueprint](blueprint-account-management.md) — design blueprint
- [Multi-account dashboard redesign](2026-07-29-multi-account-dashboard-redesign-design.md) — dated design doc

## Plans

Dated plans live in [`plans/`](../plans/). Index: [plans/README](../plans/README.md). Recent plans:

- [UI/UX overhaul in prioritized phases](../plans/013-ui-ux-overhaul.md)
- [Unified test coverage (Kotlin + Rust)](../plans/012-unified-test-coverage-kotlin-rust.md)
- [Personal loan ledger redesign](../plans/011-personal-loan-ledger-redesign.md)
- [Multi-account wallet support](../plans/009-multi-account-wallet-support.md)
- [Rust fallback consolidation](../plans/2026-08-19-rust-fallback-consolidation-plan.md)
- [Fix stale docs and roadmap markers](../plans/006-fix-stale-docs-and-roadmap-markers.md)

## Process

- [Code review guide](CODE_REVIEW.md) — review standards for this repo
