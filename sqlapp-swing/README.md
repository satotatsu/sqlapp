# sqlapp Swing migration review

Run on a Java 21 graphical desktop from the repository root:

```powershell
.\gradlew.bat :sqlapp-swing:run
```

## Edit data job YAML

Open **Job YAML editor** to create or edit a job without connecting to a database.
**Open…** loads existing UTF-8 text, including malformed YAML so it can be repaired.
**New** starts an empty buffer; use the existing
[bulk migration configuration examples](../docs/gradle-plugin/bulk-migration.md)
to express your tables and policies. No task IDs, data fingerprints or destructive
policies are guessed by the editor.

**Check syntax / types** parses the text into the existing
`BulkMigrationJobConfiguration` model. It rejects unknown fields, duplicate keys,
multiple YAML documents, invalid field types and enum values. Where available,
syntax errors show a line and column. The editor displays a synchronized line-number
gutter and the current caret line/column. A failed check or save moves the caret
to the parser-reported location when it belongs to the current buffer. Positions
outside a line or beyond the document are clamped safely; diagnostics refer to
the parser's token position, which may follow the erroneous value. Errors from
an earlier buffer do not reposition newer edits. These display operations do not
change YAML text or mark the file as edited.

This is an offline syntax/type check:
Schema references, task ordering and safety policies, data fingerprints,
checkpoints, leases and live target compatibility still require the existing
planner and executor. A syntax check is not execution approval.

**Save** replaces the opened file only when its SHA-256 still matches the opened
snapshot. External changes or deletion cause an error and retain the edit buffer.
**Save as…** requires a new filename. To edit an existing destination, open it
first. Saving preserves comments, whitespace and the current text rather than
serializing the model back to YAML. Files are limited to 1 MiB; invalid UTF-8 and
invalid Unicode text are rejected. Replacement is atomic where supported, with
fingerprint checks immediately before writing and replacement. These checks do
not provide a cross-process file lock.

Relative `schemaFile`, checkpoint, lease and report paths are resolved by the
existing command from the saved job's folder. Saving to another folder can change
what they refer to; review those paths before inspecting or executing. Saving
can succeed for syntactically valid configurations whose Schema or task policies
are not valid yet. Save failures preserve the buffer and original file.

After saving, choose **Use saved job in data plan**. This selects the file and
pins its exact saved configuration fingerprint in **Data migration plan**; it
invalidates the prior plan and does not connect or execute. The action is enabled
only for a saved, syntax-checked, unchanged buffer. Loaded files must first be
checked or saved. Inspect the live plan and use the existing confirmation to run.
The selected fingerprint also detects external edits made after this handoff.

Open/New and application close require confirmation before discarding unsaved
edits, with Cancel selected by default. Reads, checks and writes run in the
background; closing is blocked during editor file operations and migrations.
The editor does not automatically save on exit.

The additive `BulkMigrationJobConfigurationEditorIO` API lives in
`sqlapp-command`: `read(path)` returns exact text and its fingerprint,
`validate(text)` checks syntax/types, and `write(path, text, expectedFingerprint)`
preserves text with conflict checks. A null expected fingerprint permits only a
new destination. Existing resolver behavior, YAML formats and command properties
are unchanged; no dependency was added. Parser failures expose the additive
`ValidationException` subtype of `IllegalArgumentException`, with `getLine()` and
`getColumn()` returning one-based parser coordinates or `-1` when unavailable.
Messages omit the configuration text and parser exception cause.

## Reuse connection profiles

The **Connection profile** / **Profile** row in Schema planning and both data
connection forms provides **Load…** and **Save…**. Save writes the current JDBC
URL, username and optional driver class to a JSON file you choose. Load fills
those three fields and clears the password. It also invalidates any reviewed
plan, so connect and inspect again before execution. Loading never opens a DB
connection, runs migration SQL or changes SQL/YAML paths and execution policies.
A profile saved from any of these forms can be loaded into the others.

