#!/bin/sh
# classify-changes.sh - Classify changed files between two refs into change classes.
#
# Usage: classify-changes.sh [base_ref] [head_ref]
#   base_ref defaults to empty (treated as "everything changed").
#   head_ref defaults to HEAD.
#
# Prints KEY=true/false lines for each class and appends them to
# $GITHUB_OUTPUT when that variable is set (GitHub Actions).
#
# Classes: kotlin gradle rust site docs workflows actions ci_scripts config
#   code = kotlin || gradle || rust  (anything feeding the Android build)
#
# POSIX sh compatible. Unknown refs fail open (all classes true) so a
# misconfigured diff never silently skips CI.
#
# shellcheck disable=SC2034
# (the per-class flags are assigned in the case arms below and read back
# through eval in the final emit loop; ShellCheck cannot see through that
# indirection, hence the file-wide SC2034 exemption)
set -eu

BASE_REF="${1:-}"
HEAD_REF="${2:-HEAD}"

# Single source of truth for the class list. emit_all, the per-class
# initializers and the final emit loop all iterate over this, so adding a
# class means editing exactly one line and the fail-open and normal paths
# cannot drift apart.
CLASSES="kotlin gradle rust site docs workflows actions ci_scripts config"

emit() {
  # $1 = key, $2 = value
  echo "$1=$2"
  if [ -n "${GITHUB_OUTPUT:-}" ]; then
    echo "$1=$2" >> "$GITHUB_OUTPUT"
  fi
}

emit_all() {
  # $1 = value for every class
  # shellcheck disable=SC2086
  for k in $CLASSES; do
    emit "$k" "$1"
  done
  # code is derived (kotlin || gradle || rust); in the fail-open paths all
  # inputs are true, so code follows the same value.
  emit code "$1"
}

# Zero-SHA (initial push) or empty base: treat as "everything changed".
case "$BASE_REF" in
  ""|0000000000000000000000000000000000000000) emit_all true; exit 0 ;;
esac

# --no-renames: a pure rename is listed as delete+add, so both the source
# and the destination path are classified and moving a file out of its
# class cannot silently skip the corresponding checks.
if changed=$(git diff --no-renames --name-only "$BASE_REF...$HEAD_REF" 2>/dev/null); then
  :
elif changed=$(git diff --no-renames --name-only "$BASE_REF..$HEAD_REF" 2>/dev/null); then
  :
else
  # Unresolvable refs (shallow/missing history): fail open.
  emit_all true
  exit 0
fi

LIST="$(mktemp "${TMPDIR:-/tmp}/classify-changes.XXXXXX")"
trap 'rm -f "$LIST"' EXIT
printf '%s\n' "$changed" > "$LIST"

# shellcheck disable=SC2086
for k in $CLASSES; do
  eval "$k=false"
done

while IFS= read -r f; do
  [ -z "$f" ] && continue
  case "$f" in
    # Note: *.gradle.kts already covers settings.gradle.kts, and *.md
    # already covers CHANGELOG.md - keep the lists free of such
    # redundancies (ShellCheck SC2221/SC2222).
    # Root gradle.properties/gradlew/gradlew.bat are build inputs too:
    # a PR touching only them must still trigger the Android build.
    *.gradle.kts|gradle/*|gradle.properties|gradlew|gradlew.bat) gradle=true ;;
    app/*) kotlin=true ;;
    rust/*) rust=true ;;
    site/*) site=true ;;
    .github/workflows/*) workflows=true ;;
    .github/actions/*) actions=true ;;
    .github/scripts/*|scripts/*) ci_scripts=true ;;
    *.md|docs/*|plans/*) docs=true ;;
    # NOTE: .github/pull_request_template.md is intentionally absent here:
    # *.md above already classifies it as docs (and Super-Linter excludes
    # *.md from linting anyway), so listing it would be dead weight that
    # ShellCheck flags as shadowed (SC2222).
    # config/* covers config/detekt/detekt.yml and the baseline. Note: in
    # case patterns (unlike pathname expansion) * matches / too, so
    # config/* matches nested paths like config/detekt/detekt.yml.
    # app/build.gradle.kts wires detekt via config.setFrom(...), so a
    # detekt-config-only PR must still trigger the Android build that
    # validates it.
    VERSION|config/*|.gitignore|codecov.yml|metadata.json|\
.github/dependabot.yml|.github/labeler.yml|\
.codacy.yml|.codefactor.json|.editorconfig|.gitattributes|.hound.yml|.jshintrc|\
.env.example) config=true ;;
    # Any other .github/* file (e.g. a future dependabot-style config not
    # listed above) falls through to ci_scripts rather than being skipped.
    .github/*) ci_scripts=true ;;
  esac
done < "$LIST"

if [ "$kotlin" = true ] || [ "$gradle" = true ] || [ "$rust" = true ]; then
  code=true
else
  code=false
fi

# shellcheck disable=SC2086
for k in $CLASSES; do
  eval "emit \"\$k\" \"\$$k\""
done
emit code "$code"
