#!/usr/bin/env bash
#
# Regression harness for the Hesabyar pre-commit hook (Rust gate).
#
# The script clones this repository into a scratch directory. It installs the
# real hook through core.hooksPath and drives real `git commit` runs. Only the
# Kotlin gates are stubbed: a fake `gradlew` exits 0 at once, except in case U,
# where a failing stub proves a Kotlin failure blocks every Rust gate. cargo fmt
# and cargo clippy run for real against the cloned rust/ workspace.
#
# Assertion model:
#   - Failure cases assert the residual index (the commit was aborted).
#   - Success cases assert the created commit (BASE..HEAD) plus an empty
#     residual index, because a passing hook lets git create the commit.
#   - Expected worktree/index bytes are always derived from the actual base
#     files (copy + append). No expected content is rebuilt from shell
#     strings, so command-substitution newline stripping cannot corrupt a
#     comparison.
#   - Probes are inserted before the #[cfg(test)] attribute of currency.rs,
#     with end-of-line style matched to the file. This keeps them compiled,
#     avoids clippy::items_after_test_module, and gives cargo fmt zero churn.
#
# Matrix letters match the review regression matrix:
#   A normal staged Rust commit          K staged Cargo.toml + unstaged Cargo.toml
#   B partially staged Rust file         L unstaged Cargo.lock corruption
#   C staged + unrelated unstaged Rust   M cargo fmt failure
#   D staged invalid + valid worktree    N cargo clippy failure
#   E staged valid + invalid worktree    O restoration after fmt failure
#   F staged unformatted + formatted wt  P docs-only commit skips every gate
#   G staged formatted + unstaged edits  P2 Kotlin-only commit runs Kotlin gates
#   H untracked Rust file                P3 config files trigger Kotlin gates
#                                        P4 ktlintFormat auto-fix is re-staged
#                                        P5 staged Kotlin + unstaged edits survives
#                                        P6 ktlint failure restores Kotlin worktree
#                                        P7 staged Kotlin deletion passes
#                                        P8 absent staged Kotlin validated/restored
#                                        P9 detekt failure restores Kotlin worktree
#                                        P10 unstaged Kotlin mode change survives
#                                        P11 staged Kotlin symlink is rejected
#                                        P12 forced Kotlin restore failure aborts, backup kept
#                                        P13 leftover failed Kotlin backup aborts hook
#                                        Q missing rust/ directory (with Rust staged)
#   I staged Rust deletion               R unusual filename (spaces/brackets)
#   J unstaged Rust deletion             S tab in filename
#                                        T newline in filename (best effort)
#   U failing Kotlin gate blocks all Rust gates (cargo probe proves it)
#   V unstaged +x mode change survives       W +x restored after clippy failure
#   X unstaged 740/750/710 modes survive     Y exact modes restored after failure
#   Z broken stat aborts before materialization (checkout-index probe proves it)
#   E0 static check: no destructive git commands in the hook source
#   E1 static check: full-mode capture/restore wired into the hook source
#   E2 static check: mode capture precedes index materialization
#   E3 static check: Kotlin symlink rejection precedes materialization
#
# Usage:
#   scripts/test-pre-commit.sh
# Environment:
#   BASE_COMMIT  commit to test against (default: current HEAD)
#   KEEP_WORK=1  keep the scratch directory for debugging

set -uo pipefail

SRC=$(git rev-parse --show-toplevel) || exit 1
BASE=${BASE_COMMIT:-$(git -C "$SRC" rev-parse HEAD)}
WORK=$(mktemp -d "${TMPDIR:-/tmp}/hesabyar-hooktest.XXXXXX")
CLONE="$WORK/repo"
HOOKS="$WORK/hooks"
LOG="$WORK/hook.log"
EXP="$WORK/exp.bin"
WTX="$WORK/wtx.bin"
EXP_FMT="$WORK/exp_fmt.bin"

PASS=0
FAIL=0
SKIP=0
RC=0
# Commit-range base for expect_commit_exactly. Cases that create setup
# commits (beyond BASE) repoint this at their pre-probe parent commit.
DIFF_BASE=""

cleanup() {
  if [[ ${KEEP_WORK:-0} != 1 ]]; then
    rm -rf "$WORK"
  else
    echo "Scratch directory kept: $WORK"
  fi
}
trap cleanup EXIT

die() { echo "FATAL: $1" >&2; exit 1; }

command -v cargo >/dev/null 2>&1 || die "cargo not on PATH"
[[ -f "$SRC/scripts/pre-commit" ]] || die "scripts/pre-commit not found in $SRC"

echo "Base commit : $BASE"
echo "Scratch dir : $WORK"

RS="rust/hesabyar-core/src/currency.rs"
CARGO_TOML="rust/Cargo.toml"
CARGO_LOCK="rust/Cargo.lock"
CARRIER="carrier_probe.txt"

# Build the scratch clone. autocrlf is disabled so checkout is byte-exact and
# every comparison below is free of line-ending conversion noise on Windows.
git -c core.autocrlf=false clone -q --no-hardlinks "$SRC" "$CLONE" || die "clone failed"
git_clone() { git -C "$CLONE" "$@"; }
git_clone config core.autocrlf false
git_clone config user.email probe@hesabyar.local
git_clone config user.name "Hook Probe"
git_clone checkout -q -B probe "$BASE" || die "cannot check out $BASE"
[[ -f "$CLONE/$RS" ]] || die "probe target $RS missing"
[[ -f "$CLONE/$CARGO_LOCK" ]] || die "tracked Cargo.lock missing"

mkdir -p "$HOOKS"
cp "$SRC/scripts/pre-commit" "$HOOKS/pre-commit"
chmod +x "$HOOKS/pre-commit"
git_clone config core.hooksPath "$HOOKS"
export CARGO_TARGET_DIR="$WORK/target"

# Cargo probe for case U. A logging cargo shim goes first on PATH for that one
# case. A Kotlin gate failure must abort the hook before any cargo call, so the
# probe log must never appear.
CARGO_SHIM_DIR="$WORK/cargo-shim"
CARGO_PROBE_LOG="$WORK/cargo-invoked.log"
mkdir -p "$CARGO_SHIM_DIR"
printf '#!/usr/bin/env bash\necho "cargo $*" >> "%s"\nexit 0\n' "$CARGO_PROBE_LOG" \
  > "$CARGO_SHIM_DIR/cargo"
chmod +x "$CARGO_SHIM_DIR/cargo"

# Broken-stat shim for case Z. Prepended to PATH for that one case it makes
# every `stat` invocation fail, so the hook's mode capture cannot succeed.
STAT_SHIM_DIR="$WORK/stat-shim"
mkdir -p "$STAT_SHIM_DIR"
printf '#!/usr/bin/env bash\nexit 1\n' > "$STAT_SHIM_DIR/stat"
chmod +x "$STAT_SHIM_DIR/stat"

# git wrapper for case Z. It logs every invocation and then runs the real
# git binary. The mode-capture failure must abort BEFORE any worktree
# materialization, so this log must never contain checkout-index. The
# wrapper also proves it was engaged: run_hook's own `git commit` must
# appear in it.
GIT_SHIM_DIR="$WORK/git-shim"
GIT_PROBE_LOG="$WORK/git-invocations.log"
REAL_GIT=$(command -v git) || die "git not on PATH"
mkdir -p "$GIT_SHIM_DIR"
printf '#!/usr/bin/env bash\necho "git $*" >> "%s"\nexec "%s" "$@"\n' \
  "$GIT_PROBE_LOG" "$REAL_GIT" > "$GIT_SHIM_DIR/git"
