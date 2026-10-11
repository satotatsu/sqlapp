"""Check repository documentation without network access or third-party packages."""

import re
import sys
import unicodedata
from collections import Counter
from pathlib import Path
from urllib.parse import unquote, urlsplit


def slug(text):
    text = re.sub(r"\[([^]]+)\]\([^)]+\)", r"\1", text)
    text = re.sub(r"<[^>]+>", "", text).lower()
    return "".join(
        char for char in text
        if char in " -_" or unicodedata.category(char)[0] in "LN"
    ).replace(" ", "-")


def parse(path):
    anchors = set()
    counts = Counter()
    links = []
    errors = []
    fence = None
    text = path.read_text(encoding="utf-8")
    for number, line in enumerate(text.splitlines(), 1):
        marker = re.match(r"^ {0,3}(`{3,}|~{3,})(.*)$", line)
        if fence:
            if marker and marker[1][0] == fence[0] and len(marker[1]) >= len(fence) and not marker[2].strip():
                fence = None
            continue
        if marker:
            fence = marker[1]
            continue
        heading = re.match(r"^ {0,3}#{1,6}\s+(.+?)\s*#*\s*$", line)
        if heading:
            base = slug(heading[1])
            index = counts[base]
            counts[base] += 1
            anchors.add(base + ("-" + str(index) if index else ""))
        # Ignore inline code examples. Check inline Markdown links and images.
        line = re.sub(r"(`+).*?\1", "", line)
        for match in re.finditer(r"!?\[[^]\n]*\]\((<[^>]+>|[^\s)]+)(?:\s+\"[^\"]*\")?\)", line):
            links.append((number, match[1].strip("<>")))
    if fence:
        errors.append("{}: unclosed code fence".format(path))
    return anchors, links, errors, "This guide has moved to " in text


def check(root):
    root = Path(root).resolve()
    docs = root / "docs"
    files = sorted(docs.rglob("*.md"))
    files += [p for p in (root / "README.md", root / "CONTRIBUTING.md") if p.exists()]
    parsed = {p: parse(p) for p in files}
    errors = []
    graph = {}
    checked = 0
    for path, (_, links, parse_errors, _) in parsed.items():
        errors.extend(parse_errors)
        graph[path] = []
        for line, target in links:
            url = urlsplit(target)
            if url.scheme or url.netloc:
                continue
            checked += 1
            destination = (path.parent / unquote(url.path)).resolve() if url.path else path
            label = "{}:{}: {}".format(path.relative_to(root), line, target)
            if not destination.exists():
                errors.append(label + " (missing file)")
                continue
            if destination.suffix.lower() != ".md":
                continue
            if destination not in parsed:
                parsed_destination = parse(destination)
            else:
                parsed_destination = parsed[destination]
            if url.fragment and unquote(url.fragment) not in parsed_destination[0]:
                errors.append(label + " (missing heading)")
            graph[path].append(destination)
    seen = set()
    pending = [docs / "README.md"]
    if not pending[0].exists():
        errors.append("docs/README.md: missing documentation index")
    while pending:
        path = pending.pop()
        if path not in seen:
            seen.add(path)
            pending.extend(graph.get(path, []))
    current = [p for p in files if docs in p.parents and not parsed[p][3]]
    for path in current:
        if path not in seen:
            errors.append("{}: unreachable from docs/README.md".format(path.relative_to(root)))
    return errors, checked, len(current)


def main():
    errors, links, pages = check(Path(__file__).resolve().parents[2])
    if errors:
        print("\n".join(errors))
        return 1
    print("PASS: {} local links; {} current pages reachable; code fences closed.".format(links, pages))
    return 0


if __name__ == "__main__":
    sys.exit(main())
