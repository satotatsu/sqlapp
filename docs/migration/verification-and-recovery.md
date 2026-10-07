# Migration verification and recovery

[Documentation index](../README.md) · [Migration workflows](README.md)

## Migration verification

`BulkMigrationVerifier` compares two ordered Schema `Table` row streams. It
returns total expected and actual counts plus a SHA-256 digest for each chunk.
`BulkMigrationVerificationResult.getMismatches()` identifies the exact chunk
indexes that need detailed investigation or replay. Expected and actual tables
may use JDBC row iterator handlers, so verification keeps at most two chunks of
row data in memory. It retains one compact count/hash summary per chunk for
repair and diagnostics.

Expected and actual JDBC streams must be usable concurrently. Use separate
source and target connections; drivers such as Firebird JDBC permit only one
active streaming result set on a connection and otherwise close the first
stream when the second is opened.

Verification uses expected-table column order and resolves actual columns by
name. Both row streams must use the same deterministic ordering. Chunk hashes
are intended to detect migration differences; they are not authentication or
digital-signature values.

### Repairing mismatched chunks

`BulkMigrationRepairExecutor` replays only the expected-side rows from chunks
reported by `BulkMigrationVerificationResult.getMismatches()`. Repair uses
UPSERT, so changed target values and missing target rows can be corrected
without rewriting chunks whose hashes already match.

### Quick start

For the common Java workflow, `sqlapp-command` provides the `BulkMigration`
facade. It owns source/target connection lifetimes and hides job tasks, keyset
sources, dependency sorting, checkpoint stores, verification tasks, and repair
planner wiring:

```java
import com.sqlapp.data.db.command.migration.bulk.BulkMigration;

BulkMigration migration = BulkMigration.of(sourceDataSource, targetDataSource, schema);
BulkMigration.Execution execution = migration.run();
```

This is the recommended entry point. It performs UPSERT followed by
verification, fails on a mismatch, migrates all Schema tables in foreign-key
order, and returns both detailed migration and verification results. Start with
this form unless a requirement below makes an option necessary.

### Common options

Switch to the builder only when tables or execution behavior must be selected:

```java
BulkMigration migration = BulkMigration.builder()
        .source(sourceDataSource)
        .target(targetDataSource)
        .schema(schema)
        .tables("CUSTOMERS", "ORDERS")
        .mode(BulkMigrationMode.UPSERT)
        .build();

BulkMigration.Execution execution = migration.run();
BulkMigrationOperationalReport plan = migration.dryRun();
BulkMigrationJobStatus status = migration.status();
BulkMigrationJobVerificationResult verification = execution.verification();

// Or use planRepair(verification) when an existing result should be reused.
Path repairPlanFile = Path.of("build/reports/migration/repair.json");
BulkMigration.Repair repair = migration.verifyAndWriteRepairPlan(repairPlanFile);
if (!repair.isRequired()) {
    return;
}
BulkMigrationJobRepairResult repaired = repair.executeApproved(repairPlanFile);
```

UPSERT, 10,000-row chunks, migrated columns for verification, primary-key
keyset traversal, database checkpoints, and Schema foreign-key order are the
defaults. Omit `tables` to use every table in the supplied Schema. The facade
defaults `resume` to false because a Schema fingerprint is not proof that the
source data is unchanged. Enabling resume requires explicit source and target
fingerprints, for example `.resume(true).fingerprints(sourceVersion,
targetVersion)`. Advanced checkpoint, retry, listener, lease, lifecycle, custom
keyset, and per-table UPSERT configurations remain available through the
underlying APIs and declarative job configuration.

| Requirement | Add to the standard builder |
|---|---|
| Resume after an interruption | `resume(true)` and stable `fingerprints(...)` |
| Keep control state outside the target DB | `fileCheckpoints(directory)` |
| Prevent concurrent workers | `databaseLease(owner)` or `fileLease(owner, directory)` |
| Recover vendor preparation after a crash | `databaseMaintenance()` or `fileMaintenance(directory)` |
| Persist operational or verification evidence | `operationalReport(file)` or `verificationReport(file)` |
| Persist committed row/chunk results | `executionReport(file)` |
| Limit migration to selected tables | `tables(...)` |
| Override one table only | `tableOption(table, option)` |
| Rename a target table or columns | `tableOption(table, BulkMigrationTableOption.builder().targetTable(...).columnMappings(...).build())` |

