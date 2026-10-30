# Direct JDBC transfer: one saved job, review or execute

[Plugin index](README.md) · [Task reference](task-reference.md)

`transferDatabase` accepts the common transfer definition and two runtime
connections. It defaults to **review only**: source metadata and target preparation
are checked, and `transfer-review.json` is saved without creating tables or copying rows.
Set `allowExecution=true` and supply the exact configuration fingerprint to copy
and verify. It composes `TransferJobExecutionService`; the task adds no migration
rules. Existing Data Job tasks and formats are unchanged.

## Copy only table definitions, including between different database products

In the GUI table picker, **Include referenced tables** can expand a selection to
its foreign-key parents using only the pinned captured Schema. Review the added
selection before saving the shared YAML; the action neither saves nor executes
a job. A missing, ambiguous or ineligible parent leaves the selection unchanged.
Schema-only mode permits keyless definitions, while data-copy requirements and
the shared planner's cycle/order checks remain in force. References outside the
captured Schema require another capture or explicit configuration, not a live
database lookup from this helper.

Java callers can use
`BulkMigrationJobDraftBuilder.includeReferencedTables(snapshot, selection)`
to obtain the same expanded canonical table IDs, then pass them to the existing
draft/save API. The helper does not infer data fingerprints, eligibility or
execution order.

For missing-table creation, repeated constraint or index names across selected source
tables (for example Access `PrimaryKey`) receive distinct, stable target names
derived from their table and object identity. Constraint columns and rules, index
columns, ordering and uniqueness remain
unchanged and each adjustment is a compatibility warning; `ERROR` policy rejects
it. Source Schema metadata is retained. This normalization is limited to CREATE
planning and does not rename objects during optional difference application.

These warnings carry the stable `[SCHEMA_NAME_ADJUSTED]` code in shared review
and execution reports and generated SQL drafts. The GUI highlights their count
and categorizes them as **Target / Object names**. Compare original and target
names in each message and inspect scripts/application references to object names.
The code is planning evidence, not confirmation that DDL was executed. Existing
configuration and report shapes remain unchanged; review again after updating,
because coded warning text participates in Schema-plan fingerprints. Older
uncoded report messages are retained as generic compatibility warnings.

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

### INSERT tables without a usable primary key

Direct JDBC initial copy now accepts tables without a complete NOT NULL primary
key. The table picker shows a warning and permits selecting them. The generated
Data Job automatically uses `fullScan: true`, `mode: INSERT`, `resume: false` and
`requireEmptyTarget: true`; no source key or data fingerprint is invented.
Existing keyed tables retain their JDBC keyset path. Parent-first ordering,
target checks, chunk transactions and retained partial-progress evidence are shared.

Full scan opens one JDBC row stream and preserves duplicates and NULLs. It cannot
MERGE/UPSERT, resume, use an incremental strategy or produce key-based repair.
Committed chunks can remain after failure; inspect/repair the target before a new
attempt. There is no automatic truncate, cleanup or replay. A full scan does not
implicitly lock or snapshot a changing source database.

Verification compares the multiset of canonical row hashes, including duplicate
multiplicity, independently of source/target row order. Bounded hash runs are
sorted through temporary files containing only SHA-256 hashes, with at most 32
merge readers; all runs are removed on success or failure. Result chunk hashes use
`multiset-sha256-v1:`. Their indexes identify hash-sort positions, not source row
positions or resumable keys. They are rejected by key-based repair. This uses
cryptographic hash comparison, not row-by-row equality without hash assumptions.

`[KEYLESS_INSERT]` records the read/verification strategy and target-key guidance.
Warnings permit execution by default; `compatibilityPolicy: ERROR` rejects them.
This increment applies to direct JDBC jobs and explicit advanced full-scan INSERT
Data Jobs. CSV package eligibility retains its existing key/type requirements.

### Optionally add a generated primary key to new keyless target tables

```yaml
formatVersion: 1
source: from
target: to
tables: [T_order]
schemaMode: CREATE_MISSING_WITH_IDENTITY
# dataMode: JDBC_DIRECT   # default
```

In **New direct job…** or **Job settings…**, enable missing-table creation, then
**Add target-only generated PK to new tables without a primary key**. The same
saved job works with Gradle. This mode creates an additional `SQLAPP_ROW_ID`
BIGINT identity column and stable named PK through the target dialect's shared
Schema factories, only when a newly created table has no modeled PK. Existing
source PKs are preserved. Existing target tables are not altered to add this key.
Schema-only jobs may also select this mode; FILE jobs reject it explicitly.

