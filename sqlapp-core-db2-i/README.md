# Db2 for i 7.4, 7.5 and 7.6 (experimental)

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

The concrete entry points are `Db2I74`, `Db2I75` and `Db2I76`; the resolver
selects one from the JDBC version. All three share the 7.5 catalog SQL. 7.4
lacks the native BOOLEAN type introduced in 7.5; other releases are rejected
rather than mapped speculatively. No JDBC
driver is bundled. Supply the appropriate driver and connection credentials.

## Initial coverage

- Schema/table names and basic table/column remarks from `QSYS2` catalogs.
- Ordered columns, common Db2 scalar types, nullability and ordinary defaults.
- Identity generation mode and exact sequence start/increment/bounds/cache/order.
- Ordered primary/unique keys and composite foreign keys, including cross-schema
  references and delete/update rules supported by the product.
- Basic modeled CREATE TABLE, primary/unique/foreign-key DDL through the shared
  Schema model and SQL factories. LUW staging snapshot providers are excluded.
- Six queries for a nonempty table-reader request: tables, columns, indexes,
  checks, unique keys and foreign keys. No per-table/column/key SELECTs. Full
  catalog traversal can issue these queries once per selected schema.

## Additional catalog coverage

| Category | Read representation | CREATE support |
| --- | --- | --- |
| Views | Ordered columns plus query body (IBM i) or complete `STATEMENT` DDL (z/OS) | Reuses the Db2 view factory, including column aliases, CHECK OPTION and stored creation context |
| Checks | Predicate text and enabled state | Shared check constraint factory and table CREATE; disabled constraints are rejected by table CREATE |
| Indexes | Ordered ASC/DESC column or expression keys, uniqueness and index schema, plus the options listed below | CREATE INDEX after table creation, including a different index schema |
| Sequences | Exact start, increment, minimum, maximum, cache, cycle and order | IBM i native types; z/OS requires an explicitly resolved type as described below |

A view request uses two bulk queries (definitions and columns). A sequence request
uses one bulk query. Indexes and checks add one bulk query each to table loading.
No added reader executes an additional SELECT per object.

View and check text that is NULL/blank is rejected rather than emitted as empty
DDL. In IBM i this detects catalog definitions too long for VIEW_DEFINITION or
CHECK_CLAUSE. z/OS 13 uses SYSVIEWS.STATEMENT, not the obsolete TEXT/SEQNO fields;
a stored ALTER statement cannot be treated as a complete CREATE and is rejected.
IBM warns that this stored statement may not reflect every subsequent dependent
object change; verification against a real database remains necessary.

Ordinary and extended indexes are supported as described in the extended
indexes section below; incomplete key lists, random ordering, encoded-vector
and other special uniqueness semantics fail. z/OS constraint-backed and
implicitly generated indexes are excluded from standalone index creation;
primary/unique constraints remain represented separately. Physical index
storage options are not recreated. IBM i catalog views here cover SQL-created
indexes, not DDS logical files.

For z/OS, SYSSEQUENCES exposes an internal DATATYPEID instead of a documented
built-in type name. The reader preserves it in `specifics["DB2_HOST_DATATYPE_ID"]`
and leaves the Schema sequence data type unresolved. It does not guess the type
from precision, because DECIMAL(5,0) and SMALLINT would be ambiguous. CREATE SEQUENCE
requires the caller to supply the actual type (and decimal precision when needed)
from an authoritative source; until then it fails explicitly. Identity sequences
and sequence aliases are excluded from this standalone sequence reader. Sequence
runtime position is not restored from cached allocation values. IBM i distinct
sequence types remain unsupported.

## Routines, triggers and explicit privileges

Procedures and functions use one bulk query per category. IBM i joins SYSPARMS
in that query, retaining overload identity, argument direction, scalar returns
and table return columns. z/OS native SQL routines retain the complete CREATE
from SYSROUTINES.TEXT; their embedded signatures are not duplicated as arguments.
External implementations are inventoried with available execution attributes.

