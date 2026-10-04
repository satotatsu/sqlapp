# Database migration assessment

[Task guide](README.md) · [Task reference](task-reference.md)

`assessDatabaseMigration` is the common entry point for offline migration
diagnosis. Specify a source file and an explicit target database and version.
The source inventory and the target compatibility rules are discovered through
ServiceLoader from the runtime classpath. The minimum configuration writes only
the assessment report. A reviewed mapping can optionally produce DDL preview
files, but the task never executes DDL or DML and never connects to the target
database.

## Supported targets

The source is currently Access MDB/ACCDB. Supported target combinations are:

| `targetDatabase` | `targetVersion` | Runtime module |
|---|---|---|
| `oracle` | `19c`, `21c`, `23ai`, `26ai` | `sqlapp-core-oracle` |
| `sqlserver` | `2016`, `2017`, `2019`, `2022` | `sqlapp-core-sqlserver` |

Target database names are case-insensitive. A dialect's ability to generate SQL does **not** establish migration
assessment support. Other sources, targets and versions fail explicitly until
a matching assessment provider is installed.

## Access to Oracle

In a build applying `java` and `com.sqlapp.db`, using Java 21:

```groovy
dependencies {
    runtimeOnly 'com.sqlapp:sqlapp-core-oracle:<sqlapp-version>'
}

tasks.named('assessDatabaseMigration') {
    inputFile = layout.projectDirectory.file('input/customer.accdb')
    targetDatabase = 'oracle'
    targetVersion = '19c'
    outputFile = layout.buildDirectory.file('reports/migration.json')
    htmlOutputFile = layout.buildDirectory.file('reports/migration.html')
}
```

Use matching sqlapp versions. Run `./gradlew assessDatabaseMigration`, or
`gradlew.bat assessDatabaseMigration` on Windows. The plugin already includes
the MDB module for Access file export. The target Oracle module must be added
to the runtime classpath; no Oracle JDBC driver or database installation is
required. See [runtime setup](getting-started.md) for builds without `java`.

For SQL Server, use the same task with its runtime provider:

```groovy
dependencies {
    runtimeOnly 'com.sqlapp:sqlapp-core-sqlserver:<sqlapp-version>'
}

tasks.named('assessDatabaseMigration') {
    inputFile = layout.projectDirectory.file('input/customer.accdb')
    targetDatabase = 'sqlserver'
    targetVersion = '2022'
    outputFile = layout.buildDirectory.file('reports/migration.json')
}
```

The SQL Server module retains its existing JDBC dependency, but this assessment
never opens a connection. SQL Server 2025, older releases and Azure SQL are not
accepted by this provider. See [SQL Server diagnostic coverage](access-sqlserver-migration-assessment.md).

| Property | Type | Requirement/default |
|---|---|---|
| `inputFile` | `RegularFileProperty` | Required existing source file supported by exactly one inventory provider |
| `targetDatabase` | `Property<String>` | Required: `oracle` or `sqlserver` (case-insensitive) |
| `targetVersion` | `Property<String>` | Required: an explicit version from the supported targets table; Oracle version names are case-insensitive |
| `outputFile` | `RegularFileProperty` | Required JSON path distinct from the input |
| `htmlOutputFile` | `RegularFileProperty` | Optional standalone HTML review report, distinct from the input and JSON output |
| `mappingFile` | `RegularFileProperty` | Optional version 1 target mapping YAML, bound to the source fingerprint and requested target |
| `mappingTemplateFile` | `RegularFileProperty` | Optional generated YAML skeleton containing every local table/column and target-specific suggested types |
| `ddlOutputFile` | `RegularFileProperty` | Optional review-only target `CREATE TABLE` SQL; requires `mappingFile` |
| `ddlPhaseOutputDirectory` | `DirectoryProperty` | Optional atomic per-file output containing `phase-1.sql` through `phase-4.sql`, `appendix.sql` and `manifest.sha256`; requires `mappingFile` |
| `failOnBlockers` | `Property<Boolean>` | Defaults to `true`; blockers fail the task after publishing the report |
| `scanData` | `Property<Boolean>` | Defaults to `false`; opt in to local Access scalar data profiling |

Missing or ambiguous providers, unsupported combinations and invalid inputs
fail without publishing a replacement report. An older report may remain, so
the failed invocation must not be treated as fresh evidence. The task always
reruns its failure policy. `failOnBlockers=false` retains all blockers while
allowing inventory collection to finish; it does not suppress execution errors.

The version 1 JSON report preserves the source fingerprint, resolved source
and target product names, normalized target version, logical migration method,
coverage flags, inventory and findings from both providers. A source blocker
cannot be hidden by a target assessment. Results are `BLOCKED` or
`REVIEW_REQUIRED`, never a readiness certification. No target is inferred.

The Access inventory reports every saved query and separates non-hidden,
non-parameterized SELECT/UNION view candidates from queries requiring manual
porting. It also counts hidden, parameterized and action or special queries.
Those category counts can overlap. Query SQL and connection details are not
copied into the assessment report.
The same source inventory counts all Access secondary indexes, unique indexes
and indexes using Access `IgnoreNulls`. Target-specific findings still explain
the Oracle or SQL Server uniqueness, NULL and collation review required for
each affected index.
An `access.file-format` finding records the actual Jet/ACE format recognized
from the file rather than inferring it from the MDB/ACCDB extension. Use it to
confirm driver bitness, deployment support and any retained Access frontend.
Access text columns with `AllowZeroLength=true` are retained in Schema
specifics, counted as `accessAllowZeroLengthColumns`, and reported individually
as `access.allow-zero-length`. This makes the Access distinction between NULL
and an empty string explicit before Oracle conversion or client validation is
designed. Mapped columns retain the same issue as
`migration.mapping.allow-zero-length`, and generated DDL review comments state
that target validation remains unspecified.
Mapped Access calculated fields produce
`migration.mapping.calculated-expression`. The DDL review records the Access
formula, notes that the generated target column is materialized, and shows the
configured load conversion so reviewers must choose between recalculation and
preserving loaded values.
Access table and field validation rules produce
`migration.mapping.table-validation-expression` and
`migration.mapping.column-validation-expression`. They are listed in the DDL
appendix for translation review; only target `checkExpressions` explicitly
provided by the mapping are emitted as CHECK constraints. Source expressions
in review comments escape newlines and control characters so they cannot create
additional executable SQL lines.
When mapped columns use these Access features, the phase 2 completion gate adds
specific checks for calculated-value behavior and representative validation,
empty-string, insert and update tests before phase 3 creates keys and indexes.
The Access inventory also counts AutoNumber columns, columns with defaults,
calculated columns, field validation rules and table validation rules. These
overlapping counts provide an early estimate of mapping and regression-test
work without reading row values.
Relationship inventory separately counts all collected Access relationships,
cascade-update relationships and cascade-delete relationships. The cascade
counts can overlap and remain zero when relationship collection is intentionally
skipped because linked tables are present.
Mapped cascade relationships produce `migration.mapping.relationship-action`.
The DDL appendix lists the Access update/delete action and confirms that target
cascade clauses were omitted, requiring an Oracle- or SQL Server-specific
design before phase 4 applies foreign keys. When any mapped relationship uses a
cascade action, the phase 4 script repeats this approval requirement immediately
before its foreign-key statements.

