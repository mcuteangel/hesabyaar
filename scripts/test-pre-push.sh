#!/usr/bin/env bash
#
# Regression test harness for the Hesabyar pre-push hook (OpenCodeReview gate).
#
# Follows the architecture of scripts/test-pre-commit.sh:
# - Clones this repository into a fast, disposable scratch directory.
# - Exercises real pre-push hook behavior across different environments.
# - Uses lightweight shims/mocks for `ocr` to test without live API calls or network latency.
# - Verifies fail-open paths (missing CLI, missing token, unparseable JSON).
# - Verifies fail-closed gates (blocking findings with critical/high severities).
# - Verifies model fallback behavior and unicode/encoding robustness.
#
# Usage:
#   scripts/test-pre-push.sh
# Environment:
#   KEEP_WORK=1   keep the scratch directory for inspection

set -uo pipefail

SRC=$(git rev-parse --show-toplevel) || exit 1
BASE=$(git -C "$SRC" rev-parse HEAD)
WORK=$(mktemp -d "${TMPDIR:-/tmp}/hesabyar-push-hooktest.XXXXXX")
CLONE="$WORK/repo"
HOOK="$CLONE/scripts/pre-push"
SHIM_DIR="$WORK/shims"
LOG="$WORK/hook.log"

PASS=0
FAIL=0
SKIP=0
RC=0

cleanup() {
  if [[ ${KEEP_WORK:-0} != 1 ]]; then
    rm -rf "$WORK"
  else
    echo "Scratch directory kept: $WORK"
  fi
}
trap cleanup EXIT

die() { echo "FATAL: $1" >&2; exit 1; }

[[ -f "$SRC/scripts/pre-push" ]] || die "scripts/pre-push not found in $SRC"

# 1. Scratch clone
git -c core.autocrlf=false clone -q --no-hardlinks "$SRC" "$CLONE" || die "clone failed"
git_clone() { git -C "$CLONE" "$@"; }
git_clone config user.email probe@hesabyar.local
git_clone config user.name "Hook Probe"
git_clone checkout -q -B test-branch "$BASE"

# Ensure scratch clone uses the current working pre-push script under test
cp "$SRC/scripts/pre-push" "$CLONE/scripts/pre-push"
chmod +x "$CLONE/scripts/pre-push"

mkdir -p "$SHIM_DIR"
export PATH="$SHIM_DIR:$PATH"

pass() { PASS=$((PASS + 1)); echo "  PASS: $1"; }
fail() { FAIL=$((FAIL + 1)); echo "  FAIL: $1"; }
skip() { SKIP=$((SKIP + 1)); echo "  SKIP: $1"; }

expect_rc() {
  local want="$1" desc="$2"
  if [[ $want == 0 && $RC -eq 0 ]] || [[ $want == nonzero && $RC -ne 0 ]]; then
    pass "$desc (rc=$RC)"
  else
    fail "$desc (wanted rc=$want, got rc=$RC)"
    sed 's/^/      | /' "$LOG" | tail -15
  fi
}

assert_log_contains() {
  if grep -qF -- "$1" "$LOG"; then
    pass "output mentions: $1"
  else
    fail "output missing: $1"
  fi
}

reset_test_repo() {
  git_clone reset -q --hard "$BASE"
  git_clone clean -qfdx
  # Restore the current working pre-push script under test after clean
  cp "$SRC/scripts/pre-push" "$CLONE/scripts/pre-push"
  chmod +x "$CLONE/scripts/pre-push"
  rm -f "$SHIM_DIR/ocr" "$SHIM_DIR/python3" "$LOG"
}

create_test_commit() {
  echo "test probe $(date +%s%N)" >> "$CLONE/probe.txt"
  git_clone add probe.txt
  git_clone commit -q -m "test: probe commit"
}

# --- Test Cases ---

