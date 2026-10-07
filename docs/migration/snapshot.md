# Type 2 snapshot execution

[Documentation index](../README.md) · [Migration workflows](README.md)

## Type 2 snapshot execution

`MigrationSnapshotDefinition` models a slowly changing dimension (SCD2): its
business-key and tracked columns identify unchanged, changed, new, and missing
rows, while the configured validity and optional current-marker columns retain
history. `MigrationSnapshotPlanner` is the in-memory planner and
`MigrationSnapshotStreamingPlanner` compares two strictly key-ordered streams
with bounded memory. `JdbcBatchMigrationSnapshotExecutor` reuses prepared
statements and JDBC batches for the portable fallback.

For the common JDBC path, call `JdbcStreamingMigrationSnapshotExecutor.execute`.
It resolves the target Dialect automatically: a set-based provider reads only
the ordered source stream, while an unsupported target also streams its current
rows through the bounded-memory prepared-statement fallback. Advanced callers
can use `SetBasedMigrationSnapshotResolver.find` with the target connection or
Dialect directly. When present, the returned executor loads a
connection-local staging table with a reused prepared statement and batches,
then expires and inserts rows with set-based SQL in one target transaction.
`resolve` is the stricter variant and throws when no provider is available.
Staging rejects rows with missing columns or null business keys while they are
streamed. Before mutating history, two bounded-result set-based checks reject
duplicate source keys and duplicate or null keys among current target rows.
These checks retain bounded JVM memory; indexes beginning with the configured
business-key columns keep the grouped validation and subsequent joins fast.
The shared executors also reject a snapshot timestamp that is not later than
every current row's validity start and reject inconsistent current-marker
values. Marker consistency and current-key uniqueness are checked again after
the set-based or batched DML and before commit. These are scalar or
bounded-result SQL checks rather than per-row JDBC operations.
For declarative execution, use the Gradle `executeMigrationSnapshot` task or
`com.sqlapp.data.db.command.migration.snapshot.ExecuteMigrationSnapshotCommand`
with the same YAML configuration. Snapshot execution is atomic and
intentionally not split into resumable migration chunks, because per-chunk
missing-row expiry would be incorrect.
An optional `lease` block prevents concurrent processes from applying the same
snapshot ID and configuration fingerprint. `FILE` mode coordinates processes
sharing a filesystem directory; `DATABASE` mode uses a dedicated auto-commit
connection to the target and the configured lease table. A background
heartbeat renews the lease during source streaming and target DML. Every shared
executor checks the heartbeat after post-DML invariant validation and
immediately before commit. That fence performs a synchronous renewal rather
than relying only on the last background result, so an intervening JVM pause,
expiry, or ownership loss rolls back the complete
snapshot. Custom set-based executors that have not implemented this commit-time
guard reject guarded execution instead of silently weakening the fence.
Configuration resolution verifies from the Schema model that both validity
columns are date/time values and that an optional current-marker column is
boolean or numeric. This validation occurs before database connections are
opened. Source and target business-column types are not required to be
identical because heterogeneous migrations commonly use equivalent vendor
types; JDBC conversion remains the owning dialect/driver's responsibility.
Set optional `reportFile` in YAML to atomically write a versioned JSON success
artifact after both database execution scopes complete. The report records the
resolved tables, business and tracked columns, effective timestamp, selected
executor, transaction capability, configured fetch/batch sizes, resolved lease
mode/owner/duration/storage, and affected
row counts. It also contains a deterministic SHA-256 configuration fingerprint
covering the snapshot definition, effective timestamp, fetch/batch sizes and
resolved source/target table shapes. When a lease is configured, a successful
report also records the unique acquisition ID of the lease instance that
fenced that execution. The acquisition ID is runtime audit evidence: it is not
part of the YAML configuration fingerprint or approval comparison. Consumers can call
`MigrationSnapshotExecutionReportIO.read(reportFile, expectedFingerprint)` to
reject a success artifact produced from different configuration or Schema
metadata. To make this a declarative execution gate, set `approvalReportFile`
in YAML to a reviewed `MigrationSnapshotApprovalReport`. Create it from YAML
and Schema XML without database access using
`GenerateMigrationSnapshotApprovalReportCommand` or the
`generateMigrationSnapshotApprovalReport` Gradle task. Resolution requires its
fingerprint to match the current configuration and Schema metadata before
either database connection is opened. Every review-visible semantic field is
also compared with the resolved plan, preventing a retained fingerprint from
masking altered table names, columns, timestamps, or execution sizes. The
approval path is resolved relative to the YAML file, like `schemaFile` and
`reportFile`. Failed executions do not replace the report file.
Set optional `failureReportFile` to atomically write a separate, bounded JSON
failure artifact. It records the resolved configuration fingerprint, approval
evidence, lease settings, the acquisition ID when lease acquisition completed,
start/failure timestamps, exception type and message, and a failure
phase. `DATABASE_EXECUTION` means target execution failed and the executor
rolled its transaction back. `POST_COMMIT_FINALIZATION` means target execution
committed but later command finalization failed. `SUCCESS_REPORT_WRITE` means
database execution completed and committed, but publication of the success
artifact failed. Operators must not mistake either post-commit phase for a
database rollback. Failure-report publication errors are attached to the
original exception and never replace it. Success and failure artifacts never
overwrite one another. A lease-configured failure with no acquisition ID failed
before the command acquired its fence; a populated acquisition ID proves that
the reported execution owned that specific lease generation.
Set optional `approvalValidFor` to a positive ISO-8601 duration such as `PT24H`
to prevent indefinite replay. The policy is included in the configuration
fingerprint and both artifacts. Expired approvals and approvals dated in the
future are rejected before database access. Omitting it preserves intentional
long-lived approvals.
When approval is required, the success report also records the approval's
generation timestamp and a SHA-256 digest of the exact validated JSON bytes.
This binds the database result to the reviewed artifact for later audit.
The execution report separately records execution start and completion timestamps.
Validation enforces approval generation <= execution start <= completion and
checks approval expiry at the recorded start, so a long-running valid execution
does not become invalid merely because it finishes after the approval window.
The approval window is half-open: execution must start strictly before its
expiration timestamp; starting exactly at expiration is rejected.
Use `VerifyMigrationSnapshotReportCommand` or the
`verifyMigrationSnapshotReport` Gradle task to validate the saved pair without
database access. Supply the optional snapshot YAML to the verifier to also
require its current configuration fingerprint and resolved lease settings to
match the report. Even a whitespace-only change to the approval JSON is detected.
When the executor owns an auto-commit target connection, a staging, expiry, or
history-row insert failure rolls back the complete snapshot, restores the
connection's auto-commit state, and removes the connection-local staging
object. When the caller supplies an existing transaction, the executor joins
it and leaves commit or rollback ownership with that caller. It also avoids a
staging-object `DROP` that could implicitly commit the caller's work; the
connection-local object then remains isolated until the connection ends. The
command and Gradle task instead supply an auto-commit target connection and let
the selected executor own the complete target snapshot transaction, then close
that connection. This also keeps providers with transaction-breaking staging
DDL out of a caller-owned transaction.
The H2 provider creates its local stage with H2's `TRANSACTIONAL` modifier so
the stage declaration itself also preserves a caller-owned transaction.

