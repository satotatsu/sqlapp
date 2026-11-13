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

Generated columns, collations and index keys are taken from the DDL that DuckDB
stores in `duckdb_tables().sql` / `duckdb_indexes().sql`, because the catalog
functions do not expose them in structured columns. Index keys that are
expressions are kept in the `duckdb.index.keys` specific.

## Version-specific behavior

| Capability | DuckDB version |
|---|---|
| Constraint metadata (names, referenced tables in `duckdb_constraints()`) | 1.1+. On 1.0 the constraint readers fail with `UnsupportedOperationException`. |
| `ALTER TABLE ... ADD PRIMARY KEY` | 1.2+ |
| `MERGE INTO` (`SqlType.MERGE`) | 1.4+. Earlier versions fail with `IllegalArgumentException`. |

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
  as an inline ENUM; the type name is not kept. Macros, types and temporary
  objects are not read.
- `COMMENT ON SCHEMA` is not supported by DuckDB.

## Tests

All module tests use in-process DuckDB (`jdbc:duckdb:`) or no database; no
external server is used.

```
./gradlew :sqlapp-core-duckdb:test
```
