#!/usr/bin/env bash
set -euo pipefail

# parse-commits.sh - Parses conventional commits to determine release type.
# Outputs JSON with: bump_type (major/minor/patch),
#   core_bump_type (major/minor/patch, or null when no Rust core change),
#   summary
#
# Usage: ./scripts/parse-commits.sh <base_ref> <head_ref>
# Example: ./scripts/parse-commits.sh origin/main HEAD

BASE_REF="${1:?Usage: parse-commits.sh <base_ref> <head_ref>}"
HEAD_REF="${2:-HEAD}"

# Read commits from base to head (try both range syntaxes)
commits=$(git log --pretty=format:"%s|||%h" "$BASE_REF".."$HEAD_REF" 2>/dev/null || \
          git log --pretty=format:"%s|||%h" "$BASE_REF"..."$HEAD_REF" 2>/dev/null || echo "")

if [ -z "$commits" ]; then
  echo '{"bump_type":"patch","core_bump_type":null,"summary":"Release changes"}'
  exit 0
fi

has_feat=false
has_fix=false
has_breaking=false
summary_lines=""

while IFS= read -r line; do
  [ -z "$line" ] && continue
  subject="${line%%|||*}"

  # Skip merge commits by parent count, not by subject: a merge with a
  # custom message has no "Merge " prefix, and a normal commit can carry
  # one. A commit with two or more parents is a merge.
  merge_parents=$(git rev-list --parents -n 1 "${line##*|||}" 2>/dev/null | wc -w | tr -d ' ' || echo 0)
  if [ "$merge_parents" -gt 2 ]; then
    continue
  fi

  summary_lines="${summary_lines}${subject}"$'\n'

  # Check for breaking changes (type! or BREAKING CHANGE in subject)
  case "$subject" in
    *'!:'*) has_breaking=true ;;
  esac

  # Check for feat (minor)
  case "$subject" in
    'feat('*|'feat:'*) has_feat=true ;;
  esac

  # Check for fix, perf, security, refactor (patch)
  case "$subject" in
    'fix('*|'fix:'*) has_fix=true ;;
    'perf('*|'perf:'*) has_fix=true ;;
    'security('*|'security:'*) has_fix=true ;;
    'refactor('*|'refactor:'*) has_fix=true ;;
  esac
done <<< "$commits"

# Also check for BREAKING CHANGE in commit body (try both range syntaxes)
breaking_body=$(git log --pretty=format:"%b" "$BASE_REF".."$HEAD_REF" 2>/dev/null || \
                git log --pretty=format:"%b" "$BASE_REF"..."$HEAD_REF" 2>/dev/null || echo "")
if echo "$breaking_body" | grep -q "BREAKING CHANGE"; then
  has_breaking=true
fi

# Determine bump level
if [ "$has_breaking" = true ]; then
  BUMP_TYPE="major"
elif [ "$has_feat" = true ]; then
  BUMP_TYPE="minor"
elif [ "$has_fix" = true ]; then
  BUMP_TYPE="patch"
else
  BUMP_TYPE="patch"
fi

# Rust core bump: classify only the commits that touch rust/ files, with the
# same conventional-commit rules as the app bump. A range with no rust/
# changes leaves the core version untouched (core_bump_type null).
# If the range already bumps [workspace.package].version by hand (the old
# manual step), ship that version as-is instead of bumping again. A
# manifest missing at head, or newly added in the range, is the same case:
# there is no established version to bump.
rust_breaking=false
rust_feat=false
rust_fix=false
commit_hashes=$(git log --pretty=format:"%H" "$BASE_REF".."$HEAD_REF" 2>/dev/null || \
                git log --pretty=format:"%H" "$BASE_REF"..."$HEAD_REF" 2>/dev/null || echo "")
