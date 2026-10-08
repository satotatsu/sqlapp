# Data movement and migration

[Documentation index](../README.md)

Choose a workflow first, then consult its operational reference. Java and Gradle
entry points use the shared migration validation and execution components.

| Goal | Reference | Gradle configuration or related guide |
|---|---|---|
| Insert or upsert table rows | [Bulk insert and upsert](../data/bulk-insert.md) | Vendor providers and batch behavior |
| Resume a single-table transfer | [Chunk migration](chunk-migration.md) | Checkpoints, leases, and transactions |
| Verify or repair transferred data | [Verification and recovery](verification-and-recovery.md) | Java quick start and advanced operations |
| Coordinate multiple tables | [Migration jobs](jobs.md) | [Gradle tasks](../gradle-plugin/bulk-migration.md) |
| Apply an SCD2 snapshot | [Snapshot execution](snapshot.md) | [Gradle task](../gradle-plugin/snapshot.md) |
| Assess an Access migration | [Assessment](../gradle-plugin/database-migration-assessment.md) | [Mapping](assessment-mapping.md) and [initial load](access-initial-load.md) |
| Assess an Oracle migration | [Oracle assessment](../gradle-plugin/oracle-migration-assessment.md) | Offline preflight and source validation |
| Apply versioned SQL migrations | [Versioned migrations](../gradle-plugin/custom-tasks-and-migrations.md#versioned-migrations) | Gradle migration extension |
| Normalize or load legacy data | [Legacy migration](../gradle-plugin/normalization-and-legacy-migration.md) | Extraction contracts and hierarchy loading |
| Generate operational reports | [Report tasks](../gradle-plugin/migration-reports.md) | Operational and repair-plan reports |

Review generated SQL, database effects, transaction boundaries, and restart
behavior before execution. Try database-changing examples against a disposable
or explicitly authorized database first.