Triggers use one bulk query, retaining target schema, events, timing, orientation
and available transition references. Active z/OS triggers retain complete CREATE.
IBM i routine and trigger bodies are stored as `statement`, with
`specifics["DB2_HOST_REQUIRES_DEFINITION"]` explaining incomplete recreation.
CREATE for all host routines/triggers requires an authoritative complete CREATE
in `definition`; a body alone cannot preserve execution options or external
implementations. Disabled triggers require a separate restoration plan and are
rejected by CREATE. Stored catalog statements can become stale after changes;
this implementation does not repair or infer missing options.

Explicit table/sequence/type privileges share one bulk query; column privileges use
one separate bulk query. Table GRANT/REVOKE
and column UPDATE/REFERENCES preserve identifier quoting and known grant options.
z/OS role grantees are distinguished from authorization IDs; package grantees
are retained but cannot be replayed. z/OS column grantability is not exposed by
SYSCOLAUTH and is marked `DB2_HOST_GRANTABILITY_UNKNOWN`; SQL generation requires
explicit resolution. IBM i authorization-list rows also require explicit
resolution. Grantor metadata is retained where exposed, but generated SQL uses
the executing principal and does not impersonate the original grantor. Effective
rights from groups, ownership, special authorities or authorization lists are
not inferred. These readers do not cover every object/system privilege.

The shared Schema XML writer now preserves `hierachy=false` for object privileges.
The legacy default for XML without that attribute remains unchanged.

## Schema privileges

Full catalog loading automatically includes schema privileges in
`Catalog.schemaPrivileges`, using one bulk QSYS2.SYSSCHEMAAUTH query independent of
schema count. CREATEIN and USAGE retain known grant options; *PUBLIC becomes PUBLIC
and reserved GRANTOR remains null. ALTERIN, DROPIN and LUW-only schema authorities
are not enabled. For a schema-privilege-only request:

```java
var metadata = (AbstractDb2HostCatalogReader) dialect.getCatalogReader();
var privileges = metadata.getSchemaPrivilegeReader();
privileges.setSchemaName("APP"); // optional; omit to read all visible grants
List<SchemaPrivilege> grants = privileges.getAllFull(connection);
```

The existing ParametersContext overload also supports a set of schema names.
GRANT/REVOKE use ON SCHEMA with quoted identifiers; schema REVOKE does not append
RESTRICT. XML, clone and Catalog CREATE retain schema grants. Authorization-list
rows and unknown grantability require explicit resolution before SQL generation.
No group/special-authority or effective-permission expansion is performed.
SYSSCHEMAAUTH visibility depends on the connection's schema authorization or
QIBM_LIST_ALL_OBJS_SQL function usage authority; missing rows do not establish
that no permissions exist. The host reader getter is additive and does not change
the existing reader API or configuration.

## Type privileges

The object privilege reader includes ALTER and USAGE on user-defined types from
QSYS2.SYSUDTAUTH in the same bulk query as table and sequence privileges. Existing
schema/name filters apply to all branches. *PUBLIC becomes PUBLIC; known
IS_GRANTABLE values determine WITH GRANT OPTION, and reserved GRANTOR remains null.

The native schema/name stays in `ObjectPrivilege`, object kind TYPE in
`specifics["DB2_HOST_OBJECT_KIND"]`, and logical identity `TYPE:<object-name>` in
`specificName`. Same-named table, sequence and type grants remain distinct through
XML, clone and collection lookup. Catalog CREATE emits type grants too.
GRANT/REVOKE use ON TYPE and REVOKE emits RESTRICT. Only ALTER and USAGE are
supported; no additional routine privileges are synthesized from type grants.

Authorization-list rows and unknown grantability remain available for inspection
but require explicit resolution before SQL generation. Group profiles, special
authorities and effective permissions are not inferred. Generated grants use the
executing principal and do not reconstruct original grantor chains.

