# RDB transfer: one job for GUI and Gradle

## Product direction

The primary desktop workflow moves Schema and data from a source RDB to a
target RDB. The GUI lowers the preparation barrier: users choose connections,
tables and required mappings, review the result, then save or execute a job.
They do not need to author Schema XML, migration SQL or Job YAML first.

The saved job is also the input to unattended Gradle execution. GUI execution
must not require an additional GUI-only configuration or produce a different
interpretation of the same job. A saved job can be reopened and edited in the
GUI. Saving a job never starts a migration.

Schema version migrations, SQL editing and artifact inspection remain available
as supporting tools rather than the primary product navigation.

The [data-migration UX and priorities](data-migration-plan.md)
describe the planned improvements to this workflow.
The next substantial gap is shared table/column mapping with a guided review,
not a GUI-only interpretation of the saved job.

## Default compatibility policy: try, inspect and refine

The normal migration workflow should let users attempt a transfer and then fix
the parts that need attention. Potential incompatibilities are warnings by
default, including heterogeneous Schema features such as partitioning,
inheritance, generation/default expressions, physical storage options and
vendor-specific objects. Do not require users to solve every compatibility item
before their first attempt. Keep generated DDL, source Schema, warnings and
partial execution evidence together so they can inspect and refine the job.

`compatibilityPolicy: WARN` is the default transfer-job intent; `ERROR` explicitly
requests strict preflight. GUI and Gradle use the same policy. Existing strict
low-level validators are composed where needed, rather than silently weakened.
New transfer paths should classify compatibility concerns as warnings and apply
strict rejection only when requested. This policy is implemented in CSV and
the common direct-JDBC review/execution service, desktop screen and Gradle
adapter. Existing low-level/legacy entry points preserve their contracts.

Warning-only completion means the planned operations completed, not that the
whole source database was faithfully reproduced. Report retained, converted,
possibly omitted and failed features honestly. Do not silently discard source
definitions. A target dialect may omit a feature it cannot represent; flag the
source feature and retain its original model plus the generated SQL. Actual SQL
failures, invalid input, broken integrity checks, unresolved identities and
unauthorized destructive changes remain errors. Data verification mismatches
remain failed execution, with committed work retained for inspection. Never
infer permission to drop/replace existing tables from a compatibility policy.

## Supported use-case design

The product has three independent axes: data transport, source/target product
pairing, and optional target Schema preparation. Product pairing is determined
from actual connection metadata, not a duplicated `sameProduct` configuration
flag. Selecting Schema preparation must not force a particular data transport.

| Data use case | Target Schema beforehand | Current implementation |
|---|---|---|
| Same product, direct JDBC | Prepare or require existing | Shared GUI/Gradle review of missing-table DDL, explicit creation, copy and verification; embedded coverage |
| Same product, JDBC export then file import | Prepare or require existing | CSV GUI/Gradle export/import and verification implemented; optional missing ordinary-table creation |
| Different products, direct JDBC | Prepare or require existing | Best-effort shared GUI/Gradle execution with optional missing-table DDL and WARN/ERROR; actual heterogeneous DB validation and vendor conversion remain incomplete |
| Different products, JDBC export then file import | Prepare or require existing | Shared Schema/target-dialect best-effort CSV path attempts execution with warnings; vendor-specific conversion and real heterogeneous DB validation remain incomplete |

For file transport, export and import are separate actions that may run at
different times and in different environments. Export requires the source
connection only; import must work with the package and target connection without
reaching the original source. The saved definition may retain both logical
connection references without requiring both runtime bindings for either action.
Target Schema preparation, when requested, must precede import, not export.
Data-only imports still validate the live target shape and empty-target policy.

The file package must retain the canonical source Schema, resolved table/column
identities and ordering, type information, actual row counts and byte fingerprints
for its data files, format/encoding/null policy, and the originating job and
mapping fingerprints. Import must reject mismatched, missing or modified package
members before writes. Export failure must not publish a completed package.
Verification compares imported data with the recorded package, rather than
assuming the original source is still reachable or unchanged.

