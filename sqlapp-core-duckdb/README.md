# DuckDB

`com.sqlapp:sqlapp-core-duckdb` adds a DuckDB dialect: schema metadata reading,
DDL recreation and SQL generation for **DuckDB 1.1 or later** (1.1, 1.2–1.3,
1.4–1.5 and 2.0+). DuckDB 2.0 has a separate capability subclass. Supply the
DuckDB JDBC driver
(`org.duckdb:duckdb_jdbc`) separately.

## Getting started

```java
import java.sql.Connection;
import java.sql.DriverManager;

import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Schema;

class DuckDbUsage {
    static Schema readSchema(String url, String schemaName) throws Exception {
        try (Connection connection = DriverManager.getConnection(url)) { // e.g. jdbc:duckdb:/path/app.duckdb
            var dialect = DialectResolver.getInstance().getDialect(connection);
            var reader = dialect.getCatalogReader().getSchemaReader();
            reader.setSchemaName(schemaName); // DuckDB's default schema is "main"
            return reader.getAllFull(connection).stream()
                    .filter(schema -> schemaName.equals(schema.getName()))
                    .findFirst().orElseThrow();
        }
    }
}
```

The resolver parses the version from `DatabaseMetaData.getDatabaseProductVersion()`
(`v1.4.4` etc.).

On DuckDB 2.0+, use `((DuckDb) dialect).readSchema(connection, "main")` to
include complete macro definitions, including optional parameters and overloads.
This requires auto-commit. It duplicates the JDBC connection, copies only the
catalog schema into an attached in-memory database, removes its tables in
foreign-key dependency order, and exports that macro-containing copy to
a temporary directory, and removes both resources before returning. It never
copies source rows. The low-level `DuckDbSchemaReader` also offers
`setReadCompleteMacroDefinitions(true)`; its default remains catalog-only.
Export failures are propagated with cleanup failures suppressed. Snapshot table
removal also permits extraction from catalogs containing self-referencing
foreign keys; source tables and macro bodies remain unchanged.
Definitions retain their source catalog; retarget complete DDL explicitly when
moving objects to another catalog.

JDBC row transfer preserves UNION tags using tagged JSON and VARIANT dynamic
types using a versioned `sqlapp-duckdb-variant-v1:` hexadecimal payload containing
DuckDB's Parquet variant metadata/value bytes. The read projection, ordinary
parameterized SQL, batch insert, bulk upsert/update and snapshot staging share
the same reconstruction rules. Treat VARIANT payloads as opaque values. Plain
display text is rejected when writing VARIANT; it cannot retain dynamic types.
Types combining UNION and VARIANT use recursive tagged JSON: UNION member tags
are explicit, VARIANT leaves contain Parquet variant bytes, and ordinary leaves
retain canonical type text. STRUCT, MAP, lists and fixed arrays are reconstructed
from the original type expression, including SQL NULL and tagged NULL members.
The VARIANT encoding is verified with DuckDB
2.0.0 / JDBC 2.0.0.1; portability to other engine versions is not claimed.
These paths use the bundled JSON and Parquet extensions. Optional extensions
such as spatial/inet are not installed or downloaded by metadata readers/tests.
To run the opt-in spatial/inet round-trip test, preinstall the official extensions
in a dedicated directory and set `SQLAPP_DUCKDB_EXTENSION_DIRECTORY` before
running the module tests. Requested but missing extensions fail the test;
when the environment variable is absent the extension test is skipped.

## Model mapping

| DuckDB | sqlapp model |
|---|---|
| Attached database (`duckdb_databases()`) | Catalog. The internal `system` and `temp` databases are excluded. |
| Schema | Schema. `information_schema` and `pg_catalog` are excluded. |
| Table, view, column, index, sequence | Table, View, Column, Index, Sequence |
| PRIMARY KEY / UNIQUE / CHECK / FOREIGN KEY | Constraints (DuckDB 1.1+) |
| `GENERATED ALWAYS AS (...) VIRTUAL` | `Column.formula` |
| `COLLATE` | `Column.collation` |
| Inline `ENUM('a', 'b')` | `DataType.ENUM` with values |
| LIST / ARRAY / STRUCT / MAP / UNION, `TIMESTAMP_NS` etc. | `DataType.OTHER` with the DuckDB type name |
| View | Complete `CREATE VIEW` statement in `definition` |
| Scalar / table macro (`CREATE MACRO`) | Function (`FunctionType.Scalar` / `Table`). Parameterless macros have a complete `definition`. Parameterized macros retain available arguments/body and are marked `duckdb.macro.incompleteSignature`; recreating them requires the original complete `definition`. |
| `CREATE TYPE ... AS ENUM` | Domain with `DataType.ENUM` and the labels as values |

