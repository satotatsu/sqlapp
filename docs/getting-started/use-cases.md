# Choose a sqlapp workflow

[Documentation index](../README.md) · [Getting started](README.md)

Start with the outcome you need. The examples below show representative inputs
and outputs; they are illustrations, not evidence of a run against your database.
The linked guides contain configuration, required dependencies, and limitations.

## Understand and share a database

Use this when a team needs to browse an existing database's tables and
relationships, or retain a reviewable description of its structure.

**Input:** database metadata, or an already saved sqlapp Schema XML snapshot.
**Output:** Schema XML and a static HTML reference with SVG ER diagrams,
downloadable Mermaid sources, and table DDL.

```text
Database metadata -> Schema XML -> HTML reference + ER diagrams + DDL
```

With the [Gradle getting-started configuration](../gradle-plugin/getting-started.md),
run `./gradlew generateHtmlDocs` and open `build/docs/database/index.html`.
For example, a model with `CUSTOMER` and `ORDER` tables and a modeled foreign
key lets readers follow their relationship in the ER diagram and open the
corresponding table definition. The repository includes a
[Mermaid example](../examples/sqlapp-er-diagram.mmd) showing customer/order
relationships and additional inheritance and partition relationships.

Representative output layout:

```text
build/schema/Catalog.xml
build/docs/database/
  index.html             Catalog overview
  relationships.html     ER diagrams and viewpoint tabs
  tables.html            Table list
  tables/                Table detail and DDL pages
  diagrams/              SVG diagrams and Mermaid sources
```

**Why this is useful:** the same captured model feeds the reference pages,
diagrams, and SQL. The HTML site can be shared as a static artifact without
giving readers database access. Logical names, virtual foreign keys, and
[viewpoints](../schema/viewpoints.md) can make a large model easier to navigate.

**Boundary:** the output describes captured metadata. Missing privileges or
filtered exports can omit objects and relationships. Use `dumpRows = false`
for a structure-only export; row-bearing XML can expose those values in HTML.
See [HTML documentation](../schema/html-documentation.md).

## Review and manage schema changes

Use this when reviewers need to understand a structural change before SQL is
applied, or when deployments use a history of versioned SQL scripts.

**Input:** before/after Schema XML snapshots, or versioned SQL files.
**Output:** logged schema differences, generated change SQL files, or migration
plans and execution history, depending on the selected task.

```text
Before XML + after XML -> comparison -> generated SQL -> review
Reviewed versioned SQL -> migration plan -> execution and history
```

For example, when the desired model adds `CUSTOMER.EMAIL`, compare snapshots
with `diffSchemaXml`, then generate the corresponding change SQL with
`generateDiffSql`. Review the generated SQL for the chosen database dialect
before placing it in your deployment workflow. The comparison task logs its
result; it does not itself write migration SQL.

**Why this is useful:** the shared model keeps comparison and database-specific
SQL generation connected. Gradle tasks let a build produce artifacts that
reviewers can inspect before a separate application step.

**Boundary:** generation does not apply SQL. Destructive changes and vendor DDL
transaction behavior still need review. Versioned SQL migration is a separate
workflow; comparison and generation do not automatically register or execute
a versioned migration.

Continue with [Schema comparison and SQL generation](../gradle-plugin/schema-sql-and-html.md)
or [Versioned SQL migrations](../gradle-plugin/versioned-migrations.md).

## Move related data and verify it

Use this when related tables must be transferred with progress tracking and a
way to identify mismatches, resume an explicitly configured migration, or
prepare a reviewed repair.

**Input:** source and target data sources and a Schema model describing the
tables and relationships, plus any required mappings and fingerprints.
**Output:** detailed migration and verification results; optional persisted
plans, checkpoints, execution reports, and repair approval artifacts.

The common Java entry point performs UPSERT followed by verification:

```java
import com.sqlapp.data.db.command.migration.bulk.BulkMigration;

// Supply configured data sources and the Schema model for your migration.
BulkMigration migration = BulkMigration.of(sourceDataSource, targetDataSource, schema);
BulkMigration.Execution execution = migration.run();
```

For a modeled `CUSTOMER`/`ORDER` relationship, the job orders the parent table
before the child. Verification compares row counts and normalized hashes over
ordered chunks. A mismatch fails `run()` while retaining detailed results and
the committed migration result. A repair plan can then identify chunks for
review before a separately approved replay.

**Why this is useful:** transfer, verification, and repair share planning and
validation components. Callers retain evidence of partial completion rather
than treating a failed job as if no rows were committed.

**Boundary:** a multi-table job is not one atomic transaction. The common
facade does not enable resume by default; resume requires stable source/target
fingerprints. Keyset sources require suitable unique keys. Verification across
independent databases does not establish a common point-in-time snapshot.
Repair does not delete extra target rows, and its transaction guarantees depend
on the provider. Use the linked guides to select these controls deliberately.

Continue with the [Java migration entry point](../migration/verification-and-recovery.md#quick-start),
[Gradle migration configuration](../gradle-plugin/bulk-migration.md), or
[repair planning](../migration/repair.md). For an Access source, start with
[migration assessment](../gradle-plugin/database-migration-assessment.md) and
the [initial-load workflow](../migration/access-initial-load.md).

## Choose an integration

| Your environment | Entry point |
|---|---|
| A Gradle build or build pipeline | [Gradle plugin](../gradle-plugin/getting-started.md) |
| An application constructing or transforming models | [Java API](java-api.md) |
| Maven dependency management | [Maven setup](maven.md) |
| A Java tool embedding file and database commands | [Command API](command-api.md) |

Use the [compatibility matrix](../compatibility.md) to check version boundaries
and recorded database verification. Dialect availability alone does not establish
support for every operation on every server version.