## User journey

1. Select source and target connections and check their availability.
2. Select source tables and resolve target catalog/schema/table identities.
3. Review target Schema preparation, including types, keys, relationships,
   unsupported objects and required user decisions.
4. Review data-copy policy, verification and the complete execution plan.
5. Save the job and its required artifacts. Run it in the GUI or through Gradle.
6. Inspect Schema, copy and verification results together, including partial
   completion and the conditions for another run.

The common path prepares missing target objects and performs an initial copy.
Existing target objects and rows require an explicit supported policy; do not
infer replacement, deletion, resume guarantees or a destructive reset.
Unsupported conversions must remain actionable blockers rather than being
silently omitted. Schema conversion uses the canonical sqlapp Schema model and
the target dialect's factories and assessment/mapping facilities.

## Shared execution boundary

- `sqlapp-core`: canonical Schema, dialect conversion/generation and shared
  planners, validators and JDBC executors.
- `sqlapp-command`: saved transfer-job contract, file resolution, preparation,
  inspection and execution orchestration, and aggregate results. Compose the
  existing Schema, assessment, bulk-copy and verification capabilities.
- `sqlapp-swing`: interactive authoring, progressive disclosure, review,
  confirmation and progress. Invoke the command boundary.
- `sqlapp-gradle-plugin`: properties and invocation of that same boundary.
  Do not implement a second planner or execution state machine in task classes.

Both entry points must validate configuration and artifacts, inspect the live
target, and enforce the same blockers before writes. Neither a previous GUI
review nor a saved successful report is evidence that a later target is safe.
Headless execution takes any required explicit execution authorization as an
input rather than attempting to display GUI confirmation dialogs.

## Saved job and runtime inputs

The public configuration must express migration intent: source/target connection
references, selected objects, target mapping, Schema preparation, copy and
verification policy. Group advanced options without exposing internal phase
mechanics in the minimum configuration.

Use one authoritative saved definition. Generated SQL, Schema XML, resolved
identities and fingerprints are reproducibility artifacts; retain their
relationship to that definition and reject stale artifacts. Resolve relative
artifact paths against the saved job location, consistently in GUI and Gradle,
so a different working directory does not change execution.

Do not persist passwords or secret-bearing JDBC URLs in a job or generated
Gradle example. Connection references and runtime credential resolution must
work outside the GUI process. Session-only JDBC driver loading is insufficient
for unattended execution; document the runtime driver classpath for each entry
point. A job reopened without credentials requests them or reports the missing
runtime input explicitly.

This document defines requirements, not a new executable YAML format or new
Gradle task names. Final names and required fields need implementation and
compatibility tests before publication as supported API.

### Implemented definition foundation

`TransferJobConfiguration` and `TransferJobConfigurationIO` in `sqlapp-command`
now provide an offline definition and validated read/write boundary. This is a
development-stage contract. The FILE/CSV path is accepted by GUI and Gradle
adapters; direct JDBC uses the Java service below. It must not be passed to
`executeBulkMigrationJob` as if it were the existing Data Job format.

The minimal definition currently tested is:

```yaml
formatVersion: 1
source: source-db
target: target-db
tables: [PUBLIC.ITEMS]
schemaMode: CREATE_MISSING
```

`source` and `target` are runtime connection names (letters followed by letters,
digits, dots, underscores or hyphens; at most 128 characters). No JDBC URL,
username or password fields are accepted. The caller will bind these names to
runtime connections. `TransferJobPreparationService` now accepts a map of these
names to caller-owned `DataSource` instances for read-only preparation.
`TransferJobExecutionService` now provides the explicit Java review/execute
boundary described below. CSV export/import GUI and Gradle adapters are
implemented. Direct-transfer GUI/Gradle review/execution uses this same format.