## Sequence privileges

`reader.getObjectPrivilegeReader().getAllFull(connection)` includes explicit
ALTER and USAGE grants from QSYS2.SYSSEQUENCEAUTH in the same bulk query as table/type
grants. Schema and object-name filters apply to both branches. *PUBLIC becomes
PUBLIC, known IS_GRANTABLE values determine WITH GRANT OPTION, and the reserved
GRANTOR column remains null. Authorization-list and unknown-grantability rows are
retained but require explicit resolution before SQL generation.

The shared `ObjectPrivilege` model keeps the native schema/object name.
`specifics["DB2_HOST_OBJECT_KIND"]` identifies SEQUENCE; its logical `specificName`
is `SEQUENCE:<object-name>` so same-named table and sequence grants remain distinct
through XML, clone and collection lookup. This is a sqlapp identity, not a native
sequence-specific name. Untagged legacy table grants remain supported; removing
the tag from a scoped grant cannot silently turn it into a table grant.

GRANT/REVOKE use ON SEQUENCE; REVOKE emits RESTRICT. Catalog CREATE includes its
object grants after creating schema objects. SQL generation uses the executing
principal and does not recreate authorization lists, group membership, special
authorities or effective permissions. No external database is used by unit tests.

## Routine privileges

`reader.getRoutinePrivilegeReader().getAllFull(connection)` reads SYSROUTINEAUTH
with one bulk join to SYSROUTINES to distinguish functions and procedures.
`RoutinePrivilege` retains specific schema/name, ordinary name, grantee and grant
option. Overloaded routines remain separate through XML and clone. Optional
`schemaName` and `objectName` filters use the existing ParametersContext overload.

GRANT/REVOKE supports EXECUTE and ALTER on SPECIFIC FUNCTION or SPECIFIC PROCEDURE.
PUBLIC is normalized from *PUBLIC, and REVOKE emits RESTRICT. Authorization-list
rows, unknown grantability and unresolved routine kinds remain in the model but
require explicit resolution before SQL generation. SYSROUTINEAUTH.GRANTOR is
reserved and remains null. The executing principal issues generated grants;
effective rights from group profiles and special authority are not inferred.

The shared RoutinePrivilege XML writer now preserves explicit `hierachy=false`
and a specific name equal to the ordinary name. Legacy XML without a hierarchy
attribute keeps its existing default; no public API or configuration is changed.

## Table and member aliases

`reader.getSchemaReader().getSynonymReader()` reads aliases from QSYS2.SYSTABLES
with one bulk query. The shared `Synonym` model retains the direct target schema
and name. `dbLinkName` holds a remote relational database name; a catalog matching
CURRENT SERVER or *LOCAL is normalized to a local target. The original catalog
name remains in `specifics["DB2_HOST_BASE_CATALOG"]`. No per-alias BASE_TABLE
function call or target resolution is performed, including for undefined targets.

CREATE/DROP use IBM i ALIAS syntax. A named file member or partition is retained
in `specifics["DB2_HOST_MEMBER_NAME"]` and rendered with the target. The catalog
default *FIRST is recreated by omitting the member clause. *LAST is retained but
modeled CREATE rejects it because support depends on the installed PTF level.
Remote aliases combined with explicit members also require an authoritative
complete CREATE ALIAS in `definition`. XML preserves these distinctions. This
does not recreate underlying DDS files, members or relational database directory
entries. IBM i sequence/module aliases are not enabled by this implementation.

## User-defined data types

`reader.getSchemaReader().getDomainReader()` reads QSYS2.SYSTYPES with one bulk
query, excluding built-in types. As in the LUW dialect, the shared `Domain` model
represents DISTINCT and ordinary array types. Source schema/name, semantic
precision/scale, string length, datetime precision, CCSID, normalization, allocated length, type
rules and exact cardinality are retained. Storage length is preserved separately
from decimal precision and datetime precision. Native attributes use
`specifics["DB2_HOST_..."]` and survive XML.

