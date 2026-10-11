import json
from pathlib import Path
import tempfile
import unittest

from refresh_demo_previews import refresh


class RefreshDemoPreviewsTest(unittest.TestCase):
    def setUp(self):
        self.directory = tempfile.TemporaryDirectory()
        self.addCleanup(self.directory.cleanup)
        self.root = Path(self.directory.name)
        for source in (
            "offline-demo/diagrams/_summary_relations_large.mmd",
            "offline-change/change.sql",
            "offline-migration/summary.txt",
        ):
            path = self.root / "build/docs" / source
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text("generated content\n", encoding="utf-8")
        for name, match in (("match", True), ("mismatch", False)):
            path = self.root / "build/docs/offline-migration" / ("verification-" + name + ".json")
            path.write_text(json.dumps({"match": match, "mismatchedTasks": 0 if match else 1,
                                        "planFingerprint": "same-plan"}), encoding="utf-8")
        self.readme = self.root / "README.md"
        self.readme.write_text("\n".join(
            "<!-- demo-preview:{0}:start -->\n<!-- demo-preview:{0}:end -->".format(key)
            for key in ("diagram", "sql", "migration")), encoding="utf-8")

    def test_refresh_is_repeatable_and_copies_outputs(self):
        refresh(self.root)
        first = self.readme.read_bytes()
        refresh(self.root)
        self.assertEqual(first, self.readme.read_bytes())
        self.assertIn("```mermaid\ngenerated content\n```", self.readme.read_text(encoding="utf-8"))
        previews = self.root / "docs/examples/previews"
        self.assertEqual(3, len(list(previews.iterdir())))
        self.assertEqual("generated content\n", (previews / "change.sql").read_text(encoding="utf-8"))

    def test_missing_marker_leaves_files_unchanged(self):
        self.readme.write_text("missing markers", encoding="utf-8")
        with self.assertRaises(ValueError):
            refresh(self.root)
        self.assertEqual("missing markers", self.readme.read_text(encoding="utf-8"))
        self.assertFalse((self.root / "docs").exists())

    def test_unexpected_result_leaves_files_unchanged(self):
        original = self.readme.read_bytes()
        path = self.root / "build/docs/offline-migration/verification-mismatch.json"
        path.write_text(json.dumps({"match": True, "mismatchedTasks": 0,
                                    "planFingerprint": "same-plan"}), encoding="utf-8")
        with self.assertRaises(ValueError):
            refresh(self.root)
        self.assertEqual(original, self.readme.read_bytes())
        self.assertFalse((self.root / "docs").exists())
