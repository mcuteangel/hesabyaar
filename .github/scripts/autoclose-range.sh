#!/usr/bin/env bash
#
# Resolve the commit range for the AI Issue OS auto-close scan.
#
# The workflow (.github/workflows/ai-issue-os.yml, job auto_close_resolved)
# passes the push event's `before`/`after` SHAs via PUSH_BASE / PUSH_HEAD.
# Prints a single `git log` range argument on stdout:
#   normal push   -> "<base>..<head>"
#   zero-SHA base -> "<head>"            (initial push: scan all history)
# Exits nonzero when no verifiable range can be established — the caller
# must then close nothing (fail closed):
#   unknown base    -> exit 1           (force-push/pruned: no approx window)
#   empty/zero head -> exit 1
#   unknown head    -> exit 1           (nonzero but absent from checkout)
#
# Covered by scripts/test-issue-autoclose.sh. Must run inside a git repo.
set -euo pipefail

ZERO_SHA="0000000000000000000000000000000000000000"
base="${PUSH_BASE:-}"
head_sha="${PUSH_HEAD:-}"

if [[ -z "$head_sha" || "$head_sha" == "$ZERO_SHA" ]]; then
  echo "error: no valid 'after' SHA — refusing to scan an unknown range" >&2
  exit 1
fi

if ! git cat-file -e "${head_sha}^{commit}" 2>/dev/null; then
  # An unknown nonzero 'after' would otherwise print a range with exit 0,
  # and the workflow's `git log` in process substitution can fail without
  # failing the step — making the run look successful while closing nothing.
  echo "error: PUSH_HEAD ${head_sha:0:7} not found in checkout — refusing to scan an unknown range" >&2
  exit 1
fi

if [[ "$base" == "$ZERO_SHA" ]]; then
  # Initial push of the branch: no "before" exists. Scan the whole
  # history reachable from HEAD (HEAD~1 is invalid for a root commit
  # and would silently scan nothing).
  printf '%s\n' "$head_sha"
elif ! git cat-file -e "${base}^{commit}" 2>/dev/null; then
  # Force-push (or pruned history): no exact range can be established.
  # Refuse to close anything on an approximate window — an older
  # closing reference could re-close an issue that was reopened since.
  echo "error: BASE ${base:0:7} not found in checkout — cannot establish the pushed range" >&2
  exit 1
else
  printf '%s\n' "${base}..${head_sha}"
fi
