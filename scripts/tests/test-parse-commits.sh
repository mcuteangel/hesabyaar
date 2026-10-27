#!/usr/bin/env sh
# Tests for scripts/parse-commits.sh (conventional commit parsing & version bump type).
#
# POSIX-compatible: runs with bash, ash/busybox, or dash. Requires git.
# Usage: ./scripts/tests/test-parse-commits.sh
set -eu

# shellcheck disable=SC1007 # CDPATH= applies to this cd only; intentional (same idiom as other scripts/tests)
HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
PARSE="$HERE/../parse-commits.sh"
TMP=$(mktemp -d)
trap 'cd "$HERE" && rm -rf "$TMP" 2>/dev/null || true' EXIT

pass=0
fail=0
test_id=0

# Detect available Python interpreter
PYTHON_CMD=""
if python3 --version >/dev/null 2>&1; then
  PYTHON_CMD="python3"
elif py -3 --version >/dev/null 2>&1; then
  PYTHON_CMD="py -3"
fi

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

parse_json_field() {
  json_input="$1"
  field_name="$2"
  res=""
  if command -v jq >/dev/null 2>&1; then
    res=$(printf '%s' "$json_input" | jq -r --arg f "$field_name" '.[$f]')
  elif [ -n "$PYTHON_CMD" ]; then
    res=$(printf '%s' "$json_input" | $PYTHON_CMD -c "import sys, json; v = json.load(sys.stdin).get('$field_name'); sys.stdout.write('' if v is None else str(v))")
  else
    raw=$(printf '%s' "$json_input" | sed -n 's/.*"'"$field_name"'":"\([^"]*\)".*/\1/p')
    res=$(printf '%b' "$raw")
  fi
  printf '%s' "$res" | tr -d '\r'
}

# Runs the parser on main..HEAD; sets output and exit_status.
run_parse() {
  set +e
  output=$(bash "$PARSE" main HEAD)
  exit_status=$?
  set -e
}

check() {
  name=$1
  want_bump=$2
  want_summary_sub=$3

  run_parse

  if [ "$exit_status" -ne 0 ]; then
    echo "FAIL $name (parse-commits exited with status $exit_status)"
    fail=$((fail + 1))
    return
  fi

  got_bump=$(parse_json_field "$output" "bump_type")
  got_summary=$(parse_json_field "$output" "summary")

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

# --- Test 5: merge commits (real 2-parent merges) are excluded from summary ---
new_repo
git checkout -q -b feat-branch main
echo "work" > work.txt
git add work.txt
git commit -qm "feat: add export feature"
# Create a side branch and make a real 2-parent merge commit
git checkout -q -b side-branch main
echo "side" > side.txt
git add side.txt
git commit -qm "chore: side work"
git checkout -q feat-branch
git merge -q --no-ff -m "Merge branch 'side-branch' into feat-branch" side-branch

output=$(bash "$PARSE" main HEAD)
summary=$(parse_json_field "$output" "summary")

case "$summary" in
  *"Merge branch"*|*"Merge pull request"*)
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

# --- Test 6: bullet format preservation with exact newline assertion ---
new_repo
git checkout -q -b multi-branch main
echo "1" > 1.txt; git add 1.txt; git commit -qm "feat: first feature"
echo "2" > 2.txt; git add 2.txt; git commit -qm "fix: second fix"
output=$(bash "$PARSE" main HEAD)
summary=$(parse_json_field "$output" "summary")

expected_summary="- fix: second fix
- feat: first feature"

if [ "$summary" = "$expected_summary" ]; then
  echo "PASS multi-commit bullet list preserved with exact newlines"
  pass=$((pass + 1))
else
  echo "FAIL multi-commit bullet list not preserved: $summary"
  fail=$((fail + 1))
fi

check_core() {
  name=$1
  want_core=$2

  run_parse

  if [ "$exit_status" -ne 0 ]; then
    echo "FAIL $name (parse-commits exited with status $exit_status)"
    fail=$((fail + 1))
    return
  fi

  got_core=$(parse_json_field "$output" "core_bump_type")
  case "$got_core" in
    ""|"None") got_core="null" ;;
  esac

  if [ "$got_core" != "$want_core" ]; then
    echo "FAIL $name: expected core_bump_type='$want_core', got '$got_core'"
    fail=$((fail + 1))
    return
  fi

  echo "PASS $name (core_bump_type=$got_core)"
  pass=$((pass + 1))
}

