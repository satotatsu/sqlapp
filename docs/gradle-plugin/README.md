# Gradle plugin task guide

The `com.sqlapp.db` plugin exposes sqlapp commands as Gradle tasks. This
document is the index and authoritative task-name reference. Runnable project
configurations are maintained in
[`sqlapp-gradle-example`](https://github.com/satotatsu/sqlapp-gradle-example).
The examples in this guide have also been checked against that project's
`build.gradle` and `gradle.properties` layout.

## Documentation map

Start with [Getting started](getting-started.md) for dependencies, connection
configuration, and a complete Schema XML to HTML workflow. Examples use the
Groovy DSL (`build.gradle`) and describe the implementation in this checkout;
use a plugin release that contains the tasks you need.

| Guide | Contents |
|---|---|
| [Getting started](getting-started.md) | Plugin setup, JDBC runtime, credentials, task dependencies, troubleshooting |
| [Kotlin DSL](kotlin-dsl.md) | Typed `build.gradle.kts` setup, lazy properties, DataSource files, and task registration |
| [Troubleshooting](troubleshooting.md) | Task discovery, classpath, dialect, DataSource files, paths, outputs, and diagnostic commands |
| [Runnable example project](example-project.md) | How the companion project is organized and which task demonstrates each workflow |
| [Task reference](task-reference.md) | Registered names, task classes, primary inputs/outputs, database effects, and common properties |
| [Schema, SQL and HTML](schema-sql-and-html.md) | XML export, comparison, SQL generation, documentation properties and outputs |
| [Custom tasks and versioned migrations](custom-tasks-and-migrations.md) | Data export, file conversion, SQL execution, migration extension and task types |
| [Normalization and legacy migration](normalization-and-legacy-migration.md) | Normalization, PL/I import, extraction contracts and hierarchy loading |
| [Database migration assessment](database-migration-assessment.md) | Generic Access migration diagnosis, mapping, data checks, DDL preview and verification |
| [Access to Oracle](access-oracle-migration-assessment.md) | Oracle-specific mapping, review points and deployment-ready DDL verification |
| [Access to SQL Server](access-sqlserver-migration-assessment.md) | SQL Server-specific mapping, review points and deployment-ready DDL verification |
| [Oracle migration assessment](oracle-migration-assessment.md) | Offline preflight, object inventory, evidence and manual checks for a 26ai target |

Bulk migration and SCD2 snapshot configuration are covered below.

## Applying the plugin

```groovy
plugins {
    id 'com.sqlapp.db' version '<sqlapp-version>'
}
```

Use the Gradle Wrapper and Java 21. Run `gradlew tasks` (Windows:
`gradlew.bat tasks`) to inspect the tasks available to a project.

## Tasks registered by the plugin

| Area | Task | Purpose |
|---|---|---|
| Schema inspection | `countAllTables` | Count rows in database tables |
| Schema inspection | `exportSchemaXml` | Export database metadata as sqlapp Schema XML |
| Schema inspection | `exportAccessSchemaXml` | Export an Access MDB/ACCDB file as sqlapp Schema XML |
| Schema inspection | `exportSqliteSchemaXml` | Export a SQLite DB/SQLite/SQLite3 file as sqlapp Schema XML |
| Schema comparison | `diffSchemaXml` | Compare two Schema XML files |
| SQL generation | `generateDiffSql` | Generate SQL from a schema difference |
| SQL generation | `generateSql` | Generate SQL from Schema XML |
| Documentation | `generateHtmlDocs` | Generate HTML documentation and ER diagrams |
| Migration | `migration` | Apply versioned database migrations |
| Migration | `assessMigration` | Assess a Schema XML snapshot offline and write a migration preflight JSON report |
| Migration | `assessDatabaseMigration` | [Assess a source file for an explicit target database/version](database-migration-assessment.md); Access to Oracle or SQL Server |
| Migration | `assessAccessOracleMigration` | Compatibility alias with the Oracle target preset |
| Migration | `verifyDatabaseMigrationDdlPhases` | Verify generated migration DDL with only its directory; optionally bind an assessment, approved fingerprints and deployment-readiness policies |
| Migration | `generateAccessBulkMigrationJobConfiguration` | Generate a resumable initial-load YAML for the existing bulk migration task from a compatible reviewed Access assessment |
| Migration | `migrationValidate` | Read-only validation of recorded up SQL checksums; missing checksums are unverified |
| Migration | `migrationPlan` | Read-only plan of pending versioned SQL and known blockers |
| Migration | `migrationInsert` | Insert migration history |
| Migration | `migrationRepair` | Repair migration history |
| Migration | `executeBulkMigrationJob` | Execute a programmatic plan or declarative migration job |
| Migration | `validateBulkMigrationTarget` | Validate a declarative job against the live target without migrating rows |
| Migration | `verifyBulkMigrationEvidence` | Verify execution and data reports and optionally write a portable audit JSON |
| Migration | `verifyBulkMigrationEvidenceReport` | Revalidate a saved bulk migration audit and its exact source artifacts offline |
| Migration | `executeMigrationSnapshot` | Apply one atomic SCD2 snapshot from YAML |
| Migration | `generateMigrationSnapshotApprovalReport` | Generate a snapshot approval artifact without database access |
| Migration | `verifyMigrationSnapshotReport` | Verify a saved snapshot success report against its approval |
| Migration | `verifyMigrationSnapshotFailureReport` | Verify a saved snapshot failure report against its approval |
| Migration | `generateBulkMigrationOperationalReport` | Write a bulk migration plan/status snapshot as JSON |
| Migration | `generateBulkMigrationJobRepairPlanReport` | Write a review-only repair plan as JSON |
| Migration | `executeBulkMigrationJobRepair` | Re-verify and execute an approved declarative repair plan |
| Migration | `verifyBulkMigrationJobRepairEvidence` | Verify repair approval, execution and post-repair evidence without database access |
| Migration | `verifyBulkMigrationJobRepairFailureEvidence` | Verify approved repair failure evidence without database access |
| Migration | `verifyBulkMigrationJobRepairOutcome` | Verify whichever success or failure evidence exists for an approved repair |
| Migration | `verifyBulkMigrationJobRepairOutcomeReport` | Revalidate a saved unified repair outcome against its source evidence |
| Normalization | `generateNormalizationPlan` | Generate reviewable normalization candidates and a preview schema |
| Normalization | `firstNormalForm` | Split repeating column groups and optionally replace composite primary keys |
| Normalization | `columnRuleTransform` | Apply YAML-based column type and naming rules |
| Legacy migration | `pliSchemaImport` | Convert PL/I declarations to Schema XML and migration mapping |
| Legacy migration | `generateLegacyMigrationContract` | Generate the CSV extraction/load contract |
| Legacy migration | `generatePliCsvExtractor` | Generate PL/I CSV extraction artifacts |
| Legacy migration | `generateLegacyRdbLoader` | Generate a restartable hierarchy load plan |
| Legacy migration | `loadLegacyHierarchy` | Execute a generated hierarchy load plan |

Some public task classes, such as data import/export, data generation, format
conversion, SQL execution, and migration-down tasks, are not registered under
a fixed name. A build may register those task types with a project-specific
name. See [Custom tasks and versioned migrations](custom-tasks-and-migrations.md)
for registration and configuration examples.

### `generateBulkMigrationOperationalReport`

This task is intended for builds that assemble a `BulkMigrationJobPlan` in
Java or a Gradle plugin. Set `plan` and its matching read-only
`BulkMigrationJobStatus`, then set `targetFile`. `maintenanceState` and
`progress` are optional. The task only writes a JSON snapshot; it does not run
the migration, modify checkpoints, or recover maintenance. All complex values
are programmatic properties rather than a second migration-plan file format,
and the task is deliberately not build-cacheable because their stores can
change outside Gradle.

### `generateBulkMigrationJobRepairPlanReport`

This task writes a programmatically assembled `BulkMigrationJobRepairPlan` to
an approval and audit JSON file. Set `plan` and `targetFile`. It does not replay
rows, execute UPSERT, or modify the database. The plan must already have been
created with `BulkMigrationJobRepairPlanner`, which performs database/provider
preflight and dependency ordering before the task writes the report.

```groovy
generateBulkMigrationJobRepairPlanReport {
    plan = assembledRepairPlan
    targetFile = layout.buildDirectory.file('reports/migration/repair-plan.json')
}
```

### `executeMigrationSnapshot`

This task applies one complete SCD2 source snapshot atomically. It deliberately
does not use resumable chunks: missing-row expiry is correct only when the
complete source snapshot is evaluated as one unit. The target Dialect selects
set-based staging when supported and otherwise uses the bounded-memory JDBC
fallback.

```groovy
executeMigrationSnapshot {
    configurationFile = file('migration/customer-snapshot.yaml')
    sourceDataSource { jdbcUrl = 'jdbc:postgresql://source/app' }
    dataSource { jdbcUrl = 'jdbc:postgresql://target/app' }
}
```

```yaml
schemaFile: schema.xml
sourceTable: public.customer
targetTable: public.customer_history
keyColumns: [customer_id]
trackedColumns: [name, department]
validFromColumn: valid_from
validToColumn: valid_to
currentColumn: is_current
expireMissingRows: true
effectiveAt: 2026-09-16T00:00:00Z
fetchSize: 10000
batchSize: 10000
# Optional: require a reviewed approval artifact to match this run.
approvalValidFor: PT24H
approvalReportFile: approvals/customer-snapshot.json
reportFile: reports/customer-snapshot.json
failureReportFile: reports/customer-snapshot-failure.json
lease:
  mode: DATABASE
  ownerId: nightly-migration-1
  durationSeconds: 300
  # Optional; defaults to SQLAPP_BULK_MIGRATION_LEASE.
  tableName: SQLAPP_BULK_MIGRATION_LEASE
```

`effectiveAt` is required so retries and reviewed runs retain the same business
timestamp. `schemaFile` and optional `reportFile` are resolved relative to the
YAML file. Optional `approvalReportFile` is also relative to the YAML file. It
must contain the same configuration fingerprint or execution fails before a
database connection is opened. Optional `approvalValidFor` is an ISO-8601
duration; expired and future-dated approvals are rejected before connection.
A report is written only after successful
database execution and contains the resolved snapshot identity, source and
target tables, effective timestamp, selected executor and affected-row counts.
It also carries a deterministic configuration fingerprint covering the
snapshot definition, execution sizes and resolved source/target table shapes.
The optional `lease` block prevents concurrent execution of the same snapshot
identity. `DATABASE` mode uses a separate auto-commit target connection so its
heartbeat is independent of the snapshot transaction. `FILE` mode instead
requires `directory`, resolved relative to this YAML file. Lease loss is checked
after target validation and immediately before commit; a detected loss rolls
back all snapshot changes. Lease owner, storage location and duration are
operational coordination settings and do not change the approval fingerprint.

Generate the approval artifact without opening a database, review it, and then
reference it from the YAML shown above:

```groovy
generateMigrationSnapshotApprovalReport {
    configurationFile = layout.projectDirectory.file('snapshot.yaml')
    targetFile = layout.buildDirectory.file('migration-approvals/customer.json')
}
```

The generator intentionally ignores `approvalReportFile` while producing the
candidate artifact, so the first approval can be created before that file
exists. `executeMigrationSnapshot` performs the strict validation. Its success
report records the approval generation time and the SHA-256 of the exact
validated approval file. It also records separate execution start and completion
timestamps and validates that approval was active at execution start.

Audit the saved pair later without opening either database:

```groovy
verifyMigrationSnapshotReport {
    reportFile = layout.buildDirectory.file('reports/customer-snapshot.json')
    approvalFile = layout.buildDirectory.file('migration-approvals/customer.json')
    // Optional: also verify the current resolved lease and configuration.
    configurationFile = layout.projectDirectory.file('snapshot.yaml')
}
```

Verification checks the exact file digest, approval generation time,
configuration fingerprint, tables, columns, timestamp and execution sizes.
If execution produced the separately configured failure artifact, verify its
exact approval binding in the same way:

```groovy
verifyMigrationSnapshotFailureReport {
    reportFile = layout.buildDirectory.file('reports/customer-snapshot-failure.json')
    approvalFile = layout.buildDirectory.file('migration-approvals/customer.json')
}
```

The failure verifier also checks the recorded phase and chronology.
`POST_COMMIT_FINALIZATION` and `SUCCESS_REPORT_WRITE` both mean the target
database work committed; the latter specifically means the normal success
artifact could not be published.

### `executeBulkMigrationJob`

This task synchronously executes either a programmatically assembled
`BulkMigrationJobPlan` or a YAML `configurationFile` against its target
`dataSource`. Specify exactly one of `plan` and `configurationFile`. Declarative
jobs also require `sourceDataSource`; their `schemaFile` is resolved relative
to the YAML file. Database lease mode obtains a separate target connection so
lease transactions never share the connection used for migrated rows. The task
is not build-cacheable because it mutates an external database.

```groovy
executeBulkMigrationJob {
    dataSource {
        jdbcUrl = 'jdbc:postgresql://localhost/app'
        username = providers.gradleProperty('dbUser')
        password = providers.gradleProperty('dbPassword')
    }
    plan = assembledBulkMigrationPlan
    leaseConfiguration = BulkMigrationJobLeaseConfiguration.database('worker-1')
}
```

The equivalent declarative task uses independent source and target connections:

```groovy
executeBulkMigrationJob {
    configurationFile = file('migration/job.yaml')
    sourceDataSource { jdbcUrl = 'jdbc:postgresql://source/app' }
    dataSource { jdbcUrl = 'jdbc:postgresql://target/app' }
}
```

To require the reviewed live-target check during execution, add its evidence
and an explicit freshness limit:

```groovy
executeBulkMigrationJob {
    configurationFile = file('migration/job.yaml')
    sourceDataSource { jdbcUrl = 'jdbc:ucanaccess:///data/source.accdb' }
    dataSource { jdbcUrl = 'jdbc:oracle:thin:@//target.example:1521/app' }
    targetValidationReportFile = layout.buildDirectory.file('reports/target-validation.json')
    expectedTargetValidationReportFingerprint = providers.gradleProperty('approvedTargetValidationFingerprint')
    maxTargetValidationAgeSeconds = 3600L
    maxConfigurationFileSizeBytes = 1048576L
    maxTargetValidationReportFileSizeBytes = 2097152L
    targetEnvironmentId = 'production-oracle'
}
```

The report must match the resolved configuration fingerprint, plan, job,
ordered Access task IDs and provenance. Its age limit is mandatory when the
report gate is enabled; the report fingerprint remains optional. Execution
also compares the database product, version, catalog, schema and optional
environment ID, then repeats the live target checks before writing rows.
When an expected configuration or target-report fingerprint is supplied, the
SHA-256 and parsed model are derived from the same byte snapshot. The accepted
target-report fingerprint is reused in operational and repair provenance, so a
file replacement between approval and evidence generation cannot authorize
different content.
The two byte limits are optional and have no implicit default. When configured,
they reject oversized YAML or target evidence before parsing, and the bounded
read also detects a file that grows after its initial size check. Repair
execution exposes the same properties.

Run the same plan resolution and target checks before the deployment window:

```groovy
validateBulkMigrationTarget {
    configurationFile = file('migration/job.yaml')
    sourceDataSource { jdbcUrl = 'jdbc:ucanaccess:///data/source.accdb' }
    dataSource {
        jdbcUrl = 'jdbc:oracle:thin:@//target.example:1521/app'
        username = providers.gradleProperty('dbUser')
        password = providers.gradleProperty('dbPassword')
    }
    expectedConfigurationFingerprint = providers.gradleProperty('approvedJobFingerprint')
    assessmentReportFile = file('migration/assessment.json')
    ddlVerificationReportFile = file('migration/ddl-verification.json')
    reportFile = layout.buildDirectory.file('reports/target-validation.json')
    targetEnvironmentId = 'production-oracle'
}
```

This task performs no migration writes. It validates each resolved target table,
mapped column, non-null primary or unique key, and the optional
`requireEmptyTarget` initial-load condition. A successful command result retains
the configuration fingerprint, plan fingerprint and Access-derived task IDs.
The approval files and JSON report are optional. When supplied, their SHA-256
values must match the YAML provenance and the report records the validated
configuration, plan, job and task identities using atomic file replacement.
`targetEnvironmentId` is optional and distinguishes environments that otherwise
expose the same database product, catalog and schema.

```yaml
jobId: nightly-customer-migration
schemaFile: schema.xml
lease:
  mode: DATABASE
  ownerId: migration-worker-1
  durationSeconds: 300
# tableName defaults to SQLAPP_BULK_MIGRATION_JOB_LEASE
report:
  targetFile: reports/migration-status.json
  failurePolicy: FAIL_JOB
verification:
  enabled: true
  chunkSize: 10000
  failOnMismatch: true
  maxReportedMismatches: 1000
  repairPlanOnMismatchFile: reports/repair-plan.json
  isolation: REPEATABLE_READ
  targetFile: reports/verification.json
tasks:
  - id: customers
    table: public.customers
    keysetColumns: [customer_id]
    migrationId: customers-v1
    chunkSize: 10000
    mode: UPSERT
    resume: true
    checkpointMode: DATABASE
    checkpointTableName: SQLAPP_BULK_MIGRATION_CHECKPOINT
    sourceFingerprint: source-schema-v1
    targetFingerprint: target-schema-v1
    keyColumns: [customer_id]
    verificationColumns: [customer_id, customer_name, status]
    duplicateKeyStrategy: LAST
    bulk:
      batchSize: 5000
      keepNulls: true
      tableLock: true
    retry:
      maxRetries: 3
      initialBackoffMillis: 1000
      backoffMultiplier: 2.0
      maxBackoffMillis: 30000
      sqlStates: ['40001']
```

`jobId` is optional, but a stable explicit value is recommended for recurring
jobs. It keeps leases, maintenance recovery, and operational reports associated
with the same logical job when task options change. When omitted, the plan derives
an ID from its initial contents.

Unqualified table names are accepted only when unique in the Schema XML.
`CUSTOM` duplicate selection remains programmatic because executable selector
code cannot be represented safely in YAML. The nested `bulk` block is shared by
INSERT and UPSERT; the nested `retry` block controls retry of a complete,
transactional chunk and may select transient exceptions, SQLStates, and vendor
error codes.

For file checkpoints, set `checkpointMode: FILE` and
`checkpointDirectory: checkpoints`. Relative checkpoint directories are
resolved from the YAML location. File checkpoints provide at-least-once replay;
chunk retry is therefore limited to transactional DATABASE checkpoints.
`CUSTOM` checkpoint stores remain available only to programmatic plans.

The optional top-level `report` block refreshes the operational JSON report at
job and task boundaries. Its path is relative to the YAML file. `FAIL_JOB`
preserves strict reporting; `CONTINUE_JOB` records a reporting failure without
stopping data migration.

The optional `verification` block re-reads source and target in the configured
unique keyset order after migration, then compares total counts and normalized
SHA-256 hashes per chunk. `failOnMismatch: true` fails the task after retaining
the committed migration and its verification result; `false` returns the
mismatch through `ExecuteBulkMigrationJobCommand.verificationResult` without
failing execution. When `targetFile` is set, a bounded JSON summary containing
counts and only mismatched chunk hashes is atomically replaced before mismatch
failure is raised, so CI retains the evidence. The report can be read with
`BulkMigrationVerificationReportIO.read`; the overload accepting a plan
fingerprint rejects stale artifacts.
When `repairPlanOnMismatchFile` is set, a mismatch also writes a review-only
job repair plan before `failOnMismatch` is evaluated. The plan reuses the live
verification boundaries, source-name column mappings, UPSERT policy, and
source Schema dependency order. It never executes repair; an operator must
review the JSON and use an approved fingerprint through the repair executor.
The registered `executeBulkMigrationJobRepair` task performs that final step.
It resolves the original YAML, re-runs verification, rebuilds the live repair
plan, and requires the reviewed JSON fingerprint to match before writing:

```groovy
tasks.named('executeBulkMigrationJobRepair') {
    configurationFile = layout.buildDirectory.file('reports/access-load.yaml')
    approvedRepairPlanFile = layout.buildDirectory.file('reports/access-load-repair-plan.json')
    expectedApprovedRepairPlanFileFingerprint = providers.gradleProperty('approvedRepairPlanFileFingerprint')
    maxApprovedRepairPlanAgeSeconds = 86400L
    maxApprovedRepairPlanFileSizeBytes = 10 * 1024 * 1024L
    maxEvidenceFileSizeBytes = 20 * 1024 * 1024L
    repairReportDirectory = layout.buildDirectory.dir('reports/access-load-repair')
    expectedConfigurationFingerprint = providers.gradleProperty('approvedJobFingerprint')
    assessmentReportFile = layout.buildDirectory.file('reports/migration.json')
    ddlVerificationReportFile = layout.buildDirectory.file('reports/ddl-verification.json')
    targetValidationReportFile = layout.buildDirectory.file('reports/target-validation.json')
    expectedTargetValidationReportFingerprint = providers.gradleProperty('approvedTargetValidationFingerprint')
    maxTargetValidationAgeSeconds = 3600L
    targetEnvironmentId = 'production-oracle'
    sourceDataSource { jdbcUrl = 'jdbc:ucanaccess:///data/source.accdb' }
    dataSource { jdbcUrl = 'jdbc:oracle:thin:@//target.example:1521/app' }
}
```

If source data, target data, verification boundaries, mappings, or job options
changed after review, the regenerated fingerprint differs and repair is rejected.
After applying the approved chunks, the task always repeats verification with
the YAML settings and fails unless source and target match. The optional
`repairExecutionReportFile` records the approved plan file fingerprint and
task-level replay counts. `postRepairVerificationReportFile` persists the final
data-comparison evidence. Both are optional for the simple execution path.
`repairFailureReportFile` is also optional. When repair preflight or a task
fails, it records the failure phase and task plus the completed task prefix;
configured outcome files from an earlier run are cleared immediately before
replay. Outcome paths must be distinct from each other and from approval input
files. A failure-report write error is attached to the original repair error.
it is a distinct format and cannot be mistaken for successful execution.
`repairOutcomeReportFile` is the integrated audit option. When set, the
execution task runs the same strict outcome verifier and atomically publishes
`SUCCEEDED`, `EXECUTION_FAILED`, or `VERIFICATION_FAILED` before returning.
It requires all three detailed report paths because their SHA-256 values are
the authoritative evidence referenced by the bounded outcome. A failure to
publish failure-state evidence is attached to the original repair exception;
a success-state publication error fails the task after repaired data and
detailed evidence have already been written.
`repairReportDirectory` is the common concise configuration. It supplies
`repair-execution.json`, `repair-failure.json`,
`post-repair-verification.json`, and `repair-outcome.json` within that
directory. Any individually configured report file overrides only its matching
default, keeping custom CI layouts available without complicating the normal
case. A path that already exists as a regular file is rejected before database
access.
The file-only `verifyBulkMigrationJobRepairEvidence` task can later bind the
approved plan, execution report, and successful post-repair verification into
one audit check without reconnecting to either database:

```groovy
verifyBulkMigrationJobRepairEvidence {
    approvedRepairPlanFile = layout.buildDirectory.file('reports/access-load-repair-plan.json')
    expectedApprovedRepairPlanFileFingerprint = providers.gradleProperty('approvedRepairPlanFileFingerprint')
    maxApprovedRepairPlanAgeSeconds = 86400L
    repairReportDirectory = layout.buildDirectory.dir('reports/access-load-repair')
    expectedRepairExecutionReportFingerprint = providers.gradleProperty('approvedRepairExecutionFingerprint')
    expectedPostRepairVerificationReportFingerprint = providers.gradleProperty('approvedPostRepairVerificationFingerprint')
    maxEvidenceAgeSeconds = 86400L
}
```

Failed runs can be checked independently, without requiring a success or
post-repair verification report:

```groovy
verifyBulkMigrationJobRepairFailureEvidence {
    approvedRepairPlanFile = layout.buildDirectory.file('reports/access-load-repair-plan.json')
    expectedApprovedRepairPlanFileFingerprint = providers.gradleProperty('approvedRepairPlanFileFingerprint')
    maxApprovedRepairPlanAgeSeconds = 86400L
    repairReportDirectory = layout.buildDirectory.dir('reports/access-load-repair')
    expectedPostRepairVerificationReportFingerprint = providers.gradleProperty('approvedFailedVerificationFingerprint')
    maxEvidenceAgeSeconds = 86400
}
```

This checks the approved-plan file SHA, repair-plan fingerprint, completed task
prefix, failed task position, optional expected fingerprints, provenance and
evidence age. For `POST_VERIFICATION` failures, it also checks the referenced
mismatch report when that report was configured during repair.
The optional expected post-repair verification fingerprint provides a second,
externally supplied pin in addition to the SHA stored inside failure evidence.
Both evidence tasks accept the same `repairReportDirectory` used by repair
execution. Individual report-file properties remain available as overrides.

For the common audit path, `verifyBulkMigrationJobRepairOutcome` selects the
failure report when it exists; otherwise it verifies the execution and
post-repair verification reports. It returns `SUCCEEDED`, `EXECUTION_FAILED`,
or `VERIFICATION_FAILED` while delegating all checks to the strict verifiers:

```groovy
verifyBulkMigrationJobRepairOutcome {
    approvedRepairPlanFile = layout.buildDirectory.file('reports/access-load-repair-plan.json')
    expectedApprovedRepairPlanFileFingerprint = providers.gradleProperty('approvedRepairPlanFileFingerprint')
    maxApprovedRepairPlanAgeSeconds = 86400L
    repairReportDirectory = layout.buildDirectory.dir('reports/access-load-repair')
    expectedStatus = 'SUCCEEDED'
    maxEvidenceAgeSeconds = 86400
}
```

All three conventional outcome paths may be configured together. The Gradle
task treats the alternatives as optional file inputs, so the intentionally
absent failure file on success, or absent success files on execution failure,
does not cause Gradle input validation to stop before outcome selection.
An execution failure rejects any existing success-side artifact as conflicting
evidence. A verification failure also binds an available execution report to
the failure and mismatch reports by fingerprints, task results, provenance and
timestamps. Expected fingerprints are never treated as satisfied when their
corresponding evidence file is absent.
When `outcomeReportFile` is set, the task atomically writes one bounded JSON
summary after successful verification. It records the selected status,
migration and repair identities, exact evidence SHA-256 fingerprints, optional
failure phase and task, and shared provenance. This gives CI one stable
artifact for all three outcomes while the input reports remain authoritative
for details. The output path must differ from every input report. Omitting the
property preserves log-only verification.
When configured, a stale outcome file is removed before validation starts, so
a failed validation cannot leave a previous decision available to CI.
`expectedStatus` is optional. Set it to `SUCCEEDED` for a deployment gate; a
verified execution or post-verification failure then writes the actual outcome
report and fails the task with both actual and expected statuses. Leave it
unset when all three verified states are valid audit results. An invalid status
value is rejected before an existing outcome output is removed.

Use `verifyBulkMigrationJobRepairOutcomeReport` when consuming that saved
summary later or in a separate CI stage:

```groovy
verifyBulkMigrationJobRepairOutcomeReport {
    approvedRepairPlanFile = layout.buildDirectory.file('reports/access-load-repair-plan.json')
    expectedApprovedRepairPlanFileFingerprint = providers.gradleProperty('approvedRepairPlanFileFingerprint')
    maxApprovedRepairPlanAgeSeconds = 86400L
    repairReportDirectory = layout.buildDirectory.dir('reports/access-load-repair')
    expectedStatus = 'SUCCEEDED'
    expectedOutcomeReportFingerprint = providers.gradleProperty('approvedRepairOutcomeFingerprint')
    maxEvidenceAgeSeconds = 86400
}
```

The verifier recalculates outcome selection through the strict source-evidence
verifiers and compares every saved identity, source SHA, failure reference and
provenance field. It also rejects a future or expired outcome and an outcome
timestamp preceding any selected source report. Alternative source paths use
the same optional-file behavior as the generating task.
Both outcome tasks accept `repairReportDirectory` and use the same four names
as repair execution. Individual file properties remain available as selective
overrides.
All repair execution and evidence-verification tasks also accept
`expectedApprovedRepairPlanFileFingerprint`. When configured, the lowercase
`sha256:` value is checked against the exact reviewed plan file before repair
execution or evidence selection proceeds. Optional
`maxApprovedRepairPlanAgeSeconds` rejects stale approval plans; a plan dated in
the future is always rejected. Optional
`maxApprovedRepairPlanFileSizeBytes` rejects an unexpectedly large approval
file before JSON parsing. It has no default, so the common configuration stays
unchanged; set it where an operator or CI policy defines an appropriate bound.
Optional `maxEvidenceFileSizeBytes` applies the same bounded-read behavior to
repair execution, failure, post-repair verification, and saved outcome JSON.
It is also unset by default and is shared by all repair evidence verifiers.
Repair execution captures the accepted plan SHA once and binds every detailed
and unified outcome artifact to it. Integrated outcome publication rechecks the
current plan file against that captured value.
The approval file is read once as bytes, and both its SHA and parsed model come
from that snapshot. The model is retained for the live plan comparison.
Execution, failure, post-repair verification, and saved outcome evidence use
the same single-snapshot rule, so expected SHA checks and JSON validation
cannot observe different versions of a replaced file. Saved-outcome ordering
checks reuse the already accepted verification model instead of reopening it.
Repair execution also rereads each generated evidence file immediately after
its atomic write, requires the model to equal the generated model, and pins the
accepted SHA into integrated outcome verification.
Evidence verification requires execution and failure timestamps to follow the
approval timestamp.

The assessment, DDL and target-validation approval properties have the same
meaning as on `executeBulkMigrationJob`. When supplied, repair validates their
fingerprints, freshness, environment ID and connected database identity before
reading or writing repair rows. Their provenance is retained in the final
verification report.
Each task entry records the ordered comparison columns as well as its counts
and mismatched chunk hashes. The report's top-level `isolation` field records
the selected JDBC consistency level. Mismatch entries also include source and
target first/last keyset tokens. Treat this artifact as migration data because
those tokens may expose business-key values.
The task entry also records both keyset-source configuration fingerprints so
automation can reject tokens created by a different key order or codec.
`maxReportedMismatches` defaults to 1000 and limits the mismatch details stored
per task. Each task still records `mismatchedChunks`, the uncapped total.
If verification fails, the operational report's final execution event is
updated to `JOB_FAILED`; this does not roll back chunks already committed by
the migration.
`isolation` defaults to `DEFAULT`. `READ_COMMITTED`, `REPEATABLE_READ`, and
`SERIALIZABLE` select the corresponding JDBC transaction isolation. A stronger
level stabilizes each database's verification view but is not a distributed
snapshot across the source and target; quiesce application writes when an
atomic cross-database comparison is required.

Per-task `verificationColumns` may restrict comparison to columns whose values
must be identical. When omitted, INSERT verifies writable inserted columns and
UPSERT verifies staging columns. Hidden columns, formula columns, and target-
generated identity columns not retained by the migration are excluded by
default.

For `FILE` lease mode, replace `tableName` with `directory`. A relative lease
directory is resolved from the job YAML location. Lease configuration may be
supplied either in YAML or through the task's `leaseConfiguration` property,
but not both.

## Choose a workflow

- Schema XML, SQL, migration, HTML, dictionaries:
  [`sqlapp-gradle-example`](https://github.com/satotatsu/sqlapp-gradle-example)
- Normalization and legacy migration:
  [Normalization and legacy-migration tasks](normalization-and-legacy-migration.md)
- Schema viewpoints:
  [Schema viewpoints](../schema-viewpoints.md)
- Building and testing sqlapp itself:
  [Build and test](../build-and-test.md)

## Configuration conventions

Task names and properties are public user-facing APIs. File and directory
properties use Gradle's lazy property types, but Groovy build scripts may use
normal assignment syntax:

```groovy
generateNormalizationPlan {
    targetFile = file('schemas/legacy.xml')
    outputDirectory = file('build/normalization-plan')
}
```

A value listed as a convention is used only when the build does not configure
the property. Required files and directories must be configured before task
execution. Relative paths are resolved against the Gradle project directory.

### Export an Access file

```groovy
exportAccessSchemaXml {
    inputFile = file('customer.accdb')
    outputFile = layout.buildDirectory.file('schema/customer.xml')
    schemaName = 'public'
    dumpRows = true
    includeRowDumpTables.addAll('顧客', '受注')
}
```

`inputFile` and `outputFile` are required. `schemaName` and the row filters are
optional; `dumpRows` defaults to `true`. Encrypted files and Access complex
columns such as attachments and multi-value fields are not supported.

### Export a SQLite file

```groovy
exportSqliteSchemaXml {
    inputFile = file('customer.sqlite3')
    outputFile = layout.buildDirectory.file('schema/customer.xml')
    schemaName = 'public'
    dumpRows = true
    includeRowDumpTables.addAll('顧客', '受注')
}
```

`inputFile` and `outputFile` are required. Files ending in `.db`, `.sqlite`,
or `.sqlite3` are supported; a valid unencrypted SQLite header also allows an
arbitrary extension. `schemaName` renames the exported Schema model; it does
not select an attached SQLite database. Row filters are optional and
`dumpRows` defaults to `true`. The source file is opened read-only. Encrypted
SQLite files are diagnosed explicitly but require a separate encryption-aware
JDBC driver and are not decrypted by this task.

Java callers that need an attached database can use
`SqliteFileLoader.loadSchema(primaryFile, databaseName, attachments)`. Each
lazy row-reading connection reapplies the same attachments, so metadata and
row data are read from the selected database consistently.

## Documentation maintenance

When a task is added or its user-facing properties change:

1. Update this index and the applicable task reference.
2. Update a runnable configuration in `sqlapp-gradle-example`.
3. Verify property names, types, conventions, required inputs, and outputs
   against the task implementation.
