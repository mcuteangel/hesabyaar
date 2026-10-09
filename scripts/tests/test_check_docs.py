#!/usr/bin/env python3
"""Unit tests for scripts/check_docs.py."""

from __future__ import annotations

import sys
import unittest
from pathlib import Path
from unittest.mock import patch

# Add repo root to import path
ROOT = Path(__file__).resolve().parent.parent.parent
sys.path.insert(0, str(ROOT / "scripts"))

import check_docs  # noqa: E402


class CheckDocsTests(unittest.TestCase):
    def setUp(self) -> None:
        self.tmp_md = ROOT / "_test_tmp_doc.md"

    def tearDown(self) -> None:
        if self.tmp_md.exists():
            self.tmp_md.unlink()

    def test_clean_strips_anchors_and_line_numbers(self) -> None:
        self.assertEqual(check_docs.clean("<docs/ROADMAP.md#L10-L20>"), "docs/ROADMAP.md")
        self.assertEqual(check_docs.clean("app/build.gradle.kts:42"), "app/build.gradle.kts")
        self.assertEqual(check_docs.clean("AGENTS.md?query=test#heading"), "AGENTS.md")

    def test_is_external_or_anchor(self) -> None:
        self.assertTrue(check_docs.is_external_or_anchor("#section-heading"))
        self.assertTrue(check_docs.is_external_or_anchor("https://github.com/foo/bar"))
        self.assertTrue(check_docs.is_external_or_anchor("HTTP://EXAMPLE.COM"))
        self.assertTrue(check_docs.is_external_or_anchor("mailto:dev@example.com"))
        self.assertTrue(check_docs.is_external_or_anchor("//cdn.example.com/asset.js"))
        self.assertFalse(check_docs.is_external_or_anchor("docs/ROADMAP.md"))
        self.assertFalse(check_docs.is_external_or_anchor("../AGENTS.md"))

    def test_gitignored_or_existing_path_resolves(self) -> None:
        self.assertTrue(check_docs.resolves("AGENTS.md", ROOT))
        self.assertTrue(check_docs.resolves("app/build.gradle.kts", ROOT))
        self.assertTrue(check_docs.resolves("rust/lcov.info", ROOT))
        self.assertFalse(check_docs.resolves("non_existent_folder/file.kt", ROOT))

    def test_is_gitignored_logic(self) -> None:
        self.assertTrue(check_docs.is_gitignored(ROOT / "rust/lcov.info"))
        self.assertTrue(check_docs.is_gitignored(ROOT / "app/build/generated.jar"))
        self.assertFalse(check_docs.is_gitignored(ROOT / "AGENTS.md"))
        self.assertFalse(check_docs.is_gitignored(ROOT / "some_random_source_file.kt"))

    def test_broken_refs_detects_missing_paths(self) -> None:
        content = """# Test Document
Here is a broken link: [Missing](docs/non_existent_doc_12345.md).
Here is a valid link: [Roadmap](docs/ROADMAP.md).
Here is a broken code span: `app/src/main/non_existent_file.kt`.
Here is a broken root file: `non_existent_root_file.md`.
Here is an ignored line: [Missing](docs/missing2.md) <!-- check-docs: ignore -->
Here is a code block:
```
`app/src/main/fictional_path.kt`
```
"""
        self.tmp_md.write_text(content, encoding="utf-8")
        findings = check_docs.broken_refs(self.tmp_md)
        refs = [raw for _, raw in findings]
        self.assertIn("docs/non_existent_doc_12345.md", refs)
        self.assertIn("app/src/main/non_existent_file.kt", refs)
        self.assertNotIn("docs/ROADMAP.md", refs)
        self.assertNotIn("docs/missing2.md", refs)
        self.assertNotIn("app/src/main/fictional_path.kt", refs)

    def test_reference_style_links(self) -> None:
        content = """# Ref Links
See [ref1] and [ref2].

[ref1]: docs/ROADMAP.md
[ref2]: <docs/non_existent_ref_9999.md> "Optional title"
"""
        self.tmp_md.write_text(content, encoding="utf-8")
        findings = check_docs.broken_refs(self.tmp_md)
        refs = [raw for _, raw in findings]
        self.assertIn("docs/non_existent_ref_9999.md", refs)
        self.assertNotIn("docs/ROADMAP.md", refs)

    def test_new_files_heading_suppression(self) -> None:
        content = """# Regular Section
`app/src/main/real_missing.kt`

## Files to Create
`app/src/main/planned_new_file.kt`

### فایل‌های جدید
`app/src/main/another_planned_file.kt`

## Unrelated Section
`app/src/main/unrelated_missing.kt`
"""
        self.tmp_md.write_text(content, encoding="utf-8")
        findings = check_docs.broken_refs(self.tmp_md)
        refs = [raw for _, raw in findings]
        self.assertIn("app/src/main/real_missing.kt", refs)
        self.assertIn("app/src/main/unrelated_missing.kt", refs)
        self.assertNotIn("app/src/main/planned_new_file.kt", refs)
        self.assertNotIn("app/src/main/another_planned_file.kt", refs)

    def test_cli_warn_flag_exits_zero(self) -> None:
        with patch.object(sys, "argv", ["check_docs.py", "--warn"]):
            return_code = check_docs.main()
        self.assertEqual(return_code, 0)


if __name__ == "__main__":
    unittest.main()
