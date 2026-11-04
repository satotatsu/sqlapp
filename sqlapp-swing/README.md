# sqlapp Swing RDB transfer

## Windows distribution (Java included)

Build a portable ZIP on Windows with a Java 25 JDK, from the repository root:

```powershell
.\gradlew.bat :sqlapp-swing:desktopZip
```

The ZIP is written to `sqlapp-swing/build/distributions/` as
`sqlapp-swing-<version>-windows-<architecture>-portable.zip`.
Extract the entire ZIP and open `sqlapp-swing/sqlapp-swing.exe`.
No system Java installation or Gradle is needed on the recipient's computer.
Keep the `app` and `runtime` folders beside the EXE. This is a portable folder,
not a standalone executable. Build and run on the same Windows architecture;
the Java 25 toolchain must match the build JVM architecture.

Both ZIP and installer tasks first run `verifyDesktopImage`: it compares the
packaged JARs with the input JARs, checks launcher settings, and uses the bundled
Java to check JDBC service discovery, SQLite native loading with an in-memory
query, HSQLDB and UI-library initialization. Run it separately with
`./gradlew.bat :sqlapp-swing:verifyDesktopImage`. These headless checks do not
replace a graphical launch check on a clean recipient machine or testing each
external database/driver combination.

For a per-user EXE installer with Start Menu and desktop shortcuts:

```powershell
.\gradlew.bat :sqlapp-swing:desktopInstaller
```

Install a WiX version supported by the selected JDK's `jpackage` and expose its
tools on `PATH` first. WiX is not needed for the portable ZIP. The installer is
written to `sqlapp-swing/build/desktop/installer/`. Its upgrade UUID remains
stable between releases. Use increasing numeric `major.minor.patch` versions;
the default is the project version. For snapshot builds, supply for example
`-PdesktopVersion=0.80.1`. Windows Installer version limits apply (major/minor
0–255, patch 0–65535). Packages are unsigned; signing and automatic updates are
not configured by these tasks.

`desktopImage` creates the unpacked application in
`sqlapp-swing/build/desktop/image/sqlapp-swing/`. `desktopRuntime` uses `jlink`
with all modules and service bindings from the Java 25 JDK, deliberately keeping
optional JDBC-driver functionality instead of inferring a minimal runtime from
the bundled drivers. Native access uses the same JVM options as Gradle `run`.
Dependency JARs, their service descriptors, native libraries and embedded license
notices remain intact. sqlapp license texts are included under `app/legal/sqlapp`;
the runtime retains its own legal notices. Before publishing, review the chosen
JDK and dependency redistribution terms and provide any additional required
source/notice material. Rebuild packages when the bundled Java needs updating.

The **JDBC drivers** screen still accepts additional local driver JARs. They are
session-only, as in the Gradle-launched application. Oracle's driver remains a
separate addition. Choose writable user folders for jobs, profiles and results;
do not use the installation folder for working data. No database is contacted
by the packaging tasks. Existing `run`, `installDist` and `distZip` remain
available; the latter distributions require Java 25 on the recipient's machine.
Single-JAR packaging is not part of these native distribution tasks.

Run on a Java 25 graphical desktop from the repository root:

```powershell
.\gradlew.bat :sqlapp-swing:run
```

