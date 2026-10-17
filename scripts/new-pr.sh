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
#   - branch commit subjects are scanned (git log <base>..HEAD, `Merge `
#     subjects skipped, same as scripts/parse-commits.sh): a `feat` subject
#     bumps the MINOR version. Without --i-confirm-minor-bump the script asks
#     on a TTY, else fails closed. The scan is fail-open: outside a git repo
#     or when the base ref cannot be resolved it warns and continues.
#   - a `feat` title also triggers the minor-bump confirmation
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
echo "Version-bump rule (commit subjects, scripts/parse-commits.sh): 'feat:' -> minor | fix/refactor/style/perf/test/docs/chore -> patch | '!' -> major"
echo ""

# 1. Title shape: type[(scope)][!]: subject
if ! printf '%s' "$TITLE" | grep -Eq '^(feat|fix|refactor|style|perf|test|docs|chore|build|ci)(\([^)]*\))?(!)?: [^[:space:]]'; then
  fail "title must look like 'type[(scope)][!]: subject' (got: $TITLE)"
fi
TYPE=$(printf '%s' "$TITLE" | sed -E 's/^([a-z]+).*/\1/')

# 2. Minor-bump gate for feat titles (keeps the title honest; the bump itself
#    comes from the branch's commit subjects, checked in step 3)
TITLE_CONFIRMED=0
if [ "$TYPE" = "feat" ] && [ "$CONFIRM_MINOR" -eq 0 ]; then
  # <type>!: is a MAJOR bump, plain feat: is minor (scripts/parse-commits.sh)
  TITLE_BUMP="MINOR"
  case "$TITLE" in
    *'!'*) TITLE_BUMP="MAJOR" ;;
  esac
  echo "WARNING: a 'feat' title claims a $TITLE_BUMP bump; the branch's feat commit subjects are what actually bump it (scripts/parse-commits.sh)." >&2
  echo "Reserve 'feat' for user-facing new features; use refactor(...)/style(...) otherwise." >&2
  if [ -t 0 ]; then
    printf "Type 'yes' to confirm this is a user-facing feature: " >&2
    read -r answer
    [ "$answer" = "yes" ] || fail "aborted by user"
    TITLE_CONFIRMED=1
  else
    fail "refusing: re-run with --i-confirm-minor-bump once you have confirmed this is a user-facing feature"
  fi
fi

# 3. Minor-bump gate for feat commit subjects on the branch.
#    scripts/parse-commits.sh reads these subjects (skipping `Merge ` ones),
#    so they -- not the PR title -- decide the bump under merge-commit flow.
FEAT_COMMITS=""
if command -v git >/dev/null 2>&1 && git rev-parse --git-dir >/dev/null 2>&1; then
  if git rev-parse --verify --quiet "$BASE" >/dev/null 2>&1; then
    # NOTE: --pretty=format:%s emits no trailing newline, so the loop must also
    # process the final unterminated line (|| [ -n "$subject" ]).
    FEAT_COMMITS=$(git log --pretty=format:%s "$BASE..HEAD" 2>/dev/null | while IFS= read -r subject || [ -n "$subject" ]; do
      case "$subject" in
        "Merge "*) continue ;; # same skip as scripts/parse-commits.sh
        'feat('*|'feat:'*) printf '%s\n' "$subject" ;;
      esac
    done)
  else
    echo "WARNING: cannot resolve base ref '$BASE' - skipping branch commit scan" >&2
  fi
else
  echo "WARNING: not a git repo (or git not found) - skipping branch commit scan" >&2
fi
if [ -n "$FEAT_COMMITS" ] && [ "$CONFIRM_MINOR" -eq 0 ] && [ "$TITLE_CONFIRMED" -eq 0 ]; then
  echo "WARNING: feat commit subjects on this branch bump the MINOR version (scripts/parse-commits.sh):" >&2
  printf '%s\n' "$FEAT_COMMITS" | sed 's/^/  - /' >&2
  echo "Reserve 'feat' for user-facing new features; use refactor(...)/style(...) otherwise." >&2
  if [ -t 0 ]; then
    printf "Type 'yes' to confirm the minor bump: " >&2
    read -r answer
    [ "$answer" = "yes" ] || fail "aborted by user"
  else
    fail "refusing: re-run with --i-confirm-minor-bump once you have confirmed the feat commits are user-facing features"
  fi
fi

# 4. Required template sections
for section in "## Summary" "## Changes" "## Type of Change" "## Verification"; do
  case "$BODY" in
    *"$section"*) ;;
    *) fail "body is missing required template section: $section" ;;
  esac
done

# 5. Checked type box matches the title type, scoped to its own section
#    (a checked box elsewhere in the body must not satisfy the check)
type_section=$(printf '%s\n' "$BODY" | awk '/^## Type of Change/{f=1; next} /^## /{f=0} f')
if ! printf '%s\n' "$type_section" | grep -Eq "^- \[[xX]\] \`$TYPE\`"; then
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
