# Direct JDBC transfer: one saved job, review or execute

[Plugin index](README.md) · [Task reference](task-reference.md)

`transferDatabase` accepts the common transfer definition and two runtime
connections. It defaults to **review only**: source metadata and target preparation
are checked, and `transfer-review.json` is saved without creating tables or copying rows.
Set `allowExecution=true` and supply the exact configuration fingerprint to copy
and verify. It composes `TransferJobExecutionService`; the task adds no migration
rules. Existing Data Job tasks and formats are unchanged.

## Insert transport

Optional `insertMethod` selects how each INSERT chunk is written:

| Value | Behavior |
| --- | --- |
| `AUTO` (omitted) | Prefer the registered database provider; use portable JDBC batches when no provider exists. |
| `NATIVE` | Require a registered database provider; fail if none is available. |
| `JDBC_BATCH` | Use `PreparedStatement.addBatch()` / `executeBatch()` with shared SQL factories and the parameter binder. |

Add `insertMethod: JDBC_BATCH` to the same job used by the GUI and
`transferDatabase`. PostgreSQL's database provider streams COPY FROM STDIN;
`JDBC_BATCH` explicitly selects the portable alternative. The selection is part
of the reviewed plan fingerprint. Changing it requires a fresh review. Chunk
commit, verification, NULL values and identity preservation retain their existing
semantics. This option does not enable deleting target rows or merging them.
Schema-only jobs reject the option because no data is copied.

GUI **Job settings → Insert method (advanced)** edits this field. File-package
jobs keep it under **Optional import settings**: export captures it without
opening a target connection, and import applies the captured choice.

Existing YAML may omit the field, and the prior Java constructors remain
available. `TransferJobConfiguration` adds an `insertMethod` record component;
Java record deconstruction patterns must include the additional component.

## Review target preparation per table

After a direct, schema-only or file-package import review, the GUI's **Table
preparation** tab lists the resolved target catalog/schema/table and its shared
planner action: create a missing table, use an existing table for data transfer,
skip an existing table without comparison, or review/apply selected-table
differences. Search by task, target name or action and select a row for details.
An existing difference target is not a claim that SQL is needed or that all
differences can be generated. Inspect **Warnings** and **Schema SQL** as well.

Select a table with recorded SQL and choose **Show table SQL** to open its exact
task in **Schema statements**. The task selector also works directly in that
tab. Text search applies within the chosen task; **Clear** clears the search,
while **All tasks** restores the complete SQL list. Original statement order
numbers and recorded completion/uncertain-attempt states remain unchanged.
Tasks with no SQL cannot navigate through this button; warnings may still
require manual work. Filtering is a display aid, not an executable subset or a
resume plan: dependencies and session operations can span tasks.

New difference plans retain each generating table's task ID instead of assigning
every statement the generic `schema-difference` ID. The SQL, validation and
execution order are unchanged. This association participates in the existing
plan fingerprint, so review again before execution. Older reports retain their
recorded IDs and remain readable; they are not rewritten or guessed from SQL.

These are recorded review decisions, not execution statuses. Direct execution
results retain the reviewed intent; **Schema statements** contains recorded SQL
progress. File import outcomes retain SQL progress separately. Editing inputs or
a failed action clears the old table view. No extra metadata queries are issued
to populate the view, and no automatic row count or SQL parsing is involved.

The same command plan is available to GUI and Gradle as
`schemaPreparation.tables` in the review report. Existing Java `Plan`
constructors remain available and produce an empty table-evidence list. The new
evidence participates in the preparation fingerprint; create a fresh review
before execution. Job YAML and Gradle task properties are unchanged.

## Change tables in a saved job

Open **Job settings… → Edit tables…**, choose/drop the captured source Schema XML
(default: `source-schema.xml` beside the job), then **Read Schema**. Existing table
identities are resolved and preselected. Use Include checkboxes, literal filtering,
**Select visible eligible**, **Clear visible**, or **Include referenced tables**.
Hidden selections remain included. **Use selected tables** rechecks the Schema
fingerprint and returns the canonical selection to the settings form without
writing the job or accessing a database. **Save settings** persists it; review
the changed job before executing. Canceling the form leaves the saved job intact.

Direct JDBC allows keyless INSERT with the existing warnings; Schema-only selection
does not require copy keys. CSV retains its key/type restrictions. Unavailable
saved selections remain visible and can be removed; they cannot silently become
eligible. If a saved identity is missing from the captured Schema, choose the
matching capture instead of silently discarding it. Changing the Schema path
invalidates the old chooser and retains selected names for the next read.

