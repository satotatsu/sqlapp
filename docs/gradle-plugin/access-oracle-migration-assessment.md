# Access to Oracle migration assessment

[Task guide](README.md) · [Task reference](task-reference.md)

[`assessDatabaseMigration`](database-migration-assessment.md) with
`targetDatabase = 'oracle'` reads local MDB/ACCDB metadata and writes an
atomic UTF-8 JSON preflight report. Use a stable copy of the Access file.
Neither Access nor Oracle needs to be installed. No JDBC/ODBC connection is
opened, linked sources are never followed, and the input is opened read-only.

```groovy
tasks.named('assessDatabaseMigration') {
    inputFile = layout.projectDirectory.file('input/customer.accdb')
    targetDatabase = 'oracle'
    targetVersion = '19c'
    outputFile = layout.buildDirectory.file('reports/access-oracle.json')
    htmlOutputFile = layout.buildDirectory.file('reports/access-oracle.html')
}
```

Run `./gradlew assessDatabaseMigration` (Windows:
`gradlew.bat assessDatabaseMigration`) using Java 21 and a matching sqlapp
plugin version containing this feature. This task needs no DataSource and no
preceding Schema XML export. Add the Oracle module to `runtimeClasspath` as
shown in the [generic task setup](database-migration-assessment.md). The plugin
includes the MDB parser; standalone Java applications need both dialect modules
at runtime. The command does not directly depend on either dialect or add an
Oracle JDBC driver. Existing `assessMigration` Oracle-to-Oracle behavior is unchanged.

| Property | Type | Requirement/default |
|---|---|---|
| `inputFile` | `RegularFileProperty` | Required existing `.mdb` or `.accdb` |
| `targetDatabase` | `Property<String>` | Required: `oracle` |
| `targetVersion` | `Property<String>` | Required: `19c`, `21c`, `23ai` or `26ai` (case-insensitive); no guessed default |
| `outputFile` | `RegularFileProperty` | Required JSON destination, distinct from the input |
| `htmlOutputFile` | `RegularFileProperty` | Optional standalone HTML review report, distinct from input and JSON output |
| `mappingFile` | `RegularFileProperty` | Optional fingerprint-bound target table/column/type mapping YAML |
| `mappingTemplateFile` | `RegularFileProperty` | Optional complete editable mapping skeleton with Oracle type suggestions |
| `ddlOutputFile` | `RegularFileProperty` | Optional review-only Oracle `CREATE TABLE` preview; requires `mappingFile` |
| `failOnBlockers` | `Property<Boolean>` | Defaults to `true`; report is written before failing on blockers |
| `scanData` | `Property<Boolean>` | Defaults to `false`; scans local scalar values when enabled |

The task always reruns its failure policy. `failOnBlockers = false` supports
inventory collection but does not remove blockers from the report. Invalid
configuration, encrypted/unsupported/corrupt files and read/write failures
still fail. A previous report remains unchanged on assessment failure; a
failed invocation must never be treated as a fresh successful report.

## Evidence and coverage

The version 1 report records the SHA-256 source fingerprint, explicit source
and target products, target version, logical migration method, coverage flags,
inventory counts and findings with stable rule IDs and structured object
identities. It reports `BLOCKED` for complex or unknown native types and
`REVIEW_REQUIRED` otherwise; neither status certifies migration readiness.

- Local tables, columns, keys, indexes and available relationships are assessed
  through the shared Schema model. Access native types are retained in the
  assessment snapshot's `access.sourceType` column specifics. Existing exports
  and the normal file loader are unchanged.
- Type advice covers Boolean, integer, currency/decimal, floating point,
  date/time, short/long text, GUID, binary and OLE. Advice is a candidate mapping,
  not executable DDL or an automatic conversion plan. SQL BOOLEAN advice differs
  between 19c/21c and 23ai/26ai; client support still needs verification.
- Findings cover empty-string/NULL semantics, AutoNumber continuity, Access
  expressions, primary-key absence, index semantics, cascading key updates and
  naming/quoting decisions. Reserved words, exact identifier byte limits,
  collisions, target character sets and collation require separate checks.
- All saved query types returned by the Access parser are inventoried,
  including hidden, action and parameterized queries. Each retains its name,
  type and flags in a finding. Query SQL is not parsed, translated or executed.
- Linked tables retain only their local names. If any are present, **all
  relationship collection is skipped** to avoid indirectly opening linked
  databases. `relationshipsCollected=false` and a finding explain this; a zero
  count is not evidence that the source has no relationships.
- By default row data is not scanned (`dataScanned=false`). Empty values, byte lengths,
  numeric/date ranges, duplicates, orphans and post-load reconciliation remain
  unverified. No row values, SQL text, connection strings or linked file paths
  are included in the report.
