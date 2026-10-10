#!/bin/sh
# test-workflow-gates.sh - Regression tests for the merge-gate if: expressions.
#
# A typo in a gate can silently skip a required check while the `changes`
# job succeeds, so each gated job's required classes are asserted here.
# The tokens checked (needs.changes.outputs.<class>) only appear in gate
# expressions - the changes job itself uses steps.classify.outputs.*.
set -eu

# Resolve to an absolute path: callers may run this from any directory.
HERE="$(cd "$(dirname "$0")" && pwd)"
WF="$HERE/../../.github/workflows"

fail=0
check_gate() {
  # $1 = workflow file, $2.. = required class names
  file="$1"; shift
  for class in "$@"; do
    if ! grep -q "needs\\.changes\\.outputs\\.$class == 'true'" "$file"; then
      echo "FAIL: $file gate missing class '$class'"
      fail=1
    fi
  done
  # Fail-open contract: without an explicit status-check function GitHub
  # wraps job-level if: in implicit success(), which would make a failed
  # classifier job skip the gate instead of running it.
  for token in "needs.changes.result != 'success'" '!cancelled()'; do
    if ! grep -q "$token" "$file"; then
      echo "FAIL: $file gate missing fail-open token: $token"
      fail=1
    fi
  done
}

check_gate "$WF/android-ci.yml" code actions workflows config
check_gate "$WF/rust-lint.yml" rust actions workflows
check_gate "$WF/codspeed-rust.yml" rust actions workflows
check_gate "$WF/super-linter.yml" actions workflows ci_scripts config code rust
check_gate "$WF/lint.yml" actions workflows ci_scripts

if [ "$fail" -ne 0 ]; then exit 1; fi
echo "ALL GATE CHECKS PASS"