while IFS= read -r hash; do
  [ -z "$hash" ] && continue
  # Skip merge commits by parent count (see the app loop above).
  rust_parents=$(git rev-list --parents -n 1 "$hash" 2>/dev/null | wc -w | tr -d ' ' || echo 0)
  if [ "$rust_parents" -gt 2 ]; then
    continue
  fi
  rust_subject=$(git show -s --pretty=format:"%s" "$hash" 2>/dev/null || echo "")
  rust_files=$(git show --name-only --pretty=format: "$hash" 2>/dev/null || echo "")
  rust_touched=false
  while IFS= read -r rust_file; do
    case "$rust_file" in
      rust/*) rust_touched=true; break ;;
    esac
  done <<< "$rust_files"
  [ "$rust_touched" = true ] || continue
  rust_body=$(git show -s --pretty=format:"%b" "$hash" 2>/dev/null || echo "")
  case "$rust_subject" in
    *'!:'*) rust_breaking=true ;;
  esac
  case "$rust_body" in
    *"BREAKING CHANGE"*) rust_breaking=true ;;
  esac
  case "$rust_subject" in
    'feat('*|'feat:'*) rust_feat=true ;;
  esac
  case "$rust_subject" in
    'fix('*|'fix:'*) rust_fix=true ;;
    'perf('*|'perf:'*) rust_fix=true ;;
    'security('*|'security:'*) rust_fix=true ;;
    'refactor('*|'refactor:'*) rust_fix=true ;;
  esac
done <<< "$commit_hashes"

# Print the [workspace.package].version from a manifest on stdin.
# Only the exact [workspace.package] table counts: any other header resets
# the flag, so version lines under dependency tables are never misread as
# the workspace version. The value extraction is quote-agnostic: TOML
# also allows single-quoted strings, and a version line may carry an
# inline comment.
workspace_version() {
  awk '
    /^[[:space:]]*\[/ {
      sec = $0
      sub(/[[:space:]]+#.*$/, "", sec)
      gsub(/^[[:space:]]*\[[[:space:]]*|[[:space:]]*\][[:space:]]*$/, "", sec)
      if (sec == "workspace.package") in_pkg = 1
      else in_pkg = 0
    }
    in_pkg && $0 ~ /^[[:space:]]*version[[:space:]]*=/ {
      line = $0
      sub(/^[[:space:]]*version[[:space:]]*=[[:space:]]*/, "", line)
      sub(/[[:space:]]+#.*$/, "", line)
      gsub(/['"'"'"]/, "", line)
      gsub(/^[[:space:]]+|[[:space:]]+$/, "", line)
      print line; exit
    }
  ' || true
}
old_core=$(git show "$BASE_REF:rust/Cargo.toml" 2>/dev/null | workspace_version || true)
new_core=""
if [ -f rust/Cargo.toml ]; then
  new_core=$(workspace_version < rust/Cargo.toml || true)
fi

if [ -z "$new_core" ] || [ "$old_core" != "$new_core" ]; then
  CORE_BUMP_TYPE=""
elif [ "$rust_breaking" = true ]; then
  CORE_BUMP_TYPE="major"
elif [ "$rust_feat" = true ]; then
  CORE_BUMP_TYPE="minor"
elif [ "$rust_fix" = true ]; then
  CORE_BUMP_TYPE="patch"
else
  CORE_BUMP_TYPE=""
fi

# Trim summary to 20 lines, format as bulleted list
summary_lines=$(printf '%s\n' "$summary_lines" | sed '/^$/d' | head -20 || true)
summary_bullets=""
if [ -n "$summary_lines" ]; then
  while IFS= read -r line; do
    [ -z "$line" ] && continue
    if [ -z "$summary_bullets" ]; then
      summary_bullets="- $line"
    else
      summary_bullets="$summary_bullets"$'\n'"- $line"
    fi
  done <<< "$summary_lines"
fi

# Detect Python interpreter
PYTHON_CMD=""
if python3 --version >/dev/null 2>&1; then
  PYTHON_CMD="python3"
elif py -3 --version >/dev/null 2>&1; then
  PYTHON_CMD="py -3"
fi

# Output JSON using jq, python, or minimal shell fallback
if command -v jq >/dev/null 2>&1; then
  jq -n --arg bump_type "$BUMP_TYPE" --arg core_bump_type "$CORE_BUMP_TYPE" --arg summary "$summary_bullets" \
    '{bump_type: $bump_type, core_bump_type: (if $core_bump_type == "" then null else $core_bump_type end), summary: $summary}'
elif [ -n "$PYTHON_CMD" ]; then
  BUMP_TYPE="$BUMP_TYPE" CORE_BUMP_TYPE="$CORE_BUMP_TYPE" SUMMARY_BULLETS="$summary_bullets" $PYTHON_CMD -c \
    'import json, os; print(json.dumps({"bump_type": os.environ["BUMP_TYPE"], "core_bump_type": os.environ["CORE_BUMP_TYPE"] or None, "summary": os.environ["SUMMARY_BULLETS"]}))'
else
  # Shell fallback escaping backslashes, double quotes, and newlines
  escaped_summary=$(printf '%s' "$summary_bullets" | sed 's/\\/\\\\/g; s/"/\\"/g' | awk '{if (NR>1) printf "\\n"; printf "%s", $0}')
  if [ -n "$CORE_BUMP_TYPE" ]; then
    core_json="\"$CORE_BUMP_TYPE\""
  else
    core_json="null"
  fi
  printf '{"bump_type":"%s","core_bump_type":%s,"summary":"%s"}\n' "$BUMP_TYPE" "$core_json" "$escaped_summary"
fi
