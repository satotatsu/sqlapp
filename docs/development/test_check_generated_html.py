from pathlib import Path
import tempfile
import unittest

from check_generated_html import check


class GeneratedHtmlCheckTest(unittest.TestCase):
    def test_encoded_links_and_external_resources(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "index.html").write_text(
                '<a href="table%20one.html#columns">table</a>'
                '<script src="https://example.invalid/library.js"></script>', encoding="utf-8")
            (root / "table one.html").write_text("<h1>Table</h1>", encoding="utf-8")
            (root / "diagram.svg").write_text(
                '<svg xmlns="http://www.w3.org/2000/svg"><a href="table%20one.html"/></svg>',
                encoding="utf-8")
            self.assertEqual(([], 2), check(root))

    def test_missing_entry_point_target_and_invalid_svg(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            (root / "table.html").write_text('<a href="missing.html">missing</a>', encoding="utf-8")
            (root / "diagram.svg").write_text("<svg>", encoding="utf-8")
            errors, count = check(root)
            self.assertEqual(3, len(errors))
            self.assertEqual(1, count)
