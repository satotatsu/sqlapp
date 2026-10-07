# Migration report Gradle tasks

[Documentation index](../README.md)

### `generateBulkMigrationOperationalReport`

This task is intended for builds that assemble a `BulkMigrationJobPlan` in
Java or a Gradle plugin. Set `plan` and its matching read-only
`BulkMigrationJobStatus`, then set `targetFile`. `maintenanceState` and
`progress` are optional. The task only writes a JSON snapshot; it does not run
the migration, modify checkpoints, or recover maintenance. All complex values
are programmatic properties rather than a second migration-plan file format,
and the task is deliberately not build-cacheable because their stores can
change outside Gradle. Optional `maxOperationalReportFileSizeBytes` bounds the
generated JSON. The command atomically writes and rereads the report, compares
the parsed model, and exposes the accepted SHA-256 fingerprint.

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
    maxRepairPlanReportFileSizeBytes = 2 * 1024 * 1024L // optional
}
```

The generated report is atomically written, read back from a bounded byte
snapshot, and compared with the requested model. The command exposes the
accepted report and its SHA-256 fingerprint for approval workflows.