For an Access model whose names differ from the target, keep source names in
the Schema and per-table column lists. The facade resolves target names for
execution, verification, and approved repair:

```java
BulkMigration migration = BulkMigration.builder()
        .source(accessDataSource)
        .target(targetDataSource)
        .schema(accessSchema)
        .tableOption("ACCESS_CUSTOMERS", BulkMigrationTableOption.builder()
                .targetTable("APP.CUSTOMERS")
                .columnMappings(Map.of("ACCESS_ID", "CUSTOMER_ID"))
                .upsertOption(BulkUpsertOption.builder()
                        .keyColumn("ACCESS_ID")
                        .build())
                .build())
        .build();
```

For multiple mapped tables, keep the Access foreign keys in the source Schema.
Migration and repair both derive parent-before-child order from that source
relationship graph, even when every target table and foreign-key column has a
different name. The lightweight target projection contains the mapped columns
and primary key needed by DML; it does not clone source relationship objects.

### Operational and advanced flows

Call `dryRun()` before execution when an approval screen needs a detached
snapshot of the resolved task order, lifecycle operations, core options,
current status, and reproducibility fingerprint. It validates the same job used
by `execute()` but does not expose connection-bound executors, execute lifecycle
operations, or create checkpoint storage. Use `dryRun(reportFile)` when the
same snapshot should also be written as JSON. Use `status()` only when an
advanced integration needs the underlying checkpoint status objects.
Use `resumeReadiness()` for a conservative one-call operational decision. It
combines the read-only checkpoint snapshot with a configured file or database
lease and never creates the database lease table merely to inspect it.

To deliberately restart a resumable job, write and review `dryRun(reportFile)`,
then pass that file to `resetCheckpoints(reportFile)`. The report is reread and
its fingerprint must match a freshly resolved read-only live plan before any
writable checkpoint store is created. The method deletes
only checkpoints and reuses a configured job lease to exclude concurrent
execution. Writable checkpoint-store preparation and deletion both occur inside
that lease. An active competing lease rejects the reset before checkpoint
changes. It never deletes target rows. In INSERT mode those rows must be
cleared or reconciled separately before rerunning, otherwise duplicates or
constraint failures are possible. `resetCheckpointsWithFingerprint(value)`
accepts an explicitly approved fingerprint when no report file is used.
For an externally stored approval, use
`resetCheckpoints(reportFile, expectedReportFingerprint, maxEvidenceFileSizeBytes)`.
It reads one bounded byte snapshot, verifies its SHA-256 fingerprint before
opening database connections, and exposes the accepted value through
`getApprovedOperationalReportFingerprint()`.

Database checkpoints are the default. A durable file store is one additional
builder call and is useful when the target database must not contain sqlapp
control tables:

```java
BulkMigration migration = BulkMigration.builder()
        .source(sourceDataSource)
        .target(targetDataSource)
        .schema(schema)
        .resume(true)
        .fingerprints(sourceVersion, targetVersion)
        .fileCheckpoints(checkpointDirectory)
        .build();
```

Use `.customCheckpointStore(store)` only when an application needs its own
durability mechanism. One table can instead set `checkpointStore` in its
`BulkMigrationTableOption`; other tables keep the global/default store. A
custom store's `load` implementation must be read-only because `status()` and
`dryRun()` call it. File inspection likewise reads an existing checkpoint without
creating its directory or a target-database table.

