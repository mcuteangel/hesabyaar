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
  current_core=$(awk '
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
def unquote(value):
    return value.replace("\"", "").replace(chr(39), "")
for block in text.split("[[package]]")[1:]:
    name_found = False
    for line in block.split("\n"):
        nm = re.match(r"^\s*name\s*=\s*(\S+)\s*(#.*)?$", line)
        if nm and unquote(nm.group(1)) == pkg:
            name_found = True
            break
    if name_found:
        for line in block.split("\n"):
            vm = re.match(r"^version\s*=\s*(\S+)\s*(#.*)?$", line.strip())
            if vm and unquote(vm.group(1)) == old:
                sys.exit(0)
        break
sys.exit("no [[package]] %s %s entry in %s" % (pkg, old, lock_path))
'
  fi
fi

# Stage the core bump before writing anything: all fallible work (awk,
# python) happens here, so a failure leaves every file untouched. VERSION
# and CHANGELOG are staged to temp files as well, and every staged file is
# moved into place only in the publish phase below. The trap cleans the
# temp files up on any failure.
new_core=""
manifest_tmp=""
lock_tmp=""
version_tmp=""
changelog_tmp=""
trap 'rm -f "$manifest_tmp" "$lock_tmp" "$version_tmp" "$changelog_tmp"' EXIT
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

  # Rewrite only the version line inside [workspace.package], into a temp
  # file. The match is quote-agnostic (TOML also allows single quotes) and
  # quote-paired, so a trailing comment containing an apostrophe cannot
  # corrupt the line; the rewritten line is verified below so a missed
  # rewrite fails here, not after files were already written.
  manifest_tmp="$CARGO_MANIFEST.tmp"
  awk -v new_ver="$new_core" '
    /^[[:space:]]*\[/ {
      sec = $0
      sub(/[[:space:]]+#.*$/, "", sec)
      gsub(/^[[:space:]]*\[[[:space:]]*|[[:space:]]*\][[:space:]]*$/, "", sec)
      if (sec == "workspace.package") in_pkg = 1
      else in_pkg = 0
    }
    !done && in_pkg && $0 ~ /^[[:space:]]*version[[:space:]]*=[[:space:]]*['"'"'"]/ {
      sub(/"[^"]*"|'"'"'[^'"'"']*'"'"'/, "\"" new_ver "\"")
      done = 1
    }
    { print }
  ' "$CARGO_MANIFEST" > "$manifest_tmp"
  if ! grep -qE "^[[:space:]]*version[[:space:]]*=[[:space:]]*\"${new_core//./\\.}\"[[:space:]]*(#.*)?$" "$manifest_tmp"; then
    echo "ERROR: failed to rewrite $CARGO_MANIFEST to $new_core" >&2
    exit 1
  fi

  # Refresh the lock entry for the core package so it matches the manifest.
  # The release job's cargo test step validates the result.
  if [ -f "$CARGO_LOCK" ]; then
    # python3 and the lock entry were verified in the pre-write block above.
    lock_tmp="$CARGO_LOCK.tmp"
    LOCK_FILE="$CARGO_LOCK" LOCK_TMP="$lock_tmp" CORE_PACKAGE="$CORE_PACKAGE" OLD_CORE="$current_core" NEW_CORE="$new_core" python3 -c '
import os, re, sys
lock_path = os.environ["LOCK_FILE"]
lock_tmp = os.environ["LOCK_TMP"]
pkg = os.environ["CORE_PACKAGE"]
old = os.environ["OLD_CORE"]
new = os.environ["NEW_CORE"]
with open(lock_path, "r") as f:
    text = f.read()
blocks = text.split("[[package]]")
out = [blocks[0]]
found = False
for block in blocks[1:]:
    name_found = False
    for line in block.split("\n"):
        nm = re.match(r"^\s*name\s*=\s*(\S+)\s*(#.*)?$", line)
        if nm and nm.group(1).replace("\"", "").replace(chr(39), "") == pkg:
            name_found = True
            break
    if not found and name_found:
        lines = block.split("\n")
        for i, line in enumerate(lines):
            m = re.match(r"^(\s*)version\s*=\s*(\S+)\s*(#.*)?$", line)
            if m and m.group(2).replace("\"", "").replace(chr(39), "") == old:
                comment = m.group(3)
                lines[i] = m.group(1) + "version = \"%s\"" % new + (" " + comment if comment else "")
                found = True
                break
        block = "\n".join(lines)
    out.append(block)
if not found:
    sys.exit("no [[package]] %s %s entry in %s" % (pkg, old, lock_path))
with open(lock_tmp, "w") as f:
    f.write("[[package]]".join(out))
'
    echo "Cargo.lock staged for $CORE_PACKAGE ($current_core -> $new_core)" >&2
  else
    echo "WARNING: $CARGO_LOCK not found; skipping lock refresh (cargo will regenerate it)" >&2
  fi
fi

version_tmp="$VERSION_FILE.tmp"
echo "$new_version" > "$version_tmp"

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

changelog_tmp="$CHANGELOG_FILE.tmp"
if echo "$existing" | grep -q "^# Changelog"; then
  # Extract body: skip the 7-line header block
  body=$(echo "$existing" | tail -n +8)
  echo "${header}${entry}${body}" > "$changelog_tmp"
else
  echo "${header}${entry}${existing}" > "$changelog_tmp"
fi

# Publish all staged files. Re-validate the lock first: if it
# vanished after the pre-check, fail now, before anything is moved.
# The lock moves first, so a failure here leaves the manifest (the
# version source of truth) untouched.
if [ -n "$new_core" ]; then
  if [ -n "$lock_tmp" ] && [ ! -f "$CARGO_LOCK" ]; then
    echo "ERROR: $CARGO_LOCK disappeared after the pre-write check; refusing to publish a partial bump" >&2
    exit 1
  fi
  if [ -n "$lock_tmp" ]; then
    mv "$lock_tmp" "$CARGO_LOCK" || { echo "ERROR: failed to publish $CARGO_LOCK" >&2; exit 1; }
  fi
  mv "$manifest_tmp" "$CARGO_MANIFEST" || { echo "ERROR: failed to publish $CARGO_MANIFEST" >&2; exit 1; }
  echo "Core version bumped: $current_core -> $new_core" >&2
fi
mv "$version_tmp" "$VERSION_FILE"
echo "Version bumped: $current -> $new_version" >&2
mv "$changelog_tmp" "$CHANGELOG_FILE"
echo "CHANGELOG.md updated" >&2

echo "$new_version"
