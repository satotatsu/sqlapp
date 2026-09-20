# Command API getting started

`sqlapp-command` provides Java command classes for metadata export, HTML
documentation, file conversion, data generation, SQL execution, normalization,
and migration. These are the same command implementations invoked by the
Gradle plugin.

Use command classes when an application or another build tool needs explicit
Java control. For ordinary Gradle builds, the `com.sqlapp.db` tasks provide
typed inputs, task ordering, and output declarations around these commands.

## Add the dependency

Gradle:

```groovy
dependencies {
    implementation 'com.sqlapp:sqlapp-command:0.80.0'
    runtimeOnly 'com.sqlapp:sqlapp-core-postgres:0.80.0'
    runtimeOnly 'org.postgresql:postgresql:<jdbc-driver-version>'
}
```

Maven:

```xml
<dependency>
  <groupId>com.sqlapp</groupId>
  <artifactId>sqlapp-command</artifactId>
  <version>0.80.0</version>
</dependency>
```

Add the matching dialect and JDBC driver at runtime for commands that connect
to a database. `sqlapp-command` exposes the core Schema model and ELK renderer
transitively. See [Maven getting started](maven-getting-started.md) or
[Published artifacts](artifacts.md) for complete dependency examples.

## Export database metadata to Schema XML

Supply an application-created `DataSource`, select metadata, configure the
output, and call `run()`:

```java
import java.io.File;
import javax.sql.DataSource;

import com.sqlapp.data.db.command.ExportSchemaXmlCommand;

DataSource dataSource = createApplicationDataSource();

ExportSchemaXmlCommand command = new ExportSchemaXmlCommand();
command.setDataSource(dataSource);
command.setCloseDataSource(false);
command.setTarget("catalog");
command.setIncludeSchemas("public");
command.setOnlyCurrentSchema(false);
command.setDumpRows(false);
command.setOutputDirectory(new File("build/schema"));
command.setOutputFileName("Catalog.xml");
command.run();
```

This writes UTF-8 Schema XML to `build/schema/Catalog.xml`. Parent directories
are created when necessary.

`target` identifies the metadata root, such as `catalog`, `schema`, or `table`;
use the singular or plural form expected by the workflow. `includeSchemas` and
`includeObjects` filter metadata names. `onlyCurrentCatalog` defaults to true,
while `onlyCurrentSchema` defaults to false.