When `htmlOutputFile` is set, the task also writes a self-contained UTF-8 HTML
review report. It summarizes status and evidence counts, orders blockers before
warnings and review items, and shows inventory, column aggregates and integrity
checks when present. It has no external CSS, JavaScript or network dependency,
and all source-derived text is HTML-escaped. The JSON report remains the
machine-readable evidence and the HTML contains no additional source values.
Both files are replaced atomically as individual files. If optional HTML
publication fails after JSON publication, the command fails but the new JSON
report and `getReport()` remain available; regenerate the HTML before treating
the pair as matching artifacts.

## Target table and column mapping

Generate a starting file directly when desired:

```groovy
tasks.named('assessDatabaseMigration') {
    mappingTemplateFile = layout.buildDirectory.file('reports/access-target-template.yaml')
}
```

The template contains every local table and column, the current source
fingerprint, normalized target/version, source nullability and a target-specific
type suggestion. Oracle suggestions account for the selected BOOLEAN version;
SQL Server suggestions use its native scalar types. Source lengths and decimal
precision/scale are used where available. Complex, unknown and other types that
need an extraction decision have `targetType: null`, deliberately making the
template invalid as an input until the user supplies a decision. Suggested
types are reviewable starting points, not proof that observed values, clients,
collations or target settings are compatible.
Access AutoNumber columns remain required, but generated templates leave
`identity: null` as an explicit unresolved choice because the available Access reader cannot distinguish
increment from random numeric generation. After confirming the source setting,
set it to `true` to have Oracle render `GENERATED BY DEFAULT AS IDENTITY` or SQL
Server render `IDENTITY(1,1)`, or set it to `false` when using a documented
alternative generator.
Identity requests on unsupported target types, or more than one identity column
in a table, are mapping blockers and do not replace an existing DDL preview.

The generated YAML is an atomic, standalone output and can be edited outside the
build directory. Template generation does not require `scanData`; enabling the
scan later lets the mapping validators compare the edited choices to actual
aggregates. `mappingTemplateFile` must be distinct from the Access input, JSON,
HTML and `mappingFile`. It can be used alongside `mappingFile` at a different
path to refresh a comparison template. Generating a template alone does not
change the JSON format version.

Set `mappingFile` after reviewing the generated template or an initial report. Copy its
`sourceFingerprint` into a concise YAML file so a mapping cannot silently be
used with a different Access file. The configured target database and normalized
version must also match the task. A mapping-enabled run writes report format 3,
including the mapping file fingerprint and fully resolved source identifiers.

```yaml
format: sqlapp-database-migration-mapping
version: 1
sourceFingerprint: sha256:0123456789abcdef... # copy the complete value from the JSON report
targetDatabase: oracle
targetVersion: 19c
tables:
  - sourceTable: 顧客
    targetSchema: APP
    targetTable: CUSTOMERS
    checkExpressions:
      - "CUSTOMER_NAME IS NOT NULL"
    columns:
      - sourceColumn: 顧客ID
        targetColumn: CUSTOMER_ID
        targetType: NUMBER(10,0)
        nullable: false
        identity: true
      - sourceColumn: 顧客名
        targetColumn: CUSTOMER_NAME
        targetType: VARCHAR2(200 CHAR)
        nullable: false
        defaultExpression: "'unknown'"
        conversion: preserve Unicode text and reject empty required values
```

```groovy
tasks.named('assessDatabaseMigration') {
    mappingFile = layout.projectDirectory.file('migration/access-target.yaml')
    ddlOutputFile = layout.buildDirectory.file('reports/access-target.sql')
    ddlPhaseOutputDirectory = layout.buildDirectory.dir('reports/access-target-phases')
    scanData = true
}
```

