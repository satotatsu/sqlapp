# sqlapp documentation

Use this page to choose the shortest path through the sqlapp documentation.
sqlapp can be used as a Gradle plugin, as Java libraries, or as command classes
embedded in another tool.

## Start here

| Goal | Start with | Continue with |
|---|---|---|
| Add sqlapp tasks to a Gradle build | [Gradle plugin getting started](gradle-plugin/getting-started.md) | [Task reference](gradle-plugin/task-reference.md) |
| Run the companion example | [Runnable Gradle example map](gradle-plugin/example-project.md) | [`sqlapp-gradle-example`](https://github.com/satotatsu/sqlapp-gradle-example) |
| Use the Schema model or SQL APIs from Java | [Java API getting started](java-api-getting-started.md) | [Published artifacts](artifacts.md) |
| Add sqlapp libraries to Maven | [Maven getting started](maven-getting-started.md) | [Java API getting started](java-api-getting-started.md) |
| Choose a database dialect and JDBC driver | [Published artifacts](artifacts.md) | [Compatibility matrix](compatibility.md) |
| Diagnose a Gradle task | [Gradle plugin troubleshooting](gradle-plugin/troubleshooting.md) | [Building and testing](build-and-test.md) |
| Configure logs or collect failure evidence | [Logging and diagnostics](logging-and-diagnostics.md) | [Gradle plugin troubleshooting](gradle-plugin/troubleshooting.md) |
| Upgrade sqlapp dependencies | [Upgrading sqlapp](upgrading.md) | [Compatibility matrix](compatibility.md) |
| Contribute to sqlapp | [Architecture](architecture.md) | [Building and testing](build-and-test.md) and [CONTRIBUTING](../CONTRIBUTING.md) |

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
- [Custom tasks and versioned migrations](gradle-plugin/custom-tasks-and-migrations.md):
  registering task types that have no fixed task name, plus the migration
  extension.
- [Normalization and legacy migration](gradle-plugin/normalization-and-legacy-migration.md):
  reviewable normalization and legacy-system extraction/load workflows.
- [Troubleshooting](gradle-plugin/troubleshooting.md): task discovery,
  runtime classpath, dialect selection, DataSource files, paths, and outputs.

The companion
[`sqlapp-gradle-example`](https://github.com/satotatsu/sqlapp-gradle-example)
contains runnable Groovy DSL configurations. The
[example map](gradle-plugin/example-project.md) connects its tasks and files to
the relevant guides.

## Java libraries

- [Maven getting started](maven-getting-started.md) provides a complete Java 21
  POM, dependency scopes, dependency-tree checks, and packaging guidance.
- [Java API getting started](java-api-getting-started.md) shows Schema model
  construction, XML round trips, JDBC dialect resolution, SQL generation, and
  resource ownership.
- [Schema model](schema-model.md) covers ownership, tables, columns,
  constraints, indexes, relationships, lookup, XML, and mutation rules.
- [Command API getting started](command-api-getting-started.md) covers direct
  Java command execution, DataSource ownership, transactions, and failures.
- [Logging and diagnostics](logging-and-diagnostics.md) separates command,
  Log4j, and Gradle output and lists safe diagnostic evidence.
- [Upgrading sqlapp](upgrading.md) provides dependency, Schema XML, generated
  SQL, database-workflow, and rollback checks for a version change.
- [Published artifacts and dependency selection](artifacts.md) lists the core,
  command, renderer, test, plugin, and database-dialect artifacts with Gradle
  and Maven examples, plus the files attached to each module publication.
- [Data converters](converters.md) documents the `sqlapp-core` value conversion
  facilities.
- [Schema viewpoints](schema-viewpoints.md) describes reusable named table
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
- [Dialect enhancement continuation](dialect-enhancement-continuation.md) is a
  contributor-oriented record of implementation scope, limitations, and
  verification commands for ongoing dialect work.

An artifact's presence means sqlapp has an implementation for that database
family. Check the compatibility evidence and feature limitations before using
a dialect with a specific server version in production.

## Data movement and migration

- [Bulk insert and bulk migration](bulk-insert.md) is the detailed reference
  for insert/upsert behavior, chunking, checkpoints, leases, verification,
  maintenance, recovery, repair, and snapshot execution. It is an advanced
  operational document; start with the Gradle task reference when selecting a
  task.
- [Custom tasks and versioned migrations](gradle-plugin/custom-tasks-and-migrations.md)
  covers the conventional versioned-SQL migration extension and tasks.
- [Normalization and legacy migration](gradle-plugin/normalization-and-legacy-migration.md)
  covers schema normalization and generated legacy load artifacts.

Database-changing examples must be tried against a disposable or explicitly
authorized database first. Review generated SQL, task database effects,
transaction boundaries, and restart behavior before production use.

## Project internals and contribution

- [Architecture](architecture.md) explains module boundaries, the Schema model,
  dialect discovery, command and Gradle layers, and dependency direction.
- [Building and testing](build-and-test.md) gives the Java 21 setup, focused
  Gradle commands, integration-test boundaries, and dependency diagnostics.
- [Roadmap](roadmap.md) records current feature direction and known work areas.
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