The selected tables change independently of target schema, chunk size, verification,
compatibility policy, generated-key name and other saved settings. Excluding a table
does not drop it or delete data. If `mappingFile` includes a removed table, use
**Edit names… → Review scope…** to review and remove its table/column override group;
the ordinary scope checks still apply.
CSV selection changes apply to the original job and require a new export; completed
packages retain their original selection and remain protected from editing.

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
saved job works with Gradle. By default this mode creates an additional `ID`
BIGINT identity column and stable named PK through the target dialect's shared
Schema factories, only when a newly created table has no modeled PK. Existing
source PKs are preserved. Existing target tables are not altered to add this key.
Schema-only jobs may also select this mode; FILE jobs reject it explicitly.

The source file/database and FK mappings are retained. The new column is omitted
from INSERT and source-value verification, so the target generates its values.
It does not become a source/business key or enable MERGE/resume. Review the
`[TARGET_GENERATED_PK]` warning and actual Schema SQL; dialect identity support
still requires testing. Change **Generated PK column** in either GUI form, or set
`generatedKeyColumn: ROW_ID` in YAML, to choose another name (Unicode names are
supported). The option is valid only with `CREATE_MISSING_WITH_IDENTITY`.
If a source/mapped column already uses that name (case-insensitive check), review
fails before DDL with guidance to choose an unused name. A compatible manually
prepared target can also use `REQUIRE_EXISTING`. Strict warning policy rejects
the generated-key warning.

Omitting `generatedKeyColumn` now means `ID`, including existing jobs in this mode.
To retain the earlier generated name, set `generatedKeyColumn: SQLAPP_ROW_ID`.
Existing target tables are not renamed or altered by this option. Review saved
jobs again after changing the name or upgrading from the earlier default.

Existing job constructors and result record shapes remain available. The optional
`generatedKeyColumn` adds a job record component; older readers reject this field.
The new
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
For an optional offline column-definition preview, choose **Optional target
preview** and enter its explicit product version, then use **Check Schema**.
The result places the shared target SQL builder's definition beside the source
model type and mapped column name, with source PK/NULL metadata and per-column
preview issues. No target selection is required for ordinary name checks.
Changing product/version invalidates the displayed result. Naming-rule Schema
checks use the same selected preview dialect.

This is a builder preview, not live target metadata or the final table-factory
CREATE/ALTER SQL. Table factories can add or change syntax. Existing target
columns, target-only generated keys, value conversion and full-data verification
are outside this view. A preview limitation remains visible without discarding
source metadata; inspect full Schema SQL and warnings in database Review.
Preview controls are session-only and do not alter mapping YAML, job settings,
Gradle behavior or the source/mapping fingerprints. The result records the resolved
builder class separately. Java callers may pass a target `Dialect` to the
`TransferNameMappingInspectionService.inspect(snapshot, selected, mapping, dialect)`
overload; existing methods and result constructors remain supported.

In the name editor's **Schema check** tab, select one column and choose **Edit
selected column name** to return to its target-name cell. The common Schema
projection retains the validated original mapping source key, so qualified and
short table aliases are not guessed by the GUI. Existing editor values remain
unchanged; a column not yet loaded is added as an unchanged display row with its
captured source type. Editing still requires saving the mapping/settings and a
fresh database Review. The navigation button is unavailable in standalone
read-only naming-rule previews, without a single selection, or after invalidation.

Unsupported builder previews retain a bounded, single-line reason as well as the
exception category. For example, a PostgreSQL generated-column preview can explain
that version 12 or later is required. Other column metadata stays available.
This does not turn a preview issue into execution approval or change compatibility
policy. Inspection results add the original `mappingSource` key per table;
the existing three-argument `Table` constructor remains supported.

Schema check also provides an exact **Source → target table** selector for
multi-table results and **Preview issues only** for recorded builder limitations.
Combine them with literal text search to inspect a small set of columns. Preview
availability counts cover the full result; filtered row counts cover the visible
rows. Clearing search retains the chosen scopes, while a new check or invalidated
result resets them. Copy and column-name navigation use the filtered view without
changing saved mappings, selected transfer tables, compatibility policy or any
execution permission. General compatibility warnings remain separate.

