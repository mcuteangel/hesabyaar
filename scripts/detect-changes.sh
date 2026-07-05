#!/usr/bin/env bash
set -euo pipefail

# detect-changes.sh - Determines whether application code changed.
# Exit code 0 = release needed, exit code 1 = skip release.
#
# Usage: ./scripts/detect-changes.sh <base_ref> <head_ref>
# Example: ./scripts/detect-changes.sh origin/main HEAD

BASE_REF="${1:?Usage: detect-changes.sh <base_ref> <head_ref>}"
HEAD_REF="${2:-HEAD}"

changed_files=$(git diff --name-only "$BASE_REF"..."$HEAD_REF" 2>/dev/null || \
                git diff --name-only "$BASE_REF".."$HEAD_REF" 2>/dev/null || \
                git diff --name-only "$BASE_REF" "$HEAD_REF" 2>/dev/null || \
                echo "")

if [ -z "$changed_files" ]; then
  echo "SKIP: No changed files detected between $BASE_REF and $HEAD_REF"
  exit 1
fi

# Function to check if a file matches a glob pattern (simple matcher)
matches_pattern() {
  local file="$1"
  local pattern="$2"

  # Handle ** pattern (match across directories)
  if [[ "$pattern" == ** ]]; then
    local prefix="${pattern%%\*\*}"
    local suffix="${pattern##\*\*}"
    if [[ -z "$prefix" ]] && [[ -z "$suffix" ]]; then
      return 0  # ** matches everything
    fi
    if [[ -n "$prefix" ]] && [[ "$file" == ${prefix}* ]]; then
      return 0
    fi
    if [[ -n "$suffix" ]] && [[ "$file" == *${suffix} ]]; then
      return 0
    fi
    return 1
  fi

  # Handle * pattern (match within directory)
  if [[ "$pattern" == * ]]; then
    local prefix="${pattern%%\*}"
    if [[ -z "$prefix" ]] || [[ "$file" == ${prefix}* ]]; then
      return 0
    fi
    return 1
  fi

  # Exact match
  [[ "$file" == "$pattern" ]]
}

# Check if any application files changed
has_app_changes=false
while IFS= read -r file; do
  [ -z "$file" ] && continue

  # Check if this file matches any include pattern
  case "$file" in
    app/*|src/*|*.kt|*.java|*.xml|*.toml)
      has_app_changes=true
      break
      ;;
    build.gradle.kts|build.gradle|settings.gradle.kts|settings.gradle|gradle.properties|proguard-rules.pro)
      has_app_changes=true
      break
      ;;
    gradle/*)
      has_app_changes=true
      break
      ;;
  esac
done <<< "$changed_files"

if [ "$has_app_changes" = false ]; then
  echo "SKIP: No application code changes detected"
  echo "Changed files:"
  echo "$changed_files" | head -20
  exit 1
fi

echo "RELEASE_NEEDED: Application code changes detected"
echo "Changed files:"
echo "$changed_files" | head -30
exit 0