chmod +x "$GIT_SHIM_DIR/git"

reset_clone() {
  git_clone reset -q --hard "$BASE" || die "reset failed"
  git_clone clean -qfdx || die "clean failed"
  printf '#!/usr/bin/env bash\nexit 0\n' > "$CLONE/gradlew"
  chmod +x "$CLONE/gradlew"
  refresh_split
  DIFF_BASE=""
}

run_hook() {
  ( cd "$CLONE" && printf 'n\n' | git commit --allow-empty -q -m "hook probe" ) \
    > "$LOG" 2>&1
  RC=$?
}

# --- assertion helpers -------------------------------------------------------

pass() { PASS=$((PASS + 1)); echo "  PASS: $1"; }
fail() { FAIL=$((FAIL + 1)); echo "  FAIL: $1"; }
skip() { SKIP=$((SKIP + 1)); echo "  SKIP: $1"; }

expect_rc() { # expect_rc <0|nonzero> <desc>
  local want="$1" desc="$2"
  if [[ $want == 0 && $RC -eq 0 ]] || [[ $want == nonzero && $RC -ne 0 ]]; then
    pass "$desc (rc=$RC)"
  else
    fail "$desc (wanted rc=$want, got rc=$RC)"
    sed 's/^/      | /' "$LOG" | tail -15
  fi
}

assert_idx_file() { # repo path, expected-bytes file
  local want_oid got_oid
  want_oid=$(git_clone hash-object --path="$1" "$2")
  got_oid=$(git_clone ls-files -s -- "$1" | awk '{print $2}')
  if [[ "$got_oid" == "$want_oid" ]]; then
    pass "index content matches candidate: $1"
  else
    fail "index content differs from candidate: $1"
  fi
}

assert_wt_file() { # repo path, expected-bytes file (byte-exact cmp)
  if cmp -s "$CLONE/$1" "$2"; then
    pass "worktree preserved byte-exact: $1"
  else
    fail "worktree content differs: $1"
  fi
}

assert_absent_wt() {
  if [[ ! -e "$CLONE/$1" ]]; then
    pass "absent in worktree as before: $1"
  else
    fail "unexpectedly present in worktree: $1"
  fi
}

assert_present_wt() {
  if [[ -e "$CLONE/$1" ]]; then
    pass "present in worktree: $1"
  else
    fail "missing from worktree: $1"
  fi
}

assert_idx_is_base() {
  local base_oid got_oid
  base_oid=$(git -C "$SRC" rev-parse -q --verify "$BASE:$1") || base_oid=""
  got_oid=$(git_clone ls-files -s -- "$1" | awk '{print $2}')
  if [[ -n "$base_oid" && "$got_oid" == "$base_oid" ]]; then
    pass "index unchanged from base: $1"
  else
    fail "index changed from base: $1 (idx=$got_oid base=$base_oid)"
  fi
}

contains_staged() {
  git_clone diff --cached -z --name-only | grep -zqx -F -- "$1"
}

expect_commit_exactly() { # probe commit holds exactly these paths vs DIFF_BASE
  local want got n=$#
  want=$(printf '%s\n' "$@" | LC_ALL=C sort)
  got=$(git_clone diff -z --name-only "${DIFF_BASE:-$BASE}..HEAD" \
    | LC_ALL=C sort -z | tr '\0' '\n')
  if [[ "$got" == "$want" ]]; then
    pass "commit contains exactly the $n intended path(s)"
  else
    fail "commit path set mismatch"
    echo "      wanted: $(echo "$want" | tr '\n' ' ')"
    echo "      got:    $(echo "$got" | tr '\n' ' ')"
  fi
}

commit_deletion_present() {
  git_clone diff -z --name-only --diff-filter=D "${DIFF_BASE:-$BASE}..HEAD" \
    | grep -zqx -F -- "$1"
}

assert_nothing_staged() {
  if [[ -z "$(git_clone diff --cached --name-only)" ]]; then
    pass "nothing left staged after the successful commit"
  else
    fail "residual staged paths after commit: $(git_clone diff --cached --name-only | tr '\n' ' ')"
  fi
}

expect_staged_exactly() { # failure path: aborted commit keeps this index set
  local want got n=$#
  want=$(printf '%s\n' "$@" | LC_ALL=C sort)
  got=$(git_clone diff --cached -z --name-only | LC_ALL=C sort -z | tr '\0' '\n')
  if [[ "$got" == "$want" ]]; then
    pass "staged set is exactly the $n intended path(s)"
  else
    fail "staged set mismatch"
    echo "      wanted: $(echo "$want" | tr '\n' ' ')"
    echo "      got:    $(echo "$got" | tr '\n' ' ')"
  fi
}

assert_log_contains() {
  if grep -qF -- "$1" "$LOG"; then
    pass "hook output mentions: $1"
  else
    fail "hook output missing: $1"
  fi
}

stage_carrier() {
  printf 'probe carrier %s\n' "$1" > "$CLONE/$CARRIER"
  git_clone add "$CARRIER"
}

stage_cargo_toml_probe() { # $1 label
  local toml_staged="$WORK/${1}_toml.bin"
  cp "$CLONE/$CARGO_TOML" "$toml_staged"
  printf '\n# %s probe\n' "$1" >> "$toml_staged"
  cp "$toml_staged" "$CLONE/$CARGO_TOML"
  git_clone add "$CARGO_TOML"
}

setup_commit_no_verify() {
  git_clone add "$1"
  git_clone commit -q --no-verify -m "setup: add $1"
}

# --- Rust probe plumbing -------------------------------------------------------
#
# currency.rs carries the probes. refresh_split splits the base file around
# its #[cfg(test)] attribute. Probes are inserted before that attribute so
# clippy::items_after_test_module can never fire. Probe end-of-line style is
# matched to the file so cargo fmt sees no churn.

RS_HEAD="$WORK/rs.head"
RS_TAIL="$WORK/rs.tail"
RS_MID="$WORK/rs.mid"

refresh_split() {
  local line
  line=$(grep -nm1 -E '^#\[cfg\(test\)\]$|^mod tests\b' "$CLONE/$RS" | cut -d: -f1)
  [[ -n "${line:-}" ]] || die "cannot locate test module in $RS"
  head -n $((line - 1)) "$CLONE/$RS" > "$RS_HEAD"
  tail -n +"$line" "$CLONE/$RS" > "$RS_TAIL"
}

rs_probe_eol_matched() { # probe text -> $RS_MID with the file's EOL style
  if grep -q $'\r' "$RS_HEAD" 2>/dev/null; then
    printf '%s' "$1" | sed 's/$/\r/' > "$RS_MID"
  else
    printf '%s' "$1" > "$RS_MID"
  fi
}

# Build the full candidate (head + probe + tail) into $EXP and mirror it into
# the clone file. Caller stages afterwards.
mk_rs_candidate() { # probe-lf-text
  rs_probe_eol_matched "$1"
  { cat "$RS_HEAD" "$RS_MID" "$RS_TAIL"; } > "$EXP"
  cp "$EXP" "$CLONE/$RS"
}

# Replace the clone file with an alternative full candidate in $WTX.
mk_rs_alt_worktree() { # probe-lf-text
  rs_probe_eol_matched "$1"
  { cat "$RS_HEAD" "$RS_MID" "$RS_TAIL"; } > "$WTX"
  cp "$WTX" "$CLONE/$RS"
}

# Append literal extra bytes to both $EXP-derived $WTX and the clone file.
add_rs_unstaged_tail() { # raw extra text with trailing newline
  cp "$EXP" "$WTX"
  printf '%s' "$1" >> "$WTX"
  printf '%s' "$1" >> "$CLONE/$RS"
}

