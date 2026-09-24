#!/usr/bin/env sh
# Tests for scripts/parse-commits.sh (conventional commit parsing & version bump type).
#
# POSIX-compatible: runs with bash, ash/busybox, or dash. Requires git.
# Usage: ./scripts/tests/test-parse-commits.sh
set -eu

HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PARSE="$HERE/../parse-commits.sh"
TMP=$(mktemp -d)
trap 'cd "$HERE" && rm -rf "$TMP" 2>/dev/null || true' EXIT

pass=0
fail=0

test_id=0

new_repo() {
  test_id=$((test_id + 1))
  REPO="$TMP/repo-$test_id"
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
}

check() {
  name=$1
  want_bump=$2
  want_summary_sub=$3

  output=$(bash "$PARSE" main HEAD)

  # Extract bump_type and summary
  if command -v jq >/dev/null 2>&1; then
    got_bump=$(printf '%s' "$output" | jq -r '.bump_type')
    got_summary=$(printf '%s' "$output" | jq -r '.summary')
  elif python3 --version >/dev/null 2>&1; then
    got_bump=$(printf '%s' "$output" | python3 -c 'import sys, json; print(json.load(sys.stdin)["bump_type"])')
    got_summary=$(printf '%s' "$output" | python3 -c 'import sys, json; print(json.load(sys.stdin)["summary"])')
  elif py -3 --version >/dev/null 2>&1; then
    got_bump=$(printf '%s' "$output" | py -3 -c 'import sys, json; print(json.load(sys.stdin)["bump_type"])')
    got_summary=$(printf '%s' "$output" | py -3 -c 'import sys, json; print(json.load(sys.stdin)["summary"])')
  else
    echo "FAIL $name (no jq or python available to parse JSON output)"
    fail=$((fail + 1))
    return
  fi

  if [ "$got_bump" != "$want_bump" ]; then
    echo "FAIL $name: expected bump_type='$want_bump', got '$got_bump'"
    fail=$((fail + 1))
    return
  fi

  case "$got_summary" in
    *"$want_summary_sub"*)
      echo "PASS $name (bump=$got_bump)"
      pass=$((pass + 1))
      ;;
    *)
      echo "FAIL $name: expected summary to contain '$want_summary_sub', got: $got_summary"
      fail=$((fail + 1))
      ;;
  esac
}

# --- Test 1: feat commit gives minor bump and bulleted summary ---
new_repo
git checkout -q -b feat-branch main
echo "feature" > feat.txt
git add feat.txt
git commit -qm "feat: add personal loan ledger"
check "feat commit yields minor bump" "minor" "- feat: add personal loan ledger"

# --- Test 2: fix commit gives patch bump ---
new_repo
git checkout -q -b fix-branch main
echo "fix" > fix.txt
git add fix.txt
git commit -qm "fix: correct loan interest calculation"
check "fix commit yields patch bump" "patch" "- fix: correct loan interest calculation"

# --- Test 3: breaking change in title yields major bump ---
new_repo
git checkout -q -b break-branch main
echo "break" > break.txt
git add break.txt
git commit -qm "feat!: redesign database schema"
check "breaking change title yields major bump" "major" "- feat!: redesign database schema"

# --- Test 4: breaking change in body yields major bump ---
new_repo
git checkout -q -b break-body-branch main
echo "break" > break.txt
git add break.txt
git commit -qm "refactor: reorganize database models" -m "BREAKING CHANGE: drops legacy v1 table"
check "breaking change body yields major bump" "major" "- refactor: reorganize database models"

# --- Test 5: merge commits are excluded from summary ---
new_repo
git checkout -q -b feat-branch main
echo "work" > work.txt
git add work.txt
git commit -qm "feat: add export feature"
git commit --allow-empty -qm "Merge pull request #99 from user/feature"
output=$(bash "$PARSE" main HEAD)
if py -3 --version >/dev/null 2>&1; then
  summary=$(printf '%s' "$output" | py -3 -c 'import sys, json; print(json.load(sys.stdin)["summary"])')
elif command -v jq >/dev/null 2>&1; then
  summary=$(printf '%s' "$output" | jq -r '.summary')
else
  summary=$(printf '%s' "$output" | python3 -c 'import sys, json; print(json.load(sys.stdin)["summary"])')
fi
case "$summary" in
  *"Merge pull request"*)
    echo "FAIL merge commit was included in summary: $summary"
    fail=$((fail + 1))
    ;;
  *"- feat: add export feature"*)
    echo "PASS merge commits excluded from summary"
    pass=$((pass + 1))
    ;;
  *)
    echo "FAIL expected summary to contain feat commit: $summary"
    fail=$((fail + 1))
    ;;
esac

# --- Test 6: bullet format preservation (not single flattened line) ---
new_repo
git checkout -q -b multi-branch main
echo "1" > 1.txt; git add 1.txt; git commit -qm "feat: first feature"
echo "2" > 2.txt; git add 2.txt; git commit -qm "fix: second fix"
output=$(bash "$PARSE" main HEAD)
if py -3 --version >/dev/null 2>&1; then
  summary=$(printf '%s' "$output" | py -3 -c 'import sys, json; print(json.load(sys.stdin)["summary"])')
elif command -v jq >/dev/null 2>&1; then
  summary=$(printf '%s' "$output" | jq -r '.summary')
else
  summary=$(printf '%s' "$output" | python3 -c 'import sys, json; print(json.load(sys.stdin)["summary"])')
fi

# Ensure summary contains bullet on separate lines (newest commit first)
case "$summary" in
  *"- fix: second fix"*"- feat: first feature"*)
    echo "PASS multi-commit bullet list preserved"
    pass=$((pass + 1))
    ;;
  *)
    echo "FAIL multi-commit bullet list not preserved: $summary"
    fail=$((fail + 1))
    ;;
esac

echo ""
echo "$pass passed, $fail failed"
[ "$fail" -eq 0 ]
