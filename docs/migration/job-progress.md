# Migration job progress and control

[Documentation index](../README.md) · [Migration workflows](README.md)

## Progress, retries, pause, and cancellation

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

### Bounded retries

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

### Pause and cancellation

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

### Combining listeners and live report updates

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
