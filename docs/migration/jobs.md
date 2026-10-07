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

`BulkMigrationJobPlanner.plan(tasks)` performs the same validation and returns
the immutable final task order without requiring a database connection. This
is suitable for command or Gradle-task dry runs and approval displays. The
executor uses this planner internally, so a displayed plan and the subsequent
execution apply identical ordering and safety checks.

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

For long-running migration jobs, the `BulkMigrationJobExecutor.execute`
overload accepting a `BulkMigrationJobListener` reports synchronous task
start, completion, and SQL failure events with the dependency-order index and
total task count. This can feed application logging or metrics without adding
such dependencies to sqlapp-core. Listener runtime failures abort execution;
an exception thrown while reporting an SQL failure is attached to the original
SQL exception as a suppressed exception.

`ChunkedBulkMigrationExecutor.executeWithListener` and the five-argument
`execute` overload accept a `ChunkedBulkMigrationListener`. It reports each chunk before writing, after its
checkpoint is durable, and when the write or checkpoint operation fails. The
progress value contains the migration ID, zero-based durable chunk index,
chunk row count, and processed-row counters before and after the chunk. A job
task can set the same listener through `BulkMigrationJobTask.chunkListener`.
No chunk-completed event is emitted until the checkpoint save (and database
checkpoint transaction commit) succeeds. Listener runtime failures abort the
current invocation; completion-callback failure is safe to resume because that
chunk is already checkpointed.

`BulkMigrationProgressTracker` is a chunk listener that derives elapsed time,
rows per second, completion ratio, and estimated remaining duration only from
durable chunk-completed events. It subtracts the initial checkpoint count when
calculating the current invocation's rate, so resumed rows do not inflate
throughput. When a reliable total row count was not supplied, ratio and ETA
remain null instead of presenting a misleading estimate.

Use `CompositeChunkedBulkMigrationListener` when progress reporting, logging,
and cooperative pause logic are needed together. Events are dispatched in
registration order. Every listener receives `pauseAfterChunk` even when an
earlier listener already requested a pause, and the combined result pauses
when any listener returns true.

`BulkMigrationRetryOption` enables bounded exponential-backoff retries for a
chunk. A failure is retryable only when it is an `SQLTransientException` or its
SQLState/vendor error code was explicitly configured. Retry settings are part
of the job plan fingerprint, and `onChunkRetry` reports the retry number and
backoff. Automatic retries require DATABASE checkpoint mode with the data and
checkpoint participating in the same target transaction: the failed attempt
is rolled back before the same rows are retried. FILE checkpoint mode is
rejected when retries are enabled because a partially successful INSERT cannot
be replayed safely in general.

After all rows are durable, the executor persists a final checkpoint with
`complete=true`. A retryable failure of that final DATABASE-checkpoint
transaction is retried with the same bounded backoff without replaying any
data rows. `onCompletionCheckpointRetry` reports this separately from a chunk
retry. Nontransactional FILE checkpoint writes are still never retried
automatically.

For cooperative shutdown, a chunk listener can return `true` from
`pauseAfterChunk`. The executor then throws
`ChunkedBulkMigrationPausedException` only after the chunk-completed event and
durable checkpoint. Its progress identifies the exact resume boundary. Inside
a multi-table job this becomes `BulkMigrationJobPausedException`, containing
the paused task, previously completed task results, and chunk progress; the job
listener also receives `onTaskPaused`. Rerun with the same migration IDs and
checkpoint stores to continue. A pause is not reported as a chunk or SQL
failure.

For operational cancellation, `BulkMigrationCancellationToken` is a
thread-safe signal and `BulkMigrationCancellationListener` converts it into
the existing cooperative pause. Cancellation never interrupts an in-flight
SQL statement or rolls back an already durable chunk: it is acknowledged at
the next completed checkpoint and execution exits through the normal paused
exception, so the same plan can resume. The first cancellation reason wins;
later requests cannot overwrite the operator-visible reason. Combine this
listener with progress or logging listeners using
`CompositeChunkedBulkMigrationListener`.