`schemaMode` is required explicitly: `CREATE_MISSING` expresses target creation
intent; `REQUIRE_EXISTING` requests validation of pre-existing objects. Neither
means destructive replacement or approval of generated DDL. `tables` is a
nonempty unique list of source identities; live resolution of short/qualified
names and closure/order validation remain the shared planner's responsibility.
Table and schema identities retain their original text without case folding or
trimming that might alter quoted identifiers.

`dataMode` independently selects `JDBC_DIRECT` (the backward-compatible default)
or `FILE`. A FILE definition requires `files`; direct mode rejects a `files`
block instead of ignoring its settings. The file location follows the job's
directory, with optional format and encoding settings:

```yaml
formatVersion: 1
source: source-db
target: target-db
tables: [PUBLIC.ITEMS]
schemaMode: REQUIRE_EXISTING
dataMode: FILE
files:
  directory: packages/initial-copy
  format: CSV
  encoding: UTF-8
  nullValue: '\N'
```

File format defaults to CSV and encoding to UTF-8. Encoding names are validated
and normalized through Java Charset. CSV requires an explicit nonblank null token
without commas, quotes or line breaks, representable in the selected encoding.
There is no inferred sentinel that is safe for every source: the future exporter
must reject a collision with a literal source value, preserving the distinction
between NULL, empty string and the sentinel text. JSONL uses JSON null and rejects
`nullValue`. Unknown formats, nested fields and contradictory settings fail.
`Snapshot.filesDirectory()` resolves the configured directory relative to the
job without creating it. Neither read/write nor the direct service exports files.

These fields are a development-stage file-transport contract, not a complete
export/import pipeline. The source-only CSV exporter is described below.
The direct preparation/review path rejects FILE before
opening a connection, so a file job cannot accidentally execute a direct copy.
Existing constructor signatures and old YAML defaults remain supported; the
configuration record gains components, so clients using explicit record patterns
must account for the expanded shape.

Optional `sourceSchema`, `targetSchema` and `mappingFile` supply explicit names
or an existing mapping reference. Omit unused fields instead of blank strings.
`mappingFile` is resolved relative to the saved job, including after moving the
job directory. Reading the definition checks path syntax without reading that
mapping or establishing compatibility. Optional `chunkSize` defaults to 10000
and accepts 1..1000000; `verify` defaults to true. These are saved intent, not a
claim that a runtime execution has been validated.

IO accepts one strict UTF-8 YAML object up to 1 MiB and rejects unknown fields,
duplicate fields, additional documents and scalar type coercions. Saving retains
the exact text, including comments and line endings. A null expected file
fingerprint allows new-file creation only; editing requires the current exact
SHA-256 fingerprint. Replacement checks do not constitute a cross-process lock.
File fingerprints are byte identities, not resolved-plan or data fingerprints.
No connection, Schema mutation or data transfer occurs during read/write.

### Implemented connection-backed preparation

`TransferJobPreparationService.prepare(jobFile, connections, workspace)` is the
shared command-module entry for the first preparation path. It supports
`REQUIRE_EXISTING` or `CREATE_MISSING`. Missing runtime
bindings and explicit `mappingFile` settings are rejected
before borrowing connections. Product-name differences are warnings by default;
`compatibilityPolicy: ERROR` rejects them. Shared Schema types and target-dialect
execution are best-effort and do not certify complete vendor-specific conversion.
Warnings and the shared target Schema plan are returned in `Prepared` and retained
in success/failure outcomes. The bulk data-plan fingerprint retains its meaning;
Schema-plan fingerprint/DDL/missing set are checked independently before writes.
The previous Prepared constructor remains available; record patterns must account
for the additive warning component. Fresh execution compares the warning set as
well as configuration and data-plan fingerprints.

