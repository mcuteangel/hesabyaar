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

# Collect commit messages
commits=$(git log --pretty=format:"- %s (%h)" "$BASE_REF".."$HEAD_REF" 2>/dev/null || \
          git log --pretty=format:"- %s (%h)" "$BASE_REF"..."$HEAD_REF" 2>/dev/null || echo "- Release $VERSION")

# Collect changed files
changed_files=$(git diff --name-only "$BASE_REF".."$HEAD_REF" 2>/dev/null || echo "")

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

if [ -z "$GEMINI_API_KEY" ]; then
  echo "WARNING: GEMINI_API_KEY not set, falling back to raw commit list"
  echo "## Release $VERSION"$'\n'"$commits"
  exit 0
fi

# Escape prompt for JSON
escaped_prompt=$(printf '%s' "$prompt" | sed 's/\\/\\\\/g' | sed 's/"/\\"/g' | tr '\n' ' ' | sed 's/ $//')

# Call Gemini API
response=$(curl -s -w "\n%{http_code}" \
  "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=$GEMINI_API_KEY" \
  -H "Content-Type: application/json" \
  -d "{
    \"contents\": [{
      \"parts\": [{\"text\": \"${escaped_prompt}\"}]
    }],
    \"generationConfig\": {
      \"temperature\": 0.3,
      \"maxOutputTokens\": 1024
    }
  }" 2>/dev/null || echo "")

http_code=$(echo "$response" | tail -n1)
body=$(echo "$response" | head -n -1)

if [ "$http_code" != "200" ] || [ -z "$body" ]; then
  echo "WARNING: Gemini API call failed (HTTP $http_code), falling back to raw commit list"
  echo "## Release $VERSION"$'\n'"$commits"
  exit 0
fi

# Extract text from Gemini response (without jq)
notes=$(echo "$body" | grep -o '"text":"[^"]*"' | head -1 | sed 's/"text":"//;s/"$//' 2>/dev/null || echo "")

# If that fails, try multiline text extraction
if [ -z "$notes" ]; then
  notes=$(echo "$body" | python3 -c "
import sys, json
try:
    data = json.load(sys.stdin)
    print(data['candidates'][0]['content']['parts'][0]['text'])
except:
    pass
" 2>/dev/null || echo "")
fi

# If that also fails, try with python (fallback)
if [ -z "$notes" ]; then
  notes=$(echo "$body" | python -c "
import sys, json
try:
    data = json.load(sys.stdin)
    print(data['candidates'][0]['content']['parts'][0]['text'])
except:
    pass
" 2>/dev/null || echo "")
fi

if [ -z "$notes" ]; then
  echo "WARNING: Empty response from Gemini, falling back to raw commit list"
  echo "## Release $VERSION"$'\n'"$commits"
  exit 0
fi

echo "$notes"
