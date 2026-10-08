# sqlapp documentation

Use this page to choose the shortest path through the sqlapp documentation.
sqlapp can be used as a Gradle plugin, as Java libraries, or as command classes
embedded in another tool.

New to sqlapp? See [what you can build and why it helps](getting-started/use-cases.md)
for three workflows, example outputs, and their operational boundaries.

## Documentation sections

| Section | Contents |
|---|---|
| [Getting started](getting-started/README.md) | Java, Maven, command, and Gradle entry points |
| [Schema](schema/README.md) | Shared model, viewpoints, HTML documentation, and SQL workflows |
| [Data](data/README.md) | Conversion, bulk insert/upsert, and hierarchical JDBC processing |
| [Migration](migration/README.md) | Assessment, resumable jobs, verification, recovery, and snapshots |
| [Gradle plugin](gradle-plugin/README.md) | Task configuration and reference |
| [Development](development/README.md) | Architecture, builds, roadmap, and database verification |

Compatibility, dependencies, logging, and upgrade guidance remain shared
references at the documentation root. Old page paths retain links to the current guides.

## Start here

| Goal | Start with | Continue with |
|---|---|---|
| Understand what sqlapp is useful for | [Use cases and example outputs](getting-started/use-cases.md) | [Getting started](getting-started/README.md) |
| See three workflows and actual outputs in one run | [Guided demo tour](getting-started/demo-tour.md) | [Use cases](getting-started/use-cases.md) |
| Try sqlapp without a database | [Offline customer/order demo](getting-started/offline-demo.md) | [HTML documentation](schema/html-documentation.md) |
| Add sqlapp tasks to a Gradle build | [Gradle plugin getting started](gradle-plugin/getting-started.md) | [Task reference](gradle-plugin/task-reference.md) |
| Run the companion example | [Runnable Gradle example map](gradle-plugin/example-project.md) | [`sqlapp-gradle-example`](https://github.com/satotatsu/sqlapp-gradle-example) |
| Use the Schema model or SQL APIs from Java | [Java API getting started](getting-started/java-api.md) | [Published artifacts](artifacts.md) |
| Add sqlapp libraries to Maven | [Maven getting started](getting-started/maven.md) | [Java API getting started](getting-started/java-api.md) |
| Choose a database dialect and JDBC driver | [Published artifacts](artifacts.md) | [Compatibility matrix](compatibility.md) |
| Diagnose a Gradle task | [Gradle plugin troubleshooting](gradle-plugin/troubleshooting.md) | [Building and testing](development/build-and-test.md) |
| Configure logs or collect failure evidence | [Logging and diagnostics](logging-and-diagnostics.md) | [Gradle plugin troubleshooting](gradle-plugin/troubleshooting.md) |
| Upgrade sqlapp dependencies | [Upgrading sqlapp](upgrading.md) | [Compatibility matrix](compatibility.md) |
| Contribute to sqlapp | [Architecture](development/architecture.md) | [Building and testing](development/build-and-test.md) and [CONTRIBUTING](../CONTRIBUTING.md) |

Examples currently use sqlapp `0.80.0` or an explicit
`<sqlapp-version>` placeholder. Keep the plugin, core, command, renderer, and
dialect modules on the same sqlapp version.

## Gradle plugin

The [Gradle plugin task guide](gradle-plugin/README.md) is the complete index
for `com.sqlapp.db`. The focused guides are:

- [Getting started](gradle-plugin/getting-started.md): plugin application,
  runtime dependencies, DataSource settings, Schema XML export, and HTML
  generation.
- [Kotlin DSL](gradle-plugin/kotlin-dsl.md): typed `build.gradle.kts`
  equivalents for common setup, lazy properties, and custom task registration.
- [Task reference](gradle-plugin/task-reference.md): registered task names,
  task types, main inputs and outputs, database effects, common properties,
  and DataSource configuration files.
- [Schema XML, SQL, and HTML](gradle-plugin/schema-sql-and-html.md): metadata
  export, comparison, DDL generation, dictionaries, and documentation.
- [HTML database documentation](schema/html-documentation.md): generated pages,
  table DDL, clickable ER diagrams, Mermaid downloads, viewpoints, and output
  layout.
- [Custom tasks](gradle-plugin/custom-tasks-and-migrations.md): data export,
  file conversion, SQL execution, and task registration.
- [Versioned SQL migrations](gradle-plugin/versioned-migrations.md): migration
  extension, planning, checksums, recovery, and environment comparison.
- [Normalization and legacy migration](gradle-plugin/normalization-and-legacy-migration.md):
  reviewable normalization and legacy-system extraction/load workflows.
- [Database migration assessment](gradle-plugin/database-migration-assessment.md):
  Access source diagnosis, optional data checks, target mapping and DDL
  verification for [Oracle](gradle-plugin/access-oracle-migration-assessment.md)
  or [SQL Server](gradle-plugin/access-sqlserver-migration-assessment.md).
- [Oracle migration assessment](gradle-plugin/oracle-migration-assessment.md):
  offline preflight, source validation, and target review.
- [Troubleshooting](gradle-plugin/troubleshooting.md): task discovery,
  runtime classpath, dialect selection, DataSource files, paths, and outputs.

The companion
[`sqlapp-gradle-example`](https://github.com/satotatsu/sqlapp-gradle-example)
contains runnable Groovy DSL configurations. The
[example map](gradle-plugin/example-project.md) connects its tasks and files to
the relevant guides.

## Java libraries

- [Maven getting started](getting-started/maven.md) provides a complete Java 21
  POM, dependency scopes, dependency-tree checks, and packaging guidance.
- [Java API getting started](getting-started/java-api.md) shows Schema model
  construction, XML round trips, JDBC dialect resolution, SQL generation, and
  resource ownership.
- [JDBC tree data sessions](data/jdbc-tree-data-session.md) covers hierarchical data
  processing; see the [COBOL example](data/jdbc-tree-data-cobol-migration-example.md)
  and [real-database verification](development/jdbc-tree-data-real-db-verification.md).
- [Schema model](schema/model.md) covers ownership, tables, columns,
  constraints, indexes, relationships, lookup, XML, and mutation rules.
- [Command API getting started](getting-started/command-api.md) covers direct
  Java command execution, DataSource ownership, transactions, and failures.
- [Logging and diagnostics](logging-and-diagnostics.md) separates command,
  Log4j, and Gradle output and lists safe diagnostic evidence.
- [Upgrading sqlapp](upgrading.md) provides dependency, Schema XML, generated
  SQL, database-workflow, and rollback checks for a version change.
- [Published artifacts and dependency selection](artifacts.md) lists the core,
  command, renderer, test, plugin, and database-dialect artifacts with Gradle
  and Maven examples, plus the files attached to each module publication.
- [Data converters](data/converters.md) documents the `sqlapp-core` value conversion
  facilities.
- [Schema viewpoints](schema/viewpoints.md) describes reusable named table
  selections shared by documentation and loader workflows.

API documentation focuses on stable entry points and operational behavior.
For an API not covered here, use its module source and tests as the current
behavioral reference and report the missing use case when opening an issue.

## Database support

- [Compatibility and database verification matrix](compatibility.md) records
  Java and Gradle baselines, implemented dialect version boundaries, JDBC
  versions used by the build, and real-engine test evidence.
- [Published artifacts](artifacts.md#database-dialect-artifacts) maps each
  database family to its dialect artifact and current JDBC dependency behavior.
- [Dialect enhancement continuation](development/dialect-enhancement-continuation.md) is a
  contributor-oriented record of implementation scope, limitations, and
  verification commands for ongoing dialect work.

An artifact's presence means sqlapp has an implementation for that database
family. Check the compatibility evidence and feature limitations before using
a dialect with a specific server version in production.

## Data movement and migration

Start with the [migration workflow index](migration/README.md) to choose between
bulk insert/upsert, resumable migration, multi-table jobs, verification and
recovery, snapshots, assessment, and legacy loading.

- [Bulk insert and upsert](data/bulk-insert.md): Java API and vendor providers.
- [Verification and recovery](migration/verification-and-recovery.md): Java
  quick start, verification, repair, and advanced operations.
- [Repair planning](migration/repair.md) and [cutover approval](migration/cutover.md):
  detailed review, evidence, and execution boundaries.
- [Gradle migration tasks](gradle-plugin/bulk-migration.md): job configuration,
  approval gates, leases, execution evidence, and repair.
- [Versioned SQL migrations](gradle-plugin/versioned-migrations.md#versioned-migrations).
- [Normalization and legacy migration](gradle-plugin/normalization-and-legacy-migration.md).

Database-changing examples must be tried against a disposable or explicitly
authorized database first. Review generated SQL, task database effects,
transaction boundaries, and restart behavior before production use.

## Project internals and contribution

- [Architecture](development/architecture.md) explains module boundaries, the Schema model,
  dialect discovery, command and Gradle layers, and dependency direction.
- [Building and testing](development/build-and-test.md) gives the Java 21 setup, focused
  Gradle commands, integration-test boundaries, and dependency diagnostics.
- [Roadmap](development/roadmap.md) records current feature direction and known work areas.
- [CONTRIBUTING](../CONTRIBUTING.md) contains repository contribution and
  validation rules.

sqlapp is licensed under the
[GNU Lesser General Public License, version 3 or later](../LICENSE.md).

## Documentation conventions

- Shell examples use `./gradlew`; use `.\gradlew.bat` in Windows PowerShell.
- Replace angle-bracket placeholders such as `<sqlapp-version>` and
  `<jdbc-driver-version>` before running an example.
- Relative file paths in Gradle examples are resolved in the owning project;
  multi-project builds should qualify both project and task where needed.
- Credentials belong in environment variables, untracked local files, or a
  secret provider. Do not commit them with example configuration.
- Statements about real database support are limited to the evidence recorded
  in the compatibility matrix.

## Maintaining documentation

- Keep the root README focused on the product and primary entry points.
- Use this index for the full documentation map and workflow indexes for each topic.
- Put introductory Java/Maven/command guides in `getting-started/`, Schema and
  HTML guides in `schema/`, data processing in `data/`, and contributor material
  in `development/`. Keep cross-cutting operational references at the root.
- Keep shared migration semantics and Java APIs under `migration/`; keep Gradle
  task configuration under `gradle-plugin/`.
- Keep task names, types, inputs, outputs, and database effects in the Gradle
  task reference, and link to it from workflow guides.
- Split guides at workflow boundaries when details obscure the common path.
  Preserve examples, safety requirements, and failure behavior.
- When moving sections, check relative links and retain old heading anchors
  with links to the new location for existing bookmarks.
- Run the [offline documentation checker](development/build-and-test.md#documentation-checks)
  after updating links, headings, indexes, or page locations.
