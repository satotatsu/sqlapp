# Snapshot Gradle task

[Documentation index](../README.md)

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

