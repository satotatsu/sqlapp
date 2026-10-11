# CockroachDB dialect

Add `com.sqlapp:sqlapp-core-cockroach` at the same version as the other sqlapp
artifacts. It reuses `sqlapp-core-postgres` and its PostgreSQL JDBC driver;
no Cockroach-specific driver or shared Schema XML change is required.

Use the standard high-level entry point:

```java
Dialect dialect = DialectResolver.getInstance().getDialect(connection);
```

The resolver recognizes the CockroachDB release banner, including connections
whose PostgreSQL driver reports PostgreSQL as the product. When necessary it
uses a read-only `SELECT version()`. An unrecognized CockroachDB version fails
clearly. Explicit resolution uses the Cockroach release version, not the wire
protocol's PostgreSQL version:

```java
Dialect dialect = DialectResolver.getInstance()
    .getDialect("CockroachDB", 25, 4, null);
```

## Verified scope

The minimum release is 24.3. The disposable single-node qualification matrix
uses **v24.3.0** and **v25.4.0**, with 31 real-engine tests per image. Later
releases resolve to the same baseline but need separate qualification.

The current database's user schemas, tables, columns, primary/unique/check and
foreign-key constraints, indexes, views, sequences, enum types and SQL/PLpgSQL
functions are supported. Function overloads use argument signatures as stable
identities across recreation.

Metadata retains executable CREATE definitions for tables, indexes, views,
sequences, enum types and functions. Supplementary columns and constraints
remain available in the Schema model. Table definitions preserve hash-sharded
keys, hidden/generated columns, column families, STORING indexes and predicates.
Schema XML and the standard CREATE factories recreate these objects together.
Enum DDL is reconstructed from ordered catalog labels because SHOW CREATE TYPE
is unavailable in the qualified builds.

A complete `definition` is authoritative: editing supplementary fields does not
rewrite that SQL. Edit the definition when changing such an object. Newly
modeled tables/indexes/sequences without definitions use compatible PostgreSQL
CREATE generators. Target topology translation and cyclic foreign-key
reconstruction remain separate from CREATE qualification.

## Schema differences, ALTER and DROP

Use the existing registry entry point, with a freshly read original Schema and
an explicitly edited or separately read target:

```java
var registry = dialect.createSqlFactoryRegistry();
registry.getOptions().setDecorateSchemaName(true);
var operations = registry.createSql(original.diff(target));
```

This returns reviewable operations; generation itself does not execute SQL.
Qualified changes include table and column renames, adding/dropping ordinary
columns, column types/defaults/NULL constraints, CHECK/UNIQUE/foreign-key
constraints, indexes with STORING/predicates, and table/column/index comments.
Existing row values are retained in the tested changes. Adding a NOT NULL
column with a default uses CockroachDB's native backfill; altering existing
columns does not invent data repairs for incompatible values.

Table ALTER consumes modeled components even when a saved CREATE definition
is present. Definition-only table changes are rejected. Index structural
changes require an updated complete definition or an explicitly cleared
one before using modeled keys/includes/predicate; stale definitions cannot
silently override those edits. Index replacement generates DROP then CREATE
and can fail when dependents exist. UNIQUE removal targets its owning index;
DROP operations do not add an implicit CASCADE.

Functions and views use complete target CREATE definitions with CREATE OR
REPLACE. Function identity/signature changes require an explicit migration;
server-side return-type and dependency restrictions still apply. The tests
retain another function overload and a dependent view during replacement.
Enums support ordered label additions and one label rename while retaining
rows using the type. Removal and reordering are rejected.

