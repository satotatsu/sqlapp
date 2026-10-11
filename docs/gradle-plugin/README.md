# Gradle plugin task guide

The `com.sqlapp.db` plugin exposes sqlapp commands as Gradle tasks. This
document is the workflow index. The [task reference](task-reference.md) owns
task names, types, inputs, outputs, and database effects. Runnable project
configurations are maintained in
[`sqlapp-gradle-example`](https://github.com/satotatsu/sqlapp-gradle-example).
The examples in this guide have also been checked against that project's
`build.gradle` and `gradle.properties` layout.

## Documentation map

Start with [Getting started](getting-started.md) for dependencies, connection
configuration, and a complete Schema XML to HTML workflow. Examples use the
Groovy DSL (`build.gradle`) and describe the implementation in this checkout;
use a plugin release that contains the tasks you need.

| Guide | Contents |
|---|---|
| [Getting started](getting-started.md) | Plugin setup, JDBC runtime, credentials, task dependencies, troubleshooting |
| [Kotlin DSL](kotlin-dsl.md) | Typed `build.gradle.kts` setup, lazy properties, DataSource files, and task registration |
| [Troubleshooting](troubleshooting.md) | Task discovery, classpath, dialect, DataSource files, paths, outputs, and diagnostic commands |
| [Runnable example project](example-project.md) | How the companion project is organized and which task demonstrates each workflow |
| [Task reference](task-reference.md) | Registered names, task classes, primary inputs/outputs, database effects, and common properties |
| [Schema, SQL and HTML](schema-sql-and-html.md) | XML export, comparison, SQL generation, documentation properties and outputs |
| [Custom tasks](custom-tasks-and-migrations.md) | Data export, file conversion, SQL execution and task registration |
| [Versioned SQL migrations](versioned-migrations.md) | Migration extension, planning, concurrency, recovery, checksums and environment comparison |
| [Normalization and legacy migration](normalization-and-legacy-migration.md) | Normalization, PL/I import, extraction contracts and hierarchy loading |
| [Database migration assessment](database-migration-assessment.md) | Generic Access migration diagnosis, mapping, data checks, DDL preview and verification |
| [Access to Oracle](access-oracle-migration-assessment.md) | Oracle-specific mapping, review points and deployment-ready DDL verification |
| [Access to SQL Server](access-sqlserver-migration-assessment.md) | SQL Server-specific mapping, review points and deployment-ready DDL verification |
| [Oracle migration assessment](oracle-migration-assessment.md) | Offline preflight, object inventory, evidence and manual checks for a 26ai target |

- [Bulk migration tasks](bulk-migration.md): jobs, approval gates, leases, verification, and repair.
- [Snapshot task](snapshot.md): SCD2 configuration and execution reports.
- [Migration reports](migration-reports.md): operational and repair-plan reports.
- [Migration concepts and Java APIs](../migration/README.md): shared workflow reference.

## Applying the plugin

```groovy
plugins {
    id 'com.sqlapp.db' version '<sqlapp-version>'
}
```

Use the Gradle Wrapper and Java 21. Run `gradlew tasks` (Windows:
`gradlew.bat tasks`) to inspect the tasks available to a project.

## Tasks registered by the plugin

See the [task reference](task-reference.md) for the complete catalog of task
names, types, inputs, outputs, and database effects.

### `generateBulkMigrationOperationalReport`

See [Migration report Gradle tasks](migration-reports.md#generatebulkmigrationoperationalreport).

### `generateBulkMigrationJobRepairPlanReport`

See [Migration report Gradle tasks](migration-reports.md#generatebulkmigrationjobrepairplanreport).

### `executeMigrationSnapshot`

See [Snapshot Gradle task](snapshot.md#executemigrationsnapshot).

### `executeBulkMigrationJob`

See [Bulk migration Gradle tasks](bulk-migration.md#executebulkmigrationjob).

## Choose a workflow

- Schema XML, SQL, migration, HTML, dictionaries:
  [`sqlapp-gradle-example`](https://github.com/satotatsu/sqlapp-gradle-example)
- Normalization and legacy migration:
  [Normalization and legacy-migration tasks](normalization-and-legacy-migration.md)
- Schema viewpoints:
  [Schema viewpoints](../schema/viewpoints.md)
- Building and testing sqlapp itself:
  [Build and test](../development/build-and-test.md)

## Configuration conventions

Task names and properties are public user-facing APIs. File and directory
properties use Gradle's lazy property types, but Groovy build scripts may use
normal assignment syntax:

```groovy
generateNormalizationPlan {
    targetFile = file('schemas/legacy.xml')
    outputDirectory = file('build/normalization-plan')
}
```

A value listed as a convention is used only when the build does not configure
the property. Required files and directories must be configured before task
execution. Relative paths are resolved against the Gradle project directory.

### Export an Access file

```groovy
exportAccessSchemaXml {
    inputFile = file('customer.accdb')
    outputFile = layout.buildDirectory.file('schema/customer.xml')
    schemaName = 'public'
    dumpRows = true
    includeRowDumpTables.addAll('顧客', '受注')
}
```

`inputFile` and `outputFile` are required. `schemaName` and the row filters are
optional; `dumpRows` defaults to `true`. Encrypted files and Access complex
columns such as attachments and multi-value fields are not supported.

### Export a SQLite file

```groovy
exportSqliteSchemaXml {
    inputFile = file('customer.sqlite3')
    outputFile = layout.buildDirectory.file('schema/customer.xml')
    schemaName = 'public'
    dumpRows = true
    includeRowDumpTables.addAll('顧客', '受注')
}
```

`inputFile` and `outputFile` are required. Files ending in `.db`, `.sqlite`,
or `.sqlite3` are supported; a valid unencrypted SQLite header also allows an
arbitrary extension. `schemaName` renames the exported Schema model; it does
not select an attached SQLite database. Row filters are optional and
`dumpRows` defaults to `true`. The source file is opened read-only. Encrypted
SQLite files are diagnosed explicitly but require a separate encryption-aware
JDBC driver and are not decrypted by this task.

Java callers that need an attached database can use
`SqliteFileLoader.loadSchema(primaryFile, databaseName, attachments)`. Each
lazy row-reading connection reapplies the same attachments, so metadata and
row data are read from the selected database consistently.

## Documentation maintenance

When a task is added or its user-facing properties change:

1. Update this index and the applicable task reference.
2. Update a runnable configuration in `sqlapp-gradle-example`.
3. Verify property names, types, conventions, required inputs, and outputs
   against the task implementation.
