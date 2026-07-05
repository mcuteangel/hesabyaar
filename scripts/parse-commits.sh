#!/usr/bin/env bash
set -euo pipefail

# parse-commits.sh - Parses conventional commits to determine release type.
# Outputs JSON with: bump_type (major/minor/patch), summary
#
# Usage: ./scripts/parse-commits.sh <base_ref> <head_ref>
# Example: ./scripts/parse-commits.sh origin/main HEAD

BASE_REF="${1:?Usage: parse-commits.sh <base_ref> <head_ref>}"
HEAD_REF="${2:-HEAD}"

BUMP_LEVEL=0  # 0=none, 1=patch, 2=minor, 3=major
SUMMARY=""

# Read commits from base to head
commits=$(git log --pretty=format:"%s|||%h" "$BASE_REF".."$HEAD_REF" 2>/dev/null || \
          git log --pretty=format:"%s|||%h" "$BASE_REF"..."$HEAD_REF" 2>/dev/null || echo "")

if [ -z "$commits" ]; then
  echo '{"bump_type":"patch","summary":"Release changes"}'
  exit 0
fi

has_feat=false
has_fix=false
has_breaking=false
commit_list=""

while IFS= read -r line; do
  [ -z "$line" ] && continue
  subject="${line%%|||*}"
  hash="${line##*|||}"

  commit_list="${commit_list}- ${hash} ${subject}"$'\n'

  # Check for breaking changes
  if echo "$subject" | grep -qE "^[a-z]+(\(.+\))?!:"; then
    has_breaking=true
  fi

  # Check for feat (minor)
  if echo "$subject" | grep -qE "^feat(\(.+\))?:"; then
    has_feat=true
  fi

  # Check for fix, perf, security, refactor (patch)
  if echo "$subject" | grep -qE "^(fix|perf|security|refactor)(\(.+\))?:"; then
    has_fix=true
  fi

  # Check for breaking change in body
done <<< "$commits"

# Also check for BREAKING CHANGE in commit body
breaking_body=$(git log --pretty=format:"%b" "$BASE_REF".."$HEAD_REF" 2>/dev/null || echo "")
if echo "$breaking_body" | grep -q "BREAKING CHANGE"; then
  has_breaking=true
fi

# Determine bump level
if [ "$has_breaking" = true ]; then
  BUMP_LEVEL=3
  BUMP_TYPE="major"
elif [ "$has_feat" = true ]; then
  BUMP_LEVEL=2
  BUMP_TYPE="minor"
elif [ "$has_fix" = true ]; then
  BUMP_LEVEL=1
  BUMP_TYPE="patch"
else
  # Default to patch for any other changes
  BUMP_LEVEL=1
  BUMP_TYPE="patch"
fi

# Build summary from commit messages
summary_lines=""
while IFS= read -r line; do
  [ -z "$line" ] && continue
  subject="${line%%|||*}"
  # Skip merge commits
  if echo "$subject" | grep -qE "^Merge "; then
    continue
  fi
  summary_lines="${summary_lines}${subject}"$'\n'
done <<< "$commits"

# Trim trailing newline
summary_lines=$(echo "$summary_lines" | sed '/^$/d' | head -20)

# Output JSON using jq
summary_flat=$(echo "$summary_lines" | tr '\n' ' ' | sed 's/ $//')
jq -n --arg bump_type "$BUMP_TYPE" --arg summary "$summary_flat" \
  '{bump_type: $bump_type, summary: $summary}'