Use `CompositeBulkMigrationJobListener` to combine job logging, metrics, and
`BulkMigrationOperationalReportJobListener`. It dispatches every job-level and
task-level event in registration order and exposes an immutable listener list.
As with the synchronous listener contract, a listener exception stops dispatch
to later listeners for that event; use the report listener's `CONTINUE_JOB`
policy when report availability must not block other job observers.
The operational report listener validates every lease and job boundary against
its immutable plan before changing report state. It also checks task IDs,
indices, task counts, and completed job results, preventing callbacks wired to
another plan from being published under the current job identity.

The four-argument `BulkMigrationJobExecutor.executePlan` overload accepts a
common chunk listener in addition to the job listener. It combines that
listener with each task-specific listener without changing the validated plan;
task-specific callbacks run first. Use
`BulkMigrationOperationalReportChunkListener` as the common listener to
atomically refresh JSON after every checkpoint-durable chunk, rather than only
at task boundaries. It validates the chunk migration ID against the report's
immutable plan before refreshing, so a foreign chunk notification cannot
overwrite an otherwise valid report. `BulkMigrationJobProgressTracker` can be used as another
common listener: configure total rows by migration ID, then place it before the
report chunk listener in a `CompositeChunkedBulkMigrationListener` and supply
`getLatest` to the report job listener. It maintains an independent elapsed
time, resume baseline, rate, and ETA for every migration, exposes immutable
per-migration snapshots, and rejects events for IDs outside the configured
job. A single `BulkMigrationProgressTracker` remains appropriate only for one
table because its baseline and total are intentionally migration-specific.
Prefer the `BulkMigrationJobProgressTracker` constructor that accepts the
validated job plan. It reorders totals to plan order and rejects missing or
extra migration IDs before any chunk is executed; a null total remains the
explicit representation for an unknown row count.
Supply both `BulkMigrationJobProgressTracker.getLatest` and `getSnapshots` to
the report job listener to populate `progress` with the currently active
migration and `progressByMigration` with every migration seen so far. The
latter is emitted in validated plan order; mismatched map keys or migration IDs
outside the plan are rejected.

`BulkMigrationOperationalReportBuilder` combines the immutable dry-run plan,
read-only checkpoint status, optional durable maintenance state, and optional
progress/ETA snapshot into one versioned operational report. The report keeps
the plan fingerprint, ordered task and lifecycle-operation descriptions,
table/mode/chunk settings, checkpoint fingerprints and resume position so an
operator can identify both the approved plan and its durable progress. It
rejects status, checkpoints, maintenance state, or progress belonging to a
different plan or migration ID.

`BulkMigrationOperationalReportIO` writes that snapshot as indented UTF-8 JSON
using atomic replacement when supported by the file system. The report is
read-only: creating it does not execute SQL, alter checkpoints, or recover
maintenance. Its `read` operation provides the matching typed reader and
strictly rejects missing files, unknown `formatVersion` values, absent required
identity/list fields, duplicate task or migration identities, nested checkpoint
or progress identities outside their task plan, and inconsistent aggregate
task counts. Task state must also agree with durable checkpoint evidence:
`NOT_STARTED` has no checkpoint, `IN_PROGRESS` has an incomplete checkpoint,
and `COMPLETE` has a complete checkpoint. This prevents a hand-edited or
partially written report from claiming completion without durable resume state.
The same invariant is enforced by the shared `BulkMigrationJobTaskStatus`
model, so programmatic status producers cannot bypass it before report creation.
The typed report model exposes task mode, checkpoint mode, task state,
operation phase, maintenance status, and execution event as enums rather than
open-ended strings. JSON continues to use their stable names such as
`IN_PROGRESS`, `PREPARED`, and `TASK_COMPLETED`.
Execution row counts are accepted only for completion and pause events; start,
failure, and rejection events cannot carry ambiguous row-count evidence.
This makes a
successfully read report safe to use for monitoring and resume decisions;
invalid or newer reports must be handled explicitly rather than interpreted
partially. The overload accepting an expected plan fingerprint additionally
rejects a valid report produced for a different migration plan.
Execution events are also checked against durable task state:
`JOB_COMPLETED` requires every task to be complete and matching aggregate row
counts, while `TASK_COMPLETED` requires the named task and its checkpoint row
count to agree with the event. A terminal label cannot therefore turn an
incomplete status snapshot into apparent success. Execution and maintenance
timestamps may not postdate report generation, and failed events require a
nonblank exception type; malformed future or anonymous failure evidence is
rejected.
Maintenance failure text follows the durable state contract: it is required
for `RESTORE_FAILED` and forbidden for every other maintenance status. Reported
progress may never run ahead of its durable checkpoint, and the current
progress entry must agree with the same migration entry in
`progressByMigration`. Pause-event row counts must likewise match the paused
task's checkpoint. These checks keep monitoring hints from being mistaken for
durable resume evidence.

