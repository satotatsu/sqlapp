# Gradle plugin task reference

[Task guide](README.md) · [Getting started](getting-started.md) ·
[Custom task examples](custom-tasks-and-migrations.md)

The `com.sqlapp.db` plugin registers the tasks in the first table. Additional
public task types can be registered by a consuming build. Task names and
property names are user-facing API.

“Database effect” describes the task's intended behavior. A read-only task may
still open a connection and execute metadata or SELECT statements. A mutating
task can issue DDL or DML and must be configured with the intended target.

## Tasks registered by `com.sqlapp.db`

| Task name | Task class | Primary configuration | Result | Database effect |
|---|---|---|---|---|
| `countAllTables` | `CountAllTableTask` | `dataSource`, schema/table filters, `outputFormatType` | Row counts on console/output | Read-only |
| `migration` | `MigrationTask` | Shared `migration` extension | Applies pending versioned SQL | Mutates target |
| `migrationValidate` | `MigrationValidateTask` | Shared `migration` extension; existing `sqlDirectory` | Checks recorded up SQL checksums; reports unverified legacy entries | Reads history only; no DDL/DML |
| `migrationPlan` | `MigrationPlanTask` | Shared `migration` extension; existing `sqlDirectory` | Lists pending versions, boundaries and known blockers | Reads history and SQL files only |
| `migrationInsert` | `MigrationInsertTask` | Shared `migration` extension | Inserts migration-history state | Mutates history table |
| `migrationRepair` | `MigrationRepairTask` | Shared `migration` extension | Repairs migration-history state | Mutates history table |
| `generateBulkMigrationOperationalReport` | `GenerateBulkMigrationOperationalReportTask` | `plan`, `status`, optional maintenance/progress state, `targetFile` | JSON operational report | No database access by the task |
| `generateBulkMigrationJobRepairPlanReport` | `GenerateBulkMigrationJobRepairPlanReportTask` | `plan`, `targetFile` | JSON review artifact | No database access by the task |
| `executeBulkMigrationJob` | `ExecuteBulkMigrationJobTask` | Exactly one of `plan` or `configurationFile`; target `dataSource`; source data source for declarative jobs | Migration, checkpoints, optional reports | Mutates target |
| `generateMigrationSnapshotApprovalReport` | `GenerateMigrationSnapshotApprovalReportTask` | `configurationFile`, `targetFile` | Approval JSON | No database access |
| `executeMigrationSnapshot` | `ExecuteMigrationSnapshotTask` | `configurationFile`, source and target data sources | Atomic SCD2 snapshot and optional reports | Mutates target |
| `verifyMigrationSnapshotReport` | `VerifyMigrationSnapshotReportTask` | `reportFile`, `approvalFile`, optional `configurationFile` | Offline validation | No database access |
| `verifyMigrationSnapshotFailureReport` | `VerifyMigrationSnapshotFailureReportTask` | `reportFile`, `approvalFile`, optional `configurationFile` | Offline failure-artifact validation | No database access |
| `exportSchemaXml` | `ExportSchemaXmlTask` | `dataSource`, `outputDirectory`, optional target and filters | Schema XML | Read-only unless vendor metadata access has side effects |
| `exportAccessSchemaXml` | `ExportAccessSchemaXmlTask` | `inputFile`, `outputFile` | Schema XML | Reads Access file |
| `exportSqliteSchemaXml` | `ExportSqliteSchemaXmlTask` | `inputFile`, `outputFile` | Schema XML | Reads SQLite file |
| `diffSchemaXml` | `DiffSchemaXmlTask` | `originalFile`, `targetFile` | Logged schema differences | No database access |
| `generateDiffSql` | `GenerateDiffSqlTask` | `originalFile`, `targetFile`, SQL output properties, `withVersionDown` | Versioned difference SQL or console SQL | No database access |
| `generateSql` | `GenerateSqlTask` | `targetFile`, SQL type/options and output properties | Generated SQL files or console SQL | No database access |
| `generateHtmlDocs` | `GenerateHtmlDocsTask` | `targetFile`, `outputDirectory`, optional dictionaries/viewpoints/foreign keys | HTML and ER diagrams | No database access |
| `firstNormalForm` | `FirstNormalFormTask` | `targetFile`, `outputDirectory`, normalization options | Transformed Schema XML and optional mapping | No database access |
| `generateNormalizationPlan` | `GenerateNormalizationPlanTask` | `targetFile`, `outputDirectory`, optional mapping and analysis thresholds | YAML plan and optional preview Schema XML | No database access |
| `columnRuleTransform` | `ColumnRuleTransformTask` | `targetFile`, `rulesFile`, `outputDirectory` | Transformed Schema XML and optional mapping | No database access |
| `pliSchemaImport` | `PliSchemaImportTask` | `configurationFile`, `targetFile`, `outputDirectory` | Schema XML and optional migration mapping | No database access |
| `generateLegacyMigrationContract` | `GenerateLegacyMigrationContractTask` | `mappingFile`, `outputDirectory` | Extraction/load contract | No database access |
| `generatePliCsvExtractor` | `GeneratePliCsvExtractorTask` | `contractFile`, `outputDirectory` | PL/I CSV extraction artifacts | No database access |
| `generateLegacyRdbLoader` | `GenerateLegacyRdbLoaderTask` | `contractFile`, `schemaFile`, `outputDirectory`, database identity/options | Load plan and optional runner template | No database access |
| `loadLegacyHierarchy` | `LoadLegacyHierarchyTask` | `loadPlanFile`, `schemaFile`, `dataSource` | Executes hierarchy load plan | Mutates target |

