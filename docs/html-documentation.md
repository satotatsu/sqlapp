# HTML database documentation

sqlapp generates a browsable HTML reference from a saved Schema XML file or an
in-memory `Catalog`. The generated site is a static directory: it needs no
database connection or application server and can be archived, published by a
static-site host, or reviewed as a build artifact.

Schema XML is the canonical machine-readable snapshot. HTML, SVG, Mermaid and
DDL are derived views of the same Schema model.

## What the generated site contains

The site includes an overview and navigation for every object type present in
the input. Depending on the database and captured metadata, this can include:

- schemas, tables, columns, indexes and relationships;
- views, materialized views and logs, external tables and table links;
- functions, procedures, packages, triggers, rules and events;
- sequences, synonyms, database links, domains and user-defined types;
- tablespaces, partition functions and partition schemes;
- XML schemas, assemblies, operators, operator classes and dimensions;
- users, roles, privileges and database settings.

Empty database-specific sections are omitted. Object lists support browser-side
search, sorting and column filtering where the generated table enables them.
Physical and logical names and remarks are shown when they are present in the
Schema model or supplied dictionary files.

## Table details

Each table has a detail page. Its **Base** tab contains the table and column
definition together with available keys, constraints, indexes, statements and
database-specific metadata. Additional tabs appear only when their data exists,
including rows captured in Schema XML, tablespace information, partitioning,
temporal definitions, vector metadata, statistics and specifics.

The **DDL** tab always shows the table's `CREATE` DDL. sqlapp selects the dialect
from catalog product metadata. If the XML does not contain product metadata, the
default dialect is used. The DDL is generated from the same table model and is
escaped before insertion into HTML.

Table detail pages deliberately omit relationship and viewpoint diagrams. Use
the relationship page for navigation across tables, then open a table detail for
its complete definition and DDL.

If Schema XML contains table rows, those values can appear in the generated
site. Treat documentation generated from row-bearing XML according to the same
data-handling rules as its source file.

## ER diagrams and navigation

The relationship page contains SVG diagrams for the catalog and configured
viewpoints. Schema detail pages can also contain their schema-level diagrams.
When available, compact and detailed diagrams and physical-name and logical-name
variants are presented separately.

Click a table heading in an embedded SVG to open that table's detail page. The
diagrams include modeled foreign keys and can also include logical foreign keys
loaded from `foreignKeyDefinitionDirectory`. Inheritance and partition-parent
relationships are rendered when both endpoint tables are selected.

Every displayed SVG has a corresponding UTF-8 Mermaid source file in the
`diagrams/` directory and a **Mermaid (.mmd)** download link. Mermaid output is
intended for exchange, editing and rendering by other tools; the embedded SVG
provides navigation inside the generated HTML site.

## Viewpoints and dictionaries

A viewpoint is a named selection of Schema tables. Supply `viewpointsFile` to
add one tab per viewpoint to the relationship page, or also set `viewpointId`
to generate documentation for one selected table set. The underlying Schema XML
is not changed. See [Schema viewpoints](schema-viewpoints.md) for the YAML format
and name-resolution rules.

Dictionary files can add or override logical names and remarks used by the
documentation. Logical foreign-key definitions can describe relationships that
are known by the application but are not declared as database constraints.

## Generate with Gradle

```groovy
tasks.named('generateHtmlDocs') {
    targetFile = layout.projectDirectory.file('schemas/Catalog.xml')
    outputDirectory = layout.buildDirectory.dir('docs/database')

    // Optional inputs
    dictionaryFileDirectory = layout.projectDirectory.dir('dictionaries')
    foreignKeyDefinitionDirectory = layout.projectDirectory.dir('foreign-keys')
    viewpointsFile = layout.projectDirectory.file('viewpoints.yaml')
}
```

Run:

```text
./gradlew generateHtmlDocs
```

Open `index.html` in the configured output directory. The minimum configuration
needs only `targetFile` and `outputDirectory`; all enrichment inputs are
optional. Generation reads the saved XML and does not connect to the database.

See [Schema XML, SQL, and HTML tasks](gradle-plugin/schema-sql-and-html.md) for
the full Gradle property table and [Command API getting started](command-api-getting-started.md#generate-html-without-a-database-connection)
for direct Java use.

## Generated files

The output directory is a self-contained static site. Its main artifacts are:

| Path | Contents |
|---|---|
| `index.html` | Catalog overview and site entry point |
| `relationships.html` | Catalog and viewpoint ER diagrams |
| `tables.html`, `columns.html`, `indexes.html` | Cross-schema object lists |
| `tables/*.html` | Table detail and DDL pages |
| `schemas/*.html` | Schema details and schema ER diagrams |
| `diagrams/*.svg` | Embedded, clickable ER diagrams |
| `diagrams/*.mmd` | Downloadable Mermaid ER sources |
| Other plural directories and HTML files | Lists and details for object types present in the Schema model |

Keep the directory structure intact when publishing or archiving the output,
because pages, styles, scripts and diagrams use relative links.

## Scope and limitations

- The generated HTML is a read-only representation; edit Schema XML, dictionary,
  logical-relation or viewpoint inputs and regenerate it to make changes.
- Mermaid preserves schema relationships and identifiers but does not reproduce
  SVG layout or styling exactly.
- A partition that is only opaque vendor metadata and is not a Schema table is
  not synthesized as an ER entity.
- The site documents only metadata and rows present in its input snapshot.