Generated columns, collations and index keys are taken from the DDL that DuckDB
stores in `duckdb_tables().sql` / `duckdb_indexes().sql`, because the catalog
functions do not expose them in structured columns. Index keys that are
expressions are kept in the `duckdb.index.keys` specific.

## SQL generation

Besides tables, indexes, sequences, views and comments, the SQL factory registry
provides:

| Object / `SqlType` | SQL |
|---|---|
| Function `CREATE` / `DROP` | `CREATE MACRO` (stored definition, or built from arguments and statement) / `DROP MACRO [TABLE]` |
| Domain (ENUM) `CREATE` / `DROP` | `CREATE TYPE ... AS ENUM (...)` / `DROP TYPE` |
| Table `CREATE_TEMPORARY` / `TRUNCATE_TEMPORARY` | `CREATE TEMPORARY TABLE` / `TRUNCATE TABLE` with the configured prefix/suffix and never schema qualified |
| Table `ANALYZE` | `ANALYZE schema.table` |
| Table `SELECT_FOR_APP` | Native `LIMIT` / `OFFSET` paging, without scrollable JDBC result sets |
| Catalog `DEFRAG` | `CHECKPOINT database` |
| `SET_COMMENT` | `COMMENT ON TABLE/VIEW/COLUMN/INDEX/SEQUENCE/MACRO [TABLE]/TYPE` |

`Dialect.createSqlSplitter()` returns a splitter that keeps `$$...$$` /
`$tag$...$tag$` strings, `E'...'` strings with backslash escapes and nested
block comments intact.

## Data transfer

The module registers DuckDB providers for `BulkInsertResolver`,
`BulkUpsertResolver` and `SetBasedMigrationSnapshotResolver`:

| Provider | Implementation |
|---|---|
| Bulk insert | JDBC batch that binds every value with the DuckDB type handler of its column |
| Bulk upsert | `INSERT ... ON CONFLICT (keys) DO UPDATE` (all supported versions; keys must match a primary key or unique constraint) |
| SCD2 snapshot | Temporary staging table and `IS [NOT] DISTINCT FROM` comparison |

Values of every type listed above are copied unchanged between DuckDB databases
through the streaming read path (`JdbcDynamicRowIteratorHandler`) and the bulk
insert path. The DuckDB JDBC drivers reject or corrupt some values when they are
bound by Java class, so the type handlers bind them as text and let DuckDB
convert them:

| Type | Driver behavior avoided |
|---|---|
| HUGEINT / UBIGINT | `setBigDecimal` on HUGEINT stores NULL without an error |
| UUID | `setObject(UUID)` changes the value on the 1.0 driver |
| DATE / TIME | `LocalDate` / `LocalTime` are rejected before 1.2 / 1.4; `java.sql.Time` drops microseconds |
| TIMESTAMPTZ | Instant shifted by the JVM time-zone offset |
| INTERVAL | Independent month/day/time components are retained by `IntervalValue`, including mixed signs |

TIME values are read as `java.time.LocalTime`; TIMETZ uses `java.time.OffsetTime`.
DATE uses `java.time.LocalDate` (including year zero and negative years), with
`infinity` / `-infinity` retained as text. Metadata columns retain those values
without conversion through `java.sql.Date` / `java.sql.Time`.
SQL value literals use native `::DATE`, `::TIME` and `::TIMETZ` casts with the
same formatting as parameter binding, rather than JDBC escape syntax.

INTERVAL uses `IntervalValue`, retaining months, days and elapsed time independently.
For example, 24 hours remains 24 hours rather than becoming one calendar day,
including when applied across daylight-saving transitions.

Native types modeled as OTHER (including LIST/ARRAY, STRUCT, MAP, TIMESTAMP_NS
and UHUGEINT) use DuckDB text for streaming reads and parameter binding. When
constructing such rows manually, supply DuckDB text rather than a driver-specific
object. The native type expression, including quoted STRUCT field names, is
retained for DDL recreation. This is a DuckDB-to-DuckDB representation; converting
these values to another engine requires an explicit mapping.

SQL and bulk targets retain an explicitly supplied catalog as well as the schema
and table. When transferring to a differently named database, set the target
catalog on the Schema/Table model explicitly.

