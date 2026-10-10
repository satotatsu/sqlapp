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
uses **v24.3.0** and **v25.4.0**, with 16 real-engine tests per image. Later
releases resolve to the same baseline but need separate qualification.

The current database's user schemas, tables, columns, primary/unique/check and
foreign-key constraints, indexes, views, sequences, enum types and SQL/PLpgSQL
functions are supported. Function overloads use argument signatures as stable identities across recreation.

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
CREATE generators. Automatic target renaming, cyclic foreign-key reconstruction
and arbitrary ALTER/diff operations are not qualified by these CREATE tests.

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

## Tests and limits

```shell
./gradlew :sqlapp-core-cockroach:test :sqlapp-core-cockroach:assemble
./gradlew :sqlapp-core-dialect-test:cockroachCompatibilityTest
```

The matrix starts and removes owned local Testcontainers and uses UUID schemas.
It does not authorize an external or production database.

Not yet qualified: multi-node failure/retry/performance, locality/region/zone
configuration and topology mapping, ACL/owner replay, procedures, triggers,
domains, materialized views, all-database enumeration, sequence high-water
snapshots, user-defined-type arrays and general transactional DDL guarantees.
These remain separate work; PostgreSQL compatibility alone does not establish
support for them. See the [compatibility evidence and backlog](../docs/compatibility.md#cockroachdb-dedicated-dialect).