`TransferDatabaseCommand` and Gradle `transferDatabase` compose the same service.
Review-only is the default. Explicit execution requires a pinned configuration
fingerprint and performs a fresh live review, with `transfer-review.json` and
`transfer-result.json` audits. Command-owned source/target pools are closed once;
services keep caller pools open. See the [direct task guide](../gradle-plugin/direct-transfer.md).

The service borrows and closes connections, requests read-only access and never
closes the caller's DataSources. It reads one exact/current source schema through
the dialect metadata readers into the canonical Schema model. If current-schema
resolution is ambiguous or unavailable, supply an explicit source/target schema.
The target uses its own catalog and current or explicitly selected schema.

Preparation generates a new `transfer-*` workspace child containing the original
`transfer.yaml`, captured `source-schema.xml` and a pinned `data-job.yaml` in the
existing bulk-job format. It delegates table-name resolution, key requirements,
dependency ordering and plan construction to `BulkMigrationJobConfigurationResolver`
and target checks to `BulkMigrationTargetValidator`. Short source names must
resolve uniquely; aliases of the same source table are rejected. Literal dots
inside resolved identifier components are not supported by this preparation
path. Source keys must satisfy the existing NOT NULL/uniqueness checks.

The generated data job uses INSERT, empty targets, no resume, the requested
chunk size and verification policy. Target validation may read a first key row
to reject a nonempty target; it does not copy rows, create target objects or
create checkpoint tables. Connections and credentials are not serialized.
Preparation checks the source definition again before returning and provides
the copied definition, artifact fingerprints, ordered task IDs and plan
fingerprint. Generated relative Schema references remain valid after moving the
whole output directory.

Failed preparation returns no `Prepared` result; a unique child directory may
remain with intermediate artifacts for diagnosis. Do not treat such artifacts,
or a successful preparation result, as execution approval. Existing bulk commands
and Gradle tasks can consume the generated data-job format, but they must perform
their normal fresh validation and fingerprint checks. The transfer definition
now has GUI and Gradle review/execution adapters and optional table preparation. Vendor-specific heterogeneous
conversion, mapping application and recovery
remain pending.

### Implemented review and initial-copy execution

Direct execution reuses the CSV path's `TransferSchemaPreparation` planner and
statement executor for optional missing-table preparation. Fresh preparation
must match the reviewed data plan, warnings and exact Schema plan before DDL.
Database identity is checked on every borrowed connection. The target dialect
generates CREATE operations, existing targets are validated and never replaced,
and generated plans are not reinterpreted as destructive Schema diffs.
DDL progress is separate from copied-row progress, including uncertain execution
or statement-close failures. Data copying starts only after DDL finishes, then
the shared bulk command validates target state and copies/verifies. Nontransactional
DDL and committed chunks may remain after errors. Target namespaces and unselected
dependencies still require explicit resolution; no complete Schema clone is claimed.

`TransferJobExecutionService.review(jobFile, connections, workspace)` prepares
the job without writes and binds the review to the actual source/target database
identities used during preparation. Identity uses the existing
`MigrationPlanCommand.databaseIdentity` helper: product/version and a fingerprint
of JDBC URL, catalog and current schema; plaintext URLs are not included in the
review. The caller supplies runtime DataSources, credentials and driver setup.

Calling `execute(review, connections)` is the explicit write boundary. A GUI
adapter must obtain execution confirmation first; a headless adapter must expose
explicit execution authorization. The service checks the original definition,
copied definition, data job and captured Schema fingerprints, prepares a fresh
job and compares its definition/plan to the review. Every borrowed connection is
identity-checked, including connections opened after fresh preparation. Changed
artifacts, source plans, runtime bindings and nonempty targets stop execution.
There is no automatic target reset, replay, resume or rollback of prior runs.

The service composes `ExecuteBulkMigrationJobCommand` for copying and configured
post-copy verification; it does not reimplement chunk transactions or verification.
Caller-owned DataSources remain open. The returned `Outcome` retains the prepared
artifacts, copy result, optional verification result, completed task IDs and latest
committed chunk progress by migration ID. A disabled verification policy is
explicit in the retained definition and has no verification result.

