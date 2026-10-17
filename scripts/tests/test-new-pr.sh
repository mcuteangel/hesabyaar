#!/usr/bin/env sh
# Tests for scripts/new-pr.sh (PR template gate).
#
# POSIX-compatible: runs with bash, ash/busybox, or dash. Does not touch the
# network: every case uses --validate-only. stdin is /dev/null so the
# minor-bump gate runs in its fail-closed (non-TTY) mode.
# Usage: ./scripts/tests/test-new-pr.sh
set -eu

HERE=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
NEWPR="$HERE/../new-pr.sh"
TMP=$(mktemp -d)
trap 'rm -rf "$TMP" 2>/dev/null || true' EXIT

pass=0
fail=0

# mkbody <checked-type> <file> [nosummary]
mkbody() {
  {
    if [ "${3:-}" != "nosummary" ]; then
      printf '## Summary\n\nDoes things.\n\n'
    fi
    printf '## Changes\n\n- A change.\n\n'
    printf '## Type of Change\n\n- [x] `%s`: whatever\n\n' "$1"
    printf '## Verification & Evidence\n\n```text\nok\n```\n'
  } >"$2"
}

# check <name> <want-exit> [new-pr args...]
check() {
  name="$1"
  want="$2"
  shift 2
  set +e
  sh "$NEWPR" --validate-only "$@" </dev/null >/dev/null 2>&1
  got=$?
  set -e
  if [ "$got" -eq "$want" ]; then
    echo "PASS $name"
    pass=$((pass + 1))
  else
    echo "FAIL $name: want exit $want, got $got"
    fail=$((fail + 1))
  fi
}

B="$TMP/body.md"

mkbody refactor "$B"
check "valid refactor pr" 0 \
  --title "refactor(ui): migrate X to shared component" --body-file "$B"

check "invalid title shape" 1 \
  --title "add new thing" --body-file "$B"

mkbody feat "$B"
check "feat without confirmation fails closed" 1 \
  --title "feat(ui): add shiny widget" --body-file "$B"

check "feat with --i-confirm-minor-bump passes" 0 \
  --title "feat(ui): add shiny widget" --body-file "$B" --i-confirm-minor-bump

mkbody refactor "$B" nosummary
check "body missing Summary section" 1 \
  --title "refactor(ui): migrate X" --body-file "$B"

mkbody feat "$B"
check "checkbox type mismatch" 1 \
  --title "fix(ui): correct padding" --body-file "$B"

mkbody docs "$B"
check "docs title passes" 0 \
  --title "docs: update agent guide" --body-file "$B"

mkbody feat "$B"
check "breaking feat without confirmation fails" 1 \
  --title "feat!: drop old API" --body-file "$B"

mkbody chore "$B"
check "inline --body passes" 0 \
  --title "chore: refresh pins" --body "$(cat "$B")"

mkbody style "$B"
check "style title passes" 0 \
  --title "style(ui): align chart colors with theme" --body-file "$B"

echo "---"
echo "pass: $pass, fail: $fail"
[ "$fail" -eq 0 ]
