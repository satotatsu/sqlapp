# Migration job leases and fencing

[Documentation index](../README.md) · [Migration workflows](README.md)

## Leases and execution fencing

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