The [metadata target reference](gradle-plugin/schema-sql-and-html.md#choose-the-metadata-target)
lists the common catalog-, schema-, table-, and constraint-level readers. The
same target values apply to the Java command and Gradle task.

`dumpRows` defaults to true in the command. Set it to false explicitly for a
metadata-only snapshot. Enabling it reads table rows as the XML is written and
can make the output large or expose data that should not leave the database.

## Generate HTML without a database connection

`GenerateHtmlDocsCommand` reads saved Schema XML, so it does not require a
`DataSource`:

```java
import java.io.File;

import com.sqlapp.data.db.command.html.GenerateHtmlDocsCommand;

GenerateHtmlDocsCommand command = new GenerateHtmlDocsCommand();
command.setTargetFile(new File("build/schema/Catalog.xml"));
command.setOutputDirectory(new File("build/docs/database"));
command.setMultiThread(true);
command.run();
```

The command can also consume an in-memory `Catalog`:

```java
command.setCatalog(catalog);
command.setOutputDirectory(new File("build/docs/database"));
command.run();
```

When a catalog is supplied, it takes precedence over loading `targetFile`.
Use a saved XML input when documentation must be generated from a reviewed,
reproducible snapshot rather than current database state.

Dictionary files, logical relationships, viewpoints, data-file directories,
and rendering options are optional. Their Gradle property names map closely to
the corresponding command setters; see
[Schema XML, SQL, and HTML tasks](gradle-plugin/schema-sql-and-html.md).

## DataSource and connection ownership

`AbstractDataSourceCommand` obtains a connection from the supplied
`DataSource` and releases the connection after the command action. Its
transactional execution path defaults `closeDataSource` to true and can also
close a closeable DataSource.

When the application owns a pool or reuses the DataSource, always set:

```java
command.setCloseDataSource(false);
```

Then close the DataSource once at the application boundary if its concrete
type implements `AutoCloseable` or `Closeable`. Do not assume that every
`javax.sql.DataSource` has a `close()` method.

Metadata export currently uses a non-transactional, connection-release path
and closes the borrowed connection without closing the DataSource. Setting
`closeDataSource(false)` still makes caller ownership explicit and protects
code when switching to another command with transactional behavior.

Commands should receive the pool itself, not a connection captured elsewhere.
Do not close or return a borrowed connection while a command is still running.

## Transactions and failures

Transactional commands disable auto-commit on their borrowed connection,
commit after successful work, and roll back after an exception. Metadata and
file-only commands may use a non-transactional path instead. Check the specific
command before assuming one transaction covers the whole operation; bulk and
migration workflows can define chunk, table, or phase boundaries.

`AbstractCommand` uses an exception handler. The default handler converts
failures to runtime exceptions, so `run()` reports failure to its caller.
Commands also log through Log4j and may write progress to their configured
output stream. Do not treat log output as the success signal; allow exceptions
to propagate and verify the expected output or report artifact.

Commands are mutable configuration objects. Configure a new instance for each
independent run. Do not mutate or run one command concurrently from multiple
threads.

## Environment and context values

Before execution, `AbstractCommand` initializes its context with environment
variables and JVM system properties. Explicit command context parameters can
be used by commands that support placeholders or expressions.

Do not place credentials into generated files, logs, or exception messages.
Pass them through the application DataSource configuration or its secret
provider. The command API does not load the Gradle plugin's DataSource property
files; that file-loading convenience belongs to the Gradle
`DataSourceExtension`.

## Choose the command safely

| Operation | Example command | Database effect |
|---|---|---|
| Export metadata | `ExportSchemaXmlCommand` | Reads metadata; also reads rows when `dumpRows` is true |
| Generate HTML | `GenerateHtmlDocsCommand` | File-only when using Schema XML or an in-memory Catalog |
| Convert files | `ConvertDataCommand` | Reads and writes files; may remove originals when configured |
| Execute SQL files | `SqlExecuteCommand` | Executes SQL and may change database state |
| Versioned migration | `MigrationCommand` | Executes migration SQL and updates migration history |
| Normalize Schema XML | normalization command classes | Writes transformed XML, plans, and optional mappings |
| Bulk migration | bulk/migration command classes | Reads and changes database data and operational state |

Review required properties, transaction boundaries, restart behavior, and
destructive options before calling a database-changing command. The
[Gradle task reference](gradle-plugin/task-reference.md) provides a compact map
from task names to command classes and database effects. Detailed bulk behavior
is documented in [Bulk insert and bulk migration](bulk-insert.md).

## Test command integrations

### File import and bulk migration

`com.sqlapp.data.db.command.export.ImportDataCommand` remains the lightweight
file-to-database entry point. It shares format decoding with `TableFileReader`:
CSV/TSV, Excel, XML, JSON, JSONL/NDJSON, YAML and TOML. JSONL is read as one
object per line; TOML uses an `[[items]]` array. Existing Import properties,
SQL types, value converters and commit settings are retained.

Expression evaluation remains a caller policy, separate from format decoding.
For example, with placeholders enabled and `fileDirectory` configured,
`${readFileAsBytes('aaa.png')}` or `${new File('aaa.png')}` reads a relative file
as binary data. A `File` expression result is resolved against `fileDirectory`
and read into a byte array; absolute paths are retained, and missing files fail
the import. Placeholders are disabled by default. Custom Import value conversion
remains available and file values are evaluated only once. Import and
`TableFileReader` use the same value-evaluation pipeline; Import's custom
converter runs before expression evaluation. File-read failures retain the
source file, column and original input value in their diagnostics.

When supported by the dialect, `INSERT_ROWS` and `MERGE_ROWS` apply the same conversion before generating
parameterized row batches. Full batches and the final partial batch use the
same execution path. The configured final commit callback also runs when the
row count is an exact multiple of the batch size; failures do not trigger that
final callback. If the dialect cannot generate row-batch SQL, Import reports an
error before reading the input rather than reporting unexecuted rows as imported.

CSV imports detect line endings and retain Schema column types. Standalone
`CsvRowIteratorHandler` retains its existing string-column default; callers can
opt into retaining supplied types with `setPreserveColumnTypes(true)`.

Import and generator share MVEL variable resolution: imported classes and
constructor method calls work with `ParametersContext`, while omitted SQL
parameters still resolve to null. Generator file data-source expressions are
evaluated once per load.

Use `BulkMigration` when checkpointing, resume, verification and repair are
required. File Import does not require migration job configuration. The two
entry points retain their own transaction and execution policies. Their JDBC
parameter binding is shared through `JdbcParameterBinder` for the portable
batch-insert path; vendor-native bulk loaders retain their own implementations.

### Integration checks

Test file-only commands with temporary input and output directories. Test JDBC
commands with a disposable database and the same database product/version used
by the application. Verify at least:

- the resolved dialect;
- selected catalogs, schemas, tables, and objects;
- generated or modified files;
- commit and rollback behavior for a forced failure; and
- DataSource reuse or closure according to application ownership.

Do not point command integration tests at production. Repository contributors
should begin with the relevant command test class and module as described in
[Building and testing sqlapp](build-and-test.md).
