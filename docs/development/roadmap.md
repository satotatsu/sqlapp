# Roadmap

[Documentation index](../README.md) · [Section index](README.md)

This document records deferred features that need cross-database design or a
larger extension of the shared Schema model. It is not a release commitment.

## Excel schema-documentation export

An Excel workbook generated from the Schema model is a candidate for future
work. It would support users who prefer spreadsheet-based filtering, review,
comments and handoff, while Schema XML remains the canonical machine-readable
representation and HTML remains the complete documentation format.

The workbook should focus on information that works well as tables, such as
object and column dictionaries, constraints, indexes and documentation gaps.
Relationships, inheritance, partitioning, viewpoints and database-specific
DDL should continue to use HTML, SVG, Mermaid and DDL outputs rather than being
flattened into a spreadsheet. Before implementation, define stable worksheets,
identifier and hyperlink conventions, large-schema limits and whether edited
workbooks are output-only artifacts or supported inputs.

## Collation schema objects

Columns and catalogs currently retain a collation name, but the shared Schema
model does not represent a collation definition as an independent object.
PostgreSQL retains complete collation definitions through the dialect-local
[schema-object metadata extension](postgresql-schema-objects.md), including
metadata/XML/DDL recreation and PostgreSQL 18 `PG_UNICODE_FAST`. A portable
shared model and dedicated HTML object pages remain deferred.

Before adding a shared collation object, compare provider, locale,
determinism, rules and version concepts across PostgreSQL, DB2, Oracle,
SQL Server and MySQL-family databases, and define rename-safe references from
columns and indexes.

## Constraint lifecycle state

`NotNullConstraint` represents a named `NOT NULL` constraint in the
shared Schema model. PostgreSQL 18 DDL, metadata, XML, and HTML documentation
use this property.

Validation and trust state still needs a cross-database design. PostgreSQL has
`NOT VALID` and `convalidated`, Oracle has ENABLE/DISABLE with
VALIDATE/NOVALIDATE, SQL Server distinguishes disabled and untrusted
constraints, and DB2 exposes enforcement and validation-related states.

Before adding a shared property:

1. Separate enabled/enforced, validated/trusted, and inherited state.
2. Apply the model consistently to check, foreign-key, unique, and named
   `NOT NULL` constraints.
3. Define XML defaults that preserve existing documents.
4. Add HTML rendering only when a non-default state exists.
5. Add per-dialect metadata and DDL mappings with explicit version boundaries.

Named `NOT NULL` inheritance and PostgreSQL's catalog validation state are
retained by `NotNullConstraint.noInherit` and
`NotNullConstraint.validated`. These properties are intentionally scoped to
the named `NOT NULL` object; a common lifecycle contract for every constraint
type remains deferred.

## PostgreSQL modern feature follow-up

PostgreSQL-only object metadata and DDL recreation are documented in
[PostgreSQL schema objects](postgresql-schema-objects.md). The scope includes
extensions, collations, FDW/server/user-mapping/foreign-table objects, RLS,
extended statistics, publications and disconnected subscription recreation.

The PostgreSQL dialect currently has explicit version boundaries through
PostgreSQL 18. The implemented PostgreSQL 18 scope includes virtual generated
columns, temporal and named `NOT NULL` constraints, `OLD`/`NEW` returning,
modern `COPY`, logical replication additions, optimizer-statistics operations,
modern built-in functions, `postgres_fdw`, `dblink_fdw`, `file_fdw`,
`amcheck`, and `pg_buffercache` SQL support.

The PostgreSQL 18 table metadata reader retains `pg_class.relallfrozen` and the
new cumulative vacuum/analyze timing columns. Its catalog SQL does not depend
on the removed `pg_attribute.attcacheoff` column.

`PostgresBufferCacheBuilder.evict(bufferId)` generates a PostgreSQL 18 SELECT
that exposes both `buffer_evicted` and `buffer_flushed`, complementing the
relation/all eviction operations. Buffer IDs must be positive; the server
checks the actual pool bounds. For example:

```java
new PostgresBufferCacheBuilder(DialectHolder.postgreSQL180).evict(1);
// SELECT buffer_evicted, buffer_flushed FROM pg_buffercache_evict(1)
```

Execution requires the `pg_buffercache` extension and superuser privileges.
These operations are intended for developer testing, and concurrent activity
can change the reported buffer state immediately. PostgreSQL 17's scalar
eviction result is not supported by this new method.

The modern-feature follow-up now provides these optional SQL entry points:

| Entry point | Scope |
| --- | --- |
| `PostgresPgcryptoBuilder` | SHA-256/SHA-512 crypt salts, explicit iteration bounds, `crypt`, CFB encryption/decryption with explicit IV, and `fips_mode()` |
| `PostgresMonitoringBuilder` | AIO handles, backend I/O/WAL counters, explicit backend-statistics reset, pg_stat_statements 1.12 parallel/WAL fields, loaded modules and NUMA information |
| `PostgresLogicalInspectBuilder` | Snapshot metadata/information, with escaped filenames and rejection of directory paths |
| `PostgresExplainBuilder.debug/rangeTable` | Optional pg_overexplain diagnostics |
| `PostgresCreateDatabaseBuilder` | Explicit database creation from a Schema `Catalog`, with optional template and `FILE_COPY`/`WAL_LOG` strategy |
| `PostgresExtensionSettingsBuilder` | Session-scoped `passwordcheck.min_password_length`, measured in bytes |

These new operations require PostgreSQL 18, except database creation strategy,
which is supported from PostgreSQL 15. Database creation without a strategy
preserves the server default. Catalog CREATE continues to recreate contained
objects; database creation is an explicit separate operation. For example:

```java
var dialect = DialectHolder.postgreSQL180;
var crypto = new PostgresPgcryptoBuilder(dialect);
String expression = crypto.crypt("?", crypto.genSalt("sha512crypt", 10000));
// Use expression in a prepared SELECT; bind the password through JDBC.
String ioSql = new PostgresMonitoringBuilder(dialect).backendIo(1234);
String databaseSql = new PostgresCreateDatabaseBuilder(dialect)
    .template("template0").strategy("FILE_COPY").build(new Catalog("reporting"));
```

Expression arguments are trusted SQL, not string values. Bind passwords, keys
and IVs through JDBC placeholders. Iteration count omission uses the server
default, not an application security recommendation. Raw CFB encryption does
not authenticate ciphertext. pgcrypto requires OpenSSL and the installed
extension. The server still validates extension availability, runtime settings,
permissions and actual object/process identities; SQL generation does not
probe a connection or suppress server errors.

pg_stat_statements must be preloaded and upgraded to version 1.12 for the new
fields; its optional schema argument supports non-default installation schemas.
pg_overexplain and passwordcheck must be loaded in the executing session.
Logical snapshot inspection requires pg_logicalinspect plus superuser or
server-file privileges. NUMA views require a compatible server build and host.
Database creation requires CREATEDB privileges and must execute outside a
transaction block. FILE_COPY can force checkpoints. Backend reset and eviction
are explicit calls with side effects, never automatic monitoring operations.

Table CREATE now rejects an UNLOGGED partitioned parent rather than generating
unsupported DDL. Ordinary UNLOGGED tables remain supported. Existing methods,
configuration formats, shared Schema classes and module dependencies are unchanged.

`PostgresMetadataReaderTest` contains disposable PostgreSQL 18.4 coverage for
tables, columns, constraints, indexes, stored/virtual generated columns, named
NOT NULL, table statistics and extension/monitoring SQL. NUMA execution has a
separate test that explicitly skips on an unsupported host. Logical snapshot
inspection is exercised against a real serialized snapshot generated through
a logical replication slot in the disposable server.

Run it with the repository wrapper and a local Docker engine:

```bat
.\gradlew.bat :sqlapp-core-dialect-test:dockerTest --tests com.sqlapp.data.db.dialect.test.postgres.PostgresMetadataReaderTest
```

