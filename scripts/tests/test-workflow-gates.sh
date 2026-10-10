#!/bin/sh
# test-workflow-gates.sh - Regression tests for the merge-gate if: expressions.
#
# A typo in a gate can silently skip a required check while the `changes`
# job succeeds, so each gated job's required classes are asserted here.
# Matching is scoped to job-level if: expressions (gate_text): a token
# that only appears in a comment or a step-level if: must NOT satisfy
# the check, or the test would give false confidence about a broken gate.
set -eu

# Resolve to an absolute path: callers may run this from any directory.
HERE="$(cd "$(dirname "$0")" && pwd)"
WF="$HERE/../../.github/workflows"

fail=0

gate_text() {
  # $1 = workflow file: print each job-level (4-space indented) if:
  # expression, joining YAML folded (deeper-indented) continuation lines.
  # Tokens are matched against these expressions ONLY - never against
  # comments or step-level ifs, so a token mentioned in a comment cannot
  # mask a typo in the actual gate.
  awk '
    /^    if:/ {
      sub(/^    if:[[:space:]]*/, "")
      sub(/^>-[[:space:]]*$/, "")
      t = $0
      while ((getline nxt) > 0) {
        if (nxt ~ /^[[:space:]]*$/) continue
        if (nxt !~ /^     /) break
        sub(/^[[:space:]]+/, "", nxt)
        t = t " " nxt
      }
      print t
      t = ""
    }
  ' "$1"
}

check_gate() {
  # $1 = workflow file, $2.. = required class names
  file="$1"; shift
  gates="$(gate_text "$file")"
  for class in "$@"; do
    if ! printf '%s\n' "$gates" | grep -Fq "needs.changes.outputs.$class == 'true'"; then
      echo "FAIL: $file gate missing class '$class'"
      fail=1
    fi
  done
  # Fail-open contract: without an explicit status-check function GitHub
  # wraps job-level if: in implicit success(), which would make a failed
  # classifier job skip the gate instead of running it. (-F: fixed strings;
  # the tokens contain regex metacharacters like ( ) and .)
  for token in "needs.changes.result != 'success'" '!cancelled()'; do
    if ! printf '%s\n' "$gates" | grep -Fq "$token"; then
      echo "FAIL: $file gate missing fail-open token: $token"
      fail=1
    fi
  done
}

# The codspeed gate additionally isolates fork PRs (id-token is stripped
# on forks, so benchmarks must not run there). That guard is
# security-critical, so it gets its own assertion - scoped to the gate
# expressions like everything else.
check_fork_guard() {
  # $1 = workflow file
  if ! gate_text "$1" | grep -Fq "github.event.pull_request.head.repo.full_name == github.repository"; then
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
