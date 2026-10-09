#!/usr/bin/env python3
"""Report documentation references to repo paths that no longer exist.

Scans AGENTS.md, README.md, docs/, plans/ (archived plans excluded), and progress.md for:
  * relative markdown links, resolved against the linking file's folder;
  * inline code spans that name a repo path (``app/...``, ``rust/...`` ...),
    resolved against the repo root.

A line containing ``check-docs: ignore`` is skipped, and so are code spans
under a heading naming new files (``New files``, ``Files to create``,
``فایل‌های جدید``), since a plan lists paths it has not created yet. A
heading carrying ``<!-- check-docs: planned -->`` is treated the same way,
for sections that edit files an earlier, unexecuted phase creates. Paths with glob or
placeholder characters (``*``, ``<``, ``{``) are skipped.

Usage: scripts/check_docs.py [--warn]
  --warn  print findings but exit 0 (for gradual adoption in CI)
"""

from __future__ import annotations

import re
import shutil
import subprocess
import sys
import urllib.parse
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SCAN = ["AGENTS.md", "README.md", "docs", "plans", "progress.md"]
EXCLUDE_DIRS = {ROOT / "plans" / "archive"}
PATH_ROOTS = ("app/", "rust/", "docs/", "plans/", "scripts/", "config/", ".github/", "gradle/")
APP_PKG = ROOT / "app/src/main/java/io/github/mojri/hesabyar"
SKIP_CHARS = set("*<>{}$ ")

LINK_RE = re.compile(r"\[[^\]]*\]\(([^)\s]+)(?:\s+\"[^\"]*\")?\)")
REF_LINK_RE = re.compile(r"^\s*\[([^\]]+)\]:\s*<?([^>\s]+)>?(?:\s+.*)?$")
CODE_RE = re.compile(r"`([^`\n]+)`")
FENCE_RE = re.compile(r"^\s*(```|~~~)")
HEADING_RE = re.compile(r"^#{1,6}\s")
# Sections that list files a plan will create: their paths do not exist yet.
NEW_FILES_RE = re.compile(
    r"(?:^#{1,6}\s+(?:(?:new\s+files?|files?\s+to\s+create|فایل‌های\s+(?:تست\s+)?جدید)\b|.*<!--\s*check-docs:\s*planned\s*-->))|"
    r"check-docs:\s*planned",
    re.IGNORECASE,
)

ROOT_FILES = {
    p.name
    for p in ROOT.iterdir()
    if p.is_file() and not p.name.startswith(".")
}
ROOT_FILES.update([".env.example", ".gitignore", ".gitattributes"])


def markdown_files() -> list[Path]:
    files: list[Path] = []
    for entry in SCAN:
        path = ROOT / entry
        if path.is_file():
            files.append(path)
        elif path.is_dir():
            files.extend(
                p
                for p in sorted(path.rglob("*.md"))
                if not any(ex in p.parents for ex in EXCLUDE_DIRS)
            )
    return files


def clean(target: str) -> str:
    target = target.strip("<>").split("#", 1)[0].split("?", 1)[0]
    # Drop trailing line refs such as Foo.kt:42 or Foo.kt#L10-L20.
    target = re.sub(r":[\d,\-–]+$", "", target)
    return target.rstrip("/.,;")


GIT = shutil.which("git")


def is_gitignored(path: Path) -> bool:
    """Generated or build output (gitignored) is legitimately absent."""
    if not GIT:
        return False
    result = subprocess.run(
        [GIT, "check-ignore", "-q", str(path)], cwd=ROOT, check=False
    )
    return result.returncode == 0


def resolves(target: str, base: Path) -> bool:
    candidates = [base / target]
    # Docs often write Kotlin paths relative to the app package (rust/RustBridge.kt).
    if base == ROOT:
        candidates.append(APP_PKG / target)
    return any(c.exists() or is_gitignored(c) for c in candidates)


def _is_external_or_anchor(raw: str) -> bool:
    if raw.startswith("#"):
        return True
    parsed = urllib.parse.urlsplit(raw)
    return bool(parsed.scheme or parsed.netloc)


def _check_links(line: str, lineno: int, base: Path) -> list[tuple[int, str]]:
    raw_targets = LINK_RE.findall(line)
    ref_match = REF_LINK_RE.match(line)
    if ref_match:
        raw_targets.append(ref_match.group(2))

    findings: list[tuple[int, str]] = []
    for raw in raw_targets:
        if _is_external_or_anchor(raw):
            continue
        target = clean(raw)
        if (
            target
            and "..." not in target
            and not SKIP_CHARS & set(target)
            and not resolves(target, base)
        ):
            findings.append((lineno, raw))
    return findings


def _is_root_file_ref(raw: str) -> bool:
    target = clean(raw)
    return bool(
        target
        and target in ROOT_FILES
        and "/" not in target
        and "\\" not in target
    )


def _check_code_spans(
    line: str, lineno: int, in_new_files: bool
) -> list[tuple[int, str]]:
    if in_new_files:
        return []
    findings: list[tuple[int, str]] = []
    for raw in CODE_RE.findall(line):
        if "..." in raw or SKIP_CHARS & set(raw):
            continue
        is_path_root = raw.startswith(PATH_ROOTS)
        is_root_file = _is_root_file_ref(raw)
        if not (is_path_root or is_root_file):
            continue
        target = clean(raw)
        if target and not resolves(target, ROOT):
            findings.append((lineno, raw))
    return findings


def broken_refs(md: Path) -> list[tuple[int, str]]:
    findings: list[tuple[int, str]] = []
    in_fence = False
    in_new_files = False
    for lineno, line in enumerate(md.read_text(encoding="utf-8").splitlines(), 1):
        if FENCE_RE.match(line):
            in_fence = not in_fence
            continue
        if in_fence or "check-docs: ignore" in line:
            continue
        if HEADING_RE.match(line):
            in_new_files = bool(NEW_FILES_RE.search(line))
            continue
        findings += _check_links(line, lineno, md.parent)
        findings += _check_code_spans(line, lineno, in_new_files)
    return findings


def main() -> int:
    warn_only = "--warn" in sys.argv[1:]
    total = 0
    for md in markdown_files():
        for lineno, ref in broken_refs(md):
            total += 1
            print(f"{md.relative_to(ROOT)}:{lineno}: missing path: {ref}")
    if total:
        print(f"\n{total} broken doc reference(s).", file=sys.stderr)
        return 0 if warn_only else 1
    print("Docs: no broken path references.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
