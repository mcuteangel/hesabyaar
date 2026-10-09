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
set -eu

BASE_REF="${1:-}"
HEAD_REF="${2:-HEAD}"

emit() {
  # $1 = key, $2 = value
  echo "$1=$2"
  if [ -n "${GITHUB_OUTPUT:-}" ]; then
    echo "$1=$2" >> "$GITHUB_OUTPUT"
  fi
}

emit_all() {
  # $1 = value for every class
  for k in kotlin gradle rust site docs workflows actions ci_scripts config code; do
    emit "$k" "$1"
  done
}

# Zero-SHA (initial push) or empty base: treat as "everything changed".
case "$BASE_REF" in
  ""|0000000000000000000000000000000000000000) emit_all true; exit 0 ;;
esac

if changed=$(git diff --name-only "$BASE_REF...$HEAD_REF" 2>/dev/null); then
  :
elif changed=$(git diff --name-only "$BASE_REF..$HEAD_REF" 2>/dev/null); then
  :
else
  # Unresolvable refs (shallow/missing history): fail open.
  emit_all true
  exit 0
fi

LIST="$(mktemp "${TMPDIR:-/tmp}/classify-changes.XXXXXX")"
trap 'rm -f "$LIST"' EXIT
printf '%s\n' "$changed" > "$LIST"

kotlin=false; gradle=false; rust=false; site=false; docs=false
workflows=false; actions=false; ci_scripts=false; config=false

while IFS= read -r f; do
  [ -z "$f" ] && continue
  case "$f" in
    *.gradle.kts|gradle/*|settings.gradle.kts) gradle=true ;;
    app/*) kotlin=true ;;
    rust/*) rust=true ;;
    site/*) site=true ;;
    .github/workflows/*) workflows=true ;;
    .github/actions/*) actions=true ;;
    .github/scripts/*|scripts/*) ci_scripts=true ;;
    *.md|docs/*|plans/*|CHANGELOG.md) docs=true ;;
    VERSION|.github/dependabot.yml|.github/labeler.yml|.github/pull_request_template.md|\
.codacy.yml|.codefactor.json|.editorconfig|.gitattributes|.hound.yml|.jshintrc|\
.env.example) config=true ;;
    .github/*) ci_scripts=true ;;
  esac
done < "$LIST"

if [ "$kotlin" = true ] || [ "$gradle" = true ] || [ "$rust" = true ]; then
  code=true
else
  code=false
fi

emit kotlin "$kotlin"
emit gradle "$gradle"
emit rust "$rust"
emit site "$site"
emit docs "$docs"
emit workflows "$workflows"
emit actions "$actions"
emit ci_scripts "$ci_scripts"
emit config "$config"
emit code "$code"
