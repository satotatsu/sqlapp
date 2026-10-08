# Custom tasks and versioned migrations

[Task guide](README.md) · [Runtime and connection setup](getting-started.md)

This guide covers custom task registration, data export, file conversion, and
SQL execution. For the migration extension, planning, checksums, failure
recovery, and environment comparison, use [Versioned SQL migrations](versioned-migrations.md).

The plugin registers the names listed in the task guide. Other public task
classes are available for registration with a project-specific name. All task
types below are in `com.sqlapp.gradle.plugins`; applying the plugin makes
those classes available to a Groovy build script.

The companion example registers `exportData`, `importData`, `toExcel`,
`toJson`, `toYaml`, `toToml`, `toCsv`, `generateDataConfig`, `generateData`,
and several query tasks. See the [example project map](example-project.md) for
the source locations and corresponding documentation.

## Export table data

```groovy
import com.sqlapp.gradle.plugins.ExportDataTask

tasks.register('exportCustomerData', ExportDataTask) {
    dataSource {
        jdbcUrl = providers.environmentVariable('SQLAPP_JDBC_URL')
        username = providers.environmentVariable('SQLAPP_DB_USER')
        password = providers.environmentVariable('SQLAPP_DB_PASSWORD')
    }
    includeSchemas.add('public')
    includeTables.add('customer')
    outputDirectory = layout.buildDirectory.dir('data/customer')
    outputFileType = 'csv'
    csvEncoding = 'UTF-8'
}
```

Run `./gradlew exportCustomerData`. This task reads table data from the
configured database. It also exposes `excludeSchemas`, `excludeTables`,
`fetchSize`, `sheetName`, `useSchemaNameDirectory` and `converters` for more
specialized export needs. See [Converters](../data/converters.md) for conversion
behavior. Exported rows are distinct from the metadata-only workflow in the
[schema guide](schema-sql-and-html.md).

## Convert saved data files

```groovy
import com.sqlapp.gradle.plugins.ConvertDataTask

tasks.register('convertDataToJson', ConvertDataTask) {
    directory = layout.projectDirectory.dir('data/input')
    outputDirectory = layout.buildDirectory.dir('data/json')
    outputFileType = 'json'
    recursive = false
    removeOriginalFile = false
}
```

Run `./gradlew convertDataToJson`. `directory` identifies the input directory;
`outputDirectory` identifies the destination. `includes` and `excludes` are
lists of input file patterns inherited from the directory task base class.
This task converts supported data files without a JDBC connection. The
example explicitly retains source files.

## Execute SQL files

```groovy
import com.sqlapp.gradle.plugins.SqlExecuteTask

tasks.register('applyReviewedSql', SqlExecuteTask) {
    directory = layout.projectDirectory.dir('sql/reviewed')
    encoding = 'UTF-8'
    dataSource {
        jdbcUrl = providers.environmentVariable('SQLAPP_JDBC_URL')
        username = providers.environmentVariable('SQLAPP_DB_USER')
        password = providers.environmentVariable('SQLAPP_DB_PASSWORD')
    }
}
```

`./gradlew applyReviewedSql` executes the selected SQL against that database.
Unlike `generateSql`, this is a database-changing operation if the files contain
DDL or DML. Use versioned migrations when you need change-history tracking.
`SqlExecuteTask` also exposes `sqlText` for direct SQL and `placeholders`,
`placeholderPrefix` and `placeholderSuffix` for placeholder handling.

## Versioned migrations

See [Versioned SQL migrations](versioned-migrations.md#versioned-migrations).

### Concurrent migration execution

See [Versioned SQL migrations](versioned-migrations.md#concurrent-migration-execution).

### Failure diagnosis and recovery

See [Versioned SQL migrations](versioned-migrations.md#failure-diagnosis-and-recovery).

### Optional checksum validation

See [Versioned SQL migrations](versioned-migrations.md#optional-checksum-validation).

### Check the live schema before migration

See [Versioned SQL migrations](versioned-migrations.md#check-the-live-schema-before-migration).

## Compare migration state across environments

See [Versioned SQL migrations](versioned-migrations.md#compare-migration-state-across-environments).

## Additional task types

These classes are not registered under fixed names by `DbPlugin`:

| Task type | Purpose |
|---|---|
| `ImportDataTask` | Load data files into a database |
| `GenerateDataConfigTask` | Generate test-data configuration |
| `GenerateDataTask` | Generate test data |
| `ConvertGeneratorConfigTask` | Convert generator configuration files |
| `SqlQueryTask` | Execute SQL queries |
| `TableSqlExecuteTask` | Execute table-oriented SQL |
| `DropObjectsTask` | Drop selected database objects |
| `SynchronizeSchemaTask` | Synchronize schema objects |
| `MigrationSeriesDownTask` | Execute series-oriented down migration |
| `UpdateDictionariesTask` | Update documentation dictionaries |
| `AvailableFontsTask` | List available fonts |

Register each with `tasks.register('yourName', TaskType)` and configure its
inputs before execution. Registration does not make database-changing tasks
dependencies of `build`; add task dependencies only when intended.

## Implementation and test references

- [DbPlugin task registration](../../sqlapp-gradle-plugin/src/main/java/com/sqlapp/gradle/plugins/DbPlugin.java)
- [ExportDataTaskTest](../../sqlapp-gradle-plugin/src/test/groovy/com/sqlapp/gradle/plugins/ExportDataTaskTest.groovy)
- [ConvertDataTaskTest](../../sqlapp-gradle-plugin/src/test/groovy/com/sqlapp/gradle/plugins/ConvertDataTaskTest.groovy)
- [SqlExecuteTaskTest](../../sqlapp-gradle-plugin/src/test/groovy/com/sqlapp/gradle/plugins/SqlExecuteTaskTest.groovy)
- [MigrationExtension](../../sqlapp-gradle-plugin/src/main/java/com/sqlapp/gradle/plugins/extension/MigrationExtension.java)
  and [MigrationTaskTest](../../sqlapp-gradle-plugin/src/test/groovy/com/sqlapp/gradle/plugins/MigrationTaskTest.groovy)