The source of truth for fixed registration is
[`DbPlugin`](../../sqlapp-gradle-plugin/src/main/java/com/sqlapp/gradle/plugins/DbPlugin.java).

## Shared task properties

All command-backed tasks inherit these properties from `AbstractTask`:

| Property | Gradle type | Purpose |
|---|---|---|
| `runtimeClasspath` | `ConfigurableFileCollection` | Dialects, JDBC drivers, and other runtime implementation classes |
| `debug` | `Property<Boolean>` | Enables command/task diagnostic output where supported |
| `parameters` | `MapProperty<String, Object>` | Command context parameters and placeholders |
| `consoleOutputLevel` | `Property<String>` | Command console-output level |

The Java plugin's resolvable `runtimeClasspath` is added automatically when it
exists. A custom configuration can be added explicitly for builds without the
Java plugin.

## Database connection properties

Tasks implementing `DataSourceTaskProperty` expose `dataSource { ... }`.
Declarative bulk and snapshot tasks may additionally expose a separate
`sourceDataSource { ... }`.

| Property | Gradle type | Purpose/default |
|---|---|---|
| `jdbcUrl` | `Property<String>` | JDBC connection URL |
| `driverClassName` | `Property<String>` | Optional explicit JDBC driver class |
| `username`, `password` | `Property<String>` | Credentials |
| `catalog`, `schema` | `Property<String>` | Optional default catalog/schema |
| `properties` | `ConfigurableFileCollection` | Hikari-compatible configuration files |
| `maximumPoolSize` | `Property<Integer>` | Extension fallback is 5 |
| `minimumIdle` | `Property<Integer>` | Extension fallback is 0 |
| `autoCommit` | `Property<Boolean>` | Optional connection setting |
| `connectionTimeout`, `validationTimeout`, `idleTimeout`, `maxLifetime` | `Property<Long>` | Optional Hikari timing settings |

Explicit extension values are applied after configuration-file values. Keep
credentials outside committed build files.

### Configure a data source from a file

Use the `properties(...)` method inside `dataSource` to load one or more
connection files:

```groovy
tasks.named('exportSchemaXml') {
    dataSource {
        properties file('src/main/config/local/dataSource.properties')
    }
    dumpRows = false
    outputDirectory = layout.buildDirectory.dir('schema')
}
```

The corresponding Java-properties file can contain Hikari property names:

```properties
driverClassName=org.postgresql.Driver
jdbcUrl=jdbc:postgresql://localhost:5432/app
username=app_user
password=local_password
maximumPoolSize=2
minimumIdle=0
```

The loader recognizes `.properties`, Java-properties `.xml`, `.json`, `.yaml`,
and `.yml`. Multiple files can be supplied; their maps are merged in the order
processed. Values assigned directly in the `dataSource` block are applied
after loaded files and therefore override them:

```groovy
dataSource {
    properties file('src/main/config/common/dataSource.yaml')
    properties file('src/main/config/local/dataSource.properties')
    username = providers.environmentVariable('SQLAPP_DB_USER')
    password = providers.environmentVariable('SQLAPP_DB_PASSWORD')
}
```

