#!/usr/bin/env bash
set -euo pipefail

# bump-version.sh - Reads VERSION, applies semver bump, writes new VERSION.
# Also prepends to CHANGELOG.md (newest entries first).
# With an optional second argument, also bumps the Rust core version in
# rust/Cargo.toml [workspace.package] and refreshes the core package entry
# in rust/Cargo.lock (a stale lock breaks --locked builds).
#
# Usage: ./scripts/bump-version.sh <bump_type> [core_bump_type]
# Example: ./scripts/bump-version.sh minor patch

BUMP_TYPE="${1:?Usage: bump-version.sh <major|minor|patch>}"
CORE_BUMP_TYPE="${2:-}"
CARGO_MANIFEST="${CARGO_MANIFEST:-rust/Cargo.toml}"
CARGO_LOCK="${CARGO_LOCK:-rust/Cargo.lock}"
CORE_PACKAGE="${CORE_PACKAGE:-hesabyar-core}"
VERSION_FILE="${VERSION_FILE:-VERSION}"
CHANGELOG_FILE="${CHANGELOG_FILE:-CHANGELOG.md}"
DATE=$(date +%Y-%m-%d)

if [ ! -f "$VERSION_FILE" ]; then
  echo "ERROR: $VERSION_FILE not found"
  exit 1
fi

current=$(tr -d '[:space:]' < "$VERSION_FILE")

# Validate VERSION format
if ! echo "$current" | grep -qE '^[0-9]+\.[0-9]+\.[0-9]+$'; then
  echo "ERROR: $VERSION_FILE contains invalid format: '$current' (expected MAJOR.MINOR.PATCH)"
  exit 1
fi

IFS='.' read -r major minor patch <<< "$current"

case "$BUMP_TYPE" in
  major)
    major=$((major + 1))
    minor=0
    patch=0
    ;;
  minor)
    minor=$((minor + 1))
    patch=0
    ;;
  patch)
    patch=$((patch + 1))
    ;;
  *)
    echo "ERROR: Invalid bump type: $BUMP_TYPE (must be major, minor, or patch)"
    exit 1
    ;;
esac

new_version="${major}.${minor}.${patch}"