Concurrent execution protection is also opt-in. Use
`.fileLease(workerId, leaseDirectory)` when the workers share a filesystem, or
`.databaseLease(workerId)` when they share only the target database. The
five-minute renewable lease default is suitable for the common path; pass a
`BulkMigrationJobLeaseConfiguration` through the builder when owner, duration,
table name, or directory needs explicit control. Database leasing uses a
separate target connection so lease renewal is independent of chunk
transactions. `status()` remains a read-only checkpoint snapshot; operational
resume assessment, including live/expired lease state, remains available from
the detailed operational-report API.

Vendor-specific preparation and restoration can be added with
`.lifecycle(existingLifecycle)`. The facade includes that lifecycle in the
immutable plan, so its configuration fingerprint and planned operations remain
part of dry-run validation. The lifecycle is invoked only by `execute()`;
`dryRun()`, `status()`, and `verify()` do not prepare, disable, or restore database objects.
Use a durable lifecycle from the underlying API when recovery must survive a
process crash.

For the common file-backed case, `.fileMaintenance(sharedDirectory)` adds that
durability without requiring callers to assemble a store and wrapper. Execution
records lifecycle transitions, while `dryRun()` reads the matching state into
the operational report without changing it. The directory should be durable and
shared by every worker that can resume or inspect the same migration.
Use `.databaseMaintenance()` when workers share only the target database; an
overload accepts a custom control-table name. Execution writes maintenance state
through a dedicated autocommit target connection, independently of chunk
transactions. `dryRun()` uses the non-mutating JDBC reader, so it neither creates
nor repairs the maintenance table.
After reviewing the plan, call
`recoverMaintenanceWithFingerprint(approvedFingerprint)` to restore an
interrupted lifecycle, or `recoverMaintenance(approvedDryRunReport)` to use the
fingerprint from a saved report. Recovery rejects a changed plan and is
idempotent once the durable state is `RESTORED` or `COMPLETE`.
`recoverMaintenance(reportFile, expectedReportFingerprint,
maxEvidenceFileSizeBytes)` adds the same bounded-file and expected-SHA gate used
by checkpoint reset.
Execution performs the same durable-state preflight after acquiring any job
lease and refuses to overwrite
`PREPARING`, `PREPARED`, `POST_PROCESSING`, `RESTORING`, or `RESTORE_FAILED`.
Use the explicit recovery API first. `RESTORED` and `COMPLETE` are safe terminal
states and do not block a later execution. The same guard applies when the
low-level job executor is used directly. A rejected preflight publishes
`JOB_REJECTED` without publishing `JOB_STARTED` or invoking restoration, so an
operational report distinguishes a safe refusal from a failure after execution
began.

Add `.operationalReport(reportFile)` to atomically refresh the existing JSON
operational report at each job and table boundary. Report output is disabled by
default and report-write failures fail the migration instead of being silently
ignored. The detailed command API remains available when reporting failures
must be observed while allowing the migration to continue.
When file or database maintenance is enabled, every automatic report refresh
also reads its durable state. Task boundaries therefore expose `PREPARED`, a
successful job ends with `COMPLETE`, and a failed job reports the resulting
`RESTORED` or `RESTORE_FAILED` state. A maintenance read failure follows the
same report failure policy and is never silently rendered as missing state.
Call `migration.dryRun(reportFile)` to write and return the same operational
format before execution. Like `dryRun()`, this is read-only: it does not create
or upgrade a database checkpoint table, create a file-checkpoint directory, or
invoke the migration lifecycle.

Add `.executionReport(reportFile)` to publish a concise result after the
migration executor commits. Its overload accepts a maximum byte size. The
report retains Access/source task IDs while recording per-table previous and
current processed rows, completed chunks, already-complete state, and the
resolved plan fingerprint. `getExecutionReport()` and
`getExecutionReportFingerprint()` return the accepted model and SHA-256. A
publication failure throws `BulkMigrationExecutionReportException` with the
committed migration result, and a new attempt removes an older result file
before database execution begins.

