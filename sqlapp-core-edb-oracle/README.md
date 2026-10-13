# EDB Oracle-compatible product dialect (experimental)

`sqlapp-core-edb-oracle` targets **EDB Postgres Advanced Server (EPAS) 14–18**,
EDB's Oracle-compatible product. Extended Server and standard PostgreSQL are
separate products. The artifact name identifies the intended product; it does
not claim complete Oracle compatibility.

## Use

Add `com.sqlapp:sqlapp-core-edb-oracle` at the same version as your other sqlapp
artifacts. Its dependency on `sqlapp-core-postgres` shares metadata readers, SQL
factories and bulk execution without introducing a new JDBC driver/version.

```java
Dialect dialect = DialectResolver.getInstance().getDialect(connection);
// Offline generation or explicit selection:
Dialect explicit = DialectResolver.getInstance()
    .getDialect("edb-oracle", 17, 0, null);
```

Aliases are `EDB Postgres Advanced Server`, `EnterpriseDB`,
`EnterpriseDB Advanced Server`, `EPAS` and `edb-oracle`. Supply the EPAS/PostgreSQL
engine major (14–18); unsupported majors fail clearly. The Java package is
`com.sqlapp.data.db.dialect.edb.oracle`.

Automatic identification uses the JDBC product name or Advanced Server version
banner. A numerically reported PostgreSQL version requires one read-only
`SELECT pg_catalog.version()`; ordinary PostgreSQL continues through its normal
resolvers. YSQL/Cockroach markers bypass the query. Probe failures propagate.
Detection does not change autocommit, commit, rollback or search_path, and does
not pollute offline PostgreSQL resolution. Product identification does not prove
that a database was initialized in Oracle compatibility mode.

## Implemented behavior

- Matching PostgreSQL 14–18 metadata and SQL factories, with PostgreSQL 14 upsert
  fallback and PostgreSQL 15+ MERGE. Existing public APIs and Schema XML stay compatible.
- Dedicated ServiceLoader providers for COPY, bulk upsert and set-based migration
  snapshots. COPY requires `org.postgresql.core.BaseConnection`; EDB's separate
  `com.edb` JDBC driver is not qualified and has no COPY adapter here.
- VARCHAR2/NVARCHAR2, NUMBER (including negative scale) and RAW recognition through
  existing Schema types. PostgreSQL's preferred generated types are retained.
  RAW uses byte-oriented BLOB/BYTEA semantics. NUMBER precision is preserved
  instead of imposing Oracle's precision limit.
- DATE is not globally remapped. EPAS stores Redwood DATE columns as timestamp;
  stored timestamp metadata retains time, while a genuine DATE stays date-only.
- Standalone edbspl functions/procedures keep complete executable
  pg_get_functiondef output in definition. Overloaded identity, schema/catalog and
  procedure kind are retained; arguments already embedded in DDL are optional.
  Missing requested DDL fails. No per-SPL-routine/argument type lookup SELECTs.
- Package specifications/bodies retain complete, schema-qualified CREATE OR
  REPLACE definitions. CREATE/DROP factories support both Package and PackageBody.
  Source line order and existing newlines are retained; missing source and
  unrecognized headers/declarations fail. AUTHID is retained. A manually authored
  Package must provide a complete executable definition.
- Package member namespaces are excluded from standalone routine reading via
  optional nspparent metadata, following EDB's debugger catalog convention.
  PostgreSQL without that field retains normal routine behavior.
- Private and PUBLIC synonyms use their corresponding Schema model types,
  including target schema/object and optional database-link reference.
  CREATE/DROP handles quoted identifiers and remote references.
- Private and PUBLIC database links retain owner, remote user, host and remote
  product type. Passwords and user mappings are not selected. Link metadata
  reading never connects to a remote server.

Package, synonym and database-link readers discover compatibility views only in
sys/pg_catalog, then fetch the requested set in one query. Counts remain two
SELECTs per reader, independent of object count. Filters are bound. An absent
compatibility view returns an empty collection; ambiguous catalogs and execution
failures propagate. User search-path views cannot shadow these reads. ALL_*
visibility/privileges determine which objects are returned.

Private DB links are role-owned, unlike ordinary schema objects. The existing
DbLink model's schemaName is used as the owner key, matching the established
Oracle representation. SchemaReader filtering/attachment therefore uses that
owner name; if a role has no same-named schema, use the DbLinkReader directly
with that owner or without a schema filter. PUBLIC links live in PublicDbLink.
No CREATE statement silently changes roles or prefixes a role as a link schema.

