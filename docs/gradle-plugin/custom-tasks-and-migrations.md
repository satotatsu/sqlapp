# Custom tasks and versioned migrations

[Task guide](README.md) · [Runtime and connection setup](getting-started.md)

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
specialized export needs. See [Converters](../converters.md) for conversion
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

`migration` is both a registered task name and the name of a project extension.
Configure the extension explicitly to distinguish it from a task configuration:

```groovy
import com.sqlapp.gradle.plugins.extension.MigrationExtension

extensions.configure(MigrationExtension) {
    sqlDirectory = layout.projectDirectory.dir('migration/up')
    encoding = 'UTF-8'
    dataSource {
        jdbcUrl = providers.environmentVariable('SQLAPP_JDBC_URL')
        username = providers.environmentVariable('SQLAPP_DB_USER')
        password = providers.environmentVariable('SQLAPP_DB_PASSWORD')
    }
}
```

Place reviewed SQL files in `migration/up`, using numeric change prefixes,
for example `0000000010_create_customer.sql` and
`0000000020_add_customer_status.sql`. `./gradlew migration` applies versioned
changes using the configured database and migration history.

| Extension property | Type | Purpose |
|---|---|---|
| `sqlDirectory` | `DirectoryProperty` | Up migration SQL directory |
| `downSqlDirectory` | `DirectoryProperty` | Optional down migration SQL directory |
| `setupSqlDirectory` | `DirectoryProperty` | Optional SQL before version-up processing |
| `finalizeSqlDirectory` | `DirectoryProperty` | Optional SQL after version-up processing |
| `lastChangeNumber` | `Property<String>` | Optional upper change number, converted to `Long` |
| `showVersionOnly` | `Property<Boolean>` | Request version display instead of normal migration processing |
| `checksumValidation` | `Property<Boolean>` | Default `false`; record up SQL checksums and validate completed migrations before execution |
| `withSeriesNumber` | `Property<Boolean>` | Optional series-number behavior |
| `changeTable` | Nested configuration | Migration history table settings |
| `dataSource` | Nested configuration | Target database connection |

Only configure optional directories that exist and are part of your workflow.
The rollback directory is named `downSqlDirectory`, not `sqlDownDirectory`.
The migration extension's `lastChangeNumber` is a string; the SQL generation
tasks' similarly named property has different typing and numbering semantics.

`migrationInsert` and `migrationRepair` are registered separately for history
operations. They are not substitutes for applying reviewed schema changes.
Rollback task classes require explicit registration:

```groovy
import com.sqlapp.gradle.plugins.MigrationDownTask

tasks.register('rollbackMigration', MigrationDownTask)
```

That task uses the same `migration` extension, but clears `lastChangeToApply`
before execution: do not use the extension's `lastChangeNumber` to bound this
rollback task. Configure and review the down SQL before executing it;
generated reverse DDL does not establish that deleted business data can be
recovered.

Versioned SQL migration is separate from the YAML-driven bulk migration and
SCD2 snapshot tasks. See the [task guide](README.md#executebulkmigrationjob)
for source/target connections, checkpoints, leases and verification.

### Concurrent migration execution

For each selected version, migration acquires the dialect's history-table lock
before rechecking whether that version exists. The lookup preserves the
connection's transaction isolation. If another invocation has changed the
history so that the selected version can no longer be started, the command fails
with `DbConcurrencyException` instead of returning as if it completed. Stop
competing invocations, inspect the current history, and rerun from the resulting
state. Earlier versions committed by this invocation remain applied.

This uses the existing per-version locks and commit boundaries; it does not add
configuration or history columns. It is not a command-wide distributed lock:
setup/finalize SQL, history-table creation/upgrades, and DDL that implicitly
commits are not protected for the full invocation. Non-transactional SQL runs on
a separate connection, while the history connection's lock follows the database's
transaction semantics. Serialize deployments externally when command-wide
exclusion is required. Lock support and wait behavior remain dialect/driver
dependent; these checks do not introduce a lease, automatic retry, or timeout.

### Optional checksum validation

The default migration workflow does not record or validate checksums and needs
no additional configuration. For projects that want immutable applied SQL:

```groovy
extensions.configure(MigrationExtension) {
    checksumValidation = true
}
```

The next `./gradlew migration` adds a nullable `checksum` column to the configured
history table if necessary. It validates completed entries that already have a
checksum before running setup, versioned, or finalize SQL, and records checksums
for newly executed up migrations. Existing entries are never backfilled from
today's SQL: they remain `UNVERIFIED` and do not block migration. History-only
`migrationInsert` also leaves checksums null because it does not execute SQL.
Disabling the option later preserves existing checksums; subsequent migrations
run without automatic validation or new checksum recording.

Run `./gradlew migrationValidate` whenever an explicit check is useful, including
CI. This command validates recorded checksums even when `checksumValidation` is
false. It requires an existing `sqlDirectory`, reads completed history entries,
and does not create/upgrade the history table, run SQL scripts or hooks, or
rewrite checksums. A database without a history table has no entries to validate.
Validation checks all completed entries, independent of `lastChangeNumber` and
`showVersionOnly`.

| Result | Meaning |
|---|---|
| `VERIFIED` | Current up SQL source matches the recorded checksum |
| `UNVERIFIED` | No historical checksum is available; this is not a failure or proof of a match |
| `MISSING` | A checksummed migration no longer has a matching up SQL file |
| `CHANGED` | Current source does not match its recorded checksum |

`MISSING` and `CHANGED` fail the command. Restore the applied source or investigate
the discrepancy; neither validation nor repair silently accepts a new checksum.
This check does not verify database structure or diagnose incomplete migrations.

Checksums use SHA-256 over the up file text decoded with `encoding`, read with the
same line-ending normalization as SQL parsing, and encoded as UTF-8 for hashing.
Comments, whitespace and inline `-- //@UNDO` content are included. File names,
separate down files, setup/finalize files and expanded placeholder values are not
included. The checksum uses the source snapshot parsed for execution.

Java callers use `MigrationCommand.setChecksumValidation(true)` for the opt-in
workflow or `MigrationValidateCommand` for the read-only check. After validation,
`getValidationResult().entries()` provides version, state, expected checksum and
actual checksum, including when a mismatch causes the command to throw. A null
result means validation did not run; an empty result means there were no completed
entries to check. No core Schema XML format changes are required.

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
