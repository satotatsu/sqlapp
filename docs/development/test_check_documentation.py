"""Negative and boundary fixtures for the offline documentation checker."""

import tempfile
import unittest
from pathlib import Path

from check_documentation import check


class DocumentationCheckTest(unittest.TestCase):
    def fixture(self, pages):
        temporary = tempfile.TemporaryDirectory()
        self.addCleanup(temporary.cleanup)
        root = Path(temporary.name)
        for name, text in pages.items():
            path = root / name
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(text, encoding="utf-8")
        return root

    def test_duplicate_unicode_headings_and_fenced_examples(self):
        root = self.fixture({
            "docs/README.md": "# Docs\n[Guide](guide.md#日本語-1)\n",
            "docs/guide.md": "# 日本語\n## 日本語\n~~~~text\n[Example](absent.md)\n```\n~~~~\n",
        })
        self.assertEqual([], check(root)[0])

    def test_missing_file_and_heading_report_source_lines(self):
        root = self.fixture({
            "docs/README.md": "# Docs\n[Missing](absent.md)\n[Heading](guide.md#absent)\n",
            "docs/guide.md": "# Guide\n",
        })
        errors = check(root)[0]
        self.assertEqual(2, len(errors))
        self.assertIn("README.md:2:", errors[0])
        self.assertIn("missing file", errors[0])
        self.assertIn("missing heading", errors[1])

    def test_unreachable_page_is_not_hidden_by_external_link(self):
        root = self.fixture({
            "docs/README.md": "# Docs\n[Remote](https://example.com/guide.md)\n",
            "docs/guide.md": "# Guide\n",
            "docs/old.md": "# Old\nThis guide has moved to [Guide](guide.md).\n",
        })
        errors = check(root)[0]
        self.assertEqual(1, len(errors))
        self.assertIn("guide.md: unreachable", errors[0])

    def test_unclosed_fence_fails(self):
        root = self.fixture({"docs/README.md": "# Docs\n```java\ncode\n"})
        self.assertTrue(any("unclosed code fence" in error for error in check(root)[0]))


if __name__ == "__main__":
    unittest.main()