`ddlOutputFile` optionally writes an atomic, review-only `CREATE TABLE` preview
from a valid `mappingFile`. Oracle identifiers are double-quoted and SQL Server
identifiers are bracket-quoted. The first comments record the source file
fingerprint, mapping file fingerprint, normalized target database and target
version. Reviewers can compare these values with the JSON report and reject a
DDL file produced from stale or different inputs. The preview contains mapped tables, columns,
target types and explicit nullability. Its opening mapping summary reports total,
mapped and omitted Access tables and columns, plus data-profile and relationship
collection status. It also reports how many mapped Access AutoNumber strategies
remain unresolved. A second summary reports how many target tables, primary keys,
unique constraints, checks, indexes and foreign keys were emitted, with counts of
omitted keys, indexes and foreign keys. Four phase markers separate table creation,
data loading and verification, secondary-index creation, and final foreign-key
application. Secondary indexes and foreign-key statements appear after the
commented verification SQL so executing the phases independently avoids index
maintenance during the initial load and does not constrain that load with foreign keys.
Primary-key and safe unique constraints are also applied in the third phase after
duplicate verification; primary-key columns remain `NOT NULL` during the load.
For every mapped Access AutoNumber column, the suggested load order includes
target-specific guidance. SQL Server output identifies the exact quoted table
that requires `SET IDENTITY_INSERT ... ON` during its load, turns it off in the
documented sequence, and emits quoted `DBCC CHECKIDENT` inspection and conditional
reseed commands. Oracle output notes that explicit values can be loaded into
`GENERATED BY DEFAULT AS IDENTITY` columns and emits a quoted `ALTER TABLE ...
START WITH LIMIT VALUE` command to advance the generator from the loaded maximum.
Each mapped numeric AutoNumber column also receives a quoted `SELECT MIN(...),
MAX(...)` query so negative random values and positive incrementing values are
both covered,
including when the target `identity` choice is still unspecified. The query
remains available when the optional source data scan was not run. When it
was run, compare this result with the source range baseline emitted later in the
same phase. Access GUID AutoNumber columns do not receive a numeric maximum
query; the DDL states that their preserved values must instead be checked with
row-count and duplicate verification. Every mapped Access AutoNumber column
receives a duplicate query even when it is not part of an emitted primary or
unique key; an equivalent key query is emitted only once. Its normal target
NULL-count query is annotated with an expected result of zero, including when
no target `NOT NULL` or primary-key constraint is emitted, so the DDL does not
repeat the same query.
Target DDL generation rejects a mapped table with no mapped columns instead of
producing an unusable empty `CREATE TABLE` statement. The normal YAML resolver
performs the same validation earlier with the source table name in its error.
Direct DDL API calls also reject source table identities that are absent from
the supplied Access Schema and duplicate mappings for the same source table;
both errors identify the affected Access table.
They likewise reject missing or duplicate source fields, case-insensitive
duplicate target column names, and blank target column names or data types
before emitting any DDL.
Blank or control-character target table identities are also rejected. Mapping
multiple Access tables to the same case-insensitive target schema and table is
an error rather than producing duplicate `CREATE TABLE` statements.
Executable target type, default and CHECK fragments must be nonblank,
single-line and free of statement separators and SQL comments. This validation
is applied while resolving YAML and again for direct resolved-mapping API calls
before any DDL is returned.
A target default expression cannot be combined with `identity: true`; this is
enforced both while resolving YAML and by the shared DDL generator.
Likewise, `identity: true` cannot be combined with `nullable: true`; choose a
required target column or a non-identity key-generation design. Generated
mapping templates set Access AutoNumber columns to `nullable: false` even when
the Access metadata does not separately expose their required status.
Numeric and GUID AutoNumber/Replication ID columns remain required but omit an
automatic identity choice in generated templates. Their visible `identity: null`
entry requires review. For numeric columns, first confirm whether
Access uses increment or random new values, then explicitly select the target
generation strategy. Oracle and SQL Server numeric identity clauses are not GUID
generators, so GUID columns require a target GUID generation strategy.
If an edited YAML file or direct API mapping sets `identity: true` for an Access
GUID AutoNumber, assessment stops before producing DDL and identifies the
affected table and field.
Access AutoNumber columns are treated as required source values even when the
reader does not expose a separate `NOT NULL` flag. This prevents a false
nullability-change review and keeps unique-key safety checks consistent.
The end of phase 2 contains a completion gate: reconcile every generated target
count and range query with its displayed Access baseline, obtain and approve
baselines marked `not scanned` or `unavailable`, require every generated
duplicate query to return no rows and every generated orphan count to be zero,
and confirm preservation of mapped Access AutoNumber values and the approved
target key-generation strategy when applicable. This condition remains present
when the target `identity` choice is still unspecified. It instructs reviewers to stop
before key and index creation until these checks pass or an exception is
explicitly recorded.
When an Access AutoNumber is mapped without target identity, the Phase 2 gate
and DDL semantic-review appendix also require confirmation of Increment, Random
or GUID behavior, documentation of its replacement and a first-new-row test.
Stable `-- sqlapp:phase-N:begin` and `-- sqlapp:phase-N:end` comments enclose
each phase exactly once so scripts can extract a phase without relying on the
human-readable heading. Review-only mapping, omission and generated-name details
follow phase 4 inside matching `-- sqlapp:appendix:begin` and `:end` markers.
Set `ddlPhaseOutputDirectory` to have sqlapp perform that extraction. It writes
`phase-1.sql` through `phase-4.sql` plus `appendix.sql`; every file repeats the
source fingerprint, mapping fingerprint and normalized target header and is
replaced atomically. The review preamble and mapping summary are retained inside
the Phase 1 markers; the other files start their section immediately after the
three-line provenance header. A `manifest.sha256` file is replaced last and records the
SHA-256 of all five files and the assessment JSON that produced them, allowing a runner or reviewer to detect a partial
update, manual edit or mixture of outputs from different assessment runs before
executing a phase. The directory and its fixed output names must not collide
with the input, report, mapping, template or combined DDL paths.
For a quick internal-integrity check, only the generated directory is required:

```groovy
tasks.named('verifyDatabaseMigrationDdlPhases') {
    directory = layout.buildDirectory.dir('reports/access-target-phases')
}
```

This minimum configuration checks the manifest, all five DDL hashes, phase
markers and common provenance without reading a database. The assessment file,
expected fingerprints, verification report and readiness gates are optional.
Without `assessmentReportFile`, the verifier retains the assessment fingerprint
recorded in the manifest as evidence but does not validate the referenced JSON
file or apply assessment-based policies.
Use two verification passes before executing the files. First, verify internal
integrity and write a candidate evidence report without an approval gate:

```groovy
tasks.named('verifyDatabaseMigrationDdlPhases') {
    directory = layout.buildDirectory.dir('reports/access-target-phases')
    assessmentReportFile = layout.buildDirectory.file('reports/migration.json')
    verificationReportFile = layout.buildDirectory.file('reports/ddl-verification-candidate.json')
}
```

Review that report together with the assessment, mapping and DDL. Its
`assessmentReportFingerprint` and `manifestFingerprint` are the exact values to
record in the approval system. After approval, configure a separate deployment
verification using those recorded values:

```groovy
tasks.named('verifyDatabaseMigrationDdlPhases') {
    directory = layout.buildDirectory.dir('reports/access-target-phases')
    assessmentReportFile = layout.buildDirectory.file('reports/migration.json')
    expectedAssessmentReportFingerprint = 'sha256:...'
    expectedManifestFingerprint = 'sha256:...'
    // Simple deployment gate; requires assessmentReportFile and both expected fingerprints.
    requireDeploymentReady = true
    verificationReportFile = layout.buildDirectory.file('reports/ddl-verification.json')
}
```

Do not derive the expected values from the files during the deployment run;
that would confirm only self-consistency, not prior approval. A changed
assessment, mapping, Access source, manifest or phase file requires a new
candidate verification and approval.

The verifier requires exactly the five DDL entries and one assessment-report
fingerprint in the manifest, recalculates every DDL SHA-256, validates the
matching begin/end marker in each file, rejects any other `.sql` file in the
phase directory, and requires an
identical source fingerprint, mapping fingerprint and target header across the
set. Each of those provenance headers must occur exactly once and both
fingerprints must use the canonical lowercase SHA-256 form. Additional text or
SQL is rejected before the section's begin marker. Optional expected fingerprint
and target properties bind verification to
an approved Access file, mapping and deployment target instead of accepting any
internally consistent directory. As the simpler default, `assessmentReportFile`
reads those four expected values from the JSON produced by
`assessDatabaseMigration` and verifies that JSON against the hash embedded in
`manifest.sha256`. The individual `expectedSourceFingerprint`,
`expectedMappingFingerprint`, `expectedTargetDatabase` and
`expectedTargetVersion` properties remain available when approval values come
from another system. Set `expectedAssessmentReportFingerprint` when the report
itself is an approved artifact; the verifier checks its SHA-256 before trusting
the values inside it. Optional `verificationReportFile` is atomically written
only after successful verification and records the assessment report, source,
mapping, target, manifest and five DDL fingerprints for CI or deployment
evidence. It must be outside the phase directory and must not overwrite the
assessment report. Pass its `manifestFingerprint` back as
`expectedManifestFingerprint` during deployment to require the exact approved
manifest and DDL set. When the assessment inventory provides it, verification
report format 2 also records `unresolvedAutoNumberStrategies`; older assessment
reports without that inventory remain verifiable and omit the value. The same
report records `unmappedTables` and `unmappedColumnsInMappedTables` when those
inventory values are available, even when the incomplete-mapping gate is off.
It also records `mappingSemanticDifferences` and the assessment `status` when
available. `verificationPolicies` lists the effective blocker, completeness,
AutoNumber and semantic-difference checks applied to that successful run, so a
CI result distinguishes integrity-only verification from deployment-readiness
verification. `status: VERIFIED` means that every check configured for that
invocation passed. It does not by itself mean that a reviewer approved the
artifacts. An empty `verificationPolicies` list identifies the directory-only
integrity check; `APPROVED_FINGERPRINTS` identifies a run that verified both
supplied approval fingerprints. `DEPLOYMENT_READY` identifies the composite
gate, including its assessment format, Access source and matching target-product
checks; the component policies are also listed.
Verification does not open a database connection.