# Validate the core bump before writing anything: a failure after the
# VERSION/CHANGELOG writes would leave a half-bumped state, and a retry
# with a valid argument would bump the app version a second time.
current_core=""
if [ -n "$CORE_BUMP_TYPE" ] && [ "$CORE_BUMP_TYPE" != "null" ]; then
  case "$CORE_BUMP_TYPE" in
    major|minor|patch) ;;
    *)
      echo "ERROR: Invalid core bump type: $CORE_BUMP_TYPE (must be major, minor, or patch)" >&2
      exit 1
      ;;
  esac
  if [ ! -f "$CARGO_MANIFEST" ]; then
    echo "ERROR: $CARGO_MANIFEST not found (core bump requested)" >&2
    exit 1
  fi
  current_core=$(awk -F'"' '
    /^[[:space:]]*\[/ {
      sec = $0
      gsub(/^[[:space:]]*\[[[:space:]]*|[[:space:]]*\][[:space:]]*$/, "", sec)
      if (sec == "workspace.package") in_pkg = 1
      else in_pkg = 0
    }
    in_pkg && $0 ~ /^[[:space:]]*version[[:space:]]*=[[:space:]]*"/ { print $2; exit }
  ' "$CARGO_MANIFEST")
  if ! echo "$current_core" | grep -qE '^[0-9]+\.[0-9]+\.[0-9]+$'; then
    echo "ERROR: $CARGO_MANIFEST has no [workspace.package] version (got '$current_core')" >&2
    exit 1
  fi
  if [ -f "$CARGO_LOCK" ]; then
    # Fail fast on lock-refresh prerequisites: checking them here, before
    # any file is written, keeps a failure from leaving VERSION,
    # CHANGELOG.md, and the manifest half-bumped.
    if ! command -v python3 >/dev/null 2>&1; then
      echo "ERROR: python3 is required to refresh $CARGO_LOCK" >&2
      exit 1
    fi
    LOCK_FILE="$CARGO_LOCK" CORE_PACKAGE="$CORE_PACKAGE" OLD_CORE="$current_core" python3 -c '
import os, re, sys
lock_path = os.environ["LOCK_FILE"]
pkg = os.environ["CORE_PACKAGE"]
old = os.environ["OLD_CORE"]
with open(lock_path, "r") as f:
    text = f.read()
for block in text.split("[[package]]")[1:]:
    if re.search(r"(?m)^\s*name = \"%s\"$" % re.escape(pkg), block):
        for line in block.split("\n"):
            if line.strip() == "version = \"%s\"" % old:
                sys.exit(0)
        break
sys.exit("no [[package]] %s %s entry in %s" % (pkg, old, lock_path))
'
  fi
fi

echo "$new_version" > "$VERSION_FILE"
echo "Version bumped: $current -> $new_version" >&2

# Generate changelog entry
if [ -f "$CHANGELOG_FILE" ]; then
  existing=$(cat "$CHANGELOG_FILE")
else
  existing=""
fi

# Create changelog entry
entry="## [${new_version}] - ${DATE}

### Changed
- Release version ${new_version}
"

# Prepend new entry after header
header="# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.0.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

"

if echo "$existing" | grep -q "^# Changelog"; then
  # Extract body: skip the 7-line header block
  body=$(echo "$existing" | tail -n +8)
  echo "${header}${entry}${body}" > "$CHANGELOG_FILE"
else
  echo "${header}${entry}${existing}" > "$CHANGELOG_FILE"
fi

echo "CHANGELOG.md updated" >&2

# Optional Rust core bump. Stdout stays the app version: callers capture it.
if [ -n "$CORE_BUMP_TYPE" ] && [ "$CORE_BUMP_TYPE" != "null" ]; then
  # current_core was read and validated before any file was written.
  IFS='.' read -r core_major core_minor core_patch <<< "$current_core"
  case "$CORE_BUMP_TYPE" in
    major)
      core_major=$((core_major + 1))
      core_minor=0
      core_patch=0
      ;;
    minor)
      core_minor=$((core_minor + 1))
      core_patch=0
      ;;
    patch)
      core_patch=$((core_patch + 1))
      ;;
  esac
  new_core="${core_major}.${core_minor}.${core_patch}"

  # Rewrite only the version line inside [workspace.package].
  awk -v new_ver="$new_core" -F'"' '
    BEGIN { OFS = "\"" }
    /^[[:space:]]*\[/ {
      sec = $0
      gsub(/^[[:space:]]*\[[[:space:]]*|[[:space:]]*\][[:space:]]*$/, "", sec)
      if (sec == "workspace.package") in_pkg = 1
      else in_pkg = 0
    }
    !done && in_pkg && $0 ~ /^[[:space:]]*version[[:space:]]*=[[:space:]]*"/ { $2 = new_ver; done = 1 }
    { print }
  ' "$CARGO_MANIFEST" > "$CARGO_MANIFEST.tmp" && mv "$CARGO_MANIFEST.tmp" "$CARGO_MANIFEST"
  echo "Core version bumped: $current_core -> $new_core" >&2

  # Refresh the lock entry for the core package so it matches the manifest.
  # The release job's cargo test step validates the result.
  if [ -f "$CARGO_LOCK" ]; then
    # python3 and the lock entry were verified in the pre-write block above.
    LOCK_FILE="$CARGO_LOCK" CORE_PACKAGE="$CORE_PACKAGE" OLD_CORE="$current_core" NEW_CORE="$new_core" python3 -c '
import os, re, sys
lock_path = os.environ["LOCK_FILE"]
pkg = os.environ["CORE_PACKAGE"]
old = os.environ["OLD_CORE"]
new = os.environ["NEW_CORE"]
with open(lock_path, "r") as f:
    text = f.read()
blocks = text.split("[[package]]")
out = [blocks[0]]
found = False
for block in blocks[1:]:
    if not found and re.search(r"(?m)^\s*name = \"%s\"$" % re.escape(pkg), block):
        lines = block.split("\n")
        for i, line in enumerate(lines):
            if line.strip() == "version = \"%s\"" % old:
                indent = line[:len(line) - len(line.lstrip())]
                lines[i] = indent + "version = \"%s\"" % new
                found = True
                break
        block = "\n".join(lines)
    out.append(block)
if not found:
    sys.exit("no [[package]] %s %s entry in %s" % (pkg, old, lock_path))
with open(lock_path, "w") as f:
    f.write("[[package]]".join(out))
'
    echo "Cargo.lock updated for $CORE_PACKAGE ($current_core -> $new_core)" >&2
  else
    echo "WARNING: $CARGO_LOCK not found; skipping lock refresh (cargo will regenerate it)" >&2
  fi
fi

echo "$new_version"
