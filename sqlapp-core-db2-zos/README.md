# Db2 for z/OS 13 (experimental)

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

The concrete entry point is `Db2Zos13`. Only Db2 for z/OS 13 is an initial target;
older and newer versions are rejected rather than mapped speculatively. No JDBC
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
| Ordinary indexes | Ordered ASC/DESC column keys, uniqueness and index schema | CREATE INDEX after table creation, including a different index schema |
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

Ordinary indexes are supported; incomplete key lists, expressions, INCLUDE keys,
unsupported ordering and special uniqueness/encoded-vector semantics fail. z/OS
constraint-backed and implicitly generated indexes are excluded from standalone
index creation; primary/unique constraints remain represented separately.
Physical index storage options and clustering are not recreated. IBM i catalog
views here cover SQL-created indexes, not DDS logical files.

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

Explicit table and column privileges each use one bulk query. Table GRANT/REVOKE
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

Modeled CREATE supports numeric DISTINCT types and ordinary numeric arrays.
DROP uses TYPE rather than SQL-standard DOMAIN, with RESTRICT. Names and comments
are quoted. Associative arrays, character/LOB sources, named sources and other
unsupported attributes require an authoritative complete CREATE TYPE in
`definition`. Cardinalities that cannot fit the shared integer bound are retained
in specifics and cannot silently become an unbounded modeled array.
Type privileges, generated cast-function ownership and physical attributes are
not recreated by this reader.

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

## Limits and verification

**No real Db2 for z/OS server has been used to validate this implementation.**
Unit tests exercise JDBC product identification, resource loading and SQL binding,
mocked catalog row conversion, query counts and generated DDL. They do not prove
that the catalog queries execute or that the DDL is accepted by a server.

This is a basic logical-table baseline, not a full-fidelity database backup or
migration implementation. Physical placement/encoding (including CCSID), table
spaces, partitioning, temporal/security attributes, DDS/system names,
expression/partial/encoded-vector indexes and unlisted privilege categories
remain outside coverage. Routine/trigger recreation has the limits above. Unrepresented physical attributes cannot be recreated.
Schema discovery on z/OS lists schemas containing ordinary tables, views, user
sequences, active user routines, triggers, aliases, legacy synonyms or types. IBM i uses
SQL naming and ordinary SQL tables; DDS physical/logical file recreation is not supported.

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

### Supplemental catalog specifications

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