Add `.verificationReport(reportFile)` to save every explicit `verify()` result
as the existing bounded JSON verification artifact. It also applies to the
verification phase of `executeAndVerify()`. The default retains details for at
most 1,000 mismatched chunks; use `.verificationReport(reportFile, limit)` when
a different positive bound is required. Merely calling `execute()` does not run
verification or create this report.
Verification uses each connection's current behavior by default. Set
`.verificationIsolation(REPEATABLE_READ)` (or `READ_COMMITTED` / `SERIALIZABLE`)
to run all verification reads inside transactions on both source and target.
The facade rolls those transactions back and restores the original connection
settings. This provides a stable snapshot within each database, but cannot make
two independent databases expose the same wall-clock snapshot; quiesce writes
or use database-specific snapshot coordination for that stronger guarantee.
Use `verifyOrThrow()` or `run()` when a mismatch must fail
the calling workflow. `BulkMigrationVerificationMismatchException` retains the
complete verification result. A mismatch from `run()` also retains the committed
migration result; a verification-only mismatch reports that no migration result
is present. A configured verification report is written before the exception is
raised. The non-throwing methods remain useful for
interactive review; their returned result (and `Execution.requireMatch()`) lets
the caller choose the policy explicitly.
If migration commits but verification itself cannot complete, `run()` throws
`BulkMigrationPostExecutionException`. Its migration result makes the committed
work explicit, while `getCause()` retains the verification SQL, connection, or
reporting failure.
Add `.repairPlanOnMismatch(path)` when `run()` should also write a reviewable
repair plan before throwing a mismatch. This reuses the exact failed verification
result and never executes repair; `executeApproved(path)` remains mandatory.
`verifyAndWriteRepairPlan(path)` is the shortest safe file-based repair entry
point. It verifies, writes a reviewable plan, and returns a `Repair` handle whose
`isRequired()` reports whether mismatches exist. It deliberately does not
execute repairs: `executeApproved(path)` retains the review-and-fingerprint
approval gate. Use `verifyAndPlanRepair()` when the plan should only be written
after checking the result, or `planRepair(existingVerification)` to reuse a
suitable result.
When `run()` is used with an operational report, a
verification mismatch or verification failure replaces the migration-phase
`JOB_COMPLETED` event with `JOB_FAILED`. The committed migration rows remain
committed; the event describes the outcome of the combined execute-and-verify
workflow. `executeAndVerify()` intentionally keeps `JOB_COMPLETED` and returns
the mismatch for interactive handling.
`run()` is the ordinary synchronous path and returns both the migration result
and verification result. Use `execute()` when verification must be scheduled
separately, or `executeAndVerify()` when an interactive caller wants to handle a
mismatch without an exception. `executeApproved(Path)` rereads the
reviewed JSON, checks it against a freshly resolved live plan, and then executes
the repair, so callers do not need to copy fingerprints manually.
Use `executeApproved(path, expectedReportFingerprint, maxReportAgeSeconds,
maxReportFileSizeBytes)` when approval storage also supplies an expected
SHA-256 value, retention window, or file-size limit. Validation completes before
database connections are opened. The accepted SHA-256 value is available from
`getApprovedRepairPlanReportFingerprint()` on the migration and
`getReportFingerprint()` on the repair handle.

State-aware reruns follow the same artifact pattern. `writeNodeManifest(path)`
writes the current per-table state atomically and exposes its SHA-256 value via
`getNodeManifestFingerprint()`. The simple `executeModified(path)` entry point
remains available. For a manifest kept in external approval storage, use
`executeModified(path, expectedManifestFingerprint,
maxNodeManifestFileSizeBytes)` to verify one bounded byte snapshot before
opening database connections. The accepted value is available through
`getApprovedNodeManifestFingerprint()`.
For the ordinary recurring workflow,
`executeModifiedAndWriteManifest(previousPath, currentPath)` executes the
dependency-closed changed set and atomically writes the state used by the next
run. Its overload accepts the expected previous-manifest SHA-256 value and an
input size limit. After success, `getApprovedNodeManifestFingerprint()` is the
input SHA and `getNodeManifestFingerprint()` is the newly written output SHA.

