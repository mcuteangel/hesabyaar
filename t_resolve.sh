set -euo pipefail
gh pr view "${PR_NUMBER}" \
  --json number,title,body,files \
  --jq '{
    number: .number,
    title: .title,
    body: ((.body // "") | .[0:300]),
    files: ([.files[].path] | .[0:10])
  }' > pr.json
echo "number=$(jq -r .number pr.json)" >> "$GITHUB_OUTPUT"

# A conventional commit title already states the change kind. Map it
# to a label without calling the model at all.
# python3 is used here because a jq capture needs escaping that is
# fragile inside a YAML block scalar.
read -r KIND LABEL < <(python3 - << 'PYEOF'
import json, re, sys

title = json.load(open("pr.json"))["title"].strip()
match = re.match(r"^([a-z][a-z0-9]*)(?:\([^)]*\))?!?:", title, re.IGNORECASE)
if not match:
    print(" ")
    sys.exit(0)
kind = match.group(1).lower()
labels = {
    "fix": "bug",
    "bug": "bug",
    "feat": "enhancement",
    "perf": "enhancement",
    "docs": "documentation",
    "refactor": "refactor",
    "test": "test",
    "security": "security",
    "build": "ci",
    "ci": "ci",
    "chore": "chore",
    "i18n": "i18n",
}
print(kind, labels.get(kind, ""))
PYEOF
)
echo "title_label=${LABEL}" >> "$GITHUB_OUTPUT"
echo "kind=${KIND}" >> "$GITHUB_OUTPUT"
