# Compatibility and database verification matrix

This page separates three facts that are easy to confuse:

1. A dialect artifact exists for a database family.
2. The resolver contains version-specific implementations for that family.
3. A particular server version has real-engine integration-test coverage.

A version-specific class shows intentional compatibility behavior in the code;
it does not prove that every feature was executed against every server release
in that range. Conversely, a real-engine test covers the scenarios in that
test suite, not the database product's complete feature set.

This matrix was audited from the repository source and test configuration on
2026-09-19. The test images listed below were not rerun as part of this
documentation change.

## Java and Gradle

| Component | Current baseline | Compatibility statement |
|---|---|---|
| Published Java artifacts | Java 21 | Sources are compiled with a Java 21 toolchain. Consumers should use a Java 21 or later runtime compatible with the published bytecode. |
| Repository build | Gradle Wrapper 9.6.1 | This is the checked-in, supported build path for release verification. |
| Gradle plugin | Java 21; tested by this repository with Gradle 9.6.1 | A lower or upper Gradle compatibility range has not yet been established. Do not infer one from Gradle API compilation alone. |
| Companion example | Java 21; Gradle Wrapper 9.6.1 | The `develop` branch of `sqlapp-gradle-example` uses the same current baseline. |

Before publishing, the release build should run with the checked-in wrapper.
If compatibility with older Gradle releases is promised, add a TestKit matrix
and record the oldest and newest verified versions here.

## Coverage labels

| Label | Meaning |
|---|---|
| Real-engine | A repository integration suite is configured for, or the continuation record documents execution against, the named server or emulator version. |
| Module | Dialect unit/module tests and SQL or resolver tests exist, but this table does not identify a current real-server run. |
| Environment-dependent | Verification needs proprietary software, a cloud service, or another environment not supplied by the routine test suite. |

“Configured” is deliberately weaker than “passed for this release.” The
release checklist must record which integration suites were actually run.

## Database dialect matrix

| Database family | Artifact | Version-aware implementation in source | Current real-engine evidence | Current confidence boundary |
|---|---|---|---|---|
| IBM Db2 | `sqlapp-core-db2` | 9.5, 9.7, 9.8, 10.1, 10.5, 11.1, 11.5, 12.1.0, 12.1.2, 12.1.5 | Db2 Community 11.5.8.0 and 12.1.5.0 test images | Older resolver branches are retained but are not all represented by current container runs. |
| Firebird | `sqlapp-core-firebird` | 2.0, 2.5, 3.0, 5.0 | Firebird 3.0 and 5.0 test images | Firebird 2.x compatibility is code-level/module coverage in the current suite. |
| H2 | `sqlapp-core-h2` | Pre-2.x and 2.x split | Module-level H2 tests | The 1.x reader is preserved; current real-engine version evidence is not recorded here. |
| HSQLDB | `sqlapp-core-hsql` | 2.0.0, 2.1.0, 2.2.0, 2.3.0, 2.3.4, 2.4.0 | Module/in-process tests; JDBC dependency 2.7.4 | Version branches older than the current driver remain compatibility code. |
| Informix | `sqlapp-core-informix` | Generic Informix resolver | Informix Developer Database 14.10.FC9W1DE image | Older server-version boundaries are not expressed as separate dialect classes. |
| Microsoft Access / MDB | `sqlapp-core-mdb` | Generic Access/MDB resolver through UCanAccess | Access 2010 database creation and metadata tests are documented | Japanese index collation in the supplied sample is read-only with the current Jackcess path. |
| MariaDB | `sqlapp-core-mariadb` | 10.0, 10.0.5, 10.2.0, 10.2.5, 10.2.7, 10.3.0, 10.5.0, 11.4.0, 11.5.0, 11.8.0, 12.1.0 | MariaDB 10.5, 11.4, and 11.8 test images | MariaDB inherits compatible MySQL behavior and overrides version-specific differences. |
| MySQL | `sqlapp-core-mysql` | 5.6.4, 5.6.5, 5.7, 8.0, 8.0.1, 8.4, 9.0 | MySQL 5.7, 8.0, and 8.4 test images | MySQL 9.0 is resolver/module coverage without a current listed container run. |
| Oracle Database | `sqlapp-core-oracle` | 10g, 11g, 11g R2, 12c, 18c, 19c, 21c, 23ai, 26ai | Oracle Database Free 23ai image; optional pinned 26ai image | Oracle 26ai reports product version `23.26.x`; older branches are not all container-tested. Dialect support does not imply that the current test JDBC driver is certified for every server version; verify the exact driver/JDK/database combination for legacy sources. |
| Apache Phoenix | `sqlapp-core-phoenix` | Generic resolver plus a 5.3.1 feature boundary | Module SQL, resolver, and sequence-block tests | Phoenix 5.3.1 multi-row UPSERT still requires verification against a real cluster. |
| YugabyteDB YSQL | `sqlapp-core-yugabyte` | PostgreSQL engine 11 and 15 baselines | Module tests and local containers 2024.2.11.0-b36 / 2026.1.2.0-b137 | Supported relational compatibility scope: schema recreation, COPY/upsert, checkpoint/resume and conflict handling verified on both baselines. Physical/distributed extensions and multi-node failover excluded; see [scope and limits](../sqlapp-core-yugabyte/README.md). |
| PostgreSQL | `sqlapp-core-postgres` | Explicit branches from 8.2 through 18 | PostgreSQL 14 and 18.4 test images | Historical branches are preserved, but current containers do not rerun every major release. |
| SAP HANA | `sqlapp-core-saphana` | Platform and HANA Cloud split | SAP HANA Express 2.0 (`2.00.088`) image | HANA Cloud vector-index and fuzzy-search catalog behavior requires a HANA Cloud tenant. |
| Google Cloud Spanner | `sqlapp-core-spanner` | Generic Cloud Spanner resolver | Cloud Spanner emulator | Search/vector index, locality/storage, and some service-only metadata require a real service environment. |
| SQLite | `sqlapp-core-sqlite` | Generic SQLite resolver | File/in-process tests with Xerial SQLite JDBC | No server version applies; behavior also depends on the bundled/native SQLite version in the selected driver. |
| Microsoft SQL Server | `sqlapp-core-sqlserver` | 2000, 2005, 2008/R2, 2012, 2014, 2016/SP1, 2017, 2019, 2022 | SQL Server 2017, 2019, 2022, and 2025 test images | SQL Server 2025 currently resolves through the latest compatible implemented dialect unless a newer boundary is added. |
| Sybase ASE | `sqlapp-core-sybase` | Generic Sybase resolver | SAP ASE 16 image | Generated-key propagation for IDENTITY is intentionally rejected on the tested jTDS batch path. |
| Vertica | `sqlapp-core-virtica` | 7.2, 8.0, 9.0, 11.1.1, 12.0.4 | Vertica CE 25.1.0-0 image | Modern servers use the latest compatible dialect; projection, segmentation, KSAFE, flex-table, and external-table work remains deferred. |

The artifact name `sqlapp-core-virtica` retains its historical spelling.

## JDBC versions in the current verification build

These are test or implementation dependencies in the current checkout. They
are not a declaration that applications must use exactly these versions.

| Database | JDBC dependency used by current build/test configuration |
|---|---|
| Firebird | Jaybird `6.0.5` in dialect integration tests; dialect artifact currently uses `5.0.6.java11` |
| HSQLDB | `org.hsqldb:hsqldb:2.7.4` |
| Informix | `com.ibm.informix:jdbc:15.0.1.2` |
| Db2 | `com.ibm.db2:jcc:12.1.4.0` |
| MariaDB | `org.mariadb.jdbc:mariadb-java-client:3.5.9` |
| MySQL | `com.mysql:mysql-connector-j:9.7.0` |
| Oracle | `com.oracle.database.jdbc:ojdbc11:23.26.2.0.0` |
| PostgreSQL | `org.postgresql:postgresql:42.7.11` |
| SAP HANA | `com.sap.cloud.db.jdbc:ngdbc:2.29.7` |
| Cloud Spanner | `com.google.cloud:google-cloud-spanner-jdbc:2.41.0` |
| SQLite | `org.xerial:sqlite-jdbc:3.53.1.0` |
| SQL Server | Microsoft JDBC `13.4.0.jre11` in integration tests; dialect artifact currently uses `13.5.0.jre11-preview` |
| Sybase ASE | jTDS `1.3.1` in integration tests |
| Vertica | `com.vertica.jdbc:vertica-jdbc:25.3.0-0` |

