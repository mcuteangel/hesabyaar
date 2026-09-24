#!/usr/bin/env bash
set -euo pipefail

# generate-release-notes.sh - Uses Gemini to generate human-friendly release notes.
#
# Usage: ./scripts/generate-release-notes.sh <version> <base_ref> <head_ref>
# Requires GEMINI_API_KEY environment variable.

VERSION="${1:?Usage: generate-release-notes.sh <version> <base_ref> <head_ref>}"
BASE_REF="${2:?Usage: generate-release-notes.sh <version> <base_ref> <head_ref>}"
HEAD_REF="${3:-HEAD}"
GEMINI_API_KEY="${GEMINI_API_KEY:-}"
GEMINI_MODEL="${GEMINI_MODEL:-gemini-2.0-flash}"

# Collect commit messages
commits=$(git log --pretty=format:"- %s (%h)" "$BASE_REF".."$HEAD_REF" 2>/dev/null || \
          git log --pretty=format:"- %s (%h)" "$BASE_REF"..."$HEAD_REF" 2>/dev/null || echo "- Release $VERSION")

# Collect changed files
changed_files=$(git diff --name-only "$BASE_REF".."$HEAD_REF" 2>/dev/null || echo "")

generate_fallback_notes() {
  local feat_lines=""
  local fix_lines=""
  local other_lines=""

  while IFS= read -r line; do
    [ -z "$line" ] && continue
    local msg="${line#- }"
    case "$msg" in
      "Merge "*) continue ;;
    esac

    case "$msg" in
      feat*|Feat*)
        feat_lines="${feat_lines}* ${msg}"$'\n'
        ;;
      fix*|Fix*)
        fix_lines="${fix_lines}* ${msg}"$'\n'
        ;;
      *)
        other_lines="${other_lines}* ${msg}"$'\n'
        ;;
    esac
  done <<< "$commits"

  echo "نسخه ${VERSION} حساب‌یار منتشر شد."
  echo ""
  if [ -n "$feat_lines" ]; then
    echo "### امکانات جدید"
    printf '%s' "$feat_lines"
    echo ""
  fi
  if [ -n "$fix_lines" ]; then
    echo "### رفع مشکلات"
    printf '%s' "$fix_lines"
    echo ""
  fi
  if [ -n "$other_lines" ]; then
    echo "### بهبودها و تغییرات"
    printf '%s' "$other_lines"
  fi
}

if [ -z "$GEMINI_API_KEY" ]; then
  echo "WARNING: GEMINI_API_KEY not set, falling back to structured commit notes" >&2
  generate_fallback_notes
  exit 0
fi

# Build the prompt
prompt="Generate release notes for version $VERSION of an Android personal finance app called Hesabyar.

Changes in this release:
${commits}

Changed files:
${changed_files}

Requirements:
- Write in Persian (Farsi) since this is a Persian-first app
- Use simple, user-friendly language
- Group changes into categories: Features (امکانات جدید), Fixes (رفع مشکلات), Improvements (بهبودها)
- Keep it concise (3-5 bullet points per category max)
- Start with a brief summary line
- Use markdown format
- Do not include version number in the title (just the content)
- If there are no changes in a category, omit that category entirely"

# Escape prompt for JSON using python or jq, fallback to sed
if python3 --version >/dev/null 2>&1; then
  escaped_prompt=$(python3 -c 'import json, sys; print(json.dumps(sys.stdin.read()))' <<< "$prompt")
elif py -3 --version >/dev/null 2>&1; then
  escaped_prompt=$(py -3 -c 'import json, sys; print(json.dumps(sys.stdin.read()))' <<< "$prompt")
elif command -v jq >/dev/null 2>&1; then
  escaped_prompt=$(printf '%s' "$prompt" | jq -Rs .)
else
  escaped_prompt=$(printf '%s' "$prompt" | sed 's/\\/\\\\/g' | sed 's/"/\\"/g' | tr '\n' ' ' | sed 's/ $//' | sed 's/^/"/;s/$/"/')
fi

call_gemini_api() {
  local model="$1"
  local max_attempts=3
  local attempt=1
  local delay=2

  while [ "$attempt" -le "$max_attempts" ]; do
    local response
    response=$(curl -s -w "\n%{http_code}" --connect-timeout 10 --max-time 35 \
      "https://generativelanguage.googleapis.com/v1beta/models/${model}:generateContent" \
      -H "Content-Type: application/json" \
      -H "x-goog-api-key: $GEMINI_API_KEY" \
      -d "{
        \"contents\": [{
          \"parts\": [{\"text\": ${escaped_prompt}}]
        }],
        \"generationConfig\": {
          \"temperature\": 0.3,
          \"maxOutputTokens\": 1024
        }
      }" 2>/dev/null || echo "")

    local http_code
    http_code=$(echo "$response" | tail -n1)
    local body
    body=$(echo "$response" | head -n -1)

    if [ "$http_code" = "200" ] && [ -n "$body" ]; then
      echo "$body"
      return 0
    fi

    echo "Attempt $attempt with model $model returned HTTP $http_code" >&2
    if [ -n "$body" ]; then
      echo "Response body: $body" >&2
    fi

    case "$http_code" in
      429|500|502|503|504|"")
        if [ "$attempt" -lt "$max_attempts" ]; then
          echo "Retrying in ${delay}s..." >&2
          sleep "$delay"
          delay=$((delay * 2))
        fi
        ;;
      *)
        return 1
        ;;
    esac

    attempt=$((attempt + 1))
  done

  return 1
}

# Determine candidate models to try
models_to_try=("$GEMINI_MODEL")
if [ "$GEMINI_MODEL" != "gemini-2.0-flash" ]; then
  models_to_try+=("gemini-2.0-flash")
fi
if [ "$GEMINI_MODEL" != "gemini-1.5-flash" ]; then
  models_to_try+=("gemini-1.5-flash")
fi

gemini_body=""
for model_cand in "${models_to_try[@]}"; do
  echo "Attempting release note generation with Gemini model: $model_cand" >&2
  if gemini_body=$(call_gemini_api "$model_cand"); then
    break
  fi
done

if [ -z "$gemini_body" ]; then
  echo "WARNING: All Gemini API attempts failed, falling back to structured commit notes" >&2
  generate_fallback_notes
  exit 0
fi

# Extract text from Gemini response safely
notes=""

extract_text_script='
import sys, json
try:
    data = json.load(sys.stdin)
    for c in data.get("candidates", []):
        for p in (c.get("content") or {}).get("parts", []):
            t = (p.get("text") or "").strip()
            if t:
                print(t)
                sys.exit(0)
except Exception:
    pass
'

if [ -z "$notes" ] && python3 --version >/dev/null 2>&1; then
  notes=$(echo "$gemini_body" | python3 -c "$extract_text_script" 2>/dev/null || echo "")
fi

if [ -z "$notes" ] && py -3 --version >/dev/null 2>&1; then
  notes=$(echo "$gemini_body" | py -3 -c "$extract_text_script" 2>/dev/null || echo "")
fi

if [ -z "$notes" ] && command -v jq >/dev/null 2>&1; then
  notes=$(echo "$gemini_body" | jq -r '.candidates[0].content.parts[0].text // empty' 2>/dev/null || echo "")
fi

if [ -z "$notes" ]; then
  echo "WARNING: Empty or blocked response from Gemini, falling back to structured commit notes" >&2
  generate_fallback_notes
  exit 0
fi

echo "$notes"