Gradle `run` and generated distribution scripts enable native access with
`--enable-native-access=ALL-UNNAMED`. FlatLaf uses native libraries, and JDK 24+
requires explicit native-access permission to avoid restricted-method warnings.
See [JEP 472](https://openjdk.org/jeps/472).

For Eclipse, refresh the `sqlapp-swing` project and run the shared
`MigrationApplication.launch` configuration (Java Application). If you already
have a launch configuration, add `--enable-native-access=ALL-UNNAMED` under
**Run Configurations → Arguments → VM arguments**, not Program arguments.
Gradle's application JVM options do not automatically update existing Eclipse
Java Application launch configurations. These options permit native code in
classpath libraries; they do not disable native functionality or suppress other
JVM warnings. No machine-wide environment variable or Eclipse installation
setting is required.

## Appearance and code views

### Access MDB / ACCDB migration

Choose **Start → Open Access migration** (or **Access file migration**), then
browse for or drop an existing `.mdb` / `.accdb` file. Enter only the target
connection, an optional Access password and a working folder. Choose **New
Access migration job…**, select tables and choose data transfer or schema only.
Review warnings and Schema SQL, then explicitly confirm execution. The file
selection itself does not open a connection. The MDB dialect and its existing
UCanAccess 5.2.0 dependency are bundled with the desktop distribution, so this
source does not require adding a driver JAR manually.

Inside **Access file migration**, choose **File transfer** to export the
selected Access file through the common CSV workflow. Create **New Access CSV
job…**, choose tables and optional target-table creation, then export. Use
**Continue to import this package…** to select the exported package automatically.
Import uses only the target connection: the Access file selector is hidden and
the original MDB/ACCDB need not be available. The direct and CSV workflows share
the file selection but keep their jobs, reviews, passwords and results separate.
Changing the selected file invalidates both workflows' prior reviews. The same
CSV job remains usable with Gradle `exportTransferFile` / `importTransferFile`.

The table picker reports data-copy eligible and unavailable counts. **Unavailable
for data copy** shows restrictions such as missing keys or unsupported values;
it can be combined with text search and retains all existing selections, including
hidden ones. Review **Availability** for the precise reason. This view does not
relax unrelated restrictions. Direct-job creation permits keyless tables with a
**full-scan INSERT** warning; duplicates and NULLs are preserved and hash-multiset
verification is used. MERGE and resume are unavailable. **Switch to schema only**
still selects definitions without row copy. CSV jobs retain their existing
numeric-key and value-type rules.

After selecting a child table, **Include referenced tables** optionally includes
its transitive foreign-key parent tables from the captured Schema, including
rows hidden by the current filter. Clear the filter or inspect the selection
count/details to review every added table before saving. No job is saved and no
database is opened by this action. The XML byte fingerprint is rechecked first.
Missing/ambiguous references and parents unavailable in the current data-copy
mode leave the complete selection unchanged, with an actionable explanation.
Schema-only and direct full-scan INSERT can include keyless parents; CSV eligibility remains enforced.
This action adds parents, not child/descendant tables, and does not bypass the
ordinary ordering, cycle or execution checks. It is optional when referenced
tables already exist at the target and are intentionally outside the job.

When missing-table CREATE planning adjusts repeated constraint or index names,
**Summary** highlights the number of name-adjustment warnings. In **Warnings**,
filter for **Target / Object names** or `[SCHEMA_NAME_ADJUSTED]` to inspect the
recorded original name, table and target name. Check the corresponding **Schema
SQL** and any application code or scripts that refer to these names. The notice
describes the plan, not proof of execution; use the recorded SQL progress to see
completed or uncertain attempts. Source Schema retains its original names.

This screen composes the common direct-transfer service: saved YAML, warning
policies, optional missing-table creation / schema differences, SQL drafts and
partial-failure evidence retain the same behavior. Access forms, reports and VBA
are outside table/schema transfer. Data transfer retains the common keyset and
primary-key requirements; schema-only transfer does not require data-copy keys.
Changing or clearing the selected file invalidates the prior review. Missing
files and paths containing semicolons are rejected before opening the source;
no new Access database is created. Source files are protected from SQL-draft
overwrite, including file aliases.

Use the unchanged saved YAML with `transferDatabase` as shown in the **Gradle**
tab. Add the `sqlapp-core-mdb` dialect (and the target dialect/driver) to that
project's runtime dependencies and supply, for example,
`SOURCE_JDBC_URL=jdbc:ucanaccess://C:/data/source.mdb` in the Gradle process
environment. The file path and optional password remain runtime connection
settings, not credentials embedded in the YAML.

Generated local MDB tests cover Japanese and space-containing file, table and
column names, Japanese text values, foreign keys and schema-only transfer to
embedded HSQL. This does not establish compatibility with every existing Access
file, collation or complex field. See the MDB module's documented Japanese
index-collation write restriction in [its README](../sqlapp-core-mdb/README.md).

### Navigation

The application opens on **Start**, with four task-oriented entry points:
**Direct JDBC transfer**, **CSV package**, **Access file migration**, and **Review saved results**.
The transfer entries explain the connection/table/review flow without requiring
YAML preparation. **More tools** reveals Schema comparison, SQL migration
planning, SQL and Job YAML editors, JDBC driver management and **Advanced Data Job**.
All existing tool tabs remain available.
Start actions only switch to the existing tab; they do not clear inputs, replace
unsaved edits, load files, connect to a database or approve/execute a migration.
Return to **Start** at any time to choose another task.

## Direct JDBC transfer without preparing YAML

Open **Direct JDBC transfer** and enter or load the source and target connection
profiles. Choose a working folder. **New direct job…** reads source metadata and
opens the shared searchable table picker; it needs only the source connection.
Select tables, optionally change chunk size, verification, target schema or the
strict compatibility option, and save. **Create missing target tables before copying**
opts into `CREATE_MISSING`; unchecked uses `REQUIRE_EXISTING`. The resulting `transfer.yaml` is the
common `JDBC_DIRECT` format used by Gradle `transferDatabase`. It contains logical
`source` / `target` bindings and no connection passwords or JDBC URLs. Saving
does not connect to the target or copy rows.
Direct and CSV initial copies preserve original identity values (including zero
and gaps) and NULLs, so dependent foreign keys keep their original meaning.
No extra GUI option is required. Review again after upgrading; existing copied
rows are not rewritten or cleared. Identity/sequence counter synchronization for
subsequent generated inserts remains a separate check.
Review and execution Summary call out generated-key counter checks and show
per-column `[GENERATED_KEY_COUNTER]` warnings with resolved target names. Matching
row verification does not mean future generated IDs are safe. This also applies
to existing target tables. WARN leaves the transfer executable; ERROR rejects
the unverified counter condition. Check actual generator state before application
INSERTs; counters are neither probed nor changed automatically. Independently
used sequences and unmodeled vendor default expressions still need manual review.

**Review transfer** checks the live source and existing empty target tables or
plans absent selected tables without creating them,
pins the identities and artifacts, and saves `transfer-review.json`. The default
**Summary** tab shows database products, selected tables, options and warnings;
**Schema SQL** shows generated DDL when present. **Details** retains complete read-only JSON with resolved plans and artifact
fingerprints. Direct and CSV Details display JSON with indentation and line breaks;
the saved audit format and string values remain unchanged. Use **Wrap lines** for
long values. Review never copies rows. **Execute reviewed transfer…** requires
confirmation (including DDL count), reruns the shared checks, creates the
unchanged reviewed missing-table plan and copies/verifies through the same
service used by Gradle. Changing a job, endpoint, source schema or working folder
requires a new review. File selection supports drag and drop. Driver selection,
connection tests, profile loading and URL construction use existing components.

Both endpoint passwords remain in the UI after review for the next action, and
are cleared when execution starts. Temporary copies are erased after background
work. Execution consumes the review, including on failure. The displayed outcome
and `transfer-result.json` retain warnings, copy/verification state, committed
progress and DDL progress. A failure may leave created objects and committed rows.
DDL failures retain successful statements and the uncertain attempted statement;
copying does not start. A missing report path indicates
unavailable publication. Connection cleanup failures remain errors even when the
retained data outcome is COMPLETE. Settings are disabled during background work,
and app closing waits for work to finish.

Use the unchanged job with [Gradle transferDatabase](../docs/gradle-plugin/direct-transfer.md).
After a successful review, the read-only **Gradle** tab provides a Groovy task
configuration with the absolute job path, environment-based connection bindings,
and review/execute commands using that job's configuration fingerprint. Select
and copy the text into a project that applies `com.sqlapp.db`; configure both
drivers and dialects there. Connection URLs and passwords are not copied from
the GUI. Run review first and inspect Gradle's own report: execution makes a fresh
live plan, and the fingerprint pins YAML bytes rather than approving the GUI plan.
The guide is cleared when inputs change, a new review starts, or execution starts.
Add GUI-loaded JDBC JARs to Gradle's runtime classpath separately. This direct
path supports existing empty targets and optional missing-table preparation.
The target schema namespace must exist; views/routines/triggers/grants and a
complete database clone are outside this table-copy path. Real heterogeneous database
behavior remains unverified here; product differences default to warnings and
strict mode can reject them. The existing Data Job YAML format, editors, pause
controls and advanced reports remain in **Advanced Data Job** under **More tools**.

**Job settings…** opens an offline form for an existing common job. Adjust the
target schema, missing-table creation, chunk size, verification and WARN/ERROR
policy, then choose **Save settings**. The same action is available in CSV export;
it needs no connection. Tables, logical bindings, source schema, mapping and file
options stay unchanged, and the job remains in its original folder so relative
paths keep their meaning. Saving atomically replaces the opened YAML only if its
fingerprint still matches; external changes require reopening. The form rewrites
YAML formatting and comments, as noted before saving. Cancel leaves the file and
current review unchanged. A successful save invalidates review and Gradle guidance;
review the direct job again, or export a new CSV package. Completed-package
settings cannot be edited through this form, including when package markers
appear after the job was opened. Loading and saving run outside the UI thread;
closing waits for the current file operation.

If missing-table preparation cannot find the target schema, Summary names the
requested schema and lists up to ten existing schemas reported by JDBC for the
selected catalog. Set `targetSchema` with the exact name/case, or create the schema
separately with the intended owner and privileges, then review again. Shared
preparation validation messages also appear in CSV Summary; arbitrary driver
exceptions and cleanup causes are not promoted into this guidance.

Direct transfer failures replace Summary with the latest failure stage, reason,
next checks and available SQLState/vendor code. Source and target connection
opening are distinguished. Authentication, connection, missing-database and
permission SQLStates receive specific guidance; supported library validators
retain details such as missing tables/columns and nonempty targets. Repeated
failures do not accumulate old messages. Failed reviews clear stale JSON, SQL
and Gradle guidance; execution failures still retain their outcome and audit.
The status Details dialog contains the same selectable diagnostic. Raw driver
messages, credential-bearing causes and suppressed cleanup errors are not kept
in preparation diagnostics. Connection-test/source-discovery messages retain
their existing redaction. Review failures do not authorize execution.

## CSV transfer without preparing YAML

Open **CSV package**, choose **Export source to CSV**, and enter or load the
source connection. **New file job…** asks for a working folder, reads source
metadata, and opens the existing searchable table picker. Unsupported tables
show a reason and cannot be selected. Choose tables, review the visible CSV NULL
token, and save. Chunk size and verification remain optional adjustments.
**Create missing target tables before importing** is an explicit opt-in; leave
it unchecked to require existing tables. The target schema namespace must exist.
**Target schema** is optional in the creation form, just as for direct JDBC
jobs. Leave it blank to use the import connection's current schema; enter the
exact existing name/case to import elsewhere. The value is captured in the shared
job and completed package, so GUI and Gradle use the same destination. Source
table identities remain unchanged. For an already saved job, use **Job settings…**
before exporting again; completed packages retain their original destination.
Compatibility concerns default to warnings so users can first try migration and
then refine the result. **Treat compatibility warnings as errors** enables strict
preflight via the shared `compatibilityPolicy: ERROR`; its default is `WARN`.
Unsupported temporal types default to warning-only JDBC-text export; the more
specific `files.unsupportedTemporalPolicy: ERROR` is also available in YAML.
A warning-bearing temporal
column requires conversion before automatic import; exporting it does not approve
a lossy import. Inspect the warning count and `manifest.warnings` in the result.
The saved `transfer.yaml` uses the shared command format, logical connections
`source` / `target`, and a `packages` directory relative to the job. It contains
no credentials. Saving does not export data or write to either database.

Choose **Export source to CSV** explicitly to create the completed package.
Its manifest path is displayed and selected for later import. Switch to
**Import CSV package**, select or drop `manifest.json` or its containing package
folder, and provide the target
connection. **Review package and target** checks package integrity and existing
empty target tables and displays generated DDL for missing tables when requested.
Review that SQL, choose a results folder, then **Import reviewed package…**
and confirm. Changing the connection or manifest requires another review.
Before entering any connection, **Inspect package (no connection)** checks the
completed package members, recorded byte sizes and hashes through the shared
package reader. Summary shows recorded tables/rows and export warnings, and
Details retains the complete manifest. **Package tables** shows source identities,
requested target names, recorded row totals and conditional generated-PK settings.
Select a table and **Show columns** to inspect original and requested column names,
source types and source PK/NOT NULL flags. These are recorded source metadata and
requested settings, not resolved target types or proof that values can be imported.
**Show renamed columns** focuses on changed column names; **Show all columns**
clears that filter and the search. The overview remains available after a review
or import result for the same package fingerprint, so requested names can be
compared with recorded target preparation and execution results. It still shows
package metadata, not live target state. Changing inputs, a failed operation or
a different package discards the retained overview.
Select a row in **Package tables** and choose **Preview selected table…** to open
the offline preview with that exact task selected. Choose **Load sample** to read
its bounded sample. This handoff pins the package fingerprint; if the package
changes, inspect it again rather than using the old overview. Reloading this
preview continues to require the pinned fingerprint. Missing tasks never fall
back to a different table. The separate **Preview package data…** action remains
available without an overview.
When opened from the overview, sample headers show **source column → requested
target column** for renamed columns, and the sample description shows the
requested target table. Unchanged names remain concise. Types and values are
from the source; this does not simulate target conversion or approve import.
The name correspondence is tied to the same package fingerprint and exact
source table/column identities. The standalone preview continues to show source
names when no overview was supplied.
Sample columns size themselves to their headers within a bounded width; longer
names remain horizontally scrollable. Hover over a header to read its full
source/target name correspondence and source type, including after reordering
columns. Selected-row details and copying retain the complete header text.
**Find column** and **Next column** (or Enter) navigate matching headers in the
current column order, wrapping to the first match. The search is literal and
case-insensitive and includes requested target names and source types. It only
scrolls horizontally: row filters, values and row selection are preserved, and
no file reread or database connection is required.
**Previous column** (Shift+Enter in the search field) moves backward through
matches, wrapping at the start. **Match n / total** reports the current match in
the current column order, so reordered columns remain easy to navigate.
After finding a column, **Column details…** opens selectable text with the source
column and value type. When the preview came from **Package tables**, it also
shows the recorded Schema type, source PK/NOT NULL flags and requested target
table/column names. Standalone previews explicitly identify unavailable metadata.
These details do not infer target types or constraints; target Review remains
separate. Column details also work for a sample with no rows.
**Columns…** opens a searchable, sortable list of all packaged columns, including
recorded source constraints and requested target names when available. Select a
row and **Go to column** to return to that exact column in the sample, even after
sorting the list or reordering sample columns. This works for an empty sample
and never reads additional data or changes the sample's row filter or selection.
Returning with **Go to column** also updates the current-column indicator and
the **Column details…** target. It clears the previous column-search text and
match count, so an earlier search result cannot be mistaken for the column now
being inspected. The row search remains unchanged.
This opens no database connection and
does not read the target password. Select **Also validate CSV values during inspection**
for the shared offline import preflight, including headers, value types, recorded
row counts, non-null columns and ascending unique numeric keys when eligible. This may scan
large CSV files and still opens no connection. Export-only types that require
conversion can fail this optional check; package-only inspection remains available
to view warnings. Summary distinguishes file integrity from validated values and
shows saved target schema, table preparation, verification and compatibility settings.
Target compatibility still requires **Review package and target**, which repeats
the required package checks. Inspection is not an
import approval or proof of authenticity; it clears an earlier target review.
Changing the selected package discards an in-progress inspection result.
Operation buttons stay outside the settings scroll area so inspection, review
and import remain accessible when connection fields need scrolling.
After a current export, **Continue to import this package…** in Summary switches
to the import screen with that manifest selected and focuses the target connection.
It preserves any target connection already entered and starts no connection,
review or import. Source credentials are not copied to the target. The shortcut
is removed when inputs change or a new operation starts; every import still
requires a fresh target review and explicit confirmation.
Typing the package folder path also works. The folder must contain `manifest.json`
directly; parent folders, CSV-only folders and incomplete exports are rejected.
Folder selection starts no connection or import. Review resolves the manifest
and retains the same package integrity, fingerprint and target checks as explicit
file selection. The Gradle tab still uses the resolved manifest file path.
Execution revalidates the package and target and retains copy, verification,
committed progress and audit information, including on import failure.
The default **Summary** tab shows exported tables and row counts, package export
warnings, target product, optional DDL count and the next action. Import outcomes
show the execution phase, completed-table row counts, recorded verification,
per-task committed progress, DDL progress and audit path. Missing verification
is shown as not completed; disabled verification is explicit, and a partial
verification only describes recorded tables. **Details** retains the complete
read-only JSON; **Schema SQL** remains available for inspecting planned DDL.

CSV target review also offers **Save SQL draft…** and **Edit saved SQL…** in
**Schema SQL**, using the same offline actions as direct transfer. The draft
retains target delimiters, warnings and job/plan pins. Saving protects manifest,
job, Schema, CSV data and retained import/audit files, including aliases. It never
starts import, changes package bytes or authorizes execution. If you apply manual
SQL through the separate SQL migration workflow, review the package and target
again before importing. Result drafts identify completed/uncertain statements
and may include already-applied SQL; they are not retry scripts. A result that
does not match the captured review cannot reuse its draft. Input changes and new
inspection/results clear the saved-file handoff. No export is offered without
Schema statements. Package and audit formats are unchanged.
Input changes mark the previous summary as stale, and failures cannot authorize
another import without review.
Committed chunks and created objects can remain after a failure. The result
records completed DDL and the uncertain attempted statement; no automatic retry or target reset
is performed.

Database/file work runs in the background, with inputs disabled during work.
Review and connection checks retain the UI password for the next action;
export/import clear it when starting. Password copies are cleared after work.
GUI-only JDBC JAR additions must also be configured in Gradle's runtime
classpath for unattended execution. The same saved job/package can be used with
[`exportTransferFile` / `importTransferFile`](../docs/gradle-plugin/file-transfer.md).
After export, the read-only **Gradle** tab supplies source-only re-export settings
and target-only import settings for the completed package, including its absolute
manifest path and fingerprint. A new export produces a different package; review
it and update import settings before execution. After an import review, the tab
contains only the target settings and import command, so the source is unnecessary.
Copy the settings into a project applying `com.sqlapp.db`, add driver/dialect
runtime dependencies, and supply connection values through environment variables.
GUI URLs and passwords are not included. Move the entire package and update the
manifest path when transferring to another machine. `allowImport=true` explicitly
authorizes execution; omitting it refuses import rather than performing a review.
Gradle validates the target afresh. Input changes or a new operation clear the
guide; connection tests leave the current guide available.

The current CSV path supports pre-existing empty
targets or explicit creation of missing ordinary tables, ordered numeric keys or full-scan INSERT
and supported scalar columns. Product differences and missing-table features
such as identity/default/generated/domain columns or partitioning/inheritance
are warnings by default, and strict policy can reject them. The target dialect
generates best-effort DDL; inspect retained/omitted vendor features and actual
failures. Unresolved external-schema FK references require explicit resolution.
DATE, second-precision TIME and standard
TIMESTAMP (up to nanoseconds) support typed CSV import. Unsupported temporal
types can be exported as JDBC text with warnings. Full Schema cloning,
complete vendor-specific cross-product conversion, binary/LOB columns are not implemented. Direct JDBC transfer retains its existing
Data Job workflow in the advanced tool; the primary direct-transfer screen uses
the common transfer-definition format described above.

The desktop installs [FlatLaf](https://www.formdev.com/flatlaf/) 3.7.2 (Light)
before creating Swing components. SQL comparison previews, the YAML job editor,
and parsed JSON reports use
[RSyntaxTextArea](https://github.com/bobbylight/RSyntaxTextArea) 4.0.1 for syntax
highlighting, line numbers, bracket matching and folding. SQL and JSON previews
remain read-only; syntax highlighting does not validate or execute SQL. YAML
validation continues to use the shared command validator. Search, go-to-line and
error navigation reveal folded text when needed.

Schema comparison **Review** and **SQL draft** use the same read-only code
navigation as parsed report JSON: literal search, match counts, line/column
navigation, visual wrapping and caret/selection position. Each tab keeps its own
query and wrap preference. Changing Schema inputs or generating a new comparison
clears the previous search/navigation state. Comparison and save operations
temporarily disable navigation, then restore it, including after a failed save.
Search and wrapping never alter the generated review or exported SQL draft.

The editor toolbar and native right-click Undo/Redo share RSyntaxTextArea's one
undo history; grouped replacement and the existing save/dirty-buffer protections
remain in effect. The dependencies are confined to `sqlapp-swing`; command/core
modules and existing artifact formats have no GUI dependency. Both jars are
included by `installDist`. Modern Docking has been evaluated as an option for
future simultaneous pane layouts; the current workflow retains its tabs.

## Drag and drop files

Drag local files from the file manager to these targets instead of using Browse/Open:

| Target | Accepted drop | Action |
| --- | --- | --- |
| Schema comparison Before/After path | One XML file | Select that input; Compare remains explicit |
| Schema migration SQL directory fields | One folder | Select that directory; planning remains explicit |
| Data migration Job YAML path | One YAML/YML file | Select the job; inspection remains explicit |
| Schema-based job builder Schema path | One XML file | Select the Schema; Read Schema remains explicit |
| SQL / Job YAML editor, filename label or Open button | One SQL / YAML/YML file | Open through the normal unsaved-edit confirmation |
| Migration reports JSON path | One JSON file | Select the file; Detect and load or choose a format explicitly |
| Connection profile Load button | One JSON file | Load the profile through the existing validation |
| JDBC drivers list or Load button | One or more JAR files | Load the batch with the optional driver-class setting |

Single-file targets reject multiple files. Wrong extensions, files dropped on
folder targets and folders dropped on file targets leave the previous selection
unchanged and show a diagnostic. Busy or disabled targets reject file drops.
File drops use Copy semantics and do not move the original files. Normal text
copy/paste and text drag operations retain their existing handlers. Dropping a
file does not start database planning or execution. Driver JAR loading still
initializes Java code under the existing trusted-library rules.

## Editor file shortcuts

SQL and YAML editors support **Ctrl+S** (Save), **Ctrl+Shift+S** (Save as),
**Ctrl+O** (Open) and **Ctrl+N** (New). The same combinations with **Cmd** are
also available. Shortcuts apply within the focused editor panel and use the
same checks as its toolbar buttons. Saving a new nonempty document asks for a
filename; saving an unchanged document does nothing. Busy or disabled actions
do not run again. Open and New retain the unsaved-change confirmation, and
saving retains validation and external-file-change checks.

When an action would discard unsaved SQL or YAML, the confirmation offers
**Save and stay**, **Discard edits** and **Cancel**, with Cancel selected by
default. Save and stay invokes the usual save operation (or asks for a new
filename for an unsaved document) and leaves the editor open. After a successful
save, repeat New, Open, Reload, draft creation or application close. The pending
action never resumes automatically, so failed or cancelled saves and newer edits
remain available. When saving is unavailable, only Discard edits and Cancel are
offered. YAML saves still check syntax/types and all saves retain file-conflict
protection.

Open and Save as start in the folder of the last file successfully opened or
saved in that editor. New retains this folder for the next document. SQL and
YAML editors keep separate locations for the current application session; no
settings file is written. Cancelled dialogs and failed reads or writes do not
change the remembered location. Save as keeps its suggested filename inside
that folder. Moving a YAML job to another folder still changes the base used
for its relative paths; review them before planning.

**Reload from disk** reads the current SQL or YAML file again, using the same
unsaved-change confirmation as Open. It is disabled for new documents and while
an operation is running. A successful reload resets undo history and invalidates
the YAML syntax check or SQL boundary preview; check or inspect again before
using those results. A failed read retains the existing buffer and file identity.
Reload does not write the file or select or execute a migration plan. After an
external-change conflict, use Save as to preserve local edits before reloading
if both versions are needed.

The SQL and YAML status bars include **Details…**. Open it to read the complete
current message in a scrollable, read-only text view, select text and copy it
with the usual keyboard shortcut. Long and multiline validation or file errors
are retained in full. The status tooltip follows the latest message, including
after recovery from an error. Viewing details does not write files or run a job.

Both source editors show the current line and column and, when text is selected,
the selected character count. Lines and columns start at 1; tabs count as one
column. Columns and selection lengths count Unicode code points, with line
endings included in selected text (CRLF contributes two characters). The SQL
position indicator is hidden while viewing statement boundaries so it is not
mistaken for a position in the parser output. Selection and position display do
not modify the buffer or undo history.

## File dialog locations across migration screens

File dialogs across Schema comparison, Schema/data planning, saved reports,
Schema-based drafts and connection profiles now use the same in-memory location
handling. Input browsing starts near the currently entered file or directory;
when that input is blank it uses the last successfully read location for that
screen. A malformed browse hint leaves the input untouched and falls back to
the remembered/default location; actual reads and plans still validate the input.
For paired Schema XML inputs, an empty input uses the other selected Schema's
folder. Empty optional migration-directory fields start in the current Up SQL
directory, so selecting related inputs does not restart from the home folder.

Output dialogs suggest the existing SQL/text/JSON filenames inside the last
successfully saved output folder. Before the first save they start beside the
related Schema/job file, or in the Up SQL directory for Schema plans. Profile
dialogs share their own successful load/save location. These locations are
separate per screen and are not persisted. Cancelling or failed IO does not
update them; existing overwrite confirmations and execution guards still apply.

## Status and diagnostics across migration screens

Application close is blocked while Schema comparison, editor IO/checks, Schema
or data planning, migration execution, report/audit/verification saves, saved
report loading or connection-profile IO is running. The close guard checks the
current state again after unsaved-edit confirmations. It does not cancel work;
wait for the result and retry closing. The Schema draft dialog retains its own
read/build close guard.

Connection-profile IO contributes to its parent Schema/data screen's active
state. Starting planning or execution while a related profile is loading or
saving shows a wait message; retry after it completes. Save methods also reject
duplicate calls while an operation is active. Success and failure both restore
the normal action state; retained plans and execution audits remain available
under the existing inspection and retry rules.

**Details…** is available in Schema comparison, Schema migration plan, Data
migration plan, saved reports, the Schema-based job builder and connection
profiles, as well as both source editors. It opens read-only, selectable text
with the complete current status or error. Failure details include the exception
and its cause chain, suppressed failures (such as rollback or close failures),
and JDBC chained exceptions with their SQLState and vendor error code. Shared
references and cycles are marked without expanding an exception twice; deeply
nested causes are traversed without recursion. No stack trace is added.
Plan/report summaries retain the same diagnostic evidence.
Completed, failed or paused execution results also expose their summary through
Details. Reading or copying this text does not inspect or execute a database.

Starting another operation or changing inputs clears the previous extended
diagnostic when the status changes. Schema/job input changes and connection
profile edits also replace stale status text. Execution evidence and audit-save
actions retain their existing rules. Status details are an in-memory view and
do not replace saved execution, failure or verification reports.

Connection-profile Details includes the successfully loaded or saved file path;
passwords remain excluded from the profile format. Profile controls use a
compact layout so Load, Save and Details remain available in the data source
and target forms.

## Edit migration SQL

Open **SQL editor** to create or repair a SQL file, or choose **Edit saved SQL…**
after saving a Schema comparison draft. SQL editing uses RSyntaxTextArea and the
same search, literal replacement, line navigation and native undo history as the
YAML editor. Opening another file, New and closing the application ask before
discarding unsaved SQL. Reads and writes run in the background; closing is blocked
during these operations.

SQL text is saved verbatim as strict UTF-8, including comments and CRLF/LF line
endings, up to 1 MiB. Files use the lowercase `.sql` extension expected by migration
planning. **Save as…** creates a new file; existing files must be opened before
editing. **Save** checks the opened SHA-256 before writing and again immediately
before atomic replacement. External changes leave the file and editor buffer
intact. These optimistic checks are not a cross-process file lock.

**Use saved SQL directory** is available only for a clean saved buffer. It
rechecks the saved bytes and uses the existing migration reader to validate the
versioned filename and directory version uniqueness, then selects the Up SQL
directory and invalidates the previous plan. It does not connect or execute SQL.
Syntax highlighting is not SQL validation: review vendor delimiters and the
complete directory contents, then build a fresh plan against the intended database.
Rollback SQL is not generated automatically.

The standalone command-module API is `MigrationSqlEditorIO`: `read` returns the
exact text and SHA-256 snapshot, `write` accepts that fingerprint (or null to
create a new file), and `migrationDirectory` checks the saved snapshot against
the existing version-file reader. It has no JDBC or Swing dependency.

## Preview SQL statement boundaries

In **SQL editor**, select **SQL splitting** product and enter an explicit **DB
version** (`15`, `15.0` or `15.0.1`), then choose **Inspect boundaries**. Nothing
is inferred from a JDBC URL, and no connection is opened. The **Statement
boundaries** tab shows the resolved dialect class, SQL statement/batch and comment
counts, and each part returned by the shared dialect splitter. Select a row to
view its complete SQL in a read-only syntax view. **Previous part** / **Next part**
move through the currently visible rows. **Hide comments** and **Filter parts**
(case-insensitive literal matching across each part's full text) narrow the list;
the displayed count distinguishes visible parts from all parser parts. Sorting
part numbers or character counts is numeric. **Copy part** copies the selected
parser output unchanged, including comments if selected. The parser may remove
statement delimiters: this action does not produce an approved executable script.
Clipboard failures are reported and retain the selected part for retry. The original buffer, saved file
and undo history are unchanged. Editing SQL or changing the product/version
invalidates the preview; results completed for older inputs are discarded. Search,
line navigation and replacement switch back to the source editor; opening a file
or choosing New also selects that editor tab.

Select a preview part and choose **Find in SQL** to select its text in the SQL
editor. This uses a case-sensitive literal match, including multiline text; it
does not infer source positions from the splitter. Only a unique match is
selected. Multiple matches or changed parser whitespace/delimiters produce an
explanation and retain the existing source selection. The operation does not
edit SQL, change the search settings, save a file or approve a migration.

This is the existing parser's interpretation, not a syntax validator or an
execution approval. SQL Server `GO` batches and repetitions use the shared
splitter's behavior, so a part is not necessarily one SQL statement. MySQL
`DELIMITER` changes and PostgreSQL dollar-quoted bodies (including anonymous
`DO` blocks) use their dialect parsers. See the
[splitter review](../docs/development/sql-splitter-review.md) for version boundaries
and dialect-specific limits. Review stored-program
boundaries manually; this GUI does not patch or hide shared parser limitations.

The additive command-module API is `MigrationSqlInspection.inspect(text, product,
version)`. Results retain every nonblank parser part and the resolved dialect,
with no JDBC or Swing dependency. Input is limited to 1 MiB of valid UTF-8 text.

## Create a job from Schema XML

In **Job YAML editor**, choose **From Schema…**. Select a Schema XML file and
click **Read Schema**. The dialog lists the canonical table identities, primary
keys and availability reasons, and displays the SHA-256 of the exact XML bytes.
Choose individual tables or **Select visible eligible**. No tables are selected silently.
Set an optional job ID and chunk row count, then choose **Create draft in editor**.
The builder re-reads the XML and rejects changes made after inspection. Closing
the dialog leaves the original editor buffer intact; replacing unsaved edits
uses the editor's existing confirmation. File operations run in the background.

**Find tables** filters table identities, primary-key names and availability
reasons with case-insensitive literal matching. Ctrl+F / Cmd+F focuses the filter.
The count shows visible/all tables, the total selected tables and how many
selections are hidden by the filter. Filtering never changes the Include flags.
**Selected tables only** narrows the list to checked tables and combines with
the text filter. Unchecking a table removes it from this view immediately;
turn the option off to include other tables. **Clear filter** clears only the
text search. Reading another Schema or re-reading the current one resets both
filters and the selection. These view changes do not save settings or connect
to a database.
An inline hint explains empty schemas, unavailable tables, unmatched filters and
the next selection step. When selections are hidden, it reminds you that they
will still be included. In **New migration**, the ready hint also distinguishes
saving and inspecting the plan from copying rows.
**Select visible eligible** and **Clear visible** affect only currently displayed
tables, even after sorting; ineligible tables remain unselectable. **Clear all
selected** also clears hidden selections. Draft creation includes every selected
table, including hidden ones, using the shared builder's ordering and validation.

Changing the Schema path or reading it again clears the filter and selection.
Re-reading the same Schema correctly replaces the previous list and fingerprint;
an older asynchronous read still cannot replace newer inputs. Filter and selection
controls are disabled while reading or building a draft.

Drafts use `INSERT`, `requireEmptyTarget: true`, `resume: false`, transactional
chunks and the same target table identities/column names as the source. Ordered
row/hash verification is enabled by default and can be unchecked. No source or
target data fingerprints are invented. The XML path is absolute, and its exact
`schemaFingerprint` is included, so saving the draft in another folder does not
change that reference. Task IDs are generated deterministically from table
identity; dependency order comes from the shared job planner. Schema XML is not
modified, and creating a draft never connects to a database or writes a job file.

The generic advanced draft path supports tables with a complete NOT NULL primary key. Missing
keys, ambiguous identities and identifiers containing dots are shown with
reasons and cannot be selected. Catalog, SchemaCollection, Schema, TableCollection
and Table XML roots use the same table extraction and name resolution as the
existing job resolver. XML is limited to 16 MiB; malformed XML, DTDs and row data
are rejected. For explicit unique keysets, table/column mappings, upserts,
resumable jobs, leases, report paths and other advanced requirements, edit the
resulting YAML or author a job using the existing configuration API.

Review the unsaved draft, **Save as…** to a new YAML file, then **Use saved job in
data plan** and inspect the live plan. Existing execution confirmation and safety
checks apply. Draft creation is not approval, target DDL generation, automatic
parent-table closure, or a promise of source consistency. Selected foreign-key
cycles and other planning errors are reported by the existing planner. A completed
non-resumable draft cannot simply be rerun against a populated target; re-inspect
and choose the appropriate migration policy explicitly.

The additive `BulkMigrationJobDraftBuilder` API in `sqlapp-command` exposes
`inspect(path)` and `draft(snapshot, selectedTables, jobId, chunkSize, verify)`.
It composes the canonical Schema model, shared name resolution and job planner;
existing resolver behavior and YAML formats are unchanged.

## Edit data job YAML

Open **Job YAML editor** to create or edit a job without connecting to a database.
From **Data migration plan**, **Edit job…** beside the selected job path opens
that file directly in the editor. This action is disabled during data operations
and uses the editor's existing unsaved-edit confirmation. Cancelling, or a busy
editor, retains the current data review. Accepting the open invalidates that
review immediately, including when the file subsequently fails to load. No
connection or migration is started, and connection fields are retained. Save
and use **Use saved job in data plan** to return with the saved fingerprint,
then inspect a new plan.
**Open…** loads existing UTF-8 text, including malformed YAML so it can be repaired.
**New** starts an empty buffer, while **From Schema…** creates the safe common
INSERT draft described above. For advanced jobs, use the existing
[bulk migration configuration examples](../docs/gradle-plugin/bulk-migration.md)
to express your tables and policies. The raw editor does not invent data
fingerprints or execution policies.

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

## Search and navigate YAML

The editor's search row performs literal forward/backward search with optional
**Match case**. Searches wrap at either end and report when they wrapped or found
no match. **Ctrl+F** focuses the search field, **F3** finds the next match and
**Shift+F3** finds the previous match. Search selects text without changing it.
**Count matches** counts non-overlapping matches in the source buffer using the
same literal and case rules as Replace all. Counting preserves the caret,
selection and current SQL preview tab. Search-result messages have their own
row and are cleared when the search text, case option or source buffer changes,
so an old count is not presented as current. Counting does not save or execute
anything and is disabled while the editor is busy.

**Wrap lines** visually wraps long source lines at the editor width. It starts
off and is independent in the SQL and YAML editors. The choice lasts for that
editor's current application session, including New and Open; no settings file
is written. Wrapping does not insert newlines, change CRLF/LF, logical line and
column numbers, selection, search results, undo history or the unsaved marker.
It is a source-editor display option, not formatting or migration validation,
and is disabled while the editor is busy.

Search and replacement are literal; `$1`, backslashes and other replacement text
are not interpreted as regex syntax. **Ctrl+H** focuses the replacement field.
**Replace selected** changes only a selected match using the current case option.
If no matching selection exists, it selects a match first and requires another
click to change it. **Replace all** scans the original buffer for non-overlapping
matches, so replacement text containing the search term is not processed again.
Empty replacement text deletes matches. Empty searches do nothing. Replacement
results over the editor's 1 MiB UTF-8 limit are rejected without changing text.
Replacements affect the edit buffer only; they do not save or execute anything.

**Undo** / **Redo**, or **Ctrl+Z** / **Ctrl+Y** (**Ctrl+Shift+Z** also redoes while
editing YAML), keep up to 200 in-memory edits. A single replacement or an entire
Replace all operation is one undo step. Saving retains history; New, a successful
Open and accepting a Schema-generated draft start independent history. Failed or
stale loads do not erase it. New edits after undo discard the redo branch. History
is not persisted on disk and is disabled while editor operations are running.

Replacements, undo and redo invalidate syntax-check status. Returning to the exact
saved text clears the unsaved-edits marker, but **Use saved job in data plan**
still requires another syntax check. Undo and redo never modify the saved file.

Enter a one-based **Line** number and click **Go**, or use **Ctrl+L** to focus the
line field. **Column** is optional: leave it blank to go to the line start, or
enter a one-based column using the same Unicode character count as the position
indicator (tabs count as one). Press Enter in either field or click Go. The end
of the line is a valid position; CR/LF characters are excluded from its column
range, and an empty line accepts column 1 only. Out-of-range and malformed
numbers show the allowed range and preserve the selection instead of silently
choosing another position. Search, line navigation and edit history are disabled during editor reads, checks
and saves. Search and line navigation do not change the buffer or validation state.

## Open saved reports

In **Parsed report JSON**, use the read-only navigation bar to search literal
text, move to the next/previous match, count non-overlapping matches, go to a
one-based line/optional Unicode column, or wrap long lines visually. Ctrl+F,
F3/Shift+F3 and Ctrl+L operate while the JSON view has focus. The position label
shows the current line, column and selected character count. Replacement and
Undo/Redo controls are omitted; navigation never changes the report text.
Changing or clearing the report resets the query, line/column inputs, selection
and search result. Wrap lines remains a view preference for the current screen.

After successfully saving a Schema plan/result, data status, execution audit or
verification report, **Open saved report** opens that file in **Migration
reports** with its format already selected. It reads and validates the file
again; no live database checks or execution occur. The button refers to the
last successfully saved supported report, even after planning inputs change;
hover to see its path and type. Failed saves retain the previous link. Wait and
retry if the report viewer is already reading another file. Data execution
audits use the existing success or failure/pause format; the shortcut selects
the format actually written. These audits record the copy phase and do not
prove verification passed; open the separately saved verification report for
verification evidence.
The report viewer's **Details…** includes the loaded file, selected format and
summary, or the attempted file/format and diagnostics after a failed read.
Changing the file or format clears these details with the previous result.

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
If the URL, username or driver changes during saving, the result explicitly
states that the newer inputs are not saved; save again to write them. Password
changes do not affect this message because passwords are excluded. Disabling a
profile panel immediately disables both file actions, and reenabling it during
an operation keeps them disabled until that operation finishes.

There is no automatic profile store, home-directory setting, saved password,
startup connection or driver download. Driver classes still need to be on the
application's runtime classpath or loaded from **JDBC drivers**. Execution retains its existing connection
confirmation, password re-entry and live identity/fingerprint checks.

## Add JDBC driver JARs

Open **JDBC drivers**, optionally enter the JDBC driver class, then choose
**Load driver JARs…** and select the driver JAR and its dependency JARs together.
When the class is blank, JDBC service definitions in the selected libraries are
used to discover drivers. For a legacy driver without a service definition,
enter its fully qualified class, for example `oracle.jdbc.OracleDriver`.
The list shows the loaded JAR paths and detected classes; **Details…** explains
load failures. Only load trusted local JARs: loading initializes their Java code.
Loading does not open a database connection.

Successful additions are immediately available to Schema planning/execution
and both source/target data connections. Use the existing JDBC URL and credentials;
choose a class in **Loaded driver** and use **Use for Schema**, **Use for source**
or **Use for target** to fill that connection's driver class without typing it.
The destination tab opens with Advanced options visible; URL, username and
password are retained. Changing the driver invalidates the current reviewed
plan, so inspect/build a new plan before execution. Handoff is refused while
operations are running and can be retried. Class selection does not connect.
In **Advanced options**, specify the loaded driver class if multiple added
drivers accept that URL. Bundled drivers retain priority for automatic URL
selection. When adding another driver, select its common dependency JARs again
with the new driver JAR; dependencies already used by another library are allowed.
The current driver selection stays unchanged after adding another library.
Added libraries do not replace bundled classes. If the same JAR contains another
driver class that was not registered through its service definition, enter that
fully qualified class and select the same JARs again to register it explicitly.
Selecting the same path twice in one load, selecting only already loaded JARs
without specifying an unregistered class, or registering an already loaded driver
class is rejected. Duplicate classes are checked before initialization for
explicit names and discovered service providers. Restart to change driver versions.
JARs stay loaded for the application session; there is no unload, automatic
download, file copy or persisted JAR list. Select them again after restarting.
Connection profiles keep their existing format and do not include JAR paths.
Adding a JAR supplies a JDBC driver, not a new sqlapp database dialect.

Loading runs off the UI thread and disables duplicate loads. Wait for existing
operations to finish before loading. The application also blocks closing while
a load is running. A failed load leaves the previously loaded libraries available.

## Schema comparison

Use **Read DB…** beside either XML input to capture that side from a database.
Choose an existing writable destination folder, enter a connection (or load a
profile), and use **Test connection** or **Choose schema…** if needed. Blank Schema
means the current schema and fails explicitly if it cannot be resolved uniquely.
Editing the URL, username or driver clears the Schema choice back to current.
**Read and use Schema XML** reads full Schema metadata through the shared dialect
reader, including supported views, sequences and routines; it does not attach a
row iterator, dump table rows or execute migration SQL. Available objects depend
on the dialect and the account's metadata permissions. Connection pools request
read-only mode and close afterward.

Each capture writes UTF-8 `schema.xml` atomically inside a unique `schema-*`
child of the chosen folder. Existing XML files are not overwritten. The result
fills only the selected Before/After input and invalidates the old comparison;
**Compare** remains explicit and both sides must use the same database product.
Cancel or failure retains the previous inputs and review. The dialog blocks
closing while a capture or profile operation is running. Password copies are
cleared after operations and the password field is cleared on successful capture
or cancellation. Failure details redact the supplied URL and password. Reading
from DB is explicit; ordinary XML comparison remains offline. A failed write may
leave an empty capture folder; an input change during capture can leave a saved
XML file without selecting it, and the status gives its path.

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
SQL files.

The **SQL draft** tab renders the shared factory's SQL using the target
dialect's delimiter policy, including delimiter changes and resets when supplied. **Save SQL draft…** writes that exact UTF-8
snapshot to a `.sql` file, including SHA-256 fingerprints of the input XML bytes.
Later file edits do not regenerate the displayed draft during saving. Both
export actions protect the input XML files, including existing hard-link aliases.
SQL export is disabled when there are no SQL statements. This is a draft requiring
review of vendor delimiters, destructive changes, transactions and rollback;
it is not automatically registered or executed as a migration. Changing either
input clears both previews and disables their export actions. A failed save keeps
the comparison result available for retry.

After saving, **Use saved SQL directory** selects the file's parent as **Up SQL
directory** on the Schema migration plan screen. The default save name is a
17-digit timestamp followed by `_schema_migration_draft.sql`; choose a version and
description suitable for your migration history. The shared migration reader must
recognize the saved name as `<numeric version>_<description>.sql` and must find no
duplicate versions in that directory. The saved bytes must still match the draft.
Changing the comparison inputs disables this action until another draft is saved.
A busy plan screen refuses the handoff and leaves the saved draft available for retry.
This action clears the previous plan and keeps the existing connection, target
version, rollback directories and policy settings. Review those settings and all
SQL files in the selected directory before connecting to build a new plan; the
handoff performs no database connection or SQL execution. It does not infer a
next database version or generate rollback SQL.

## Build a Schema migration plan

Both connected plan screens offer **Focus on results**. This temporarily hides
the input settings and gives the reviewed summary, task tables and details more
space, especially in smaller windows. **Show input settings** restores the form
and its previous divider position. Inputs, review, audit results and execution
eligibility are retained; changing presentation never rebuilds or approves a
plan. The control is disabled while the screen is busy. SQL-directory, Job YAML
and driver-class handoffs restore the relevant inputs before editing them.

**Build URL…** and **Test connection** are available here as well as in Data
migration. The shared URL form supports PostgreSQL, MySQL, SQL Server and Oracle
service names. Applying the preview changes only the JDBC URL and invalidates
the old plan; cancellation retains the connection and review. Existing URL
options are not carried over. Credentials, driver classes, SQL directories and
policy choices are retained.

**Test connection** works before choosing any SQL directory. It uses the same
short-lived read-only connection check as Data migration and displays the product
and version. It does not inspect migration history, read SQL files, create a
history table or approve execution. Password copies are cleared after the check;
the field remains available for the next action. Errors are shown in **Details…**
with the supplied JDBC URL and password redacted.

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

The current distribution includes HSQLDB, PostgreSQL, MySQL, SQL Server and
SQLite JDBC drivers. Oracle's dialect is included, but its JDBC driver must be
provided separately. Use **JDBC drivers** to load additional local JARs without
restarting. Specifying a driver class alone does not install its JAR.
Alternatively, after `:sqlapp-swing:installDist`, launch on Windows with a local
Oracle driver on the runtime classpath:

```powershell
java -cp "sqlapp-swing/build/install/sqlapp-swing/lib/*;C:/drivers/ojdbc11.jar" com.sqlapp.swing.MigrationApplication
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

For an initial copy, open **Data migration plan** and enter the source and target
JDBC connections. Choose **New migration…**, select a folder for the migration
settings, and select the source tables to copy. **Save and review plan** saves
the settings and opens the connected plan review. Preparing YAML or Schema XML
by hand is unnecessary. This flow reads metadata and inspects the plan; copying
rows still requires **Execute reviewed job…** and its existing confirmation.

Each connection has **Test connection**. It checks only that connection and its
supported database dialect, and displays the database product/version. No job or
other connection is required. It opens a short-lived pool in read-only mode and
does not read table rows, create artifacts or inspect/execute a migration. Test
success does not check target table compatibility or approve execution; inspect
the plan afterward. Failures appear in **Details…**, with the supplied JDBC URL
and password redacted. Password fields remain available for the next action in
this session; temporary password copies are cleared after each test.

**Build URL…** on either connection opens an optional form for PostgreSQL, MySQL,
SQL Server or Oracle Thin service-name connections. Choose the database type and
enter a host, port and database/service name. Ports start with the conventional
value for the selected database and remain editable. **Use URL** applies the
preview to that connection only; usernames, passwords and explicit driver classes
are retained. Applying a URL invalidates the previous migration plan. Cancel,
Escape or closing the dialog retains the existing connection. No connection is
opened and no driver is downloaded by this form.

The form supports single hosts, including IPv6 literals and international DNS
names. Database names are encoded for PostgreSQL/MySQL and quoted for SQL Server;
unsupported names fail explicitly. Oracle accepts simple service names. Existing
URLs are not parsed into the form; only a recognized database type is preselected.
Existing URL parameters are replaced, not merged into the generated URL.
Use direct URL input for other databases, TLS options, multiple hosts, Oracle SID
or TNS/wallet connections, and other driver properties. Generated URLs do not add
credentials or override the driver's TLS settings. JDBC driver JARs still need to
be available through **JDBC drivers** or the application's classpath.

URL formats follow the driver documentation:
[PostgreSQL](https://jdbc.postgresql.org/documentation/use/),
[MySQL](https://dev.mysql.com/doc/connector-j/en/connector-j-reference-jdbc-url-format.html),
[SQL Server](https://learn.microsoft.com/en-us/sql/connect/jdbc/building-the-connection-url),
and [Oracle](https://docs.oracle.com/en/database/oracle/oracle-database/19/jjdbc/data-sources-and-URLs.html).

The source's current schema is used by default. **Choose schema…** in the source
connection reads visible Schema names through the shared dialect reader and
opens a literal-filtered list. No target connection or job is required. Select
**Use selected schema**, or **Use current schema** to restore the default. The
current choice is always shown below the source connection. Alternatively enter
the exact **Source schema** in **Advanced options**.

This selection affects **New migration** only; it does not rewrite a saved job.
Editing the source URL, username or driver clears the choice back to the current
schema. Cancellation or discovery failure retains the previous choice and review.
Listing schemas uses a short-lived read-only pool, does not read table metadata
or rows, and creates no files. Supplied passwords and JDBC URLs are redacted from
failure details; temporary password copies are cleared. Ambiguous schema names
fail explicitly: select a specific database/catalog in the connection or use a
saved job with resolved identities.

Table eligibility and primary key restrictions are shown in the selection list.
**Copy options** exposes the
optional job ID, chunk size and verification choice; verification starts enabled.
The initial copy requires existing, same-named, empty target tables. It does not
create target tables, replace existing rows or enable checkpoint resume.

The selected folder receives a unique `migration-*` child containing
`source-schema.xml` and `migration.yaml`. Keep these files together; the saved
job pins the Schema fingerprint and can be reopened or edited later. Connection
passwords are not written to these files. Cancelling table selection may leave
the captured Schema XML in that child folder.
The table selection screen shows the settings destination before saving.
**Cancel**, **Escape**, or closing that dialog retains the previously selected
job and review. Cancellation is disabled while settings are being saved.
Missing JDBC URLs identify the source or target field to complete; a saved
connection profile can also supply that connection's non-secret settings.

Alternatively, select an existing job YAML and enter the source and target
JDBC connections. **Connect and inspect plan** resolves the job
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
that operation failure and verification status separately. Use **Last execution** and **Last verification** to switch between the copy audit
and per-task verification counts, mismatch counts and JSON details.
The **Mismatch chunks** tab lists each recorded task/chunk, expected and actual
row counts, hashes and first/last keys. Chunk indexes are zero-based. Scroll
horizontally to see all fields. **Find in recorded details** (Ctrl+F) filters all
columns by case-insensitive literal text, including task names, hashes and keys.
Its row count refers to recorded details, not omitted mismatches. **Clear** restores
all recorded rows. Chunk indexes and row counts sort numerically. Select a row to
read its complete values in the lower detail pane; select text there and copy it
with Ctrl+C. Changing the filter clears the selection to avoid stale row details. The displayed total mismatch count includes
omitted details: the report's per-task `maxReportedMismatches` limit can make this
larger than the recorded rows. Different keyset fingerprints are noted separately; the shared verifier
remains the authority for the MATCH/MISMATCH outcome. Missing hashes or keys are labeled
`not recorded`. The same tab is available when opening saved verification JSON
in **Migration reports**.
**Save verification…** exports the completed verification snapshot as standard JSON,
including when a mismatch made the overall operation fail. It uses the resolved
isolation and mismatch limit from the executed job; later YAML edits do not change
the saved result. Verification must be enabled and must have completed.
The snapshot timestamp is generated by the GUI after execution; a configured YAML
`verification.targetFile` remains a separately generated artifact. Open either in
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

The **Tasks / migrations** list is shared by Schema plans, data plans, execution
results and saved reports. **Filter tasks / migrations** performs a
case-insensitive literal search across every displayed column, including long
cell values; regex characters are treated as text. The count distinguishes
visible rows from all rows. **Clear** restores the list, and Ctrl+F / Cmd+F
focuses this filter while the list has focus.

Select a row to read its full column values in the read-only area below the
table and copy selected text. Row details follow the correct model row after
sorting or filtering. Changing the filter clears the selection, and displaying
a new review or clearing inputs resets the filter and old row details. Filtering
changes only the view: the plan, execution approval, JSON and saved artifacts
retain the complete result.

Count columns sort numerically in the main list as well as recorded-detail
tables: Schema statement counts, data checkpoint/processed row counts, completed
chunks and verification counts. Missing checkpoint counts remain `not recorded`
and sort after numbers in ascending order (before numbers in descending order);
they are never treated as zero. Copying follows the resulting visible order.
The live data plan also sorts execution order and chunk size numerically.

When rows are added, removed or edited in the current list, visible counts,
filter membership and copy availability update with the table. Replacing the
list detaches the previous model so later changes to it cannot affect the view.

**Copy visible rows** copies the entire visible list with column headings as
tab-separated text, in its current sorted row order and displayed column order.
Use Ctrl/Cmd-click or Shift-click to select multiple rows, then **Copy selected
rows** to copy just that selection with the same headings, ordering and limit.
With multiple rows selected, the detail area identifies the selection count and
shows the first selected row. Filtering clears the selection; copying selected
rows is disabled when nothing is selected. Both copy actions appear below the
search field so they do not reduce its available width.
It is also available in recorded-detail tables. Tabs, CR/LF and quotes inside
cells are preserved using quoted cells and doubled quotes. The action does not
require a selected row and does not copy rows hidden by the filter. Empty lists
disable copying. Copy failures are displayed for retry; changing the filter or
review clears the previous copy feedback.

The complete clipboard text is limited to 1 MiB in UTF-8, including headings
and separators. Oversized results are rejected before touching the clipboard;
filter fewer rows or use the existing full report export. Copying never truncates
the result or changes the plan, source data, saved report or execution approval.

Open **Migration reports**, select a JSON file, then click **Detect and load**.
The six supported report types are identified from top-level JSON fields, not
the filename. Detection still delegates to the existing typed readers for
version, fingerprint and consistency validation. Unknown or ambiguous formats
fail explicitly; **Choose format** reveals the type selector and **Load report**
for the existing explicit workflow. Known reports opened from another screen
retain their supplied type and bypass detection.

After a successful load, **Recent reports** offers up to ten paths for this
session. Select an entry and **Open recent** to reread and validate the current
file; choosing an entry alone does not load it. Automatically detected reports
are detected again, while explicitly typed reports keep their selected reader.
Paths are normalized, deduplicated and ordered by most recent successful load.
Failed or superseded loads do not enter history. Missing or changed files are
reread normally; an old cached review is never substituted for a failed read.
**Clear history** removes the list without clearing the displayed review.
History stores only paths, type and read mode in memory, is not written to disk,
and is cleared when the application closes. History actions are disabled during
loading. The row is hidden until a report has loaded successfully.

Automatic detection accepts one strict UTF-8 JSON object up to 16 MiB; duplicate
keys and trailing JSON values are rejected. For larger or custom reports, choose
the intended format explicitly and use its existing reader. Detection and typed
validation use the same temporary byte snapshot, removed before publishing the
review, so concurrent edits cannot mix file revisions. The original report is
not modified. Changing the selected path/type discards an in-flight result.

The summary shows the saved outcome and plan fingerprint;
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
| Data verification result | **Save verification…** or job YAML `verification.targetFile` (`BulkMigrationVerificationReportIO`) |

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
module. JDBC and migration business logic remain in existing modules. The desktop
adds only the FlatLaf and RSyntaxTextArea dependencies described above; existing
public APIs and artifact formats are unchanged.

Tests (headless panel state checks and isolated in-memory HSQLDB planning/execution):

```powershell
.\gradlew.bat :sqlapp-swing:test
```

`DataMigrationCreationIntegrationTest` covers source discovery, generated job
handoff and automatic live inspection together, using in-memory source and target
databases. It checks that a missing target table leaves execution disabled and
is not created by inspection, and that source/target row counts remain unchanged.
The selection dialog is supplied by a test callback; this does not verify native
file chooser interaction or keyboard input. Existing `JobDraftPanel` tests cover
table selection and saving separately.

## Execution follow-up

### Schema-only transfer between database products

Open **Direct JDBC transfer**, enter source and target connections, and choose
**New direct job…**. Select **Schema only**, choose tables and save the job.

For optional table/column name changes, open **Job settings…** and select or drop
a **Name mapping YAML**. Unspecified names stay unchanged; PK/FK/index references
follow mapped names. The same original saved job and relative mapping path work
with Gradle. Review **Schema SQL** and expression warnings before execution.
Mapping supports schema-only creation/differences and direct JDBC copy/verification.
For data copy, use the same **Job settings… → Name mapping YAML**; missing-table DDL,
INSERT and verification share the saved mapping. Keyed reading uses NOT NULL keys;
keyless direct INSERT uses full scan with duplicate-preserving multiset verification.
Target empty/compatible-table requirements remain in force. **Job settings… → Edit names…**
opens an offline Tables/Columns editor and saves the shared mapping YAML. Save job
settings afterward to attach the path, then review again. Mapping saves are immediate
and remain saved if job settings are canceled. Existing files are protected against
concurrent changes; comments/formatting are rewritten. Choose/drop the captured
**Source Schema XML** and click **Load columns** to append source columns and their
informational types for listed tables while retaining existing names. The default
path is `source-schema.xml` beside the job. This is an offline snapshot, so review
against the databases before execution. Same-name columns need no YAML overrides;
removing a row removes only the rename, not the copied column. Target-type editing,
value conversion and CSV mapping remain unsupported. See the
[mapping format and limitations](../docs/gradle-plugin/direct-transfer.md#optionally-map-table-and-column-names-schema-only).
The name editor's **Optional target preview** accepts a product and explicit
version for **Check Schema** and naming-rule Schema checks. Its optional columns
show the target SQL builder's column-definition preview and any unsupported
preview issue beside source model types, mapped names and source PK/NULL metadata.
They are hidden when no preview is requested. Product/version changes clear old
evidence. Preview controls are session-only; saved mappings and Gradle jobs are
unchanged. This does not read existing target columns, add target-only keys,
convert values or replace the complete table-factory SQL in database Review.

In the main name editor's Schema check, **Edit selected column name** returns to
the selected column's target-name cell using the common projection's verified
mapping key. It preserves authored names and handles filtering/reordered views.
An unloaded column is added as an unchanged display row; name changes still need
the usual save and database Review. Standalone read-only previews do not offer
this action. Builder preview limitations now show a bounded reason, including
version requirements where provided, while preserving other column metadata.

For multi-table Schema checks, **Source → target table** selects one exact
captured source identity or **All tables**. **Preview issues only** limits rows to
recorded target-builder preview limitations, and combines with table selection
and literal search. It does not match words in column names or treat general
compatibility warnings as column-preview failures. The preview count summarizes
all checked columns; the existing row count shows the current filtered view.
**Clear** clears text search while retaining table/issue filters. New checks and
invalidated evidence reset these filters; no-target and single-table checks hide
irrelevant controls. Editing a selected column and copying visible rows retain
their existing behavior and do not change the transfer scope.

Selecting a column with a recorded preview issue now shows **Preview issue** and
**Target definition preview** first in the detail text, followed by all source and
target metadata. Ordinary rows keep their normal detail order. The table order,
sorting and tab-separated copies are unchanged.

Recorded-detail tables also provide **Copy displayed details** for the plain text
shown below the table. With multiple selected rows, that text explicitly describes
the first selected row; use **Copy selected rows** for all selected rows as a table.
The same 1 MiB UTF-8 clipboard limit applies without partial output. Copy failures
remain visible and can be retried; changed/cleared selections cannot copy stale
details. Replacing a report's table model clears old column-based display scopes
and detail priorities. These controls do not modify the report or saved job.

When captured target previews contain issues, **Previous issue** / **Next issue**
(Alt+P / Alt+N) move through only visible issue rows in the current sort order,
wrapping at either end. They retain table selection, text search and the
issue-only filter, and preserve the current horizontal scroll position. The
position label counts issues in that filtered view. If none match, navigation
is disabled and the view says so; filters are not automatically cleared. An
ordinary selected row is skipped on the way to the next/previous issue. With
multiple rows selected, navigation starts from the first selected row and ends
with one issue selected. Reasons remain first in the detail text, and the normal
edit/copy actions stay available. Busy, invalidated and replaced results cannot
navigate old rows. This does not change migration scope or execution approval.

After **Check Schema**, **Save preview report…** in the name editor saves a
standalone UTF-8 HTML report that can be opened in a browser. The save dialog
defaults to **All checked columns**; choose **Current filtered view** to retain
only visible columns in their current sorted order. The report labels its scope
and included/total counts, preserves source/mapping fingerprints, resolved builder,
raw mapping keys and full-check warnings, and highlights column preview issues.
Source file names are included without absolute source paths. Metadata and SQL
text are escaped as text, with no external assets or scripts.

Reports preserve captured evidence and can include unsaved name proposals. Saving
one does not save mappings or jobs, verify current target state, or grant execution
approval. Input changes invalidate the save action until a new check. File writes
run off the UI thread; editing/closing waits for completion. Source/job/mapping
paths and existing destinations are protected, and reports are limited to 16 MiB.
Timestamped default names retain earlier reports. Destination checks are optimistic,
not a cross-process file lock; choose a new filename for each retained snapshot.

The Columns tab offers a **Source table** selector for exact table identities,
with **All tables** to return to the complete list. Double-click a source-table
cell in Tables to open its columns. Search and renamed-only filtering apply
within that table; new column rows inherit its identity. This changes the editor
view only and does not exclude any data from the transfer.

The Tables/Columns tabs offer literal **Find**, sorting, **Renamed only**, counts
and **Reset selected names**. Filters affect display only; saving includes hidden
overrides. Reset/remove actions operate on the selected rows in the displayed
order and do not exclude source tables or columns from the transfer.
**Naming rules…** previews case changes and optional Unicode-space-to-underscore
replacement for selected target names. Only **Apply preview** changes the mapping;
hidden/unselected rows remain unchanged. **Undo last rules** restores the last
batch for that tab if its rows have not subsequently changed. Stale previews and
undo fail before changing any row. Results are saved as ordinary explicit mapping
names for GUI/Gradle, with no global rules. Run Check Schema and database Review
after applying, since rules can produce colliding names.
The naming preview also offers **Check proposed Schema** using the source XML
chosen in the name editor. It checks the full proposed mapping, including hidden
overrides, before changing any cells. Results and failures appear in its Schema
check tab; stale asynchronous results are discarded. A failed check still allows
applying an unfinished draft for correction, while database Review remains required
for execution. No files or databases are changed by the proposed-name check.
**Check names** shows blank/invalid names and duplicate source entries together
in **Input checks**. Double-click a problem or use **Go to selected field** to
reveal its field despite filters/sorting. Editing clears stale problem locations;
saving checks the latest cell values again. Successful input checks do not replace
the database Review or authorize execution.
**Preview YAML** shows the generated mapping in a read-only RSyntaxTextArea view
with search/line navigation and **Copy YAML**. It uses the save formatter and
includes hidden overrides. Editing clears the old preview and disables copying
until refreshed; previewing/copying writes no file and does not execute a transfer.
**Source Schema XML → Check Schema** checks the mapping against captured metadata
through the same shared Schema projection used by execution. **Schema check**
shows resolved table/column names, source logical types, source PK/NULL properties,
warnings and source/mapping fingerprints. Unknown columns, out-of-selection
mappings, collisions and missing referenced parents are reported without database
access or file writes. Edits or Schema-path changes clear the result. It remains a
snapshot: target existence/types, generated keys and DDL require database Review.
Closing the name editor protects unsaved name changes (including invalid edits);
choose to keep editing or explicitly discard. Filters/previews and unchanged source
column loading do not trigger this prompt. Saved mapping paths are attached to
Job settings relative to the job folder when possible; other drives stay absolute.
Save settings to keep that reference and review again before running.
**Job settings… → Edit tables…** reopens the table picker using a captured source
Schema XML. Read the Schema, change Include checkboxes, optionally include referenced
parents, then **Use selected tables**. Filters retain hidden selections. Changes
stay in the settings form until **Save settings**, followed by a new Review.
Direct keyless INSERT, Schema-only eligibility and CSV key/type restrictions reuse
the existing picker behavior. All other settings are retained. For excluded-table
name mappings, **Edit names… → Review scope…** previews whole table/column groups
outside the selection. Only **Remove listed groups** changes the editor; retained
mappings and source-type rows remain intact. Save mapping and settings afterward.
Unknown/duplicate source aliases and stale removal proposals are rejected rather
than silently deleted. This is scope cleanup, not a replacement for Schema checks
or database Review. CSV changes require a new export;
completed packages are not edited. A changed Schema/path cannot publish an old
selection, and missing saved identities are reported instead of silently omitted.

For target-only generated primary keys, **Generated PK column** defaults to `ID`
and can be changed in New direct job, New file job (optional import settings),
or Job settings. The shared YAML option is
`generatedKeyColumn`; it requires `CREATE_MISSING_WITH_IDENTITY`. Choose an unused
name if the source already contains `ID`. Set `SQLAPP_ROW_ID` explicitly to retain
the earlier default; existing target tables are unchanged.
The table picker allows tables without primary keys in this mode. Specify an
existing target schema or leave it blank to use the target connection's current
schema. Review the **Summary**, **Warnings** and **Schema SQL** tabs, then confirm
**Execute reviewed transfer…**. **Job settings…** can also switch a saved direct
job to schema-only. The **Gradle** tab runs the same saved job with
`transferDatabase`; no hand-authored migration SQL or Data Job YAML is required.

The **Schema SQL** tab in Direct JDBC transfer offers **Save SQL draft…** and,
after saving, **Edit saved SQL…** to open the built-in SQL editor. A fresh review
captures the actual target dialect's statement delimiters, warnings and job/plan
fingerprints. Saving writes the displayed UTF-8 snapshot atomically, protecting
the source job, captured Schema, generated data-job and audit files, including
hard-link aliases. No database connection or SQL execution is started by saving
or editing, and the shared transfer job is unchanged. Input changes or another
result clear the saved-file handoff. A save failure leaves the review available.

Execution-result drafts include the full planned SQL, execution phase, completed
statement count and uncertain attempt. Some statements may already be applied;
inspect and repair the target before retrying. The draft is not a resume script
or execution approval. Edit and execute manual corrections through the separate
SQL migration workflow, reviewing its versioned SQL directory and policies.
Saving does not apply the transfer job's permission settings to that workflow.
No draft export is offered when there are no Schema statements.

Schema-only creates missing selected tables, constraints and indexes using the
target dialect. It does not copy or verify rows. Existing tables are skipped
with warnings without comparison, alteration or clearing, even when they contain
data. Include referenced parent tables. Views, independent sequences, routines,
triggers and other schema objects are outside this mode's scope and are called
out in warnings. Default WARN permits trying generated SQL; ERROR rejects the
scope/compatibility warning. Review generated SQL and repair vendor differences.
DDL can remain after failure; inspect retained completed/uncertain statements
before another review. The default CREATE_MISSING mode does not create the target
schema namespace or alter existing tables. Use Schema migration plan for reviewed
SQL history.

To optionally update existing selected tables, enable **Apply differences to
existing selected tables** under **Schema only** in the new-job dialog or
**Job settings…**. The shared job uses `schemaMode: APPLY_DIFFERENCES`. Review
the generated CREATE/ALTER/DROP SQL and warnings. Columns, constraints and indexes
can change; unselected tables are outside the intended modification scope.
Components are matched by name. Column renames are not inferred: differing names
appear as removal/addition and can lose old column values when explicitly allowed.
Database-enforced dependency effects still require review. Source and target
definitions are read again before execution, and a changed plan requires another
review. No source rows are transferred and no row verification is performed.

Potentially data-impacting changes remain reviewable but disable execution by
default. **Allow removals and changes that may affect existing data** saves
`allowDestructiveSchemaChanges: true`. Enable it only after reviewing SQL, then
save and review again. Java and Gradle enforce the same gate. Target factories
may include target-data preparation, such as a PostgreSQL UPDATE to fill a new
default column; this also requires permission and appears in Schema SQL.
Progress includes those statements. There is no automatic rollback, reset or
data restoration after partial failure.

Unsupported table changes produce `[SCHEMA_DIFF_UNRESOLVED]` warnings under WARN;
no SQL for that table is included, while other selected-table SQL remains
reviewable. Completing generated SQL does not prove full Schema equivalence.
Inspect warnings and fix remaining definitions manually. ERROR rejects
compatibility warnings. The existing default still skips existing tables.

Existing jobs retain JDBC row-transfer defaults. New schema-only jobs use
`dataMode: SCHEMA_ONLY`, `schemaMode: CREATE_MISSING`, and omitted or false
`verify`. They require the updated application/plugin; no JDBC credentials are
persisted. See the [shared job guide](../docs/gradle-plugin/direct-transfer.md).

Results with warnings include a **Warnings** tab with literal search, full selected
row text and copy actions. CSV package review lists export warnings with their
task and column alongside target compatibility warnings. Target warnings retain
their original messages without guessing identifiers from text. Import execution
results list retained target warnings; export warnings remain in the manifest and
package review. Changing inputs or starting another transfer or package inspection
clears the old warning tab. Viewing or copying warnings does not approve execution.

The warning table distinguishes data-impact permission, unresolved manual work,
and generated-key follow-up using stable warning codes. Uncoded or future warnings
remain general compatibility warnings; messages are preserved without inferring
identifiers or categories from English prose. **Next action** offers guidance and
is included in selected-row details, filtering and copying. It does not grant
permission, modify a job, synchronize counters or recheck the database.

Direct JDBC and CSV import results show next steps near the top of Summary.
The first line describes completion or the stage where execution stopped in plain
language; the recorded phase remains visible for troubleshooting. A completed run
does not imply that optional row verification was enabled or all warnings resolved.
Completed runs remind users to inspect compatibility warnings, check generated-key
counters when listed, and test application reads and writes before switching
connections. Disabled row verification is called out explicitly. Interrupted runs
instead direct users to committed rows, attempted DDL and target repair before
another review; retrying never implies clearing the target. These instructions do
not perform additional database operations.

The direct-transfer and CSV import results also provide a searchable **Schema statements** table. It preserves SQL execution order and shows reviewed, completed, uncertain-attempt and unexecuted statements. Select a row to inspect its full SQL. Recorded completion does not guarantee transaction commit; uncertain attempts may have taken effect. The table does not authorize execution or provide automatic resume.

After review, **Table preparation** lists target catalog/schema/table identities
and the common planner's recorded intent: create missing, use existing for data,
skip existing without comparison, or review/apply differences. It supports search
and selected-row details. Direct execution results keep this reviewed intent;
it is never shown as execution completion. Consult Schema statements for SQL
progress and Warnings for unsupported/manual work. File import review offers the
same table evidence; its outcome shows SQL progress separately. Input changes
and failed actions discard stale table evidence. No additional database queries
or row counts are performed for this display.

Select a table with recorded SQL and choose **Show table SQL** to open its exact
task in **Schema statements**. Its **Table / task** selector combines with text
search; **Clear** keeps the selected task, and **All tasks** restores every
statement. Global order numbers and recorded statuses are preserved. New Schema
difference plans retain the generating table's task ID; old reports retain their
original IDs. Tasks without SQL cannot use the navigation button and may still
have warnings requiring manual work. This filters the display only: inspect the
full Schema SQL and dependencies before execution, and review again after changes.

After saving, **Open saved preview** opens that HTML snapshot in the default
browser. Its saved fingerprint is checked in the background before opening;
missing or changed files produce an actionable message and can be retried after
saving a new report. The editor waits for this check before allowing other
operations or closing. Current name edits do not replace the last saved snapshot.
If browser integration is unavailable, the message includes the file to open
manually. This check is neither a file lock nor database validation or execution
approval.

In the Schema check table, double-click a selected row or press **Enter** to
open its verified target column name in the mapping editor. This uses the same
identity checks as **Edit selected column name**, including filtered/sorted
views. Multiple selections, busy controls and invalidated checks cannot open an
editor. Read-only previews retain ordinary Enter navigation. These shortcuts do
not apply type conversions, save mapping files or approve execution.

Schema statements can be filtered by **Recorded status**, together with the
exact table/task and text search. Status counts describe all recorded SQL,
before display filters. After a partial Schema preparation failure, **Show
uncertain attempt** clears the task/text scopes, shows the uncertain SQL and
selects it for inspection. It never retries SQL: an uncertain statement may
already have taken effect. **Show table SQL** resets the status scope so all
recorded statements for that task are visible. Global statement order, recorded
statuses and the execution plan remain unchanged.

Direct transfer and CSV import summaries now show the number of unexecuted
Schema statements, the uncertain statement's global order/task (when recorded),
and the first unexecuted statement's order/task. These use the same recorded
status classification as the Schema statements table. Uncertain SQL is separate
from unexecuted SQL and may already have taken effect; the summary points to
**Show uncertain attempt** for inspection. The displayed boundary is not a
restart instruction and does not query or reset the target database.

The **Table preparation** tab shows full-review counts for create, use-existing,
skip and difference preparation. Choose **Reviewed preparation** to filter a
category, then combine it with text search, sorting and **Show table SQL**.
Counts remain totals for the captured review; filtering changes only the display
and never excludes tables from the job. SQL navigation requires one selected
row with recorded SQL. A skipped table has not been compared, and recorded
preparation is not proof of successful execution or schema equivalence.

## CSV, TSV and JSONL file transfer

The **File transfer** screen supports CSV, TSV and JSONL. Use **New file job…**, select
**File format → JSONL**, choose tables and save. The same choice is available
from Access file migration. CSV remains the default. JSONL uses JSON `null`;
the CSV/TSV NULL-token field is disabled and its value is omitted from the job.
Export, inspect/review and confirmed import use the same package workflow;
import requires only the target connection. The package summary shows its format.
The GUI-saved YAML works unchanged with `exportTransferFile` / `importTransferFile`.

JSONL has one flat object per data row, using captured source column names as
keys and native JSON numbers, booleans, strings and null. Integers and decimals
are read without floating-point rounding. Text line breaks are escaped inside
the object; NULL, empty text and literal CSV NULL tokens remain distinct. Empty
tables have empty data files. Export defaults to UTF-8 and stores the encoding in
the manifest. Field limits are 1 MiB characters and JSONL rows are bounded at
16 MiB characters. Missing/extra/duplicate fields, nested values, wrong scalar
types, invalid Unicode, invalid keys/counts and altered package members fail
validation. These checks do not authorize import or reconnect to the source.

Automatic import retains these constraints across formats: empty existing targets or explicit missing-table creation, supported scalar
and temporal values, optional pinned name-only mappings, and no binary/LOB transport.
This feature reads completed sqlapp packages; it does not infer a schema from
arbitrary standalone JSONL files. JSONL support adds no dependencies or task
properties. Existing CSV jobs/packages and Java save overloads retain defaults.

## TSV packages

**File transfer → New file job… → File format → TSV** saves a tab-delimited
package job usable unchanged by the Gradle file-transfer tasks. TSV shares CSV's
quoted-field parser, scalar/temporal conversion, NULL-token collision checks,
package integrity, target review, missing-table creation and row verification.
Tabs, quotes and line breaks inside text are quoted and retained. The package
contains `table-N.tsv` members with a header; it is not a plain split-on-tab
format. Input and output use the encoding recorded in the package (UTF-8 by
default). A NULL token is optional; when configured it cannot contain a tab, quote or
line break. Empty text and NULL remain distinct. CSV stays the default; JSONL
continues to use native null without a token. Existing key/type/mapping/LOB
constraints still apply. Filtering or choosing a format never starts migration.

## Character encoding and CSV/TSV headers

**New file job…** and **Job settings…** expose **Character encoding** (default
**UTF-8**) and **CSV / TSV header**. Choose an encoding such as windows-31j,
Shift_JIS or UTF-16LE, or type another Java-supported charset name. Invalid
encodings prevent saving, and unrepresentable characters fail export rather
than being replaced silently. The saved encoding applies to both export and
import and is displayed in the package summary.

Header choices are:

- **Validate names and skip (default)**: write a header on export; check its exact
  captured column names/order and consume it on import.
- **Skip without checking names**: write a header on export and consume one
  parsed header record on import, without using its names for column mapping.
- **No header row**: omit the export header and retain the first import record
  as data. Empty tables then produce empty data files.

A header record may contain quoted line breaks; this is not a physical-line
skip. SKIP/NONE use captured Schema column order. Missing expected headers and
invalid row counts still fail; no arbitrary rows are discarded. JSONL has no
header and disables this control. Existing jobs/packages without `headerMode`
retain name validation for CSV/TSV and NONE for JSONL. Existing Java constructors
remain available. Changing an original saved job requires a new export; completed
packages keep their recorded settings and cannot be edited through Job settings.

## Preview packaged data before import

In **File transfer → Import**, choose a completed package or its manifest, then
**Preview package data…**. Select a table and **Load sample**. The default is 20
rows, adjustable from 1 to 100. Reading runs off the UI thread and opens no source
or target connection. It uses the same captured Schema, encoding, header mode,
NULL decoding, scalar/temporal conversion and key ordering as import.

The grid shows source column names/types and sampled values. SQL NULL is `null`,
empty text is `""`, and literal text `null` is `"null"`. Embedded whitespace and
line breaks in text are escaped. Values longer than 512 Unicode code points are
marked truncated; total rendered cell text is limited to 1 MiB characters.
Search, sorting and copy actions operate only on the displayed sample. Changing
the table or limit clears old evidence; failures permit retry and late results
cannot replace a newer selection.

The displayed total is the manifest's recorded row count, not a new row-count
query. Package hashes are checked before/after sampling, which can read every
file's bytes even though only the selected table's leading rows are parsed.
Preview does not validate unsampled rows or target conversion and grants no
import approval. Use full packaged-value validation and target Review before
import. Existing warnings remain in package inspection/review. The package is
never modified and saved jobs/Gradle task settings are unchanged.

### Recheck a package without closing the preview

**Reload package** rechecks the selected package and refreshes the table list.
It clears the old sample, fingerprint display and full-check evidence immediately;
if the same task and catalog/schema/table identity remain, the selection is restored even when table order changes.
An inspection failure keeps Reload available so repaired files can be checked
again without closing the dialog.

**Validate all values** invokes the shared import validator on the pinned
manifest. It checks every packaged row, type, key and recorded count off the UI
thread, without opening a source or target connection. Success is recorded only
for that fingerprint and displayed separately from the sample. Failure clears
old sample/check evidence and permits retry. Table controls and copy buttons are disabled
while checking; duplicate clicks cannot start overlapping work. Reloading clears
the recorded success, and a later sample failure also withdraws it.

Full file-value validation does not check live target compatibility, authorize
import or prove target row equality. Use target Review and the existing explicit
import confirmation afterward. These controls change no package, saved job,
command API or Gradle setting.

Full-check evidence distinguishes **not run**, **in progress**, **passed for this
fingerprint**, and **failed**. A sample read is never labeled as a successful
whole-package check. Reload resets the state to not run; a failed full check
keeps a failure state until retry or reload.

## Export eligibility is separate from import eligibility

File export does not require a primary key or a numeric key. Keyless/incomplete-key
tables use the shared streaming full-table reader and retain duplicate rows,
NULLs and empty strings; output order is not a restart/keyset guarantee. Complete
keys retain the existing keyed export path. All formats (CSV/TSV/JSONL) keep the
same encoding/header/NULL-token and scalar conversion checks. No target connection
is opened, and target schema/preparation settings do not affect export.

**New file job…** and file **Job settings…** now keep target schema, Create Missing
Target, copy verification and chunk controls under **Optional import settings
(no target access during Export)**, collapsed by default. Existing saved settings are
retained; opening or closing the group does not change them. Export-related file
settings and table selection remain visible. A key is not required in the export
table picker, including Edit tables on saved jobs.

Package import also accepts tables without a PK or with a text PK. Numeric NOT
NULL primary keys keep ordered keyset reads; other tables use full-scan INSERT
into empty targets. `[FILE_FULL_SCAN_INSERT]` warns about this route; WARN permits
import and ERROR rejects it at target Review before any writes. Optional missing
table creation preserves foreign-key dependency order. Every row is retained,
including duplicates and NULLs. Verification compares unordered SHA-256 hashes
with duplicate counts; hash-sort chunk positions are not file row positions.
This route does not provide MERGE, resume or key-based repair. Preview and full
package validation use the same value rules without requiring a numeric key.

## Default NULL and empty-text handling

New CSV/TSV jobs default to **Unquoted empty fields → SQL NULL**, with **Use
explicit NULL token** unchecked. An unquoted empty field (`,,` or adjacent tabs)
is SQL NULL; a quoted empty field (`""`) is empty text. Export leaves SQL NULL
unquoted and quotes empty strings, including single-column NULL records. NULL
rows are not discarded as empty lines. UTF-8 remains the default encoding.

Choose **Empty text** to read unquoted empty fields as empty strings instead.
This enables an explicit NULL token so exported SQL NULL still has a distinct
representation. A configured token is reserved: matching real text fails export
rather than silently losing the distinction. Quoted empty strings remain empty
strings in both modes. Non-text columns do not infer zero/false from empty text.
JSONL keeps native JSON null and disables these controls.

The shared YAML supports only `emptyFieldMode: NULL` or `EMPTY_STRING`. A
configured token does not change empty-field interpretation. New token-free jobs
need no extra NULL settings.

## Understand execution failures

Direct JDBC transfer, schema-only execution and file-package import show a
**Reason** and **What to check next** near the top of Summary when an execution
fails. Known SQLStates distinguish constraint violations, value conversion,
permissions, authentication, connection loss, SQL/object resolution, transaction
failures and timeout/cancellation. SQLState and the vendor code remain visible
for investigation. A recorded verification mismatch points to the mismatched
tables/chunks and target precision. Unknown causes are explicitly described as
unknown; messages are not guessed from driver text.

Details and the saved result JSON retain the same structured `failureDiagnostic`.
Raw driver messages, SQL values and exception causes are omitted. Existing DDL
uncertainty and committed progress remain visible. The cause category does not
identify a failed source row, prove rollback or approve a retry; inspect the
retained target state and review again after correcting the problem. No new job
settings are needed, and successful results have no failure diagnostic.

## Inspect recorded verification by table and chunk

After direct JDBC execution or file-package import, **Verification** lists the
recorded tasks, reviewed target names when available, status, expected/actual row
counts, chunk counts and mismatched chunk counts. Search or sort the table, choose
**Show mismatched tables**, then select a task and **Show chunks**. Summary links
to this workflow. Schema-only runs and results without comparisons do not create
verification tabs. Missing tasks are not inferred to have matched or zero rows.

**Verification chunks** shows at most 200 records per page, with **Previous page**,
**Next page**, and **Mismatched chunks only**. Search, sort and copy affect the
current page. Changing page/filter/task resets the page search and selection.
Select a chunk to read full hashes in the existing detail pane. Indexes are zero
based, not source row numbers; unordered multiset hashes compare duplicate counts
and their sorted positions cannot locate source records or drive repair.

These tabs display captured results without opening files or database connections,
rerunning verification, or approving execution/retry. A partially verified result
can contain matched recorded tasks even though the transfer failed. Target labels
come from the recorded review, not live metadata; file imports use them only when
the retained review's manifest and plan fingerprints match the outcome. Missing
identity evidence is shown as **not recorded**. Existing JSON evidence remains in
Details. Job YAML, Gradle properties and public result APIs are unchanged.

## Inspect retained execution progress

After direct JDBC transfer, schema-only execution or file import, **Execution
progress** collects recorded task evidence in one searchable table. It shows
reviewed target names, the stopped task, copy completion evidence, last recorded
committed chunk counts, verification and Schema SQL progress. **Show stopped
task** and **Show recorded issues** narrow the display; **Show all tasks** restores
it. Select a task and use **Show task SQL** or **Show task verification** to open
the matching recorded details. An uncertain SQL attempt is selected automatically
when navigating to that task's statements.

Copy completion, committed progress and verification are separate evidence.
Missing chunk progress is shown as **not recorded**, not zero. A completion event
without a returned copy result remains distinguishable from a returned result
with a recorded processed-row count. No copy completion record does not prove a
task never started. In schema-only runs, row copy is **not requested** and row
verification is **not applicable**. SQL status comes from the existing recorded
statement sequence; later tasks are not marked as failed merely because a prior
task stopped.

Known tasks and target names are read only from the retained outcome/review.
File-review context must match the outcome's manifest and plan fingerprints;
unrelated review context cannot supply target names or additional planned tasks.
No file or database connection is opened. Filtering/navigation does not approve
execution, retry, resume, rollback or target cleanup. Inspect the retained
uncertainty and target state before correcting settings and reviewing again.
Existing job YAML, result formats, public APIs and Gradle properties are unchanged.

## Filter warnings and retain package context after import

Warnings now has **Category** and **Task** filters in addition to the literal
text search. Filters combine, and **Show all warnings** resets them together with
search. Choice counts cover all retained warning records before filtering, not
the number of tasks. **No recorded task** selects warnings without a structured
task ID; task names are never inferred by parsing English message text. Select a
row to read/copy the complete original message and suggested next action.

After successful or failed file import, the GUI also retains the original package
export warnings when the prior review's manifest and plan fingerprints match the
outcome. Original export task/column identities and duplicate records remain
intact, alongside import compatibility warnings. Summary states the retained package-export warning count when nonzero. An unrelated review, absent package context or an
outcome displayed alone cannot supply these export warnings. No package is
reopened and no database connection is used to populate the view.

`[FILE_FULL_SCAN_INSERT]` is classified separately as **Package export / File
INSERT** or **Import / File INSERT**. Its guidance explains non-resumable INSERT
into empty targets, duplicate-preserving unordered verification, default WARN
permission for this route and ERROR rejection during target review. It does not
ask users to repair a valid PK-free package merely to satisfy numeric-key rules.
Other warnings and future unknown codes retain their original messages and
existing general guidance. Filtering/display never changes job settings,
compatibility policy or execution approval. Result JSON, package manifests,
public APIs, YAML and Gradle properties are unchanged.

## Generated primary keys for file imports

CSV/TSV/JSONL file jobs now offer the same optional target-only generated PK as
direct transfer. In New file job, expand Optional import settings, enable Create
missing target tables and Add target-only generated PK, then keep ID or enter an
unused column name. File Job settings supports the same controls inside its
optional import group. Export still needs no target connection, and these options
remain collapsed by default.

Before export, choose the intended import settings. A completed package retains
its captured job; edit the saved job and export a new package to change them.
Review package and target shows the generated CREATE SQL and warnings before
confirmation. Only newly created tables without a source PK receive the extra
BIGINT identity key. Existing target tables and source files/data are unchanged;
original PK/FK/UNIQUE relationships are retained. A name collision stops review
with guidance to choose another name. WARN permits generated-key warnings; ERROR
rejects them before DDL or row writes.

Generated values come from the target and are excluded from source-value
verification. Duplicates, NULLs and source field names still follow the ordinary
file import rules. These target IDs do not enable MERGE, resume or automatic
repair and do not establish new FK mappings. The existing
schemaMode: CREATE_MISSING_WITH_IDENTITY and optional generatedKeyColumn are
shared with Java and Gradle; no new Gradle task properties are needed.

## Rename target tables and columns in file transfers

CSV, TSV and JSONL jobs can now reuse the same table/column name editor as direct
JDBC transfer. Save a file job, open Job settings, expand Optional import settings
and choose Edit names. Load the captured source Schema when needed, check mapping
scope and target previews, save the mapping, then save job settings. Export a new
package and review it against the target before confirming import. Ordinary jobs
need no mapping and retain their current defaults.

The package keeps original source headers/JSONL fields, Schema and values. The
same pinned name-only mapping is applied to target DDL, INSERT and verification,
including PK/FK/UNIQUE column references. Existing empty targets, new tables,
keyless/text-key tables and optional generated PK creation are supported. Generated
names are checked against projected target column names. This does not introduce
value conversions, expression rewriting, MERGE or resume. Check/default/generated
SQL expressions retain source text; inspect the existing mapping-expression
warnings and Schema SQL before execution. ERROR policy can reject these warnings.

Mapped packages carry transfer-mapping.yaml and a fingerprint pin inside the
captured job, which is covered by the manifest fingerprint chain. The original
job and mapping file are unchanged; the package uses only its captured local
mapping. Moving the entire package and disconnecting the source/original mapping
is supported. Missing or modified mappings fail preflight before target access;
execution stages the mapping with the rest of the immutable input files. Completed
packages cannot adopt later edits to the original mapping: export a new package.
SQL draft saving protects the mapping member as well as the job, Schema and data.
No public result/configuration record shapes or Gradle task properties change.