Offline evidence generation and revalidation can independently bound migration
reports, approval artifacts, and target-validation reports with
`maxEvidenceFileSizeBytes`, `maxApprovalArtifactFileSizeBytes`, and
`maxTargetValidationReportFileSizeBytes`. These settings are optional. Each
accepted JSON report is parsed and fingerprinted from the same bytes, and a
generated evidence report is read back and compared with the requested model
before its SHA-256 fingerprint is returned.
Completion and pause events always include their processed-row boundary.
Paused events must refer to an `IN_PROGRESS` task, while task-scoped failures
cannot refer to a `COMPLETE` or `INCOMPATIBLE` task. A task-start event also
cannot bypass an incompatible checkpoint.
The shared progress snapshot also validates derived metrics: completion ratio
requires a total and must equal processed rows divided by that total; ETA
requires both a total and a positive measured rate. Unknown totals therefore
leave both ratio and ETA unknown instead of publishing contradictory estimates.
`assessResume` converts a validated report into a conservative operational
decision: `COMPLETE`, `RESUMABLE`, `POSSIBLY_RUNNING`, `RECOVERY_REQUIRED`, or
`INCOMPATIBLE`. Started or mid-task reports are deliberately not declared safe
to resume because the JSON file alone cannot prove that another worker has
stopped. An unfinished durable maintenance phase takes precedence and requires
recovery before ordinary resume.