P_A=$'pub fn hook_matrix_probe_a() -> i32 {\n    7\n}\n'
P_ALT=$'pub fn hook_matrix_probe_alt() -> i32 {\n    9\n}\n'
P_E=$'pub fn hook_matrix_probe_e() -> i32 {\n    5\n}\n'
P_F_UNF=$'pub   fn   hook_matrix_probe_f()->i32{7}\n'
P_F_STABLE=$'pub fn hook_matrix_probe_f() -> i32 {\n    7\n}\n'
P_G=$'pub fn hook_matrix_probe_g() -> i32 {\n    3\n}\n'
P_BAD_CLIPPY=$'pub fn hook_matrix_bad() -> i32 {\n    let unused_probe_value = 7;\n    3\n}\n'
P_BAD_N=$'pub fn hook_matrix_bad_n() -> i32 {\n    let unused_probe_value_n = 9;\n    4\n}\n'
P_PARSE=$'fn broken( {\n'

# --- matrix cases -------------------------------------------------------------

case_e0_static_no_destructive_ops() {
  echo "=== E0: hook source has no destructive git commands ==="
  reset_clone
  if grep -nE 'git (reset|stash|clean|checkout)([^-]|$)' "$SRC/scripts/pre-commit"; then
    fail "destructive git command found in hook source"
  else
    pass "no git reset/stash/clean/checkout in hook source (checkout-index allowed)"
  fi
}

case_a() {
  echo "=== A: normal staged Rust commit ==="
  reset_clone
  mk_rs_candidate "$P_A"
  git_clone add "$RS"
  stage_carrier a
  run_hook
  expect_rc 0 "hook passes on valid formatted staged Rust"
  assert_log_contains "cargo clippy passed"
  expect_commit_exactly "$CARRIER" "$RS"
  assert_nothing_staged
}

case_b() {
  echo "=== B: partially staged Rust file ==="
  reset_clone
  mk_rs_candidate "$P_A"
  git_clone add "$RS"
  stage_carrier b
  add_rs_unstaged_tail '// b-extra unstaged edit
'
  run_hook
  expect_rc 0 "hook passes with partial stage"
  assert_idx_file "$RS" "$EXP"
  assert_wt_file "$RS" "$WTX"
  expect_commit_exactly "$CARRIER" "$RS"
  assert_nothing_staged
}

case_c() {
  echo "=== C: staged Rust edit + unrelated unstaged Rust edit ==="
  reset_clone
  local other="rust/hesabyar-core/src/calendar.rs"
  local other_exp="$WORK/other.bin"
  mk_rs_candidate "$P_A"
  git_clone add "$RS"
  stage_carrier c
  cp "$CLONE/$other" "$other_exp"
  printf '// c-unrelated unstaged comment\n' >> "$CLONE/$other"
  printf '// c-unrelated unstaged comment\n' >> "$other_exp"
  run_hook
  expect_rc 0 "hook passes"
  assert_idx_file "$RS" "$EXP"
  assert_idx_is_base "$other"
  assert_wt_file "$other" "$other_exp"
  expect_commit_exactly "$CARRIER" "$RS"
  assert_nothing_staged
}

case_d() {
  echo "=== D: staged Clippy-invalid + valid unstaged worktree ==="
  reset_clone
  mk_rs_candidate "$P_BAD_CLIPPY"
  git_clone add "$RS"
  mk_rs_alt_worktree "$P_ALT"
  stage_carrier d
  run_hook
  expect_rc nonzero "clippy sees staged (invalid) content, not worktree"
  assert_log_contains "cargo clippy failed"
  assert_idx_file "$RS" "$EXP"
  assert_wt_file "$RS" "$WTX"
  expect_staged_exactly "$CARRIER" "$RS"
}

case_e() {
  echo "=== E: staged valid + Clippy-invalid unstaged worktree ==="
  reset_clone
  mk_rs_candidate "$P_E"
  git_clone add "$RS"
  stage_carrier e
  add_rs_unstaged_tail "$P_BAD_CLIPPY"
  run_hook
  expect_rc 0 "unstaged lint violation does not block the commit"
  assert_idx_file "$RS" "$EXP"
  assert_wt_file "$RS" "$WTX"
  expect_commit_exactly "$CARRIER" "$RS"
  assert_nothing_staged
}

case_f() {
  echo "=== F: staged unformatted + pre-formatted worktree ==="
  reset_clone
  mk_rs_candidate "$P_F_UNF"
  git_clone add "$RS"
  stage_carrier f
  mk_rs_alt_worktree "$P_F_STABLE"
  printf '// f-worktree extra comment\n' >> "$WTX"
  printf '// f-worktree extra comment\n' >> "$CLONE/$RS"
  run_hook
  expect_rc 0 "fmt normalizes the staged content"
  rs_probe_eol_matched "$P_F_STABLE"
  { cat "$RS_HEAD" "$RS_MID" "$RS_TAIL"; } > "$EXP_FMT"
  assert_idx_file "$RS" "$EXP_FMT"
  assert_wt_file "$RS" "$WTX"
  expect_commit_exactly "$CARRIER" "$RS"
  assert_nothing_staged
}

case_g() {
  echo "=== G: staged formatted + additional unstaged same-file edits ==="
  reset_clone
  mk_rs_candidate "$P_G"
  git_clone add "$RS"
  stage_carrier g
  add_rs_unstaged_tail '// g-unstaged comment
'
  run_hook
  expect_rc 0 "hook passes; no formatting delta so no re-stage"
  assert_idx_file "$RS" "$EXP"
  assert_wt_file "$RS" "$WTX"
  expect_commit_exactly "$CARRIER" "$RS"
  assert_nothing_staged
}

case_h() {
  echo "=== H: untracked Rust file survives and stays unstaged ==="
  reset_clone
  local u="rust/hesabyar-core/src/orphan_u.rs"
  printf '%s' "$P_A" > "$CLONE/$u"
  stage_cargo_toml_probe h
  stage_carrier h
  run_hook
  expect_rc 0 "hook passes with untracked orphan module present"
  assert_log_contains "No staged Rust sources to format"
  assert_log_contains "cargo clippy passed"
  assert_present_wt "$u"
  if contains_staged "$u"; then
    fail "untracked file became staged: $u"
  else
    pass "untracked file stayed unstaged"
  fi
  expect_commit_exactly "$CARRIER" "$CARGO_TOML"
  assert_nothing_staged
}

case_i() {
  echo "=== I: staged Rust deletion reaches clippy without breaking fmt ==="
  reset_clone
  local o="rust/hesabyar-core/src/orphan_i.rs"
  printf '%s' "$P_A" > "$CLONE/$o"
  setup_commit_no_verify "$o"
  DIFF_BASE=$(git_clone rev-parse HEAD)
  git_clone rm -q "$o"
  stage_carrier i
  run_hook
  expect_rc 0 "staged deletion excluded from cargo fmt path list"
  assert_log_contains "cargo clippy passed"
  if grep -qF "does not exist" "$LOG"; then
    fail "rustfmt saw the deleted path"
  else
    pass "rustfmt never received the deleted path"
  fi
  if commit_deletion_present "$o"; then
    pass "deletion recorded in the commit"
  else
    fail "deletion missing from the commit"
  fi
  assert_absent_wt "$o"
  expect_commit_exactly "$CARRIER" "$o"
  assert_nothing_staged
}

