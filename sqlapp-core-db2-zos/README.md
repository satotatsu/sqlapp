# Db2 12 and 13 for z/OS (experimental)

This separate Gradle/Maven artifact depends on `sqlapp-core-db2`, following the
PostgreSQL/MySQL derivative dialect pattern. No `sqlapp-core-db2-common` artifact
is required. The existing DB2 artifact remains the LUW entry point. Basic type
registrations and SQL builders are reused; bulk row mapping is shared in its
`common` package. The product-specific catalog SQL lives in this module.

## Use

Add `com.sqlapp:sqlapp-core-db2-zos` with the same version as the other sqlapp
artifacts. JDBC connections are resolved automatically using product metadata;
DRDA product-version prefixes DSN (z/OS) and QSQ (IBM i) distinguish ambiguous
`DB2` names. Offline, use the explicit product name and supported version:

```java
Dialect dialect = DialectResolver.getInstance().getDialect("Db2 for z/OS", 13, 0);
CatalogReader reader = dialect.getCatalogReader();
SchemaReader schemas = reader.getSchemaReader();
schemas.setSchemaName("APP");
List<Schema> model = schemas.getAllFull(connection);
List<SqlOperation> ddl = dialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE);
```

The concrete entry points are `Db2Zos12` and `Db2Zos13`; the resolver selects
one from the JDBC version. Db2 12 reuses the Db2 13 catalog SQL because every
catalog column read here also exists in Db2 12. Features that depend on a
function level still require that level on the subsystem. Other releases are
rejected rather than mapped speculatively. No JDBC
driver is bundled. Supply the appropriate driver and connection credentials.

## Initial coverage

- Schema/table names and basic table/column remarks from `SYSIBM` catalogs.
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
`Catalog.schemaPrivileges`. It uses one bulk SYSIBM.SYSSCHEMAAUTH query, independent
of the number of schemas. CREATEIN, ALTERIN and DROPIN retain grant options,
grantor/grantee identity and native authorization provenance. No LUW-only schema
authorities are inferred. For a schema-privilege-only request, use the existing
Db2 reader API through the host catalog reader:

```java
var metadata = (AbstractDb2HostCatalogReader) dialect.getCatalogReader();
var privileges = metadata.getSchemaPrivilegeReader();
privileges.setSchemaName("APP"); // optional; omit to read all visible grants
List<SchemaPrivilege> grants = privileges.getAllFull(connection);
```

The optional ParametersContext overload can select a set of named schemas.
`SCHEMANAME='*'` is retained as an all-schema scope with
`specifics["DB2_HOST_SCHEMA_SCOPE"]="ALL"`; it cannot silently become an individual
schema grant. GRANT/REVOKE use ON SCHEMA with quoted names and native PUBLIC/ROLE
syntax. Schema REVOKE does not append RESTRICT. XML, clone and Catalog CREATE
include schema grants. Unknown grantability and unsupported grantees require
explicit resolution; generated SQL does not restore original grantor chains or
effective permissions. The new getter is additive; the existing reader API and
configuration remain compatible.

## Type privileges

The object privilege reader includes USAGE on user-defined types from
SYSIBM.SYSRESAUTH, restricted to OBTYPE='D'. Buffer-pool, collection, storage-group,
table-space and JAR rows from that catalog are not interpreted as type grants.
The existing schema/name filters also apply to this branch of the single bulk
query; no per-type catalog lookup is needed.

Type grants retain native schema/name in `ObjectPrivilege`, object kind TYPE in
`specifics["DB2_HOST_OBJECT_KIND"]`, and logical identity `TYPE:<object-name>` in
`specificName`. Table, sequence and type grants with the same SQL name remain
distinct through XML, clone and collection lookup. Catalog CREATE includes these
grants. GRANT/REVOKE use ON TYPE, known WITH GRANT OPTION and RESTRICT for REVOKE.
ALTER on a type is not enabled for z/OS. USAGE does not grant EXECUTE on generated
cast functions; those are separate routine privileges.

