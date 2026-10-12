#!/bin/sh
# test-coverage-cache.sh - Regression tests for the DeepSource coverage
# cache/forward mechanism (.github/actions/coverage-cache,
# .github/workflows/coverage-forward.yml).
#
# The mechanism exists because coverage uploads live inside smart-gated
# build jobs: a skipped job leaves its DeepSource language key missing and
# the PR aggregate collapses to a partial key set ("down 45.8% from main").
# These tests pin the structural invariants that review rounds caught:
# trusted-code checkout for the secret-bearing workflow, save-before-report
# ordering, PR-then-main restore fallback, and base-pinned cache scope.
# Static YAML assertions in the style of test-workflow-gates.sh.
set -eu

HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$HERE/../.."
ACT="$ROOT/.github/actions/coverage-cache/action.yml"
FWD="$ROOT/.github/workflows/coverage-forward.yml"
ANDROID="$ROOT/.github/workflows/android-ci.yml"
RUSTLINT="$ROOT/.github/workflows/rust-lint.yml"

fail=0
check() {
  # $1 = description, $2.. = command to run (must exit 0)
  desc="$1"; shift
  if "$@" >/dev/null 2>&1; then
    echo "PASS: $desc"
  else
    echo "FAIL: $desc"
    fail=1
  fi
}

# 1. The secret-bearing forward workflow must run on pull_request_target
# (base branch's workflow file), never on pull_request.
check "forward runs on pull_request_target" \
  grep -q "pull_request_target:" "$FWD"
check "forward does not run on pull_request" \
  sh -c "! grep -q '^  pull_request:' '$FWD'"

# 2. The checkout must pin the base revision so the local actions resolve
# to trusted code; no PR-controlled code may execute with the DSN.
check "forward checks out the base SHA" \
  grep -Fq 'ref: ${{ github.event.pull_request.base.sha }}' "$FWD"

# 3. The forward job must stay ungated: docs-only syncs still re-report.
check "forward job has no needs.changes gate" \
  sh -c "! grep -q 'needs.changes' '$FWD'"

# 4. Forwarding reports against the PR head SHA (cf. #310, #295).
check "forward reports against PR head SHA" \
  grep -Fq 'github.event.pull_request.head.sha' "$FWD"

# 5. Cache restore needs actions:read on top of contents:read.
check "forward declares actions:read permission" \
  grep -q "actions: read" "$FWD"

# 6. Save must precede the DeepSource upload inside each producer job, so a
# forward restoring after the save always sees fresh data and a stale report
# can never overwrite the run's fresh report for the same SHA.
save_before_upload() {
  # $1 = workflow file, $2 = language label in the save step name
  file="$1"; lang="$2"
  save_line=$(grep -n "Save $lang coverage to cache" "$file" | head -1 | cut -d: -f1)
  upload_line=$(grep -n "Upload coverage to DeepSource" "$file" | head -1 | cut -d: -f1)
  [ -n "$save_line" ] && [ -n "$upload_line" ] && [ "$save_line" -lt "$upload_line" ]
}
check "android-ci saves kotlin cache before DeepSource upload" \
  save_before_upload "$ANDROID" "Kotlin"
check "rust-lint saves rust cache before DeepSource upload" \
  save_before_upload "$RUSTLINT" "Rust"

# 7. The nested save step must not inherit the implicit success() guard:
# callers invoke it best-effort (!cancelled()) after failures.
check "action save step keeps !cancelled()" \
  grep -Fq "inputs.mode == 'save' && !cancelled()" "$ACT"

# 8. Restore prefers this PR's entries, then falls back to main.
# Only the two restore-keys entries are bare "ds-coverage-...-" prefixes.
prefix_lines=$(grep -n '^ *ds-coverage-.*-$' "$ACT" || true)
first_prefix=$(printf '%s\n' "$prefix_lines" | head -1)
second_prefix=$(printf '%s\n' "$prefix_lines" | tail -1)
case "$first_prefix" in
  *scope*)
    case "$second_prefix" in
      *-main-*) echo "PASS: restore-keys list PR scope before main" ;;
      *) echo "FAIL: restore-keys list PR scope before main (main not second)"; fail=1 ;;
    esac
    ;;
  *) echo "FAIL: restore-keys list PR scope before main (PR scope not first)"; fail=1 ;;
esac

# 9. PR scope pins the base revision so a base change falls back to main
# instead of forwarding stale coverage.
check "cache scope pins PR base SHA" \
  grep -Fq 'github.event.pull_request.base.sha' "$ACT"

# 10. Manual dispatches from non-main branches must not pollute main's cache.
check "non-main dispatches get a write-only sha scope" \
  grep -Fq 'SCOPE="sha-${{ github.sha }}"' "$ACT"

# 11. JavaScript is deliberately NOT forwarded: the key is shared with the
# OCR workflow's report and forwarding one producer's file would clobber
# the other's. The aggregate impact is negligible (a few hundred lines).
check "forward does not handle a javascript key" \
  sh -c "! grep -qi 'javascript' '$FWD'"

if [ "$fail" -ne 0 ]; then exit 1; fi
echo "ALL COVERAGE-CACHE CHECKS PASS"