case_j() {
  echo "=== J: unstaged deletion of tracked Rust file ==="
  reset_clone
  rm "$CLONE/$RS"
  stage_cargo_toml_probe j
  stage_carrier j
  run_hook
  expect_rc 0 "absent dirty file handled without cp failure"
  assert_idx_is_base "$RS"
  assert_absent_wt "$RS"
  expect_commit_exactly "$CARRIER" "$CARGO_TOML"
  assert_nothing_staged
}

case_k() {
  echo "=== K: staged Cargo.toml + broken unstaged Cargo.toml ==="
  reset_clone
  cp "$CLONE/$CARGO_TOML" "$EXP"
  printf '# k-probe staged\n' >> "$EXP"
  cp "$EXP" "$WTX"
  printf 'broken-k-probe = = =\n' >> "$WTX"
  cp "$EXP" "$CLONE/$CARGO_TOML"
  git_clone add "$CARGO_TOML"
  cp "$WTX" "$CLONE/$CARGO_TOML"
  stage_carrier k
  run_hook
  expect_rc 0 "clippy parsed the index manifest, not the broken worktree copy"
  assert_idx_file "$CARGO_TOML" "$EXP"
  assert_wt_file "$CARGO_TOML" "$WTX"
  expect_commit_exactly "$CARRIER" "$CARGO_TOML"
  assert_nothing_staged
}

case_l() {
  echo "=== L: corrupted unstaged Cargo.lock ==="
  reset_clone
  cp "$CLONE/$CARGO_LOCK" "$WTX"
  printf '[[package]]\nname = "probe-lock-corruption"\n' >> "$WTX"
  cp "$WTX" "$CLONE/$CARGO_LOCK"
  stage_cargo_toml_probe l
  stage_carrier l
  run_hook
  expect_rc 0 "lockfile materialized from the index for validation"
  assert_idx_is_base "$CARGO_LOCK"
  assert_wt_file "$CARGO_LOCK" "$WTX"
  expect_commit_exactly "$CARRIER" "$CARGO_TOML"
  assert_nothing_staged
}

case_m() {
  echo "=== M: cargo fmt fails on staged syntax error ==="
  reset_clone
  mk_rs_candidate "$P_PARSE"
  git_clone add "$RS"
  stage_carrier m
  add_rs_unstaged_tail '// m-extra unstaged comment
'
  run_hook
  expect_rc nonzero "parse error in staged content fails cargo fmt"
  assert_log_contains "cargo fmt failed"
  assert_idx_file "$RS" "$EXP"
  assert_wt_file "$RS" "$WTX"
  expect_staged_exactly "$CARRIER" "$RS"
}

case_n() {
  echo "=== N: cargo clippy fails on staged lint ==="
  reset_clone
  mk_rs_candidate "$P_BAD_N"
  git_clone add "$RS"
  stage_carrier n
  run_hook
  expect_rc nonzero "-D warnings denies the staged lint"
  assert_log_contains "cargo clippy failed"
  assert_idx_file "$RS" "$EXP"
  expect_staged_exactly "$CARRIER" "$RS"
}

case_o() {
  echo "=== O: full restoration after fmt failure (multi-input + untracked) ==="
  reset_clone
  mk_rs_candidate "$P_PARSE"
  git_clone add "$RS"
  add_rs_unstaged_tail '// o-extra unstaged
'
  local toml_exp="$WORK/o_toml_idx.bin" toml_wt="$WORK/o_toml_wt.bin"
  cp "$CLONE/$CARGO_TOML" "$toml_exp"
  printf '# o-probe staged\n' >> "$toml_exp"
  cp "$toml_exp" "$toml_wt"
  printf 'broken-o-probe = = =\n' >> "$toml_wt"
  cp "$toml_exp" "$CLONE/$CARGO_TOML"
  git_clone add "$CARGO_TOML"
  cp "$toml_wt" "$CLONE/$CARGO_TOML"
  local u="rust/hesabyar-core/src/orphan_o.rs"
  printf '%s' "$P_A" > "$CLONE/$u"
  stage_carrier o
  run_hook
  expect_rc nonzero "fmt failure aborts the commit"
  assert_wt_file "$RS" "$WTX"
  assert_wt_file "$CARGO_TOML" "$toml_wt"
  assert_idx_file "$CARGO_TOML" "$toml_exp"
  assert_present_wt "$u"
  expect_staged_exactly "$CARRIER" "$RS" "$CARGO_TOML"
}

case_p() {
  echo "=== P: docs-only commit skips every quality gate ==="
  reset_clone
  stage_carrier p
  run_hook
  expect_rc 0 "docs-only commit passes"
  assert_log_contains "No staged Kotlin sources — skipping ktlint and detekt"
  assert_log_contains "No staged Rust sources — skipping cargo fmt and clippy"
  if grep -qF "cargo clippy passed" "$LOG"; then
    fail "clippy ran on a docs-only commit"
  else
    pass "clippy skipped on a docs-only commit"
  fi
  if grep -qF "[1/5]" "$LOG"; then
    fail "ktlint ran on a docs-only commit"
  else
    pass "ktlint skipped on a docs-only commit"
  fi
  local dirty_rust
  dirty_rust=$(git_clone status --porcelain -- rust/)
  if [[ -z "$dirty_rust" ]]; then
    pass "rust/ untouched"
  else
    fail "rust/ modified by docs-only run: $dirty_rust"
  fi
  expect_commit_exactly "$CARRIER"
  assert_nothing_staged
}

case_p2() {
  echo "=== P2: Kotlin-only commit runs Kotlin gates and skips Rust gates ==="
  reset_clone
  local kt="app/src/main/java/io/github/mojri/hesabyar/CarrierP.kt"
  printf '// p probe\n' > "$CLONE/$kt"
  git_clone add "$kt"
  stage_carrier p_kt
  run_hook
  expect_rc 0 "Kotlin-only commit passes"
  assert_log_contains "[1/5] Running ktlintFormat"
  assert_log_contains "[2/5] Running ktlintCheck"
  assert_log_contains "[3/5] Running detekt"
  assert_log_contains "No staged Rust sources — skipping cargo fmt and clippy"
  if grep -qF "cargo clippy passed" "$LOG"; then
    fail "clippy ran on a Kotlin-only commit"
  else
    pass "clippy skipped on a Kotlin-only commit"
  fi
  local dirty_rust
  dirty_rust=$(git_clone status --porcelain -- rust/)
  if [[ -z "$dirty_rust" ]]; then
    pass "rust/ untouched"
  else
    fail "rust/ modified by Kotlin-only run: $dirty_rust"
  fi
  expect_commit_exactly "$CARRIER" "$kt"
  assert_nothing_staged
}

case_p3() {
  echo "=== P3: build and linter configuration changes trigger Kotlin gates ==="
  local cfg_files=(
    "gradle/libs.versions.toml"
    ".editorconfig"
    "config/detekt/detekt.yml"
    "gradle/wrapper/gradle-wrapper.properties"
    "feature-module/gradle/wrapper/gradle-wrapper.properties"
    "subproject/config/detekt/detekt.yml"
    "subproject/gradle.properties"
    "subproject/gradle/libs.versions.toml"
    "subproject/.editorconfig"
  )
  local cfg
  for cfg in "${cfg_files[@]}"; do
    reset_clone
    mkdir -p "$CLONE/$(dirname "$cfg")"
    printf '\n# p3 probe\n' >> "$CLONE/$cfg"
    git_clone add "$cfg"
    stage_carrier "p3"
    run_hook
    expect_rc 0 "config commit for $cfg passes Kotlin gates"
    assert_log_contains "[1/5] Running ktlintFormat"
    assert_log_contains "[2/5] Running ktlintCheck"
    assert_log_contains "[3/5] Running detekt"
    assert_log_contains "No staged Rust sources — skipping cargo fmt and clippy"
    if grep -qF "cargo clippy passed" "$LOG"; then
      fail "clippy ran on a config-only commit: $cfg"
    else
      pass "clippy skipped on a config-only commit: $cfg"
    fi
    expect_commit_exactly "$CARRIER" "$cfg"
    assert_nothing_staged
  done
}

