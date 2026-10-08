"""Refresh README previews from outputs of a successful demoSqlapp run."""

import json
from pathlib import Path


def refresh(root):
    generated = root / "build/docs"
    output = root / "docs/examples/previews"
    reports = []
    for name in ("verification-match.json", "verification-mismatch.json"):
        reports.append(json.loads((generated / "offline-migration" / name).read_text(encoding="utf-8")))
    match, mismatch = reports
    if not match["match"] or mismatch["match"] or mismatch["mismatchedTasks"] != 1:
        raise ValueError("Expected the demo match followed by one intentional mismatch")
    if match["planFingerprint"] != mismatch["planFingerprint"]:
        raise ValueError("Verification reports must describe the same migration plan")
    sources = (
        ("diagram", "offline-demo/diagrams/_summary_relations_large.mmd", "customer-order.mmd", "mermaid"),
        ("sql", "offline-change/change.sql", "change.sql", "sql"),
        ("migration", "offline-migration/summary.txt", "migration-summary.txt", "text"),
    )
    readme_path = root / "README.md"
    readme = readme_path.read_text(encoding="utf-8")
    previews = {}
    for key, source, name, language in sources:
        content = (generated / source).read_text(encoding="utf-8").rstrip() + "\n"
        start = "<!-- demo-preview:{}:start -->".format(key)
        end = "<!-- demo-preview:{}:end -->".format(key)
        if readme.count(start) != 1 or readme.count(end) != 1:
            raise ValueError("Expected exactly one preview marker pair for " + key)
        a = readme.index(start) + len(start)
        b = readme.index(end)
        if b < a:
            raise ValueError("Reversed preview markers for " + key)
        readme = readme[:a] + "\n\n```" + language + "\n" + content + "```\n\n" + readme[b:]
        previews[name] = content
    output.mkdir(parents=True, exist_ok=True)
    for name, content in previews.items():
        (output / name).write_text(content, encoding="utf-8")
    readme_path.write_text(readme, encoding="utf-8")


if __name__ == "__main__":
    refresh(Path(__file__).resolve().parents[2])
    print("Refreshed README and three saved demo previews.")