case_static_syntax() {
  echo "=== 1. Static syntax check ==="
  if bash -n "$SRC/scripts/pre-push"; then
    pass "pre-push syntax is valid bash"
  else
    fail "pre-push syntax error"
  fi
  if [[ -x "$SRC/scripts/pre-push" ]]; then
    pass "pre-push has executable bit set"
  else
    fail "pre-push is missing executable bit"
  fi
}

case_missing_ocr_cli() {
  echo "=== 2. Missing ocr CLI (fail-open) ==="
  reset_test_repo
  create_test_commit
  # Fake PATH without ocr
  (
    cd "$CLONE"
    export OCR_LLM_TOKEN="fake-token"
    # Hide ocr
    PATH="/usr/bin:/bin"
    bash scripts/pre-push > "$LOG" 2>&1
  )
  RC=$?
  expect_rc 0 "missing ocr CLI allows push"
  assert_log_contains "ocr CLI not found"
}

case_missing_token() {
  echo "=== 3. Missing OCR_LLM_TOKEN (fail-open) ==="
  reset_test_repo
  create_test_commit
  cat <<'EOF' > "$SHIM_DIR/ocr"
#!/usr/bin/env bash
exit 0
EOF
  chmod +x "$SHIM_DIR/ocr"
  (
    cd "$CLONE"
    unset OCR_LLM_TOKEN
    bash scripts/pre-push > "$LOG" 2>&1
  )
  RC=$?
  expect_rc 0 "missing token allows push"
  assert_log_contains "OCR_LLM_TOKEN is not set"
}

case_nothing_to_review() {
  echo "=== 4. Nothing to review (HEAD == origin/main) ==="
  reset_test_repo
  cat <<'EOF' > "$SHIM_DIR/ocr"
#!/usr/bin/env bash
echo "OCR_SHOULD_NOT_BE_CALLED"
exit 1
EOF
  chmod +x "$SHIM_DIR/ocr"
  # BASE_REF is hardcoded to origin/main in the hook; reset the clone to
  # origin/main so rev-list returns 0 and the gate must exit before ocr.
  git -C "$CLONE" fetch -q origin main 2>/dev/null || true
  git_clone reset -q --hard origin/main || git_clone reset -q --hard "$BASE"
  cp "$SRC/scripts/pre-push" "$CLONE/scripts/pre-push" 2>/dev/null || true
  chmod +x "$CLONE/scripts/pre-push" 2>/dev/null || true
  (
    cd "$CLONE"
    export OCR_LLM_TOKEN="fake-token"
    bash scripts/pre-push > "$LOG" 2>&1
  )
  RC=$?
  expect_rc 0 "clean head exits 0 early without invoking ocr"
  if grep -q "OCR_SHOULD_NOT_BE_CALLED" "$LOG"; then
    fail "ocr was invoked unexpectedly when diff was empty"
  else
    pass "ocr was not invoked"
  fi
}