For rows with a preview issue, selected-row details put the recorded reason and
target definition first, retaining every remaining field afterward. Ordinary
rows, SQL order and table-copy formats are unchanged. **Copy displayed details**
copies that plain text for inspection or sharing; **Copy selected rows** remains
the table copy for multiple rows. Details copying is bounded to 1 MiB UTF-8,
reports failures without partial output, and uses the current selection. Nothing
is executed or written to the saved job by these display/copy actions.

Use **Previous issue** / **Next issue** in Schema check to inspect recorded
preview limitations one by one. Navigation follows the current sorted, filtered
view and wraps at the ends, leaving table/search/issue filters intact. Its
position label refers to visible issues rather than the full result. No matches
disable navigation; no-preview, clean, busy or invalidated results cannot step
through old evidence. From a multi-selection it uses the first selected row as
the starting point and selects one issue. Navigation does not run SQL, grant
execution approval or change the saved job. Existing edit/copy actions work on
the newly selected row.

After **Check Schema**, **Save preview report…** creates a portable HTML snapshot.
Its save dialog defaults to **All checked columns** and also offers **Current
filtered view**, preserving visible sort order. Included/total counts and the
scope are explicit, including an empty filtered view. Full captured warnings
remain separate from included column rows. The report retains source Schema and
canonical mapping fingerprints, the resolved preview builder and original mapping
keys, and escapes metadata/default expressions as text. It includes the source
file name without its absolute path, and uses no external assets or scripts.

Saving this report does not save name proposals, change the shared job or authorize
execution. It records the checked snapshot, not live target state. Existing files
and source/job/mapping paths are rejected as destinations; each report is a new
UTF-8 `.html`/`.htm` file, bounded to 16 MiB. Writes use the existing atomic-file
helper and optimistic destination checks, not a cross-process lock.

Java callers can use `TransferMappingPreviewReportIO.writeNew(path, inspection)`
for the full report, or supply exact `ColumnRef(sourceTable, sourceColumn)` values
and extra protected input paths for a subset. `render` provides the same HTML
without file IO. Unknown, duplicate or ambiguous column identities fail before
writing. No Gradle task properties or job YAML fields are added.
Use **Source table** in Columns to focus on one exact source identity, or choose
**All tables** to see every mapping. Double-click a source-table cell in Tables
to open that table's columns. Find and Renamed only work within the chosen table;
Clear filter keeps that table selected. New column rows inherit the selected
source table. These display controls do not exclude tables or columns from the job.
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

For bulk naming changes, select rows in Tables or Columns and choose **Naming
rules…**. Keep, uppercase or lowercase the current target names and optionally
replace Unicode spaces with underscores. Case conversion uses `Locale.ROOT`;
Japanese and other uncased characters are retained. A blank table target starts
from the final table-name component of its source identity; the job's target
schema is unchanged. Column rules use the current target column names. Load
source columns first if you want to change columns not yet listed in the editor.

**Preview selected names** shows current/proposed names without editing the
mapping. Only **Apply preview** changes selected rows, including after sorting or
filtering; hidden/unselected overrides stay intact. Changing rules requires a new
preview. Preview and undo check the affected rows before changing any of them,
so stale previews cannot overwrite later edits or apply only part of a batch.
**Undo last rules** restores the last naming batch for that tab if those rows
still match. Cancel, Escape or the window close button leaves previews unapplied.
Check Schema for collisions and FK consistency, then perform database Review;
rules do not invent unique suffixes, change types or transform row values.

From this preview, **Check proposed Schema** resolves the proposed mapping using
the Source Schema XML chosen in the parent name editor and the saved job's table
selection. It includes hidden/unselected overrides when checking for collisions
and uses the same shared projection as execution. The **Schema check** tab shows
resolved names, source PK/NULL/type metadata, warnings and fingerprints before
the names have been applied to editor cells. The editor and files remain unchanged.

This check runs in the background. Editing rules invalidates old results, and late
results cannot replace newer previews. Controls and closing are disabled during
the bounded metadata read. A failed check is shown with its reason; you may still
apply the names as an unfinished draft and correct them in the name editor before
normal database Review. This preserves try/inspect/refine behavior and does not
authorize execution, infer target objects or bypass the job's compatibility policy.

After editing the job's table selection, **Review scope…** in the parent name
editor resolves mapping source identities against the captured Schema and lists
groups outside the current selection. The list shows original/resolved source
names, target names and column override counts, together with source/mapping
fingerprints. Only **Remove listed groups** removes complete table and column
groups from the editor, including displayed unchanged columns; retained groups
and loaded source types stay intact. Cancel, Escape or closing the preview makes
no change. Editing after scope review invalidates the removal proposal before any
rows are removed.

