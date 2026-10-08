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
