# File packages: CSV / TSV / JSONL export, move, import and verify

[Plugin index](README.md) · [Task reference](task-reference.md)

`exportTransferFile` and `importTransferFile` use the same transfer job definition
as the Java services. Export needs only the source DataSource; import needs only
the target DataSource and the completed package. Import does not reconnect to the
source. These tasks are available in this checkout; use a plugin build containing
them. The runnable [example](../examples/file-transfer/README.md) supplies runtime
driver coordinates and connection values externally.

Missing-table CREATE planning reports repeated source constraint or index names (such as
Access `PrimaryKey`) as compatibility warnings and assigns stable, distinct
target names derived from table and object identity. Keys, constraint rules and index
columns, ordering and uniqueness remain intact;
`compatibilityPolicy: ERROR` rejects the adjustment. Captured source metadata is
not renamed.

The GUI's optional **Include referenced tables** action adds foreign-key parents
from the pinned captured Schema before saving a CSV job. Hidden rows can be
included; review the selection count and clear the filter before saving. An
unavailable parent (including a CSV-unsupported key/value type) leaves the whole
selection unchanged. The action opens no connection and does not export data.
The resulting ordinary YAML works with these Gradle tasks unchanged.

Name-adjustment warnings use `[SCHEMA_NAME_ADJUSTED]` in the shared plan, audit
report and SQL draft. The GUI summarizes their count and groups them under
**Warnings → Target / Object names** with guidance for scripts or applications
that refer to constraint/index names. These messages describe planned names;
recorded DDL progress determines whether statements completed or have uncertain
effects. Configuration and report structures remain compatible. Review again
after updating because warning text is included in the Schema-plan fingerprint.

For an Access source in the GUI, use **Access file migration → File package
transfer** and select or drop the MDB/ACCDB file. The source JDBC URL is assembled
automatically and **New Access file job…** creates the same shared job format.
An Access database's unnamed schema is omitted from generated YAML rather than
saved as an invalid blank schema. Review and import the completed package with
only a target connection; the original Access file is not required during import.
The generated job can be used unchanged by these Gradle tasks, with source and
target connections supplied externally as usual.

In the GUI, a successful **Review package and target** exposes **Schema SQL**
when missing-table DDL exists. **Save SQL draft…** writes the displayed UTF-8
snapshot with the actual target delimiter policy, warnings and job/plan pins;
**Edit saved SQL…** opens it in the existing SQL editor. These actions open no
database connection and do not start import or authorize execution. Review the
package and target again after applying manual SQL to the target.

Import-result drafts are available only when their manifest, plan and retained
Schema statements/warnings match the captured review. They include the full
planned SQL, completed count and uncertain attempt, so applied statements may
be present. They are not resume scripts. Package members (including CSV files),
captured import members and audit files are protected against save destinations
that alias them. Input changes, inspection or another result clear the old saved
file handoff. Saving leaves job/package bytes and integrity fingerprints unchanged.

Java callers can use `TransferFileImportService.reviewWithSqlDraft`, returning
a `SqlReview` with the ordinary `review` and nullable `schemaSqlDraft`. The existing
review/execute API, public record components, CSV package format, audit JSON and
Gradle properties remain unchanged. A null draft means there are no Schema SQL
statements. Import still needs only the target connection; SQL capture uses the
same metadata review and adds no source access.

```yaml
formatVersion: 1
source: from
target: to
tables: [PUBLIC.ITEMS]
schemaMode: REQUIRE_EXISTING
dataMode: FILE
chunkSize: 10000
files:
  directory: packages
  format: CSV
  encoding: UTF-8
  nullValue: '\N'
  unsupportedTemporalPolicy: WARN # Default; choose ERROR for strict export
```

The names `from` and `to` are logical references. Each task's `dataSource` binds
its one required endpoint; URLs and credentials are not written to the job or
package. The output directory is resolved relative to the job file. A new child
directory is created for each export, and the task reports the completed manifest
path and its SHA-256 fingerprint. Move the entire package, not just its CSV files.
Optional `targetSchema` selects an existing destination namespace without
changing source table identities. Omit it to use the import connection's current
schema. The desktop CSV creation form and `TransferFileJobDraftBuilder.save`
overload can set this field before export; existing builder signatures retain
their previous default. The format remains version 1. Names requiring quoting
are handled by the shared target dialect and validator, not by concatenating DDL
in the GUI. Real cross-vendor identifier behavior still requires validation.