Unknown source names and duplicate resolved aliases are reported instead of
being guessed away. Scope review does not validate retained column rules, target
collisions or FKs; run Check Schema and normal database Review afterward. The
operation writes no files and deletes no database tables or row data. Save the
mapping and settings to retain the editor change. The command module exposes the
same offline authoring preview through `TransferNameMappingScopeService.review`;
execution retains its existing out-of-scope validation and mapping format.
The mapping fingerprint here identifies the formatted editor configuration;
normal saves still check the original file's raw-byte fingerprint separately.

Resolved names are saved in the existing mapping YAML; no runtime/global rule
settings are introduced. GUI and Gradle therefore use the same explicit mapping.
The command module also exposes pure `TransferNameRules.apply(name)` authoring
with the shared Unicode/name validation contract.

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

To check names against the captured model, choose **Source Schema XML → Check
Schema**. The **Schema check** tab shows resolved source-to-target table/column
names, source logical types, source PK participation and NULL allowance. It uses
the same Schema projection as execution: unknown columns, mappings outside the
saved job selection, duplicate resolved identities, target-name collisions and
missing referenced parents are reported before any database connection. Atomic
column-name swaps retain the ordinary execution semantics. Existing expression
warnings are retained because opaque expressions cannot be rewritten reliably.

The check reads bounded metadata-only XML and rechecks the inspected fingerprint;
it writes no files and reads no row data. Evidence includes the source file and
fingerprint plus a fingerprint of the formatted mapping. Editing names or the
Schema path clears the displayed result. External changes to the file require
another check; this is a snapshot, not a live database check or execution approval.
Current target objects, target types, generated target-only keys, compatibility
policy and actual DDL remain part of normal database Review. The check does not
prevent saving an unfinished mapping for later correction.

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

## Troubleshooting Access to PostgreSQL floating-point DDL

Access `DOUBLE` columns are generated as PostgreSQL `DOUBLE PRECISION`, using the
target dialect rather than the source type spelling. If an earlier run failed on
`DOUBLE DEFAULT 0`, use the corrected build and review the saved job again. A
recorded **Uncertain attempt** means that statement's completion was not confirmed;
inspect retained Schema progress and target tables before retrying. The tool does
not automatically drop partially created objects or replay a failed transfer.

**Open saved preview** opens the last successfully saved HTML report after an
asynchronous fingerprint check. Missing or modified files are reported without
opening them; save a new report and retry. Current name edits remain separate
from the saved snapshot. When browser integration is unavailable, open the path
shown in the message manually. The additive command API
`TransferMappingPreviewReportIO.verifySaved(Saved)` performs the same bounded
byte check; it does not lock files or validate the database and grants no
execution approval. Job YAML and Gradle task configuration are unchanged.

## Execution failure diagnostics

Direct transfer outcomes now include optional `failureDiagnostic` evidence. The
same diagnostic appears in the GUI Summary, the execution exception and
`transfer-result.json` when the command can write the audit. It records a
self-authored cause category, a validated SQLState and a numeric vendor code,
without retaining raw driver messages, values, URLs or exception causes.

Known SQLStates distinguish unique/FK/NOT NULL/CHECK constraints, conversion,
permissions, authentication, connection, database/schema selection, SQL/object
resolution, unsupported operations, transaction failures and timeout/cancellation.
Batch wrappers and chained SQL exceptions are searched with a bounded,
cycle-safe traversal; a specific constraint code takes precedence over a generic
constraint wrapper. Vendor codes remain available even when no SQLState is usable.
Unknown causes are reported as unknown, rather than inferred from driver text.
A recorded verification mismatch has its own diagnostic and directs users to
mismatched tables/chunks and target precision.

Summary guidance does not identify a failed source row or prove rollback. Retain
completed/uncertain DDL, committed progress and verification evidence when fixing
the target; this information does not authorize retry or resume. Successful
outcomes and legacy Java constructors have no failure diagnostic. Existing
constructors/accessors, YAML keys and Gradle task properties remain available;
the optional component is an additive change to the Java outcome record and JSON
shape. Java record patterns matching the complete outcome must include it.

No companion example checkout was modified. Existing examples need no property
changes; after a failed authorized run, inspect `outcome.failureDiagnostic` in
the saved result alongside `phase` and `committedProgress`.

## GUI inspection of recorded verification

