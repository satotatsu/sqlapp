# CSV packages: export, move, import and verify

[Plugin index](README.md) · [Task reference](task-reference.md)

`exportTransferFile` and `importTransferFile` use the same transfer job definition
as the Java services. Export needs only the source DataSource; import needs only
the target DataSource and the completed package. Import does not reconnect to the
source. These tasks are available in this checkout; use a plugin build containing
them. The runnable [example](../examples/file-transfer/README.md) supplies runtime
driver coordinates and connection values externally.

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
non-null columns, and ascending unique numeric primary keys. Only indexed package
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

This CSV path requires existing empty targets or explicit missing-table creation,
no explicit mapping file, and numeric NOT NULL primary keys. Strings,
booleans, integer/decimal values and finite float/double values are supported.
Other temporal types are exportable with warnings but require conversion for
automatic import. Binary/LOB values, string keys, JSONL execution, full target Schema
recreation and complete vendor-specific cross-product conversion are not implemented. Export requires
SERIALIZABLE source reads; JDBC driver buffering still depends on runtime settings.
The Swing **CSV package** screen uses these same services. **New CSV job…** reads
source metadata and saves this format from a table picker without handwritten
YAML. Export, target review and confirmed import are separate actions. See the
[GUI guide](../../sqlapp-swing/README.md#csv-transfer-without-preparing-yaml).
