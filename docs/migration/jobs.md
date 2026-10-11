# Multi-table migration jobs

[Documentation index](../README.md) · [Migration workflows](README.md)

## Multi-table migration jobs

`BulkMigrationJobExecutor` groups table and keyset migrations into one ordered
job result. Tasks are sorted with `TableOrder.CREATE`, the same Schema foreign
key dependency sorter used by `GenerateDataInsertCommand`. Real and virtual
foreign keys already attached to the Schema model therefore determine the
parent-before-child order; job configuration does not duplicate dependency
IDs.

```java
var customerTask = BulkMigrationJobTask.builder()
		.taskId("customers")
		.keysetSource(customerSource)
		.options(customerOptions) // unique migrationId
		.build();
var orderTask = BulkMigrationJobTask.builder()
		.taskId("orders")
		.keysetSource(orderSource)
		.options(orderOptions) // unique migrationId
		.build();
var job = BulkMigrationJobExecutor.execute(targetConnection,
		List.of(orderTask, customerTask));
```

Every task must have a unique task ID and checkpoint migration ID. Each task
retains the checkpoint mode and transaction guarantees of
`ChunkedBulkMigrationExecutor`. The job itself is not one database transaction:
if a later child task fails, earlier completed tasks remain committed. Rerunning
the same job skips those completed task checkpoints and resumes the unfinished
table. A failure is reported as `BulkMigrationJobException`; its failed task ID
and completed job result allow callers to record precise job progress before
retrying. The original SQL exception is retained as the cause. Tables without
modeled foreign keys use the shared sorter's deterministic name ordering.

Because a job does not provide one transaction spanning all tables, a cycle of
table-to-table foreign keys cannot be made safe merely by choosing an order.
The executor rejects cyclic and cycle-dependent task graphs before writing any
rows. Self-referencing foreign keys are allowed because they do not create an
ordering dependency between job tasks; row ordering and constraint semantics
within that table remain the caller's responsibility.

### Planning without a database

`BulkMigrationJobPlanner.plan(tasks)` performs the same validation and returns
the immutable final task order without requiring a database connection. This
is suitable for command or Gradle-task dry runs and approval displays. The
executor uses this planner internally, so a displayed plan and the subsequent
execution apply identical ordering and safety checks.

### Lifecycle and durable maintenance

`BulkMigrationJobPlanner.plan(tasks, lifecycle)` also includes migration
maintenance in the dry-run. A lifecycle describes its constraint, trigger,
identity/sequence, statistics, or other operations as immutable
`BulkMigrationJobOperation` values. Those operations and the lifecycle
configuration fingerprint are included in the plan fingerprint. Independent
maintenance implementations can be combined with
`CompositeBulkMigrationJobLifecycle`; operation IDs must be unique.

The executor calls lifecycle preparation before the first table and successful
post-processing after the last table. If preparation or migration fails, it
calls restoration and preserves a restoration failure as a suppressed
exception on the original failure. A composite restores components in reverse
order and continues restoring remaining components after one restore fails.
Restore implementations must be idempotent because the executor may invoke
them after a partially completed preparation. Process termination cannot be
recovered by an in-memory lifecycle alone; durable maintenance-state recording
is required before automatically recovering such an interrupted job.

`DurableBulkMigrationJobLifecycle` wraps a lifecycle with a
`BulkMigrationMaintenanceStateStore`. It records `PREPARING`, `PREPARED`,
`POST_PROCESSING`, `RESTORING`, `RESTORED`, `RESTORE_FAILED`, and `COMPLETE`
against the plan fingerprint. `RESTORE_FAILED` also retains the failure
message. The core module supplies an in-memory store for tests and embedded
use. `JdbcBulkMigrationMaintenanceStateStore` creates and updates its control
table through the Schema model and dialect SQL factories. It participates in
the transaction of its supplied connection; use a dedicated autocommit
connection when maintenance evidence must survive a rollback of the data
connection. The command module supplies
`FileBulkMigrationMaintenanceStateStore`, which uses atomic replacement when
the filesystem supports it and validates every persisted field. A nonterminal
state is evidence of an interrupted job, but automatic recovery should run
only after the same plan fingerprint has been verified.

Monitoring code can use
`JdbcBulkMigrationMaintenanceStateStore.readOnly(connection, tableName)` to
inspect JDBC state without creating or changing the control table. A missing
table produces no state. An ambiguous table name, missing required column,
primary key other than `PLAN_FINGERPRINT` alone, or malformed persisted state
is reported as an error rather than being repaired or treated as absent.

`DurableBulkMigrationJobLifecycle.inspect(plan)` is read-only.
`recoverInterrupted(connection, plan, expectedFingerprint)` requires the exact
approved fingerprint and restores only `PREPARING`, `PREPARED`,
`POST_PROCESSING`, `RESTORING`, or `RESTORE_FAILED`. Missing, `RESTORED`, and
`COMPLETE` state is a no-op. Recovery records the normal `RESTORING` and
`RESTORED` transitions and requires the plan to have been created with that
same durable lifecycle instance, preventing a different maintenance
configuration from being applied accidentally.