The GUI's Verification tab presents the same task comparisons retained by direct
execution, including partial failure outcomes. It lists reviewed target names,
counts and mismatches; Show chunks opens a selected task's comparisons in pages
of 200, with a mismatches-only filter. Searching/sorting/copying is page-scoped.
This does not rerun database queries or change job/task settings. Multiset-hash
positions are not source row numbers. See the
[GUI guide](../../sqlapp-swing/README.md#inspect-recorded-verification-by-table-and-chunk).

## GUI view of retained task progress

Execution progress combines the direct result's task IDs, completion events,
returned copy results, last committed chunk records, Schema SQL progress and
verification. It distinguishes missing evidence from zero/never-started tasks.
Stopped tasks, uncertain SQL and mismatches can be filtered and opened in the
existing task SQL/verification views. Schema-only runs show copy as not requested.
This is a read-only GUI view of the same retained result; no result/YAML/task
property changes or extra database queries are introduced. See the
[GUI guide](../../sqlapp-swing/README.md#inspect-retained-execution-progress).

## GUI warning filters

Warnings can be filtered by recorded category and structured task ID as well as
literal message search. Selector counts cover all retained records before
filtering; Show all warnings clears category, task and text filters. Messages
without an explicit task ID stay unassigned, preserving the original evidence.
This is a GUI-only display improvement; policies, saved jobs and task properties
are unchanged. See the
[GUI guide](../../sqlapp-swing/README.md#filter-warnings-and-retain-package-context-after-import).

### Replace existing rows after staging

Direct JDBC jobs stop at a nonempty target by default. To opt into full
replacement, enable **Stage and verify data before replacing existing target
rows** in Direct JDBC transfer → Job settings. The GUI and the Gradle
`transferDatabase` task use the same YAML option:

```yaml
targetPreparation: STAGED_REPLACE
verify: true
```

The job copies into ordinary staging tables in the target schema and verifies
the copy. The final step locks the selected tables, deletes old rows in
child-first order, inserts verified rows in parent-first order and verifies
the result before committing one transaction. A failure before commit rolls
back the final changes if rollback succeeds. Review and result reports
identify the staging tables and distinguish a rollback from an uncertain
commit. If the commit acknowledgment is lost, a matching verification result
describes rows seen inside the transaction. The result records
`COMMIT_UNCERTAIN`, retains the staging tables and omits committed row counts.
In the same GUI session, enter the target password if needed and select
**Compare retained staging**. The comparison reads the retained staging and
the current target in one read-only snapshot, with no source connection.
For each table it shows the row counts, whether the copied columns match,
and whether staging still matches the verification recorded before commit.
A match describes the current data. The recorded commit outcome stays
uncertain and the staging tables remain available.

After restarting the GUI, enter the original target connection and choose
**Compare saved staging…**, or drop `transfer-result.json` onto that button.
Keep the result with
`transfer-review.json`, `transfer.yaml`, `data-job.yaml` and
`source-schema.xml` in the same folder. The files can move together.
The comparison checks the captured fingerprints and target identity.
The source connection and the original job file are unnecessary. Each JSON
report can be up to 32 MiB. Java callers can use
`TransferStagedReplacement.compareRetainedStage` with either the saved result
path or the in-memory review and outcome, plus the target DataSource.

When the database rejects an individual replacement operation, the result identifies
the target task that stopped. The job rechecks target columns and foreign-key
scope before staging and again before the final DELETE.

Existing staging tables are never reused. Stages are dropped after
a successful commit and are retained for inspection after a failed run.
Newly created empty target tables can remain after rollback.

Supported targets are PostgreSQL and HSQLDB 2.x in LOCKS or MVLOCKS mode.
This mode requires privileges to create and drop staging tables, lock the
selected targets, and read, delete and insert their rows. The job stops if
an unselected table references a selected target or if the selected
relationships cannot be ordered. PostgreSQL requires ordinary standalone
tables without row-level security, rewrite rules or user triggers. HSQLDB
requires tables without user triggers. Sequence allocations can advance
even if row changes roll back. Prepare a stable source before the transfer.
Use the normal `insertMethod` setting to select the PostgreSQL COPY provider
or JDBC batch. The staged SQL shown in the GUI is a review outline.

`STAGED_REPLACE` applies to verified JDBC and [file transfers](file-transfer.md#replace-existing-rows-after-staging).
This mode uses transactional DELETE and INSERT. Existing
transfer YAML and constructors remain valid. Java record patterns for
`TransferJobConfiguration`, `Review` and `Outcome` must account for the added
components.

#### Compare retained staging with Gradle

Configure `compareTransferStaging` with the saved `transfer-result.json` and the
original target connection. Use the same companion files as in the GUI.

```groovy
tasks.named('compareTransferStaging') {
    resultFile.set(layout.projectDirectory.file('reviewed-run/transfer-result.json'))
    dataSource {
        jdbcUrl.set(providers.environmentVariable('TRANSFER_TARGET_URL'))
        username.set(providers.environmentVariable('TRANSFER_TARGET_USER'))
        password.set(providers.environmentVariable('TRANSFER_TARGET_PASSWORD'))
    }
    // Optional: fail the build when target data differs or retained staging has changed.
    failOnDifference.set(true)
}
```

Run `./gradlew compareTransferStaging`. Each comparison writes a separate
`staging-comparison.json` under `build/sqlapp-transfer/compare` by default.
The report records the input result fingerprint, target identity and per-table
counts and comparisons. A difference is reported without failing the task by
default. With `failOnDifference: true` the task writes the comparison report
before failing. The saved transfer result remains `COMMIT_UNCERTAIN`.

In the GUI's **Migration reports** tab, select or drop `staging-comparison.json`
and choose **Detect and load**. The table shows both row counts
and separates differences in the target from changes to the retained staging.
The **Catalog** and **Task ID** columns distinguish same-named tables in
different catalogs and link each row to its original transfer task. Both can
be searched with the table filter.
Use **Show** to select all tables, target differences, staging changes, or
tables with both kinds of difference. This selection combines with the text
filter and limits the rows copied by **Copy visible rows**. Loading another
report resets the selection to all tables.
Use **Save visible CSV…** on a report table to export the current filtered rows
and displayed column order with headers as UTF-8 CSV. Select a destination
folder; each save creates `report-table-*/visible-rows.csv`. Hover over the
saved status to see the full path, or use **Open saved folder** to open the
last successful snapshot's folder. If opening is unavailable, the saved file
and its path remain available for manual access and retry. This is a snapshot of report rows for
investigation and sharing. The original report is preserved. A snapshot above
16 MiB requires filtering fewer rows before saving.
When differences exist, the **Differences** tab lists just those tables.
Viewing the report uses the saved snapshot and requires no database connection.
Direct JDBC comparisons also use this table view, including filtering, numeric
row-count sorting and the **Differences** tab. Changing connection or job inputs
removes the previous comparison so it cannot be mistaken for a current result.
Use **Compare again** in the comparison tab to re-read the current target and
retained staging using the same result file (or current retained execution).
A saved result is read and validated again without selecting it again; the
previous comparison is not reused.
It does not rerun the transfer or change the original recorded commit state.
Changing connection or job inputs clears this shortcut as well.
After a successful repeat, **Changes since previous comparison** lists tables
whose row counts or match statuses changed since the previous successful
comparison in this session. It requires the same original result fingerprint,
target identity and resolved table set. Failed comparisons do not replace the
baseline. This compares counts and statuses, not historical row contents;
unchanged values do not prove unchanged data. Input changes clear the history.
The **Change** column distinguishes improved match statuses, new mismatches,
mixed status changes, and row-count-only changes. Mixed changes mean one match
status improved while the other worsened. An improvement can still leave a
mismatch in the other status; inspect both current match columns.
In the history tab, **Show** filters these change categories. It combines with
text search, numeric sorting, copying visible rows and CSV export. Loading
another comparison resets the history scope to **All changes**.
The history tab is session-only; saving a comparison retains the latest report
in the existing format. Its visible history rows can be exported as CSV.
After a comparison in the GUI, use **Save comparison…** and choose a folder to
write the same report format. **Open last saved comparison** opens it in **Migration reports**.
The shortcut keeps the last successfully saved snapshot when you compare again,
including when the new comparison fails. Saving the new comparison updates the
shortcut without overwriting the earlier file. Connection or job input changes
clear the shortcut.
An in-session comparison can still display its findings if saving the original
transfer result failed. Saving the comparison requires that result's fingerprint.

The comparison summary distinguishes matching targets, target differences and
changes to staging since its recorded verification. A match with changed staging
does not establish a match with the original copied data. Keep retained staging
and the original result while investigating differences. Even when all compared
rows match, the original commit remains uncertain; the comparison describes the
copied columns at comparison time.
