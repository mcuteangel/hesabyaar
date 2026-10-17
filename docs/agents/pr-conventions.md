# PR & Issue Conventions (agent guide)

> Part of the agent guide. Start at `AGENTS.md`; it says when to read this file.
> Coding agents open PRs via the API and never see `.github/pull_request_template.md` —
> this file is the source of truth for PR/issue conventions.

## PR Titles Drive the Version Bump

The Release workflow (`scripts/parse-commits.sh`) computes the next version from the
**PR title** (it becomes the squash-merge commit subject):

- `feat:` / `feat(scope):` → **minor** bump
- `fix:` / `perf:` / `refactor:` / `style:` / `test:` / `docs:` / `chore:` → **patch** bump
- `<type>!:` or `BREAKING CHANGE` in the body → **major** bump

## The `feat` Rule

Reserve `feat` for **user-facing new features** — something the user can see or do
that they could not do before.

Design-system / UI-polish work (e.g. epic #459) that adds no user-facing feature
must be titled `refactor(<scope>):` or `style(<scope>):` — **never `feat(...):`**.
Past `feat(ui):` titles for shared-component extractions (#451 HesabyarFab → v0.12.0,
#452 HesabyarTabRow → v0.13.0) caused needless minor bumps; #486 (charts) and #487
(typography) were retitled to `refactor` before merge for the same reason.

Rule of thumb: if the screen looked the same to the user before and after
(a migration to a shared component, a missing text style, a theme/RTL fix),
it is a `refactor`, not a `feat`.

## Issues

- One issue per gap; English; file:line refs; actionable scope.
- Valid out-of-scope review findings become issues (dedupe against open issues first;
  max 3/day outside the night-guard flow).
- Never close an issue without verification evidence posted as a comment.

## PR Bodies

Follow `.github/pull_request_template.md`: Summary, Changes, Type of Change
(checkbox matching the title prefix), Invariants & Quality Checklist,
Verification & Evidence with raw command output.
