# PR & Issue Conventions (agent guide)

> Part of the agent guide. Start at `AGENTS.md`; it says when to read this file.
> Coding agents open PRs via the API and never see `.github/pull_request_template.md` —
> this file is the source of truth for PR/issue conventions.

## Commit Messages Drive the Version Bump

The Release workflow (`scripts/parse-commits.sh`) computes the next version from
the **commit subjects** on the merged branch (`git log` subjects, `Merge `
subjects skipped) — not from the PR title:

- `feat:` / `feat(scope):` → **minor** bump
- `fix:` / `perf:` / `refactor:` / `style:` / `test:` / `docs:` / `chore:` → **patch** bump
- `<type>!:` or `BREAKING CHANGE` in a commit body → **major** bump

This repo lands PRs as **merge commits** (verified: the recent merges all have
two parents), so the PR title plays no role in the bump — the branch's commit
messages do. The PR title only decides the bump for squash merges. (An earlier
version of this guide said the title drives the bump; that was wrong — see
issue #491.)

## PR Titles Still Matter

The PR title must still be conventional-commit shaped (`type[(scope)][!]: subject`):
it is what reviewers read, and it becomes the commit subject if the PR is ever
squash-merged. `.github/workflows/pr-title.yml` hard-fails malformed titles.
But the title does not drive the version bump under this repo's merge-commit flow.

## The `feat` Rule

Reserve `feat` in **commit messages** for **user-facing new features** — something
the user can see or do that they could not do before.

Design-system / UI-polish work (e.g. epic #459) that adds no user-facing feature
must use `refactor(<scope>):` or `style(<scope>):` commit subjects — **never
`feat(...):`**. Past `feat(ui):` commit subjects for shared-component extractions
(#451 HesabyarFab → v0.12.0, #452 HesabyarTabRow → v0.13.0) caused needless minor
bumps. #486 (charts, three `feat` commits) and #487 (typography, one `feat`
commit) were merged with their minor bumps intact — retitling the PRs was not
enough; the rule is enforced on commit messages from the next PRs on.

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
- the branch's **commit subjects** are scanned (`git log <base>..HEAD`,
  `Merge ` subjects skipped like `scripts/parse-commits.sh`): a `feat` subject
  bumps the MINOR version, so pass `--i-confirm-minor-bump` only after
  confirming it is a user-facing feature (without it the script asks on
  a TTY, and fails closed otherwise). The scan is fail-open: outside a git
  repo, or when the base ref cannot be resolved, it warns and continues.
- a `feat` title also triggers the confirmation (keeps the title honest with
  the branch content)
- body must contain `## Summary`, `## Changes`, `## Type of Change` and
  `## Verification & Evidence`, with the `- [x]` box matching the title type
- The API never shows the template checklist, so include the checklist in the body you pass to the script. This includes the Coverage on Touch item.

If you must use the API directly (no `gh` auth), run with `--validate-only`
first and fix every violation before creating the PR.

## CI Enforcement

`.github/workflows/pr-title.yml` checks every PR on open/edit/sync:

- malformed titles (not `type[(scope)][!]: subject`) **fail** the check
- `feat` titles get a **warning** annotation (they no longer bump the version,
  but they should stay honest with the branch content)
- `feat` **commit subjects** on the branch get a **warning** annotation:
  they bump MINOR via `scripts/parse-commits.sh` (listed via the API,
  `Merge ` subjects skipped)

The check cannot tell a real feature from a mislabeled refactor — that
judgment stays with the author. When in doubt, prefer `refactor`.

## PR Bodies

Follow `.github/pull_request_template.md`: Summary, Changes, Type of Change
(checkbox matching the title prefix), Invariants & Quality Checklist,
Verification & Evidence with raw command output.
