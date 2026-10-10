#!/bin/sh
# test-classify-changes.sh - Regression tests for scripts/classify-changes.sh
set -eu

# Resolve to an absolute path BEFORE cd'ing into the temp repo below.
# A relative path would no longer resolve after the directory change and
# every invocation would fail.
SCRIPT="$(cd "$(dirname "$0")/.." && pwd)/classify-changes.sh"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
cd "$TMP"
git init -q -b main
git config user.email t@t.t
git config user.name t
mkdir -p app rust site .github/workflows .github/actions/ci-test scripts gradle/wrapper config/detekt
touch app/A.kt app/build.gradle.kts rust/lib.rs site/index.html \
  .github/workflows/x.yml .github/actions/ci-test/action.yml scripts/a.sh \
  README.md gradle.properties gradlew VERSION .codacy.yml .gitignore codecov.yml \
  config/detekt/detekt.yml
git add -A
git commit -qm base
BASE="$(git rev-parse HEAD)"
N=0

check() {
  # $1 = changed file, $2 = expected KEY=value line
  N=$((N + 1))
  git checkout -q -b "t$N" "$BASE"
  echo x >> "$1"
  git add -A
  git commit -qm "change $1"
  out="$(sh "$SCRIPT" "$BASE" HEAD)"
  echo "$out" | grep -q "^$2$" || { echo "FAIL: $1 expected $2, got:"; echo "$out"; exit 1; }
  echo "ok: $1 -> $2"
}

check_multi() {
  # $1,$2 = changed files, $3 = expected KEY=value line for the combined commit
  N=$((N + 1))
  git checkout -q -b "t$N" "$BASE"
  echo x >> "$1"
  echo x >> "$2"
  git add -A
  git commit -qm "change $1 $2"
  out="$(sh "$SCRIPT" "$BASE" HEAD)"
  echo "$out" | grep -q "^$3$" || { echo "FAIL: $1+$2 expected $3, got:"; echo "$out"; exit 1; }
  echo "ok: $1 + $2 -> $3"
}

check "app/A.kt" "kotlin=true"
check "app/A.kt" "code=true"
check "app/build.gradle.kts" "gradle=true"
check "app/build.gradle.kts" "code=true"
check "gradle.properties" "gradle=true"
check "gradle.properties" "code=true"
check "gradlew" "gradle=true"
check "gradlew" "code=true"
check "rust/lib.rs" "rust=true"
check "rust/lib.rs" "code=true"
check "site/index.html" "site=true"
check "site/index.html" "code=false"
check ".github/workflows/x.yml" "workflows=true"
check ".github/actions/ci-test/action.yml" "actions=true"
check ".github/actions/ci-test/action.yml" "code=false"
check "scripts/a.sh" "ci_scripts=true"
check "README.md" "docs=true"
check "README.md" "code=false"
check "VERSION" "config=true"
check "VERSION" "code=false"
check ".codacy.yml" "config=true"
check "config/detekt/detekt.yml" "config=true"
check "config/detekt/detekt.yml" "code=false"
check ".gitignore" "config=true"
check "codecov.yml" "config=true"
# negative assertions: catch over-classification that would needlessly
# trigger heavy jobs (rust-lint, CodSpeed, Super-Linter) on unrelated changes
check "app/A.kt" "rust=false"
check "app/A.kt" "workflows=false"
check "app/A.kt" "config=false"
check "scripts/a.sh" "code=false"
check "scripts/a.sh" "rust=false"
check ".github/workflows/x.yml" "code=false"
check ".github/workflows/x.yml" "config=false"
check "README.md" "workflows=false"
check_multi "app/A.kt" "rust/lib.rs" "kotlin=true"
check_multi "app/A.kt" "rust/lib.rs" "rust=true"
check_multi "app/A.kt" "rust/lib.rs" "code=true"
check_multi "README.md" "VERSION" "docs=true"
check_multi "README.md" "VERSION" "config=true"

# self-path detection
N=$((N + 1))
git checkout -q -b "t$N" "$BASE"
echo x >> ".github/workflows/x.yml"
git add -A
git commit -qm "change workflow"
out="$(sh "$SCRIPT" "$BASE" HEAD ".github/workflows/x.yml")"
echo "$out" | grep -q "^self=true$" || { echo "FAIL: expected self=true"; exit 1; }
echo "ok: self=true"
out="$(sh "$SCRIPT" "$BASE" HEAD ".github/workflows/other.yml")"
echo "$out" | grep -q "^self=false$" || { echo "FAIL: expected self=false"; exit 1; }
echo "ok: self=false"

# empty base fails open (everything true)
out="$(sh "$SCRIPT" "" HEAD)"
echo "$out" | grep -q "^code=true$" || { echo "FAIL: empty base should fail open"; exit 1; }
echo "ok: empty base fails open"

# unresolvable non-empty refs fail open (everything true)
out="$(sh "$SCRIPT" deadbeefdeadbeefdeadbeefdeadbeefdeadbeef HEAD 2>/dev/null)"
echo "$out" | grep -q "^kotlin=true$" || { echo "FAIL: bad base should fail open"; exit 1; }
echo "$out" | grep -q "^code=true$" || { echo "FAIL: bad base should fail open (code)"; exit 1; }
echo "ok: unresolvable refs fail open"

# zero-SHA base (initial push) fails open (everything true)
out="$(sh "$SCRIPT" 0000000000000000000000000000000000000000 HEAD)"
echo "$out" | grep -q "^workflows=true$" || { echo "FAIL: zero-SHA base should fail open"; exit 1; }
echo "$out" | grep -q "^code=true$" || { echo "FAIL: zero-SHA base should fail open (code)"; exit 1; }
echo "ok: zero-SHA base fails open"

# three-dot to two-dot fallback: orphan branches share no merge base, so
# the A...B diff fails and the script must fall back to A..B
git checkout -q --orphan orphan
mkdir -p rust
echo x > rust/orphan.rs
git add -A
git commit -qm orphan
ORPHAN="$(git rev-parse HEAD)"
out="$(sh "$SCRIPT" "$BASE" "$ORPHAN")"
echo "$out" | grep -q "^rust=true$" || { echo "FAIL: orphan fallback should classify rust=true, got:"; echo "$out"; exit 1; }
echo "$out" | grep -q "^kotlin=false$" || { echo "FAIL: orphan fallback should classify kotlin=false, got:"; echo "$out"; exit 1; }
echo "ok: three-dot to two-dot fallback"

# GITHUB_OUTPUT contract: KEY=value lines are appended to the file
GOUT="$(mktemp)"
GITHUB_OUTPUT="$GOUT" sh "$SCRIPT" "$BASE" "$BASE" > /dev/null
grep -q "^kotlin=false$" "$GOUT" || { echo "FAIL: GITHUB_OUTPUT missing kotlin=false"; exit 1; }
grep -q "^code=false$" "$GOUT" || { echo "FAIL: GITHUB_OUTPUT missing code=false"; exit 1; }
# 9 classes in CLASSES plus the derived `code` output = 11 lines total.
[ "$(wc -l < "$GOUT")" -eq 11 ] || { echo "FAIL: GITHUB_OUTPUT should have 11 lines (9 classes + code + self), got $(wc -l < "$GOUT")"; exit 1; }
rm -f "$GOUT"
echo "ok: GITHUB_OUTPUT contract"

echo "ALL PASS"