Modeled CREATE supports numeric, CHAR, VARCHAR, GRAPHIC, VARGRAPHIC, BINARY,
and VARBINARY DISTINCT types with explicit STRONG or WEAK TYPE RULES, and
ordinary arrays with the same built-in element types. DATE, TIME, and TIMESTAMP are also supported as
DISTINCT sources and ordinary array elements. TIMESTAMP requires the catalog
fractional-second precision (0 through 12). Newly read TIMESTAMP domains store
that precision in `length`, with `DB2_HOST_LENGTH_UNIT=FRACTIONAL_SECOND_DIGITS`.
Editing this model value changes generated DDL and survives XML. Legacy XML
without the unit marker retains its catalog-based precision behavior.
DECFLOAT DISTINCT types and ordinary arrays use the catalog storage length
of 8 or 16 bytes to recreate precision 16 or 34, respectively.
New metadata stores the precision in model `length`, marked with
`DB2_HOST_LENGTH_UNIT=DECIMAL_DIGITS`; editing it to 16 or 34 survives XML
serialization and changes generated DDL. Snapshots without the marker retain
their catalog storage-length interpretation.
LOB source lengths also survive model edits and XML serialization. DBCLOB maps
to NCLOB; older CLOB representations remain accepted for DDL. Unsupported scale
attributes and lengths on BOOLEAN or ROWID are rejected instead of ignored.
FLOAT sources use the catalog storage length of 4 or 8 bytes to recreate
REAL or DOUBLE, respectively.
Native BOOLEAN, introduced in IBM i 7.5, is mapped to the shared BOOLEAN type
and supported for DISTINCT sources and ordinary array elements.
ROWID DISTINCT types use the shared ROWID model and recreate the native source
without a length parameter. ROWID array definitions are rejected.
DATALINK DISTINCT types retain the shared DATALINK representation and recreate
the catalog maximum length (1 through 32717 bytes) and CCSID. Positive allocation
metadata requires a complete definition. DATALINK array definitions are rejected.
XML ordinary arrays recreate the catalog CCSID and UTF-8/UTF-16 normalization
without a length parameter. They accept only absent or zero allocation.
XML DISTINCT types additionally recreate an explicit catalog allocation and
STRONG or WEAK TYPE RULES. Missing allocation metadata requires a complete definition.
Ordinary arrays also support BLOB and CLOB up to 1048576 bytes and DBCLOB up
to 524288 double-byte characters. Character LOBs use the catalog CCSID and,
for UTF-8 or UTF-16, the normalization flag. LOB arrays accept only absent or
zero allocation.
LOB DISTINCT types support BLOB and CLOB up to 2147483647 bytes and DBCLOB up
to 1073741823 double-byte characters. They require an explicit catalog allocated
length, including 0 for no allocation. Character LOBs also require CCSID and,
for UTF-8 or UTF-16, normalization metadata.
Character and graphic sources require an explicit
CCSID and a normalization flag for UTF-8 or UTF-16. VARCHAR, VARGRAPHIC, and
VARBINARY DISTINCT types require a catalog allocated length (0 means no
ALLOCATE clause). VARCHAR, VARGRAPHIC, and VARBINARY arrays accept an absent
or zero allocation and emit no ALLOCATE clause. Graphic lengths and VARGRAPHIC allocation count double-byte
characters. Older XML without this metadata can be refreshed from the catalog or
supplied with a complete CREATE TYPE definition. DROP uses TYPE with RESTRICT.
Graphic sources with CCSID 65535 and unsupported attributes require
an authoritative complete CREATE TYPE in `definition`. Missing or excessive
array bounds cannot silently become unbounded arrays. Generated cast-function
ownership and IBM i system object names are not recreated. Type grants are handled
by the object privilege reader above.