Authorization IDs, PUBLIC and native role grantees are supported. Internal grantee
codes are retained as unsupported inventory and cannot be misread as roles;
this distinction also applies to table, column, sequence and routine grants.
Unknown grantability requires explicit resolution before SQL generation. The
executing principal issues generated grants; original grantor chains and effective
permissions are not reconstructed.

## Sequence privileges

`reader.getObjectPrivilegeReader().getAllFull(connection)` includes explicit
ALTER and USAGE grants from SYSIBM.SYSSEQUENCEAUTH in the same bulk query as table/type
grants. Schema and object-name filters apply to both branches. Grant options,
authorization IDs, roles and PUBLIC retain their native meaning. Package/plan
grantees remain inventory only, with collection/token and authorization provenance
retained where exposed.

The shared `ObjectPrivilege` model keeps the native schema/object name.
`specifics["DB2_HOST_OBJECT_KIND"]` identifies SEQUENCE; its logical `specificName`
is `SEQUENCE:<object-name>` to distinguish a sequence from a same-named table in
collection identities. This is a sqlapp identity, not a native sequence-specific
name. XML, clone and collection lookup preserve the distinction. Untagged legacy
table grants remain supported. Removing the object-kind tag from a scoped grant
is rejected rather than silently targeting a table.

GRANT uses ON SEQUENCE and known WITH GRANT OPTION; REVOKE uses ON SEQUENCE with
RESTRICT. Catalog CREATE now includes its object grants after object creation.
Generated SQL uses the executing principal and does not reconstruct original
grantor chains, plan/package authorization or effective permissions. No external
database is used by the unit tests.

## Routine privileges

`reader.getRoutinePrivilegeReader().getAllFull(connection)` reads SYSROUTINEAUTH
with one bulk query. A grouped join to SYSROUTINES resolves routine names without
duplicating privilege rows for procedure versions. `RoutinePrivilege` retains
schema, ordinary name, specific name, grantor, grantee and grant option. Native
routine kind, grantee kind, package collection/token and authorization provenance
remain in `specifics["DB2_HOST_..."]`. Optional `schemaName` and `objectName`
filters can be supplied through the existing ParametersContext overload.

GRANT/REVOKE EXECUTE targets SPECIFIC FUNCTION for functions and the ordinary
PROCEDURE name for procedures, matching z/OS grammar. Role grantees and PUBLIC
use native syntax; REVOKE emits RESTRICT. Grants for all routines in a schema,
plan/package grantees and missing/ambiguous routine resolutions are retained for
inventory but cannot be replayed as individual grants. Generating SQL does not
restore the original grantor, special authorities or effective permissions.

The shared RoutinePrivilege XML writer now preserves explicit `hierachy=false`
and a specific name equal to the ordinary name. Legacy XML without a hierarchy
attribute keeps its existing default; no public API or configuration is changed.

## Aliases and legacy synonyms

`reader.getSchemaReader().getSynonymReader()` reads table/view aliases,
sequence aliases and legacy synonyms with one bulk UNION query. The shared
`Synonym` model retains the direct target schema/name; `dbLinkName` holds the
remote location, and `specifics["ALIAS_TYPE"]` distinguishes TABLE and SEQUENCE.
Aliases are not resolved to a final base object, so undefined targets and alias
chains are retained without additional target queries. Schemas containing only
aliases or legacy synonyms are included in schema discovery.

CREATE/DROP use host ALIAS syntax, including three-part table targets and
FOR SEQUENCE for sequence aliases. Identifiers are quoted separately. Sequence
aliases with remote locations are rejected. XML retains target identity and
object kind. `specifics["DB2_HOST_NAME_KIND"]` distinguishes ALIAS from legacy
SYNONYM. Legacy synonyms are inventory only: new synonyms are unsupported at
modern APPLCOMPAT levels, and silently converting them to aliases would change
visibility and lifetime semantics. Both CREATE and DROP for these legacy rows
fail explicitly rather than treating them as aliases.
The shared Synonym collection identifies objects by schema/name. If different
alias namespaces contain that same identity, loading fails explicitly instead
of silently discarding one of the objects.