case_p4() {
  echo "=== P4: ktlintFormat auto-formatting is detected and re-staged ==="
  reset_clone
  cat << 'EOF' > "$CLONE/gradlew"
#!/usr/bin/env bash
if [ "$1" = "ktlintFormat" ]; then
  target="app/src/main/java/io/github/mojri/hesabyar/CarrierP4.kt"
  if [ -f "$target" ]; then
    printf '// auto-formatted\n' >> "$target"
  fi
fi
exit 0
EOF
  chmod +x "$CLONE/gradlew"
  local kt="app/src/main/java/io/github/mojri/hesabyar/CarrierP4.kt"
  printf '// initial unformatted\n' > "$CLONE/$kt"
  git_clone add "$kt"
  stage_carrier p4
  run_hook
  expect_rc 0 "hook passes and re-stages ktlintFormat changes"
  assert_log_contains "Re-staging 1 file(s) with auto-fixes..."
  expect_commit_exactly "$CARRIER" "$kt"
  if git_clone show "HEAD:$kt" | grep -qF "// auto-formatted"; then
    pass "re-staged commit candidate includes auto-formatted delta"
  else
    fail "re-staged commit candidate missing auto-formatted delta"
  fi
  assert_nothing_staged
}

case_p5() {
  echo "=== P5: staged Kotlin file with unstaged edits survives the hook ==="
  reset_clone
  local kt="app/src/main/java/io/github/mojri/hesabyar/CarrierP5.kt"
  local exp="$WORK/p5_staged.bin"
  local wtx="$WORK/p5_wt.bin"
  printf '// p5 staged content\n' > "$exp"
  cp "$exp" "$CLONE/$kt"
  git_clone add "$kt"
  stage_carrier p5
  # Append unstaged edits to the same file in the worktree
  cp "$CLONE/$kt" "$wtx"
  printf '// p5 unstaged edit\n' >> "$wtx"
  cp "$wtx" "$CLONE/$kt"
  run_hook
  expect_rc 0 "hook passes with partially staged Kotlin file"
  assert_idx_file "$kt" "$exp"
  assert_wt_file "$kt" "$wtx"
  expect_commit_exactly "$CARRIER" "$kt"
  assert_nothing_staged
}

case_p6() {
  echo "=== P6: ktlintCheck failure still restores Kotlin worktree ==="
  reset_clone
  cat << 'EOF' > "$CLONE/gradlew"
#!/usr/bin/env bash
if [ "$1" = "ktlintCheck" ]; then
  exit 1
fi
exit 0
EOF
  chmod +x "$CLONE/gradlew"
  local kt="app/src/main/java/io/github/mojri/hesabyar/CarrierP6.kt"
  local exp="$WORK/p6_staged.bin"
  local wtx="$WORK/p6_wt.bin"
  printf '// p6 staged content\n' > "$exp"
  cp "$exp" "$CLONE/$kt"
  git_clone add "$kt"
  stage_carrier p6
  cp "$CLONE/$kt" "$wtx"
  printf '// p6 unstaged edit\n' >> "$wtx"
  cp "$wtx" "$CLONE/$kt"
  run_hook
  expect_rc nonzero "hook fails when ktlintCheck fails"
  assert_log_contains "ktlintCheck failed"
  assert_wt_file "$kt" "$wtx"
  assert_idx_file "$kt" "$exp"
  expect_staged_exactly "$CARRIER" "$kt"
}

case_p7() {
  echo "=== P7: staged Kotlin deletion passes and stays deleted ==="
  reset_clone
  local kt="app/src/main/java/io/github/mojri/hesabyar/CarrierP7.kt"
  printf '// p7 doomed content\n' > "$CLONE/$kt"
  setup_commit_no_verify "$kt"
  DIFF_BASE=$(git_clone rev-parse HEAD)
  git_clone rm -q "$kt"
  stage_carrier p7
  run_hook
  expect_rc 0 "staged Kotlin deletion passes Kotlin gates"
  assert_log_contains "[1/5] Running ktlintFormat"
  if commit_deletion_present "$kt"; then
    pass "Kotlin deletion recorded in the commit"
  else
    fail "Kotlin deletion missing from the commit"
  fi
  assert_absent_wt "$kt"
  expect_commit_exactly "$CARRIER" "$kt"
  assert_nothing_staged
}

case_p8() {
  echo "=== P8: absent staged Kotlin file is validated then restored absent ==="
  reset_clone
  local kt="app/src/main/java/io/github/mojri/hesabyar/CarrierP8.kt"
  local exp="$WORK/p8_staged.bin"
  printf '// p8 staged content\n' > "$exp"
  cp "$exp" "$CLONE/$kt"
  git_clone add "$kt"
  stage_carrier p8
  # Delete from the worktree without staging: the hook must materialize the
  # index version for validation and delete it again on restore.
  rm "$CLONE/$kt"
  run_hook
  expect_rc 0 "hook passes with an absent staged Kotlin file"
  assert_idx_file "$kt" "$exp"
  assert_absent_wt "$kt"
  expect_commit_exactly "$CARRIER" "$kt"
  assert_nothing_staged
}

case_p9() {
  echo "=== P9: detekt failure still restores Kotlin worktree ==="
  reset_clone
  cat << 'EOF' > "$CLONE/gradlew"
#!/usr/bin/env bash
if [ "$1" = "detekt" ]; then
  exit 1
fi
exit 0
EOF
  chmod +x "$CLONE/gradlew"
  local kt="app/src/main/java/io/github/mojri/hesabyar/CarrierP9.kt"
  local exp="$WORK/p9_staged.bin"
  local wtx="$WORK/p9_wt.bin"
  printf '// p9 staged content\n' > "$exp"
  cp "$exp" "$CLONE/$kt"
  git_clone add "$kt"
  stage_carrier p9
  cp "$CLONE/$kt" "$wtx"
  printf '// p9 unstaged edit\n' >> "$wtx"
  cp "$wtx" "$CLONE/$kt"
  run_hook
  expect_rc nonzero "hook fails when detekt fails"
  assert_log_contains "detekt failed"
  assert_wt_file "$kt" "$wtx"
  assert_idx_file "$kt" "$exp"
  expect_staged_exactly "$CARRIER" "$kt"
}

case_p10() {
  echo "=== P10: unstaged Kotlin mode change survives materialization ==="
  host_represents_chmod || { skip "P10: host cannot represent chmod on .kt files"; return 0; }
  git_clone config core.fileMode true
  reset_clone
  local kt="app/src/main/java/io/github/mojri/hesabyar/CarrierP10.kt"
  local exp="$WORK/p10_staged.bin"
  local wtx="$WORK/p10_wt.bin"
  printf '// p10 staged content\n' > "$exp"
  cp "$exp" "$CLONE/$kt"
  git_clone add "$kt"
  stage_carrier p10
  chmod +x "$CLONE/$kt"
  cp "$exp" "$wtx"
  run_hook
  expect_rc 0 "hook passes with an unstaged Kotlin mode change present"
  assert_idx_file "$kt" "$exp"
  assert_wt_file "$kt" "$wtx"
  if [[ -x "$CLONE/$kt" ]]; then
    pass "original Kotlin worktree mode (+x) restored"
  else
    fail "Kotlin worktree executable bit lost by materialization"
  fi
  local idx_mode
  idx_mode=$(git_clone ls-files -s -- "$kt" | awk '{print $1}')
  if [[ "$idx_mode" = "100644" ]]; then
    pass "index mode untouched (100644)"
  else
    fail "index mode changed: $idx_mode"
  fi
  expect_commit_exactly "$CARRIER" "$kt"
  assert_nothing_staged
}