## Temporal values and export warnings

`files.unsupportedTemporalPolicy` defaults to `WARN`. Unsupported temporal
columns (for example fractional TIME, timezone types or vendor aliases) are
exported using JDBC `getString`, with a warning for each column in
`manifest.warnings`. The original canonical Schema is retained; only the export
projection reads text to avoid passing through a lossy `java.sql.Time` conversion.
The GUI displays the warning count and details; the export command logs a WARN
summary. Warnings do not fail export. Set the policy to `ERROR` to stop export
without publishing a completed manifest. This policy does not suppress malformed
data, encoding errors, NULL-token collisions, SQL failures or integrity errors.

Warning-bearing temporal text is driver-specific, and requires an explicit
conversion before automatic import; a completed export is not a claim that all
its column types can be imported unchanged. Inspect format, precision and timezone
in the warnings. This policy does not imply a timezone or silently reduce precision.

Typed CSV import supports canonical DATE (`yyyy-MM-dd`), TIME with explicit zero
fractional precision (`HH:mm:ss`), and standard TIMESTAMP with explicit precision
0..9 (`yyyy-MM-dd'T'HH:mm:ss[.fraction]`). They represent local wall-clock fields,
without a timezone conversion. Invalid dates/times, offsets in local values,
excess precision and local fields normalized by the runtime timezone are rejected
before target access. Nanoseconds are retained for TIMESTAMP; target precision
reduction fails the default verification. `verify: false` still opts out of that
target comparison. The shared Schema model stores temporal fractional precision
in column `length`, rather than numeric `scale`.

Existing configurations without this field default to WARN, and previous Java
constructors remain available. FileTransfer and manifest records have additive
components; update record patterns and older strict readers when consuming new
artifacts. Manifest version 1 now includes the warning list; missing lists in
older packages are read as empty.

## Task properties

| Task | Property | Type / behavior |
|---|---|---|
| `exportTransferFile` | `configurationFile` | Required `RegularFileProperty`, shared transfer YAML |
| | `dataSource` | Source connection; standard task DataSource settings |
| `importTransferFile` | `manifestFile` | Required `RegularFileProperty`, completed `manifest.json` |
| | `expectedManifestFingerprint` | Required `Property<String>`, lowercase `sha256:` plus 64 hex digits |
| | `allowExecution` | `Property<Boolean>`, default false; must explicitly be true to import |
| | `workspaceDirectory` | `DirectoryProperty`, default `build/sqlapp-transfer/import`; owns unique working copies and audit reports |
| | `dataSource` | Target connection; source settings are not required |

Both tasks disable build caching and always execute when requested: neither a
previous export nor a previous successful import permits Gradle to skip live
validation. Indirect CSV/Schema/job members are validated by the command. The
export output location is controlled by YAML and is intentionally not a static
Gradle output property. Import's dedicated workspace is an output directory.
Authorization, pinned manifest and offline CSV validation run before import pool
construction. A manifest fingerprint binds reviewed bytes; it is not a signature
or a claim that the live target is safe.

## Execution behavior

`compatibilityPolicy` defaults to `WARN`: try the generated target-dialect DDL
and shared Schema types, retain compatibility warnings, and inspect/refine the
result. Product differences, partitioning/inheritance and identity/default/
generated/domain semantics are not by themselves reasons to block the first
attempt. The original Schema and generated SQL remain in review/audit artifacts.
Set `compatibilityPolicy: ERROR` for strict preflight. This also makes temporal
export-only types errors, even when their specific file policy is WARN.

Different products use this best-effort path without claiming complete
conversion. Driver behavior, vendor expressions, omitted physical features,
identity sequence state and subsequent inserts still require testing and repair.
Actual execution/verification errors and destructive replacement are not
downgraded by WARN. Existing tables are not dropped or overwritten.

Before target access, import checks all package hashes, canonical Schema/table/
column membership, strict decoding, CSV header and width, row counts, value types,
non-null columns, and ascending unique numeric primary keys when eligible; other tables retain file order for INSERT. Only indexed package
members are used. Import execution captures reviewed bytes into its workspace and
repeats validation against those copies. Changes to the original package after
capture do not change the data being imported. This is not a cross-process lock
against edits to the private workspace.

