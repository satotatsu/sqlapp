"""Check local HTML/SVG references in the saved example without network access."""

from html.parser import HTMLParser
from pathlib import Path
import sys
from urllib.parse import unquote, urlsplit
from xml.etree import ElementTree as ET


class References(HTMLParser):
    def __init__(self):
        super().__init__()
        self.references = []

    def handle_starttag(self, tag, attributes):
        self.references.extend(value for key, value in attributes
                               if key in ("href", "src") and value)


def check(root):
    errors = []
    count = 0
    if not (root / "index.html").is_file():
        errors.append("Missing entry point: " + str(root / "index.html"))
    for path in sorted(root.rglob("*")):
        if path.suffix not in (".html", ".svg"):
            continue
        try:
            if path.suffix == ".html":
                parser = References()
                parser.feed(path.read_text(encoding="utf-8"))
                references = parser.references
            else:
                tree = ET.parse(path)
                references = [value for element in tree.iter()
                              for key, value in element.attrib.items()
                              if key.rsplit("}", 1)[-1] in ("href", "src")]
            for reference in references:
                url = urlsplit(reference)
                if url.scheme or url.netloc or not url.path:
                    continue
                count += 1
                target = path.parent / unquote(url.path)
                if not target.exists():
                    errors.append("{}: missing {}".format(path, reference))
        except (OSError, UnicodeError, ValueError, ET.ParseError) as error:
            errors.append("{}: {}".format(path, error))
    return errors, count


if __name__ == "__main__":
    errors, count = check(Path(__file__).resolve().parents[2] / "docs/examples/gradle-html")
    for error in errors:
        print(error, file=sys.stderr)
    if not errors:
        print("PASS: {} local HTML/SVG references; SVG XML parsed.".format(count))
    sys.exit(bool(errors))