## Generate an initial data-load job

After the mapped assessment is reviewed, generate a YAML configuration for the
existing `executeBulkMigrationJob` task:

```groovy
tasks.named('generateAccessBulkMigrationJobConfiguration') {
    assessmentReportFile = layout.buildDirectory.file('reports/migration.json')
    schemaFile = layout.buildDirectory.file('schema/access.xml')
    outputFile = layout.buildDirectory.file('reports/access-load.yaml')
}
```

The generated job enables post-load row-count and ordered chunk-hash
verification by default, fails the job on a mismatch, and writes
`<job-name>-verification.json` beside the job YAML. Set
`verificationReportFile` to store that evidence elsewhere, or set
`verification = false` only when verification will be performed separately.
It also writes task and chunk progress, completion and failure details to
`<job-name>-operations.json`. Set `operationalReportFile` to change that path,
or set `operationalReport = false` when another operational reporting listener
is configured.

The generated job uses resumable 10,000-row `INSERT` chunks and preserves
Access AutoNumber values based on the source Schema XML, including mappings
that implement future values with a target sequence rather than an identity
column. `chunkSize`, `resume` and `jobId` are optional.
Each task carries the assessment source fingerprint and resolved mapping
fingerprint as its source and target checkpoint identities. A resumed job is
therefore rejected when its captured Access source or reviewed mapping has
changed.
Task IDs use the qualified Access table name, for example
`access:Sales.Customers`, so operational and verification reports identify the
original Access object without relying on generated sequence numbers.
The durable `migrationId` is namespaced as `<jobId>:<qualified Access table>`
so different explicitly named jobs do not share checkpoint progress in the
same target checkpoint table. Set a stable, unique `jobId` when a target
database hosts more than one Access migration.
The resolved Access primary-key columns are written to each task's
`keysetColumns` in declared order, making the resume key reviewable without
adding user configuration.
The generator also writes the loadable, non-computed Access columns to
`verificationColumns`. This makes the exact ordered chunk-hash comparison scope
visible in the YAML; hidden and formula columns are excluded consistently with
the bulk writer.
`checkpointMode` defaults to `DATABASE`, which commits each data chunk and its
checkpoint atomically on the target connection. Set it to `FILE` only when the
selected target bulk provider cannot participate in that transaction. The
default file directory is `<job-name>-checkpoints`; `checkpointDirectory` can
override it. `CUSTOM` is available only through the programmatic bulk API.
Set `leaseOwnerId` to a stable worker or deployment-runner ID to add a target
database lease and reject concurrent execution of the same job and plan. The
lease is optional because an honest owner ID cannot be inferred from the
offline files. Its default duration is 300 seconds and can be changed with
`leaseDurationSeconds`; the executor renews it while the job is active.
For an approval-controlled pipeline, set
`expectedAssessmentReportFingerprint` to the lowercase `sha256:...` value of
the approved assessment JSON. The generator then rejects a changed report
before parsing or producing the job. This gate is optional for the simple
interactive workflow.
Set `ddlVerificationReportFile` to the format 2 JSON evidence produced by
`verifyDatabaseMigrationDdlPhases` when data loading must wait for approved DDL.
The generator requires `VERIFIED`, the `DEPLOYMENT_READY` policy, and exact
assessment-report, source, mapping, target-product and target-version
provenance. The gate is optional for workflows that provision the target by a
separate controlled process.
Set `expectedDdlVerificationReportFingerprint` to the approved lowercase
`sha256:...` value when the verification report itself is an approved artifact.
The generator checks the complete file before parsing it, so replacing the DDL
evidence requires a new approval. This option requires
`ddlVerificationReportFile` and remains optional for the simple workflow.
The generated YAML records the assessment-report fingerprint and, when the DDL
gate is used, the DDL-verification-report fingerprint under `provenance`. These
values make the reviewed inputs traceable without making either approval gate
mandatory at execution time.
After reviewing the generated YAML, deployment automation can set
`executeBulkMigrationJob.expectedConfigurationFingerprint` to its approved
lowercase `sha256:...` value. The executor verifies the entire configuration
file before parsing it or opening the source connection. This final gate is
optional for interactive runs.

Before the load window, validate that exact YAML against the live target without
moving any rows:

```groovy
tasks.named('validateBulkMigrationTarget') {
    configurationFile = layout.buildDirectory.file('reports/access-load.yaml')
    sourceDataSource { jdbcUrl = 'jdbc:ucanaccess:///data/source.accdb' }
    dataSource {
        jdbcUrl = 'jdbc:oracle:thin:@//target.example:1521/app'
        username = providers.gradleProperty('dbUser')
        password = providers.gradleProperty('dbPassword')
    }
    expectedConfigurationFingerprint = providers.gradleProperty('approvedJobFingerprint')
    assessmentReportFile = layout.buildDirectory.file('reports/migration.json')
    ddlVerificationReportFile = layout.buildDirectory.file('reports/ddl-verification.json')
    reportFile = layout.buildDirectory.file('reports/target-validation.json')
    targetEnvironmentId = 'production-oracle'
}
```

The task uses the executor's configuration resolver and validates target table
identity, renamed columns, non-null primary or unique keys, and the generated
`requireEmptyTarget: true` condition. It reads live target metadata and, for the
empty-target condition, at most the first ordered target row. It creates no
checkpoints, leases or migrated rows. The three approval properties and report
output remain optional. When approval files are supplied, their complete-file
SHA-256 values must match the YAML provenance. A successful report atomically
records the configuration, plan, job, task and provenance identities for CI.
It also records the database product, version, catalog and schema. Set the
optional `targetEnvironmentId` when separate environments expose the same
database identity values.

Require that evidence when executing the load:

```groovy
tasks.named('executeBulkMigrationJob') {
    configurationFile = layout.buildDirectory.file('reports/access-load.yaml')
    sourceDataSource { jdbcUrl = 'jdbc:ucanaccess:///data/source.accdb' }
    dataSource { jdbcUrl = 'jdbc:oracle:thin:@//target.example:1521/app' }
    targetValidationReportFile = layout.buildDirectory.file('reports/target-validation.json')
    expectedTargetValidationReportFingerprint = providers.gradleProperty('approvedTargetValidationFingerprint')
    maxTargetValidationAgeSeconds = 3600L
    targetEnvironmentId = 'production-oracle'
}
```

The age limit has no implicit default because the acceptable interval depends
on the deployment process. The report fingerprint is optional. Even with this
gate, execution repeats the live metadata, key and empty-target checks before
the first lifecycle operation or row write. It also compares the recorded
database product, version, catalog, schema and optional environment ID with the
current target connection.
Deployment automation can also set `assessmentReportFile` and
`ddlVerificationReportFile` on `executeBulkMigrationJob`. When either file is
present, the executor recomputes its SHA-256 and requires an exact match with
the corresponding value under the YAML `provenance` object before moving any
data. Both inputs are optional, independently selectable approval gates; an
ordinary interactive run still needs only the generated YAML and its Schema
XML.
For declarative jobs, the executor carries the configuration fingerprint and
the optional assessment, DDL-verification and target-validation report fingerprints into both generated
JSON artifacts. Operational report format 3 and post-load verification report
format 6 expose the same `provenance` object, allowing an audit to connect the
reviewed inputs, executed plan, progress and verification outcome. Readers
continue to accept operational report format 2 and verification report format
5; those older reports have no provenance field.

After the load, `verifyBulkMigrationEvidence` performs the corresponding
offline audit:

```groovy
tasks.named('verifyBulkMigrationEvidence') {
    operationalReportFile = layout.buildDirectory.file('reports/access-load-operations.json')
    verificationReportFile = layout.buildDirectory.file('reports/access-load-verification.json')
    configurationFile = layout.buildDirectory.file('reports/access-load.yaml')
    assessmentReportFile = layout.buildDirectory.file('reports/migration.json')
    ddlVerificationReportFile = layout.buildDirectory.file('reports/ddl-verification.json')
    targetValidationReportFile = layout.buildDirectory.file('reports/target-validation.json')
    expectedTargetEnvironmentId = 'production-oracle'
    outputFile = layout.buildDirectory.file('reports/access-load-evidence.json')
}
```

It requires a completed job, matching migrated data, the same plan fingerprint
and identical provenance in both reports. Supplied source artifacts are hashed
again and compared with that provenance. The three source files are optional;
the two reports are required. Provenance is required by default because it is
the link to the reviewed inputs. Set `requireProvenance = false` only to inspect
older or programmatically generated reports; `requireSuccessfulExecution` and
`requireMatchingData` can likewise be relaxed for failure investigation. The
task is file-only and never opens a database connection.
`outputFile` is optional. When present, the task atomically writes a format 1
JSON audit artifact containing the plan and job IDs, both input-report
fingerprints, the effective verification policies, the artifacts that were
checked, the final execution and data-match state, and the common provenance.
When `targetValidationReportFile` is supplied, the offline audit also verifies
its exact bytes and confirms that its job, plan, configuration and approval
provenance match the execution evidence. The recorded validation time must not
be later than the operational report, and the validated task IDs must exactly
match the executed task IDs. Set `expectedTargetEnvironmentId` to
reject otherwise valid evidence created for another deployment environment.

The complete supported workflow is therefore: generate or review the job,
run `validateBulkMigrationTarget`, execute with the approved target-validation
fingerprint, run the configured post-load verification, create the audit JSON
with `verifyBulkMigrationEvidence`, and later revalidate the saved audit with
`verifyBulkMigrationEvidenceReport`. The integration suite exercises this
whole chain, including an Access-named source table, a renamed target table and
column, live data transfer, exact provenance, and tamper rejection.
`BulkMigrationEvidenceReportIO.read(evidence, operations, verification)` can
later revalidate both source-report hashes and their plan, state and provenance
against the saved audit artifact. Reading also rejects a policy that disagrees
with the recorded result, unknown policy or artifact names, and missing source
report entries.
The same check is exposed as `verifyBulkMigrationEvidenceReport` for deployment
and archival pipelines:

```groovy
tasks.named('verifyBulkMigrationEvidenceReport') {
    evidenceReportFile = layout.buildDirectory.file('reports/access-load-evidence.json')
    operationalReportFile = layout.buildDirectory.file('reports/access-load-operations.json')
    verificationReportFile = layout.buildDirectory.file('reports/access-load-verification.json')
    targetValidationReportFile = layout.buildDirectory.file('reports/target-validation.json')
    expectedTargetEnvironmentId = 'production-oracle'
    expectedEvidenceReportFingerprint = providers.environmentVariable('APPROVED_EVIDENCE_SHA256')
    expectedPlanFingerprint = providers.environmentVariable('APPROVED_PLAN_ID')
    expectedConfigurationFingerprint = providers.environmentVariable('APPROVED_JOB_SHA256')
    maxEvidenceAgeSeconds = 86400
}
```

`expectedEvidenceReportFingerprint` is optional. When supplied, the task checks
the exact lowercase `sha256:...` identity of the audit JSON before parsing it.
`expectedPlanFingerprint` and `expectedConfigurationFingerprint` optionally
bind the audit to the approved execution plan and generated job YAML.
`maxEvidenceAgeSeconds` rejects expired evidence and future timestamps; it has
no default because archival retention requirements vary by deployment.
The configuration, assessment and DDL-verification files are also optional.
When supplied, each file must have been recorded in `verifiedArtifacts` during
the original audit and its current SHA-256 must match the saved provenance.
The task is file-only and does not need the migration source or target database.
Generation requires a format 3 Access assessment with no blockers, no unmapped
tables or columns, and no unresolved AutoNumber strategy. The job generator
also recomputes the supplied Schema XML SHA-256 when the assessment contains a
source fingerprint and rejects a different source file. The report target
product and version must match the resolved mapping target, and its migration
method must remain `LOGICAL_MIGRATION`. The generated YAML retains that value
as `schemaFingerprint`, so `executeBulkMigrationJob` repeats the check before
resolving a plan. Existing handwritten bulk YAML remains compatible because
`schemaFingerprint` is optional. The bulk executor accepts a different target
schema or table name through the optional per-task `targetTable` value. Simple
one-to-one column renames are represented by the optional `columnMappings` map.
Column lists in the generated or handwritten configuration, including
`keysetColumns`, `keyColumns`, `updateColumns`, and `verificationColumns`, use
the source Schema names. The resolver applies `columnMappings` to target-side
UPSERT and verification operations, so Access names remain usable throughout
the user-authored configuration. Unknown and duplicate resolved columns are
rejected before execution.
Duplicate-key policies are evaluated against the corresponding source values
before rows are renamed for the target. This applies across chunk boundaries
and to count-based resume history. A resumed JDBC keyset migration still
requires `KEEP_LAST`, because rows preceding its resume token are unavailable
for reconstructing `KEEP_FIRST`, `ERROR`, or custom selection history.
Mapping conversion expressions are not supported by the initial-load executor.
Every mapped table and column must match the supplied Schema XML,
and each table must have a primary key for resumable keyset reads; the
generator also requires every primary-key column to resolve and be `NOT NULL`,
and rejects other mappings explicitly. Type conversion
performed by the target JDBC driver still requires non-production validation.
Configure the Access source and target data sources separately on
`executeBulkMigrationJob`, then pass this file as its `configurationFile`.
Before lifecycle operations or row writes begin, execution checks JDBC metadata
for every resolved target table and mapped column. A missing or ambiguous table,
or a missing column, rejects the job. Resumable key columns must also be
`NOT NULL` and backed by an actual target primary key or unique index. Failures
include the task ID and are written as rejections to the operational report
when reporting is configured.
Generated Access initial-load tasks also set `requireEmptyTarget: true`. The
preflight opens an ordered target read and rejects a table containing any row.
Handwritten generic jobs default this option to `false`, preserving append and
upsert workflows; enable it explicitly for other initial-load jobs.

