# YugabyteDB YSQL

Use `com.sqlapp:sqlapp-core-yugabyte` and a PostgreSQL-compatible JDBC driver.
This artifact depends on core and PostgreSQL to reuse the Schema model,
version-specific catalog readers, types, quoting and SQL factories. It adds no
driver dependency. YCQL is not supported.

The usual entry point is `DialectResolver.getInstance().getDialect(connection)`.
The resolver recognizes JDBC product `PostgreSQL` with a database version such
as `11.2-YB-2.20.1.1-b0` or `15.2-YB-2025.1.0.0-b0`. No extra query is executed.
The version must retain the `-YB-` marker; drivers stripping that marker cannot
be automatically distinguished from PostgreSQL. Do not use a database name or
JDBC URL as proof of product identity.

For explicit resolution, `getDialect("YugabyteDB", 15, 2, null)` takes the
**PostgreSQL engine version**, not the YugabyteDB release. Only engine majors
11 and 15 are accepted. Unknown majors and ambiguous YugabyteDB release
numbers fail with an actionable error. Existing PostgreSQL resolution stays
unchanged. Metadata-specific matches are not cached under `PostgreSQL`.

YugabyteDB 2025.1+ is based on PostgreSQL 15; earlier stable releases use 11.2.
Always inspect the connected engine, including during a major upgrade, rather
than inferring the engine from the product release:
https://docs.yugabyte.com/stable/faq/compatibility/
https://docs.yugabyte.com/stable/manage/ysql-major-upgrade-yugabyted/

Support covers ordinary relational schema metadata/recreation and bulk data
migration on the PostgreSQL 11 and 15 YSQL engines. YCQL and YugabyteDB physical
placement/sharding extensions are outside this compatibility scope.

## Bulk operations

`BulkInsertResolver.resolve(dialect)` selects PostgreSQL COPY through a YSQL
transaction wrapper. YSQL can commit COPY batches separately in auto-commit
mode; the wrapper starts one transaction, rolls back on failure and restores
auto-commit. When the caller already owns a transaction, it neither commits
nor rolls back that transaction. The caller must roll back after a failure.

`BulkUpsertResolver.resolve(dialect)` reuses the PostgreSQL executor: COPY to a
temporary staging table followed by `INSERT ... ON CONFLICT`. The existing
options for conflict keys, duplicate policy, insert/update actions and identity
values remain available. PostgreSQL providers retain their product checks;
YSQL registers its own providers so ordinary PostgreSQL selection is unchanged.
The PG15 YSQL SQL factory also uses ON CONFLICT instead of SQL MERGE, which YSQL
does not implement. Merge generation requires a modeled primary/unique key.

Set-based SCD2 snapshot acceleration is not enabled. Use the shared portable
snapshot path. Distributed conflicts propagate as SQLExceptions; caller-owned
transactions are not replayed. The database lease store retries 40001/40P01
only within transactions it owns, after rollback, with a bounded retry count.