Passwords are neither read nor stored by profile saving. They remain available
for the next explicit connection attempt, and must be entered again after
loading. Common embedded URL credentials, including password/token properties,
URL userinfo and Oracle user/password syntax, are rejected on both read and
write. Remove secrets from URLs before saving; custom driver properties must
also be reviewed because arbitrary vendor URL formats cannot be exhaustively
classified. Profile files are ordinary unencrypted UTF-8 JSON and can contain
sensitive host and account names.

A concise profile is:

```json
{
  "formatVersion": 1,
  "jdbcUrl": "jdbc:hsqldb:mem:example",
  "username": "SA"
}
```

`username` and `driverClass` default to empty strings when omitted. GUI saves
include both. Other fields, unsupported versions, duplicate keys, trailing
JSON, invalid UTF-8 and files over 64 KiB are rejected. Invalid loads leave the
current fields and password intact. Failures appear beside the profile actions;
hover over the status for details. Reads and writes run in the background.
A pending load is discarded if inputs change or the connection form becomes
busy before loading finishes. Saves capture the settings at the time of the
click and replace files atomically where supported, with overwrite confirmation.

There is no automatic profile store, home-directory setting, saved password,
startup connection or driver download. Driver classes still need to be on the
application's runtime classpath. Execution retains its existing connection
confirmation, password re-entry and live identity/fingerprint checks.

## Schema comparison

Select **Before Schema XML** (current structure) and **After Schema XML**
(desired structure), then click **Compare**. The target Schema's database
product/version selects the existing sqlapp SQL factory. Both files must have
a single `schema` root, use the same database product, and contain no row data.
The initial runtime includes HSQLDB, PostgreSQL, MySQL, Oracle, SQL Server and
SQLite dialects. Additional dialects can be added to the module's runtime
dependencies without introducing GUI-specific SQL generation.

Comparison runs in the background without opening database connections.
**Save review** writes the displayed text in UTF-8. This is a review artifact,
not an executable migration script: statement delimiters, destructive changes,
transaction behavior and rollback must be reviewed before creating migration
SQL files. Changing either input clears the previous review.

## Build a Schema migration plan

Open **Schema migration plan**, enter the JDBC URL, username, password and
**Up SQL directory**, then click **Connect and build plan**. This action
connects to the chosen database and reads its metadata and migration history.
It calls the existing `MigrationPlanCommand`: up, setup and finalize SQL are
not executed, and missing history tables are not created. The connection pool
is closed after planning. Comparison and saved-report browsing remain offline.

The result lists pending migrations, transaction and rollback information,
known blockers, policy settings, setup/finalize statement counts and the
database identity fingerprint. **Save plan JSON** saves the displayed artifact
in the existing `MigrationPlanArtifact` format, preserving its creation time
and fingerprint. It can be reopened in **Migration reports** or supplied to
the existing CLI/Gradle expected-plan workflow. Saving does not approve or
execute the plan. Changes to connection, directory or policy settings invalidate
the plan and disable plan saving/applying until a new plan is built. The last
execution audit, if available, remains separately savable while editing inputs.

**Advanced options** exposes optional down/setup/finalize directories, an
upper version bound, history-table name, driver class, checksum policy,
out-of-order and non-transactional rejection, rollback requirements, repeatable
migrations and recursive directory scanning. The GUI defaults to UTF-8,
`changelog`, all available versions, checksum recording/validation, and
rejection of out-of-order and non-transactional SQL. Rollback requirements,
repeatable migrations and recursion are off by default. These are GUI defaults;
existing command and Gradle defaults are unchanged. Custom history column
layouts and live Schema drift checks are not exposed in this increment.

The pool requests JDBC read-only connections; enforcement depends on the
driver and database. Use an account with the necessary metadata/history read
permissions. Passwords are not saved in artifacts or settings. The password
field and working character buffer are cleared on each planning attempt;
re-enter the password for another attempt. Connection acquisition has a
10-second pool timeout; driver/network timeouts are driver-specific. There is
no cancellation control for planning yet.

The included HSQLDB dialect supplies an embedded JDBC driver. Other JDBC
drivers must be available on the runtime classpath; specifying a driver class
does not install its JAR. For example, after `:sqlapp-swing:installDist`, launch
on Windows with a locally installed PostgreSQL driver:

```powershell
java -cp "sqlapp-swing/build/install/sqlapp-swing/lib/*;C:/drivers/postgresql.jar" com.sqlapp.swing.MigrationApplication
```

