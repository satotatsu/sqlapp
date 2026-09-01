# Gradle plugin task guide

The `com.sqlapp.db` plugin exposes sqlapp commands as Gradle tasks. This
document is the index and authoritative task-name reference. Runnable project
configurations are maintained in
[`sqlapp-gradle-example`](https://github.com/satotatsu/sqlapp-gradle-example).

## Applying the plugin

```groovy
plugins {
    id 'com.sqlapp.db' version '<sqlapp-version>'
}
```

Use the Gradle Wrapper and Java 21. Run `gradlew tasks` (Windows:
`gradlew.bat tasks`) to inspect the tasks available to a project.

## Tasks registered by the plugin

| Area | Task | Purpose |
|---|---|---|
| Schema inspection | `countAllTables` | Count rows in database tables |
| Schema inspection | `exportSchemaXml` | Export database metadata as sqlapp Schema XML |
| Schema inspection | `exportAccessSchemaXml` | Export an Access MDB/ACCDB file as sqlapp Schema XML |
| Schema inspection | `exportSqliteSchemaXml` | Export a SQLite DB/SQLite/SQLite3 file as sqlapp Schema XML |
| Schema comparison | `diffSchemaXml` | Compare two Schema XML files |
| SQL generation | `generateDiffSql` | Generate SQL from a schema difference |
| SQL generation | `generateSql` | Generate SQL from Schema XML |
| Documentation | `generateHtmlDocs` | Generate HTML documentation and ER diagrams |
| Migration | `migration` | Apply versioned database migrations |
| Migration | `migrationInsert` | Insert migration history |
| Migration | `migrationRepair` | Repair migration history |
| Migration | `executeBulkMigrationJob` | Execute a programmatic plan or declarative migration job |
| Migration | `generateBulkMigrationOperationalReport` | Write a bulk migration plan/status snapshot as JSON |
| Normalization | `generateNormalizationPlan` | Generate reviewable normalization candidates and a preview schema |
| Normalization | `firstNormalForm` | Split repeating column groups and optionally replace composite primary keys |
| Normalization | `columnRuleTransform` | Apply YAML-based column type and naming rules |
| Legacy migration | `pliSchemaImport` | Convert PL/I declarations to Schema XML and migration mapping |
| Legacy migration | `generateLegacyMigrationContract` | Generate the CSV extraction/load contract |
| Legacy migration | `generatePliCsvExtractor` | Generate PL/I CSV extraction artifacts |
| Legacy migration | `generateLegacyRdbLoader` | Generate a restartable hierarchy load plan |
| Legacy migration | `loadLegacyHierarchy` | Execute a generated hierarchy load plan |

Some public task classes, such as data import/export, data generation, format
conversion, SQL execution, and migration-down tasks, are not registered under
a fixed name. A build may register those task types with a project-specific
name. See the runnable examples for their configuration.

### `generateBulkMigrationOperationalReport`

This task is intended for builds that assemble a `BulkMigrationJobPlan` in
Java or a Gradle plugin. Set `plan` and its matching read-only
`BulkMigrationJobStatus`, then set `targetFile`. `maintenanceState` and
`progress` are optional. The task only writes a JSON snapshot; it does not run
the migration, modify checkpoints, or recover maintenance. All complex values
are programmatic properties rather than a second migration-plan file format,
and the task is deliberately not build-cacheable because their stores can
change outside Gradle.

### `executeBulkMigrationJob`

This task synchronously executes either a programmatically assembled
`BulkMigrationJobPlan` or a YAML `configurationFile` against its target
`dataSource`. Specify exactly one of `plan` and `configurationFile`. Declarative
jobs also require `sourceDataSource`; their `schemaFile` is resolved relative
to the YAML file. Database lease mode obtains a separate target connection so
lease transactions never share the connection used for migrated rows. The task
is not build-cacheable because it mutates an external database.

```groovy
executeBulkMigrationJob {
    dataSource {
        url = 'jdbc:postgresql://localhost/app'
        username = providers.gradleProperty('dbUser')
        password = providers.gradleProperty('dbPassword')
    }
    plan = assembledBulkMigrationPlan
    leaseConfiguration = BulkMigrationJobLeaseConfiguration.database('worker-1')
}
```

The equivalent declarative task uses independent source and target connections:

```groovy
executeBulkMigrationJob {
    configurationFile = file('migration/job.yaml')
    sourceDataSource { jdbcUrl = 'jdbc:postgresql://source/app' }
    dataSource { jdbcUrl = 'jdbc:postgresql://target/app' }
}
```

```yaml
schemaFile: schema.xml
lease:
  mode: DATABASE
  ownerId: migration-worker-1
  durationSeconds: 300
# tableName defaults to SQLAPP_BULK_MIGRATION_JOB_LEASE
tasks:
  - id: customers
    table: public.customers
    keysetColumns: [customer_id]
    migrationId: customers-v1
    chunkSize: 10000
    mode: UPSERT
    resume: true
    sourceFingerprint: source-schema-v1
    targetFingerprint: target-schema-v1
    keyColumns: [customer_id]
    duplicateKeyStrategy: LAST
    bulk:
      batchSize: 5000
      keepNulls: true
      tableLock: true
    retry:
      maxRetries: 3
      initialBackoffMillis: 1000
      backoffMultiplier: 2.0
      maxBackoffMillis: 30000
      sqlStates: ['40001']
```

Unqualified table names are accepted only when unique in the Schema XML.
`CUSTOM` duplicate selection remains programmatic because executable selector
code cannot be represented safely in YAML. The nested `bulk` block is shared by
INSERT and UPSERT; the nested `retry` block controls retry of a complete,
transactional chunk and may select transient exceptions, SQLStates, and vendor
error codes.

For `FILE` lease mode, replace `tableName` with `directory`. A relative lease
directory is resolved from the job YAML location. Lease configuration may be
supplied either in YAML or through the task's `leaseConfiguration` property,
but not both.

## Choose a workflow

- Schema XML, SQL, migration, HTML, dictionaries:
  [`sqlapp-gradle-example`](https://github.com/satotatsu/sqlapp-gradle-example)
- Normalization and legacy migration:
  [Normalization and legacy-migration tasks](normalization-and-legacy-migration.md)
- Schema viewpoints:
  [Schema viewpoints](../schema-viewpoints.md)
- Building and testing sqlapp itself:
  [Build and test](../build-and-test.md)

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