case_p11() {
  echo "=== P11: staged Kotlin symlink is rejected before materialization ==="
  reset_clone
  local kt="app/src/main/java/io/github/mojri/hesabyar/CarrierP11.kt"
  local target="app/src/main/java/io/github/mojri/hesabyar/CarrierP11Target.kt"
  printf '// p11 target\n' > "$CLONE/$target"
  if ! host_supports_symlink; then
    skip "P11: host cannot create symbolic links"
    return 0
  fi
  ( cd "$CLONE" && ln -s "CarrierP11Target.kt" "$kt" )
  git_clone add "$kt" "$target"
  stage_carrier p11
  run_hook
  expect_rc nonzero "hook rejects staged Kotlin symbolic link"
  assert_log_contains "is a symbolic link"
  assert_log_contains "must not write through a symlink"
  if [[ -L "$CLONE/$kt" ]]; then
    pass "worktree symlink preserved (not overwritten)"
  else
    fail "worktree symlink was modified or removed"
  fi
  expect_staged_exactly "$CARRIER" "$kt" "$target"
}

case_p12() {
  echo "=== P12: Kotlin restoration failure aborts commit and retains backup ==="
  reset_clone
  local pkg="app/src/main/java/io/github/mojri/hesabyar/p12pkg"
  local kt="$pkg/CarrierP12.kt"
  mkdir -p "$CLONE/$pkg"
  local exp="$WORK/p12_staged.bin"
  local wtx="$WORK/p12_wt.bin"
  printf '// p12 staged content\n' > "$exp"
  cp "$exp" "$CLONE/$kt"
  git_clone add "$kt"
  stage_carrier p12
  cp "$exp" "$wtx"
  printf '// p12 unstaged edit\n' >> "$wtx"
  cp "$wtx" "$CLONE/$kt"

  # Fake gradlew replaces the parent folder with a regular file during ktlintFormat.
  # This causes restore_kt_worktree's `mkdir -p $(dirname "$f")` to fail when
  # restoring the original worktree state after the quality gates pass.
  cat << 'EOF' > "$CLONE/gradlew"
#!/usr/bin/env bash
pkg="app/src/main/java/io/github/mojri/hesabyar/p12pkg"
if [ -d "$pkg" ]; then
  rm -rf "$pkg"
  touch "$pkg"
fi
exit 0
EOF
  chmod +x "$CLONE/gradlew"

  run_hook
  expect_rc nonzero "hook aborts when Kotlin worktree restoration fails"
  assert_log_contains "Failed to restore Kotlin file"
  assert_log_contains "Retaining Kotlin backup directory"
  assert_log_contains "Failed to restore the original Kotlin worktree state"
  expect_staged_exactly "$CARRIER" "$kt"

  local retained_backup
  retained_backup=$(grep -oE "Retaining Kotlin backup directory '[^']+'" "$LOG" | head -1 | sed "s/Retaining Kotlin backup directory '//;s/'//")
  if [[ -n "$retained_backup" && -d "$retained_backup" ]]; then
    pass "backup directory retained on disk: $retained_backup"
    if [[ -f "$retained_backup/tracked/$kt" ]]; then
      pass "backup contains original dirty worktree file"
    else
      fail "backup missing tracked file $kt"
    fi
    rm -rf "$retained_backup"
  else
    fail "backup directory was not retained on disk (retained_backup='$retained_backup')"
  fi

  rm -f "$CLONE/$pkg"
}

case_p13() {
  echo "=== P13: leftover failed Kotlin backup aborts before creating new backup ==="
  reset_clone
  local kt="app/src/main/java/io/github/mojri/hesabyar/CarrierP13.kt"
  printf '// p13 staged content\n' > "$CLONE/$kt"
  git_clone add "$kt"
  stage_carrier p13

  local stale_backup="$WORK/hesabyar-kt-stale.999999"
  mkdir -p "$stale_backup"

  # Inject prior failed restore state into hook copy
  sed -e "s|^kt_restore_failed=0|kt_restore_failed=1\nkt_backup_dir=\"$stale_backup\"|" \
    "$SRC/scripts/pre-commit" > "$HOOKS/pre-commit"
  chmod +x "$HOOKS/pre-commit"

  run_hook
  expect_rc nonzero "hook aborts when leftover Kotlin backup state failed"
  assert_log_contains "Leftover Kotlin backup could not be restored"
  assert_log_contains "$stale_backup"
  expect_staged_exactly "$CARRIER" "$kt"

  # Verify stale backup directory was preserved on disk and not clobbered
  if [[ -d "$stale_backup" ]]; then
    pass "stale backup directory retained without clobber: $stale_backup"
  else
    fail "stale backup directory was deleted or moved: $stale_backup"
  fi

  # Restore clean hook
  cp "$SRC/scripts/pre-commit" "$HOOKS/pre-commit"
  chmod +x "$HOOKS/pre-commit"
  rm -rf "$stale_backup"
}

case_q() {
  echo "=== Q: missing rust/ directory hard-fails when Rust sources are staged ==="
  reset_clone
  mk_rs_candidate "$P_A"
  git_clone add "$RS"
  stage_carrier q
  mv "$CLONE/rust" "$CLONE/rust_hidden_probe"
  run_hook
  mv "$CLONE/rust_hidden_probe" "$CLONE/rust"
  expect_rc nonzero "missing workspace fails fast"
  assert_log_contains "rust/"
  assert_log_contains "not found"
  assert_idx_file "$RS" "$EXP"
  assert_wt_file "$RS" "$EXP"
  expect_staged_exactly "$CARRIER" "$RS"
}

case_r() {
  echo "=== R: unusual filename (spaces, brackets, parens) ==="
  reset_clone
  local name="odd name (v1) [ok].rs"
  local p="rust/hesabyar-core/src/$name"
  printf '%s' "$P_A" > "$CLONE/$p"
  stage_cargo_toml_probe r1
  stage_carrier r1
  run_hook
  expect_rc 0 "untracked odd-named file passes and survives"
  assert_present_wt "$p"
  if contains_staged "$p"; then
    fail "odd-named untracked became staged"
  else
    pass "odd-named untracked stayed unstaged"
  fi
  expect_commit_exactly "$CARRIER" "$CARGO_TOML"
  reset_clone
  printf '%s' "$P_A" > "$CLONE/$p"
  setup_commit_no_verify "$p"
  printf '%s' "$P_ALT" > "$CLONE/$p"
  git_clone add "$p"
  cp "$CLONE/$p" "$EXP"
  stage_carrier r2
  run_hook
  expect_rc 0 "tracked odd-named file stages cleanly"
  assert_idx_file "$p" "$EXP"
  assert_wt_file "$p" "$EXP"
  expect_commit_exactly "$CARRIER" "$p"
  assert_nothing_staged
}