Table/view DISTINCT columns retain a schema-qualified `Column.domain` reference
from SYSCOLUMNS.USER_DEFINED_TYPE_SCHEMA/NAME. This includes weak types even when
DATA_TYPE reports a built-in base type. CREATE TABLE quotes each identifier and
does not repeat the source type's length or scale. XML and clone retain the
reference. Table/view loading remains six/two bulk queries without a separate
type lookup. Create the referenced type before the table. Array columns, identity
on a DISTINCT column, per-column type sizing and unresolved identities are
rejected. Native default SQL is preserved as opaque text and is not rewritten
when referenced objects are renamed.

## Extended indexes, physical attributes, temporal tables and access control

These additions keep the bulk-query design: tables still load in six queries
(partitions and temporal state are joined into the table query), and a full
schema read adds one mask query and one row-permission query per schema.
Each attribute is stored in the shared Schema model where one exists
(`Index.where`, `Index.includes`, `ReferenceColumn.statement`,
`Table.partitioning`, `Table.temporalPeriods`, `Table.systemVersioning`,
`Schema.masks`) and otherwise in documented specifics
(`Db2HostIndexes`, `Db2HostTables`). All of it survives XML and clone.
Catalog values that cannot be represented fail instead of being dropped.

| Area | Db2 for z/OS 12/13 | Db2 for i 7.4/7.5/7.6 |
| --- | --- | --- |
| Index keys | Expression keys from SYSKEYTARGETS.DERIVED_FROM | Expression keys from SYSKEYS.KEY_EXPRESSION |
| Index options | INCLUDE (unique indexes), CLUSTER, PARTITIONED, UNIQUE WHERE NOT NULL | Sparse `WHERE`, PARTITIONED / NOT PARTITIONED, UNIQUE WHERE NOT NULL |
| Placement | `IN db.ts` for an explicit table space, `IN DATABASE db` for an implicit one | `FOR SYSTEM NAME` when the system name differs |
| Encoding | Table `CCSID EBCDIC/ASCII/UNICODE`; column `FOR BIT/SBCS/MIXED DATA` | Column `CCSID n`; CCSID 65535 becomes `FOR BIT DATA` |
| Partitioning | Table-controlled RANGE (`PARTITION n ENDING AT (...)`) | RANGE (`STARTING FROM ... ENDING AT ...`) and `HASH ... INTO n PARTITIONS` |
| Temporal | SYSTEM_TIME and BUSINESS_TIME periods, history tables (TYPE H), versioning incl. ON DELETE ADD EXTRA ROW, BUSINESS_TIME WITHOUT OVERLAPS keys and unique indexes | SYSTEM_TIME period and versioning from SYSPERIODS |
| Table options | APPEND YES, VOLATILE, DATA CAPTURE CHANGES, AUDIT CHANGES/ALL, VALIDPROC | — |
| Archiving | Archive tables (TYPE R) and `ALTER TABLE ... ENABLE ARCHIVE USE ...` | — |
| Generated columns | ROW BEGIN/END, TRANSACTION START ID, ROW CHANGE TIMESTAMP, IMPLICITLY HIDDEN | Same |
| Access control | Column masks, explicit row permissions, ACTIVATE ROW/COLUMN ACCESS CONTROL | Same |

DDL notes:

- Table CREATE emits `PERIOD` clauses before the keys, and separate
  `ALTER TABLE ... ADD VERSIONING USE HISTORY TABLE ...` and
  `ALTER TABLE ... ENABLE ARCHIVE USE ...` statements. Schema CREATE moves
  these ALTERs after all tables, so history and archive tables exist first.
- BUSINESS_TIME WITHOUT OVERLAPS is stored as
  `specifics["DB2_HOST_BUSINESS_TIME_WITHOUT_OVERLAPS"]` on the key or index;
  the period columns are not repeated in its column list.
- Schema CREATE emits masks after the other objects, then row permissions,
  then ACTIVATE statements, so a table is never activated before its rules
  exist. Implicit default permissions are not read; activation recreates them.
  Masks are created without OR REPLACE because z/OS does not accept it.
