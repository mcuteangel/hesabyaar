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

## Creating the PR: Use scripts/new-pr.sh

The GitHub API never shows `.github/pull_request_template.md`, so never call
`gh pr create` (or the API) directly. Always go through the gate:

```sh
scripts/new-pr.sh --title "refactor(ui): ..." --body-file pr-body.md --base main
```

The script prints the template rules, then validates before creating the PR:

- title must be conventional-commit shaped: `type[(scope)][!]: subject`
- a `feat` title bumps the MINOR version: pass `--i-confirm-minor-bump` only
  after confirming it is a user-facing feature (without it the script asks on
  a TTY, and fails closed otherwise)
- body must contain `## Summary`, `## Changes`, `## Type of Change` and
  `## Verification & Evidence`, with the `- [x]` box matching the title type

If you must use the API directly (no `gh` auth), run with `--validate-only`
first and fix every violation before creating the PR.

## CI Enforcement

`.github/workflows/pr-title.yml` checks every PR title on open/edit/sync:

- malformed titles (not `type[(scope)][!]: subject`) **fail** the check
- `feat` titles pass but get a **warning** annotation: they bump MINOR

The check cannot tell a real feature from a mislabeled refactor — that
judgment stays with the author. When in doubt, prefer `refactor`.

## PR Bodies

Follow `.github/pull_request_template.md`: Summary, Changes, Type of Change
(checkbox matching the title prefix), Invariants & Quality Checklist,
Verification & Evidence with raw command output.