Review binds the plan to the target product/version and connection identity.
Execution rechecks that identity, target tables, columns, non-null keys and empty
targets. It composes the existing bulk planner and transactional chunk executor
with INSERT and resume disabled. No target is cleared or reset. A second import
into a now nonempty target fails even if the first import succeeded.
CSV import explicitly requests preservation of source identity values and NULLs
from the bulk insert provider, just like direct transfer. Existing packages keep
their format and require a fresh review, not manual edits. Database-generated
sequence/identity counters for later inserts are not synchronized automatically.
Review and outcomes name modeled identity/auto-increment columns with the same
`[GENERATED_KEY_COUNTER]` follow-up as direct transfer, for both existing and
newly prepared tables. WARN permits execution; ERROR stops before copying.
Copied-row verification does not certify future generated values. Inspect the
target counter separately; no restart or nextval probe is performed. This is
limited to columns represented as generated keys in the canonical Schema.
Upgrading does not repair rows already copied with regenerated keys; inspect the
previous audit and use a known empty target before rerunning.

`schemaMode: REQUIRE_EXISTING` preserves the existing-table path. Set
`schemaMode: CREATE_MISSING` before exporting to prepare missing ordinary tables
from the packaged canonical Schema. The target schema namespace must already
exist. If the namespace is missing, the shared diagnostic names it and lists up to
ten existing schemas for the selected catalog. Set `targetSchema` to the exact
name/case before exporting a new package, or create the intended namespace with
its required owner and privileges. Do not edit a completed package to change it.
Existing tables must pass the shared empty-target/key checks before any
DDL. Review includes ordered CREATE statements generated by the target dialect,
and a fingerprint covering both the data plan and DDL. Execution regenerates
and compares that plan before writing; a changed missing-table set requires a
new review. It creates only missing selected tables, then revalidates all targets
and copies/verifies data. Select all same-schema FK parents as well as children;
referenced tables and constraints are mapped to `targetSchema` (or the target's
current schema). Existing tables are never altered or dropped.

`CREATE_MISSING` warns about identity/default/generated/domain columns and
temporary/partitioned/inherited tables, then tries the target dialect's generated
DDL. Strict policy rejects those concerns instead. Unresolved external-schema FK
dependencies and dialects that cannot generate executable CREATE operations
still require explicit resolution. This is table preparation, not a full
database clone: views, routines, triggers, grants and schema namespaces are not
created. Embedded HSQL tests cover execution plus Schema models labeled as
different products and containing partitioning; they do not certify real
heterogeneous databases. Live behavior of other dialects is unverified here.

Verification is enabled by default. It compares typed rows from the captured CSV
with target rows using the shared ordered count/hash verifier in SERIALIZABLE
read isolation. A mismatch fails the invocation while retaining completed copy
results. `verify: false` is an explicit opt-out retained in the result. The target
must support transactions; verification additionally requires SERIALIZABLE.
This does not fence concurrent writers or establish a maintenance window.

Each execution workspace retains `import-result.json` with the package/plan
fingerprints, phase, copy results, requested verification policy and comparisons,
committed progress and stopped task ID. Schema preparation also retains the
planned SQL, successful statement count and a one-based attempted statement
when an operation fails. A failed statement may have taken effect. Revalidation,
SCHEMA_PREPARATION, COPY, VERIFICATION and
FINALIZATION failures throw and are not reported as success. Committed chunks can
remain after failure, and DDL may be committed independently of the copy.
Audit-publication failure is also an error and is exposed
as an unavailable audit path in the retained Java outcome. Inspect target and
checkpoint state before another run; there is no automatic recovery or resume.

Java callers can use `TransferFileImportService.review(...)` and
`execute(review, targetBindings, workspace)` separately, with confirmation between
them. `ImportTransferFileCommand` composes these actions after explicit headless
authorization. Commands own/close their supplied DataSource by default, consistent
with other commands; set `closeDataSource=false` for a caller-owned connection
pool. Services never close caller-owned DataSources. Existing driver/command
logging remains subject to its own logging configuration.

For headless use, `allowExecution=true` authorizes both the job's requested DDL
and its data copy against the live plan resolved by the command. A pinned package
fingerprint does not pin a separately reviewed live-target DDL plan. Use the
split Java review/execute API or GUI when a separate plan approval is required.
The review/outcome Java records now include Schema preparation; previous
constructors remain available. Record patterns must account for the additional
component. The audit envelope remains version 1 with additive Schema progress.

## Current boundaries

