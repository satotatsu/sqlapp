# Oracle migration assessment

[Task guide](README.md) · [Task reference](task-reference.md)

`assessMigration` reads an existing sqlapp Schema or Catalog XML snapshot and
writes a UTF-8 JSON preflight report. It opens no database connections and does
not alter the source XML, generate migration SQL, or execute an upgrade.

The initial Oracle provider assesses Oracle 10g, 11g, 12c, 18c, 19c, 21c,
23ai and 26ai snapshots with an explicitly selected **26ai** target. Other
targets are rejected rather than evaluated using guessed compatibility rules.
Existing dialect resolution, metadata readers and SQL generators are unchanged.

## Minimal Gradle configuration

Use Java 21 and matching versions of the sqlapp plugin and Oracle module that
contain this feature. In a build applying `java` and `com.sqlapp.db`:

```groovy
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment

dependencies {
    runtimeOnly 'com.sqlapp:sqlapp-core-oracle:<sqlapp-version>'
}

tasks.named('assessMigration') {
    schemaFile = layout.projectDirectory.file('schemas/oracle-source.xml')
    targetVersion = '26ai'
    migrationMethod = MigrationAssessment.Method.DIRECT_UPGRADE
    outputFile = layout.buildDirectory.file('reports/migration/oracle-assessment.json')
}
```

Run `./gradlew assessMigration` (Windows: `gradlew.bat assessMigration`). The
existing task runtime classloader discovers the Oracle provider from
`runtimeClasspath`; a JDBC driver and credentials are unnecessary for assessment.
See [runtime setup](getting-started.md) for builds without the Java plugin.

An Oracle 10g snapshot with `DIRECT_UPGRADE` produces a `BLOCKED` report and
fails the task **after writing the report**. For a separately planned data/object
copy to a new database, select `MigrationAssessment.Method.LOGICAL_MIGRATION`.
This changes the assessed method; it does not execute or certify that migration.
For inventory collection without a blocking exit, optionally set
`failOnBlockers = false`. The report still retains `BLOCKED` and every finding.

| Property | Gradle type | Requirement/default |
|---|---|---|
| `schemaFile` | `RegularFileProperty` | Required existing Schema/Catalog XML; input path is not significant |
| `targetVersion` | `Property<String>` | Required; initial provider accepts `26ai` case-insensitively |
| `targetCharacterSet` | `Property<String>` | Optional, no default; `AL32UTF8` enables column character-set checks (case-insensitive); other values fail |
| `migrationMethod` | `Property<MigrationAssessment.Method>` | Required: `DIRECT_UPGRADE` or `LOGICAL_MIGRATION`; no inferred method |
| `outputFile` | `RegularFileProperty` | Required JSON destination, distinct from input; parent directories are created |
| `failOnBlockers` | `Property<Boolean>` | Defaults to `true`; warnings/reviews do not fail the task |

The task always reruns so an existing report cannot bypass the failure policy.
The XML must carry the Oracle product name and supported major version, either
on each standalone Schema or inherited from its Catalog. Unknown/missing
metadata, empty catalogs, mixed products and unsupported targets fail clearly.
Prefer metadata-only exports: row data is not assessed and unnecessarily
increases memory consumption. No default Oracle version is guessed.

## Reading the report

Format version 1 includes the input file's SHA-256 fingerprint, target version,
selected method, resolved source catalog/schema identities and version fields,
an inventory by schema/object collection, and findings with stable rule IDs.
Each finding includes severity, evidence category, structured object identity
where applicable, reason, suggested action and an optional reference URL.
Raw SQL, DB-link connection strings and credentials are not copied to the report.

* `BLOCKER` / `DOCUMENTED_RULE`: a known incompatibility of the selected method.
  Direct upgrades from releases earlier than 19c to 26ai are rejected according
  to the [Oracle upgrade guide](https://docs.oracle.com/en/database/oracle/oracle-database/26/upgrd/oracle-database-releases-that-support-direct-upgrade.html)
  (checked 2026-09-25). The rule does not block logical migrations.
* `WARNING` / `SCHEMA`: a schema-level object is marked invalid, or an SQL-backed
  view/materialized view/procedure/function/package/package body/trigger has
  neither nonblank `definition` nor `statement` text in the snapshot. Either
  representation is accepted as captured text; executability is not certified.
* `REVIEW` / `MANUAL_CHECK`: target compilation and dependency checks, DB-link
  authentication, synonym resolution, sequence next values, materialized-view
  refresh, and other object-specific checks remain unexecuted.

Environment reviews always cover extraction completeness, character sets/NLS,
JDBC/ODBC and Access client compatibility, authentication, grants/roles, jobs,
external resources, business SQL/performance regression and cutover rehearsal.
They are a checklist of missing evidence, not a live environment comparison.
19c/21c direct-upgrade candidates and 23ai/26ai release-update cases also retain
manual review of exact patches, platform and Oracle prerequisites.

Overall status is `BLOCKED` when blockers exist and `REVIEW_REQUIRED` otherwise.
There is deliberately no success/readiness certification. A zero inventory
count does not establish absence from the real database; exports may be filtered
or lack privileges. Schema child collections are inventoried; nested package
members, table columns/indexes, catalog-wide grants and scheduler catalogs are
not independently diagnosed by this initial provider. SQL is not parsed for
deprecated syntax. The report does not replace Oracle AutoUpgrade checks,
target compilation, data verification, client tests or cutover rehearsals.

The source fingerprint identifies input bytes, not report authenticity. Reports
are atomically replaced where supported. Preserve the reviewed snapshot and
report together; changes to the input during assessment cause failure.

## Java entry point and extension boundary

```java
var command = new AssessMigrationCommand();
command.setSchemaFile(new File("schemas/oracle-source.xml"));
command.setTargetVersion("26ai");
command.setMigrationMethod(MigrationAssessment.Method.LOGICAL_MIGRATION);
command.setOutputFile(new File("reports/oracle-assessment.json"));
command.run();
var report = command.getReport();
```

Imports are `com.sqlapp.data.db.command.migration.assessment.AssessMigrationCommand`,
`com.sqlapp.data.schemas.migration.assessment.MigrationAssessment` and `java.io.File`.
Keep `sqlapp-core-oracle` on the runtime classpath. `getReport()` also retains
findings after a blocker-policy exception.

Common immutable findings and the provider SPI live in `sqlapp-core`. Oracle
rules live in `sqlapp-core-oracle`; XML/file handling and reporting live in
`sqlapp-command`. Gradle only maps properties. This adds no production module
dependency and makes no changes to existing Schema serialization. A test-only
Oracle runtime dependency exercises provider discovery in the Gradle plugin.

The separate `sqlapp-gradle-example` repository is outside this change. The
minimal task configuration above is the proposed addition to its Oracle
workflow; no files in that repository were changed.
