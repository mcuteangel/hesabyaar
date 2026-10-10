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
  # classifier job skip the gate instead of running it. (-F: fixed strings;
  # the tokens contain regex metacharacters like ( ) and .)
  for token in "needs.changes.result != 'success'" '!cancelled()'; do
    if ! grep -Fq "$token" "$file"; then
      echo "FAIL: $file gate missing fail-open token: $token"
      fail=1
    fi
  done
}

# The codspeed gate additionally isolates fork PRs (id-token is stripped
# on forks, so benchmarks must not run there). That guard is
# security-critical, so it gets its own assertion.
check_fork_guard() {
  # $1 = workflow file
  if ! grep -Fq "github.event.pull_request.head.repo.full_name == github.repository" "$1"; then
    echo "FAIL: $1 gate missing fork-PR isolation guard"
    fail=1
  fi
}

check_gate "$WF/android-ci.yml" code actions workflows config
check_gate "$WF/rust-lint.yml" rust actions workflows
check_gate "$WF/codspeed-rust.yml" rust actions workflows
check_fork_guard "$WF/codspeed-rust.yml"
# code/rust are in the super-linter gate so VALIDATE_GITLEAKS scans app and
# Rust changes for secrets - keep this coupling documented here too.
check_gate "$WF/super-linter.yml" actions workflows ci_scripts config code rust
check_gate "$WF/lint.yml" actions workflows ci_scripts

if [ "$fail" -ne 0 ]; then exit 1; fi
echo "ALL GATE CHECKS PASS"