For an entirely local trial, put the following in a directory as
`0000000010_create_customer.sql`:

```sql
CREATE TABLE customer (id INTEGER PRIMARY KEY, name VARCHAR(100));
```

Use `jdbc:hsqldb:mem:sqlapp_gui_demo`, username `SA`, an empty password and
that directory. The generated plan contains version 10, while its SQL remains
unapplied. This in-memory database exists only in the running Java process.

## Apply a reviewed Schema migration plan

After building a connected plan, review the pending SQL files and the displayed
plan. **Apply reviewed plan** becomes available when the plan has pending
versioned/repeatable migrations, no known blockers, and no setup/finalize
directories. The confirmation dialog shows the connection, selected plan and
fingerprint and asks for the password again. **Cancel** is the default choice.
Only choosing **Apply** starts a writable database operation.

The adapter first rebuilds the plan using a read-only connection and compares
it with the reviewed artifact using the shared fingerprint implementation.
Changed SQL, repeatables, policies, migration history or database identity
require a new review. It then passes the exact reviewed artifact and fingerprint
to `MigrationCommand` as the expected plan. The command retains its own
validation, concurrency checks, transaction boundaries and failure recovery.
Planning remains read-only; execution uses a separate writable connection pool
and the same captured connection/settings. No new execution semantics are
implemented in the GUI.

The current plan is consumed on every execution attempt, including failure;
applying again requires building and reviewing a new plan. While applying,
inputs and execution buttons are disabled, and the application blocks normal
window closing. There is no force-cancel, live statement progress, down
migration or automatic repair control in this increment.

The result shows committed versions/repeatables and, on SQL failure, the
existing command's statement position, SQL state, rollback/history recovery
outcomes and recovery advice. A returned rollback does not establish that all
DDL effects were undone. Inspect the actual database after failure before
repairing history or retrying.

**Save last result JSON** saves the existing `MigrationExecutionReport` without
changing its fingerprint. It can be reopened in **Migration reports** as
**Schema execution result**. The last audit remains available for saving when
inputs change or a new plan is built. Save it before starting another
execution or closing the app; execution reports are retained in memory until
explicitly saved. If a failure occurs before the execution command produces
an audit, the screen reports that no audit is available and disables audit
saving. A save failure retains the in-memory audit for another save attempt.

GUI application currently supports up and enabled repeatable SQL only.
Plans with setup/finalize directories are reviewable and savable but cannot
be applied in the GUI: the existing plan format fingerprints their statement
counts, not their bodies. Importing a saved plan as executable authorization
is also not supported; build and review a connected plan in this session.
Custom history column layouts and live Schema drift checks remain unavailable.

## Inspect a data migration plan

Open **Data migration plan**, select an existing job YAML and enter the source
and target JDBC connections. **Connect and inspect plan** resolves the job
through `BulkMigrationJobConfigurationResolver`, validates the live target's
tables/columns/keys through `BulkMigrationTargetValidator`, and reads the
configured checkpoints. `requireEmptyTarget`, when configured in YAML, is
also checked. Both connection pools request read-only mode and close after
inspection. No migration executor, lease acquisition or lifecycle hook runs.

The table shows the shared planner's dependency order, source and target table
identities, incremental strategy, chunk size, checkpoint mode/state and column
mappings. `INCOMPATIBLE` checkpoints remain visible for review; they are not
reset or repaired. DATABASE checkpoints use the existing read-only JDBC store,
so a missing checkpoint table is not created. FILE checkpoints are loaded
without creating directories or rewriting files.

The selected YAML owns task selection and advanced migration options. Its
`schemaFile` is resolved relative to the YAML, and existing Schema/configuration
fingerprint checks are retained. **Advanced options** exposes driver classes
and an optional expected configuration SHA-256. For example, a simple initial
copy job can use the following existing format with a captured Schema XML:

```yaml
jobId: customer-copy
schemaFile: schema.xml
tasks:
  - id: customers
    table: PUBLIC.CUSTOMER
    mode: INSERT
    resume: false
```