## User-defined data types

`reader.getSchemaReader().getDomainReader()` reads SYSIBM.SYSDATATYPES with one
bulk query. As in the LUW dialect, the shared `Domain` model represents DISTINCT
and array types. Source schema/name, precision/scale, array cardinality, string
subtype, encoding, inline length and associative-index attributes are retained.
Native attributes use `specifics["DB2_HOST_..."]`; internal index type IDs are
preserved without guessing an undocumented mapping. Schema discovery includes
schemas containing only user-defined types.

Modeled CREATE supports ordinary numeric, binary, datetime, VARCHAR, BLOB, and
CLOB arrays. LOB arrays require the catalog's unspecified inline length (-1).
VARCHAR arrays also support the catalog BIT subtype, emitting FOR BIT DATA
with the recorded encoding scheme.
CHAR and VARCHAR DISTINCT sources also support FOR BIT DATA.
Supported DISTINCT sources include numeric types, CHAR, VARCHAR, BINARY,
VARBINARY, BLOB, CLOB, DATE, TIME, and TIMESTAMP. TIMESTAMP without time zone
uses the catalog fractional-second precision
(0 through 12), including the catalog's legacy representation of precision 6.
Newly read TIMESTAMP domains store fractional-second precision in `length`,
with `DB2_HOST_LENGTH_UNIT=FRACTIONAL_SECOND_DIGITS`. Edits survive XML and
change generated DDL. Legacy XML without the marker retains catalog-based precision.
ROWID DISTINCT types use the shared ROWID model and recreate the native source
without a length parameter. ROWID array definitions are rejected.
Character sources require a catalog
encoding scheme and a supported character subtype. BLOB and CLOB use the catalog
inline length, including -1 for an unspecified inline length. Length and inline
length are validated against the z/OS 13 limits and retained through XML round trips.
Edited LOB source lengths also survive XML serialization and change generated
DDL. Unsupported scale attributes and lengths on ROWID are rejected instead of
being silently omitted.
DROP uses TYPE rather than SQL-standard DOMAIN, with RESTRICT. Names and comments
are quoted. Associative arrays, graphic sources, named sources and other
unsupported attributes require an authoritative complete CREATE TYPE in
`definition`. Cardinalities that cannot fit the shared integer bound are retained
in specifics and cannot silently become an unbounded modeled array.
Generated cast-function ownership and physical attributes are not recreated by
this reader. Type USAGE grants are handled by the object privilege reader above.

Table/view DISTINCT columns retain a schema-qualified `Column.domain` reference
from SYSCOLUMNS.TYPESCHEMA/TYPENAME, using the existing column query. CREATE TABLE
quotes each part of the referenced type name and does not repeat its base length
or precision. XML and clone retain the reference. Loading tables still takes six
bulk queries, and views two; no additional type lookup is needed. Create the
referenced type before creating the table. Array columns, identity on a DISTINCT
column, per-column type sizing and unresolved type identities are rejected.
Native DISTINCT literal defaults use the qualified generated cast function;
the source-type join also distinguishes graphic string literals. An implicit
non-null DISTINCT default cannot be safely reconstructed from the
current column query and is rejected. Explicit default SQL remains opaque and
is not rewritten when referenced objects are renamed.

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

## Limits and verification

**No real Db2 for z/OS server has been used to validate this implementation.**
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

