#!/usr/bin/env sh
# Tests for scripts/new-pr.sh (PR template gate).
#
# POSIX-compatible: runs with bash, ash/busybox, or dash. Does not touch the
# network: every case uses --validate-only. stdin is /dev/null so the
# minor-bump gate runs in its fail-closed (non-TTY) mode.
# The suite runs outside any git repo so the branch commit scan stays
# fail-open and hermetic; the scan cases cd into their own temp git repos.
# Usage: ./scripts/tests/test-new-pr.sh
set -eu

# shellcheck disable=SC1007 # CDPATH= applies to this cd only; intentional (same idiom as other scripts/tests)
HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
NEWPR="$HERE/../new-pr.sh"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP" 2>/dev/null || true' EXIT

# Hermetic cwd: outside any git repo the branch scan warns and continues
# (fail-open), so the non-scan checks behave exactly as before.
mkdir -p "$TMP/nogit"
cd "$TMP/nogit"

pass=0
fail=0

# mkbody <checked-type> <file> [nosummary]
# shellcheck disable=SC2016 # backticks are literal sample markdown in the fixture body
mkbody() {
  {
    if [ "${3:-}" != "nosummary" ]; then
      printf '## Summary\n\nDoes things.\n\n'
    fi
    printf '## Changes\n\n- A change.\n\n'
    printf '## Type of Change\n\n- [x] `%s`: whatever\n\n' "$1"
    printf '## Verification & Evidence\n\n```text\nok\n```\n'
  } >"$2"
}

# check <name> <want-exit> [new-pr args...]
check() {
  name="$1"
  want="$2"
  shift 2
  set +e
  sh "$NEWPR" --validate-only "$@" </dev/null >/dev/null 2>&1
  got=$?
  set -e
  if [ "$got" -eq "$want" ]; then
    echo "PASS $name"
    pass=$((pass + 1))
  else
    echo "FAIL $name: want exit $want, got $got"
    fail=$((fail + 1))
  fi
}

B="$TMP/body.md"

mkbody refactor "$B"
check "valid refactor pr" 0 \
  --title "refactor(ui): migrate X to shared component" --body-file "$B"

check "invalid title shape" 1 \
  --title "add new thing" --body-file "$B"

mkbody feat "$B"
check "feat without confirmation fails closed" 1 \
  --title "feat(ui): add shiny widget" --body-file "$B"

check "feat with --i-confirm-minor-bump passes" 0 \
  --title "feat(ui): add shiny widget" --body-file "$B" --i-confirm-minor-bump

mkbody refactor "$B" nosummary
check "body missing Summary section" 1 \
  --title "refactor(ui): migrate X" --body-file "$B"

mkbody feat "$B"
check "checkbox type mismatch" 1 \
  --title "fix(ui): correct padding" --body-file "$B"

mkbody docs "$B"
check "docs title passes" 0 \
  --title "docs: update agent guide" --body-file "$B"

mkbody feat "$B"
check "breaking feat without confirmation fails" 1 \
  --title "feat!: drop old API" --body-file "$B"

mkbody chore "$B"
check "inline --body passes" 0 \
  --title "chore: refresh pins" --body "$(cat "$B")"

mkbody style "$B"
check "style title passes" 0 \
  --title "style(ui): align chart colors with theme" --body-file "$B"

# --- branch commit scan tests (temp git repos, like test-parse-commits.sh) ---

# new_scan_repo <name>: fresh git repo, main + one commit, on branch 'work'
new_scan_repo() {
  REPO="$TMP/scan-$1"
  mkdir -p "$REPO"
  cd "$REPO"
  git init -q .
  git symbolic-ref HEAD refs/heads/main
  git config user.email test@example.com
  git config user.name test
  git config commit.gpgsign false
  git config core.hooksPath /dev/null
  echo "init" > README.md
  git add -A
  git commit -qm "initial commit"
  git checkout -q -b work main
}

# check_scan <repo> <name> <want-exit> [new-pr args...]
check_scan() {
  repo="$1"
  name="$2"
  want="$3"
  shift 3
  cd "$repo"
  set +e
  sh "$NEWPR" --validate-only --base main "$@" </dev/null >/dev/null 2>&1
  got=$?
  set -e
  cd "$TMP/nogit" # back to the hermetic cwd
mkbody refactor "$B" # restore: later scan tests use refactor titles (never inside a real repo)
  if [ "$got" -eq "$want" ]; then
    echo "PASS $name"
    pass=$((pass + 1))
  else
    echo "FAIL $name: want exit $want, got $got"
    fail=$((fail + 1))
  fi
}

mkbody refactor "$B"

new_scan_repo feat
echo "x" > x.txt && git add x.txt && git commit -qm "feat: add widget"
check_scan "$REPO" "feat commit without confirmation fails closed" 1 \
  --title "refactor(ui): migrate X" --body-file "$B"

check_scan "$REPO" "feat commit with --i-confirm-minor-bump passes" 0 \
  --title "refactor(ui): migrate X" --body-file "$B" --i-confirm-minor-bump