Server contracts: [pgcrypto](https://www.postgresql.org/docs/18/pgcrypto.html),
[statistics](https://www.postgresql.org/docs/18/monitoring-stats.html),
[pg_logicalinspect](https://www.postgresql.org/docs/18/pglogicalinspect.html),
[pg_overexplain](https://www.postgresql.org/docs/18/pgoverexplain.html), and
[CREATE DATABASE](https://www.postgresql.org/docs/18/sql-createdatabase.html).
The new statement-statistics columns follow the upstream
[pg_stat_statements 1.12 definition](https://github.com/postgres/postgres/blob/REL_18_STABLE/contrib/pg_stat_statements/pg_stat_statements--1.11--1.12.sql).

Command-line applications such as `pg_dump`, `pg_upgrade`, `vacuumdb`,
`pg_createsubscriber`, and `pg_recvlogical`, as well as `psql` commands and
server configuration for OAuth, TLS and asynchronous I/O, are outside the
core dialect scope unless sqlapp later introduces a dedicated PostgreSQL
administration-command module.

Shared collation objects and cross-database constraint lifecycle state remain
covered by their dedicated roadmap sections above.

## Data use case domains

### Current scope

Oracle Database 23ai supports single-column data use case domains through the
existing `Domain` model.

The Oracle dialect currently generates:

- `CREATE DOMAIN` and `DROP DOMAIN`
- base data type and `STRICT`
- `DEFAULT` and `DEFAULT ON NULL`
- `NOT NULL` and single or multiple named `CHECK` constraints
- constraint deferrability
- `DISPLAY` and `ORDER` expressions
- object-level schema annotations

Single-column domain definitions and object-level annotations can be loaded
from Oracle metadata when the relevant catalog views are available. Missing
views or privileges produce a warning and leave the remaining metadata intact.

On Oracle 23ai, `Domain` represents a data use case domain. The older Oracle
mapping of `Domain` to `CREATE TYPE ... VARRAY/TABLE OF` is not retained for
that version.

Oracle package/type specifications and bodies retain complete executable
ALL_SOURCE definitions, including AUTHID and named END clauses, using the same
source-preservation approach as functions and procedures. Missing source fails
with the object identity. CREATE factories accept both complete CREATE DDL and
legacy definitions without duplicating CREATE; no per-object SELECT was added.
These paths are verified with unit tests and XML round trips, not a live database.

### Deferred scope

The following Oracle features are intentionally deferred:

- live-database recreation verification and older release-update boundaries;
  table/column/constraint/index unit coverage does not certify all Oracle options
- structured modification of existing identity/virtual columns beyond simple
  renames (CREATE/ADD preserves formulas and 12c+ identity/default-on-null)
- advanced invisible-column, IOT/external-table/LOB-storage and index lifecycle
  options beyond the explicitly tested generation paths

Oracle 12c+ invisible user columns are now retained independently from system
hidden columns, and support CREATE/ADD plus isolated visibility ALTERs. Existing
identity columns support isolated ALWAYS/BY DEFAULT/ON NULL mode changes without
restarting the sequence. Shared table-level LOB tablespaces are read only when
all LOB segments have one non-null tablespace, and generated for BLOB/CLOB/NCLOB
columns. Simple IOT metadata and ORGANIZATION INDEX generation now preserve the
root tablespace and primary key; overflow/mapping and other complex IOT variants
fail explicitly. Index prefix compression and advanced LOW/HIGH compression now
survive metadata/XML/CREATE generation with dialect version checks.
Index visibility (11g+) and nonpartitioned UNUSABLE state also survive recreation;
Isolated ordinary-index visibility/usability ALTER and nonpartitioned REBUILD now
use ALTER INDEX through Table diffs and the SQL factory registry. Whole partitioned
index rebuilds, structural combinations and special-index state changes fail explicitly.
Isolated physical index partition/subpartition usability transitions now use
MODIFY ... UNUSABLE or REBUILD ... through Table/Index diffs. Metadata/XML/CREATE
preserves segment state; composite parent state changes and structural combinations
fail explicitly. Ordinary index/physical segment tablespace moves and compression
changes now use REBUILD; composite compression modifies the parent then rebuilds
its children. The existing onlineIndex option controls ONLINE with version,
deferrable-constraint, bitmap/join and local-hash restrictions. Partitioned root
tablespace changes affect default attributes only. Explicit Index diffs can also
rename indexes after other operations. Partition structure changes remain deferred.
Nonpartitioned Domain/FunctionDomain indexes now retain qualified indextype and
opaque parameters for recreation, parameter ALTER and explicit REBUILD. Multiline
catalog parameters survive XML via an optional encoded specific. Reverse-key
BTree recreation preserves REVERSE. Join/unknown index CREATE fails explicitly
when the model lacks the required definition. Simple Local Domain partitions now
preserve opaque parameters and support CREATE, parameter ALTER and usability
transitions through individual REBUILD/MODIFY PARTITION. Global/composite Domain
indexes, CREATE of unusable Domain partitions and custom indextype dependencies
remain deferred.
Complete DBMS_METADATA index DDL now enables JOIN/unknown and advanced Domain
recreation when available, with batched reads, permission-denial fallback and a
fingerprint guard against stale DDL. Global RANGE/HASH index structure changes
use validated DROP/CREATE; Local structure follows the base table and independent
changes are rejected. Constraint-backed indexes and ONLINE replacement are guarded.
Structured editing of opaque DDL and custom indextype prerequisites remain deferred.
An isolated simple RANGE table partition split or adjacent two-partition merge now maintains ordinary Local/global
indexes with UPDATE INDEXES and explicit Local segment names/tablespaces. Other
Local table partition structure changes and ONLINE splitting remain deferred.
IOT metadata now reads root prefix compression and PCTTHRESHOLD in the existing
catalog query. Oracle 10g+ IOTs additionally retain batched DBMS_METADATA CREATE
TABLE DDL, allowing overflow/mapping/compressed/partitioned forms to be recreated
without losing unmodeled attributes. Permission-denial logs a warning and retains
catalog metadata. Opaque table DDL has a model fingerprint and cannot be silently
replayed after structural edits. Simple authored IOTs support PCTTHRESHOLD,
INCLUDING, OVERFLOW tablespace, MAPPING TABLE and prefix compression options;
isolated nonpartitioned root tablespace changes use ALTER TABLE MOVE TABLESPACE.
Opaque IOT structural ALTER, complex IOT MOVE and custom indextype/Text preference
prerequisite creation remain deferred.
Local/global range index partition compression modes and explicit NOCOMPRESS now
survive metadata/XML/DDL. Subpartition compression inherits its partition setting;
incompatible overrides and hash partition compression changes fail explicitly.
Per-column/per-partition LOB storage and IOT overflow/mapping models,
identity sequence restart and complex virtual-column changes remain deferred.

Identity increment/bounds/cache/cycle/order changes are now generated as changed
options only through ALTER TABLE MODIFY on Oracle 12c+. Identity sequence restart,
current-position changes remain deferred. Shared
identity boolean setters retain values independently of the identity flag's
assignment order, preserving XML round trips without changing the XML format.

Isolated virtual-expression ALTERs are now generated for Oracle 11g+ tables
without modeled indexes/constraints; combined physical changes and dependencies
require separate handling. Plain, virtual and identity column comment changes
use COMMENT ON without a MODIFY. Identity restart/current-position changes and
complex virtual-column dependencies remain deferred.

- complete structured materialized-view storage, refresh schedules and advanced
  options (basic BUILD/REFRESH settings and DBMS_MVIEW refresh are implemented;
  Oracle 10g+ retains executable DDL for advanced settings; PREBUILT/ON STATEMENT
  in manually authored models require complete executable definitions)
- structured DB-link authentication and CREATE/DROP generation; catalog readers
  no longer query DDL per link or guess credentials from null usernames

DBMS_METADATA enrichment for materialized views and 26ai domains is optional
when access is denied: ORA-01031 and ORA-31603 produce a warning and retain
available catalog metadata. Other failures propagate. Materialized-view catalog
and definition reads use two bulk SELECTs; missing domain definitions prevent
unsafe CREATE regeneration until a complete definition is supplied.

- advanced association forms beyond the implemented flexible-domain USING mappings
- structured flexible-domain selector editing (26ai retains executable DDL)
- structured enumeration values/aliases (26ai retains executable DDL)
- domain-column annotations for alternative type specifications
- complete annotation scope preservation for single-column domains
- named `NOT NULL` constraints and complete lifecycle state beyond CHECKs
- alternative data types for a domain column
- structured metadata editing for flexible and enumerated domains (26ai retains executable DDL)
- older 23ai release-update metadata coverage for multiple-column domains
- domain-specific HTML documentation

### Required design work

Named domain CHECKs now use `Domain.getChecks()` and the shared
`DomainCheckConstraint` model. Oracle 23ai/26ai readers retain all CHECK rows,
including per-check deferrability, enable state and Oracle validation/rely
metadata. PostgreSQL readers retain separate constraint names and expressions
instead of combining them with AND. Legacy `Domain.check` and its XML attribute
remain available for a single unnamed expression. See `oracle-dialect-parity.md`
for API examples, XML compatibility and verification scope.

Regular multi-column domains now use optional `Domain.getColumns()` and
`DomainColumn` components. Oracle 23ai/26ai CREATE factories generate the row
form; Oracle23ai and Oracle26ai readers share batched regular-domain components
and column annotations. The newer domain-kind catalog field stays isolated to
26ai; the 23ai query uses the existing domain column count.
`Column.setDomain` and optional `domainColumnName` retain Schema object
references through XML and cloning. Oracle CREATE TABLE supports ordered
multi-column associations and explicit groups for repeated associations;
PostgreSQL accepts the shared scalar-domain reference.

Oracle26ai also retains executable `definition` DDL for flexible/enumerated
domains and regular domains whose alternatives, NOT NULL constraints or
annotation scope cannot be reproduced from the structured model. Definitions
and domain dependencies are retrieved in bulk. Domain collections are created
in dependency order; prerequisites outside the supplied collection must already
exist. Oracle23ai/26ai Catalog creation composes the registered Schema factory
by object kind, placing all domain prerequisites first, then domains in global
dependency order, then tables. Independent Schema CREATE calls and dependencies
between other object kinds still require caller ordering.
Structured editing of these advanced forms remains deferred; identifiers inside
complete definition SQL do not follow Schema-model renames automatically.

Oracle26ai flexible-domain components are read in one additional bulk query.
The reader validates the returned value-column count and the declared
discriminant count, and rejects incomplete mappings. Unknown annotation
component names are rejected using exact identifier matching on 23ai/26ai.
On 26ai, multiline annotation values retain complete executable domain DDL
through XML. Physical character-column inference from a complete domain
definition requires explicit BYTE/CHAR semantics or an explicit table-column
type; ambiguous length semantics are not guessed.
`DomainColumn.specifics.oracle.domain.discriminant=true` identifies USING
components. CREATE TABLE maps ordinary components to DOMAIN arguments and
discriminants to USING arguments, preserving each component order through XML.
Table columns must specify physical data types; a flexible selector still needs
a complete Domain definition. Oracle23ai and unresolved/incomplete mappings fail
explicitly rather than emitting a scalar association.

Oracle23ai/26ai ALTER DOMAIN now handles structured DISPLAY/ORDER and
object annotations without dropping the domain. Unsupported type, constraint,
name, flexible-selector and complete-definition changes fail explicitly.
Added domain differences are dependency-sorted; deleted domain collections and
differences use the reverse order. Schema/Catalog migrations postpone domain
removals until table detachments and dependent-object changes have completed.
Cross-schema catalog additions and removals require qualified identifiers.
Added domains in Catalog differences are dependency-ordered after new sequences,
types and catalog prerequisites, before consuming table changes. Ordinary
operation order is preserved for indexes, views and other later operations.
New schemas and existing-table associations use the same path. Target models
that retain table/domain references to removed domains fail before returning SQL.
Dependencies outside the supplied Catalog, privileges and cross-schema
references unrelated to domains remain caller prerequisites.
Oracle26ai preserves complete DDL for multiline DISPLAY/ORDER expressions and
character-domain length semantics; manually authored scalar/component domains
retain explicit BYTE/CHAR semantics in generated DDL.

Before implementing the deferred scope:

1. Compare equivalent concepts in PostgreSQL, DB2, SQL Server and other
   supported databases.
2. Decide which concepts belong in the shared `sqlapp-core` Schema model and
   which remain dialect-specific.
3. Model domain columns, multiple constraints, annotations and flexible-domain
   selection without encoding Oracle SQL as opaque strings.
4. Keep single-column domain configuration concise.
5. Define XML compatibility and rename propagation for every reference between
   domains, domain columns and table columns.
6. Add shared Schema-model tests before adding Oracle metadata and SQL tests.

### Completion criteria

The deferred work is complete when a domain can be read from database
metadata, written to Schema XML, read back, and regenerated as equivalent DDL,
with references surviving object and column renames.

## Oracle AI Database 26ai

`Oracle26ai` has a separate dialect and resolver boundary and inherits
compatible Oracle 23ai behavior, with dedicated duality-view readers and CREATE
factories for logical replication. Oracle reports 26ai releases as
product version `23.26.x`; the resolver treats `23.26` and later release updates
as `Oracle26ai` while preserving `Oracle23ai` for earlier `23.x` releases.

Before adding 26ai-only SQL:

- register it only in the 26ai dialect or SQL factory registry
- add a 23ai/26ai boundary test
- distinguish features introduced by a 26ai release update when the JDBC
  product version provides enough information

## Oracle modern feature follow-up

The current Oracle 23ai/26ai scope includes native JSON, Boolean and Vector
types, AI Vector Search SQL and indexes, SQL macros, scalable and session
sequences, single-column data use case domains, schema annotations, supported
existence clauses, JSON relational duality view DDL, and the corresponding
metadata readers where Oracle exposes suitable catalog views.

The following work is intentionally deferred:

- structured modeling of duality-view GraphQL fields, nested objects,
  generated or hidden fields, flex columns and field-level directives
- broader schema-annotation coverage beyond the currently supported table,
  column, index and domain objects
- existence-clause support for additional Oracle object factories where the
  exact database-version boundary and syntax have been verified
- remaining advanced data use case domain forms listed above
- broader DDL, metadata-permission, and Schema XML/DDL round-trip integration
  coverage. Oracle Database Free 23ai and 26ai (`23.26.3`) cover the current
  bulk insert, generated-key, cursor/commit, set-based migration, and core
  metadata-reader paths.

Duality-view definitions remain in the existing `View.statement` property.
Oracle-only flags and discovered metadata remain in `View.specifics`. A shared
structured model should be introduced only if another database exposes an
equivalent feature or sqlapp needs to transform individual definition nodes.

Oracle26ai now reads `ALL_JSON_DUALITY_VIEWS.LOGICAL_REPLICATION` and generates
`AS ENABLE LOGICAL REPLICATION` or `AS DISABLE LOGICAL REPLICATION` from
`View.specifics.LOGICAL_REPLICATION` (Boolean). Omitting the option preserves
the default CREATE path. Earlier dialects reject the option rather than ignore
it; support for earlier 23.x release updates is not inferred from the major
version alone. See `oracle-dialect-parity.md` for examples and validation limits.

Oracle26ai also reads `ALL_JSON_DUALITY_VIEW_TABS` and
`ALL_JSON_DUALITY_VIEW_TAB_COLS` once per requested object set. Typed JSON
catalog rows are retained in View specifics, with helpers for table and column
details. The original view statement remains the DDL source; discovered flags
are supplemental metadata rather than a second GraphQL definition.

## Less-covered database dialects

The H2, SAP HANA, Cloud Spanner and Vertica dialects now cover additional
modern scalar types that already fit the shared Schema model. Further features
below need shared modeling or a larger cross-database design and are therefore
deferred.

Implementation status, verification commands, and the ordered continuation
plan are recorded in `docs/development/dialect-enhancement-continuation.md`.

### H2

- row types and nested row fields
- enum labels as first-class schema objects
- multiset element definitions
- declared aggregate definitions and Java alias implementation details
- compatibility-mode-specific identifiers, types and DDL behavior

### SAP HANA

- `HALF_VECTOR` element precision; the shared vector model currently has no
  half-precision element type
- future vector-index algorithms beyond the currently supported HNSW DDL and
  `VECTOR_INDEXES` metadata
- graph workspaces and knowledge-graph objects
- JSON document collections as objects distinct from relational tables
- HANA Cloud fuzzy-search index search-mode metadata round trips from
  `M_FUZZY_SEARCH_INDEXES`; DDL uses the existing full-text index model
- workload classes, remote sources and virtual tables

### Cloud Spanner

- interleaved-table parent, `ON DELETE` behavior and key ordering
- change streams and their tracked-object configuration
- property graphs, graph node/edge tables and labels
- search-index partitioning, ordering and interleaving definitions; basic
  TOKENLIST-column, STORING, WHERE and sharding-option DDL uses the existing
  column and full-text index model
- mutable vector-index operations; CREATE and metadata recovery use the
  existing vector-index model and Spanner-specific tree options
- locality-group objects and placement keys; table, column and index
  locality/storage options are preserved in dialect specifics
- named schemas for PostgreSQL-dialect databases versus GoogleSQL databases
- `STRUCT`, `PROTO`, named enum and graph value definitions
- generated UUID primary-key strategies
- generated identity behavior that does not map to the shared identity
  properties or Spanner-specific options
- sequence ALTER operations and dependency-aware sequence default-expression
  modeling; CREATE and metadata round trips use the existing Sequence model
- view dependency extraction and structured security semantics shared with
  other databases; Spanner CREATE and metadata round trips currently preserve
  the security type in dialect specifics

### Vertica

- projections, superprojections, segmentation, sort order and `KSAFE`
- flex tables and their key/value materialization
- complex `ARRAY`, `SET`, `MAP` and `ROW` element/field definitions
- external tables and COPY/parser/reject-data configuration
- text indexes and user-defined transform or analytic functions
- storage policies, resource pools and fault-group topology
- temporary-view lifetime and structured schema-privilege inheritance
  (`INCLUDE/EXCLUDE SCHEMA PRIVILEGES`) shared with other databases
- exact IDENTITY start-value round trips after rows have been generated;
  Vertica exposes the current distributed value but not the original start
  value in its sequence catalog

Vertica projections require a dedicated typed Schema model and must not be
encoded as a collection of opaque `Table.specifics` keys. A normal projection
is a physical table-storage and access layout rather than an independently
queried materialized result, so it must not be represented as `Mview` either.
The future model should preserve the anchor-table reference, ordered projection
columns and expressions, sort positions, per-column encoding, segmentation
expression, segmented/unsegmented state, superprojection and aggregate flags,
`KSAFE`, and partition-range endpoints. Rename-sensitive references and XML
round trips must be covered before a Vertica metadata reader is added.

### Shared design requirements

Before implementing these items, compare equivalent concepts in the other
supported databases and add only reusable concepts to `sqlapp-core`. Vendor
syntax that does not affect object identity may remain in dialect-specific
`specifics`, but parent/child references, rename-sensitive column references,
ordered keys, nested fields and dependency relationships need typed Schema
objects with XML round-trip tests.