Failures throw `ExecutionException`, whose `outcome()` distinguishes revalidation,
copy, verification and finalization. Copy failures retain the active task ID and
observed committed chunk counters; completed copy results and verification
mismatches remain accessible. A post-copy mismatch fails the invocation even
though copied rows remain committed. Raw driver causes are omitted from the
facade exception. Existing command/driver logging continues to follow its own
configuration; this facade is not a log-redaction facility. Outcomes are currently
in-memory results, not a persistent aggregate execution report or recovery plan.

### Implemented source-only CSV export

`TransferFileExportService.export(jobFile, connections)` accepts a FILE/CSV job
and only requires its source runtime binding. The target binding is never used;
either Schema policy is accepted because target preparation belongs to import.
JSONL execution and explicit mapping files are currently rejected. The exporter
reuses the shared source metadata reader, bulk configuration resolver, dependency
ordering, JDBC keyset iterator and CSV writer. The initial implementation therefore
requires source tables with modeled NOT NULL unique keys; it is not an arbitrary
query or keyless-table exporter.

Source reads use one read-only SERIALIZABLE transaction and roll it back before
closing the borrowed connection. A driver that cannot provide that isolation is
rejected rather than silently weakening it. The application streams rows without
collecting the whole dataset; driver-side buffering still depends on the JDBC
driver and runtime connection settings. Caller-owned DataSources remain open.

Output goes into a unique `export-*` child of `files.directory`. It includes
`transfer.yaml`, canonical UTF-8 `source-schema.xml`, indexed `table-N.csv` files
and a final UTF-8 `manifest.json`. Table filenames never derive from untrusted
database identifiers. The manifest records original job/Schema byte fingerprints,
file settings, resolved source identities, ordered columns, actual emitted row
counts, file byte sizes and SHA-256 fingerprints. Schema XML contains metadata,
not exported row data. Files can be moved together without changing their byte
identities or requiring the original source connection.

CSV uses comma separation, double-quote escaping, quoted fields including empty
strings and null tokens, a header row and LF record separators. Quoted string
line endings and leading/trailing whitespace are preserved. Configured encoding
uses a strict encoder; unrepresentable characters fail instead of being replaced.
NULL uses the configured token; any non-null serialized value equal to that token
fails export. Supported values are strings, booleans, integral/decimal numbers
and finite floats/doubles. Temporal support and warning policy are described below.
Binary, LOB and other unsupported non-null value types
are explicitly rejected for now. Fields are limited to 1048576 Java characters.

Only after all row iterators, writers and the source connection close successfully
does the exporter publish the manifest atomically. Errors, token collisions,
encoding loss, unsupported values or a changed original job leave no completed
manifest. Partial output directories may remain for diagnosis; they are not
importable packages. Publishing checks are not a cross-process file lock.

`TransferFilePackageIO.read(manifestFile, expectedFingerprint)` checks a strict
bounded manifest, required counters, safe member names, job/settings consistency,
Schema fingerprints and each data file's size/hash. Missing members, symbolic
links and same-size data modifications are rejected. This is byte integrity,
not authenticity, independent recounting of CSV records, target validation or
import approval. An external expected manifest fingerprint pins reviewed bytes;
without it, reading is inspection only. Import must still validate the manifest's
table/column semantics against Schema and parse/recount records before writes.

## Execution and failure semantics

### Implemented CSV import and Gradle integration

