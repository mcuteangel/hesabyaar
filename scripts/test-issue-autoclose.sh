#!/usr/bin/env bash
#
# Regression tests for the issue auto-close pattern.
#
# The pattern lives in .github/workflows/ai-issue-os.yml. This script
# extracts it from the workflow file and runs it. The workflow YAML
# cannot run under unit test, and the regex must not be duplicated
# here. Extraction keeps one single source of truth.
#
# Usage: scripts/test-issue-autoclose.sh   (run from repo root or anywhere)
set -euo pipefail

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKFLOW="${REPO_ROOT}/.github/workflows/ai-issue-os.yml"

if [ ! -f "$WORKFLOW" ]; then
  echo "FAIL: workflow not found: ${WORKFLOW}" >&2
  exit 1
fi

# Extract the patterns from the workflow step.
LOOSE_CHAIN=$(sed -n "s/^.*LOOSE_CHAIN='\(.*\)'$/\1/p" "$WORKFLOW")
PER_NUM_TEMPLATE=$(sed -n "s/^.*PER_NUM_TEMPLATE='\(.*\)'$/\1/p" "$WORKFLOW")

if [ -z "$LOOSE_CHAIN" ] || [ -z "$PER_NUM_TEMPLATE" ]; then
  echo "FAIL: could not extract chain patterns from ${WORKFLOW}" >&2
  exit 1
fi

# Decide whether a commit message closes a given issue number.
# This mirrors both filters of the auto_close_resolved job in order:
# first the LOOSE_CHAIN history filter (git log --grep runs it against
# each raw message line, case-insensitive), then the per-number check
# on the newline-flattened message.
issue_closed_by() {
  local msg="$1" num="$2"
  local flat pattern
  printf '%s\n' "$msg" | grep -qiE "${LOOSE_CHAIN}" || return 1
  flat=$(printf '%s' "$msg" | tr '\n' ' ')
  printf '%s' "$flat" | grep -qE '#[0-9]+' || return 1
  pattern="${PER_NUM_TEMPLATE//\$\{NUM\}/${num}}"
  printf '%s\n' "$flat" | grep -qiE "${pattern}"
}

PASS=0
FAIL=0

check() {
  local expect="$1" num="$2" msg="$3"
  local got status
  if issue_closed_by "$msg" "$num"; then got=CLOSE; else got=SKIP; fi
  if [ "$got" = "$expect" ]; then
    status=PASS
    PASS=$((PASS + 1))
  else
    status=FAIL
    FAIL=$((FAIL + 1))
  fi
  printf '%s  expect=%-5s got=%-5s  #%-*s %s\n' \
    "$status" "$expect" "$got" 4 "$num" "$msg"
}

echo "=== Positive: plain GitHub-style closing forms (target #40) ==="
check CLOSE 40 "fix #40"
check CLOSE 40 "fixes #40"
check CLOSE 40 "fixed #40"
check CLOSE 40 "close #40"
check CLOSE 40 "closes #40"
check CLOSE 40 "closed #40"
check CLOSE 40 "resolve #40"
check CLOSE 40 "resolves #40"
check CLOSE 40 "resolved #40"
check CLOSE 40 "patch #40"
check CLOSE 40 "patches #40"
check CLOSE 40 "patched #40"

echo
echo "=== Positive: casing, position, and list forms ==="
check CLOSE 40 "Fix #40"
check CLOSE 40 "feat: add X (fixes #40)"
check CLOSE 40 "fixes #40, update docs"
check CLOSE 40 "resolves issue #40"
check CLOSE 36 "resolve issues #36, #21, #20, #19"
check CLOSE 21 "resolve issues #36, #21, #20, #19"
check CLOSE 19 "resolve issues #36, #21, #20, #19"
check SKIP 40 "resolve issues #36, #21, #20, #19"
check CLOSE 40 "resolves #12, #40"
check CLOSE 40 "fixes #12, closes #40"

echo
echo "=== Negative: verb embedded inside another word ==="
check SKIP 40 "unfixed #40"
check SKIP 40 "discloses #40"
check SKIP 40 "hotfixes #40"
check SKIP 40 "prefixes #40"

echo
echo "=== Negative: letters right after the issue number ==="
check SKIP 40 "fix #40abc"
check SKIP 40 "fixes #40x"

echo
echo "=== Negative: partial issue-number matching ==="
check SKIP 40 "fix #400"
check SKIP 400 "fix #40"

echo
echo "=== Negative: mention without a closing verb ==="
check SKIP 40 "see #40 for context"
check SKIP 40 "updated README, discussed approach in #40"
check SKIP 40 "improved logging related to #40"
check SKIP 40 "reworked module, refs #40"
check SKIP 40 "issue #40 is a duplicate of #41"
check SKIP 40 "fixes #36, see also #40"