The automatic file import path requires existing empty targets or explicit missing-table creation,
an optional pinned name-only mapping file. Numeric NOT NULL primary keys use ordered keysets;
other tables use full-scan INSERT and unordered verification. Strings,
booleans, integer/decimal values and finite float/double values are supported.
Other temporal types are exportable with warnings but require conversion for
automatic import. Binary/LOB values, full target Schema
recreation and complete vendor-specific cross-product conversion are not implemented. Export requires
SERIALIZABLE source reads; JDBC driver buffering still depends on runtime settings.
The Swing **File transfer** screen uses these same services. **New file job…** reads
source metadata and saves this format from a table picker without handwritten
YAML. Export, target review and confirmed import are separate actions. See the
[GUI guide](../../sqlapp-swing/README.md#csv-transfer-without-preparing-yaml).

## JSONL packages

Choose **File transfer → New file job… → File format → JSONL** in the GUI, or
use the same shared job with these settings:

```yaml
formatVersion: 1
source: source
target: target
tables: [PUBLIC.ITEMS]
schemaMode: CREATE_MISSING
dataMode: FILE
files:
  directory: packages
  format: JSONL
```

Do not set `files.nullValue` for JSONL; JSON null is used directly. CSV remains
the default format; its NULL token is optional. Encoding defaults
to UTF-8. Existing `exportTransferFile` and `importTransferFile` task properties,
source-only export and target-only import remain unchanged. Copy the entire
completed package, including `manifest.json`, `transfer.yaml`, `source-schema.xml`
and `table-N.jsonl` members. Format version stays 1; updated readers retain CSV
support and reject mixed manifest format/file extensions.

Each JSONL row is one flat object with exact captured Schema column names.
Numbers and booleans use native JSON values, character/temporal columns use
strings and SQL NULL uses JSON null. Empty files represent empty tables.
Import validates duplicate/missing/extra keys, scalar types, NOT NULL constraints,
ascending unique numeric keys when eligible, row counts, byte lengths and hashes before target
review. Big integers and decimals retain precision. Fields are bounded to 1 MiB
characters and lines to 16 MiB characters; nested objects/arrays, blank lines,
trailing values and invalid Unicode are rejected. Ordinary final newline and
CRLF lines are accepted. Package inspection and value checking grant no import
approval. Existing CSV key/type, mapping and LOB limitations still apply.

`TransferFileJobDraftBuilder.save` has an additive overload accepting `FileFormat`;
existing overloads continue to save CSV. Unsupported temporal export still follows
the saved warning/error policy; using JSONL does not make an unsupported typed
import possible. No new dependency or database-specific SQL generation is added.

## TSV packages

TSV is also available in the GUI's **File format** selector and the existing
shared job:

```yaml
files:
  directory: packages
  format: TSV
  nullValue: __SQLAPP_NULL__
```

The rest of the job and `exportTransferFile` / `importTransferFile` properties
are unchanged. Exported `table-N.tsv` files use a tab-delimited header and quoted
fields, including embedded tabs, quotes and line breaks. The same scalar,
temporal, numeric-key ordering, row-count and package-integrity validation runs
before target review. NULL-token collisions fail export; the configured token
must be nonblank and contain no tabs, quotes or line breaks. Commas are valid
inside TSV text and NULL tokens. CSV validation keeps its comma delimiter rules.
Import remains target-only and requires a completed package rather than arbitrary
standalone TSV files. The additive `TSV` enum/configuration value preserves CSV
and JSONL jobs and package version 1; manifest/file extensions must agree. No new
task property, dependency or database query is introduced.

## Character encoding and headers

CSV/TSV export and import share these optional file settings, also editable in
**New file job…** and **Job settings…**:

```yaml
files:
  directory: packages
  format: TSV
  encoding: windows-31j
  nullValue: __SQLAPP_NULL__
  headerMode: SKIP
```

`encoding` defaults to UTF-8; Java charset aliases are normalized. Invalid
encodings and lossy character conversion fail explicitly. `headerMode` is
`VALIDATE` (CSV/TSV default), `SKIP` or `NONE`. VALIDATE/SKIP both write a header
on export and consume exactly one parsed record on import; VALIDATE also checks
canonical column names and order. SKIP ignores header names and uses captured
Schema column order. NONE omits the export header and reads the first record as
data; an empty table then has an empty file. Quoted line breaks inside a header
belong to that one record. Neither SKIP nor NONE infers or remaps column order,
and neither suppresses row/key/type/integrity checks. Missing expected headers
still fail. JSONL defaults to NONE and rejects other header modes.

Package version remains 1. Readers default absent header settings identically in
the manifest and saved job, retaining old CSV package compatibility. New packages
pin both encoding and header mode; edit the original job and export a new package
when changing them. Existing `FileTransfer` 4/5-argument constructors and draft
save overloads preserve their defaults; the new settings overload exposes the
shared options without additional Gradle properties or dependencies. The services
continue to consume completed packages rather than infer schemas from standalone
CSV/TSV files.

## Offline package preview API

`TransferFileImportService.preview(manifest, expectedFingerprint, taskId)` returns
an immutable display sample using the import reader, with a default limit of 20.
The overload taking `rowLimit` accepts 1..100. The exact task ID comes from the
manifest. Results retain the manifest snapshot/fingerprint, table identity,
recorded count, source types, SQL-NULL flags and per-cell truncation flags.
Character/temporal values are quoted, while numbers, booleans and SQL null are
unquoted. A cell displays at most 512 original Unicode code points; total cell
text is capped at 1 MiB characters. These are display limits, not conversion rules.

This API opens no database connection and does not change YAML, data files or
Gradle execution. It verifies all package member hashes before/after reading but
parses only the selected sample. Other tables and the unparsed tail are not fully
value-validated. Manifest row totals remain recorded evidence. Full value checks,
target compatibility review, explicit import authorization and execution-time
revalidation remain separate requirements. Sampling is not an execution approval,
conversion preview against the live target, or a source-database sampling API.
The Swing **Preview package data…** dialog uses this shared method.

The GUI package preview now provides **Reload package** and **Validate all
values** in the same dialog. Reload discards old sampled/full-check evidence and
rebuilds table choices, preserving a selection only when the task ID and full table identity still match. Whole
value checking calls the existing `validatePackage` API with the pinned manifest
fingerprint; it opens no database and grants no target/import approval. Failures
permit retry, clear stale sample evidence and require reloading after package
changes. Full validation remains separate from sampled values and live target
review. No YAML, package, public command API or Gradle property changes are needed.

## Export-only eligibility and optional import settings

Export is source-only and does not require a primary key or numeric key.
Keyless/incomplete-key tables stream all rows through the shared Table row
iterator, preserving duplicates and NULLs; complete keys keep keyed export.
The file-job authoring helper now applies export scalar/type rules rather than
using import key eligibility. Existing jobs and Gradle task properties are
unchanged. `schemaMode`, target schema, verification and chunk settings in the
shared job are future import settings and do not trigger target access on export.
The GUI groups them as collapsed **Optional import settings (no target access during
Export)** in new and saved file-job forms.

Automatic import accepts keyless and nonnumeric-key tables using non-resumable
INSERT into empty targets. `[FILE_FULL_SCAN_INSERT]` explains this route on export
and import review. Default WARN policy permits import; ERROR policy rejects this
route during review before DDL or data writes. Numeric NOT NULL primary keys keep
the ordered keyset route. Other tables retain every row, including duplicates,
and verify an unordered SHA-256 multiset including duplicate counts. Verification
chunk positions describe sorted hashes, not source file row positions. No
synthetic key, deduplication, MERGE, resume or key-based repair is inferred.
Package validation and preview also accept these tables.

## NULL and empty fields in CSV/TSV

`nullValue` is optional. Without it, the default `emptyFieldMode: NULL` maps bare
empty fields to SQL NULL and quoted `""` to empty text. Export writes bare NULL
fields and quotes empty strings. Single-column NULL rows are retained as parsed
empty records. An empty file still represents zero rows.

```yaml
files:
  directory: packages
  format: TSV
  # UTF-8; bare empty = NULL, quoted empty = empty text by default
```

To interpret bare empty fields as empty text, use:

```yaml
files:
  directory: packages
  format: TSV
  emptyFieldMode: EMPTY_STRING
  nullValue: __SQLAPP_NULL__
```

EMPTY_STRING requires an explicit token to represent exported SQL
NULL without ambiguity. Configured token collisions still fail export. With
`emptyFieldMode: NULL`, a token may also be supplied explicitly. Quoted empty text
stays empty text; empty numeric/boolean/temporal text is not coerced to zero.
JSONL uses native JSON null and rejects non-NULL empty-field modes.

`emptyFieldMode` has just two choices, NULL (default) and EMPTY_STRING. Supplying
an explicit token does not change the default interpretation of bare empty fields.
No Gradle property is added. Change
the original job and export again to alter interpretation.

## Import failure diagnostics

Failed import outcomes and `import-result.json` now include optional
`failureDiagnostic` evidence: a cause category, validated SQLState and numeric
vendor code. GUI Summary shows the corresponding explanation and what to check
next; execution exceptions also contain the codes for headless runs. Raw driver
messages and exception causes are not retained. Known causes include constraint
violations, conversion, permissions, connection and transaction failures. A
recorded verification mismatch is distinguished from a copy failure. Unrecognized
codes remain available with an unknown-cause explanation.

The diagnostic survives failure to write the audit, in the retained Java outcome.
It does not change commit boundaries, clear target rows, authorize retry or
identify the failed file record. Inspect DDL uncertainty, committed progress and
verification before correcting the target and reviewing again. Successful and
legacy-constructed outcomes have no diagnostic. Existing constructors/accessors,
job YAML and task properties remain available; JSON and the Java outcome record
have an additional optional component. Complete Java record patterns must account
for it. See [direct transfer diagnostics](direct-transfer.md#execution-failure-diagnostics).

## GUI inspection of import verification

File import now exposes recorded per-task comparisons in Verification, alongside
Details JSON. Show mismatched tables and Show chunks navigate to paged comparisons
(200 chunk records per page). Row counts, full hashes and the recorded comparison
order remain inspectable; missing comparisons are not treated as success. Target
names are taken only from the matching retained review. No source/target connection
or package reread is needed to inspect these results, and no job/task properties
change. See the
[GUI guide](../../sqlapp-swing/README.md#inspect-recorded-verification-by-table-and-chunk).

## GUI view of retained import progress

Execution progress now lists retained import task evidence: last committed chunk
counts, copy completion/results, verification, stopped-task and Schema SQL status.
The matching retained review supplies known task/target identities; unrelated
review context is ignored. Missing progress is not shown as zero or proof that no
rows were committed. Task SQL and verification navigation use recorded evidence
without package rereads or database connections. This adds no YAML/task settings
and does not enable retry or resume. See the
[GUI guide](../../sqlapp-swing/README.md#inspect-retained-execution-progress).

## Warning inspection after GUI import

The GUI's Warnings view now keeps original export warnings alongside import
warnings after success or failure, using only matching retained review context
(manifest and plan fingerprints). It does not reread files or merge new fields
into result JSON. The original manifest remains the persistent export-warning
source; an outcome viewed without matching package context cannot recreate it.
Category/task selectors combine with literal search, and Show all warnings clears
the filters. Counts describe retained warning records, including duplicates;
unstructured warnings are not assigned a task by parsing message text.

`[FILE_FULL_SCAN_INSERT]` receives route-specific guidance: WARN permits
non-resumable INSERT into empty targets and ERROR rejects that route at target
review. Unordered verification preserves duplicate counts; hash positions do not
identify source rows. This display change adds no job or task settings. See the
[GUI guide](../../sqlapp-swing/README.md#filter-warnings-and-retain-package-context-after-import).

## Optional generated target primary key

CSV, TSV and JSONL packages can use the existing target preparation option:

```yaml
formatVersion: 1
source: from
target: to
tables: [PUBLIC.EVENTS]
dataMode: FILE
schemaMode: CREATE_MISSING_WITH_IDENTITY
# generatedKeyColumn: ROW_ID  # optional; defaults to ID
files: {directory: packages, format: TSV}
```

On import, missing tables without a primary key receive a target-only BIGINT
identity primary key. Existing source primary keys are retained, existing target
tables are not altered, and the target schema namespace must already exist. The
shared Schema planner and target dialect SQL factories generate the reviewed DDL.
A generated name that collides with a source column stops review before DDL;
choose an unused `generatedKeyColumn` instead. PK creation reports
`[TARGET_GENERATED_PK]`; default WARN permits review, while ERROR rejects this
compatibility warning before writes.

Export remains source-only. Package headers, canonical source Schema and exported
values contain no synthetic column. Import and verification compare only source
columns, preserving duplicates and NULLs; the database generates the extra key.
Existing FK relationships keep referencing their original columns, including
original UNIQUE columns. No FK is inferred from the generated key, and it is not
a business identifier, source row identity, resume key or repair key. Keyless
imports retain non-resumable INSERT, empty-target checks and unordered multiset
verification. Inspect verification and target definitions before application use.

Use New file job → Optional import settings → Create missing target tables → Add
target-only generated PK, or edit these options in file Job settings. The name
field defaults to ID. Changing a saved job requires a new export; completed
packages keep their captured configuration and fingerprints. Existing task
properties and overloads remain available. `TransferFileJobDraftBuilder.save`
adds an overload with an optional generated key name before the output Path; old
save overloads keep the default name. No companion example checkout was modified;
existing examples can add the schema mode and optional name shown above.

## Optional target table and column names

File packages now accept the same optional name-only mapping as direct JDBC jobs:

```yaml
# transfer.yaml
formatVersion: 1
source: from
target: to
tables: [PUBLIC.CUSTOMERS]
dataMode: FILE
schemaMode: CREATE_MISSING
mappingFile: names.yaml
files: {directory: packages, format: CSV}
```

```yaml
# names.yaml
formatVersion: 1
tables:
  PUBLIC.CUSTOMERS:
    targetTable: CUSTOMER_COPY
    columns: {CUSTOMER_ID: ID, CUSTOMER_NAME: NAME}
```

Unspecified names stay unchanged. Set targetSchema separately for the namespace;
targetTable is a table name, not a dotted schema/table expression. Mapping source
identities must resolve uniquely in the captured Schema and remain within the
selected table scope. Target table/column collisions and missing source columns
stop export before a completed manifest is published. Include referenced tables
before mapping FK relationships.

Export preserves the source Schema, original CSV/TSV headers or JSONL field names
and all source values. The existing shared projection and target-dialect factories
apply the same names to target CREATE SQL, INSERT column mappings, PK/FK/UNIQUE
references and verification. Numeric keys retain ordered keyset verification;
keyless and nonnumeric-key tables retain unordered duplicate-preserving
verification. Source columns can also be renamed before optional target-only PK
creation, avoiding a generated-name collision without discarding source values.
Existing empty targets, missing-table creation and generated target PK creation
are supported. No value transforms, SQL expression rewrites, MERGE or resume are
introduced. SQL expressions/defaults/check bodies retain their original text;
renaming in CREATE modes emits the same expression-review warning as direct
transfer, and ERROR policy rejects it before writes.

For mapped exports, the completed package includes transfer-mapping.yaml. Its
fingerprint is pinned by the existing transfer-name-mapping comment in the
captured transfer.yaml, which is itself pinned by manifest.jobFingerprint. The
captured job rewrites mappingFile to that fixed local member; the author's job and
mapping are untouched. The captured job fingerprint can therefore differ from
the original author's job fingerprint. Move the whole package. Import never
follows the author's external mapping path and does not need that original file
or a source connection. Missing/changed mapping members, missing pins and external
mapping paths are rejected during package preflight, before target access.
Execution also copies the pinned mapping into its private import workspace.

Package formatVersion, public Java records and task properties are unchanged;
unmapped packages retain their existing layout. Hashes are integrity evidence,
not signatures or execution approval. Editing an original mapping requires a new
export; editing a completed package invalidates it. In the GUI, save the file job,
then open Job settings → Optional import settings → Edit names. The existing
source-Schema checks, scope editor and mapping preview remain available. Save the
mapping and job settings, export a new package, then review against the target.
SQL draft saving also protects the captured mapping member, including file aliases.

The separate companion example repository was not modified. Existing examples
can add mappingFile and the names.yaml shown above without new Gradle properties.

### Offline package metadata

`TransferFileImportService.describePackage(manifest, expectedFingerprint)` checks
package integrity and returns immutable table/column lists from the canonical
source Schema and pinned name mapping, without a database connection or row
decoder. The overview remains available for export-only value types. Recorded
row totals, requested names, source types and conditional generated-PK settings
are not a value check, resolved target metadata or execution approval. Use
`validatePackage` for offline value validation and `review` for target checks.
In the GUI, **Inspect package** exposes this information in **Package tables**;
select a table and **Show columns** for its source-to-target name correspondence.
The GUI retains this overview alongside a target review or import outcome only
when its package fingerprint matches. It is discarded on input changes or
operation failure. **Show renamed columns** filters the selected table's column
list; **Show all columns** resets this filter and the text search. Neither view
performs a target lookup or grants execution approval.
