#!/usr/bin/env sh
# Tests for scripts/detect-changes.sh (release gating logic).
#
# POSIX-compatible: runs with bash, ash/busybox, or dash. Requires git.
# Usage: ./scripts/tests/test-detect-changes.sh
set -eu

HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
DETECT="$HERE/../detect-changes.sh"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP"' EXIT

pass=0
fail=0

new_repo() {
  REPO="$TMP/repo"
  rm -rf "$REPO"
  mkdir -p "$REPO/rust/hesabyar-core" "$REPO/rust/uniffi-gen" "$REPO/gradle" "$REPO/app"
  cd "$REPO"
  git init -q -b main .
  git config user.email test@example.com
  git config user.name test
  printf '[workspace.package]\nversion = "0.7.2"\n' > rust/Cargo.toml
  printf '[package]\nname = "core"\nversion = "0.9.9"\ndependencies = []\n' > rust/hesabyar-core/Cargo.toml
  echo base > app/MainActivity.kt
  git add -A
  git commit -qm base
}

branch() { git checkout -q -b "$1" main; }

# check <description> <expected-exit-code>  (0 = release, 1 = skip)
check() {
  name=$1
  want=$2
  got=0
  "$DETECT" main HEAD > "$TMP/out.txt" 2>/dev/null || got=$?
  first=$(head -n 1 "$TMP/out.txt")
  if [ "$got" = "$want" ]; then
    pass=$((pass + 1))
    printf 'PASS %-42s %s\n' "$name" "$first"
  else
    fail=$((fail + 1))
    printf 'FAIL %-42s want=%s got=%s [%s]\n' "$name" "$want" "$got" "$first"
  fi
}

commit_all() {
  git add -A
  git commit -qm "scenario"
}

# 1. Dependency-only bump in a member manifest -> skip
new_repo
branch t1
echo 'anyhow = "1"' >> rust/hesabyar-core/Cargo.toml
commit_all
check "member manifest dep-only bump" 1

# 2. Manual version bump in workspace manifest -> release
new_repo
branch t2
sed 's/version = "0.7.2"/version = "0.8.0"/' rust/Cargo.toml > rust/Cargo.toml.new
mv rust/Cargo.toml.new rust/Cargo.toml
commit_all
check "workspace manifest version bump" 0

# 3. Manual version bump in a member manifest -> release
new_repo
branch t3
printf '[package]\nname = "core"\nversion = "1.0.0"\ndependencies = []\n' > rust/hesabyar-core/Cargo.toml
commit_all
check "member manifest version bump" 0

# 4. Table-form [dependencies.*] version bump is NOT a package bump -> skip
new_repo
branch t4
printf '[package]\nname = "core"\nversion = "0.9.9"\n\n[dependencies.serde]\nversion = "2"\n' > rust/hesabyar-core/Cargo.toml
commit_all
check "table-form dependency version bump" 1

# 5. Gradle catalog change only -> skip
new_repo
branch t5
printf '[versions]\njna = "5.14.0"\n' > gradle/libs.versions.toml
commit_all
check "libs.versions.toml only" 1

# 6. Cargo.lock only -> skip
new_repo
branch t6
echo 'lockfile' > rust/Cargo.lock
commit_all
check "Cargo.lock only" 1

# 7. App code mixed with manifest edit -> release
new_repo
branch t7
echo 'class NewScreen' > app/NewScreen.kt
echo 'logos = "0.14"' >> rust/Cargo.toml
commit_all
check "mixed app code + manifest" 0

# 8. Unrelated file only (no include match) -> skip
new_repo
branch t8
echo 'docs' > README.md
commit_all
check "unrelated non-app file" 1

printf '\n%d passed, %d failed\n' "$pass" "$fail"
[ "$fail" -eq 0 ]
