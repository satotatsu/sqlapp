# Db2 for i 7.5 (experimental)

This separate Gradle/Maven artifact depends on `sqlapp-core-db2`, following the
PostgreSQL/MySQL derivative dialect pattern. No `sqlapp-core-db2-common` artifact
is required. The existing DB2 artifact remains the LUW entry point. Basic type
registrations and SQL builders are reused; bulk row mapping is shared in its
`common` package. The product-specific catalog SQL lives in this module.

## Use

Add `com.sqlapp:sqlapp-core-db2-i` with the same version as the other sqlapp
artifacts. JDBC connections are resolved automatically using product metadata;
DRDA product-version prefixes DSN (z/OS) and QSQ (IBM i) distinguish ambiguous
`DB2` names. Offline, use the explicit product name and supported version:

```java
Dialect dialect = DialectResolver.getInstance().getDialect("Db2 for i", 7, 5);
CatalogReader reader = dialect.getCatalogReader();
SchemaReader schemas = reader.getSchemaReader();
schemas.setSchemaName("APP");
List<Schema> model = schemas.getAllFull(connection);
List<SqlOperation> ddl = dialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE);
```

The concrete entry point is `Db2I75`. Only Db2 for i 7.5 is an initial target;
older and newer versions are rejected rather than mapped speculatively. No JDBC
driver is bundled. Supply the appropriate driver and connection credentials.

## Initial coverage

- Schema/table names and basic table/column remarks from `QSYS2` catalogs.
- Ordered columns, common Db2 scalar types, nullability and ordinary defaults.
- Identity generation mode and exact sequence start/increment/bounds/cache/order.
- Ordered primary/unique keys and composite foreign keys, including cross-schema
  references and delete/update rules supported by the product.
- Basic modeled CREATE TABLE, primary/unique/foreign-key DDL through the shared
  Schema model and SQL factories. LUW staging snapshot providers are excluded.
- Four queries for a nonempty table-reader request: tables, columns, unique keys,
  foreign keys. No per-table/column/key SELECTs. Full catalog traversal can issue
  these queries once per selected schema.

## Limits and verification

**No real Db2 for i server has been used to validate this implementation.**
Unit tests exercise JDBC product identification, resource loading and SQL binding,
mocked catalog row conversion, query counts and generated DDL. They do not prove
that the catalog queries execute or that the DDL is accepted by a server.

This is a basic logical-table baseline, not a full-fidelity database backup or
migration implementation. Physical placement/encoding (including CCSID), table
spaces, partitioning, temporal/security attributes, DDS/system names, standalone
indexes, checks, views, routines, triggers and privileges are outside initial
coverage. Unrepresented physical attributes cannot be recreated. Schema discovery
on z/OS currently lists schemas containing ordinary tables. IBM i uses SQL naming
and ordinary SQL tables; DDS physical/logical file recreation is not supported.

Unsupported generated columns and unresolved referenced keys fail explicitly.
Non-enforced/disabled constraints and explicit physical options are rejected by
the basic CREATE path. Unsupported types and versions fail explicitly. Advanced
metadata categories have no reader in this initial implementation; do not interpret
their absence as proof that no such objects exist on the server.

Before claiming server compatibility, use an explicitly authorized disposable
schema to verify catalog queries, JDBC identification, quoted identifiers, native
types/defaults, identity options, composite keys and a read/create/read round trip.
No external database is accessed by the unit tests or the default Gradle test task.

## References

- [IBM z/OS catalog reference](https://www.ibm.com/docs/en/db2-for-zos/13.0.0?topic=tables-db2-catalog-table-spaces)
- [IBM i catalog reference](https://www.ibm.com/docs/en/i/7.5.0?topic=views-i-catalog-tables)
- [z/OS column/default representation](https://www.ibm.com/docs/en/db2-for-zos/13.0.0?topic=tables-syscolumns)
- [IBM i referenced keys](https://www.ibm.com/docs/en/i/7.5.0?topic=views-sysrefcst)
