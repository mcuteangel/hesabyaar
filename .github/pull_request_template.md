## Summary

<!-- Briefly describe the purpose and outcome of this pull request. -->

## Changes

<!-- List specific changes made in this PR. -->
- 

## Type of Change

<!-- The PR title prefix drives the automatic version bump (scripts/parse-commits.sh):
     feat: → minor | fix/perf/refactor/style/test/docs/chore: → patch | <type>!: → major.
     Reserve `feat` for user-facing new features. Design-system / UI-polish work
     (e.g. epic #459) that adds no user-facing feature must be titled
     `refactor(ui):` or `style(ui):` — NOT `feat(ui):` — otherwise it needlessly
     bumps the minor version. -->

- [ ] `feat`: New user-facing feature (→ minor version bump)
- [ ] `fix`: Bug fix (→ patch)
- [ ] `refactor`: Code refactoring without behavior change (→ patch)
- [ ] `style`: UI styling / design-system change without behavior change (→ patch)
- [ ] `perf`: Performance improvement (→ patch)
- [ ] `test`: Adding or updating tests (→ patch)
- [ ] `docs`: Documentation updates (→ patch)
- [ ] `chore`: Build, CI, or tooling maintenance (→ patch)

## Invariants & Quality Checklist

- [ ] **Architecture & Business Logic:** Business rules and financial calculations are located in `rust/hesabyar-core` (or belong to documented exceptions). No new business logic in Kotlin.
- [ ] **Money Representation:** Amounts use `i64` in Rust and `Long` in Kotlin (representing Rial). No `Float` or `Double` used for money.
- [ ] **Room Database & Migrations:** If schema/entities changed, explicit migration is included without `fallbackToDestructiveMigration()`. New JSON schema and `MigrationTestHelper` test are referenced.
- [ ] **Persian & RTL:** Persian-first UI, Jalali calendar via `JalaliCalendarHelper.kt`, and native RTL layout (LTR used only for numerical formatting where justified).
- [ ] **Secrets:** No API keys or credentials hardcoded.
- [ ] **Style & Detekt:** Code passes `./gradlew ktlintCheck detekt`. No `@Suppress` added to bypass new findings.

## Verification & Evidence

<!-- Paste exact verification commands and raw test/lint results below. -->

```bash
# Verification command (e.g. ./scripts/check-rust.sh, ./scripts/check-android.sh, or ./scripts/check-rust-bridge.sh)

```

```text
# Raw test output / evidence
```