## Version-specific behavior

| Capability | DuckDB version |
|---|---|
| Constraint metadata (names, referenced tables in `duckdb_constraints()`) | 1.1+. On 1.0 the constraint readers fail with `UnsupportedOperationException`. |
| `ALTER TABLE ... ADD PRIMARY KEY` | 1.2+ |
| `ALTER TABLE ... ADD UNIQUE` | 2.0+ (older dialects continue to reject it) |
| `MERGE INTO` (`SqlType.MERGE`) | 1.4+. Earlier versions fail with `IllegalArgumentException`; use the bulk upsert, which works on every version. |

## Limitations

These follow from DuckDB itself; unsupported requests fail with
`IllegalArgumentException` instead of producing SQL that DuckDB rejects.

- DuckDB generates constraint names itself; names written in DDL are not kept,
  so constraint names read back differ from the original DDL.
- No identity columns; use `DEFAULT nextval('seq')`.
- Foreign keys: same schema only, NO ACTION/RESTRICT only (no CASCADE / SET NULL).
- Only PRIMARY KEY (1.2+) and UNIQUE (2.0+) can be added to an existing table;
  constraints cannot be dropped. A column cannot be added
  with a CHECK constraint or as a generated column; NOT NULL is added in a
  second `ALTER COLUMN ... SET NOT NULL` statement.
- Index sort order is not kept by DuckDB (ART indexes); partial indexes are not
  supported.
- Sequences have no data type, cache or ordering option; those properties are
  not emitted.
- A column whose type is a named `CREATE TYPE ... AS ENUM` is reported by DuckDB
  as an inline ENUM; the type name is not kept. Temporary objects are not read.
- `duckdb_functions()` exposes neither macro parameter defaults (`b := 5`) nor
  which parameters have defaults. All parameterized macros read from metadata
  are therefore marked incomplete. CREATE fails with an actionable error until
  the caller supplies the complete original `Function.definition`; it never
  silently turns optional parameters into required parameters. No per-macro
  probing query or database export is performed. Manually authored functions
  without this metadata marker can still be built from arguments/body. Available
  overloads and bodies are retained as `duckdb.macro.partialDefinition` for
  inspection; that incomplete SQL is never emitted automatically. The optional
  complete-schema entry point above uses a schema-only snapshot/export to fill
  `definition` automatically on DuckDB 2.0+.
- Changing a `Column.check` requires table recreation and fails during ALTER
  planning, including addition and removal of the check.
- DuckDB has no domains: only ENUM domains can be created. Temporary tables can
  only keep self-referencing foreign keys (DuckDB rejects foreign keys across
  catalogs). `FORCE CHECKPOINT` and `VACUUM` are not generated (`VACUUM` does
  not reclaim space in DuckDB; `FORCE CHECKPOINT` aborts running transactions).
- `COMMENT ON SCHEMA` is not supported by DuckDB.

## Tests

All module tests use in-process DuckDB (`jdbc:duckdb:`) or no database; no
external server is used.

```
./gradlew :sqlapp-core-duckdb:test
```

DuckDB runs inside its JDBC driver, so no Docker container is needed. Each
engine version is tested by swapping the driver jar:

```
./gradlew :sqlapp-core-duckdb:duckdbCompatibilityTest     # all versions below
./gradlew :sqlapp-core-duckdb:duckdb14CompatibilityTest   # one version
```

| Task | DuckDB JDBC | Expected result |
|---|---|---|
| `duckdb10CompatibilityTest` | 1.0.0 | Constraint readers fail with `UnsupportedOperationException`; bulk tests and macros without overloads pass |
| `duckdb11CompatibilityTest` | 1.1.3 | All module tests |
| `duckdb12CompatibilityTest` | 1.2.2.0 | All module tests |
| `duckdb14CompatibilityTest` | 1.4.4.0 | All module tests |
| `duckdb15CompatibilityTest` | 1.5.6.0 | All module tests |
| `duckdb20CompatibilityTest` | 2.0.0.1 | All module tests |

Each task runs the whole module test suite with the given driver and checks that the connected engine reports the expected version
(`sqlapp.test.duckdb.expectedVersion`).

The command-layer integration (SQL script execution, `ExportSchemaXmlCommand`,
XML round trip and regenerated DDL) is in `sqlapp-core-dialect-test`. It uses a
DuckDB file in a temporary directory, so it needs no Docker either:

```
./gradlew :sqlapp-core-dialect-test:dockerTest --tests '*duckdb*'
```
