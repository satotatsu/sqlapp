# SQLite data type regression checks

Native INTEGER now resolves to the shared 64-bit BIGINT model instead of
competing with the portable TINYINT/SMALLINT/INT registrations. Portable integer
models still retain their explicit logical types and generate INTEGER. Metadata
and generated-query bindings can now report BIGINT where INT was previously
selected; saved Schema models are not automatically rewritten.

Native DOUBLE, DOUBLE PRECISION and FLOAT aliases resolve to DOUBLE and emit
REAL. The shared DOUBLE model also emits REAL with a retained source DOUBLE or
DOUBLE PRECISION name. These changes do not alter method signatures, configuration
formats, dependency versions, resolver boundaries or metadata queries.

`SqliteDataTypeMatrixTest` verifies resolution and public CREATE TABLE generation
across resolver selections 3.15, 3.37 and 3.53. Generated ordinary and STRICT table
DDL is executed with the bundled SQLite JDBC engine in memory; these executions
do not constitute live testing of older engine versions.

`SqliteDataTypeMetadataTest` covers both legacy and modern column-reader paths,
Long.MIN_VALUE/Long.MAX_VALUE and values beyond 8/32-bit ranges through the sqlapp
JDBC type handler. It also checks SQLite's affinity precedence (including
FLOATING POINT's INTEGER affinity), BLOB and NUMERIC fallback, and retention of
original declarations. Existing STRICT ANY tests continue to cover dynamic
values separately.

## Validation

Run with Java 25 and the repository Gradle Wrapper:

```powershell
.\gradlew.bat :sqlapp-core-sqlite:test --tests '*SqliteDataType*Test' --tests '*SqliteExplainBuilderTest' --console=plain
.\gradlew.bat :sqlapp-core-sqlite:test --console=plain
.\gradlew.bat :sqlapp-gradle-plugin:test --tests '*ExportSqliteSchemaXmlTaskTest' --tests '*BulkMigrationFacadeIntegrationTest' --console=plain
```

Tests use in-memory or temporary local SQLite databases. No external or
production databases were accessed, and no tracked generated files were
intentionally changed.

On 2026-11-01, all 827 SQLite module tests and all nine selected Gradle-plugin
integration/export tests passed, with no failures or skipped tests. The new
SQLite matrix and metadata tests account for 16 cases. The existing generated-key
binding test now expects BIGINT for its native SQLite INT column.

References: [SQLite storage classes and affinity](https://www.sqlite.org/datatype3.html),
[STRICT tables](https://www.sqlite.org/stricttables.html).