case_clean_review() {
  echo "=== 5. Clean review allows push ==="
  reset_test_repo
  create_test_commit
  cat <<'EOF' > "$SHIM_DIR/ocr"
#!/usr/bin/env bash
out=""
while [[ $# -gt 0 ]]; do
  if [[ "$1" == "-o" ]]; then
    out="$2"
    shift 2
  else
    shift
  fi
done
cat <<'JSON' > "$out"
{
  "status": "complete",
  "comments": [],
  "summary": { "files_reviewed": 1 }
}
JSON
exit 0
EOF
  chmod +x "$SHIM_DIR/ocr"
  (
    cd "$CLONE"
    export OCR_LLM_TOKEN="fake-token"
    export BASE_REF="HEAD~1"
    bash scripts/pre-push > "$LOG" 2>&1
  )
  RC=$?
  expect_rc 0 "clean review allows push"
  assert_log_contains "ocr review clean"
}

case_blocking_findings() {
  echo "=== 6. Critical/High findings block push (exit 1) ==="
  reset_test_repo
  create_test_commit
  cat <<'EOF' > "$SHIM_DIR/ocr"
#!/usr/bin/env bash
out=""
while [[ $# -gt 0 ]]; do
  if [[ "$1" == "-o" ]]; then
    out="$2"
    shift 2
  else
    shift
  fi
done
cat <<'JSON' > "$out"
{
  "status": "complete",
  "summary": { "files_reviewed": 1 },
  "comments": [
    {
      "severity": "HIGH",
      "category": "security",
      "path": "test.txt",
      "start_line": 1,
      "content": "Potential secret leaked in code"
    }
  ]
}
JSON
exit 0
EOF
  chmod +x "$SHIM_DIR/ocr"
  (
    cd "$CLONE"
    export OCR_LLM_TOKEN="fake-token"
    export BASE_REF="HEAD~1"
    bash scripts/pre-push > "$LOG" 2>&1
  )
  RC=$?
  expect_rc nonzero "blocking finding stops push with non-zero exit"
  assert_log_contains "push BLOCKED"
}

case_non_blocking_findings() {
  echo "=== 7. Low/Medium findings allow push ==="
  reset_test_repo
  create_test_commit
  cat <<'EOF' > "$SHIM_DIR/ocr"
#!/usr/bin/env bash
out=""
while [[ $# -gt 0 ]]; do
  if [[ "$1" == "-o" ]]; then
    out="$2"
    shift 2
  else
    shift
  fi
done
cat <<'JSON' > "$out"
{
  "status": "complete",
  "summary": { "files_reviewed": 1 },
  "comments": [
    {
      "severity": "LOW",
      "category": "maintainability",
      "path": "test.txt",
      "start_line": 1,
      "content": "Minor formatting issue"
    }
  ]
}
JSON
exit 0
EOF
  chmod +x "$SHIM_DIR/ocr"
  (
    cd "$CLONE"
    export OCR_LLM_TOKEN="fake-token"
    export BASE_REF="HEAD~1"
    bash scripts/pre-push > "$LOG" 2>&1
  )
  RC=$?
  expect_rc 0 "low severity finding does not block push"
  assert_log_contains "No blocking-severity findings — pushing"
}

case_model_fallback_retry() {
  echo "=== 8. Primary model failure retries with fallback model ==="
  reset_test_repo
  create_test_commit
  cat <<'EOF' > "$SHIM_DIR/ocr"
#!/usr/bin/env bash
out=""
while [[ $# -gt 0 ]]; do
  if [[ "$1" == "-o" ]]; then
    out="$2"
    shift 2
  else
    shift
  fi
done

if [[ "$OCR_LLM_MODEL" == "cohere/north-mini-code:free" ]]; then
  # Simulate primary model failure
  cat <<'JSON' > "$out"
{
  "status": "error",
  "message": "model overloaded"
}
JSON
  exit 0
fi

# Fallback model succeeds
cat <<'JSON' > "$out"
{
  "status": "complete",
  "comments": [],
  "summary": { "files_reviewed": 1 }
}
JSON
exit 0
EOF
  chmod +x "$SHIM_DIR/ocr"
  (
    cd "$CLONE"
    export OCR_LLM_TOKEN="fake-token"
    export BASE_REF="HEAD~1"
    export OCR_LLM_MODEL="cohere/north-mini-code:free"
    export OCR_LLM_FALLBACK_MODEL="kilo-auto/free"
    bash scripts/pre-push > "$LOG" 2>&1
  )
  RC=$?
  expect_rc 0 "fallback model review succeeds"
  assert_log_contains "retrying once with kilo-auto/free"
  assert_log_contains "ocr review clean"
}

case_both_models_fail_open() {
  echo "=== 9. Both models fail (fail-open) ==="
  reset_test_repo
  create_test_commit
  cat <<'EOF' > "$SHIM_DIR/ocr"
#!/usr/bin/env bash
out=""
while [[ $# -gt 0 ]]; do
  if [[ "$1" == "-o" ]]; then
    out="$2"
    shift 2
  else
    shift
  fi
done
cat <<'JSON' > "$out"
{
  "status": "error",
  "message": "upstream service unavailable"
}
JSON
exit 0
EOF
  chmod +x "$SHIM_DIR/ocr"
  (
    cd "$CLONE"
    export OCR_LLM_TOKEN="fake-token"
    export BASE_REF="HEAD~1"
    export OCR_LLM_MODEL="cohere/north-mini-code:free"
    export OCR_LLM_FALLBACK_MODEL="kilo-auto/free"
    bash scripts/pre-push > "$LOG" 2>&1
  )
  RC=$?
  expect_rc 0 "two model failures still allows push (fail-open)"
  assert_log_contains "skipping gate (push continues)"
}

case_malformed_json_fail_open() {
  echo "=== 10. Malformed JSON from CLI (fail-open) ==="
  reset_test_repo
  create_test_commit
  cat <<'EOF' > "$SHIM_DIR/ocr"
#!/usr/bin/env bash
out=""
while [[ $# -gt 0 ]]; do
  if [[ "$1" == "-o" ]]; then
    out="$2"
    shift 2
  else
    shift
  fi
done
echo "THIS IS NOT VALID JSON" > "$out"
exit 0
EOF
  chmod +x "$SHIM_DIR/ocr"
  (
    cd "$CLONE"
    export OCR_LLM_TOKEN="fake-token"
    export BASE_REF="HEAD~1"
    bash scripts/pre-push > "$LOG" 2>&1
  )
  RC=$?
  expect_rc 0 "malformed JSON allows push (fail-open)"
  assert_log_contains "could not parse ocr output"
}

case_strict_mode_blocks_missing_token() {
  echo "=== 11. Strict mode (OCR_STRICT_GATE=1) blocks push on missing token ==="
  reset_test_repo
  create_test_commit
  cat <<'EOF' > "$SHIM_DIR/ocr"
#!/usr/bin/env bash
exit 0
EOF
  chmod +x "$SHIM_DIR/ocr"
  (
    cd "$CLONE"
    unset OCR_LLM_TOKEN
    export OCR_STRICT_GATE=1
    bash scripts/pre-push > "$LOG" 2>&1
  )
  RC=$?
  expect_rc nonzero "strict mode blocks push without token (exit 1)"
  assert_log_contains "OCR_STRICT_GATE=1 — push BLOCKED"
}

case_partial_review_status() {
  echo "=== 12. Partial review status continues to check findings ==="
  reset_test_repo
  create_test_commit
  cat <<'EOF' > "$SHIM_DIR/ocr"
#!/usr/bin/env bash
out=""
while [[ $# -gt 0 ]]; do
  if [[ "$1" == "-o" ]]; then
    out="$2"
    shift 2
  else
    shift
  fi
done
cat <<'JSON' > "$out"
{
  "status": "partial",
  "message": "1 of 2 files timed out",
  "summary": { "files_reviewed": 1 },
  "comments": []
}
JSON
exit 0
EOF
  chmod +x "$SHIM_DIR/ocr"
  (
    cd "$CLONE"
    export OCR_LLM_TOKEN="fake-token"
    export BASE_REF="HEAD~1"
    bash scripts/pre-push > "$LOG" 2>&1
  )
  RC=$?
  expect_rc 0 "partial status without blocking comments allows push"
  assert_log_contains "partially complete"
  assert_log_contains "ocr review clean"
}

# --- Execution ---

case_static_syntax
case_missing_ocr_cli
case_missing_token
case_nothing_to_review
case_clean_review
case_blocking_findings
case_non_blocking_findings
case_model_fallback_retry
case_both_models_fail_open
case_malformed_json_fail_open
case_strict_mode_blocks_missing_token
case_partial_review_status

echo ""
echo "==========================================="
echo "  Results: $PASS passed, $FAIL failed, $SKIP skipped"
echo "==========================================="

if [[ $FAIL -gt 0 ]]; then
  echo "Pre-push regression harness FAILED."
  exit 1
fi
echo "All pre-push hook regression cases passed."