For execution fencing, `BulkMigrationJobLeaseStore` defines an atomic,
owner-aware lease contract shared by database and file-backed implementations.
`BulkMigrationJobLeaseManager` acquires a lease for the immutable plan
fingerprint, assigns a unique acquisition token, renews that exact acquisition,
and releases it idempotently.
Only an absent or expired lease may be acquired; an expired or replaced lease
cannot be renewed or released by its former process, even when a replacement
process intentionally reuses the same configured owner ID. The process-local
`InMemoryBulkMigrationJobLeaseStore` is intended for tests and single-process
execution, not coordination between application instances.
Custom `BulkMigrationJobLeaseStore` implementations must override
`release(BulkMigrationJobLease)` with an atomic acquisition-token comparison.
The compatibility default fails closed with `SQLFeatureNotSupportedException`;
it never emulates fencing with a racy load followed by owner-only release.
The lease-aware `BulkMigrationJobExecutor.executePlan` overload acquires the
plan lease before publishing `JOB_STARTED`, notifies job listeners through
`onLeaseAcquired`, renews it before and after every
chunk, and owner-conditionally releases it after success, failure, or pause.
Operational-report events then retain the unique acquisition ID, so two runs
using the same configured owner remain distinguishable in audit data. A lease
acquisition failure first invokes `onLeaseAcquisitionFailed` and is then
published as `JOB_REJECTED` without an acquisition ID;
it is not silently omitted from the operational report. The report listener
clears attempt-local lease state after every terminal job event, so reusing the
listener for a later unleased or rejected attempt cannot copy an earlier
acquisition ID into that attempt. `JOB_REJECTED` is defined as a pre-execution
event and the report model rejects JSON that associates it with an acquisition
ID, even if a custom listener sequence observed a lease callback first.
Failure to renew stops the job with `BulkMigrationJobLeaseLostException`; a
durably completed chunk is never replayed merely because its post-chunk renewal
failed. Configure the lease duration above the maximum expected duration of a
single chunk because renewal occurs at chunk boundaries rather than on a
background thread.
`JdbcBulkMigrationJobLeaseStore` is the multi-process default building block.
An existing JDBC lease table must have `JOB_ID` as its sole primary-key
column. Both stores reject missing or composite primary keys, which would allow
multiple owners to be stored for the same plan. Automatically created tables
already use this structure. Its required columns are `JOB_ID`,
`PLAN_FINGERPRINT`, `OWNER_ID`, `ACQUISITION_ID`, and `EXPIRES_AT`.
Lease metadata lookup rejects ambiguous table matches, including names that
differ only in case. Ensure that the connection's catalog and schema identify
a single lease table; metadata from multiple tables is never combined.
Drivers returning an empty current schema (including Vertica JDBC) are treated
as not supplying a schema. The lookup still rejects multiple matching tables.
The same fallback applies when `Connection.getSchema()` is unsupported (for
example, jTDS). Other SQL errors are propagated rather than treated as missing
schema information.
Both JDBC lease stores reject malformed persisted owner IDs and expiry timestamps
with a `SQLException` identifying the plan and relevant columns. Invalid lease
data is never treated as an absent or expired lease; correct the control data
before attempting resume.
It creates its control table through the dialect's Schema SQL factory and runs
each read-modify-write operation at `TRANSACTION_SERIALIZABLE`, without
handwritten vendor DML. Give it a dedicated auto-commit connection: the store
temporarily owns that connection's transaction and isolation settings and must
not share the data-writing connection used by the chunk executor.
`FileBulkMigrationJobLeaseStore` is the switchable filesystem alternative. It
uses a stable per-plan lock file for OS-level cross-process exclusion, an
in-process lock to avoid overlapping Java file locks, and atomic replacement of
the separate lease state file. Lock files intentionally remain after release;
the owner-conditioned lease state file is removed, while the stable lock path
continues coordinating later executions.
`BulkMigrationJobLeaseConfiguration.database(ownerId)` is the default built-in
configuration and uses a five-minute lease in
`sqlapp_bulk_job_lease`. `file(ownerId, directory)` explicitly selects the file
store. `BulkMigrationJobLeaseManagerFactory` validates the mutually exclusive
table/directory settings and creates the corresponding manager; FILE mode does
not require a JDBC connection. Applications needing a custom store continue to
construct `BulkMigrationJobLeaseManager` directly.
The lease-aware `assessResume` overload reads the current lease from either
store and resolves a stale started report against a caller-supplied current
time. A matching unexpired lease remains `POSSIBLY_RUNNING`; no lease or an
expired lease becomes `RESUMABLE`. Incompatibility and unfinished maintenance
still take precedence. The overload rejects a lease belonging to another plan,
including a matching job ID with a different plan fingerprint, so a state-file
mix-up cannot authorize resume.
All task checkpoints being complete is not sufficient when the latest event is
task-level: lifecycle post-processing may still be pending. With execution
history present, only `JOB_COMPLETED` confirms `COMPLETE`; an expired lease
after `TASK_COMPLETED` is therefore `RESUMABLE`, allowing lifecycle completion
without replaying durable chunks.
Lease-aware job execution also starts a daemon heartbeat at one third of the
configured lease duration. This keeps the lease alive while a single database
chunk or lifecycle operation runs longer than a chunk boundary. Background
renewal failure is retained and raised as `BulkMigrationJobLeaseLostException`
at the next listener boundary; it is never silently converted into a successful
renewal. Custom heartbeat intervals must be positive and shorter than the lease
duration.
Heartbeat health is also checked before job/task start and completion callbacks.
In particular, lifecycle post-processing cannot publish `JOB_COMPLETED` after a
background lease failure. Failure and pause callbacks deliberately bypass this
forward-progress check so the original job outcome still reaches operational
listeners and any lease error can remain secondary context.
Heartbeat renewal and health inspection are serialized with each other. A job
completion check therefore waits for an already-running renewal attempt to
finish and cannot race past the small window between a store rejecting renewal
and the heartbeat recording that rejection.
`GenerateBulkMigrationOperationalReportCommand` exposes the same operation to
command integrations. The Gradle plugin registers
`generateBulkMigrationOperationalReport` for builds that assemble the plan and
matching status programmatically. The generator optionally accepts
`maxOperationalReportFileSizeBytes`. Its result is atomically written and read
back from one bounded snapshot before the accepted report and SHA-256
fingerprint are exposed. Unified repair outcome publication uses the same
write, reread, model-comparison, and fingerprint sequence under
`maxEvidenceFileSizeBytes`.

