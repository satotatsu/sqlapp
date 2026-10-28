# Direct JDBC transfer: one saved job, review or execute

[Plugin index](README.md) · [Task reference](task-reference.md)

`transferDatabase` accepts the common transfer definition and two runtime
connections. It defaults to **review only**: source metadata and target preparation
are checked, and `transfer-review.json` is saved without creating tables or copying rows.
Set `allowExecution=true` and supply the exact configuration fingerprint to copy
and verify. It composes `TransferJobExecutionService`; the task adds no migration
rules. Existing Data Job tasks and formats are unchanged.

```yaml
formatVersion: 1
source: from
target: to
tables: [PUBLIC.ITEMS]
schemaMode: REQUIRE_EXISTING
# JDBC_DIRECT and compatibilityPolicy: WARN are defaults.
```

Choose `schemaMode: CREATE_MISSING` to prepare absent selected tables before
copying. `REQUIRE_EXISTING` keeps the existing-target path. The target schema
namespace must already exist. Existing tables must be empty and pass the shared
key/column checks; none are altered, dropped or cleared. Both modes use the same
saved job in the GUI and Gradle.
When the target namespace is missing, preparation reports its requested name
and up to ten existing schemas from JDBC metadata in the selected catalog. Use
the exact name/case in `targetSchema`, or create the namespace with the intended
owner and privileges before reviewing again. Suggestions are diagnostic only;
they do not choose another schema or authorize namespace creation.
Preparation failures expose `TransferJobPreparationService.PreparationException`
(an `IllegalArgumentException`) with `phase`, exception type, available SQLState
and vendor code, and selected library validation text. Source/target connection
opening is identified separately. Raw driver messages, causes and suppressed
cleanup exceptions are omitted. Existing method signatures and configuration
formats are unchanged; this adds structured read-only diagnostics for Java callers.

Review includes the target dialect's ordered CREATE statements and an independent
Schema-plan fingerprint alongside the existing data-plan fingerprint. Execution
captures a fresh source model, regenerates the plan and compares the exact DDL,
warnings and missing-table set before writing. A changed set requires another
review. The shared Schema executor is also used by CSV import. After DDL, the
bulk command revalidates targets and copies/verifies through the existing
executor. Parent/child FK order and target schema remapping are shared with CSV.

DDL can commit independently of data chunks. The outcome retains planned SQL,
successful DDL count, a one-based uncertain attempted statement and warnings.
A failed statement may have taken effect. DDL failure does not start copying;
copy/verification failure retains completed DDL and any committed data progress.
There is no automatic rollback, retry or resume of the aggregate job, nor fencing
of concurrent writers. Review artifacts are not locks.

```groovy
tasks.named('transferDatabase') {
    configurationFile.set(layout.projectDirectory.file('transfer.yaml'))
    sourceDataSource { jdbcUrl.set(providers.environmentVariable('SOURCE_JDBC_URL')) }
    dataSource { jdbcUrl.set(providers.environmentVariable('TARGET_JDBC_URL')) }
    // Configure usernames/passwords and JDBC runtime as in the runnable example.
}
```

| Property | Behavior |
|---|---|
| `configurationFile` | Required `RegularFileProperty`; common transfer YAML |
| `sourceDataSource` | Nested source connection, configured with `sourceDataSource { ... }` |
| `dataSource` | Nested target connection, standard task DataSource settings |
| `allowExecution` | `Property<Boolean>`, default false; false performs review only |
| `expectedConfigurationFingerprint` | Optional for review; required for execution, `sha256:` plus 64 lowercase hexadecimal digits |
| `workspaceDirectory` | `DirectoryProperty`, default `build/sqlapp-transfer/direct`; unique child per review/preparation |

The task disables caching and up-to-date skipping. Offline authorization, format
and fingerprint checks run before pool construction. Runtime drivers/dialects
must cover both endpoints. Execution explicitly authorizes a fresh live review
and requested DDL/copy; the configuration fingerprint does not approve a separately saved
target plan. Use the split Java review/execute API when a separate plan approval
is needed. Review is read-only database access, not an offline operation.

`compatibilityPolicy: WARN` attempts the shared-model transfer even when product
names differ; warnings remain in review and success/failure outcomes. `ERROR`
rejects that mismatch before copying. Target identities and reviewed artifacts
are pinned and checked again, including every subsequently borrowed connection.
Execution also rechecks the warning set and data plan. A real SQL error or
verification mismatch still fails the task. No target is cleared, replaced or
automatically resumed. Already committed chunks can remain after failure.

The review child contains captured source Schema, generated legacy `data-job.yaml`,
the shared job copy and version-1 `transfer-review.json`. Execution writes
`transfer-result.json` with the shared outcome: phase, warnings, copy and
verification results, completed tasks, committed progress, stopped task and
Schema preparation progress. Prepared/Outcome have additive Schema-plan/progress
components; previous Java constructors remain available. Record patterns and
strict JSON consumers must account for the new fields. Audit envelopes remain
version 1 with additive metadata.
Failure outcomes remain available as `internalCommand().result`; an unavailable
`resultFile` signals failed/unavailable publication. Publication and pool cleanup
failures also fail the command; a saved COMPLETE result does not imply cleanup
succeeded. Inspect retained results before retrying. Credentials and JDBC URLs
are not written to the job/review/result. Runtime driver logging follows its own
configuration.

This adapter requires `JDBC_DIRECT` and no explicit `mappingFile`. It does
not claim complete heterogeneous conversion. Embedded tests use HSQL databases,
including different product-label fixtures to exercise WARN/ERROR; actual
heterogeneous servers have not been tested here. The desktop primary direct
screen creates and runs this same format using the shared review/execute service
and `TransferJobReportIO` reports; its integration test runs the GUI-authored job
unchanged through the headless command against a second target. See the
[desktop guide](../../sqlapp-swing/README.md#direct-jdbc-transfer-without-preparing-yaml).

See the [runnable example](../examples/direct-transfer/README.md). The companion
`sqlapp-gradle-example` checkout is outside this task's authorized writable
workspace. The proposed companion change is to add the same `transferDatabase`
configuration, environment bindings and review/execute commands; it was not edited.