- z/OS table spaces and databases are referenced, not created. Implicitly
  created ones are omitted so Db2 creates them again.
- Platform-specific attributes are validated against the target dialect: for
  example `INCLUDE`/`CLUSTER`/BUSINESS_TIME on IBM i, or a sparse index, a
  column CCSID or HASH partitioning on z/OS, fail explicitly.

Behavior changes for existing users:

- Host TIMESTAMP precision is now read from SYSCOLUMNS.SCALE (z/OS) and
  SYSCOLUMNS.DATETIME_PRECISION (IBM i). It was previously taken from the
  storage length, which produced invalid precisions such as `TIMESTAMP(10)`.
- z/OS table reads include history and archive tables (SYSTABLES.TYPE H
  and R); schema discovery includes schemas that contain them.
- z/OS tables with an edit procedure (EDITPROC) now fail explicitly. They were
  previously read without it, which silently dropped the procedure.
- Tables with row-begin/end, transaction-start-ID or row change timestamp
  columns, z/OS FOR BIT DATA columns and IBM i explicit CCSIDs used to fail or
  lose these attributes; they are now read and recreated.

Not covered, or verified only by unit tests:

- z/OS `LIMITKEY` and IBM i `LOWVALUE`/`HIGHVALUE` are replayed verbatim as
  partition bounds. Their external format has not been checked against a
  server for every key type.
- IBM i partitioning is read from the statistical views SYSTABLESTAT and
  SYSPARTITIONSTAT, joined only when SYSTABLES.PARTITION_TABLE is not `NO`.
  These views can be slow on large partitioned tables. The PARTITIONING_KEYS
  list must name existing columns. Distributed (DB2 Multisystem) tables fail.
- IBM i IS_SPANNING_INDEX is mapped from `YES`/`NO`; any other value fails.
- z/OS EXCLUDE NULL KEYS, PADDED, COMPRESS, buffer pools and storage groups
  are not recreated. Db2 does not expose EXCLUDE NULL KEYS in SYSINDEXES, so
  it cannot be detected.
- z/OS EDITPROC fails because WITH/WITHOUT ROW ATTRIBUTES cannot be
  determined from the catalog columns read here.
- z/OS inclusive BUSINESS_TIME ends (key period role `I`) and row-begin/end
  columns outside a SYSTEM_TIME period fail.
- IBM i record format names (RCDFMT) are not available from the SQL catalog
  views used here and are not recreated. IBM i versioning that is defined but
  not established (VERSIONING_STATUS `D`) fails.
- Index-controlled partitioning (z/OS legacy) fails explicitly.

## Materialized query tables, sequence values, schema diff and global variables

- Materialized query tables (SYSTABLES.TABLE_TYPE `M`) are read as `Mview`
  with their columns and indexes. Only the query is stored in the catalog, so
  the statement is rebuilt as
  `CREATE TABLE name (columns) AS (query) DATA INITIALLY DEFERRED REFRESH DEFERRED
  ENABLE|DISABLE QUERY OPTIMIZATION MAINTAINED BY USER`. Other refresh or
  maintenance modes fail. The MQT isolation level is kept in specifics but is
  not emitted.
- `SqlType.SEQUENCE_NEXT_VALUES` produces a recursive common table expression
  over SYSIBM.SYSDUMMY1 that returns `NEXT VALUE FOR` the sequence n times.
- Diff (`ALTER`/`DROP`) support: added/removed objects use the registered
  CREATE/DROP factories. Table changes cover `RENAME TABLE`, `ADD COLUMN`,
  `DROP COLUMN ... RESTRICT`, widening `SET DATA TYPE`, `SET/DROP DEFAULT`,
  `SET/DROP NOT NULL`, comments, indexes and constraints. Column renames fail
  because IBM i has no `RENAME COLUMN`. Sequences are altered in place
  (INCREMENT, MIN/MAX, CYCLE, CACHE, ORDER); a changed START value fails. Index
  structure changes drop and recreate the index. Masks are enabled/disabled in
  place. Views, MQTs, triggers, types, aliases and global variables accept
  only comment changes; anything else fails with an explicit-plan error.
  Routine ALTER/DROP is not registered.
