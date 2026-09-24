#!/usr/bin/env sh
# Tests for scripts/generate-release-notes.sh (Gemini release notes & resilient fallback).
#
# POSIX-compatible: runs with bash, ash/busybox, or dash. Requires git.
# Usage: ./scripts/tests/test-generate-release-notes.sh
set -eu

HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
GENERATE="$HERE/../generate-release-notes.sh"
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

# --- Test 1: Fallback when GEMINI_API_KEY is unset ---
new_repo
git checkout -q -b feat-branch main
echo "feature" > feat.txt; git add feat.txt; git commit -qm "feat: add export feature"
echo "bugfix" > fix.txt; git add fix.txt; git commit -qm "fix: resolve sync crash"
echo "chore" > chore.txt; git add chore.txt; git commit -qm "perf: speed up query"

stdout_file="$TMP/t1_stdout.md"
stderr_file="$TMP/t1_stderr.log"

GEMINI_API_KEY="" bash "$GENERATE" "0.8.0" "main" "HEAD" > "$stdout_file" 2> "$stderr_file" || true

# Assert stdout contains Persian headers and commits
stdout_content=$(cat "$stdout_file")
stderr_content=$(cat "$stderr_file")

case "$stdout_content" in
  *"نسخه 0.8.0 حساب‌یار منتشر شد."*"### امکانات جدید"*"* feat: add export feature"*"### رفع مشکلات"*"* fix: resolve sync crash"*"### بهبودها و تغییرات"*"* perf: speed up query"*)
    echo "PASS fallback generates structured Persian notes"
    pass=$((pass + 1))
    ;;
  *)
    echo "FAIL fallback output structure invalid: $stdout_content"
    fail=$((fail + 1))
    ;;
esac

# Assert stdout does NOT contain WARNING lines
case "$stdout_content" in
  *"WARNING:"*)
    echo "FAIL stdout contains WARNING (should be in stderr): $stdout_content"
    fail=$((fail + 1))
    ;;
  *)
    echo "PASS stdout does not contain WARNING logs"
    pass=$((pass + 1))
    ;;
esac

# Assert stderr contains the warning
case "$stderr_content" in
  *"WARNING:"*)
    echo "PASS stderr contains warning"
    pass=$((pass + 1))
    ;;
  *)
    echo "FAIL stderr missing expected warning: $stderr_content"
    fail=$((fail + 1))
    ;;
esac

# --- Test 2: Resilient fallback when API call fails ---
new_repo
git checkout -q -b fail-branch main
echo "feature" > feat.txt; git add feat.txt; git commit -qm "feat: new dashboard"

t2_stdout="$TMP/t2_stdout.md"
t2_stderr="$TMP/t2_stderr.log"

# Pass a dummy key and invalid model so the API fails
GEMINI_API_KEY="invalid-key-for-test" GEMINI_MODEL="invalid-model-xyz" \
  bash "$GENERATE" "0.8.0" "main" "HEAD" > "$t2_stdout" 2> "$t2_stderr" || true

t2_out=$(cat "$t2_stdout")
t2_err=$(cat "$t2_stderr")

case "$t2_out" in
  *"نسخه 0.8.0 حساب‌یار منتشر شد."*"### امکانات جدید"*"* feat: new dashboard"*)
    echo "PASS API failure triggers resilient fallback"
    pass=$((pass + 1))
    ;;
  *)
    echo "FAIL API failure fallback invalid: $t2_out"
    fail=$((fail + 1))
    ;;
esac

case "$t2_out" in
  *"WARNING:"*)
    echo "FAIL stdout on API failure contains WARNING: $t2_out"
    fail=$((fail + 1))
    ;;
  *)
    echo "PASS stdout clean on API failure"
    pass=$((pass + 1))
    ;;
esac

# --- Test 3: Merge commits are excluded from fallback notes ---
new_repo
git checkout -q -b merge-branch main
echo "feature" > feat.txt; git add feat.txt; git commit -qm "feat: bank loan module"
git commit --allow-empty -qm "Merge pull request #100 from user/branch"

t3_stdout="$TMP/t3_stdout.md"
t3_stderr="$TMP/t3_stderr.log"

GEMINI_API_KEY="" bash "$GENERATE" "0.8.0" "main" "HEAD" > "$t3_stdout" 2> "$t3_stderr" || true
t3_out=$(cat "$t3_stdout")

case "$t3_out" in
  *"Merge pull request"*)
    echo "FAIL fallback included merge commit: $t3_out"
    fail=$((fail + 1))
    ;;
  *"* feat: bank loan module"*)
    echo "PASS merge commits excluded from fallback notes"
    pass=$((pass + 1))
    ;;
  *)
    echo "FAIL expected feat commit in fallback notes: $t3_out"
    fail=$((fail + 1))
    ;;
esac

echo ""
echo "$pass passed, $fail failed"
[ "$fail" -eq 0 ]
