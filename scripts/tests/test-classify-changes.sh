#!/bin/sh
# test-classify-changes.sh - Regression tests for scripts/classify-changes.sh
set -eu

SCRIPT="$(dirname "$0")/../classify-changes.sh"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT
cd "$TMP"
git init -q -b main
git config user.email t@t.t
git config user.name t
mkdir -p app rust site .github/workflows scripts
touch app/A.kt rust/lib.rs site/index.html .github/workflows/x.yml scripts/a.sh README.md
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

check "app/A.kt" "kotlin=true"
check "app/A.kt" "code=true"
check "rust/lib.rs" "rust=true"
check "rust/lib.rs" "code=true"
check "site/index.html" "site=true"
check "site/index.html" "code=false"
check ".github/workflows/x.yml" "workflows=true"
check "scripts/a.sh" "ci_scripts=true"
check "README.md" "docs=true"
check "README.md" "code=false"

# empty base fails open (everything true)
out="$(sh "$SCRIPT" "" HEAD)"
echo "$out" | grep -q "^code=true$" || { echo "FAIL: empty base should fail open"; exit 1; }
echo "ok: empty base fails open"

echo "ALL PASS"