- [z/OS SYSTABLES (partitioning, versioning, access control)](https://www.ibm.com/support/knowledgecenter/en/SSEPEK_12.0.0/cattab/src/tpc/db2z_sysibmsystablestable.html)
- [z/OS SYSCOLUMNS (DEFAULT, PERIOD, HIDDEN, partition keys)](https://www.ibm.com/support/knowledgecenter/en/SSEPEK_10.0.0/cattab/src/tpc/db2z_sysibmsyscolumnstable.html)
- [z/OS SYSTABLEPART (LIMITKEY)](https://www.ibm.com/support/knowledgecenter/SSEPEK_12.0.0/cattab/src/tpc/db2z_sysibmsystableparttable.html)
- [z/OS SYSKEYTARGETS (index on expression)](https://www.ibm.com/support/knowledgecenter/en/SSEPEK_12.0.0/cattab/src/tpc/db2z_sysibmsyskeytargetstable.html)
- [z/OS SYSCONTROLS (masks and permissions)](https://ibm.com/docs/SSEPEK_13.0.0/cattab/src/tpc/db2z_sysibmsyscontrolstable.html)
- [z/OS schema privilege catalog](https://www.ibm.com/docs/en/db2-for-zos/13.0.0?topic=tables-sysschemaauth)
- [z/OS schema GRANT](https://www.ibm.com/docs/en/db2-for-zos/13.0.0?topic=statements-grant-schema-privileges)
- [z/OS schema REVOKE](https://www.ibm.com/docs/en/db2-for-zos/13.0.0?topic=statements-revoke-schema-privileges)
- [z/OS type privilege catalog](https://www.ibm.com/docs/en/db2-for-zos/13.0.0?topic=tables-sysresauth)
- [z/OS type GRANT](https://www.ibm.com/docs/en/db2-for-zos/13.0.0?topic=statements-grant-type-jar-file-privileges)
- [z/OS type REVOKE](https://www.ibm.com/docs/en/db2-for-zos/13.0.0?topic=statements-revoke-type-jar-file-privileges)
- [z/OS sequence privileges](https://www.ibm.com/docs/en/db2-for-zos/13.0.0?topic=spaces-syssequenceauth)
- [z/OS sequence GRANT](https://www.ibm.com/docs/en/db2-for-zos/13.0.0?topic=statements-grant-sequence-privileges)
- [z/OS sequence REVOKE](https://www.ibm.com/docs/en/db2-for-zos/13.0.0?topic=statements-revoke-sequence-privileges)
- [z/OS routine privileges](https://www.ibm.com/docs/en/db2-for-zos/13.0.0?topic=tables-sysroutineauth)
- [z/OS routine GRANT](https://www.ibm.com/docs/en/db2-for-zos/13.0.0?topic=statements-grant-function-procedure-privileges)
- [z/OS routine REVOKE](https://www.ibm.com/docs/en/db2-for-zos/13.0.0?topic=statements-revoke-function-procedure-privileges)
- [z/OS user-defined type catalog](https://www.ibm.com/docs/en/db2-for-zos/13.0.0?topic=tables-sysdatatypes)
- [z/OS DISTINCT types](https://www.ibm.com/docs/en/db2-for-zos/13.0.0?topic=statements-create-type-distinct)
- [z/OS array types](https://www.ibm.com/docs/en/db2-for-zos/13.0.0?topic=statements-create-type-array)
- [z/OS alias catalog fields](https://www.ibm.com/docs/en/db2-for-zos/13.0.0?topic=design-retrieving-catalog-information-about-aliases)
- [z/OS CREATE ALIAS](https://www.ibm.com/docs/en/db2-for-zos/13.0.0?topic=statements-create-alias)
- [z/OS DROP](https://www.ibm.com/docs/en/db2-for-zos/13.0.0?topic=statements-drop)
- [z/OS routines](https://www.ibm.com/docs/en/db2-for-zos/13.0.0?topic=tables-sysroutines)
- [z/OS triggers](https://www.ibm.com/docs/en/db2-for-zos/13.0.0?topic=tables-systriggers)
- [z/OS table privileges](https://www.ibm.com/docs/en/db2-for-zos/13.0.0?topic=tables-systabauth)
- [z/OS column privileges](https://www.ibm.com/docs/en/db2-for-zos/13.0.0?topic=tables-syscolauth)
- [IBM i routine parameters and returns](https://www.ibm.com/docs/en/i/7.5.0?topic=views-sysparms)
- [IBM i triggers](https://www.ibm.com/docs/en/i/7.5.0?topic=views-systriggers)
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
