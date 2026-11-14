# DuckDB

`com.sqlapp:sqlapp-core-duckdb` adds a DuckDB dialect: schema metadata reading,
DDL recreation and SQL generation for **DuckDB 1.1 or later** (1.1, 1.2–1.3,
1.4+; DuckDB 1.5 and 2.0 use the 1.4 dialect). Supply the DuckDB JDBC driver
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
| Scalar / table macro (`CREATE MACRO`) | Function (`FunctionType.Scalar` / `Table`). All overloads are combined into one complete `CREATE MACRO` statement in `definition`; arguments and `statement` are set only for a single overload. |
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
| INTERVAL | Read as DuckDB text and kept as `Interval`; an interval with mixed signs (e.g. `-1 month 2 days`) cannot be represented by `Interval` and is kept as text |

TIME values are read as `java.time.LocalTime`, and the columns read by the
metadata reader keep TIME/INTERVAL values without the default conversion.

## Version-specific behavior

| Capability | DuckDB version |
|---|---|
| Constraint metadata (names, referenced tables in `duckdb_constraints()`) | 1.1+. On 1.0 the constraint readers fail with `UnsupportedOperationException`. |
| `ALTER TABLE ... ADD PRIMARY KEY` | 1.2+ |
| `MERGE INTO` (`SqlType.MERGE`) | 1.4+. Earlier versions fail with `IllegalArgumentException`; use the bulk upsert, which works on every version. |

## Limitations

These follow from DuckDB itself; unsupported requests fail with
`IllegalArgumentException` instead of producing SQL that DuckDB rejects.

- DuckDB generates constraint names itself; names written in DDL are not kept,
  so constraint names read back differ from the original DDL.
- No identity columns; use `DEFAULT nextval('seq')`.
- Foreign keys: same schema only, NO ACTION/RESTRICT only (no CASCADE / SET NULL).
- Constraints other than a primary key (1.2+) cannot be added to, and no
  constraint can be dropped from, an existing table. A column cannot be added
  with a CHECK constraint or as a generated column; NOT NULL is added in a
  second `ALTER COLUMN ... SET NOT NULL` statement.
- Index sort order is not kept by DuckDB (ART indexes); partial indexes are not
  supported.
- Sequences have no data type, cache or ordering option; those properties are
  not emitted.
- A column whose type is a named `CREATE TYPE ... AS ENUM` is reported by DuckDB
  as an inline ENUM; the type name is not kept. Temporary objects are not read.
- `duckdb_functions()` does not expose macro parameter defaults
  (`b := 5`); a macro read back declares the parameter without its default.
  Macros with several overloads need DuckDB 1.1+ to be recreated.
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
