#!/usr/bin/env bash
set -euo pipefail

# detect-changes.sh - Determines whether application code changed.
# Exit code 0 = release needed, exit code 1 = skip release.
#
# Usage: ./scripts/detect-changes.sh <base_ref> <head_ref>
# Example: ./scripts/detect-changes.sh origin/main HEAD

BASE_REF="${1:?Usage: detect-changes.sh <base_ref> <head_ref>}"
HEAD_REF="${2:-HEAD}"

# Handle zero SHA (initial push) - treat as all files changed
if echo "$BASE_REF" | grep -qE '^0+$'; then
  echo "RELEASE_NEEDED: Initial push detected (zero SHA base)"
  exit 0
fi

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

  # Dependency manifests/lockfiles never trigger a release on their own.
  # Dependabot bumps land here weekly (grouped); releasing per dependency
  # update would flood the releases page. App code touching these same
  # files alongside other sources still triggers a release below.
  case "$file" in
    gradle/libs.versions.toml|gradle/libs.versions.toml.lock|gradle.lockfile|versions.lock)
      continue
      ;;
    # Matches the workspace manifest and every member manifest
    # (e.g. rust/hesabyar-core/Cargo.toml, rust/uniffi-gen/Cargo.toml).
    rust/Cargo.toml|rust/*/Cargo.toml|rust/Cargo.lock)
      continue
      ;;
  esac

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

# A manual version bump in a Rust workspace manifest must still trigger a
# release even though manifest edits are otherwise treated as dependency-only.
while IFS= read -r file; do
  [ -z "$file" ] && continue
  case "$file" in
    rust/Cargo.toml|rust/*/Cargo.toml)
      if git diff "$BASE_REF"..."$HEAD_REF" -- "$file" 2>/dev/null | grep -qE '^[+-]version\s*='; then
        echo "RELEASE_NEEDED: Version bump detected in $file"
        exit 0
      fi
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