Sequence metadata also models start, increment, minimum, maximum and cycle.
ALTER supports increment/range/start configuration without resetting the
current value. Restart/high-water replay, CYCLE, cache and ownership changes
require separate handling. CockroachDB documents CYCLE as unimplemented in its
[ALTER SEQUENCE reference](https://docs.cockroachlabs.com/docs/stable/alter-sequence).

Schema differences delete views/functions before tables and tables before
sequences/types; foreign-key child tables precede their parents. Additions and
changes process prerequisites before the tested dependents. This qualifies
ordinary acyclic dependencies, not every cross-object routine/view dependency
or simultaneous key-and-reference change. Primary-key changes, generated or
identity column alterations/additions, schema/database moves and advanced
storage changes and in-place locality alterations require explicit migrations. Unsupported changes fail
at generation rather than silently returning empty SQL.

Multiple DDL statements are not guaranteed to be atomic. Review and execute
operations deliberately, surface execution failures and re-read the Schema
before retrying after partial application.

## Placement specifics

Placement is retained through the shared Schema model and XML. Common cases
need only one value:

```java
table.getSpecifics().put("COCKROACH_LOCALITY", "GLOBAL");
catalog.getSpecifics().put("COCKROACH_PRIMARY_REGION", "us-east1");
```

| Object | Specific | Value |
| --- | --- | --- |
| Table | `COCKROACH_LOCALITY` | `GLOBAL`, `REGIONAL BY TABLE [IN region]`, or `REGIONAL BY ROW [AS column]` |
| Catalog | `COCKROACH_PRIMARY_REGION` | Plain region name |
| Catalog | `COCKROACH_REGIONS` | Optional JSON array of plain region names, including the primary region |
| Catalog | `COCKROACH_SECONDARY_REGION` | Optional different region from that list |
| Catalog | `COCKROACH_SURVIVAL_GOAL` | Optional `ZONE` or `REGION` |

For example, `COCKROACH_REGIONS` can be `["us-east1","us-west1"]`.
Regions in a locality SQL clause must be quoted when necessary, for example
`REGIONAL BY TABLE IN "us-east1"`. `IN PRIMARY REGION` and a quoted custom row
region column are supported. The target must already provide the named regions
and satisfy CockroachDB's replication, license and survival-goal requirements.

Table reading extracts the outer LOCALITY clause from SHOW CREATE, separately
from defaults, quoted names and comments. An explicit locality specific
**overrides only that clause** in a saved definition; the table body and
comments remain intact. Without a saved definition, the normal modeled CREATE
adds the clause. Removing the specific retains a saved definition's locality;
clear or edit the definition when intentionally removing saved SQL. Invalid
locality values fail generation. Schema/Table ALTER of placement specifics
continues to require an explicit migration.

Catalog reading uses SHOW REGIONS and SHOW SURVIVAL GOAL for the current
database. Catalog CREATE applies PRIMARY REGION, missing additional regions,
SECONDARY REGION and SURVIVE **to an existing target database**, then generates
its contents. As with the shared Catalog API, it does not create or select the
database: execute on a connection already using the intended target (Cockroach
`USE`, or a database-specific JDBC URL; PostgreSQL JDBC `setCatalog` does not
select it). Existing regions are not removed. With no placement specifics,
ordinary Catalog behavior is retained. The default CREATE-if-absent option
also permits reusing the database's public Schema; disabling it retains strict
schema creation.

PRIMARY REGION owns `public.crdb_internal_region`. The reader does not model
this engine-managed enum as a separately creatable Type in a multi-region
database; dependent columns still retain its qualified name. User-created
same-named types in ordinary databases or other schemas remain visible.

The real-engine matrix uses an owned single-node container with one declared
region/zone. It verifies all three table locality modes, a custom row-region
column, specificity override, Catalog/Table XML and DDL recreation, and an
ordinary non-regional database. Additional-region/secondary/REGION-failure
SQL is covered by generation tests, not multi-region runtime qualification.
See the official [SHOW CREATE examples](https://www.cockroachlabs.com/docs/v24.3/show-create).

## Data and transactions

The normal bulk APIs select the dedicated providers automatically. COPY reuses
the PostgreSQL streaming implementation. An executor-owned COPY transaction
commits on success and rolls back on failure, restoring auto-commit. Existing
caller transactions remain caller-owned.

UPSERT uses streaming JDBC batches with CockroachDB `ON CONFLICT`, the shared
bulk planner, key validation and duplicate policies. It supports insert/update,
insert-only, update-only and key-only tables. JSON, built-in arrays, UUID,
Unicode, binary values and decimals are covered. PostgreSQL temporary-table
`CREATE TABLE AS ... WITH NO DATA` is not used. A configured staging table name
is rejected because this path creates no staging table. Unsupported batch
options retain actionable validation errors.

The default executor-owned UPSERT transaction is atomic. Turning off
`useTransaction` can leave earlier batches committed after a later failure.
Caller transactions are never committed or rolled back by the provider.
SQLSTATE **40001** is propagated; callers must retry their entire transaction
with a replayable row source. There is no automatic retry of consumed streams.
Shared migration checkpoint/resume and chunked load paths are also qualified.

One-dimensional enum arrays are qualified for metadata, Schema XML, saved and
modeled CREATE DDL, COPY and JDBC UPSERT. Element types retain quoted schema
and type names, including names that collide with built-in types. Catalog
identity resolves array elements rather than guessing from an underscore
prefix. Tests distinguish SQL NULL, empty arrays, NULL elements and literal
`NULL`, and cover commas, quotes and Unicode labels. Invalid enum values
surface the database error and roll back executor-owned bulk transactions.
This does not qualify arbitrary user-defined types or nested arrays; see the
[CockroachDB 24.3 array reference](https://www.cockroachlabs.com/docs/v24.3/array).


## Tests and limits

```shell
./gradlew :sqlapp-core-cockroach:test :sqlapp-core-cockroach:assemble
./gradlew :sqlapp-core-dialect-test:cockroachCompatibilityTest
```

The matrix starts and removes owned local Testcontainers and uses UUID schemas.
It does not authorize an external or production database.

Not yet qualified: multi-node failure/retry/performance, locality/region/zone
topology mapping and arbitrary zone configurations, ACL/owner replay, procedures, triggers,
domains, materialized views, all-database enumeration, sequence high-water
snapshots and general transactional DDL guarantees.
These remain separate work; PostgreSQL compatibility alone does not establish
support for them. See the [compatibility evidence and backlog](../docs/compatibility.md#cockroachdb-dedicated-dialect).

For table renames, generate `originalTable.diff(targetTable)` explicitly. A
Schema diff matches names; a delete/add pair retaining the same table ID is
rejected to avoid accidental data loss. Without stable IDs, review delete/add
operations explicitly: a Schema diff cannot infer rename intent.

Metadata reads use one query per table/view/sequence definition set and one
joined ENUM query; index definitions reuse the ordinary index query.
Query-count regression tests cover growing object sets. See the
[metadata audit fixes](../docs/compatibility.md#postgresql-family-metadata-audit-fixes-2026-10-11).