The Schema must provide a usable keyset, and the target table must already
exist. This example does not enable resume. For resumable jobs, supply the
source and target data fingerprints required by the existing migration APIs;
the GUI does not infer them from a Schema or JDBC URL. See
[bulk migration configuration](../docs/gradle-plugin/bulk-migration.md) for
task policies and durable identity requirements.

**Save status JSON** writes the displayed operational snapshot using the
existing `BulkMigrationOperationalReportIO` format. Its timestamp, plan
fingerprint, configuration provenance and checkpoint counts are preserved.
Open it in **Migration reports** as **Data migration status / dry run**.
The source/target mapping presentation is derived from the job; the saved
operational format is unchanged, so retain the original YAML and Schema for
the full migration configuration. The input job cannot be overwritten by
the save action.

This is a plan/checkpoint inspection, not an execution approval or a data
comparison. Source table definitions come from the captured Schema; source
rows and their equality to the target are not verified. Lease/maintenance
state and assessment/DDL approval artifacts are not validated here, even when
their declared fingerprints are retained in report provenance. The normal
execution workflow must still perform its own checks. No target-validation
approval artifact is issued by this screen.

All connection, YAML and fingerprint changes clear the result. Both password
fields and working buffers are cleared for every attempt; credentials are
not saved in reports or settings. JDBC driver availability and timeout behavior
are the same as for Schema planning.

## Execute a reviewed data job

After inspection, use **Execute reviewed job…**. The confirmation displays the
source, target, plan and configuration fingerprints and asks for both passwords
again. Cancel is selected by default. The GUI re-inspects the job and database
identities before writing, and passes the reviewed YAML fingerprint to
`ExecuteBulkMigrationJobCommand`. The shared resolver checks `schemaFingerprint`
again when execution resolves the job.

For GUI execution, job YAML must specify `schemaFingerprint` as `sha256:` followed
by the lowercase SHA-256 of its Schema XML. Existing YAML without this field can
still be inspected. To calculate the value in PowerShell:

```powershell
'sha256:' + (Get-FileHash .\schema.xml -Algorithm SHA256).Hash.ToLowerInvariant()
```

Jobs with incompatible checkpoints or required assessment/DDL approval artifacts
cannot be executed from this initial GUI. Use the existing command interface for
approval artifacts. The GUI does not issue a target-validation approval artifact.
Existing command properties, YAML formats and dependencies are unchanged.

Execution honors YAML mappings, checkpoint/resume policy, lease, operational
report and verification settings. Configured output files and lease settings
appear in the plan summary; inspect them before confirming. Execution may create
checkpoint/lease tables or directories and write configured report files. The
target pool allows two connections because a database lease holds its own
connection while data chunks run. Source access remains read-only.

Each chunk commits separately. Failed jobs can retain rows and checkpoints from
previous chunks. The outcome includes the shared failure report where available;
the stopped task's committed chunks are not necessarily in its completed-task
list. Re-inspect checkpoints and correct the failure before confirming another
run. YAML controls whether that run resumes. Plans are consumed by execution,
so a second run always requires fresh inspection and confirmation. Closing the
window is blocked while execution is active. See the safe-pause control below.

**Save last audit…** saves the existing success or failure JSON, retaining its
timestamp and fingerprint, and remains available after editing connection inputs.
Save it before exiting. These audits describe the copy phase: a successful copy
can be followed by a verification or report-publication failure. The GUI displays
that operation failure and verification status separately. For a durable
verification artifact, configure YAML `verification.targetFile`, then open it in
**Migration reports**. A completed execution audit alone does not prove that
verification passed. No audit is fabricated when execution fails before one is
produced; the GUI explicitly reports its absence.

## Data execution progress and safe pause

During a data job, the activity indicator and current task are shown above the
review. After each committed chunk, the display shows its migration ID,
checkpoint row count and completed chunk count. These counts belong to the
current migration and include previously saved progress when resuming. The GUI
samples the latest snapshot rather than queuing every event. No total-row count
or percentage is estimated, and in-flight rows are not counted as committed.
After copying, the phase changes to report publication and verification. The last
committed counters remain on screen and in the GUI's **Parsed report JSON** view.
The exported standard copy/pause audit keeps its existing format.