- Global variables (QSYS2.SYSVARIABLES) are read as `Constant` with
  `DB2_OBJECT_TYPE=VARIABLE`, the type in `DB2_HOST_VARIABLE_*` specifics and
  the default expression in `statement`. CREATE emits
  `CREATE VARIABLE name type [CCSID n] [DEFAULT (expression) | DEFAULT NULL]`
  plus `COMMENT ON VARIABLE`; DROP emits `DROP VARIABLE name`. Timestamp
  precision comes from DATETIME_PRECISION. Variables whose READONLY value is
  not `N` fail. A complete `CREATE VARIABLE` in `definition` is replayed
  verbatim.

## Limits and verification

**No real Db2 for i server has been used to validate this implementation.**
Unit tests exercise JDBC product identification, resource loading and SQL binding,
mocked catalog row conversion, query counts and generated DDL. They do not prove
that the catalog queries execute or that the DDL is accepted by a server.

This is a logical-table baseline with the selected physical, temporal and
security attributes listed above; it is not a full-fidelity database backup or
migration implementation. Storage options (buffer pools, storage groups,
compression), table space creation, DDS file attributes, encoded-vector
indexes and unlisted privilege categories remain outside coverage. Routine/trigger recreation has the limits above. Unrepresented physical attributes cannot be recreated.
Schema discovery on z/OS lists schemas containing ordinary, history or archive tables, views, user
sequences, active user routines, triggers, aliases, legacy synonyms or types. IBM i uses
SQL naming and ordinary SQL tables; DDS physical/logical file recreation is not supported.

Unsupported generated columns and unresolved referenced keys fail explicitly.
Non-enforced/disabled constraints and physical options that are not modeled
are rejected by the CREATE path. Unsupported types and versions fail explicitly. Advanced
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

### Supplemental catalog specifications