Drivers absent from this table must be supplied from the database vendor or an
appropriate compatible distribution. See
[Published artifacts and dependency selection](artifacts.md) for dependency
scope guidance.

## Feature-specific compatibility

The database matrix does not mean that every sqlapp feature has the same
coverage. Consult these focused references before using advanced operations:

- [Bulk insert and migration provider matrix](data/bulk-insert.md)
- [Dialect enhancement verification and limitations](development/dialect-enhancement-continuation.md)
- [Schema viewpoints](schema/viewpoints.md)
- [Gradle plugin task guide](gradle-plugin/README.md)

Generated-key handling, set-based migration, metadata catalogs, temporary
tables, UPSERT syntax, and lease storage each have narrower compatibility
boundaries than basic dialect resolution.

## Release verification expectations

For each published release:

1. Run unit tests for every changed dialect module.
2. Run the real-engine integration suites for changed databases when the
   required environment is available.
3. Record every tested server image and JDBC driver in the release notes.
4. Identify configured suites that were not run.
5. Do not broaden a supported-version claim solely because a newer server
   falls back to the latest known dialect.

Production users should validate metadata export and generated DDL against a
non-production instance matching the exact server and JDBC driver versions
used in production.

## Symfoware transition

The legacy `sqlapp-core-symfoware` artifact and public Java packages have been
removed. Native-interface databases are no longer supported. This is a breaking
change for consumers of the old artifact or classes.

Symfoware Server (Postgres) uses `sqlapp-core-postgres` through a compatible JDBC
connection reporting `PostgreSQL` and the actual PostgreSQL engine major/minor
version in `DatabaseMetaData`. It reuses PostgreSQL resolution, metadata readers
and SQL factories. Do not substitute the Symfoware product release (V12.x, etc.)
for the PostgreSQL engine version. Bare `Symfoware` is not a PostgreSQL alias,
because it cannot safely distinguish Native and Postgres interfaces.

Vendor driver identification, extensions, permissions, schema recreatability and
migration operations remain unverified against Symfoware. No real Symfoware
server was used for this transition. Verify the target server/driver combination
before use; PostgreSQL unit tests do not establish vendor compatibility.

## Removed Derby and HiRDB dialects

`sqlapp-core-derby` and `sqlapp-core-hirdb`, including their public Java packages,
are no longer built or published. This is a breaking change for applications
using those artifacts or classes. Derby and HiRDB are no longer supported
sources or targets, including migration sources. Use an earlier sqlapp release
if the removed dialects are required.

The general resolver fallback for unknown products is unchanged. Receiving a
generic dialect does not establish support for a removed database. Do not use
that fallback to generate or execute database-specific migration SQL.

## Index schema qualification

When `decorateSchemaName` is enabled, generated CREATE INDEX statements now
qualify the target table even when the index and table have the same schema.
Previously that path ignored the option and depended on the session search path.
Disabling the option retains unqualified names for same-schema objects. Public
APIs and Schema XML formats are unchanged.

The PostgreSQL 10–12 table metadata query no longer references PostgreSQL-13-only
insert-triggered autovacuum settings. PostgreSQL 13+ retains these fields in its
separate version-specific query. Schema XML and SQL-generation APIs are unchanged.

## Foreign-key SQL generation

With `decorateSchemaName` enabled, foreign-key references now qualify a table
in the same schema, matching table/index name decoration and avoiding reliance
on the session search path. With the option disabled, the same-schema reference
stays unqualified. This affects dialects using the shared foreign-key factory.
PostgreSQL SQL factories (including YSQL inheritance) now render `CascadeRule.None`
as `NO ACTION` for both delete and update, instead of invalid `NONE` syntax.
Public APIs and Schema XML formats are unchanged.

## PostgreSQL internal triggers

PostgreSQL 9.0+ metadata readers (also used by the YSQL 11/15 baselines) now
exclude `pg_trigger.tgisinternal` implementation triggers. Foreign keys remain
in the Schema constraint model; their internal triggers must not be emitted as
additional user-authored CREATE TRIGGER statements during schema recreation.
User-defined triggers remain available. The pre-9.0 query is unchanged because
those catalogs do not expose this flag. Public APIs and Schema XML formats are
unchanged; exported trigger collections may contain fewer implementation objects.