`TransferFileImportService.review(manifest, expectedFingerprint, targetBindings)`
validates package bytes and CSV semantics before borrowing the target. It checks
table membership against the original job using the existing resolver, exact
catalog/schema/table and ordered column identities against canonical Schema,
header, width, strict value conversion, NULL constraints, actual record counts
and numeric ascending unique primary keys. Text primary keys and unsupported
canonical column types are rejected rather than guessed. Review validates live
existing empty target tables and binds the plan to the target connection identity.
With `CREATE_MISSING`, `TransferSchemaPreparation` generates a reviewed ordered
CREATE plan for absent selected ordinary tables using the target dialect's SQL
factory. Existing tables are checked before DDL. The target schema namespace
must exist; no table is altered, replaced or cleared. All same-schema FK parents
must be selected. Referenced columns are remapped through the shared model to
the target schema, including existing empty parents. Unsupported dependency
features (identity/default/generated/domain columns, temporary/partitioned/
inherited tables) are warnings by default and errors under strict policy.
Unresolved external-schema references still require explicit resolution.
This creates captured table definitions, not a complete database clone.

`execute(review, targetBindings, workspace)` pins/copies reviewed package members
to a unique workspace child, repeats preflight, checks live target identity and
plan, executes the unchanged reviewed CREATE plan when requested, then composes
the shared bulk planner, target validator, chunk executor and
ordered count/hash verifier. There is no original-source connection. Successful
copy followed by failed verification remains a failed overall invocation, with
committed rows retained. `ImportException.outcome()` and the version-1
`import-result.json` envelope retain phase, copy results, verification policy and
comparisons, committed progress and stopped task identity. Schema preparation
retains planned SQL, successful statement count and the one-based uncertain
attempt on DDL failure, separately from data-copy progress. DDL and committed
chunks may remain after failure. An unavailable audit
path explicitly signals failed/unavailable audit publication. Private working
copies and reports remain for inspection; capture is not a cross-process lock.

`ExportTransferFileCommand` and `ImportTransferFileCommand` expose the same services
without Swing. The Gradle plugin registers `exportTransferFile` and
`importTransferFile` as thin command adapters with no separate migration logic.
Import requires `allowExecution=true` and a reviewed manifest fingerprint, and
performs authorization/offline preflight before pool construction. Both tasks
disable caching and up-to-date skipping. TestKit covers export/import in separate
Gradle runs with the source database moved offline before import, and verifies a
repeat import fails instead of being skipped or clearing the target.

See the [task guide](../gradle-plugin/file-transfer.md) and
[runnable example](../examples/file-transfer/README.md). The separate companion
example checkout is outside this task's authorized writable scope; no external repository was changed. CSV import
currently attempts same/different product pairs, with existing targets or optional missing
ordinary-table preparation, numeric keys and the documented scalar types.
Full Schema recreation, vendor-specific heterogeneous mapping and JSONL execution
remain incomplete; product-name mismatch alone is a warning in the CSV path.