- [IBM i SYSTABLES (system name, PARTITION_TABLE, CONTROL)](https://www.ibm.com/support/knowledgecenter/ssw_ibm_i_72/db2/rbafzcatsystbls.htm)
- [IBM i SYSCOLUMNS (CCSID, DATETIME_PRECISION)](https://www.ibm.com/support/knowledgecenter/ssw_ibm_i_74/db2/rbafzcatsyscol.htm)
- [IBM i SYSKEYS (KEY_EXPRESSION)](https://www.ibm.com/support/knowledgecenter/ssw_ibm_i_74/db2/rbafzcatsyskeys.htm)
- [IBM i SYSINDEXES (SEARCH_CONDITION, IS_SPANNING_INDEX)](https://www.ibm.com/support/knowledgecenter/ssw_ibm_i_72/db2/rbafzcatsysindex.htm)
- [IBM i SYSPARTITIONSTAT](https://ibm.com/support/knowledgecenter/ssw_ibm_i_73/db2/rbafzcatsyspstat.htm)
- [IBM i SYSCONTROLS (masks and permissions)](https://www.ibm.com/support/knowledgecenter/ssw_ibm_i_74/db2/rbafzcatsyscontrols.htm)
- [IBM i SYSVARIABLES (global variables)](https://www.ibm.com/support/knowledgecenter/ssw_ibm_i_72/db2/rbafzcatsysvariables.htm)
- [IBM i schema privilege catalog](https://www.ibm.com/docs/en/i/7.5?topic=views-sysschemaauth)
- [IBM i schema GRANT](https://www.ibm.com/docs/en/i/7.5.0?topic=statements-grant-schema-privileges)
- [IBM i schema REVOKE](https://www.ibm.com/docs/en/i/7.5.0?topic=statements-revoke-schema-privileges)
- [IBM i type privileges](https://www.ibm.com/docs/en/i/7.5.0?topic=views-sysudtauth)
- [IBM i type GRANT](https://www.ibm.com/docs/en/i/7.5.0?topic=statements-grant-type-privileges)
- [IBM i type REVOKE](https://www.ibm.com/docs/en/i/7.5.0?topic=statements-revoke-type-privileges)
- [IBM i sequence privileges](https://www.ibm.com/docs/en/i/7.5.0?topic=views-syssequenceauth)
- [IBM i sequence GRANT](https://www.ibm.com/docs/en/i/7.5.0?topic=statements-grant-sequence-privileges)
- [IBM i sequence REVOKE](https://www.ibm.com/docs/en/i/7.5.0?topic=statements-revoke-sequence-privileges)
- [IBM i routine privileges](https://www.ibm.com/docs/en/i/7.5.0?topic=views-sysroutineauth)
- [IBM i routine GRANT](https://www.ibm.com/docs/en/i/7.5.0?topic=statements-grant-function-procedure-privileges)
- [IBM i routine REVOKE](https://www.ibm.com/docs/en/i/7.5.0?topic=statements-revoke-function-procedure-privileges)
- [IBM i weak type references in catalogs](https://www.ibm.com/docs/en/i/7.5.0?topic=udts-weakly-typed-database-catalogs)
- [IBM i user-defined type catalog](https://www.ibm.com/docs/en/i/7.5.0?topic=views-systypes)
- [IBM i DISTINCT types](https://www.ibm.com/docs/en/i/7.5.0?topic=statements-create-type-distinct)
- [IBM i array types](https://www.ibm.com/docs/en/i/7.5.0?topic=statements-create-type-array)
- [IBM i alias catalog fields](https://www.ibm.com/docs/en/i/7.5.0?topic=views-systables)
- [IBM i CREATE ALIAS](https://www.ibm.com/docs/en/i/7.5.0?topic=statements-create-alias)
- [IBM i routines](https://www.ibm.com/docs/en/i/7.5.0?topic=views-sysroutines)
- [IBM i routine parameters and returns](https://www.ibm.com/docs/en/i/7.5.0?topic=views-sysparms)
- [IBM i triggers](https://www.ibm.com/docs/en/i/7.5.0?topic=views-systriggers)
- [IBM i table privileges](https://www.ibm.com/docs/en/i/7.5.0?topic=views-systabauth)
- [IBM i column privileges](https://www.ibm.com/docs/en/i/7.5.0?topic=views-syscolauth)
- [z/OS routines](https://www.ibm.com/docs/en/db2-for-zos/13.0.0?topic=tables-sysroutines)
- [z/OS view statements](https://www.ibm.com/docs/en/db2-for-zos/13.0.0?topic=tables-sysviews)
- [IBM i view definitions](https://www.ibm.com/docs/en/i/7.5.0?topic=views-sysviews)
- [z/OS sequences](https://www.ibm.com/docs/en/db2-for-zos/13.0.0?topic=tables-syssequences)
- [IBM i sequences](https://www.ibm.com/docs/en/i/7.5.0?topic=views-syssequences)
- [z/OS index keys](https://www.ibm.com/docs/en/db2-for-zos/13.0.0?topic=tables-syskeys)
- [IBM i indexes](https://www.ibm.com/docs/en/i/7.5.0?topic=views-sysindexes)
- [IBM i index keys](https://www.ibm.com/docs/en/i/7.5.0?topic=views-syskeys)
- [z/OS checks](https://www.ibm.com/docs/en/db2-for-zos/13.0.0?topic=tables-syschecks)
- [IBM i checks](https://www.ibm.com/docs/en/i/7.5.0?topic=views-syschkcst)

Run unit tests with the repository Java 25 Gradle Wrapper:

```powershell
./gradlew.bat :sqlapp-core-db2:test :sqlapp-core-db2-zos:test :sqlapp-core-db2-i:test
```