The source file/database and FK mappings are retained. The new column is omitted
from INSERT and source-value verification, so the target generates its values.
It does not become a source/business key or enable MERGE/resume. Review the
`[TARGET_GENERATED_PK]` warning and actual Schema SQL; dialect identity support
still requires testing. If a source/mapped column already uses `SQLAPP_ROW_ID`,
review fails before DDL; prepare a compatible target with another generated key
and use `REQUIRE_EXISTING` instead. Strict warning policy rejects the warning.

Existing job/result record shapes and constructors remain available. The new
SchemaMode value and low-level `fullScan` property are additive; older versions
cannot execute these new modes. Generic advanced draft creation keeps its key
requirements; Java callers may explicitly use `BulkMigrationJobDraftBuilder.draftInsert`.
See [the keyless INSERT example](../examples/direct-transfer/keyless-insert.yaml).

<a id="optionally-map-table-and-column-names-schema-only"></a>

### Optionally map table and column names

Add `mappingFile: maps/names.yaml` to a `SCHEMA_ONLY` or `JDBC_DIRECT` job, or choose/drop that
file in **Job settings… → Name mapping YAML**. Relative paths resolve from the
original saved job folder. The GUI's Gradle handoff uses that same original job.
The optional UTF-8 mapping file is limited to 1 MiB:

```yaml
formatVersion: 1
tables:
  PUBLIC.CUSTOMERS:
    targetTable: customers_copy
    columns:
      ID: customer_id
  PUBLIC.ORDERS:
    targetTable: orders_copy
    columns:
      CUSTOMER_ID: customer_id
```

Keys identify source tables and source columns. Omitted names remain unchanged;
`targetSchema` in the job controls the target namespace. Japanese names and spaces
are retained. Source table identities must resolve uniquely within the captured
Schema, and every mapping must refer to a selected table. Include all referenced
parents from the same source schema. Unknown fields/columns, duplicate rules and
case-insensitive target-name collisions fail explicitly before execution.

The common Schema projection updates PK/unique/FK columns and index/include
columns together, retaining composite-key order and index options. It leaves the
original Schema XML unchanged. Name mappings support both `CREATE_MISSING` and
`APPLY_DIFFERENCES`; differences compare the desired mapped names with the target,
and do **not** infer an ALTER RENAME. Existing destructive-change guards still
apply. Types, values and SQL expressions are not transformed. When names change,
`[SCHEMA_MAPPING_EXPRESSIONS]` warns that checks, defaults, generated expressions
and predicates remain verbatim; inspect the generated SQL. `ERROR` policy rejects
this warning, while `WARN` permits trying the DDL and refining the configuration.

Review artifacts retain the mapping bytes as `transfer-mapping.yaml`. Both the
original mapping and captured copy are fingerprinted and checked before execution;
even a comment edit requires another review. Keep the original job and mapping
together for GUI/Gradle reuse. The private captured job is audit evidence, not a
relocated standalone configuration. Existing job format/version and public result
record shapes are unchanged; `TransferNameMappingIO` is an additive Java API.
Across separate Gradle invocations, the configuration pin covers job bytes;
execution performs its own fresh review and does not replay an earlier report.
Inspect a new review after changing the mapping. See the
[mapped job example](../examples/direct-transfer/schema-only-mapped.yaml).
For `JDBC_DIRECT`, the same mapping is resolved into the generated bulk job's
`targetTable` and `columnMappings`. Source queries and keyset order retain original
identifiers; INSERT, empty-target/PK checks and post-copy verification use mapped
target identifiers. Keyed-read requirements, identity/NULL preservation,
transaction chunks, parent-first ordering and no automatic replay are unchanged.
Portable JDBC batches bind supplied audit timestamps (`created_at`, `updated_at`)
and version counters (`lock_version`, `version_no`) verbatim, including NULLs,
instead of applying normal CRUD-generated values. This also keeps SQL placeholders
aligned with bound copy columns. Ordinary CRUD generation retains its conventions.
Use `CREATE_MISSING` to prepare absent mapped tables, or `REQUIRE_EXISTING` to copy
into already compatible empty mapped targets. Source expressions are unused when
no DDL is prepared; name changes alone do not produce an expression warning in
`REQUIRE_EXISTING` mode. Data transfer does not apply Schema differences.

The generated data job pins the mapping's raw-byte fingerprint in a YAML comment;
its own byte fingerprint is checked during revalidation and before copy. Original
and captured mappings must still match that pin, including comment-only edits.
The bulk execution plan fingerprint retains its existing semantics. See the
[direct copy example](../examples/direct-transfer/direct-mapped.yaml).
CSV export/import still reject `mappingFile` explicitly.
In **Job settings… → Edit names…**, edit target table names in the Tables tab
and source-to-target column names in the Columns tab without writing YAML.
A new mapping starts with the job's selected source table identities. Blank
target table names and unlisted columns keep their original names. Column rows
require both names; remove a row to remove its override. Names retain case,
Unicode and spaces.

