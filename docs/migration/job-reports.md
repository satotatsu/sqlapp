# Migration job reports

[Documentation index](../README.md) · [Migration workflows](README.md)

## Report construction and evidence

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


## Live job reports

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