echo
echo "=== Multi-line message bodies ==="
check CLOSE 40 "$(printf 'feat: rework parser\n\nfixes #40')"
check SKIP 40 "$(printf 'chore: cleanup\n\nsee #40')"

# --- Push-range resolution -------------------------------------------
# .github/scripts/autoclose-range.sh decides which commits the workflow
# scans for closing references. Tested here against throwaway git repos
# (the workflow YAML itself cannot run under unit test).
RANGE_SCRIPT="${REPO_ROOT}/.github/scripts/autoclose-range.sh"
if [ ! -f "$RANGE_SCRIPT" ]; then
  echo "FAIL: range script not found: ${RANGE_SCRIPT}" >&2
  exit 1
fi

echo
echo "=== Push range resolution ==="
TMPREPO="$(mktemp -d)"
trap 'rm -rf "$TMPREPO"' EXIT
git init -q -C "$TMPREPO" 2>/dev/null || git init -q "$TMPREPO"
git -C "$TMPREPO" config user.email "test@example.com"
git -C "$TMPREPO" config user.name "test"
git -C "$TMPREPO" commit -q --allow-empty -m "initial commit"
A=$(git -C "$TMPREPO" rev-parse HEAD)
git -C "$TMPREPO" commit -q --allow-empty -m "fix #40: correct the thing"
git -C "$TMPREPO" commit -q --allow-empty -m "docs: touch readme"
C=$(git -C "$TMPREPO" rev-parse HEAD)
git -C "$TMPREPO" commit -q --allow-empty -m "Fixes #41"
D=$(git -C "$TMPREPO" rev-parse HEAD)
ZERO=0000000000000000000000000000000000000000

range_for() { ( cd "$TMPREPO" && PUSH_BASE="$1" PUSH_HEAD="$2" bash "$RANGE_SCRIPT" ); }

# 1. normal push: exactly the pushed range — nothing older or newer
R=$(range_for "$A" "$C")
if [ "$R" = "${A}..${C}" ]; then
  SCAN=$(git -C "$TMPREPO" log "$R" --format='%s')
  if printf '%s\n' "$SCAN" | grep -q "fix #40" && ! printf '%s\n' "$SCAN" | grep -q "Fixes #41"; then
    PASS=$((PASS + 1)); printf 'PASS  normal push scans exactly the pushed range\n'
  else
    FAIL=$((FAIL + 1)); printf 'FAIL  normal push: wrong commits scanned\n'
  fi
else
  FAIL=$((FAIL + 1)); printf 'FAIL  normal push: expected "%s..%s", got "%s"\n' "$A" "$C" "$R"
fi

# 2. zero-SHA base (initial push): full history, including root commits
R=$(range_for "$ZERO" "$D")
if [ "$R" = "$D" ]; then
  SCAN=$(git -C "$TMPREPO" log "$R" --format='%s')
  if printf '%s\n' "$SCAN" | grep -q "fix #40" && printf '%s\n' "$SCAN" | grep -q "Fixes #41"; then
    PASS=$((PASS + 1)); printf 'PASS  zero-SHA base scans full history\n'
  else
    FAIL=$((FAIL + 1)); printf 'FAIL  zero-SHA base: history incomplete\n'
  fi
else
  FAIL=$((FAIL + 1)); printf 'FAIL  zero-SHA base: expected "%s", got "%s"\n' "$D" "$R"
fi

# 3. unknown base (force-push): fail closed, close nothing
if range_for "deadbeefdeadbeefdeadbeefdeadbeefdeadbeef" "$D" >/dev/null 2>&1; then
  FAIL=$((FAIL + 1)); printf 'FAIL  unknown base should exit nonzero\n'
else
  PASS=$((PASS + 1)); printf 'PASS  unknown base fails closed\n'
fi

# 4. empty / zero head: fail closed
if range_for "$A" "" >/dev/null 2>&1; then
  FAIL=$((FAIL + 1)); printf 'FAIL  empty head should exit nonzero\n'
else
  PASS=$((PASS + 1)); printf 'PASS  empty head fails closed\n'
fi
if range_for "$A" "$ZERO" >/dev/null 2>&1; then
  FAIL=$((FAIL + 1)); printf 'FAIL  zero head should exit nonzero\n'
else
  PASS=$((PASS + 1)); printf 'PASS  zero head fails closed\n'
fi

echo
echo "=== Result: ${PASS} passed, ${FAIL} failed ==="
[ "$FAIL" -eq 0 ]
