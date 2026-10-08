# Try database documentation without a database

[Documentation index](../README.md) · [Getting started](README.md)

This repository demo turns a small, fictional customer/order model into a
browsable HTML reference. It demonstrates the shared model, table DDL, and
relationship diagrams without credentials, a running database, or data rows.

## Run the demo

Use Java 21 and the repository Gradle Wrapper from the repository root:

```shell
./gradlew --init-script docs/examples/offline-html/demo.init.gradle demoDatabaseDocumentation
```

Windows PowerShell:

```powershell
.\gradlew.bat --init-script docs/examples/offline-html/demo.init.gradle demoDatabaseDocumentation
```

The first build may download Gradle and Java library dependencies. With those
dependencies already cached, add `--offline` to prevent dependency downloads.
No database server or JDBC connection is used in either case.

The optional init script registers compilation and execution tasks for this invocation;
ordinary builds do not acquire a new task or dependency. It uses the checked-out
command, renderer, and HSQL dialect modules. The dialect generates table DDL
from the model; it does not open an HSQL database.

## Inspect the result

Open `build/docs/offline-demo/index.html` in a browser. Then:

1. Open the table list and inspect `CUSTOMER` and `CUSTOMER_ORDER`.
2. Open their detail pages to see columns, primary keys, and generated DDL.
3. Open `relationships.html` to see the customer/order foreign key.
4. Follow a table heading in the SVG to its detail page.
5. Download a Mermaid source from the diagram page for reuse in another tool.

The output is a static site. Keep its directory structure intact when sharing
it, because pages and diagrams use relative links.

## See what drives the output

- [catalog.xml](../examples/offline-html/catalog.xml) is the fictional Schema
  model: two tables, five columns, two primary keys, and one foreign key.
- [GenerateDemo.java](../examples/offline-html/GenerateDemo.java) invokes the
  existing `GenerateHtmlDocsCommand` with that XML and an output directory.
- [demo.init.gradle](../examples/offline-html/demo.init.gradle) supplies the
  repository runtime classpath, compiles the example separately from production
  source sets, and launches it with Java 21.

To explore the model, change a column name or length in the sample XML and run
the task again. The table page and its DDL are derived from the same input.
Keep edits to the sample internally consistent, including any key references.

## Continue with your own database

Replace the fictional snapshot with metadata exported from your database using
the [Gradle getting-started guide](../gradle-plugin/getting-started.md).
Use `dumpRows = false` when you need structure-only documentation.

The sample demonstrates documentation generation. Database-specific metadata
coverage and SQL behavior require the matching dialect; the sample does not
establish compatibility with your server. See the
[compatibility matrix](../compatibility.md) and
[HTML documentation guide](../schema/html-documentation.md).
