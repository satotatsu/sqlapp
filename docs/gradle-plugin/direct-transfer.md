# Direct JDBC transfer: one saved job, review or execute

[Plugin index](README.md) · [Task reference](task-reference.md)

`transferDatabase` accepts the common transfer definition and two runtime
connections. It defaults to **review only**: source metadata and target preparation
are checked, and `transfer-review.json` is saved without creating tables or copying rows.
Set `allowExecution=true` and supply the exact configuration fingerprint to copy
and verify. It composes `TransferJobExecutionService`; the task adds no migration
rules. Existing Data Job tasks and formats are unchanged.

## Copy only table definitions, including between different database products

```yaml
formatVersion: 1
source: from
target: to
tables: [PUBLIC.CUSTOMERS, PUBLIC.ORDERS]
dataMode: SCHEMA_ONLY
schemaMode: CREATE_MISSING
# targetSchema: public   # omit to use the target connection's current schema
# verify is false by default in SCHEMA_ONLY; verify: true is rejected.
```

Use the same `transferDatabase` task, runtime connections, review-only default,
explicit execution flag and configuration pin shown below. The GUI creates this
job through **Direct JDBC transfer → New direct job… → Schema only**, or changes
an existing job with **Job settings… → Schema only**. GUI and Gradle share the
same service, Schema model, target-dialect CREATE factories, warning policy and
DDL progress/audit handling. Review the **Schema SQL** and **Warnings** tabs
before confirming execution. No Data Job YAML or copy planner is generated.

This mode creates absent selected tables, constraints and indexes in an existing
target schema. It supports tables without a primary key and does not read row
data, INSERT, verify rows or synchronize generated-key counters. Existing target
tables are skipped with warnings, including nonempty or structurally different
tables; they are not compared, altered or cleared. Include referenced parent
tables from the same source schema. Views, independent sequences, routines,
triggers and other schema-level objects are outside this mode's scope, which is
reported as a compatibility warning. Consequently `compatibilityPolicy: ERROR`
also rejects that scope warning. `WARN` permits trying generated DDL and fixing
vendor differences. Actual SQL failures stop execution and retain completed and
uncertain DDL progress; there is no automatic cleanup or retry.

Schema-only review fingerprints captured Schema bytes, job bytes and the DDL
plan. Editing captured artifacts or changing the planned missing-table set
requires another review. A concurrent database change can still cause SQL failure;
review is not a lock. `Prepared.dataJob` and copy/verification results are null
for this mode, and completed data tasks/committed row progress are empty. Java
consumers should inspect the saved `dataMode` and DDL progress rather than infer
row-copy completion. Existing constructors, default JDBC behavior and format
version 1 remain available; `SCHEMA_ONLY` is an additive enum value. Older
versions cannot consume jobs using it. No module dependency was added.

See [the schema-only example](../examples/direct-transfer/schema-only.yaml).

### Optionally apply differences to existing selected tables

```yaml
formatVersion: 1
source: from
target: to
tables: [PUBLIC.CUSTOMERS, PUBLIC.ORDERS]
dataMode: SCHEMA_ONLY
schemaMode: APPLY_DIFFERENCES
# targetSchema: public
# allowDestructiveSchemaChanges: true  # enable only after reviewing data-impact warnings and SQL
```

`CREATE_MISSING` remains the default GUI choice. For this optional behavior,
enable **Schema only → Apply differences to existing selected tables** in the
new-job dialog or **Job settings…**. GUI and Gradle use the canonical Schema
diff and actual target dialect's SQL factories. Target metadata is captured in
bulk and projected onto selected tables. Missing tables are created; existing
selected tables can receive column, constraint and index changes. Unselected
tables are not deliberately altered or dropped. Database-enforced dependency
effects on referencing objects still require review.
Components are matched by exact name; renames are not inferred from their
positions. Replacing a column with a different name is a removal and addition,
and may discard that column's old values when explicitly authorized. Use a
reviewed custom SQL migration when you need to preserve data through a rename.