The high-level `BulkMigration` facade uses the same write-and-reread checks for
explicit dry-run, verification, and repair-plan JSON. After those operations,
`getOperationalReportFingerprint()`, `getVerificationReportFingerprint()`, and
`getRepairPlanReportFingerprint()` expose the SHA-256 of the accepted file.
The `Repair` handle also exposes its generated approval fingerprint through
`getReportFingerprint()`.
`ExecuteBulkMigrationJobCommand` is the corresponding mutating command. It
executes a validated plan against a configured target data source and can
optionally fence execution with the existing database or file lease
configuration. Database leases use a dedicated second connection. The Gradle
plugin registers `executeBulkMigrationJob` as a synchronous, non-cacheable
task. It accepts either a programmatic plan or a YAML configuration plus a
separate source data source. Declarative jobs resolve table identities against
a captured Schema XML, reject ambiguous short names, and create JDBC keyset
sources only after the source connection has been opened. UPSERT keys, update
columns, duplicate strategy, checkpoint policy, and reproducibility
fingerprints are part of that configuration. Vendor-neutral bulk-copy controls
and bounded chunk retry rules are represented by nested `bulk` and `retry`
blocks and participate in the plan fingerprint. `CUSTOM` duplicate selectors
stay programmatic because they contain executable code. Execution remains
separate from the read-only report task.
Set the command or Gradle task's `executionReportFile` to persist the committed
result immediately after migration and before optional verification. The JSON
contains total and per-table processed rows, previous checkpoint rows,
completed chunks, already-complete flags, dependency order, plan identity, and
artifact provenance. `maxExecutionReportFileSizeBytes` adds an explicit output
bound. A previous file is removed before each attempt, preventing an earlier
success from surviving a failed rerun. If the database work completes but this
file cannot be published, `BulkMigrationExecutionReportException` retains the
committed `BulkMigrationJobResult`.
The command and Gradle task also accept `executionFailureReportFile`. A failed
or paused multi-table run records the stopped Access task ID, bounded exception
type/message, and the completed parent-before-child task prefix. Failure-report
write errors are suppressed onto the original execution failure. Success and
failure outputs are separate and stale copies are cleared only after command
configuration has passed validation and a new execution attempt is ready.
An optional top-level `lease` block selects `DATABASE` or `FILE` fencing. Its
owner, duration, and database table or file directory are resolved before the
target migration begins; relative file directories use the job-file directory
as their base. Supplying both YAML and programmatic lease settings is rejected.
Each task may independently use a DATABASE checkpoint table or a FILE
`checkpointDirectory`. Relative checkpoint directories are resolved from the
job YAML location. FILE mode retains at-least-once replay semantics and cannot
enable transactional chunk retries; CUSTOM stores remain programmatic.
An optional `report` block attaches the existing operational-report listener
to the declarative execution. The report file is updated atomically at job and
task boundaries. Its failure policy is either strict `FAIL_JOB` or best-effort
`CONTINUE_JOB`.
An optional `verification` block performs a second ordered JDBC pass over the
source and target after execution. It compares row counts and normalized chunk
hashes without materializing the full tables. Verification uses the migration's
unique keyset columns. A mismatch may either fail the command or be returned for
external policy handling; already committed chunks are not rolled back.
`verification.targetFile` optionally writes a stable JSON summary atomically.
The summary includes totals and mismatched chunk hashes rather than every
matching row or chunk, keeping the artifact bounded. It is written before a
configured mismatch failure is raised.
`verification.repairPlanOnMismatchFile` optionally writes the corresponding
review-only multi-table repair plan at the same point. It carries target table
and column mappings while retaining Access/source task names and foreign-key
order. Generating this file does not apply data changes; repair still requires
explicit approval of the generated plan fingerprint. Both the verification
report and mismatch repair plan are atomically written, read back, and compared
with their requested models. `ExecuteBulkMigrationJobCommand` exposes the
SHA-256 fingerprints of the accepted files.
Programmatic job-level repair-plan generation can optionally set
`maxRepairPlanReportFileSizeBytes`. The generated approval JSON is atomically
written and read back from one bounded snapshot; its parsed model and SHA-256
therefore refer to exactly the same bytes. Standalone table repair-plan JSON
also supports bounded snapshot reads through `BulkMigrationRepairPlanReportIO`.
The declarative repair command re-runs the same verification both before and
after repair. Its optional post-repair verification file provides bounded JSON
evidence that the approved replay actually restored equality.
An optional repair execution report separately records the approved repair-plan
file fingerprint, per-task replay counts, affected rows, unresolved extra or
missing chunks, and the approval provenance used for execution.
When repair execution fails, an optional repair failure report records the
failure phase and task, exception type and bounded message, the successfully
completed task prefix, the approved repair-plan file fingerprint, and the same
approval provenance. Success and failure use separate files, so an earlier
successful report cannot be mistaken for the outcome of a later failed run.
Immediately before an approved replay starts, the command removes configured
success, failure, post-repair verification, and unified outcome outputs from an earlier run.
Those output paths must be distinct from one another and from all approval
inputs. If failure-evidence writing itself fails, that secondary error is
attached to the original repair failure instead of replacing it.
Optional `repairOutcomeReportFile` publishes the same bounded unified outcome
as the file-only outcome verifier directly from the repair command. It requires
execution, failure, and post-repair verification report paths so every status
can reference its exact authoritative SHA-256 evidence. This is the concise
execution path; the separate verifier remains available for later independent
audit.
For the common path, set only `repairReportDirectory`. The command derives
`repair-execution.json`, `repair-failure.json`,
`post-repair-verification.json`, and `repair-outcome.json` there. Advanced
callers may override any individual path while retaining defaults for the
others.
Keep the reviewed repair plan outside this mutable report directory. Set
`expectedApprovedRepairPlanFileFingerprint` to its lowercase `sha256:` value
when approval is transferred between people or CI stages. Repair execution
checks it before opening either migration connection, and the file-only repair
evidence verifiers apply the same gate.
Optional `maxApprovedRepairPlanAgeSeconds` limits how long that reviewed plan
may be reused. Future plan timestamps are rejected even when no age limit is
configured.
Optional `maxApprovedRepairPlanFileSizeBytes` applies an operator-selected
byte limit before the approval JSON is parsed. It is unset by default and is
shared by repair execution and all file-only repair evidence verifiers.
Optional `maxEvidenceFileSizeBytes` bounds the execution, failure,
post-repair verification, and unified outcome JSON inputs. Reads stop as soon
as the configured limit is crossed, including when a file grows after its
initial size check.
Repair execution captures the approved-plan SHA before opening the migration
connections and uses that same value in execution, failure, and outcome
evidence. The final outcome publication rechecks the file against the captured
SHA, so a plan replaced while repair is running cannot produce a verified
outcome.
The approved file is read once as bytes; its SHA and parsed model therefore
refer to exactly the same content. That validated model is retained for the
live repair-plan comparison. Offline verification also rejects execution or
failure evidence dated before the approved plan.
Repair execution, failure, post-repair verification, and saved outcome files
are also fingerprinted and parsed from one byte snapshot during offline
verification. Outcome publication reuses those accepted fingerprints.
Generated repair evidence is atomically written, read back, compared with its
source model, and fingerprinted before it can be referenced by failure or
outcome evidence.
Declarative execution applies the same rule to the job YAML and live-target
validation report: parsing and SHA-256 calculation use one byte snapshot. The
accepted target-report SHA is retained for execution and repair provenance
instead of fingerprinting the path again later.
Optional `maxConfigurationFileSizeBytes` and
`maxSchemaFileSizeBytes` and `maxTargetValidationReportFileSizeBytes` apply
bounded reads to these approval inputs. They are unset by default and are
shared by declarative migration and repair execution. Schema XML fingerprint
verification and model parsing use the same byte snapshot.
Optional `maxApprovalArtifactFileSizeBytes` bounds SHA-256 reads of the
assessment and DDL-verification reports without loading either artifact into
memory. The same setting is available for target validation, migration, and
repair execution.
The unified outcome selector and saved-outcome verifier accept the same
directory property, so later CI stages need only the approved plan plus the
repair report directory. All three commands share one filename definition.
If replay completes but post-repair verification still differs, the failure
phase is `POST_VERIFICATION`. The evidence records all completed repair tasks,
the first mismatched task and, when configured, the SHA-256 of the bounded
post-repair verification report. This is repair-failure report format version 2.
Readers apply the same per-task count and chunk-list validation used for
successful execution evidence. They reject negative or contradictory counts,
duplicate task or chunk identifiers, overlapping extra/missing classifications,
and preflight failures that claim completed tasks.
The file-only repair-failure evidence verifier binds that failure report back
to the approved plan. It checks that completed tasks are an exact prefix of the
approved dependency order and that an execution failure identifies the next
task, in addition to optional artifact fingerprints, provenance, and age.
For `POST_VERIFICATION`, supplying `postRepairVerificationReportFile` also
checks its SHA-256, mismatch state, task order, plan fingerprint, provenance,
and generation time.
An optional expected post-repair verification SHA-256 can be supplied by CI or
an approval system, adding an external pin to the fingerprint already embedded
in the failure report.
A single outcome verifier is available for the ordinary audit path. Given the
approved plan and the conventional success/failure output locations, it chooses
failure evidence when present and otherwise verifies successful execution plus
post-repair comparison. The result is `SUCCEEDED`, `EXECUTION_FAILED`, or
`VERIFICATION_FAILED`; the underlying strict validators retain the same checks.
An optional outcome file atomically records that result with the migration and
repair identities, exact SHA-256 fingerprints of the evidence used, the
optional failure phase and task, and common provenance. It provides a bounded
CI artifact without copying row or exception details from the authoritative
execution, failure, and verification reports. It is published only after all
identity, freshness, provenance, ordering, and conflict checks pass and cannot
overwrite an input report.
An existing outcome at that dedicated path is removed before validation, so a
rejected evidence set cannot be confused with an earlier verified decision.
For a deployment gate, optional `expectedStatus` can require `SUCCEEDED`.
Verified failure evidence is still written to the outcome file before the gate
fails, preserving the actual result for diagnosis. With no expected status,
all three verified states remain successful audit results.
A separate saved-outcome verifier supports later audit and independent CI
stages. It can externally pin the outcome file SHA-256 and expected status,
re-runs the strict source-evidence verifier, compares every identity, source
fingerprint, failure reference and provenance field, and checks that the
outcome timestamp follows its evidence. Optional maximum age rejects stale
outcomes; future timestamps are always rejected.
For an execution failure it rejects leftover success artifacts instead of
silently ignoring them. For a post-verification failure, an available execution
report is joined by plan and approval fingerprints, complete task results,
provenance, and timestamps; an externally pinned execution SHA remains enforced.
It also accepts the same reviewed assessment, DDL-verification and live-target
validation gates as declarative migration execution, including target report
age and environment identity checks.
Each task summary also records the ordered column names used to calculate its
hashes, so the artifact remains meaningful when `verificationColumns` narrows
the comparison. The top-level `isolation` field records the JDBC consistency
level used for the run. Mismatched chunks produced from JDBC keyset sources
also include source and target first/last keyset tokens, allowing an operator
to narrow a follow-up query without rescanning from row zero. This is
verification-report format version 5. Each JDBC task also records the source
and target keyset-configuration fingerprints so later repair code can reject
tokens created by a different key order, codec, or fetch configuration.
`maxReportedMismatches` defaults to
1,000 details per task while `mismatchedChunks` retains the uncapped total, so
an extensively different table cannot make the JSON artifact unbounded.
Readers reject older artifacts that do
not identify these semantics. Keyset tokens can contain business-key values,
so protect the report as migration data rather than as an unrestricted log.
When an operational report is configured, a verification query, report-write,
or configured mismatch failure replaces its final execution event with
`JOB_FAILED`. This describes the command outcome; committed migration chunks
remain committed.
Verification isolation defaults to the connections' current settings. The
declarative `isolation` option accepts `READ_COMMITTED`, `REPEATABLE_READ`, or
`SERIALIZABLE`. For the stronger levels, verification transactions are opened
on both source and target after migration, then rolled back and their original
JDBC settings restored. This stabilizes each side independently but cannot
create one distributed, same-instant snapshot
across two databases. Quiesce writers or use database-native coordinated
snapshots when that guarantee is required.
`BulkMigrationVerificationReportIO` reads the artifact back while validating
its format version, plan fingerprint, unique task IDs, non-negative counts,
match flags, mismatched chunks, and aggregate totals. Use the fingerprint-aware
read overload before making an automated deployment decision.
Verification does not blindly compare every modeled column. INSERT defaults to
the writable inserted columns; UPSERT defaults to the resolved staging columns.
This avoids false mismatches from hidden, formula, or target-generated identity
values. A task's `verificationColumns` can explicitly narrow the comparison,
and unknown or duplicate names are rejected.