Set `requireDeploymentReady` to `true` for the common deployment path. It
combines the blocker, incomplete-mapping and unresolved-AutoNumber gates below,
and requires the assessment to have scanned
the Access data, retained its aggregate `dataProfile` and collected its
relationships. The current assessment status must be `REVIEW_REQUIRED`;
`BLOCKED`, missing and unknown statuses are rejected. The mapped assessment
must use `formatVersion=3`; the value is copied to `assessmentFormatVersion` in
the verification evidence. `sourceProduct` must be `access`, and
`targetProduct` must match the mapped and DDL target; both are retained in the
verification evidence. It also requires both
`expectedAssessmentReportFingerprint` and `expectedManifestFingerprint`, so the
verified files are the artifacts that were approved. It defaults to `false` for compatibility and
requires `assessmentReportFile`.
Set `requireDataScan` alone to require `dataScanned=true` without enabling the
other deployment-readiness gates. Successful verification reports retain the
`dataScanned` value and include `DATA_SCAN` in `verificationPolicies` when this
condition was enforced.
Set `requireRelationshipsCollected` alone to require
`relationshipsCollected=true` without the other readiness gates. Successful
verification reports retain this value and record `RELATIONSHIPS_COLLECTED`
when enforced. This ensures orphan-record checks had the source relationship
metadata they need.
Set `requireApprovedFingerprints` alone to require both approved SHA-256 values
without enabling the data and mapping readiness gates. Successful reports add
`APPROVED_FINGERPRINTS` to `verificationPolicies` when this condition is
enforced.
Set `failOnUnresolvedAutoNumberStrategies` to `true` to reject deployment
verification while the assessment inventory still contains mapped Access
AutoNumber columns whose target `identity` choice is `null`. Its default is
`false` for compatibility, and enabling it requires `assessmentReportFile`.
Set `failOnIncompleteMapping` to `true` to reject verification when
`unmappedTables` or `unmappedColumnsInMappedTables` is greater than zero. This
gate also defaults to `false` and requires `assessmentReportFile`, allowing an
explicitly scoped partial migration when it remains disabled.
Set `failOnMappingSemanticDifferences` to `true` to require the
`mappingSemanticDifferences` inventory count to be zero. Leave it disabled when
reviewed Access-to-target changes such as NULL handling, defaults, validation
expressions or calculated fields are intentional; their count remains in the
verification report. `requireDeploymentReady` does not imply this zero-count
gate because reviewed Access semantics such as cascades or `IgnoreNulls` can
remain intentionally different; the approved assessment fingerprint binds
those reviewed findings to the deployment.
Set `failOnAssessmentBlockers` to `true` to reject an assessment whose status is
`BLOCKED`. This covers blockers outside the three focused inventories, including
data findings such as duplicate keys, orphan rows and observed target-capacity
violations. The gate defaults to `false` and requires `assessmentReportFile`.
A source primary key is emitted when all
of its columns are mapped. A foreign key is emitted after table creation when
both tables and every participating column are mapped and its referenced key is
an emitted primary key or safe non-nullable unique constraint. This prevents a
foreign key from referencing a nullable unique key that the preview deliberately
omits. Omitted source primary keys, unique constraints and indexes are listed in
comments at the end of the DDL with their reasons, such as an unmapped participating
column or nullable unique column. Access `IgnoreNulls` indexes and standalone
unique indexes are not silently converted to ordinary target indexes: they are
listed for target-specific null and uniqueness design. `IgnoreNulls` also produces
a `migration.mapping.index-null-handling` review finding, while each standalone
unique index produces `migration.mapping.unique-index-design`. Every omitted
source foreign key is also listed
with its reason, including an unmapped referenced table, inconsistent or
unmapped columns, and a referenced key that was not emitted. Access constraint and
index omissions are repeated as an approval gate before Phase 3, and relationship
omissions are repeated before Phase 4, so the generated scripts do not hide an
incomplete target design at execution time. Access constraint and index names
are preserved when they are nonblank, unique in the target schema,
within the target identifier limit and contain no control characters. Quoting
preserves spaces, Unicode and target delimiter characters. An unusable or
duplicate name falls back to a deterministic ASCII `PK_`, `UK_`, `FK_` or `IX_`
hash within both target limits. Each fallback is listed in comments at the end
of the DDL. Every emitted primary key, unique constraint, index and foreign key is
also listed in a source-to-target name mapping, including Access names retained
without change and deterministic generated names. A separate mapping lists every
mapped Access table and column with its schema-qualified target name. Unmapped
Access tables and columns in mapped tables are listed separately as omitted from
the target DDL. Access table and field descriptions are retained as sanitized
comments in the DDL appendix for migration review. The report inventories their
presence as `accessTablesWithDescriptions` and `accessColumnsWithDescriptions`
without copying the description text. The DDL also lists each mapped column's Access native type (or
the canonical Schema type when native evidence is unavailable), target type and
explicit conversion expression. The same entry compares source and target
nullability, identity generation and default expressions; absent target choices
remain explicit as `unspecified` or `<none>`. Differences in these three column
semantics are collected in a separate review section so relaxed constraints,
new load restrictions and dropped or changed generation behavior are visible.
The JSON and HTML reports expose the same cases as
`migration.mapping.nullability-change`, `migration.mapping.identity-change` and
`migration.mapping.default-change` review findings, with a
`mappingSemanticDifferences` inventory total. When a mapped default differs,
the Phase 2 completion gate requires a target-side insert test of the approved
behavior.
For an AutoNumber whose target identity choice is absent or false, the identity
finding specifically asks the reviewer to check Access's Increment versus Random
setting (or GUID generation), document the selected target strategy and test the
first newly generated row after loading.
The `unresolvedAutoNumberStrategies` inventory count reports mapped Access
AutoNumber columns whose `identity` value is still `null`. An explicit `false`
counts as a reviewed alternative rather than an unresolved choice.
With `scanData=true`, negative values observed in a numeric Access AutoNumber
mapped to target identity produce
`migration.mapping.observed-negative-autonumber`. This is database evidence that
requires checking the Access Random setting and approving any change to sequential
target generation.
Control characters are
escaped in those comments. A non-primary unique constraint is emitted only when every participating
column is mapped and required in both the source Schema and target mapping.
Nullable unique keys remain review items because Access, Oracle and SQL Server
do not share identical NULL uniqueness semantics. Fully mapped non-unique
secondary indexes are emitted after the tables, preserving column order and
descending keys. Their target names use a deterministic ASCII hash so they fit
both Oracle and SQL Server identifier limits without trusting Access names.
Source defaults are never copied automatically; only reviewed target defaults
from the mapping are emitted. Conversion expressions and cascade rules remain excluded, and the
SQL starts with comments stating that scope. The command
never executes this SQL. If mapping validation finds an
invalid target type or another mapping blocker, the JSON/HTML evidence is
published but an existing DDL preview is left unchanged.