Before cutover, `assessCutover(checks, lastVerifiedAt,
maximumVerificationAge)` compares source and target watermarks and rejects stale
verification. Pass a report path to persist the same decision and measurements
as atomic JSON; the overload also accepts `maxCutoverReportFileSizeBytes`.
`getCutoverReportFingerprint()` returns the SHA-256 value of the bytes that were
written, so deployment approval can bind to the exact cutover evidence.
At the deployment boundary, call `approveCutover(reportFile,
expectedReportFingerprint, maximumReportAge, maxCutoverReportFileSizeBytes)`.
It accepts only an exact, recent `READY` report, rejects future timestamps and
expired decisions, and exposes the accepted SHA-256 value through
`getApprovedCutoverReportFingerprint()`. This validation is file-only and does
not open source or target database connections.

To avoid supplying verification time manually, use
`assessCutover(checks, verificationReport, maximumVerificationAge,
cutoverReport)`. It requires a successful verification report whose plan
fingerprint still matches the live migration plan, then uses that report's
`generatedAt` as verification time. The advanced overload accepts the expected
verification-report SHA-256 value and independent input/output size limits.
`getApprovedVerificationReportFingerprint()` identifies the exact verification
evidence used by the assessment without changing the existing cutover-report
format.

For a portable deployment gate, call
`assessCutoverAndWriteEvidence(checks, verificationReport,
maximumVerificationAge, cutoverReport, evidenceFile)`. The additional evidence
file links the successful verification report, its plan fingerprint, and the
cutover decision by SHA-256 without embedding machine-specific file paths. The
advanced overload adds an expected verification SHA-256 value and independent
size limits for all three files. Use `getCutoverEvidenceFingerprint()` as the
value handed to the deployment stage. That stage calls
`approveCutoverEvidence(evidenceFile, expectedEvidenceFingerprint,
verificationReport, cutoverReport, maximumReportAge, ...)`; approval succeeds
only when the evidence file is exact, both referenced files still have the
recorded bytes and metadata, verification succeeded, and the cutover decision
is recent and `READY`. The accepted SHA values are exposed through
`getApprovedCutoverEvidenceFingerprint()`,
`getApprovedVerificationReportFingerprint()`, and
`getApprovedCutoverReportFingerprint()`. Approval is file-only and does not
open database connections. The simpler cutover APIs remain suitable when an
external system already stores the relationship between the two reports.

For CI/CD systems that transfer directories as artifacts,
`assessCutoverPackage(checks, verificationReport, maximumVerificationAge,
packageDirectory)` publishes a new directory containing `verification.json`,
`cutover.json`, and `evidence.json`. Files are prepared in a sibling staging
directory and the completed directory is moved into place; an existing output
directory is rejected instead of being overwritten. The returned
`CutoverPackage.evidenceFingerprint()` is the value to carry in the deployment
approval. A reviewer can call `inspectCutoverPackage(packageDirectory)` to
cross-check and read the evidence, verification summary, cutover measurements,
and package SHA-256 value without approving anything or opening database
connections. `approveCutoverPackage(packageDirectory,
expectedEvidenceFingerprint, maximumReportAge)` verifies the complete package
without database access. It requires exactly the three documented regular
files and rejects missing entries, extra entries, and symbolic-link
substitution before reading report content. Advanced overloads provide the expected input
verification SHA-256 value and independent size limits. This package API is
optional; the individual-report APIs remain available for artifact stores that
manage files separately.
All file-only inspection and approval behavior is implemented by
`MigrationCutoverArtifactService`; applications that do not use the facade can
call that service directly and retain the same package inventory, fingerprint,
timestamp, successful-verification, and `READY` checks.

When only one table needs advanced behavior, keep the common builder unchanged
and add a `BulkMigrationTableOption` for that table:

```java
.tableOption("ORDERS", BulkMigrationTableOption.builder()
        .migrationId("orders-v2")
        .chunkSize(2_000)
		.verificationChunkSize(1_000)
        .keysetColumns(List.of("TENANT_ID", "ORDER_ID"))
        .verificationColumns(List.of("TENANT_ID", "ORDER_ID", "STATUS"))
        .upsertOption(orderUpsertOption)
		.retryOption(orderRetryOption)
        .build())
```