# Base main with a core manifest at 0.7.0 (the guard compares base vs head)
setup_core_base() {
  mkdir -p rust
  printf '[workspace]\nmembers = ["hesabyar-core"]\n\n[workspace.package]\nversion = "0.7.0"\nedition = "2021"\n' > rust/Cargo.toml
  git add rust/Cargo.toml
  git commit -qm "chore: add core manifest"
}

# --- Test 7: rust feat commit yields core minor bump ---
new_repo
setup_core_base
git checkout -q -b core-feat-branch main
mkdir -p rust/hesabyar-core/src
echo "pub fn foo() {}" > rust/hesabyar-core/src/foo.rs
git add rust/hesabyar-core/src/foo.rs
git commit -qm "feat(core): add foo helper"
check_core "rust feat yields core minor" "minor"

# --- Test 8: mixed rust + kotlin fix yields core patch ---
new_repo
setup_core_base
git checkout -q -b core-fix-branch main
mkdir -p rust/hesabyar-core/src
echo "pub fn bar() {}" > rust/hesabyar-core/src/bar.rs
echo "fix" > fix.kt
git add rust/hesabyar-core/src/bar.rs fix.kt
git commit -qm "fix(core): correct bar calculation"
check_core "mixed rust fix yields core patch" "patch"

# --- Test 9: rust breaking title yields core major ---
new_repo
setup_core_base
git checkout -q -b core-break-branch main
mkdir -p rust/hesabyar-core/src
echo "pub fn baz() {}" > rust/hesabyar-core/src/baz.rs
git add rust/hesabyar-core/src/baz.rs
git commit -qm "feat(core)!: redesign core API"
check_core "rust breaking title yields core major" "major"

# --- Test 10: non-rust feat leaves core untouched (null) ---
new_repo
git checkout -q -b app-feat-branch main
echo "feature" > feature.kt
git add feature.kt
git commit -qm "feat: add app screen"
check_core "non-rust feat yields core null" "null"

# --- Test 11: hand-bumped core version in range is shipped as-is (null) ---
new_repo
setup_core_base
git checkout -q -b core-manual-branch main
mkdir -p rust/hesabyar-core/src
echo "pub fn qux() {}" > rust/hesabyar-core/src/qux.rs
printf '[workspace]\nmembers = ["hesabyar-core"]\n\n[workspace.package]\nversion = "0.8.0"\nedition = "2021"\n' > rust/Cargo.toml
git add rust/hesabyar-core/src/qux.rs rust/Cargo.toml
git commit -qm "fix(core): qux fix with manual bump"
check_core "hand-bumped core yields core null" "null"

# --- Test 12: rust breaking body yields core major ---
new_repo
setup_core_base
git checkout -q -b core-break-body-branch main
mkdir -p rust/hesabyar-core/src
echo "pub fn zed() {}" > rust/hesabyar-core/src/zed.rs
git add rust/hesabyar-core/src/zed.rs
git commit -qm "refactor(core): reorganize core models" -m "BREAKING CHANGE: drops legacy core type"
check_core "rust breaking body yields core major" "major"

# --- Test 13: rust change without any manifest leaves core untouched (null) ---
new_repo
git checkout -q -b core-nomanifest-branch main
mkdir -p rust/hesabyar-core/src
echo "pub fn solo() {}" > rust/hesabyar-core/src/solo.rs
git add rust/hesabyar-core/src/solo.rs
git commit -qm "feat(core): solo core without manifest"
check_core "rust change without manifest yields core null" "null"

