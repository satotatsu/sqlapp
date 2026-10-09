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

On 2026-10-09, after the domain array/precision, qualified base type and shared
quoted-type-name fixes, the full matrix passed all 46 tests on each image
(92 real-engine tests). The core/all-retained-dialect/command/plugin regression
passed 2,703 cases across the initial run and final rerun, with one optional
external YSQL case skipped. Core and plugin results were reused as UP-TO-DATE
in the final run; PostgreSQL/Yugabyte/command and both full engine matrices
executed again after the final catalog SQL change. Yugabyte assemble was up to
date. No external or production database was accessed.

The matrix asserts the actual JDBC engine major and tests both engines for:

- Schema-based table, PK/FK/unique/check constraint, index, view and sequence recreation.
- Covering PRIMARY KEY/UNIQUE key/payload separation through Schema XML and regeneration;
  quoted INCLUDE columns and NULLS NOT DISTINCT on the PostgreSQL 15 baseline.
- All five FK ON UPDATE/DELETE actions, deferred NO ACTION versus immediate RESTRICT.
- Composite MATCH FULL/SIMPLE recreation and partial-NULL behavior; CHECK literals and
  period_id identifiers do not acquire PostgreSQL 18-only metadata flags.
- Explicit 0A000 rejection of deferred PRIMARY KEY/UNIQUE constraints on both baselines;
  these constraints are not part of the supported YSQL deferral scope.
- Legacy and modern constraint-query execution with reversed composite key positions
  on both current baselines; historical PostgreSQL servers themselves are unverified.
- NO INHERIT CHECK state with NOT VALID, standalone constraint/backing-index comments,
  and XML FK reference identity verified through actual constraint violations.
  Child-table inheritance itself is not part of the YSQL compatibility scope.
- NOT VALID CHECK/FK state, post-CREATE constraint addition, comments, legacy violations,
  new-write enforcement and VALIDATE before/after repair (23514/23503).
- Standalone TableReader recreation with quoted cross-schema composite FK references.
- View LOCAL/CASCADED CHECK OPTION and security_barrier preservation, write rejection (44000).
- YSQL 15 security_invoker base-table permission checking (42501) and YSQL 11 owner access.
- Deferrable FKs in both initial modes, standalone ADD CONSTRAINT, child-before-parent
  insertion and SET CONSTRAINTS checking with SQLSTATE 23503.
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
- Composite CREATE TYPE definitions, quoted attributes, collation, custom type/array
  references and type/attribute comments, including a changed search_path.
- Quoted view/column comments and recreated view query results.
- Same-named FKs on different tables, composite key order, constant CHECKs and
  table-constraint comments, with FK/check/unique rejection after recreation.
- Enum label order after ALTER TYPE BEFORE/AFTER, Unicode/apostrophe labels and comments.
- Multiple domain CHECK expressions, numeric precision/scale, defaults, NOT NULL and comments.
- Array domains over numeric(12,3), varchar(7) and timestamp(3), plus scalar
  timestamp(0), through XML and domain recreation; defaults, comments, NULL
  elements, rounding and actual NOT NULL/CHECK rejection are verified.
- Scalar/array domains over a quoted enum type in a quoted schema: read with
  the schema on search_path and recreated after removing it; qualified base
  names, defaults, cardinality checks and invalid enum rejection are verified.
- Standalone and table-based index comments with preserved uniqueness.
- INSTEAD OF trigger timing and INSERT/UPDATE/DELETE through a recreated
  trigger on a view that cannot be automatically updated.
- Disabled/ALWAYS/REPLICA trigger-state recreation, WHEN conditions, statement
  triggers and TRUNCATE events in an ordinary session.
- Table/view trigger-comment preservation, including Unicode and apostrophes.
- COPY beyond the 20,000-row auto-commit boundary, atomic failure rollback,
  Unicode, null/empty strings, decimals, binary, arrays and explicit identities.
- Key-only generated upsert and bulk upsert, including duplicate no-op behavior.
- Nested integer/text COPY and upsert, primitive-byte numeric arrays and bytea arrays.
- UUID/numeric/boolean/date/timestamp arrays with SQL NULL, empty arrays and NULL elements.
- Two-dimensional UUID/numeric/boolean/date/timestamp/bytea/varchar arrays through
  XML and table recreation, COPY and upsert; element numeric precision/scale,
  varchar length and timestamp precision are retained. NULL, empty and nonempty
  values are exchanged between existing rows; binary/string escaping is checked.
- Staging upsert, duplicate policies, insert/update-only actions, caller rollback
  and temporary-table cleanup.
- Chunked checkpoint atomicity, pause/resume, sustained loads, keyset verification
  and repair, concurrent lease fencing, savepoints and snapshot write conflicts.

## Limits