References: [COPY transaction behavior](https://docs.yugabyte.com/stable/api/ysql/the-sql-language/statements/cmd_copy/),
[PG15 feature differences](https://docs.yugabyte.com/stable/api/ysql/pg15-features/).

The core metadata-identification extension is additive: existing product
resolvers return null by default and retain ordinary name/version resolution.
It is shared infrastructure for databases exposing another product's JDBC name;
Yugabyte-specific detection stays in this module.

## Verification

Module tests cover ServiceLoader resolution, engine boundaries, failure
propagation, mixed PostgreSQL/YSQL use, provider selection and generated SQL.
An optional external `YugabyteMetadataRoundTripTest` is skipped unless
`SQLAPP_YSQL_JDBC_URL`, `SQLAPP_YSQL_USER` and `SQLAPP_YSQL_PASSWORD` are supplied.
Only run that test against an explicitly authorized disposable database; it
creates and drops its own randomly named schema.

The reproducible local compatibility matrix is:

```shell
./gradlew :sqlapp-core-yugabyte:test :sqlapp-core-dialect-test:yugabyteCompatibilityTest
```

The configured JDBC driver is `org.postgresql:postgresql:42.7.11`.
It starts fresh disposable single-node containers, without external database
credentials or host data volumes, using these fixed images:

- `yugabytedb/yugabyte:2024.2.11.0-b36` (PostgreSQL 11).
- `yugabytedb/yugabyte:2026.1.2.0-b137` (PostgreSQL 15).

On 2026-10-08, all 25 tests passed on each image (50 real-engine tests).
The preceding PostgreSQL/YSQL/command regression passed 750 unit tests and skipped the
optional YSQL external-database test. Gradle reused the unchanged plugin
result (81 previously passing tests). The earlier wider core/retained-dialect/
command/plugin run passed 2,664 tests. Packaging and SPI descriptors were also
verified. No external or production database was accessed.

The matrix asserts the actual JDBC engine major and tests both engines for:

- Schema-based table, PK/FK/unique/check constraint, index, view and sequence recreation.
- Sequence comments with Unicode/apostrophes, configuration and nextval checks.
- Smallint/integer/bigint sequence types with ascending and descending increments.
- Full signed type bounds, CYCLE wraparound and non-cycling SQLSTATE 2200H
  after recreation, in a single session with CACHE 100.
- Complete FK-bearing Schema recreation excluding internal implementation triggers.
- Covering partial unique-index recreation with descending keys, unchanged catalog
  definitions and conditional duplicate rejection.
- Explicit NULLS FIRST/LAST and function-expression index recreation, including
  case-insensitive expression uniqueness and nullable values.
- PG15 NULLS NOT DISTINCT covering partial indexes and NULL duplicate rejection;
  PG11 ignores the option and retains ordinary UNIQUE NULL semantics.
- Generated parent/child identity alignment across root batches and final partial batches.
- Composite JDBC keyset resume after a persisted cursor.
- Quoted identifiers, identity, numeric, boolean, date/time, UUID, JSONB, bytea and text-array columns.
- Enum/domain, function and trigger metadata and executable recreation.
- INSTEAD OF trigger timing and INSERT/UPDATE/DELETE through a recreated
  trigger on a view that cannot be automatically updated.
- Disabled/ALWAYS/REPLICA trigger-state recreation, WHEN conditions, statement
  triggers and TRUNCATE events in an ordinary session.
- Table/view trigger-comment preservation, including Unicode and apostrophes.
- COPY beyond the 20,000-row auto-commit boundary, atomic failure rollback,
  Unicode, null/empty strings, decimals, binary, arrays and explicit identities.
- Key-only generated upsert and bulk upsert, including duplicate no-op behavior.
- Nested integer/text COPY and upsert, primitive-byte numeric arrays and bytea arrays.
- Staging upsert, duplicate policies, insert/update-only actions, caller rollback
  and temporary-table cleanup.
- Chunked checkpoint atomicity, pause/resume, sustained loads, keyset verification
  and repair, concurrent lease fencing, savepoints and snapshot write conflicts.

## Limits

The verified builds are compatibility baselines, not a claim that every
PostgreSQL statement works on every YugabyteDB release. Sharding/colocation,
hash/range physical index layout, placement/tablespaces, extensions, multi-node
failover and performance tuning are not covered. COPY tests cover nested integer/text arrays, primitive-byte smallint arrays and
one-dimensional bytea arrays; other array element types remain unverified.
Sequence metadata covers configuration, not live-position migration or ownership.

DDL transaction guarantees and isolation behavior depend on YSQL version and
server flags. DDL recreation tests execute outside caller transactions; they do
not promise PostgreSQL transactional-DDL semantics. Test production topology,
settings and load separately. See the
[integration-test guide](../sqlapp-core-dialect-test/README.md#yugabytedb-ysql-compatibility).