CSV temporal support now uses `TransferCsvTemporalCodec` for canonical local DATE,
TIME(0) and TIMESTAMP(0..9). TIMESTAMP retains nanoseconds; invalid/local-gap values
and excess source precision fail rather than normalize. TIME fractional precision
and timezone/vendor temporal types default to warning-only export. A cloned text
projection plus the shared keyset source's protected value-extraction hook calls
JDBC `getString` for those columns, preserving the driver's text without an
intermediate SQL Time conversion. No extra SELECT per row is issued. Canonical
Schema, selected identities and ordering remain unchanged. The manifest retains
column warning identities and messages; `files.unsupportedTemporalPolicy: ERROR`
opts into strict failure instead. Warnings do not claim typed import support;
conversion is required for those temporal columns. Existing configurations and
constructors remain readable/available; newly written manifests have additive
warning metadata. See the [temporal guide](../gradle-plugin/file-transfer.md#temporal-values-and-export-warnings).

The Swing CSV workflow composes the same services: source discovery, the shared
table picker, `TransferFileJobDraftBuilder` offline authoring, explicit export,
target-only review and confirmed import. The builder checks the captured Schema
fingerprint and shared CSV type/key eligibility before saving; no export or
database changes occur on save. Generated jobs use logical `source` / `target`
bindings, an existing-target policy by default (optional missing-table creation)
and a relative `packages` directory, and contain
no credentials. Users can run the resulting definition/package with Gradle.
All service work runs outside Swing's event thread. Input changes invalidate
review, and execution rechecks the pinned package and target. The GUI displays
the shared import outcome on failure as well as success. Reviewed DDL appears in
a read-only RSyntax SQL tab and is cleared when inputs invalidate review. See the
[GUI guide](../../sqlapp-swing/README.md#csv-transfer-without-preparing-yaml).

Schema preparation, data copying and verification have distinct outcomes within
one aggregate job result. Record completed work, resolved identities, relevant
fingerprints and the exact failure phase. Non-transactional DDL and committed
data chunks mean a later failure cannot be reported as a rollback of the whole
job. Verification failure must not be presented as successful completion.

Another run must inspect existing objects and checkpoint/evidence state before
deciding which work is still valid. Never repeat Schema creation or data copy
merely because a later phase failed. Recovery and resume use supported shared
policies and require explicit decisions where no safe default exists.

## Current implementation and gaps

- Existing `ExportDataCommand` and `ImportDataCommand` provide data-format and
  JDBC primitives. Their defaults are not the transfer-job contract: import
  defaults to MERGE, CSV encoding defaults to the platform charset, and their
  general-purpose file handling has no transfer-package integrity boundary.
  Do not wire them into the new file route without explicit INSERT/empty-target,
  encoding/null, identity/order and package validation adapters. In particular,
  the new CSV exporter configures the token explicitly and rejects collisions;
  the CSV importer preserves the same policy and verifies typed package rows.

- GUI-generated Data Job YAML already uses the existing shared bulk-job format.
  `DataMigrationExecutionService` delegates to `ExecuteBulkMigrationJobCommand`;
  `ExecuteBulkMigrationJobTask` accepts that configuration file in Gradle.
- `DataMigrationDraftService` creates source Schema and an initial-copy job;
  the direct JDBC GUI assumes existing, same-named empty target tables. The CSV
  GUI optionally prepares missing ordinary tables from the shared transfer job.
- `SchemaComparisonService` is an offline same-product before/after comparison.
  It explicitly rejects different products. It is not the implementation of
  heterogeneous target Schema preparation.
- Assessment/mapping and target DDL generation already exist in command/core
  facilities. Establish their supported source/target combinations and compose
  them; do not remove the same-product comparison guard to simulate conversion.
- The CSV transfer job now coordinates optional missing-table preparation, data
  copy, verification and runtime connection references in GUI and Gradle. A
  complete Schema clone and vendor-specific heterogeneous conversion remain
  unfinished. Direct transfer definitions now have a shared command/Gradle
  adapter and desktop integration. The legacy Data Job tools remain available.

## Implementation order and acceptance evidence

1. Define and test the common command-level job contract and runtime input
   boundary, including round-trip loading, relative paths, invalid inputs and
   secret exclusion. Preserve existing Data Job YAML and task APIs.
2. Compose target Schema preparation and existing copy/verification services.
   Test unsupported conversions, target collisions, partial DDL failure,
   committed copy failure and verification failure without external databases.
3. Add a Gradle adapter with registration, conventions, property mapping and
   execution tests; provide a matching runnable example and task documentation.
4. Make the desktop entry a source-to-target transfer workflow using the same
   saved definition. Add open/edit/save and explicit execution handoff tests.
5. Demonstrate that a GUI-authored job runs in Gradle and a Gradle-authored job
   opens in the GUI. Compare resolved plans, blockers and result semantics, not
   just serialized field names. Include moved job directories and separate JVM
   execution with runtime credentials and drivers.

Track product support explicitly. Embedded integration tests do not establish
compatibility for every pair of supported database dialects. External database
validation remains separate and requires authorization for the target and writes.