The same form is used by custom tasks such as `ExportDataTask`,
`ImportDataTask`, and `SqlExecuteTask`. The companion example stores the path
in `gradle.properties` and reuses it:

```properties
dataSourceProperties=./src/main/config/local/dataSource.properties
```

```groovy
dataSource {
    properties "${dataSourceProperties}"
}
```

Keep the local connection file out of version control. The current loader skips
a missing path, directory, or unrecognized extension, so validate the file path
in CI instead of assuming that declaring it proves it was loaded. A task that
needs a connection will still fail later if required JDBC settings are absent.

## Schema and table selection

Database and SQL-generation tasks reuse these list properties:

| Property | Gradle type | Purpose |
|---|---|---|
| `includeSchemas` | `ListProperty<String>` | Include matching schemas |
| `excludeSchemas` | `ListProperty<String>` | Exclude matching schemas |
| `includeTables` | `ListProperty<String>` | Include matching tables |
| `excludeTables` | `ListProperty<String>` | Exclude matching tables |
| `includeObjects` | `ListProperty<String>` | Include matching non-table object targets where supported |
| `excludeObjects` | `ListProperty<String>` | Exclude matching non-table object targets where supported |
| `onlyCurrentCatalog` | `Property<Boolean>` | Limit metadata/action to the current catalog |
| `onlyCurrentSchema` | `Property<Boolean>` | Limit metadata/action to the current schema |

An empty include list normally means “no include restriction.” Exclusions are
then applied by the owning command. Exact name matching and qualification rules
remain database/object specific.

