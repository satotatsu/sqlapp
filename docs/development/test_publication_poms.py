"""Check freshly generated publication POMs; see build-and-test.md."""

from pathlib import Path
import unittest
import xml.etree.ElementTree as ET


ROOT = Path(__file__).resolve().parents[2]
NS = {"m": "http://maven.apache.org/POM/4.0.0"}


def pom(module, publication):
    return ET.parse(ROOT / module / "build" / "publications" / publication
                    / "pom-default.xml").getroot()


class PublicationPomTest(unittest.TestCase):
    def test_required_metadata(self):
        for module, publication in (
            ("sqlapp-gradle-plugin", "pluginMaven"),
            ("sqlapp-gradle-plugin", "sqlappPluginPluginMarkerMaven"),
            ("sqlapp-core", "mavenJava"),
        ):
            with self.subTest(module=module, publication=publication):
                document = pom(module, publication)
                for field in ("name", "description", "url", "licenses/license/name",
                              "licenses/license/url", "scm/url",
                              "developers/developer/id", "developers/developer/name"):
                    value = document.findtext("/".join("m:" + part for part in field.split("/")),
                                              namespaces=NS)
                    self.assertTrue(value and value.strip() and value != "null", field)

    def test_marker_points_to_plugin(self):
        plugin = pom("sqlapp-gradle-plugin", "pluginMaven")
        marker = pom("sqlapp-gradle-plugin", "sqlappPluginPluginMarkerMaven")
        self.assertEqual("com.sqlapp.db", marker.findtext("m:groupId", namespaces=NS))
        self.assertEqual("com.sqlapp.db.gradle.plugin",
                         marker.findtext("m:artifactId", namespaces=NS))
        for field, expected in (("groupId", "com.sqlapp"),
                                ("artifactId", "sqlapp-gradle-plugin")):
            self.assertEqual(expected, plugin.findtext("m:" + field, namespaces=NS))
        for field in ("groupId", "artifactId", "version"):
            self.assertEqual(plugin.findtext("m:" + field, namespaces=NS),
                             marker.findtext("m:dependencies/m:dependency/m:" + field,
                                             namespaces=NS))


if __name__ == "__main__":
    unittest.main()