To avoid entering source column names, choose or drop a captured **Source Schema
XML** and click **Load columns**. The suggested file is `source-schema.xml` beside
the saved job, as created by the new-job workflow. Loading resolves the tables
currently listed in the editor through the common job resolver; qualify ambiguous
names. It appends missing columns with their source names as defaults, shows
informational source model types, and retains existing target names and overrides.
Repeated loading does not duplicate columns. Same-name column entries are omitted
from the saved mapping; removing a column row removes its name override, not the
column from the transfer. Existing overrides absent from the snapshot remain
visible for correction during review.

The reader accepts bounded metadata-only Schema XML, rejects DTDs and row data,
and checks that the inspected file has not changed while resolving columns.
This operation accesses no database and does not establish that the snapshot is
current. Review the saved job against the databases before execution. Target types
cannot be edited here.

Both editor tabs support literal, case-insensitive **Find**, sortable column
headers and **Renamed only**. Counts distinguish visible rows, all editor rows
and renamed rows. Filtering and sorting never change which mappings are saved.
**Reset selected names** restores the source names for selected rows while
retaining column metadata and other overrides. **Remove selected overrides**
removes only those editor rows, even after filtering/sorting; it does not exclude
tables or columns from the transfer. Use the job's table selection to exclude
tables. **Add row** clears filters so the new editable row is visible. Clear the
filter to inspect unchanged columns again.

**Check names** lists input problems together in the **Input checks** tab. It
uses the shared name rules for blank/invalid identifiers and reports repeated
source table/column entries. Choose **Go to selected field**, double-click a
problem or press Enter to clear the relevant filters and edit the field, even
after sorting or moving columns. Editing invalidates the old problem locations;
check again to refresh them. Saving also runs these checks before writing a file.
The result is syntax feedback only: aliases, target collisions, missing database
objects and relationship consistency still require the ordinary database Review.

**Preview YAML** opens a read-only, syntax-highlighted view of the generated
mapping using the same formatter as **Save mapping…**. Search and line navigation
are available, and **Copy YAML** copies the whole generated mapping, including
overrides hidden by table/column filters. The preview does not write a file or
run a transfer. Editing names clears the old preview and disables copying until
**Refresh preview** regenerates it. Invalid input opens Input checks instead of
leaving an old valid preview available. Copy failures can be retried. Save the
mapping and job settings, then perform the normal database Review before running.

Closing the name editor with **Close**, the window close button or Escape checks
for unsaved mapping changes. Choose **Keep editing** to retain them or explicitly
discard to close. Filters, sorting, input checks, previews and loading unchanged
source columns alone do not count as name changes. Invalid in-progress edits are
also protected. The editor shows the current mapping file (or a new/unsaved label).

When a mapping is saved from this editor, Job settings fills its reference relative
to the job's folder when both paths have the same root; another drive uses an
absolute path. Generated relative references use forward slashes on Windows too.
Browse resolves relative references from the job folder too.
Save job settings to retain the reference. Moving a job together with its mapping
directory preserves these relative references; completed review artifacts still
need a new Review in the new location. Canceling Job settings does not undo an
already saved mapping file.

**Save mapping…** writes the mapping immediately; **Save settings** then attaches
its path to the job. Canceling job settings does not undo a mapping already saved.
Existing mappings use fingerprint-checked writes; other existing destination
files are never overwritten. Saving rewrites YAML comments and formatting.
Review again to validate source identities, collisions and FK consistency against
the Schema before executing. The same saved job and mapping work with Gradle.
Target-type overrides and value conversion remain future work.

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

### Manual Schema SQL drafts

The Direct JDBC GUI can save its displayed **Schema SQL** draft and open it in
the SQL editor. The draft uses the actual target delimiter policy and retains
warnings and job/plan fingerprints. Result drafts additionally identify execution
progress and include already-applied SQL; they are not automatic retry scripts.
Manual editing/execution is separate from `transferDatabase` and does not change
or authorize the shared job. Review any versioned SQL migration before applying it.

Java callers can opt into `TransferJobExecutionService.reviewWithSqlDraft`, whose
`SqlReview` contains the ordinary `review` plus nullable `schemaSqlDraft` (null
when no Schema SQL exists). `TransferSchemaSqlScript.render` provides the same
serialization for a plan and an explicit target dialect. The existing `review`,
`Prepared`/`Review` record components, execution API, configuration and audit JSON
formats are unchanged. The optional draft is captured while the target dialect
is known; saving it needs no new database access. A fresh review is required for
GUI export. No Gradle task property or module dependency was added.

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

This adapter accepts `JDBC_DIRECT` or `SCHEMA_ONLY`, including optional `mappingFile`.
It does
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
