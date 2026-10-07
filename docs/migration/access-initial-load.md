# Initial Access data load

[Documentation index](../README.md) · [Migration workflows](README.md)

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

See [Access-to-Oracle coverage](../gradle-plugin/access-oracle-migration-assessment.md) and
[Access-to-SQL Server coverage](../gradle-plugin/access-sqlserver-migration-assessment.md) for rules
and limitations. Access files are read-only; rows are read only with `scanData=true`.
Linked sources are never read. Forms, reports, VBA, macros, data reconciliation and migration execution
remain outside this assessment.