## Database-link recreation

Metadata alone cannot recreate authentication or reliably identify the transport.
Supply the user/password, complete connection string and explicit transport:

```java
link.setUserId("remote_user");
link.setPassword(applicationSuppliedPassword);
link.setDataSource("host=target.example port=5444 dbname=target");
link.getSpecifics().put("edb.dbLink.transport", "postgres_fdw");
var operations = dialect.createSqlFactoryRegistry().createSql(link, SqlType.CREATE);
```

Transport values are `postgres_fdw`, `oci`, or `legacy` (unqualified USING string).
The catalog remote type is retained separately as `edb.dbLink.remoteType`; it is
not used to guess transport. The catalog HOST value is not guaranteed to be a
complete connection string: replace it when necessary. Missing inputs, encrypted
passwords or unsupported transport values fail clearly. Supplied literals preserve
quotes/backslashes and use PostgreSQL escape-string syntax. Execute private-link
DDL as the intended owner. Generating SQL does not establish a remote connection.

## Validation and remaining qualification

```powershell
.\gradlew.bat :sqlapp-core-edb-oracle:test :sqlapp-core-edb-oracle:assemble :sqlapp-core-postgres:test --continue --console=plain
.\gradlew.bat :sqlapp-core-dialect-test:edbOracleCompatibilityTest --continue --console=plain
```

The opt-in compatibility task runs disposable upstream PostgreSQL
14.22/15.17/16.13/17.9/18.4. It checks inherited sequence/index recreation,
ordinary PostgreSQL detection and caller transactions, COPY/upsert rollback,
standalone routine recreation and simulated Oracle catalog views. Those simulated
views validate catalog-query execution, binding, counts and model mapping;
PostgreSQL does not emulate Oracle SPL, packages, synonyms or database links.
Module tests cover EDB resolution, types, SPL definitions, package DDL/XML,
synonym DDL and explicit database-link validation/escaping.

The module remains experimental until these paths run on an authorized EPAS
Oracle-mode server. Real EPAS catalog namespaces, PUBLIC ownership markers,
package/SPL source formats and privileges, packaged-member exclusion, native
Oracle types/empty-string semantics, EDB-driver COPY and generated Oracle DDL are
not qualified. Wrapped source and comments preceding package headers are not
guessed. Redwood configuration, EDB partition extensions and remaining
Oracle-only types/catalogs are separate work.

No EPAS image was present locally on 2026-10-13. No EDB registry credentials,
licenses, external/production databases or remote database-link targets were accessed.

## References

- [EDB Oracle support](https://www.enterprisedb.com/docs/edb-postgres-ai/databases/oracle_compatibility/)
- [EPAS data types](https://www.enterprisedb.com/docs/epas/13/epas_compat_reference/02_the_sql_language/02_data_types/)
- [Redwood DATE storage](https://www.enterprisedb.com/docs/epas/latest/reference/sql_reference/02_data_types/04_date_time_types/)
- [SPL and package definitions](https://www.enterprisedb.com/docs/epas/latest/application_programming/02_packages/01a_display_packages/)
- [ALL_SOURCE](https://www.enterprisedb.com/docs/epas/14/epas_compat_cat_views/oracle_compatible_views/19_all_source/)
- [ALL_SYNONYMS](https://www.enterprisedb.com/docs/epas/13/epas_compat_cat_views/21_all_synonyms/)
- [ALL_DB_LINKS](https://www.enterprisedb.com/docs/epas/latest/reference/oracle_compatibility_reference/epas_compat_cat_views/oracle_compatible_views/06_all_db_links/)
- [CREATE DATABASE LINK](https://www.enterprisedb.com/docs/epas/16/reference/oracle_compatibility_reference/epas_compat_sql/21_create_public_database_link/)
- [DROP PACKAGE](https://www.enterprisedb.com/docs/epas/17/reference/oracle_compatibility_reference/epas_compat_sql/49_drop_package/)
- [EDB debugger package namespace detection](https://github.com/EnterpriseDB/pldebugger/blob/master/pldbgapi--1.1.sql)
- [EDB JDBC Connector](https://www.enterprisedb.com/docs/jdbc_connector/latest/)


Latest verification on 2026-10-13: **309 tests passed** (EDB 19, PostgreSQL
265, Docker controls 25), zero failures/errors/skips. Module assembly succeeded.
Initial Docker-unavailable runs have now been superseded by this successful
upstream matrix; real EPAS qualification remains outstanding.
