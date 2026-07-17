#!/usr/bin/env python3
"""Enrich CHANGELOG.md with release notes.

Replaces the current version's placeholder entry (written by bump-version.sh)
with the generated notes, and backfills up to N previous placeholder entries
with the descriptions published on their GitHub release pages.

Usage: update-changelog.py <version> <notes_file> [prev_limit]
"""
import re
import subprocess  # nosec: needed to call the gh CLI; args are a fixed list, no shell
import sys

VERSION = sys.argv[1]
NOTES_FILE = sys.argv[2]
PREV_LIMIT = int(sys.argv[3]) if len(sys.argv) > 3 else 5

with open(NOTES_FILE, encoding="utf-8") as fh:
    current_notes = fh.read().strip()

with open("CHANGELOG.md", encoding="utf-8") as fh:
    changelog = fh.read()

m = re.search(r"(?m)^## \[", changelog)
if not m:
    print("No changelog entries found", file=sys.stderr)
    sys.exit(1)
header = changelog[: m.start()]
body = changelog[m.start():]

# Split body into [heading, content, heading, content, ...]
parts = re.split(r"(?m)^(## \[[^\]]+\].*)$", body)
headings = []
contents = []
i = 1
while i < len(parts):
    headings.append(parts[i])
    contents.append(parts[i + 1] if i + 1 < len(parts) else "")
    i += 2


def strip_wrapper(text: str) -> str:
    text = re.sub(r"(?s)^<div dir=\"rtl\">\s*", "", text)
    text = re.sub(r"(?s)\s*</div>\s*$", "", text)
    # Drop any nested "previous releases" section to avoid recursion.
    text = re.sub(r"(?s)\n## نسخه‌های قبلی.*$", "", text)
    return text.strip()


def is_placeholder(content: str) -> bool:
    c = content.strip()
    if not re.search(r"- Release version \d+\.\d+\.\d+", c):
        return False
    # A placeholder is ONLY the version bullet (under any ### subsection).
    # Strip headers, bullets, and blockquotes; if nothing real remains it's a
    # placeholder. Real (backfilled) entries keep prose/extra bullets behind.
    cleaned = re.sub(r"(?m)^#{1,6} .*$", "", c)
    cleaned = re.sub(r"(?m)^[-*+] .*$", "", cleaned)
    cleaned = re.sub(r"(?m)^>.*$", "", cleaned)
    return not cleaned.strip()


def gh_body(tag: str):
    try:
        out = subprocess.run(  # nosec B603,B607: fixed list, no shell; `tag` is a trusted local version
            ["gh", "release", "view", tag, "--json", "body", "--jq", ".body"],
            capture_output=True,
            text=True,
            timeout=30,
        )
        if out.returncode == 0 and out.stdout.strip():
            return strip_wrapper(out.stdout)
    except Exception as exc:
        print(f"gh release view failed for {tag}: {exc}", file=sys.stderr)
    return None


# Replace the current version's entry with the generated notes.
cur_idx = next(
    (idx for idx, h in enumerate(headings) if re.search(r"^## \[" + re.escape(VERSION) + r"\]", h)),
    None,
)
if cur_idx is None:
    headings.insert(0, f"## [{VERSION}]")
    contents.insert(0, "")
    cur_idx = 0
contents[cur_idx] = "\n" + current_notes + "\n"

# Backfill previous placeholder entries from their release pages.
backfilled = 0
for idx, h in enumerate(headings):
    if idx == cur_idx or backfilled >= PREV_LIMIT:
        continue
    if not is_placeholder(contents[idx]):
        continue
    mt = re.search(r"^## \[([^\]]+)\]", h)
    if not mt:
        continue
    ver = mt.group(1)
    tag = ver if ver.startswith("v") else "v" + ver
    body_notes = gh_body(tag)
    if body_notes:
        contents[idx] = "\n" + body_notes + "\n"
        backfilled += 1

new_body = "".join(h + c for h, c in zip(headings, contents))
with open("CHANGELOG.md", "w", encoding="utf-8") as fh:
    fh.write(header + new_body)

print(
    f"CHANGELOG.md updated (current + {backfilled} backfilled)",
    file=sys.stderr,
)