The DDL ends with a suggested data load order derived from the fully mapped
foreign keys that it emits. Parent tables precede their children. Self
references do not affect table order. Tables in a foreign-key cycle, along with
tables that depend on that cycle, are listed separately because they require a
staged load, deferred constraints or another reviewed loading strategy.
The same section provides a post-load row-count baseline for every mapped
table. With `scanData=true` it records the Access row count captured in the
profile; otherwise it says `not scanned` instead of implying a zero-row source.
Each baseline is followed by a commented, dialect-quoted `SELECT COUNT(*)`
statement for the target table. Copy or uncomment it only after loading the
data, then compare its result with the recorded source count. These checks do not replace
key, orphan or value-level reconciliation.
When column profiling is available, the DDL also records each mapped source
column's NULL count and a quoted target `SELECT COUNT(*) ... WHERE ... IS NULL`
query. An unscanned column says `not scanned`; a scanned but unsupported column
says `unavailable`. This makes NULL changes caused by conversions, defaults or
load behavior explicit during reconciliation.
For scanned numeric and date/time columns with observed non-NULL values, the
DDL records the source minimum and maximum and provides a quoted target
`SELECT MIN(...), MAX(...)` query. Comparing these extrema can expose range,
sign or date conversion errors even when table and NULL counts match. Text
extrema are excluded because target collation changes their ordering.
For each primary key and emitted non-nullable unique constraint, the DDL adds a
commented `GROUP BY ... HAVING COUNT(*) > 1` query. Each emitted foreign key
gets a commented anti-join count that excludes child rows with any NULL key
component, matching the preflight orphan-check scope. Run these after loading
to detect target duplicates and orphan rows introduced by conversion or load
behavior. Keys omitted from the DDL are omitted from these queries as well.

Only `sourceTable` and `sourceColumn` are required for source identity in the
common case. Matching is case-insensitive. A short table name must resolve to
exactly one local table; add `sourceSchema` and, when needed, `sourceCatalog`
to disambiguate it. `targetTable` and `targetColumn` default to their resolved
source names. `targetType` is required for every mapped column. Target table
and column identities must be unique under case-insensitive comparison.
Target table and column identifiers, and a target schema when supplied, must be
nonblank and cannot contain control characters such as line breaks; invalid identifiers fail before any
existing report or DDL preview is replaced.
Each table must map at least one column. Partial mappings are accepted so teams
can review difficult objects incrementally. The report emits a
`migration.mapping.unmapped-table` review finding for each omitted table and a
`migration.mapping.unmapped-column` finding for each omitted column in a mapped
table. Inventory entries `unmappedTables` and
`unmappedColumnsInMappedTables` provide totals. Columns belonging to an omitted
table are not repeated as individual findings. These findings do not block an
intentional partial migration; record the scope decision during review.
When the generated DDL has any omitted Access table or field, Phase 1 repeats
this requirement and directs the reviewer to approve the appendix scope before
creating target tables.

`nullable` is optional. When it is `false`, `scanData=true` turns observed
source NULLs into blockers; without complete scalar coverage it produces a
review item rather than asserting there are no NULLs. `conversion` records the
chosen conversion intent in JSON and HTML. It is descriptive text: this task
does not execute it or transform values. When at least one conversion is present,
the Phase 2 completion gate requires representative, boundary and NULL values to
be checked against the approved conversion result.

`defaultExpression` is an optional, reviewed target SQL expression. Source
defaults are not copied automatically because Access functions and expression
syntax are not portable. The expression is retained in JSON/HTML and emitted
after the target type in the DDL preview. It must be a single line without a
semicolon or SQL comments, and cannot be combined with `identity`. This lexical
guard prevents a mapping entry from adding another SQL statement; target syntax
and semantics still require review.

`checkExpressions` optionally records reviewed, table-level target SQL predicates.
Each expression is retained in JSON/HTML and emitted as an unnamed `CHECK`
constraint, avoiding non-portable Access constraint names. Access validation
rules are never copied automatically. As with `defaultExpression`, every entry
must be nonblank, single-line, comment-free and contain no semicolon. Column
references use target names and their quoting is the mapping author's
responsibility; the target database must still validate the expression.

Oracle validation accepts explicit `NUMBER`, character types with lengths,
`DATE`, `TIMESTAMP`, LOBs, `RAW`, binary floating types and version-appropriate
`BOOLEAN`. SQL Server validation accepts explicit decimal/numeric, sized
character and binary types, integer/floating types, `date`, `datetime`,
`datetime2`, `bit` and `uniqueidentifier`. Invalid or unsupported target type
spelling is a blocker. With data scanning, the providers also check observed
text length, decimal integer/scale requirements, fractional date precision,
NOT NULL compatibility, and SQL Server `datetime`'s 1753 lower bound. A
`VARCHAR2(... BYTE)` check uses observed UTF-8 bytes only as a reference and
still requires validation against the actual Oracle character set. SQL Server
`varchar` similarly requires validation against the target collation/code page.

Configuration errors, ambiguous or missing names, duplicate targets, fingerprint
mismatches and target mismatches fail before replacing the report. The mapping
file is fingerprinted again after assessment to detect concurrent edits.

See [Access-to-Oracle coverage](access-oracle-migration-assessment.md) and
[Access-to-SQL Server coverage](access-sqlserver-migration-assessment.md) for rules
and limitations. Access files are read-only; rows are read only with `scanData=true`.
Linked sources are never read. Forms, reports, VBA, macros, data reconciliation and migration execution
remain outside this assessment.

## Optional data preflight