run_odd_name_case() { # $1 label, $2 raw filename
  reset_clone
  local p="rust/hesabyar-core/src/$2"
  if ! mkdir -p "$(dirname "$CLONE/$p")" 2>/dev/null \
     || ! printf '%s' "$P_A" > "$CLONE/$p" 2>/dev/null || [[ ! -f "$CLONE/$p" ]]; then
    skip "$1: filesystem refuses this filename on Windows"
    return 0
  fi
  printf '%s' "$P_A" > "$EXP"
  stage_cargo_toml_probe "$1"
  stage_carrier "$1"
  run_hook
  expect_rc 0 "$1: hook passes"
  assert_present_wt "$p"
  assert_wt_file "$p" "$EXP"
  if contains_staged "$p"; then
    fail "$1: untracked became staged"
  else
    pass "$1: untracked stayed unstaged"
  fi
  expect_commit_exactly "$CARRIER" "$CARGO_TOML"
  rm -f "$CLONE/$p"
  return 0
}

case_s() {
  echo "=== S: tab in filename ==="
  run_odd_name_case "tab-name" "$(printf 'tab\tname.rs')"
}

case_t() {
  echo "=== T: newline in filename (best effort) ==="
  run_odd_name_case "newline-name" "$(printf 'nl\nname.rs')"
}

case_u() {
  echo "=== U: failing Kotlin gate blocks every Rust gate ==="
  reset_clone
  printf '#!/usr/bin/env bash\nexit 1\n' > "$CLONE/gradlew"
  chmod +x "$CLONE/gradlew"
  rm -f "$CARGO_PROBE_LOG"
  local kt="app/src/main/java/io/github/mojri/hesabyar/CarrierU.kt"
  printf '// u probe\n' > "$CLONE/$kt"
  git_clone add "$kt"
  mk_rs_candidate "$P_A"
  git_clone add "$RS"
  stage_carrier u
  local saved_path=$PATH
  export PATH="$CARGO_SHIM_DIR:$PATH"
  run_hook
  export PATH=$saved_path
  expect_rc nonzero "commit aborted by the Kotlin gate failure"
  assert_log_contains "ktlintFormat failed"
  if grep -qF "[4/5]" "$LOG"; then
    fail "hook reached the Rust gates after a Kotlin failure"
  else
    pass "hook stopped before the Rust gates"
  fi
  if [[ -f "$CARGO_PROBE_LOG" ]]; then
    fail "cargo ran despite the Kotlin gate failure: $(cat "$CARGO_PROBE_LOG")"
  else
    pass "cargo never executed (probe log untouched)"
  fi
  assert_idx_file "$RS" "$EXP"
  assert_wt_file "$RS" "$EXP"
  expect_staged_exactly "$CARRIER" "$kt" "$RS"
}

# Can this host create symbolic links that bash can see with [[ -L ]]?
# Windows without Developer Mode / SeCreateSymbolicLinkPrivilege fails.
host_supports_symlink() {
  local pf="$WORK/symlink-capability.bin"
  local lf="$WORK/symlink-capability.link"
  : > "$pf"
  rm -f "$lf"
  if ln -s "$pf" "$lf" 2>/dev/null && [[ -L "$lf" ]]; then
    rm -f "$pf" "$lf"
    return 0
  fi
  rm -f "$pf" "$lf"
  return 1
}

# Can this host represent a chmod on a .rs file that bash and Git can see?
# NTFS under Git Bash cannot: chmod there is a silent no-op, so cases V and W
# report SKIP instead of faking the scenario.
host_represents_chmod() {
  local pf="$WORK/mode-capability.rs"
  : > "$pf"
  chmod +x "$pf"
  if [[ ! -x "$pf" ]]; then
    rm -f "$pf"
    return 1
  fi
  rm -f "$pf"
  return 0
}

# Print a path's permission bits as octal digits, or an empty string when no
# stat variant can read them. GNU stat accepts -c; BSD and macOS accept -f.
read_mode() {
  local m
  if m=$(stat -c '%a' -- "$1" 2>/dev/null) && [[ -n "$m" ]]; then
    printf '%s' "$m"
  elif m=$(stat -f '%Lp' -- "$1" 2>/dev/null) && [[ -n "$m" ]]; then
    printf '%s' "$m"
  fi
}

# Can this host represent a nonstandard permission mode such as 0740 on a
# file that bash can read back? NTFS under Git Bash cannot: chmod there is a
# silent no-op and stat reads back the old mode. Cases X and Y report SKIP
# instead of faking the scenario.
host_represents_full_modes() {
  local pf="$WORK/mode-capability-full.rs" m
  : > "$pf"
  chmod 740 "$pf" 2>/dev/null || { rm -f "$pf"; return 1; }
  m=$(read_mode "$pf")
  rm -f "$pf"
  [[ "$m" == "740" ]]
}

case_v() {
  echo "=== V: unstaged worktree mode change survives materialization ==="
  host_represents_chmod || { skip "V: host cannot represent chmod on .rs files"; return 0; }
  git_clone config core.fileMode true
  reset_clone
  mk_rs_candidate "$P_A"
  git_clone add "$RS"
  stage_carrier v
  chmod +x "$CLONE/$RS"
  cp "$EXP" "$WTX"
  run_hook
  expect_rc 0 "hook passes with an unstaged mode change present"
  assert_idx_file "$RS" "$EXP"
  assert_wt_file "$RS" "$WTX"
  if [[ -x "$CLONE/$RS" ]]; then
    pass "original worktree mode (+x) restored"
  else
    fail "worktree executable bit lost by materialization"
  fi
  local idx_mode
  idx_mode=$(git_clone ls-files -s -- "$RS" | awk '{print $1}')
  if [[ "$idx_mode" = "100644" ]]; then
    pass "index mode untouched (100644)"
  else
    fail "index mode changed: $idx_mode"
  fi
  expect_commit_exactly "$CARRIER" "$RS"
  assert_nothing_staged
}

case_w() {
  echo "=== W: worktree mode restored even when clippy fails ==="
  host_represents_chmod || { skip "W: host cannot represent chmod on .rs files"; return 0; }
  git_clone config core.fileMode true
  reset_clone
  mk_rs_candidate "$P_BAD_N"
  git_clone add "$RS"
  stage_carrier w
  chmod +x "$CLONE/$RS"
  cp "$EXP" "$WTX"
  run_hook
  expect_rc nonzero "clippy failure aborts the commit"
  assert_log_contains "cargo clippy failed"
  assert_idx_file "$RS" "$EXP"
  assert_wt_file "$RS" "$WTX"
  if [[ -x "$CLONE/$RS" ]]; then
    pass "mode restored through the failure path"
  else
    fail "mode lost on the clippy failure path"
  fi
  expect_staged_exactly "$CARRIER" "$RS"
}

# Shared body for cases X and Y: stage one candidate, leave a DIFFERENT
# unstaged worktree version behind, and stamp a nonstandard permission mode
# on it. This is stricter than V/W: restore must reproduce every bit, not
# just an execute class, and the committed bytes must be the staged
# candidate rather than the unstaged worktree version.
run_full_mode_scenario() { # <probe-text> <clippy-must-fail:0|1> <mode>
  local probe="$1" want_fail="$2" want_mode="$3" got_mode idx_mode head_oid exp_oid
  reset_clone
  mk_rs_candidate "$probe"
  git_clone add "$RS"
  stage_carrier x
  mk_rs_alt_worktree "$P_ALT"
  chmod "$want_mode" "$CLONE/$RS"
  run_hook
  if [[ $want_fail == 1 ]]; then
    expect_rc nonzero "clippy failure aborts the commit (mode $want_mode)"
    assert_log_contains "cargo clippy failed"
    expect_staged_exactly "$CARRIER" "$RS"
  else
    expect_rc 0 "hook passes with unstaged edit and mode $want_mode present"
    expect_commit_exactly "$CARRIER" "$RS"
    assert_nothing_staged
    head_oid=$(git_clone rev-parse "HEAD:$RS")
    exp_oid=$(git_clone hash-object --path="$RS" "$EXP")
    if [[ "$head_oid" = "$exp_oid" ]]; then
      pass "commit recorded the staged candidate, not the worktree copy"
    else
      fail "commit recorded wrong Rust content"
    fi
  fi
  assert_idx_file "$RS" "$EXP"
  assert_wt_file "$RS" "$WTX"
  got_mode=$(read_mode "$CLONE/$RS")
  if [[ "$got_mode" = "$want_mode" ]]; then
    pass "exact worktree mode $want_mode restored (got $got_mode)"
  else
    fail "worktree mode wrong: wanted $want_mode, got $got_mode"
  fi
  idx_mode=$(git_clone ls-files -s -- "$RS" | awk '{print $1}')
  if [[ "$idx_mode" = "100644" ]]; then
    pass "index mode untouched (100644)"
  else
    fail "index mode changed: $idx_mode"
  fi
}