Unspecified tables retain the facade defaults. Table and column overrides are
resolved against the Schema model during `build()`; unknown, ambiguous,
duplicate, null, or empty identifiers fail before a database connection is
used. Custom keyset columns must still identify a modeled primary key, unique
constraint, or unique index and are validated by the shared keyset source.
Verification uses each table's effective migration `chunkSize` by default.
Set the facade-level `verificationChunkSize` to use one independent size for
all tables, or a table option's `verificationChunkSize` for a single-table
override. All configured sizes must be positive.
Global `bulkOption`, `upsertOption`, and `retryOption` apply to every table;
`BulkMigrationTableOption` can override each of them for one table. `bulkOption`
controls INSERT, while the bulk-copy settings nested in `upsertOption` control
UPSERT staging. Optional `jobListener` and `chunkListener` builder properties
expose the existing progress, pause, cancellation, and metrics callbacks without
changing the default no-listener path.
When `verificationColumns` is omitted, verification follows the resolved write
plan: hidden and generated/formula columns are excluded, and INSERT identity
columns are included only when `keepIdentity` is enabled. UPSERT uses its
resolved staging columns. An explicit per-table `verificationColumns` list
continues to override this default.
`status()` and `dryRun()` use the store returned by
`JdbcBulkMigrationCheckpointStore.readOnly(connection, tableName)`: they report
`NOT_STARTED` when the checkpoint table does not exist and never create or
upgrade that table. An existing table must contain all checkpoint columns and
use `MIGRATION_ID` as its sole primary-key column. Ambiguous same-name metadata,
an obsolete table, and inconsistent persisted progress are reported as errors
instead of being changed or treated as an absent checkpoint during inspection.

```java
var verification = BulkMigrationVerifier.verify(expected, actual, 10_000);
var repair = BulkMigrationRepairExecutor.execute(targetConnection, expected,
		verification, BulkMigrationRepairOption.defaults());
var after = BulkMigrationVerifier.verify(expected, rereadTarget, 10_000);
```

For JDBC streaming sources, pass the same `BulkMigrationKeysetSource` to the
keyset-source overload of `execute`. Repair rejects results without a retained
source fingerprint and rejects sources whose key order, token codec, or fetch
configuration fingerprint differs from verification. For each mismatched
chunk, it opens the source strictly after the preceding chunk's expected last
key instead of scanning from the beginning. The reread row count, hash, first
key, and last key must still match the verification artifact before UPSERT.
Adjacent mismatched chunks share one open stream; a matched gap causes repair
to reopen directly after that gap's last expected key.
The iterator is opened only after the fingerprint checks pass and is closed
before target writes begin.

For an operational dry run, create a `BulkMigrationRepairPlan` first:

```java
BulkMigrationRepairPlan plan = BulkMigrationRepairPlanner.plan(
        targetConnection, expectedSource, targetTable, verification, repairOption);

// Review mismatchChunks, estimatedReplayRows, key/staging/update columns,
// atomic, transactionBreakingStaging, stagingTableName and database identity.
BulkMigrationRepairResult result =
        BulkMigrationRepairExecutor.execute(targetConnection, plan);
```

When source and target column names differ, configure the repair with the same
one-to-one mapping used by migration. Verification columns remain source names;
UPSERT keys and updates remain target names:

```java
BulkMigrationRepairOption repairOption = BulkMigrationRepairOption.builder()
        .columnMappings(Map.of("ACCESS_ID", "CUSTOMER_ID"))
        .bulkUpsertOption(BulkUpsertOption.builder()
                .keyColumn("CUSTOMER_ID")
                .build())
        .build();
```

The mapping is validated before target access, included in the plan
fingerprint, and written into the reviewable repair-plan JSON.

