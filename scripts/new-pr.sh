#!/usr/bin/env sh
# new-pr.sh - Create a pull request the Hesabyar way.
#
# The GitHub API never shows `.github/pull_request_template.md`, so agents
# creating PRs via the API skip it. This script is the gate: it prints the
# template rules, validates the title and body against the template, and only
# then creates the PR with `gh`.
#
# Usage:
#   scripts/new-pr.sh --title "refactor(ui): ..." --body-file pr-body.md \
#       [--base main] [--head my-branch] [--i-confirm-minor-bump] [--validate-only]
#   scripts/new-pr.sh --title "..." --body "..." [...]
#
# Rules enforced:
#   - title must be conventional-commit shaped: type[(scope)][!]: subject
#     (type in: feat fix refactor style perf test docs chore build ci)
#   - a `feat` title bumps the MINOR version (scripts/parse-commits.sh):
#     without --i-confirm-minor-bump the script asks on a TTY, else fails closed
#   - body must contain ## Summary, ## Changes, ## Type of Change and
#     ## Verification & Evidence, with the `- [x]` box matching the title type
#
# Exit 0 when the PR is created (or would be, with --validate-only).
set -eu

TITLE=""
BODY=""
BODY_FILE=""
BASE="main"
HEAD=""
CONFIRM_MINOR=0
VALIDATE_ONLY=0

usage() {
  cat <<'USAGE'
Usage:
  scripts/new-pr.sh --title "refactor(ui): ..." --body-file pr-body.md \
      [--base main] [--head my-branch] [--i-confirm-minor-bump] [--validate-only]
  scripts/new-pr.sh --title "..." --body "..." [...]

Validates the title and body against .github/pull_request_template.md and the
version-bump rule (scripts/parse-commits.sh), then creates the PR with `gh`.
USAGE
}

fail() {
  echo "ERROR: $1" >&2
  exit 1
}

while [ $# -gt 0 ]; do
  case "$1" in
    --title) TITLE="${2:?--title needs a value}"; shift 2 ;;
    --body) BODY="${2:?--body needs a value}"; shift 2 ;;
    --body-file) BODY_FILE="${2:?--body-file needs a value}"; shift 2 ;;
    --base) BASE="${2:?--base needs a value}"; shift 2 ;;
    --head) HEAD="${2:?--head needs a value}"; shift 2 ;;
    --i-confirm-minor-bump) CONFIRM_MINOR=1; shift ;;
    --validate-only) VALIDATE_ONLY=1; shift ;;
    -h|--help) usage; exit 0 ;;
    *) fail "unknown argument: $1" ;;
  esac
done

[ -n "$TITLE" ] || { usage >&2; fail "--title is required"; }
if [ -z "$BODY" ] && [ -z "$BODY_FILE" ]; then
  usage >&2
  fail "--body or --body-file is required"
fi
if [ -n "$BODY_FILE" ]; then
  [ -f "$BODY_FILE" ] || fail "body file not found: $BODY_FILE"
  BODY=$(cat "$BODY_FILE")
fi

echo "Template: .github/pull_request_template.md"
echo "Version-bump rule: 'feat:' -> minor | fix/refactor/style/perf/test/docs/chore -> patch | '!' -> major"
echo ""

# 1. Title shape: type[(scope)][!]: subject
if ! printf '%s' "$TITLE" | grep -Eq '^(feat|fix|refactor|style|perf|test|docs|chore|build|ci)(\([^)]*\))?(!)?: [^[:space:]]'; then
  fail "title must look like 'type[(scope)][!]: subject' (got: $TITLE)"
fi
TYPE=$(printf '%s' "$TITLE" | sed -E 's/^([a-z]+).*/\1/')

# 2. Minor-bump gate for feat titles
if [ "$TYPE" = "feat" ] && [ "$CONFIRM_MINOR" -eq 0 ]; then
  echo "WARNING: a 'feat' title bumps the MINOR version (scripts/parse-commits.sh)." >&2
  echo "Reserve 'feat' for user-facing new features; use refactor(...)/style(...) otherwise." >&2
  if [ -t 0 ]; then
    printf "Type 'yes' to confirm this is a user-facing feature: " >&2
    read -r answer
    [ "$answer" = "yes" ] || fail "aborted by user"
  else
    fail "refusing: re-run with --i-confirm-minor-bump once you have confirmed this is a user-facing feature"
  fi
fi

# 3. Required template sections
for section in "## Summary" "## Changes" "## Type of Change" "## Verification"; do
  case "$BODY" in
    *"$section"*) ;;
    *) fail "body is missing required template section: $section" ;;
  esac
done

# 4. Checked type box matches the title type
if ! printf '%s\n' "$BODY" | grep -Eq "^- \[[xX]\] \`$TYPE\`"; then
  fail "body has no checked '- [x] \`$TYPE\`' box under '## Type of Change' matching the title"
fi

if [ "$VALIDATE_ONLY" -eq 1 ]; then
  echo "OK: title and body pass the PR template checks"
  exit 0
fi

command -v gh >/dev/null 2>&1 || fail "'gh' CLI not found - install it, or validate with --validate-only and create the PR via the API"

TMPBODY=$(mktemp)
trap 'rm -f "$TMPBODY"' EXIT INT TERM
printf '%s\n' "$BODY" >"$TMPBODY"

if [ -n "$HEAD" ]; then
  gh pr create --title "$TITLE" --body-file "$TMPBODY" --base "$BASE" --head "$HEAD"
else
  gh pr create --title "$TITLE" --body-file "$TMPBODY" --base "$BASE"
fi