**Pause after chunk** requests a stop at the next completed chunk after its
checkpoint has been saved. It does not interrupt JDBC, abandon a transaction or
roll back earlier chunks. The request remains pending during an active query,
write or retry. If copying finishes before another chunk boundary, completion
can win the race; the displayed outcome is authoritative. Verification and
report publication are not interrupted by this control.

Pause is available only when every YAML task enables `resume` and
`useTransaction`. For keyset UPSERT, the shared engine additionally requires
`duplicateKeyStrategy: KEEP_LAST` to resume; the GUI requires that policy before
enabling pause. The plan summary explains unavailable pause settings. The GUI
does not change these policies automatically. DATABASE checkpoints commit with
the target data; FILE checkpoints retain the existing at-least-once replay
semantics. Backend validation still applies at execution.

An acknowledged pause appears as **PAUSED**, with the shared failure/pause audit
available through **Save last audit…**. Save the audit, inspect the job again,
review its checkpoint state, and confirm execution again to resume. The source
and target fingerprint requirements remain those of the existing YAML API;
pausing does not make changing source data safe. There is no force-cancel or
statement-level progress control.

## Saved migration reports

Open **Migration reports**, select the report type and a JSON file, then click
**Load report**. The summary shows the saved outcome and plan fingerprint;
**Tasks / migrations** lists migrations or tables in artifact order. Columns
can be sorted. **Parsed report JSON** retains the known model's detailed
fields, including evidence, failure and provenance information. This tab is
a formatted view of the parsed model, not the original file bytes; unknown
fields ignored by existing readers are not displayed.

| Report type | Existing artifact / producer |
|---|---|
| Schema migration plan | `migrationPlan.outputFile` (`MigrationPlanIO`) |
| Schema execution result | `migration.executionReportFile` (`MigrationExecutionReportIO`) |
| Data migration status / dry run | `BulkMigration.dryRun(path)` (`BulkMigrationOperationalReportIO`) |
| Data execution result | `executeBulkMigrationJob.executionReportFile` (`BulkMigrationJobExecutionReportIO`) |
| Data failure / pause result | `executeBulkMigrationJob.executionFailureReportFile` (`BulkMigrationJobFailureReportIO`) |
| Data verification result | Job YAML `verification.targetFile` (`BulkMigrationVerificationReportIO`) |

For creation and configuration, see [versioned migrations](../docs/gradle-plugin/versioned-migrations.md),
[bulk migration tasks](../docs/gradle-plugin/bulk-migration.md) and
[job reports](../docs/migration/job-reports.md). Creating these artifacts may
require database access; browsing saved files does not.

Loading uses the existing artifact readers and their format and consistency
checks, including embedded fingerprints where the format defines them. A
loaded report does not approve an operation, prove authenticity, or check the
current database state. Missing files, wrong report types, unsupported versions
and invalid reports show an error and clear the previous result. Changing the
file or report type also clears the previous result.

The viewer distinguishes failed and paused jobs, Schema migrations recorded as
committed versus those without a commit record, and verification mismatches.
A completed data execution report does not imply that post-migration
verification passed. A failure
report's completed-task list may omit durable chunks from the stopped task;
the actual checkpoints must be inspected before resuming.

Data jobs expose committed-chunk progress and safe pause. Schema execution has
no pause control. Restart/resume uses the existing job YAML policy and fresh inspection.

The GUI depends on `sqlapp-command` to reuse `GenerateDiffSqlCommand` and the
canonical Schema model. The package-private comparison adapter contains no
Swing code; the report adapter delegates to existing readers, and the planning
adapter composes `MigrationPlanCommand`; the execution adapter composes the
same settings with `MigrationCommand` and its expected-plan checks. A shared Swing view presents both
generated plans and saved reports. The data execution adapter delegates to `ExecuteBulkMigrationJobCommand`.
Both planning paths share connection setup.
HikariCP is already provided by the command
module. JDBC and migration business logic remain in existing modules. No new
dependencies or changes to existing public APIs or artifact formats are needed.

Tests (headless panel state checks and isolated in-memory HSQLDB planning/execution):

```powershell
.\gradlew.bat :sqlapp-swing:test
```