For `exportSchemaXml`, `target` selects the metadata reader and XML root type.
See [Choose the metadata target](schema-sql-and-html.md#choose-the-metadata-target)
for common values, singular versus collection output, and dialect limitations.

## File and directory properties

Common file-processing properties are:

| Property | Gradle type | Purpose |
|---|---|---|
| `targetFile` | `RegularFileProperty` | Primary input or target Schema XML, depending on task |
| `originalFile` | `RegularFileProperty` | Original/baseline Schema XML |
| `directory` | `DirectoryProperty` | Input directory for directory tasks |
| `includes`, `excludes` | `ListProperty<String>` | Gradle file-tree patterns below `directory` |
| `outputDirectory` | `DirectoryProperty` | Generated-file destination |
| `encoding` | `Property<String>` | Text encoding; generated SQL commonly falls back to UTF-8 |
| `csvEncoding` | `Property<String>` | CSV-specific encoding |
| `recursive` | `Property<Boolean>` | Whether file conversion scans recursively |
| `removeOriginalFile` | `Property<Boolean>` | Whether conversion removes its source file |
| `fileDirectory` | `DirectoryProperty` | Supporting resource-file directory |

Property meaning is ultimately defined by the task class. For example,
`targetFile` is an input for SQL generation but an output for some
transformation tasks.

## SQL generation properties

`generateSql` and `generateDiffSql` share:

| Property | Type | Current behavior |
|---|---|---|
| `outputDirectory` | `DirectoryProperty` | Without it, SQL is written to standard output |
| `outputAsMultiFiles` | `Property<Boolean>` | Falls back to `true` |
| `outputFileExtension` | `Property<String>` | SQL filename suffix; current implementation falls back to `.sql` |
| `lastChangeNumber` | `Property<Object>` | Optional initial version number |
| `changeNumberStep` | `Property<Object>` | Falls back to 10 |
| `numberOfDigits` | `Property<Object>` | Falls back to 19 |
| `encoding` | `Property<String>` | Falls back to UTF-8 |
| `schemaOptions` | Nested options | Schema-level DDL behavior |
| `tableOptions` | Nested options | Table/DML behavior |

`generateSql` additionally exposes `sqlType` and schema/table filters.
`generateDiffSql` additionally requires `originalFile` and declares
`withVersionDown` as an input. See
[Schema XML, SQL and HTML tasks](schema-sql-and-html.md) for implementation
limitations around single-file difference output.

## Directory task command-line options

Classes derived from `AbstractDirectoryTask` support these Gradle options in
addition to build-script properties:

```shell
./gradlew <task> --dir path/to/input --include "**/*.csv" --exclude "**/skip-*"
```

`--include` and `--exclude` may be repeated. Patterns are evaluated by the
Gradle file tree rooted at `directory`.

## Migration extension

The registered `migration` task reads the project extension of the same name.

| Property | Type | Purpose |
|---|---|---|
| `dataSource` | Nested configuration | Migration target |
| `sqlDirectory` | `DirectoryProperty` | Version-up SQL |
| `downSqlDirectory` | `DirectoryProperty` | Version-down SQL |
| `setupSqlDirectory` | `DirectoryProperty` | SQL before normal version processing |
| `finalizeSqlDirectory` | `DirectoryProperty` | SQL after normal version processing |
| `fileDirectory` | `DirectoryProperty` | Files referenced by migration SQL/configuration |
| `lastChangeNumber` | `Property<String>` | Optional upper version bound |
| `showVersionOnly` | `Property<Boolean>` | Show current version instead of normal application |
| `withSeriesNumber` | `Property<Boolean>` | Enable series-number behavior |
| `placeholders`, `placeholderPrefix`, `placeholderSuffix` | Properties | Placeholder processing |
| `changeTable` | Nested configuration | Migration-history table and column names |
| `checksumValidation` | `Property<Boolean>` | Default `false`; opt-in checksum recording and validation during migration. Explicit `migrationValidate` checks regardless of this setting |
| `preMigrationSchemaFile` | `RegularFileProperty` | Optional expected live Schema XML; `migration` blocks on breaking drift and `migrationPlan` reports it |

Custom `MigrationDownTask` and `MigrationSeriesDownTask` instances reuse this
extension. See [Custom tasks and versioned migrations](custom-tasks-and-migrations.md).

## Public task types without fixed names

These task classes are available after applying the plugin but are not
registered by `DbPlugin` under a fixed name:

| Task class | Typical purpose | Database effect |
|---|---|---|
| `AvailableFontsTask` | List fonts available to renderers | None |
| `ConvertDataTask` | Convert supported data files | File output; may remove input when configured |
| `ConvertGeneratorConfigTask` | Convert generator configuration formats | File output; may remove input when configured |
| `DropObjectsTask` | Drop selected objects/tables | Mutates target destructively |
| `ExportDataTask` | Export selected table rows | Read-only database; writes files |
| `GenerateDataConfigTask` | Generate data-generator configuration from metadata | Read-only database; writes files |
| `GenerateDataTask` | Generate/load relational test data | Mutates target |
| `ImportDataTask` | Import data files | Mutates target |
| `MigrationDownTask` | Apply down migrations | Mutates target destructively |
| `MigrationSeriesDownTask` | Apply series-based down migrations | Mutates target destructively |
| `SqlExecuteTask` | Execute SQL text or files | Depends on SQL; may mutate target |
| `SqlQueryTask` | Execute and format a query | Intended read-only use |
| `SynchronizeSchemaTask` | Synchronize a target schema | Mutates target |
| `TableSqlExecuteTask` | Execute generated table-oriented SQL | Mutates target |
| `UpdateDictionariesTask` | Generate or update documentation dictionaries | Writes files; may read metadata when configured |

Register a type with a project-specific name:

```groovy
import com.sqlapp.gradle.plugins.ExportDataTask

tasks.register('exportCustomerData', ExportDataTask) {
    // task properties
}
```

Registration alone does not add the task to `build` or make it depend on
another sqlapp task.

## Safety and caching

Command-backed task implementations are disabled for Gradle build caching by
default. Database state and external checkpoint/lease stores can change without
a corresponding Gradle input change.

Before running a mutating task:

1. inspect the resolved target URL and schema filters;
2. confirm the task's input files and task dependencies;
3. review generated SQL, migration plan, or approval artifact where available;
4. use a disposable database first;
5. retain reports and fingerprints needed for diagnosis and audit.

`--dry-run` shows Gradle task ordering. It does not validate database
credentials, inspect SQL semantics, or simulate database changes.

## Detailed workflow references

- [Schema XML, SQL, and HTML](schema-sql-and-html.md)
- [Custom tasks and versioned migrations](custom-tasks-and-migrations.md)
- [Normalization and legacy migration](normalization-and-legacy-migration.md)
- [Bulk migration and type-2 snapshots](README.md#executebulkmigrationjob)
- [Runnable example project](example-project.md)