Review is read-only and shows CREATE/ALTER/DROP SQL even when execution of
potentially data-impacting changes is disabled. `[SCHEMA_DATA_IMPACT]` warnings
cover removals, narrowing/type/native/generated column changes, physical table
changes, constraint/index removals or replacements, and generated target-data
preparation. Classification is conservative, not proof that other changes are
safe for every application. For example, PostgreSQL can generate an UPDATE to
populate a new NOT NULL column with its default; it remains visible in Schema SQL.
No source row-copy job is generated.

Without `allowDestructiveSchemaChanges: true`, the GUI disables execution for a
plan with data-impact warnings. The shared executor enforces the same gate before
any SQL, including from Gradle or Java. Enable **Allow removals and changes that
may affect existing data**, save, and review again only when intended. Changed
job bytes require a new Gradle configuration pin. The optional boolean defaults
to false and is omitted from generated YAML when false. It is valid only with
SCHEMA_ONLY/APPLY_DIFFERENCES. Existing constructors retain the false default;
new record components/enum values require the updated application. No task
property or module dependency was added.

The plan also fingerprints captured target definitions. Execution captures fresh
source and target metadata and compares exact SQL, warnings and fingerprints.
Definition or permission changes require another review. This does not lock
metadata or fence concurrent writers. Statements may commit individually; a
later failure retains successful statements and an uncertain attempted statement,
without automatic rollback, reset, restoration or retry. Inspect target state
before re-running. Audits retain SQL and warnings; no chunk/row copy results or
source/target row verification are produced for this mode.

Under WARN, unsupported table SQL-generation failures produce
`[SCHEMA_DIFF_UNRESOLVED]` warnings, with no SQL for that table while other
selected-table SQL remains available. Differences with no generated SQL are also
reported. ERROR rejects compatibility warnings. Completing generated SQL is not
proof of full Schema equivalence; inspect unresolved warnings and fix vendor
differences manually. Views, independent sequences, routines, triggers and other
Schema objects remain outside this feature. Use SQL migration planning for a
hand-reviewed script/history or data restoration strategy.

See [the optional-difference example](../examples/direct-transfer/schema-differences.yaml).

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
Common initial-copy jobs explicitly preserve source identity values and NULLs.
Generated per-table bulk options use `keepIdentity: true` and `keepNulls: true`,
so parent keys are not silently regenerated while children retain their original
foreign keys. Low-level Bulk API defaults are unchanged. Existing shared jobs
need no new option; review them again after upgrading. Sequence/identity counter
synchronization for subsequent generated inserts remains a separate concern.
Selected columns modeled as identity or auto-increment now produce a per-column
`[GENERATED_KEY_COUNTER]` warning in review and outcomes, including for
`REQUIRE_EXISTING`. It names the resolved target table/column and states that
counter alignment has not been checked or changed. `WARN` permits transfer;
`ERROR` rejects this unverified condition before copying. This follows the same
shared policy in GUI and Gradle. A target without a generator can treat that
follow-up as not applicable after inspection. The stable code and
`TransferGeneratedKeyChecks.messages` expose this warning category without
changing job/report record layouts. The GUI calls out the pending follow-up even
when copied-row verification matches.

Before application writes, check the target's next generated values against
imported keys, accounting for increment direction, bounds, cycling, caches and
concurrent writers. No automatic MAX+1 restart or probe INSERT/nextval is executed.
Detection uses canonical identity/auto-increment metadata; independent sequences
and vendor default expressions not represented that way are not comprehensively
detected. Existing generic DDL/default/identity compatibility warnings remain.

The bulk job target uses the command's nontransactional connection wrapper:
the chunk executor alone commits/rolls back data and checkpoint transactions.
There is no extra target commit/rollback after autoCommit is restored. Connection
release and owned DataSource cleanup still run, including on a failed borrow.
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