### Plan fingerprints and stable job identity

The plan also exposes a deterministic SHA-256 fingerprint over the ordered
task IDs, table identities, migration and schema fingerprints, migration mode,
chunk/checkpoint settings, and bulk/UPSERT options. Execution-only objects such
as listeners and checkpoint-store instances are intentionally excluded. The
value is for dry-run approval and reproducibility checks, not authentication.
List-valued column settings are encoded element by element, and source style
(row-count or keyset) is included. The implementation of a custom duplicate
row selector or keyset query cannot be serialized generically; callers should
represent changes to such source logic in `sourceFingerprint`.

The plan also has a stable job ID. By default it is derived from each
migration ID and fully qualified target table identity, independently of task
order and execution options. Therefore changing a chunk size or UPSERT option
changes the exact plan fingerprint without changing the logical job identity.
Use `.jobId("nightly-customers")` on the facade, or the planner overload taking
a job ID, when several separately operated jobs intentionally use the same
table and migration identifiers. Durable maintenance stores use the job ID as
their key and retain the exact plan fingerprint as state data. Consequently a
changed configuration cannot hide unfinished maintenance; the new plan reports
`RECOVERY_REQUIRED`, while recovery remains forbidden until the saved plan is
reconstructed and explicitly approved.

Operational report format version 2 includes the stable `jobId`. Its
`maintenance` object also includes the fingerprint of the plan that wrote the
state, which may intentionally differ from the report's current plan
fingerprint when configuration changed after an interruption.

### Executing an approved plan

An approved plan can be passed directly to
`BulkMigrationJobExecutor.executePlan(connection, plan)`. Immediately before any
database work, the executor recalculates the fingerprint and rejects a plan if
its mutable Schema tables or task options no longer match the state captured
at planning time. The list-based overload remains available and creates a plan
internally.

Every executor-created `BulkMigrationJobResult` includes the plan fingerprint.
The partial result carried by SQL-failure and pause exceptions includes the
same value, allowing successful, failed, and paused executions to be correlated
with the exact dry-run approval. The legacy result constructor remains
available and leaves the fingerprint null for caller-created results.

### Read-only status and checkpoint reset

`BulkMigrationJobStatusInspector.inspect(plan)` reads explicitly configured
checkpoint stores and classifies each task as `NOT_STARTED`, `IN_PROGRESS`,
`COMPLETE`, or `INCOMPATIBLE`. It also returns aggregate processed rows and the
plan fingerprint. Inspection intentionally rejects tasks relying on the
implicit database store: constructing that store may create or upgrade its
control table, which would violate the inspector's read-only contract. Supply
an already initialized JDBC store, file store, or another explicit store when
status-only access is required.

To deliberately restart a job from the beginning,
`BulkMigrationJobCheckpointManager.reset(plan, expectedFingerprint)` deletes
only the task checkpoints. It requires the exact plan fingerprint and an
explicit store on every task; all stores are validated before the first
deletion. A partial reset failure reports the failed task and checkpoints
already deleted. Migrated table rows are never removed. In `INSERT` mode,
rerunning after reset can therefore create duplicates or constraint failures;
clear or reconcile target data separately when a true restart is intended.

### Verification and multi-table repair

After migration, `BulkMigrationJobVerifier` applies the existing streaming
chunk verification to every expected/actual table pair and returns aggregate
row counts and the number of mismatched tasks. Verification tasks use the same
Schema FK dependency order as migration tasks. Each expected and actual row
stream must still use the same deterministic order within its table.

`BulkMigrationJobRepairExecutor` accepts the expected table and verification
result for each task, then delegates mismatched chunks to the existing UPSERT
repair executor in parent-before-child order. Its aggregate result reports
replayed chunks and rows, affected rows, and tasks that still require manual
reconciliation. The repair job is not atomic across tables. A SQL failure or
keyset source consistency failure is
reported as `BulkMigrationJobRepairException` with the failed task ID and all
completed repair results; follow-up verification remains required.

A repair job task accepts exactly one expected source: `expected` for a
materialized `Table`, or `expectedKeysetSource` for boundary-based JDBC
re-reading. Set the optional `target` when its catalog, schema, table name, or
column model differs from the source; otherwise the expected table identity is
used for backward compatibility. Dependency ordering and UPSERT generation use
the target Schema model. `options` is optional and defaults to
`BulkMigrationRepairOption.defaults()`.
All verification columns, UPSERT plans, and keyset fingerprints are preflighted
before the first task writes anything; an invalid later task therefore cannot
leave earlier tasks partially repaired.

## Progress, retries, pause, and cancellation

See [Migration job progress and control](job-progress.md).

## Report construction and evidence

See [Migration job reports](job-reports.md).

## Leases and execution fencing

See [Migration job leases and fencing](job-leases.md).

## Live job reports

See [Migration job reports](job-reports.md).