The plan has a SHA-256 configuration fingerprint. Execution rejects the plan
when its source keyset configuration, source or target Schema model,
verification result, repair options, database product/version, or selected
UPSERT provider no longer matches. Expected row hashes and key boundaries are
then checked while preparing the replay, before target writes begin. SQL text
is deliberately not stored in the core plan because staging DDL and MERGE SQL
are generated by the selected dialect provider at execution time; the plan
instead exposes the resolved key, staging, and update columns and configured
staging-table name needed for review.

`sqlapp-command` can persist that review snapshot atomically as UTF-8 JSON:

```java
BulkMigrationRepairPlanReportIO reportIO =
        new BulkMigrationRepairPlanReportIO();
reportIO.write(reportFile, plan);

BulkMigrationRepairPlanReport approved =
        reportIO.read(reportFile, plan.getFingerprint());
BulkMigrationRepairResult result = BulkMigrationRepairExecutor.execute(
        targetConnection, plan, approved.planFingerprint());
```

The JSON is an approval and audit artifact, not a serialized executable plan:
it contains no credentials, connections, row data, or Java callbacks. The live
source and target are resolved again by the caller, and the current plan must
still pass its fingerprint, database identity, expected-hash, and key-boundary
checks before execution.

Multi-table repair has the same review boundary. `BulkMigrationJobRepairPlanner`
validates every task before writing, sorts tasks into the source Schema's
foreign-key create order, and returns a `BulkMigrationJobRepairPlan` with one
immutable child plan per task. This preserves Access dependency order when
target tables are renamed. Its fingerprint includes the ordered task IDs and child-plan
fingerprints. An approved fingerprint can therefore authorize exactly one
reviewed dependency order and set of repairs:

```java
BulkMigrationJobRepairPlan jobPlan = BulkMigrationJobRepairPlanner.plan(
        targetConnection, repairTasks);
new BulkMigrationJobRepairPlanReportIO().write(reportFile, jobPlan);

BulkMigrationJobRepairPlanReport approved =
        new BulkMigrationJobRepairPlanReportIO().read(
                reportFile, jobPlan.getFingerprint());
BulkMigrationJobRepairResult result = BulkMigrationJobRepairExecutor.execute(
        targetConnection, jobPlan, approved.planFingerprint());
```

Job execution revalidates all child plans and target database/provider
identities before the first task writes. The job JSON additionally validates
unique task IDs, task order, aggregate mismatch and replay counts, atomicity,
and the aggregate fingerprint. A no-replay task does not require provider
resolution, which keeps an otherwise empty repair plan usable for reporting.

The explicit-target repair path is covered against PostgreSQL 18, SQL Server
2022, MySQL 8.4, MariaDB 11.8, Firebird 5, Oracle 23ai/26ai, DB2 12.1.5,
Informix 14.10, Vertica 25.1, Sybase ASE 16, and SAP HANA Express 2.0,
including JDBC keyset verification, boundary-only rereading, vendor bulk
UPSERT, and post-repair verification. Oracle, Vertica, Sybase, and SAP HANA
tests explicitly opt into non-atomic repair because their staging DDL breaks
the surrounding transaction.

The repair chunk size is taken from the verification result. By
default, each replay asks the selected UPSERT provider to use a transaction.
Repair validates and buffers every selected expected chunk before the first
target write. Set `BulkMigrationRepairOption.maxBufferedRows` to a positive
limit to fail safely before writing instead of allowing an unexpectedly large
mismatch set to consume unbounded heap; zero retains the unlimited default.
The executor validates each selected expected hash before writing it and stops
if the source changed after verification. When verification used an explicit
column subset, repair recomputes this guard hash from that same ordered subset;
changes in unverified columns do not create a false source-change failure.
Already repaired chunks may remain
committed if a later chunk fails; callers that require a wider transaction may
provide a non-auto-commit connection, subject to the selected provider's
transaction capabilities.

Repair never deletes target rows. A chunk with more actual than expected rows,
or one with no corresponding expected rows, is reported by
`requiresManualReconciliation()`. Positional chunk hashing can also cause one
missing or extra row to shift later chunk boundaries. Always reread and verify
the target after repair; use key-aware reconciliation before deleting any
remaining target-only rows.