For a running multi-table job, pass a
`BulkMigrationOperationalReportJobListener` to
`BulkMigrationJobExecutor.executePlan`. It reloads the explicit checkpoint
stores and atomically refreshes the same report at task start, completion,
failure, and cooperative pause boundaries. Optional suppliers can attach the
latest durable maintenance state and `BulkMigrationProgressTracker` snapshot.
The listener follows the normal synchronous listener contract: a report read
or write failure is not hidden. `FAIL_JOB` is the default and propagates the
failure. `CONTINUE_JOB` preserves migration execution while delivering the
failure to the configured consumer and `getLastFailure()`; a later successful
publication clears that value. In strict mode, failures raised while reporting
an existing task failure or pause are retained by the executor as suppressed
listener failures.

The report's `execution` object distinguishes job-level `JOB_REJECTED`,
`JOB_STARTED`, `JOB_COMPLETED`, `JOB_FAILED`, and `JOB_PAUSED` as well as the corresponding
task events, even when their durable checkpoint state is otherwise identical.
It contains the applicable task ID, event time, known processed-row count, and
bounded failure details. Event task IDs are validated against the plan before
JSON is written. `JOB_COMPLETED` is emitted only after lifecycle post-processing
succeeds. Job completion-listener failure is propagated after completion and
does not invoke lifecycle restoration; failure and pause notifications occur
after restoration has been attempted.