case_x() {
  echo "=== X: unstaged nonstandard modes survive materialization ==="
  host_represents_full_modes || { skip "X: host cannot represent nonstandard modes"; return 0; }
  git_clone config core.fileMode true
  local m
  for m in 740 750 710; do
    echo "  --- mode $m ---"
    run_full_mode_scenario "$P_A" 0 "$m"
  done
}

case_y() {
  echo "=== Y: exact nonstandard modes restored even when clippy fails ==="
  host_represents_full_modes || { skip "Y: host cannot represent nonstandard modes"; return 0; }
  git_clone config core.fileMode true
  local m
  for m in 740 750 710; do
    echo "  --- mode $m ---"
    run_full_mode_scenario "$P_BAD_N" 1 "$m"
  done
}

case_z() {
  echo "=== Z: unusable stat aborts the hook before materialization ==="
  reset_clone
  mk_rs_candidate "$P_A"
  git_clone add "$RS"
  stage_carrier z
  mk_rs_alt_worktree "$P_ALT"
  local mode_before mode_after
  mode_before=$(read_mode "$CLONE/$RS")
  rm -f "$CARGO_PROBE_LOG" "$GIT_PROBE_LOG"
  local saved_path=$PATH
  export PATH="$STAT_SHIM_DIR:$GIT_SHIM_DIR:$PATH"
  run_hook
  export PATH=$saved_path
  expect_rc nonzero "hook aborts when no stat can capture a mode"
  assert_log_contains "Cannot capture file mode"
  if grep -qF "[4/5]" "$LOG"; then
    fail "hook reached the Rust gates despite mode-capture failure"
  else
    pass "hook stopped before cargo fmt materialized the tree"
  fi
  if [[ -f "$CARGO_PROBE_LOG" ]]; then
    fail "cargo ran despite the mode-capture failure: $(cat "$CARGO_PROBE_LOG")"
  else
    pass "cargo never executed (probe log untouched)"
  fi
  # The git wrapper must be engaged (its own `git commit` is logged) and it
  # must never have logged checkout-index. Final-state checks alone cannot
  # tell a clean abort from a transient materialization plus restore.
  if grep -q "^git commit" "$GIT_PROBE_LOG" 2>/dev/null; then
    pass "git wrapper probe engaged during the aborted run"
  else
    fail "git wrapper probe never logged an invocation (instrumentation broken)"
  fi
  if grep -q "checkout-index" "$GIT_PROBE_LOG" 2>/dev/null; then
    fail "checkout-index ran despite the mode-capture failure: $(grep checkout-index "$GIT_PROBE_LOG" | head -1)"
  else
    pass "git checkout-index never invoked before the abort"
  fi
  expect_staged_exactly "$CARRIER" "$RS"
  assert_idx_file "$RS" "$EXP"
  assert_wt_file "$RS" "$WTX"
  mode_after=$(read_mode "$CLONE/$RS")
  if [[ "$mode_after" = "$mode_before" ]]; then
    pass "worktree mode untouched by the aborted run"
  else
    fail "worktree mode changed: wanted $mode_before, got $mode_after"
  fi
}

# shellcheck disable=SC2016
case_e1_static_mode_restore_wiring() {
  echo "=== E1: hook source wires full-mode capture and restore ==="
  reset_clone
  local src="$SRC/scripts/pre-commit"
  if grep -qF 'declare -A RUST_DIRTY_MODES' "$src" \
     && grep -qF 'if ! mode=$(capture_file_mode "$f"); then' "$src" \
     && grep -qF "stat -c '%a'" "$src" \
     && grep -qF "stat -f '%Lp'" "$src" \
     && grep -qF 'chmod "$m" "$root/$f"' "$src"; then
    pass "full-mode capture/restore calls present in hook source"
  else
    fail "full-mode capture/restore wiring missing from hook source"
  fi
}

# shellcheck disable=SC2016
case_e2_static_capture_before_materialization() {
  echo "=== E2: mode capture is checked before index materialization ==="
  reset_clone
  local src="$SRC/scripts/pre-commit" cap_ln mat_ln
  cap_ln=$(grep -nF 'capture_file_mode "$f"' "$src" | head -1 | cut -d: -f1)
  mat_ln=$(grep -nF 'git checkout-index -f' "$src" | head -1 | cut -d: -f1)
  if [[ -n "$cap_ln" && -n "$mat_ln" && "$cap_ln" -lt "$mat_ln" ]] \
     && grep -qF 'Cannot capture file mode' "$src"; then
    pass "capture (line $cap_ln) precedes checkout-index (line $mat_ln) and failure aborts"
  else
    fail "capture/materialization order wrong (cap=$cap_ln mat=$mat_ln) or abort message missing"
  fi
}

# shellcheck disable=SC2016
case_e3_static_kotlin_symlink_rejection() {
  echo "=== E3: Kotlin symlink rejection precedes materialization ==="
  reset_clone
  local src="$SRC/scripts/pre-commit"
  local sym_ln mat_ln
  sym_ln=$(grep -nF 'if [[ -L "$f" ]]; then' "$src" | head -1 | cut -d: -f1)
  mat_ln=$(grep -nF 'git show ":$f"' "$src" | head -1 | cut -d: -f1)
  if [[ -n "$sym_ln" && -n "$mat_ln" && "$sym_ln" -lt "$mat_ln" ]] \
     && grep -qF "must not write through a symlink" "$src" \
     && grep -qF "Retaining Kotlin backup directory" "$src" \
     && grep -qF "Failed to restore the original Kotlin worktree state" "$src" \
     && grep -qF "Leftover Kotlin backup could not be restored" "$src"; then
    pass "symlink check (line $sym_ln) precedes materialization (line $mat_ln) and restore-failure retention is wired"
  else
    fail "symlink rejection or restore failure retention wiring missing from hook source"
  fi
}

# --- runner -------------------------------------------------------------------

CASES=(e0_static_no_destructive_ops a b c d e f g h i j k l m n o p p2 p3 p4 p5 p6 p7 p8 p9 p10 p11 p12 p13 q r s t u v w x y z e1_static_mode_restore_wiring e2_static_capture_before_materialization e3_static_kotlin_symlink_rejection)

for c in "${CASES[@]}"; do
  "case_$c"
done

echo ""
echo "==========================================="
echo "  Results: $PASS passed, $FAIL failed, $SKIP skipped"
echo "==========================================="
if [[ $FAIL -gt 0 ]]; then
  echo "Harness FAILED. Scratch dir: $WORK"
  exit 1
fi
echo "All hook regression cases passed."