Catalog boundaries: [PostgreSQL 9.0](https://www.postgresql.org/docs/9.0/catalog-pg-trigger.html)
and [PostgreSQL 8.4](https://www.postgresql.org/docs/8.4/catalog-pg-trigger.html).

## PostgreSQL/YSQL upsert and COPY array boundaries

The PostgreSQL 9.5–14 ON CONFLICT SQL factory, also used by YSQL 11/15, now
emits `DO NOTHING` when no non-key column can be updated. A table containing
only its conflict key can therefore be inserted repeatedly without generating
an empty `DO UPDATE`. Tables with updateable columns retain their update path.

The shared PostgreSQL COPY reader now retains nested Java array dimensions.
It distinguishes primitive byte arrays used as numeric array values from
scalar `bytea` elements inside binary array columns. Existing scalar binary
COPY and null/empty-string handling are retained. YSQL tests verify integer
and text matrices, primitive-byte smallint arrays and bytea arrays through
COPY, staging upsert and JDBC readback. Public APIs and configuration formats
are unchanged.

## PostgreSQL/YSQL trigger firing state

PostgreSQL 9.0+ trigger readers and SQL factories, inherited by YSQL 11/15,
retain disabled triggers and the `ALWAYS`/`REPLICA` firing modes during
recreation. CREATE is followed by the required ALTER TABLE statement. Ordinary
enabled triggers keep their existing single CREATE statement.

The optional existing `Trigger.specifics` map entry `TRIGGER_FIRING_MODE` accepts
`ALWAYS` or `REPLICA`; `enable=false` restores the disabled state. Invalid values
fail explicitly, and restoring a special state requires the modeled table name.
The public factory constant `Postgres90CreateTriggerFactory.FIRING_MODE` is an
additive API. Schema XML uses its existing optional vendor-attribute format.
The pre-9.0 readers and factories are unchanged.

TRUNCATE is now retained in the event metadata. Complete trigger definitions
remain authoritative for recreating WHEN conditions and row/statement triggers.
Firing-state syntax: [PostgreSQL 9.0 ALTER TABLE](https://www.postgresql.org/docs/9.0/sql-altertable.html).

## PostgreSQL/YSQL covering partial indexes

CREATE INDEX now emits INCLUDE before storage options and WHERE, following
PostgreSQL's clause order. This fixes recreation of partial covering indexes;
indexes without INCLUDE retain their existing ordering. The owning PostgreSQL
factory hook remains available and now runs immediately after the key columns.

PostgreSQL 11+ index queries, inherited by YSQL 11/15, read partial predicates
with `pg_get_expr(indpred, indrelid)` and key ASC/DESC flags from the catalog for
B-tree/LSM indexes. They no longer infer those fields from a regular expression
over the complete CREATE INDEX statement. The pre-11 metadata query and parsing
path are unchanged. No public API, configuration or Schema XML format changes
are required. Physical YugabyteDB index layout remains outside the support scope.

Clause order: [PostgreSQL 11 CREATE INDEX](https://www.postgresql.org/docs/11/sql-createindex.html).

## NULL ordering and PostgreSQL/YSQL expression index keys

The shared `NullsOrder.NullsLast` enum now renders `NULLS LAST` instead of the
incorrect `NULLS FIRST`. Enum names, public APIs and the Schema XML structure
are unchanged. Existing FIRST/LAST XML round trips are covered by core tests.
SQL generated for a modeled LAST order intentionally changes to the correct value.

PostgreSQL 11+ B-tree/LSM index readers, also used by YSQL 11/15, retain the
catalog NULL-order flags. Other access methods and pre-11 readers retain their
existing behavior. The PostgreSQL index factory emits ASC explicitly when a
NULL order is modeled, so YSQL cannot reinterpret an omitted first-key order
as HASH. YugabyteDB physical hash/range placement is still outside the
compatibility scope; generated indexes express the modeled logical sort order.

Unresolved keys in an `IndexType.Function` index are generated as parenthesized
SQL expressions rather than quoted identifier names. Resolved table columns
retain normal identifier quoting. Tests recreate `lower(label)` indexes and
verify their UNIQUE behavior; custom collations and operator classes are not
covered by this verification.

YSQL defaults: [CREATE INDEX](https://docs.yugabyte.com/stable/api/ysql/the-sql-language/statements/ddl_create_index/).

## PostgreSQL 15+/YSQL 15 NULLS NOT DISTINCT indexes

The PostgreSQL 15+ CREATE INDEX factory now emits `NULLS NOT DISTINCT` after
the key/INCLUDE clauses and before storage options and WHERE. Previously it
appeared between UNIQUE and INDEX, producing invalid syntax. YSQL 15 inherits
the corrected factory and its reader retains `indnullsnotdistinct` in the
existing `nullsNotDistinct` vendor attribute.

Per the requested compatibility policy, pre-15 dialects ignore this attribute
and keep ordinary UNIQUE semantics (multiple NULL values are allowed).
Non-unique indexes and values other than case-insensitive `true` also retain
the existing ignore behavior. No new configuration errors or public API/XML
format changes are introduced by this fix.

Verification covers a covering partial UNIQUE index: YSQL 15 rejects duplicate
NULL keys inside the predicate after recreation, while excluded rows may still
repeat NULL. YSQL 11 ignores the option and permits repeated NULL keys. Both
engines reject repeated non-NULL keys inside the predicate.

Clause order: [PostgreSQL 15 CREATE INDEX](https://www.postgresql.org/docs/15/sql-createindex.html).

## PostgreSQL/YSQL INSTEAD OF trigger metadata

The PostgreSQL 9.0+ trigger query now distinguishes the INSTEAD timing bit from
AFTER. This corrects metadata for view triggers on PostgreSQL 9.1+ and YSQL
11/15, where INSTEAD OF is supported. The existing column and bit operations
remain valid on 9.0, which has no such triggers; its BEFORE/AFTER behavior and
the pre-9.0 query remain unchanged.

Complete executable trigger definitions remain authoritative for recreation.
The corrected `actionTiming` also makes the Schema metadata agree with that DDL.
No public API, configuration or XML-format changes are required. Verification
uses a UNION ALL view that cannot be automatically updated, recreates its
INSTEAD OF trigger, and checks INSERT, UPDATE and DELETE through the view.

Version boundary: [PostgreSQL 9.1 CREATE TRIGGER](https://www.postgresql.org/docs/9.1/sql-createtrigger.html).

## PostgreSQL/YSQL trigger comments

Both legacy and 9.0+ PostgreSQL trigger queries now use
`obj_description(trigger_oid, 'pg_trigger')`. The second argument identifies
the containing catalog, not the current database; the previous database-name
argument could lose comments. Other trigger version boundaries are unchanged.

The PostgreSQL CREATE TRIGGER factory, inherited by YSQL 11/15, now restores
modeled `remarks` with a separate COMMENT ON TRIGGER statement after creation
and firing-state restoration. Trigger names are unqualified; the target table
or view follows the existing schema-decoration option. Identifier and SQL
literal quoting use the dialect builder. No-comment objects retain the
existing operation count. Comment restoration requires the modeled target
table/view name, including when CREATE is supplied as a full definition; readers
already supply that name. Public APIs and Schema XML formats are unchanged.

Verification covers table-trigger comments across ordinary, disabled, ALWAYS
and REPLICA states, plus Japanese text and apostrophes on a recreated INSTEAD
OF view trigger. Comment lookup: [PostgreSQL system information functions](https://www.postgresql.org/docs/15/functions-info.html).
Comment syntax: [PostgreSQL COMMENT](https://www.postgresql.org/docs/15/sql-comment.html).

## YSQL sequence comments

The YugabyteDB YSQL 11/15 sequence reader now obtains comments with
`pg_catalog.obj_description(sequence_oid, 'pg_class')` alongside the existing
`pg_sequence` configuration fields. The inherited PostgreSQL sequence factory
already emits COMMENT ON SEQUENCE for modeled remarks, so no SQL-generation
change is needed. API and Schema XML formats remain unchanged; full metadata
exports now retain an additional existing remarks field when a comment exists.

The real-engine round trip checks Japanese/apostrophe comments, start and
increment values, and the resulting nextval values. Live sequence position and
ownership remain outside the supported recreation scope. Ordinary PostgreSQL
sequence readers are unchanged.

## Typed YSQL sequences

The YSQL 11/15 reader preserves the `smallint`, `integer` or `bigint` sequence
type from `pg_sequence.seqtypid` in the existing Schema model. The PostgreSQL
10+ CREATE SEQUENCE factory emits `AS` for these types, including type-name
aliases resolved by the dialect. Earlier PostgreSQL versions and unsupported
types retain the previous behavior of omitting the type clause. Unspecified
types also retain their existing default behavior.

The additive `Postgres100CreateSequenceFactory` is registered from PostgreSQL
10 onward; existing public signatures and Schema XML formats are unchanged.
Ordinary PostgreSQL metadata readers are unchanged. The local YSQL matrix
checks all three types with ascending and descending increments, bounds, cache
configuration and nextval results after recreation. Live position and ownership
remain outside this support scope.

## YSQL sequence bounds and cycling

The local YSQL 11/15 integration matrix additionally recreates all three
sequence types using their full signed integer bounds, in both directions,
with and without CYCLE. It verifies exact metadata values through 64-bit
bounds, preservation of the cycle flag, and nextval behavior at the boundary:
cycling wraps to the opposite bound and continues; non-cycling reports
SQLSTATE 2200H. These are single-session checks using CACHE 100. They do not
assert consecutive values across connections or nodes. No production-code,
public API or Schema XML changes were needed for this coverage.

## PostgreSQL/YSQL schema fidelity batch

The shared PostgreSQL readers now preserve the following metadata for YSQL
11/15 and ordinary PostgreSQL:

- Enum labels use `pg_enum.enumsortorder` on PostgreSQL 9.1+, including values
  inserted with ALTER TYPE BEFORE/AFTER. PostgreSQL 8.3–9.0 uses OID order and
  never references the newer column. The enum reader also retains type comments.
- Domains with multiple CHECK constraints produce one Domain object. Each
  expression is parenthesized and combined with AND, retaining acceptance and
  rejection behavior in the existing single `check` property. Separate domain
  constraint names are not retained by this representation.
- Domain/type comments use the `pg_type` catalog; index comments use `pg_class`
  in all three index-query generations. The correction does not require a
  newer catalog column.

CREATE DOMAIN/TYPE for modeled domains and enums now appends COMMENT ON when
remarks are present. Standalone CREATE INDEX also appends its comment; table
recreation continues using its existing index-comment path. Comments are
quoted through the dialect builder. Objects without comments retain the
previous operation count. Existing APIs and Schema XML formats are unchanged;
commented objects can now return an additional SET_COMMENT operation.

Real YSQL 11/15 tests cover non-alphabetical enum order after BEFORE/AFTER,
Unicode/apostrophe labels and comments, multiple domain checks with OR inside
one check, numeric precision/scale, defaults and NOT NULL. They execute the
recreated objects and check SQLSTATE 23514/23502/23505 for rejected domain
values, nulls and duplicate index keys. Standalone and table-based index
recreation both preserve comments and uniqueness. Composite-type recreation
is covered by the separate batch below.

The COPY/upsert matrix additionally covers one-dimensional UUID, numeric,
boolean, date and timestamp arrays, distinguishing SQL NULL, empty arrays and
NULL elements. Numeric scale, leap-day dates and timestamp microseconds are
checked after COPY and an actual upsert update. Other array element types and
multidimensional variants of these added types remain unverified.

Catalog references: [PostgreSQL 9.1 pg_enum](https://www.postgresql.org/docs/9.1/catalog-pg-enum.html),
[PostgreSQL object-description functions](https://www.postgresql.org/docs/15/functions-info.html#FUNCTIONS-INFO-COMMENT-TABLE).

## Composite types, views and table constraints

PostgreSQL composite-type metadata now stores a complete executable CREATE TYPE
in `definition`, replacing the incorrect use of pg_get_ruledef with a type OID.
Attribute order, typmods, array dimensions and schema-qualified user-defined type references are
read from pg_attribute/pg_type. Dropped attributes are excluded by the query.
PostgreSQL 9.1+ uses a separate query to preserve attribute collations; older
versions never reference attcollation or pg_collation. Supplemental modeled
columns retain attribute comments. The definition is authoritative for CREATE;
modeled attributes are not mandatory duplicates of its contents.

The additive PostgresCreateTypeFactory is registered through the normal SQL
factory registry. It uses `definition` when available and can also generate a
simple composite type from modeled columns. Type and attribute comments are
emitted separately. View recreation likewise preserves column comments in
addition to the existing view comment. View comments are joined using both
object OID and the pg_class catalog identity.

Table constraint metadata and recreation now preserve CHECK, PK/UNIQUE and FK
comments. COMMENT ON CONSTRAINT leaves the constraint name unqualified and
qualifies its owning table. Constant CHECK expressions without column references
are retained. Foreign keys are grouped by schema, owning table and constraint
name so equal names on different tables do not merge. FK key positions are
preserved. PK/UNIQUE metadata follows conkey positions rather than physical
column order. PostgreSQL 8.4+ uses generate_subscripts; older branches now use
the generate_series fallback described below.

The YSQL 11/15 matrix recreates a quoted composite type containing numeric
precision/scale, two-dimensional text arrays, enum/domain references and enum arrays, with an
explicit C collation and Unicode/apostrophe comments on the type and attribute.
It changes search_path between reading and recreation and evaluates composite
values afterward, compares the complete recreated definition and checks array
dimensions on the re-read model. Separate cases verify quoted view/column comments and view
results, and recreate two parent/child pairs with equal FK names, reversed key
positions, constant checks and constraint comments. Rejected inserts assert
SQLSTATE 23503, 23514 and 23505.

YSQL 11 rejected ALTER TYPE DROP ATTRIBUTE during test preparation with an
unsupported-feature error. That operation and real dropped-attribute metadata
are outside this verified scope; the query exclusion is covered by unit checks.
Composite ALTER, extension-specific attribute types and cross-object dependency
planning for arbitrary composite-type graphs remain unverified. Existing APIs,
XML formats and dependencies are unchanged. Objects with comments may yield
additional SET_COMMENT operations. The changed checkConstraints.sql resource
was converted from its legacy CP932 text encoding to UTF-8.


## Deferrable foreign keys

PostgreSQL foreign-key SQL generation preserves the Schema model's existing
Deferrability setting for both CREATE TABLE and standalone ALTER TABLE ADD
CONSTRAINT. InitiallyDeferred and InitiallyImmediate emit DEFERRABLE followed
by the initial mode. NotDeferrable and an absent setting retain the ordinary,
non-deferrable default without adding a clause. The PostgreSQL 18 temporal
foreign-key factory uses the same behavior. Public APIs, XML formats and
module dependencies are unchanged.

This change concerns foreign keys. YugabyteDB does not support deferrable
primary-key or UNIQUE constraints; no support for those is implied. View
security/check options are covered below; NOT VALID constraint preservation
is covered below.

The integration case reads a full Schema containing the referenced parent,
recreates both initial modes, and checks child-before-parent insertion,
SET CONSTRAINTS DEFERRED and rejection with SQLSTATE 23503 when switching back
to immediate checking. The immediate case also drops and recreates the FK
through standalone ALTER TABLE generation. The first test attempt using a
standalone TableReader exposed a separate reference-resolution issue:
the generated FK referenced the child table. The shared reader fix described
below now covers that standalone path as well.
See YugabyteDB's [ALTER TABLE reference](https://docs.yugabyte.com/stable/api/ysql/the-sql-language/statements/ddl_alter_table/)
for its foreign-key-only deferrability support.

Validation: the focused PostgreSQL FK test class passed both tests; the full
PostgreSQL/YSQL/command regression then passed 758 unit tests (one optional
external test skipped), with 81 unchanged plugin tests reused. Both local
YSQL compatibility tasks passed all 33 cases each (66 total), including the
composite-array correction from the preceding batch. Yugabyte packaging passed.
No generated files were intentionally edited and no external/production
database was accessed. The first focused integration attempt failed on the
standalone TableReader reference-resolution limitation described above; the
final full-Schema test and complete matrix passed.


## Standalone foreign-key reference ownership

The shared ForeignKeyConstraintReader constructed a referenced Table but left
its referenced Columns detached. Their catalog/schema/table identifiers were
read correctly. When no parent Table was available in a containing Schema,
ReferenceColumnCollection.getTable() fell back to the owning child Table;
SQL generation then used that incorrect resolved Table. This was a model
ownership problem after catalog reading, not a YSQL deferrability restriction
or a missing REFERENCES clause in the SQL factory.

The helper now attaches referenced Columns to the referenced Table it already
constructs. A standalone read retains an owner with the correct identity;
when a complete Schema is available, getRelatedTable() continues to prefer
the canonical Table resolved from it. The helper is shared by PostgreSQL,
DB2, MySQL, Oracle, H2, SQL Server and other readers, so the fix belongs in
sqlapp-core rather than a PostgreSQL-only SQL-generation workaround. No
public API, XML format, dependency or vendor-version boundary changes.

The core regression reproduces the incorrect parent before the fix and
checks same-named child columns, catalog/schema identity, composite-column
order, full-Schema resolution and self references. Local YSQL 11/15 tests
read only the child through TableReader, recreate a quoted cross-schema
composite FK through CREATE TABLE and standalone ADD CONSTRAINT, read it
back and reject orphan inserts with SQLSTATE 23503. The retained referenced
Table contains only the referenced columns; it is not a full parent-table
metadata snapshot. Other database engines have unit regressions, not new
real-database verification for this fix.

Final validation on 2026-10-08: the focused core reader tests passed 2/2;
the focused standalone TableReader case passed on both local YSQL engines.
The broad Gradle run executed :sqlapp-core:test, :test for every retained
dialect module in settings.gradle, :sqlapp-command:test and
:sqlapp-gradle-plugin:test: 2,677 passed, one optional external test skipped.
:sqlapp-core-dialect-test:yugabyte11CompatibilityTest and
:sqlapp-core-dialect-test:yugabyte15CompatibilityTest each passed 34 cases
(68 total). :sqlapp-core-yugabyte:assemble was up to date. The initial new
unit test first needed API/type corrections to compile, then reproduced the
ownership failure before the implementation fix; all final checks passed.
No external or production database was accessed, and no generated output
was intentionally edited.


## View check and security options

PostgreSQL view metadata now retains security_barrier, check_option and
security_invoker from pg_class.reloptions in the existing Schema Specifics map.
Views read from the database need no additional configuration to preserve
these settings when regenerated. PostgreSQL 9.2+ reads reloptions; the older
query keeps a typed NULL placeholder without referencing that catalog column.
Version-specific factories enable security_barrier from 9.2, check_option
from 9.4 and security_invoker from 15. Yugabyte YSQL 11/15 inherits the matching
PostgreSQL factory, with no vendor checks in shared code.

The ordinary statement-only CREATE VIEW path is unchanged when no supported
options are specified. Advanced callers can use the additive constants
PostgresCreateViewFactory.SECURITY_BARRIER, CHECK_OPTION and SECURITY_INVOKER
as Specifics keys. Boolean values accept true/false, ignoring case and outer
whitespace; check_option accepts local/cascaded. Invalid values and a known
option configured for an unsupported target version throw an actionable
IllegalArgumentException. Security semantics are not silently discarded.
Complete definition DDL remains authoritative; separately modeled options are
not appended to it. Public XML formats and dependencies are unchanged.

The local YSQL matrix reads, drops and recreates nested updatable views with
security_barrier=true and both CHECK OPTION modes, compares re-read options,
and verifies rejected writes with SQLSTATE 44000. A separate case creates a
NOLOGIN test role and verifies the recreated security_invoker view on YSQL 15
rejects access without base-table privileges (42501), then permits it after a
SELECT grant. YSQL 11 retains ordinary owner-based access. Roles and schemas
are unique disposable test fixtures and are removed afterward. These cases
do not migrate roles or GRANTs, or establish RLS-policy and non-leakproof
predicate evaluation guarantees. NOT VALID constraint preservation is covered
below. See the [PostgreSQL CREATE VIEW reference](https://www.postgresql.org/docs/15/sql-createview.html)
for the options and permission semantics.

Validation on 2026-10-08: focused view/query unit tests passed 5/5 and both
new integration cases passed on each YSQL engine. The complete run of
:sqlapp-core-postgres:test, :sqlapp-core-yugabyte:test, :sqlapp-command:test and
:sqlapp-gradle-plugin:test passed 842 tests and skipped one optional external
YSQL test. Both yugabyte11CompatibilityTest and yugabyte15CompatibilityTest
passed all 36 cases (72 total); :sqlapp-core-yugabyte:assemble passed.
The initial unit run had one test assertion comparing the definition list
rather than its generated SQL text; that assertion was corrected and the
final suite passed. No external or production database was accessed and no
generated output was intentionally edited.


## NOT VALID CHECK and foreign-key constraints

The existing metadata readers now retain a terminal NOT VALID clause from
pg_get_constraintdef in Constraint Specifics, using the additive public key
PostgresConstraintOptions.NOT_VALID ("notValid"). A CHECK expression containing
the text 'NOT VALID' is not treated as an unvalidated constraint. No new catalog
columns are required. Read models preserve this state automatically.

PostgreSQL 9.1+ foreign-key and 9.2+ CHECK factories support the flag; the
PostgreSQL 18 factory variants retain their existing enforcement behavior.
For advanced authored models, true requests NOT VALID and false retains the
ordinary default. Invalid boolean values and unsupported target versions fail
clearly instead of silently changing validation semantics. Existing APIs and
XML formats remain compatible; constants and version-specific factories are
additive and no dependencies changed.

NOT VALID is an ALTER TABLE ADD CONSTRAINT option. CREATE TABLE generation
therefore omits marked CHECK/FK constraints from its inline list, then composes
the registered constraint factories to emit separate ALTER TABLE operations.
Constraint comments follow those operations. The Schema model is not mutated,
and ordinary constraints keep their previous generation path. The operation
list for a table with unvalidated constraints is intentionally longer.

Local YSQL 11/15 verification starts with a row violating both constraints,
reads the state through standalone TableReader, drops and regenerates both
constraints without scanning away the old violation, and confirms new invalid
writes still fail with 23514/23503. VALIDATE CONSTRAINT fails on the old row,
then succeeds after that row is repaired; re-reading no longer marks the
constraints NOT VALID. Recreating the empty table from the original model
preserves unvalidated state and Unicode/apostrophe constraint comments, while
still rejecting new violations. NOT VALID does not permit loading invalid
rows into a newly constrained table; migration of legacy violating data needs
a separate data/constraint execution plan. No automatic validation, constraint
disabling or data repair is performed by SQL generation.

Domain and partition-specific validation rules, concurrent validation locking,
and PostgreSQL 18 combined NOT ENFORCED/NOT VALID execution are not verified
on real engines by this batch. The PostgreSQL 18 factories have unit coverage;
the real-engine scope is the two YSQL baselines. See the
[PostgreSQL ALTER TABLE reference](https://www.postgresql.org/docs/18/sql-altertable.html)
for validation semantics, and the [9.2 release notes](https://www.postgresql.org/docs/9.2/release-9-2.html)
for the CHECK support boundary.

Validation on 2026-10-08: focused NOT VALID and constraint-metadata unit tests
passed 7/7, and the focused integration case passed on each local YSQL engine.
The final Gradle run executed :sqlapp-core-postgres:test,
:sqlapp-core-yugabyte:test and :sqlapp-command:test: 764 passed, one optional
external YSQL test skipped. :sqlapp-gradle-plugin:test reused its 81 passing
results. Both yugabyte11CompatibilityTest and yugabyte15CompatibilityTest
passed all 37 cases (74 total); :sqlapp-core-yugabyte:assemble was up to date.
The first integration-test insertion script failed before creating the test,
so its first invocation found no matching test; the insertion was corrected
and the focused and final runs passed. No external/production database was
accessed and no generated output was intentionally edited.


## Foreign-key actions and historical constraint queries

PostgreSQL 18 used a separate FK factory that emitted the Schema enum None as
NONE in ON DELETE/ON UPDATE clauses. The ordinary PostgreSQL factory already
translated that enum to NO ACTION. Both factories now compose the same
PostgreSQL-local action renderer, preserving CASCADE, RESTRICT, SET NULL and
SET DEFAULT and emitting valid NO ACTION. No public API, XML format or module
dependency changes. The catalog-query spelling RISTRICT is also corrected to
RESTRICT; the existing permissive enum parser already recognized the former
r-prefixed spelling, so that typo alone did not lose the modeled action.

The FK query unconditionally used generate_subscripts (introduced in 8.4),
and both FK and 8.4+ unique-key queries referenced preceding FROM items from
a table function, which requires 9.3's implicit LATERAL behavior. Both queries
now expand conkey in a derived SELECT list instead. Version 8.4+ uses
generate_subscripts; older branches use generate_series with array_lower and
array_upper. Both preserve conkey order, and FK columns are paired with the
same position in confkey. The old PK/UNIQUE pg_depend ordering is removed in
favor of ordinal key order even before 8.4. The derived query explicitly
projects source.oid AS constraint_oid, because OID is a system column before
PostgreSQL 12 and is absent from SELECT *. Namespace OIDs keep their normal
catalog references. No version-specific column or LATERAL dependency is added
to older branches.

Unit tests cover historical query selection from the resolver's 8.x default
through current branches, including the 8.4 function boundary, and all five
referential actions through PostgreSQL 18. Local YSQL 11/15 cases recreate and
exercise every ON UPDATE/ON DELETE action, including NULL/default target values
and 23503 rejection. Parent deletion with RESTRICT rejects immediately even
when the FK is declared deferred; NO ACTION allows deleting the parent before
the child within one transaction and committing after both deletions.

A separate local case forces 8.3, 8.4, 9.2 and 15 query selection against each
current YSQL catalog and verifies reversed composite PK, UNIQUE and FK column
order. This establishes query execution and key-pairing semantics on those two
engines, not real execution against historical PostgreSQL servers; legacy
server compatibility still needs its own engine matrix. PostgreSQL 18 action
rendering has unit coverage in this batch, not new PostgreSQL 18 real-engine
verification. Primary version references: [PostgreSQL 8.4 release notes](https://www.postgresql.org/docs/9.2/release-8-4.html)
and [PostgreSQL 9.3 release notes](https://www.postgresql.org/docs/9.3/release-9-3.html).


Validation on 2026-10-08: the full PostgreSQL/YSQL/command regression executed
and passed 766 cases, with one optional external YSQL test skipped. Gradle
reused the plugin result (81 passing cases); Yugabyte assemble was up to date.
Both local compatibility tasks passed all 39 cases each (78 real-engine cases).
Initial focused failures exposed the missing pre-12 system OID projection, an
incorrect namespace alias introduced during the rewrite, and missing options
in the direct-reader test setup; all were corrected before the successful
focused and complete runs. No external or production database was accessed,
and no generated files were manually changed.


## PostgreSQL 18 foreign-key MATCH generation

The PostgreSQL 18-specific foreign-key factory now preserves the Schema model's
MATCH option, as the older PostgreSQL factories already do. Previously its
abstract base hook emitted no MATCH clause, silently changing MATCH FULL to
the database default. Standalone ALTER TABLE and inline CREATE TABLE paths
are covered across PostgreSQL 8, 11, 15 and 18, including an unset option.
The change is confined to PostgreSQL 18 SQL generation; metadata readers,
public APIs, XML and dependencies are unchanged. MATCH PARTIAL is retained
as requested by the model, consistently with older factories; SQL rendering
coverage does not establish database support for that option.


Validation on 2026-10-08: the focused foreign-key factory class passed all four
cases after correcting an ambiguous null setter call in the new test. The full
PostgreSQL, Yugabyte, command and plugin regression completed successfully:
PostgreSQL 220, Yugabyte four (one optional external test skipped), and command
543 cases passed; the unchanged plugin result (81 cases) was reused. No real
database test was rerun for this PostgreSQL 18-only generation change, and
PostgreSQL 18 server execution remains unverified in this batch.


## Constraint recreation and keyword recognition

PostgreSQL PRIMARY KEY and UNIQUE SQL factories now retain DEFERRABLE and
INITIALLY IMMEDIATE/DEFERRED from the Schema model in both standalone ALTER
TABLE and inline CREATE TABLE generation. A PostgreSQL 9.0 factory isolates
this syntax from the older 8.x branch, whose generation remains unchanged;
the PostgreSQL 18 temporal factory also preserves the setting. FK and unique
constraints share the same deferrability renderer. No existing public API,
XML format or dependency changes are required; the 9.0 factory is additive.
Version reference: [PostgreSQL 9.0 CREATE TABLE](https://www.postgresql.org/docs/9.0/sql-createtable.html).

YSQL still supports deferred foreign keys rather than deferred PRIMARY KEY or
UNIQUE constraints. Generated SQL preserves the request instead of silently
changing its timing. The local YSQL matrix explicitly checks the database's
0A000 rejection for the latter constraints, rather than claiming their support.
See [YSQL CREATE TABLE](https://docs.yugabyte.com/stable/api/ysql/the-sql-language/statements/ddl_create_table/).

Metadata recognition now masks quoted identifiers and string literals before
looking for PERIOD, WITHOUT OVERLAPS and terminal NOT ENFORCED syntax. Names
such as period_id and CHECK expressions containing 'NOT ENFORCED' no longer
acquire PostgreSQL 18-only flags. Actual temporal syntax, including quoted
period column names, retains unit coverage. On local YSQL 11/15 the new
composite-FK case reads and recreates MATCH FULL and MATCH SIMPLE through
both table and standalone paths, checks partial-NULL rejection/acceptance,
and verifies that a CHECK literal is still enforced without a spurious flag.
PostgreSQL 18 temporal execution and historical PostgreSQL servers remain
outside this real-engine matrix. MATCH semantics reference:
[PostgreSQL 18 CREATE TABLE](https://www.postgresql.org/docs/18/sql-createtable.html).


Validation on 2026-10-08: 11 focused unit cases and two new real-engine cases
on each YSQL baseline passed. The subsequent complete regression passed
PostgreSQL 222, Yugabyte four and command 543 cases (769 executed passes),
with one optional external YSQL test skipped. The plugin's 81 passing cases
and Yugabyte assemble were up to date. Both full local compatibility tasks
passed 41 cases each (82 real-engine cases), including the explicit unsupported
constraint checks. Commands: :sqlapp-core-postgres:test, :sqlapp-core-yugabyte:test,
:sqlapp-command:test, :sqlapp-gradle-plugin:test, :sqlapp-core-yugabyte:assemble,
:sqlapp-core-dialect-test:yugabyte11CompatibilityTest and
:sqlapp-core-dialect-test:yugabyte15CompatibilityTest through the repository
Gradle wrapper. No external/production database was accessed and no generated
files were manually changed.


## Covering constraints, NULL uniqueness and Schema XML

PostgreSQL 11+ constraint metadata now distinguishes key positions from
INCLUDE payload positions using pg_index.indnkeyatts. PostgreSQL 15+ reads
indnullsnotdistinct for UNIQUE NULLS NOT DISTINCT. Dedicated 11 and 15 queries
keep these catalog fields out of earlier branches. The same version boundaries
apply to standalone and inline constraint generation, with INCLUDE preceding
DEFERRABLE and NULLS NOT DISTINCT restricted to UNIQUE rather than PRIMARY KEY.
PostgreSQL 18's temporal factory composes the same options. Earlier branches
retain their previous omission of options they cannot express, consistently
with existing index generation. See [PostgreSQL 15 CREATE TABLE](https://www.postgresql.org/docs/15/sql-createtable.html).

The canonical Schema model stores payload columns in the backing Index.
UniqueConstraint now exposes the existing IncludeColumnsProperty/getIncludes
view of that collection and persists the existing optional includes element
in standalone constraint XML and table PRIMARY KEY XML. Payload columns take
part in equality, clone and difference processing while remaining separate
from key columns. Existing XML without includes is unchanged and accepted;
older binaries are not guaranteed to retain the newly persisted constraint
payload metadata. No dependency changes or vendor-specific fields were added
to core.

The local YSQL test reads reversed composite keys with quoted payload names,
round-trips through Schema XML, recreates PRIMARY KEY/UNIQUE tables and
standalone UNIQUE constraints, and verifies that different payloads cannot
bypass key uniqueness. It checks ordinary NULL-distinct behavior on YSQL 11
and NULLS NOT DISTINCT behavior on YSQL 15. Historical PostgreSQL engines and
PostgreSQL 18 temporal combinations remain outside this real-engine matrix.

A separate shared fix addresses SchemaUtils.readXml(Reader): the initial root
probe now reads through the same BufferedReader that is marked and reset for
object loading. Previously it consumed the underlying Reader directly, so
the subsequent load could see end-of-input. Focused tests cover automatic
Table/Catalog detection with Unicode and normal Schema equality. The XML format
and caller ownership behavior of this reader API are unchanged.


Validation on 2026-10-09: the focused common-model/Reader tests and PostgreSQL
query/generator tests passed after fixing the new test setup's API types. The
initial real-engine tests exposed both the automatic Reader consumption bug
and missing constraint includes serialization; both are now fixed and the
focused cases pass on both YSQL baselines. The full core/all-retained-dialect/
command/plugin regression then executed 2,692 passing cases, with one optional
external YSQL case skipped. The plugin's 81 cases were executed, and Yugabyte
assemble was up to date. Both full local compatibility tasks passed 42 cases
each (84 real-engine cases). No generated files were manually modified and no
external/production database was accessed.

Executed repository Gradle Wrapper tasks: :sqlapp-core:test and every included
:sqlapp-core-{db}:test, :sqlapp-core-test:test (no sources), :sqlapp-command:test,
:sqlapp-gradle-plugin:test, :sqlapp-core-yugabyte:assemble,
:sqlapp-core-dialect-test:yugabyte11CompatibilityTest and
:sqlapp-core-dialect-test:yugabyte15CompatibilityTest. Scope selection used the
retained module list in settings.gradle and excluded generic external-database
integration tasks.


## CHECK inheritance, standalone constraint comments and FK XML identity

PostgreSQL CHECK metadata now preserves a terminal NO INHERIT option in
Specifics.noInherit, without matching that text in quoted identifiers/literals.
PostgreSQL 9.2+ ordinary and PostgreSQL 18 factories retain it before enforcement
and validation options. Earlier factories omit the unavailable syntax, while
malformed boolean values fail clearly. No new catalog column dependency is
introduced in older readers. The local YSQL case verifies connoinherit and
convalidated after recreation and checks that invalid new rows still fail.
It does not establish child-table inheritance support in YSQL or test that
behavior on a real PostgreSQL server. Reference:
[PostgreSQL inheritance](https://www.postgresql.org/docs/11/ddl-inherit.html).

Standalone CHECK, FK and PRIMARY KEY/UNIQUE generation now returns CREATE
followed by SET_COMMENT operations when remarks are present. UNIQUE/PRIMARY
KEY generation also restores the backing-index comment, resolving remarks
from the table's canonical index when needed after XML loading. The new index
created by an ADD CONSTRAINT column list uses the constraint name; a different
source index name is not used as a nonexistent destination comment target.
Schema-qualified names and Unicode/apostrophes are escaped by the SQL builder.
Callers must execute all returned operations to retain comments. Inline table
creation keeps its existing comment phase, filtering standalone comment
operations for separately added NOT VALID constraints to avoid duplicates.

The integration test also exposed an independent shared XML bug: detached FK
reference columns could be resolved to identically named local columns when
reading a standalone Table, causing generated SQL to reference the child
itself and allow an otherwise invalid foreign-key value. The XML reader now
keeps referenced columns owned by a referenced Table and adds them together,
retaining schema identity and column order. Self-references reuse canonical
local columns. Focused tests cover same-named columns, reversed composite
positions, quoted cross-schema names, standalone self-references and canonical
Schema parent/self relationships. Public APIs and XML formats are unchanged;
this changes the incorrectly restored reference ownership for existing XML.
The local YSQL case round-trips the Table through XML, recreates constraints
and then the table, verifies constraint/index comments, and checks CHECK/FK/
UNIQUE violations through SQLSTATE 23514/23503/23505.

Validation on 2026-10-09: focused PostgreSQL constraint/metadata tests and shared
FK XML tests passed. The focused disposable YSQL 11/15 tests passed after fixing
the XML ownership defect exposed by the FK violation assertion. The full Gradle
core/all-retained-dialect/command/plugin test run, Yugabyte assemble, and
`yugabyte11CompatibilityTest` / `yugabyte15CompatibilityTest` completed with
BUILD SUCCESSFUL: 2,697 ordinary cases passed and one optional external YSQL
case was skipped; both real-engine matrices passed 43 cases each (86 total).
The plugin's 81 cases executed; assemble was up to date. No external or production
database was accessed and no generated files were edited manually. PostgreSQL
historical/18 behavior is covered by unit tests, not real-engine verification;
child-table inheritance remains outside the YSQL compatibility scope.


## Array element type modifiers and fractional datetime precision

PostgreSQL column metadata queries now decode length, numeric precision/scale,
and datetime/interval precision using the array element OID when `attndims > 0`.
Scalar columns continue to use their original type OID. The existing base,
10/11, 12-17 and 18 query variants retain their version-specific catalog fields;
no new catalog columns, module dependencies, resolver branches or public APIs
were introduced. The base query is also used for composite-type attributes.
This fixes invalid regeneration such as `numeric(0,0)[][]` for a source
`numeric(12,3)[][]`, and lost `varchar(n)` array element lengths.

The shared PostgreSQL column mapper now stores fractional datetime and interval
precision in the Schema model's `length`, where the type generators expect it;
`scale` retains numeric scale. Explicit datetime precision zero is preserved.
This affects ordinary and array columns. XML remains in the existing format,
although newly read metadata now includes the corrected length/precision.

A disposable YSQL 11/15 case reads a table, round-trips Schema XML, regenerates
the table and checks array dimensions, numeric precision/scale, varchar length
and timestamp precision. COPY and staging upsert then move two-dimensional UUID,
numeric, boolean, date, timestamp, bytea and varchar values, SQL NULL, empty
arrays and NULL elements. The second phase swaps NULL/empty/nonempty values
between existing rows, checking updates as well as inserts. Binary elements
include zero, 0xff, quotes, backslashes and empty bytea; strings include Unicode,
apostrophes, quotes and backslashes. Unit tests cover scalar/array datetime
precision 0/3/6, numeric zero/nonzero scale, character lengths and interval
precision. Domain metadata and interval field qualifiers are separate paths and
are not expanded by this change; other element types, arbitrary dimensions and
non-default array lower bounds remain outside the verified scope.

Catalog references: [pg_type element OIDs](https://www.postgresql.org/docs/11/catalog-pg-type.html)
and [pg_attribute type modifiers/dimensions](https://www.postgresql.org/docs/11/catalog-pg-attribute.html).

Validation on 2026-10-09: the added case first exposed invalid numeric array DDL
and missing datetime precision; both defects were fixed before the successful
focused tests. The final Gradle run executed `:sqlapp-core-postgres:test`,
`:sqlapp-core-yugabyte:test`, `:sqlapp-command:test`,
`:sqlapp-core-yugabyte:assemble`, and both full YSQL compatibility tasks.
BUILD SUCCESSFUL: PostgreSQL 230, Yugabyte 4 and command 543 cases passed (777),
with one optional external YSQL case skipped. Both disposable real-engine
matrices passed 44 cases each (88 total); assemble was up to date. Unchanged
core/other-dialect/plugin suites were not rerun in this batch. No external or
production database was accessed and no generated files were edited manually.
The base/18 column-query variants and interval precision were not verified on
historical/18 real engines; their compatibility limits remain as stated above.


## Array domains, fractional precision and quoted base types

PostgreSQL domain metadata now resolves the base array's element OID for its
numeric precision/scale, character length and datetime/interval precision.
Builtin element names come from `format_type`; user-defined names are always
schema-qualified with catalog identifier quoting, independently of search_path.
Array suffixes live only in the Domain's array dimension. Scalar domains continue
to use their original base type. The column and domain readers share a local
precision selector that retains zero fractional precision in model `length`;
numeric `scale` remains separate. Existing catalog/version boundaries and
dialect resolution are unchanged.

The PostgreSQL domain factory preserves qualified, quoted OTHER base type names
rather than passing them through the builtin type formatter. The domain reader
also retains the actual user-defined base type instead of `USER-DEFINED`.
This applies to scalar and array domains whose base type remains available
when the domain is recreated. Dependency ordering for arbitrary domain/type
graphs is not added.

Focused coverage includes XML round trips and recreated numeric(12,3)[][],
varchar(7)[][], timestamp(3)[][] and timestamp(0) domains; defaults, CHECK,
NOT NULL, comments, NULL elements, timestamp rounding and actual 23502/23514
rejections are checked. Another case recreates scalar/array domains over an
enum in a quoted schema, read while that schema is on search_path and recreated
after removing it, with a dot in the quoted type name,
apostrophes in labels, defaults, cardinality checks and invalid-enum SQLSTATE
22P02. Unit SQL generation covers the existing PostgreSQL 8.3/11/15/18 registry
boundaries and exactly one array suffix per modeled dimension.

These tests exposed a separate common issue: type-name normalization stripped
outer quotes from qualified names and changed quoted identifier contents.
`SchemaUtils.normalizeDataType` and Dialect type matching now preserve quoted
qualified names, including escaped quotes and repeated spaces. Existing builtin
normalization, including quoted CHAR and enum literals, remains covered.
The additive `SchemaUtils.isQuotedQualifiedTypeName(String)` utility shares the
quote-aware detection between both paths. Existing signatures, XML format,
configuration and module dependencies are unchanged. This shared correction
also applies to Column type setters; it is not a PostgreSQL-only conditional.
Single unqualified quoted type names retain the previous normalization behavior.

References: [domain catalog representation](https://www.postgresql.org/docs/11/catalog-pg-type.html)
and [CREATE DOMAIN](https://www.postgresql.org/docs/11/sql-createdomain.html).

Validation on 2026-10-09: focused common SchemaUtils/Dialect, PostgreSQL domain
SQL/column precision, and both disposable YSQL domain tests passed. The expanded
search_path assertion exposed unqualified base type metadata; always-qualified
catalog names fixed it and both focused engine runs then passed. The final
Gradle invocation selected core/all-retained-dialect `test` tasks, command and
plugin tests, Yugabyte assemble, and both full YSQL compatibility tasks:
BUILD SUCCESSFUL. Across this batch's initial run and final rerun, 2,703 ordinary
cases passed and one optional external YSQL case was skipped. The final run
reused unchanged core/plugin results as UP-TO-DATE; PostgreSQL/Yugabyte/command
and both engine matrices executed again after the final catalog SQL change.
Both matrices passed 46 cases each (92 real-engine cases); assemble was up to
date. No external or production database was accessed and no generated files
were edited manually. Historical PostgreSQL and PostgreSQL 18 real engines,
arbitrary domain dependency graphs, interval field qualifiers and single
unqualified quoted type-name normalization remain outside this verified scope.


## Unconstrained numeric and varchar, and PostgreSQL 15 numeric scale

PostgreSQL metadata and SQL generation now preserve omitted numeric precision
and varchar length, including arrays and domains. The catalog queries keep
unconstrained varchar length NULL instead of -5 or an artificial 1 GiB limit.
Domain readers retain SQL NULL for optional precision fields instead of treating
it as zero. SQL generation emits bare NUMERIC, DECIMAL and VARCHAR when the
modifier is absent, preserving the server's unconstrained semantics.

Numeric catalog modifiers decode scale as a signed 11-bit value. This decoding
also preserves legacy nonnegative scales and uses no new catalog columns.
PostgreSQL 15 and later accept negative scale and scale greater than precision;
type-name matching also accepts negative-scale NUMERIC/DECIMAL arrays from 15.
Generation validates precision 1..1000 and scale -1000..1000, and rejects extended
scale on pre-15 dialects rather than changing its rounding semantics. An explicit
nonzero scale requires precision. Existing public APIs, XML and dependencies
are unchanged. SQL changes cover all four PostgreSQL column query versions and
the shared domain query; PostgreSQL-compatible dialects inherit these fixes.

The real-engine regression recreates scalar/array columns and domains through
Schema XML, then stores a 1,501-digit integer with fractional digits and a
40,000-character Unicode string through bulk insertion. It checks exact values,
NULL array elements and unconstrained catalog modifiers. The 15 baseline also
checks numeric(2,-3) rounding, numeric(3,5) fractional rounding, domain defaults
and overflow SQLSTATE 22003; the 11 baseline checks rejection of negative scale.

References: [PostgreSQL 15 numeric types](https://www.postgresql.org/docs/15/datatype-numeric.html)
and [numeric typmod implementation](https://github.com/postgres/postgres/blob/REL_15_STABLE/src/backend/utils/adt/numeric.c).


Validation on 2026-10-09: the focused boundary cases first reproduced invalid
varchar modifiers and loss of signed numeric scale; all focused cases passed
after correction. The final invocation executed PostgreSQL, Yugabyte and command
`test` tasks, Yugabyte `assemble`, and both full YSQL compatibility tasks:
BUILD SUCCESSFUL in 7m 7s. PostgreSQL passed 234 cases, Yugabyte passed 4 with one
optional external test skipped, and command passed 543 (781 ordinary passes).
Both engines passed all 48 cases each (96 real-engine passes). Public APIs,
configuration, XML format and module dependencies are unchanged. Invalid numeric
modifiers now fail explicitly instead of being clipped. Only disposable local
containers were used; no external/production database was accessed and generated
files were not edited manually. Historical PostgreSQL and PostgreSQL 18 real
engines remain unverified; their SQL/type boundaries are covered by unit tests.


## Bit strings and interval field restrictions

PostgreSQL column queries (all supported query variants) and domain queries now
keep an omitted bit varying length NULL rather than -1. SQL generation emits
bare VARBIT for the unconstrained type; explicit BIT/VARBIT lengths remain
unchanged. Type matching recognizes the catalog spelling BIT VARYING, including
length modifiers and arrays. BIT without a length still resolves from catalog
metadata as BIT(1). Bytea behavior is unchanged.

PostgreSQL 12+ column queries now decode interval field masks instead of reporting
every qualified interval as unrestricted INTERVAL. Historical column readers and
domains already decode these masks. SQL generation preserves YEAR, YEAR TO MONTH,
DAY TO HOUR, DAY TO MINUTE, DAY TO SECOND, HOUR TO MINUTE, HOUR TO SECOND, MINUTE
TO SECOND and single-field restrictions. The YEAR spelling is corrected. Native
PostgreSQL interval matching uses fractional precision after the field clause,
with no SQL-standard leading precision defaults. Explicit fractional precision
0..6 is retained for INTERVAL and clauses ending in SECOND, including arrays.

A common Schema issue also affected other dialects: AbstractColumn omitted
interval modifiers from XML and ignored interval length when comparing objects.
Column, Domain, TypeColumn and NamedArgument now retain existing length/scale
attributes through XML, and precision changes affect equality. This uses existing
XML attributes and APIs; old XML without modifiers remains readable with absent
values. No vendor-specific conditional was added to core. For PostgreSQL,
length represents fractional precision; other dialects retain their existing
interpretation of model length/scale. This correction may expose real interval
precision differences previously omitted from migration comparisons.

The focused engine tests cover unlimited, bounded and fixed bit strings, scalar
and array columns, domains, 42,000-bit values, NULL and empty array elements, and
length mismatch errors. Interval coverage compares values before and after
Schema XML recreation for 20 field/precision declarations, scalar/array columns,
scalar/array domains and defaults, including precision zero and fractional
rounding. These tests use disposable local YSQL 11 and 15 containers.

References: [bit string types](https://www.postgresql.org/docs/15/datatype-bit.html)
and [interval fields and precision](https://www.postgresql.org/docs/15/datatype-datetime.html).


Validation on 2026-10-09: focused tests reproduced -1 bit varying length and
loss of interval YEAR field restrictions and precision-zero rounding. The
initial common test incorrectly treated TypeColumn as a standalone XML root;
it was corrected to round-trip Type/Procedure owners for type attributes and
arguments. The final focused common XML/equality tests and PostgreSQL native
type matching/generation tests passed, as did both focused engine runs.
The final regression selected core and every retained dialect `test` task,
command/plugin tests, Yugabyte assemble and both full YSQL compatibility tasks:
BUILD SUCCESSFUL in 9m 51s. Results total 2,711 ordinary passes with one optional
external YSQL case skipped, and 50 passes on each engine (100 real-engine passes),
with no failures or errors. No external/production database was accessed, no
module dependency/version/configuration was changed, and no generated files
were edited manually. Historical PostgreSQL and PostgreSQL 18 remain unverified
on real engines; unit tests cover type generation and matching at those boundaries.