Add `scanData = true` to either task configuration above, or call
`command.setScanData(true)` before `run()` in Java. This profiles local tables
without sampling and emits a version 2 report with `dataProfile` and
`dataScanned=true`. The default remains version 1 without `dataProfile`.

The profile contains row and NULL counts; empty-string counts and maximum text
lengths in Unicode code points, UTF-16 units and UTF-8 bytes; finite numeric
minima/maxima and normalized integer-digit/scale requirements; and local date/time
minima/maxima and fractional precision. UTF-8 lengths are reference measurements,
not a claim about the target character set. All-null and empty columns have null
extrema/length maxima, rather than fabricated zero values. Boolean columns have
NULL counts only. Non-finite numbers have a separate count and warning.

Observed NULLs in required columns are blockers. Oracle additionally reports
observed empty short-text values (a blocker for required columns, otherwise a
warning). SQL Server warns about observed dates before 1753 when considering
legacy `datetime`; this does not prohibit `datetime2`.

Binary, OLE, complex and unknown columns are excluded and explicitly marked
`UNSUPPORTED`; their NULL counts are unknown. Links are never followed. Key checks
have their own coverage below. Target sizing/mapping validation and reconciliation
remain manual checks. `dataScanned=true` means profiling, not complete validation.
The file is streamed by row, with no row samples or text contents retained in the
report; numeric/date extrema are actual aggregate values and may be sensitive.
Use a stable copy of the input. Read failures or a changed fingerprint fail without
replacing the previous report. A full scan may take time on large files.

### Duplicate keys and orphan rows

The same `scanData=true` setting also checks declared primary keys, unique keys/
indexes and collected local relationships. The canonical Schema determines the
column order and parent identity; no business keys or undeclared relationships
are inferred. Each key check reads local rows directly, independently of index
contents, and may require another table scan. Parent-key values are held only
for the duration of one relationship check; they are never added to the report.

`dataProfile.integrityChecks` is an additive version 2 field. Each entry includes
the key identity, ordered column names, related table/columns for foreign keys,
coverage and an explanation. Older version 2 reports without this field remain
readable, and the existing Java profile constructor remains available.

| Field | Meaning |
|---|---|
| `kind` | `PRIMARY_KEY`, `UNIQUE_KEY` or `FOREIGN_KEY` |
| `coverage` | `CHECKED`, `UNSUPPORTED` or `LIMIT_EXCEEDED` |
| `checkedRows` | Rows with all key components non-NULL; child rows for foreign keys |
| `nullRows` | Rows with at least one NULL key component, excluded from comparison |
| `violationRows` | Duplicate rows beyond the first occurrence, or child rows without a parent |

Completed checks report zero when no violations were found. Unsupported or
limited checks report **null counts**, with `access.data.integrity-not-checked`
review findings; incomplete results are never presented as zero violations.
An empty check list means no checks were available, not proof of integrity.
If links cause relationship collection to be skipped, orphan checks are also
omitted and `relationshipsCollected=false` remains explicit.

Supported comparisons cover exact numeric types (Byte, Integer, Long, Large
Number, Currency and Decimal), local Date/Time and Date/Time Extended, GUID and
Yes/No. Composite keys and self-references are supported. Numeric scale is
normalized; unsigned Byte values and GUID case are normalized. Parent/child
components must have compatible type families. Text, Single/Double floating
point, binary, complex, unknown or unresolved keys are not checked. Text
collation and floating-point conversion require explicit comparison rules.

At most 100,000 distinct keys are retained per check, with at most 32 components
per key. Exceeding the key count stops that check and reports `LIMIT_EXCEEDED`;
other checks and scalar profiling continue. Wider keys are `UNSUPPORTED`.
Violations already observed before the limit remain blockers, with their counts
explicitly described as lower bounds in findings; complete structured counts stay null.
These bounds are fixed in this release. Large-key checks require a separate
validation process; no temporary file containing keys is created.

Observed duplicates, orphan rows and primary-key NULLs produce source blockers
(`access.data.duplicate-key`, `access.data.orphan-key`,
`access.data.primary-key-null`) for both Oracle and SQL Server. They use the
existing report-before-failure policy. NULL-containing unique/foreign keys are
counted separately; target NULL uniqueness, target collation, conversions and
post-load constraints are still unverified. No data is repaired automatically.

## Java entry point and compatibility

```java
var command = new AssessDatabaseMigrationCommand();
command.setInputFile(new File("input/customer.accdb"));
command.setTargetDatabase("oracle");
command.setTargetVersion("19c");
command.setOutputFile(new File("reports/migration.json"));
command.setHtmlOutputFile(new File("reports/migration.html")); // optional
command.setMappingFile(new File("migration/access-target.yaml")); // optional
command.setMappingTemplateFile(new File("reports/access-target-template.yaml")); // optional
command.run();
var report = command.getReport();
```

Import the command from `com.sqlapp.data.db.command.migration.assessment` and
`java.io.File`. Standalone Java applications must include `sqlapp-core-mdb` and
the selected target module (`sqlapp-core-oracle` or `sqlapp-core-sqlserver`) at runtime.
`sqlapp-command` has no runtime dependency on these dialects. `getReport()` is cleared at the start of each run and becomes
available after publication, including when the blocker gate subsequently fails.

`assessAccessOracleMigration` and `AssessAccessOracleMigrationCommand` remain
compatibility entry points using the same implementation with Oracle preset.
Use the generic entry point to select another target. The existing
`assessMigration` task for Oracle-to-Oracle assessment is unchanged.

## Adding diagnostic support

Common contracts live in `sqlapp-core` under
`com.sqlapp.data.schemas.migration.assessment`:

- `MigrationAssessmentSourceProvider` recognizes a file and returns a
  `MigrationAssessmentSource`: canonical Schemas, collection evidence and
  explicit coverage. Source-specific details belong in Schema specifics when
  needed by target rules. Implementations must not connect to databases or
  follow external links. They must report omitted assets and unscanned data.
  Optional `load(path, true)` returns a shared `MigrationDataProfile`; the default
  implementation rejects scanning so existing providers cannot silently ignore it.
  Existing constructors and metadata-only loading remain available; Schema XML
  is unchanged.
- `DatabaseMigrationAssessmentProvider` declares support for an exact source
  product, target database and target version, then assesses the source without
  modifying it or connecting to external resources. It returns target findings
  only; the command combines them with source evidence. Target product naming
  and optional version normalization are supplied by this provider.

Register implementations in the owning module's `META-INF/services` file named
after the corresponding interface. The MDB provider owns Access inventory;
the Oracle and SQL Server providers own their respective target semantics,
including interpretation of native type IDs retained in `access.sourceType`.
The source and target dialects do not depend on each other.
The command depends only on the shared contracts and handles file validation,
fingerprints, report publication and blocker policy.

Multiple matching providers are an error, not a first-match preference. Add
pair-specific rules and tests before advertising another supported target.