- Optional `scanData=true` emits a version 2 aggregate profile, required-NULL
  blockers and `access.oracle.observed-empty-string` findings for short text.
  Empty short text in a required column is a blocker; otherwise it is a warning.
  Long-text/LOB handling still needs a mapping decision. See
  [data preflight coverage](database-migration-assessment.md#optional-data-preflight),
  including excluded types and numeric/date extrema retained in the report.
- The same scan checks supported declared keys and local relationships. Source
  duplicate-key, orphan-row and primary-key-NULL blockers apply to Oracle too.
  Text keys and checks exceeding the bounded key budget require separate review;
  see [integrity coverage](database-migration-assessment.md#duplicate-keys-and-orphan-rows).
- Forms, reports, VBA and macros are not inventoried. A permanent coverage
  finding calls for application inventory and frontend/cutover testing.

Attachments and multi-value fields need explicit extraction and child-table
or binary-content mappings. The diagnostic can flag their metadata; it does
not implement extraction. Encryption/password support is not provided.

## Java entry point

```java
var command = new AssessDatabaseMigrationCommand();
command.setInputFile(new File("input/customer.accdb"));
command.setTargetDatabase("oracle");
command.setTargetVersion("19c");
command.setOutputFile(new File("reports/access-oracle.json"));
command.setHtmlOutputFile(new File("reports/access-oracle.html")); // optional
command.setMappingFile(new File("migration/access-oracle.yaml")); // optional
command.setMappingTemplateFile(new File("reports/access-oracle-template.yaml")); // optional
command.run();
var report = command.getReport();
```

Import the command from `com.sqlapp.data.db.command.migration.assessment`.
`getReport()` is reset at the start of each run and is populated only after a
report is written. It remains available if the blocker gate subsequently fails.

The earlier `assessAccessOracleMigration` task and
`AssessAccessOracleMigrationCommand` remain Oracle-preset compatibility aliases
of the same generic implementation.

## Deployment-ready DDL verification

During mapping work, the generated files can first be checked with only their
directory:

```groovy
tasks.named('verifyDatabaseMigrationDdlPhases') {
    directory = layout.buildDirectory.dir('reports/access-oracle-phases')
}
```

This checks the phase files and manifest without requiring fingerprints or an
assessment report. Add the approval settings below only for the deployment
gate.

For a reviewed Access mapping, enable `scanData`, generate the phase directory,
and preserve the assessment JSON used to create it:

```groovy
tasks.named('assessDatabaseMigration') {
    scanData = true
    mappingFile = layout.projectDirectory.file('migration/access-oracle.yaml')
    ddlPhaseOutputDirectory = layout.buildDirectory.dir('reports/access-oracle-phases')
}

tasks.named('verifyDatabaseMigrationDdlPhases') {
    directory = layout.buildDirectory.dir('reports/access-oracle-phases')
    assessmentReportFile = layout.buildDirectory.file('reports/access-oracle.json')
    expectedAssessmentReportFingerprint = 'sha256:<approved-assessment-sha256>'
    expectedManifestFingerprint = 'sha256:<approved-manifest-sha256>'
    requireDeploymentReady = true
    verificationReportFile = layout.buildDirectory.file('reports/access-oracle-ddl-verification.json')
}
```

The readiness gate requires a complete mapping, resolved Access AutoNumber
choices, no blockers, a completed Access data scan, collected relationships
and both approval fingerprints. Reviewed semantic differences remain visible in
the evidence and are bound by the approved assessment fingerprint. It
does not connect to Oracle or execute the generated DDL. Use the
[generic DDL verification reference](database-migration-assessment.md) for the
individual gates when an approved exception is needed.

## Initial Access data load

For a complete one-to-one mapping that uses no conversion expressions, generate
an initial-load job for the existing
bulk migration task:

Mapped target schema and table names are written to the optional `targetTable`
property, and renamed columns to `columnMappings`, in the generated YAML.

```groovy
tasks.named('generateAccessBulkMigrationJobConfiguration') {
    assessmentReportFile = layout.buildDirectory.file('reports/access-oracle.json')
    schemaFile = layout.buildDirectory.file('schema/access.xml')
    outputFile = layout.buildDirectory.file('reports/access-oracle-load.yaml')
}
```

For an approval-controlled deployment, also bind generation to the approved
assessment and deployment-ready DDL verification evidence:

```groovy
tasks.named('generateAccessBulkMigrationJobConfiguration') {
    ddlVerificationReportFile = layout.buildDirectory.file('reports/ddl-verification.json')
    expectedAssessmentReportFingerprint = 'sha256:<approved-assessment-sha256>'
    expectedDdlVerificationReportFingerprint = 'sha256:<approved-ddl-verification-sha256>'
}
```

These fingerprints are optional. Keep the first configuration for the simple
review workflow; use the additional properties when the files are approved
artifacts in CI or deployment automation.

Review the YAML, create the Oracle objects from the approved DDL, then supply
the YAML to `executeBulkMigrationJob.configurationFile` with separately
configured Access source and Oracle target data sources. The generated job uses
resumable `INSERT` chunks, preserves existing Access AutoNumber values even
when future Oracle values use a sequence, and
enables operational and fail-on-mismatch post-load verification JSON reports.
It binds resumable checkpoints to the captured Access source and reviewed
mapping fingerprints. Qualified Access table names are retained in task IDs,
and checkpoint IDs are additionally namespaced by `jobId`. Generation does not
execute the job. The supplied Schema XML is checked against the assessment
source fingerprint when present. Mappings that rename identifiers or use
conversion expressions are rejected because the current declarative bulk
executor cannot apply those transformations safely.

Type references: [Oracle 19c types](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlqr/Data-Types.html),
[Oracle NULL behavior](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/Nulls.html),
[Oracle 23 SQL BOOLEAN](https://docs.oracle.com/en/database/oracle/oracle-database/23/nfcoa/oracle-database-23c-new-features-guide.pdf).