`SetBasedMigrationSnapshotExecutor.supportsCallerTransactionAtomicity()`
exposes this capability to advanced callers. Oracle, SAP HANA, Vertica, and
SAP ASE return `false`; passing them a connection whose auto-commit is already
disabled fails before staging DDL is executed. Use an auto-commit connection
for these providers so the executor can establish and complete its own target
transaction. Other set-based providers and the prepared-statement fallback can
join a caller-owned transaction.

Set-based providers are available for H2, HSQLDB, PostgreSQL, DB2, MySQL and
MariaDB, SQLite, SQL Server, SAP HANA, Oracle 18c and later, Vertica, SAP ASE,
and Informix. The Oracle provider uses a private temporary table, so older
Oracle versions use the streaming fallback. Firebird, Derby, Cloud Spanner,
Phoenix, HiRDB, Symfoware, and Access intentionally use the fallback:

- Firebird global temporary table definitions are persistent catalog objects,
  not safely generated per execution.
- Derby cannot derive a declared global temporary table with `AS SELECT` or
  `LIKE`, and declared temporary tables support only a restricted type set.
- Cloud Spanner and Phoenix do not provide the connection-local temporary-table
  semantics required by the shared executor.
- HiRDB and Symfoware are outside this enhancement scope.
- Access/UCanAccess does not offer a suitable native set-based temporary-table
  path; its portable JDBC fallback remains the supported route.

Informix uses `WITH NO LOG` only for the disposable session temporary table;
the target history-table updates remain transaction controlled. The providers
have real-engine integration coverage with SAP HANA Express 2.0, DB2 Community
12.1.5, Oracle Database Free 23ai/26ai, Vertica CE 25.1, SAP ASE 16, and Informix
14.10. A future JDBC driver may still remove current generated-key or
temporary-table limitations without requiring a public API change.
