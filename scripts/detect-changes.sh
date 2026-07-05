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

# Check if any application files changed
has_app_changes=false
while IFS= read -r file; do
  [ -z "$file" ] && continue

  # Check if this file matches any include pattern
  case "$file" in
    app/*|src/*|*.kt|*.kts|*.java|*.xml|*.toml)
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