# --- Test 14: newly added manifest ships as-is (null) ---
new_repo
git checkout -q -b core-newmanifest-branch main
mkdir -p rust/hesabyar-core/src
echo "pub fn fresh() {}" > rust/hesabyar-core/src/fresh.rs
printf '[workspace]\nmembers = ["hesabyar-core"]\n\n[workspace.package]\nversion = "0.7.0"\nedition = "2021"\n' > rust/Cargo.toml
git add rust/hesabyar-core/src/fresh.rs rust/Cargo.toml
git commit -qm "feat(core): fresh core with manifest"
check_core "newly added manifest yields core null" "null"

# --- Test 15: merge commit with a custom subject is skipped ---
# The base manifest must exist on main: without it the parser's
# missing-manifest guard forces core null, and the assertion passes even
# if the Rust loop stops skipping merge commits.
new_repo
mkdir -p rust/hesabyar-core/src
printf '[workspace]\nmembers = ["hesabyar-core"]\n\n[workspace.package]\nversion = "0.7.0"\nedition = "2021"\n' > rust/Cargo.toml
git add rust/Cargo.toml
git commit -qm "base manifest"
echo "base" > shared.txt
git add shared.txt
git commit -qm "base shared file"
git checkout -q -b side main
echo "side" > shared.txt
git add shared.txt
git commit -qm "side change"
git checkout -q main
echo "main" > shared.txt
git add shared.txt
git commit -qm "main change"
git checkout -q -b merger main
git merge side -m "feat: integrate side work" 2>/dev/null || true
echo "pub fn merged() {}" > rust/hesabyar-core/src/merged.rs
git add shared.txt rust/hesabyar-core/src/merged.rs
git commit -qm "feat: integrate side work"
check "merge with custom subject skipped for app" "patch" "side change"
check_core "merge with custom subject skipped for core" "null"

# --- Test 16: single-quoted manifest version still yields a core bump ---
# The manifest must exist at base: a newly added manifest ships as-is
# (see test 14), which would mask the parsing check.
new_repo
mkdir -p rust/hesabyar-core/src
printf '[workspace]\nmembers = ["hesabyar-core"]\n\n[workspace.package]\nversion = '"'"'0.7.0'"'"'\nedition = "2021"\n' > rust/Cargo.toml
git add rust/Cargo.toml
git commit -qm "base single-quoted manifest"
git checkout -q -b core-singlequote-branch main
echo "pub fn sq() {}" > rust/hesabyar-core/src/sq.rs
git add rust/hesabyar-core/src/sq.rs
git commit -qm "feat(core): single-quoted manifest version"
check_core "single-quoted manifest yields core minor" "minor"

# --- Test 17: python fallback (no jq) handles JSON null ---
new_repo
git checkout -q -b nojq-branch main
echo "x" > plain.txt
git add plain.txt
git commit -qm "feat: no rust change"
SHIM="$TMP/nojqbin"
mkdir -p "$SHIM"
for t in git bash python3 tr sed grep awk mktemp rm cat mkdir dirname wc head; do
  for dir in /usr/bin /bin; do
    if [ -x "$dir/$t" ]; then ln -s "$dir/$t" "$SHIM/$t"; break; fi
  done
done
OLD_PATH="$PATH"
PATH="$SHIM"
# Assert on parse_json_field directly: check_core maps both "" and "None"
# to null, so it passes with the old TypeError bug too (the substitution
# yields empty output either way). The discriminator is stderr: the fixed
# fallback writes empty output with no traceback.
run_parse
if [ "$exit_status" -ne 0 ]; then
  echo "FAIL python fallback: parse-commits exited with status $exit_status"
  fail=$((fail + 1))
elif command -v jq >/dev/null 2>&1; then
  echo "FAIL python fallback: jq still on PATH"
  fail=$((fail + 1))
else
  got=$(parse_json_field "$output" "core_bump_type" 2>"$TMP/pyerr.txt")
  if [ -z "$got" ] && [ ! -s "$TMP/pyerr.txt" ]; then
    echo "PASS python fallback without jq yields empty core for null"
    pass=$((pass + 1))
  else
    echo "FAIL python fallback: got '$got', stderr: $(cat "$TMP/pyerr.txt")"
    fail=$((fail + 1))
  fi
fi
PATH="$OLD_PATH"

echo ""
echo "$pass passed, $fail failed"
[ "$fail" -eq 0 ]