new_scan_repo scoped-feat
echo "x" > x.txt && git add x.txt && git commit -qm "feat(charts): add bar chart"
check_scan "$REPO" "scoped feat commit without confirmation fails closed" 1 \
  --title "refactor(charts): migrate X" --body-file "$B"

new_scan_repo breaking-feat
echo "x" > x.txt && git add x.txt && git commit -qm "feat!: drop old API"
check_scan "$REPO" "breaking feat commit without confirmation fails closed" 1 \
  --title "refactor(ui): migrate X" --body-file "$B"

check_scan "$REPO" "breaking feat commit with --i-confirm-minor-bump passes" 0 \
  --title "refactor(ui): migrate X" --body-file "$B" --i-confirm-minor-bump

new_scan_repo headarg
git checkout -q -b other main
echo "z" > z.txt && git add z.txt && git commit -qm "feat: other branch feature"
git checkout -q work
check_scan "$REPO" "scan honors --head branch" 1 \
  --head other --title "refactor(ui): migrate X" --body-file "$B"
check_scan "$REPO" "scan defaults to checked-out HEAD" 0 \
  --title "refactor(ui): migrate X" --body-file "$B"
check_scan "$REPO" "unresolvable --head fails open" 0 \
  --head does-not-exist --title "refactor(ui): migrate X" --body-file "$B"

# A confirmed MINOR title must not cover a MAJOR branch scan. The title
# prompt needs a TTY, so these drive it through `script` (one "yes" per
# prompt the fixed code asks).
new_scan_repo tcover
echo "b" > b.txt && git add b.txt && git commit -qm "feat!: breaking change"
mkbody feat "$B"
if printf 'yes\n' | script -qec "sh $NEWPR --validate-only --base main --title 'feat: add x' --body-file '$B'" /dev/null >/dev/null 2>&1; then
  echo "FAIL minor title confirmation covered a major scan"; fail=$((fail + 1))
else
  echo "PASS minor title confirmation does not cover a major scan"; pass=$((pass + 1))
fi
if printf 'yes\nyes\n' | script -qec "sh $NEWPR --validate-only --base main --title 'feat: add x' --body-file '$B'" /dev/null >/dev/null 2>&1; then
  echo "PASS confirming the major scan passes"; pass=$((pass + 1))
else
  echo "FAIL confirming the major scan should pass"; fail=$((fail + 1))
fi
# No double prompt: a confirmed MINOR title covers a MINOR scan.
new_scan_repo tcover2
echo "c" > c.txt && git add c.txt && git commit -qm "feat: minor thing"
if printf 'yes\n' | script -qec "sh $NEWPR --validate-only --base main --title 'feat: add x' --body-file '$B'" /dev/null >/dev/null 2>&1; then
  echo "PASS minor title confirmation covers a minor scan (no double prompt)"; pass=$((pass + 1))
else
  echo "FAIL minor title confirmation should cover a minor scan"; fail=$((fail + 1))
fi
cd "$TMP/nogit" # back to the hermetic cwd
mkbody refactor "$B" # restore: later scan tests use refactor titles

new_scan_repo merge
git checkout -q -b side main
echo "s" > s.txt && git add s.txt && git commit -qm "chore: side work"
git checkout -q work
git merge -q --no-ff -m "Merge branch 'side' into work" side
check_scan "$REPO" "merge commits skipped by scan" 0 \
  --title "refactor(ui): migrate X" --body-file "$B"

new_scan_repo clean
echo "y" > y.txt && git add y.txt && git commit -qm "refactor(ui): migrate X"
check_scan "$REPO" "refactor commits pass scan" 0 \
  --title "refactor(ui): migrate X" --body-file "$B"

# outside a git repo the scan is fail-open (warns and continues)
check "scan fail-open outside git repo" 0 \
  --title "refactor(ui): migrate X" --body-file "$B"

# --- type-checkbox section scoping (issue #492) ---

# mkbody_wrong_section <checked-type> <file>: like mkbody, but the checked
# box lives under ## Summary instead of ## Type of Change.
# shellcheck disable=SC2016 # backticks are literal sample markdown in the fixture body
mkbody_wrong_section() {
  {
    printf '## Summary\n\n- [x] `%s`: whatever\n\n' "$1"
    printf '## Changes\n\n- A change.\n\n'
    printf '## Type of Change\n\n- [ ] `%s`: whatever\n\n' "$1"
    printf '## Verification & Evidence\n\n```text\nok\n```\n'
  } >"$2"
}

mkbody_wrong_section refactor "$B"
check "checked box outside Type of Change fails" 1 \
  --title "refactor(ui): migrate X" --body-file "$B"

mkbody refactor "$B"
check "checked box under Type of Change passes" 0 \
  --title "refactor(ui): migrate X" --body-file "$B"

echo "---"
echo "pass: $pass, fail: $fail"
[ "$fail" -eq 0 ]