The verified builds are compatibility baselines, not a claim that every
PostgreSQL statement works on every YugabyteDB release. Sharding/colocation,
hash/range physical index layout, placement/tablespaces, extensions, multi-node
failover and performance tuning are not covered. COPY tests cover nested integer/text arrays, primitive-byte smallint arrays,
and one- and two-dimensional bytea/UUID/numeric/boolean/date/timestamp arrays,
plus two-dimensional varchar arrays. Other element types, arbitrary dimensions
and non-default lower bounds remain unverified. Domain array metadata is covered
for the builtin and enum cases listed above; arbitrary domain/type dependency
graphs, domain collation and interval field qualifiers remain unverified.
Multiple domain CHECKs are combined into one expression; their individual
constraint names are not retained. Composite ALTER, extension-specific attribute types and arbitrary composite-type
dependency graphs remain unverified. YSQL 11 rejects ALTER TYPE DROP ATTRIBUTE;
real dropped-attribute recreation is not covered.
Sequence metadata covers configuration, not live-position migration or ownership.
FK recreation also covers standalone TableReader reads with quoted, cross-schema
composite references. Referenced columns retain their parent-table owner even
when the complete parent table is not loaded. View security/check options are covered as listed above. Role/GRANT migration,
RLS policies and predicate-leakproofness guarantees are not covered. NOT VALID
CHECK/FK state is preserved; domain/partition-specific validation and migration
of legacy violating rows into a newly constrained target are outside this scope.

DDL transaction guarantees and isolation behavior depend on YSQL version and
server flags. DDL recreation tests execute outside caller transactions; they do
not promise PostgreSQL transactional-DDL semantics. Test production topology,
settings and load separately. See the
[integration-test guide](../sqlapp-core-dialect-test/README.md#yugabytedb-ysql-compatibility).


Additional numeric boundary coverage preserves unconstrained numeric/varchar
columns, arrays and domains through XML and recreation, with 1,501-digit values,
40,000-character Unicode strings and NULL array elements. The PostgreSQL 15
baseline checks negative and excess numeric scale, rounding, domain defaults
and overflow; the 11 baseline verifies the unsupported syntax boundary.
See [compatibility details](../docs/compatibility.md#unconstrained-numeric-and-varchar-and-postgresql-15-numeric-scale).

Latest validation on 2026-10-09 after these numeric boundary fixes: both full
matrices passed 48 cases each (96 real-engine cases). PostgreSQL/Yugabyte/command
regression passed 781 cases with one optional external YSQL test skipped;
Yugabyte assemble and the final Gradle invocation succeeded. The earlier
46-case/2,703-case totals above describe the preceding batch and are retained
as historical evidence, not the scope executed for this update.


Bit and interval preservation coverage now includes unlimited bit varying,
fixed/bounded bit strings, empty/NULL array elements, and 20 interval
field/precision declarations. Scalar/array columns and scalar/array domains
(including defaults) are compared before and after Schema XML recreation.
The shared model also retains interval precision in XML and comparison.
See [bit/interval compatibility details](../docs/compatibility.md#bit-strings-and-interval-field-restrictions).

Latest validation on 2026-10-09 after bit/interval and shared interval XML fixes:
50 tests passed on each baseline (100 real-engine tests). Core/all-retained-
dialect/command/plugin regression results total 2,711 passes and one optional
external YSQL skip. The final invocation and Yugabyte assemble succeeded.
The earlier totals above describe preceding batches. No external or production
database was accessed; historical PostgreSQL and PostgreSQL 18 were not run.


Routine type regression now covers independent OID lookups across connections,
quoted enum renames, unsigned OID validation, UUID/custom-enum array returns,
and scalar/array function overload identity and invocation after XML recreation.
The PostgreSQL generator preserves routine array dimensions and honors schema
qualification. Complex default/TABLE-return parsing is outside this new coverage.
See [routine OID and array details](../docs/compatibility.md#routine-oid-isolation-and-array-type-identity).


Routine recreation coverage also includes catalog-derived argument names/modes,
complex defaults, OUT/INOUT/VARIADIC, TABLE/SETOF, custom array arguments, quoted
dotted function names and colliding body delimiters. Procedure/local SET cases
use executable definition and are verified after dropping the originals. The expanded
cases use generated signature-aware DROP operations and verify restored function
and procedure comments. See
[compatibility details](../docs/compatibility.md#routine-catalog-arguments-and-executable-recreation).

Unnamed arguments and default alignment across OUT/INOUT arguments also have
XML/recreation/execution coverage on both engines. The latest ordinary regression
has 2,721 passes and one optional external case skipped; the preceding full YSQL
regression has 55 passes per engine, followed by six focused routine matrix passes
after the final unnamed-argument correction. Detailed validation scope is recorded
in the compatibility notes linked above.

Routine EXECUTE privileges now have reader/XML/GRANT/REVOKE coverage for zero-input
and array overloads, custom quoted types, procedures, PUBLIC and grant options.
Local NOLOGIN-role tests verify execution denial after revoke and success after
restoration. Grantor context and automatic ACL replay limitations are documented
in [compatibility notes](../docs/compatibility.md#routine-execute-privilege-round-trip).

The privilege batch's final regression passed 876 ordinary tests (one optional
external case skipped) and all 57 compatibility tests per local YSQL engine
(114 real-engine passes). There were no failures/errors; see the linked notes
for grantor-context, visibility and automatic ACL replay limits.


Routine difference generation now supports volatility, null-input behavior,
security and comment changes without replacing functions. YSQL 15 also supports
procedure security changes; YSQL 11 rejects those before SQL generation while
supporting procedure comments. Local tests verify identity, ACL, body, settings,
custom COST and dependent-view preservation through changes and reverse changes.
Body/signature changes require an explicit migration. See the
[routine attribute compatibility notes](../docs/compatibility.md#postgresql-and-ysql-routine-attribute-differences).
