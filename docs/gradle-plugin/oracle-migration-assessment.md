# Oracle migration assessment

[Task guide](README.md) · [Task reference](task-reference.md)

`assessMigration` reads an existing sqlapp Schema or Catalog XML snapshot and
writes a UTF-8 JSON preflight report. It is offline by default; an optional
DataSource adds read-only source checks. It does not alter the source XML,
generate migration SQL, or execute an upgrade.

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
    targetCharacterSet = 'AL32UTF8'
    outputFile = layout.buildDirectory.file('reports/migration/oracle-assessment.json')
}
```

Run `./gradlew assessMigration` (Windows: `gradlew.bat assessMigration`). The
existing task runtime classloader discovers the Oracle provider from
`runtimeClasspath`. A JDBC driver and credentials are unnecessary for offline
assessment and required when `dataSource` is configured.
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
| `dataSource` | `DataSourceExtension` | Optional; when configured, reads the source Oracle database with SELECT statements |
| `scanCharacterData` | `Property<Boolean>` | Defaults to `false`; enables aggregate full scans of BYTE `CHAR`/`VARCHAR2` columns and requires `dataSource` |

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
members, table indexes, catalog-wide grants and scheduler catalogs are
not independently diagnosed by this initial provider. SQL is not parsed for
deprecated syntax. The report does not replace Oracle AutoUpgrade checks,
target compilation, data verification, client tests or cutover rehearsals.

The source fingerprint identifies input bytes, not report authenticity. Reports
are atomically replaced where supported. Preserve the reviewed snapshot and
report together; changes to the input during assessment cause failure.

## Data Pump migration to AL32UTF8 with an unknown source

For a planned Data Pump migration, configure the method and known target only:

```groovy
tasks.named('assessMigration') {
    migrationMethod = MigrationAssessment.Method.LOGICAL_MIGRATION
    targetCharacterSet = 'AL32UTF8'
}
```

To read the missing facts from the source Oracle database, add the normal
`dataSource` configuration. Its presence enables online assessment; omitting it
keeps the task fully offline. The connection is marked read-only before queries.

```groovy
tasks.named('assessMigration') {
    dataSource {
        jdbcUrl = providers.environmentVariable('SQLAPP_JDBC_URL')
        username = providers.environmentVariable('SQLAPP_JDBC_USER')
        password = providers.environmentVariable('SQLAPP_JDBC_PASSWORD')
    }
}
```

Online assessment reads `NLS_DATABASE_PARAMETERS` and `ALL_TAB_COLUMNS` for the
owners present in the reviewed Schema XML. The report records database-derived
evidence separately from Schema evidence, together with JDBC database product
name and version. Credentials, connection strings, and row values are never
written to the report. The connected JDBC product must be Oracle, and every
Schema must have an owner name. Use a source account limited to the required
`SELECT` privileges: JDBC read-only mode is a driver hint and is not a database
authorization boundary.

Each owner also receives an `oracle.charset.online-coverage` finding with the
number of selected tables, character columns, scan candidates, successful
scans, and failed scans. Use it to verify that the report covers the intended
Schema XML scope; a successful command alone does not imply that every data
column was scanned.

`scanCharacterData` defaults to `false`. Set it to `true` only for an approved
diagnostic window. It runs one aggregate full-table query for each BYTE-semantics
`CHAR`/`VARCHAR2` column, using Oracle `CONVERT` and `LENGTHB`, and records only
the maximum converted byte length and overflow row count. Quoted identifiers
are preserved. A failed column scan is retained as a warning with Oracle error
code and SQLState; it is never reported as a successful scan. This scan can be
expensive and does not replace invalid-byte checks with Oracle DMU.

The source character set and length semantics are not supplied speculatively.
Suspected `JA16SJIS` is not recorded as fact. The source character set is read
from the Schema model, including inheritance, or a captured Catalog
`NLS_CHARACTERSET` setting. Conflicting declarations fail clearly.

| Metadata evidence | Finding |
|---|---|
| Source character set missing | `oracle.charset.source-unknown`: capture it; no encoding is guessed |
| CHAR/VARCHAR2 semantics missing | `oracle.charset.semantics-unknown`: inspect `CHAR_USED`; equal character and byte lengths do not establish semantics |
| BYTE semantics and source is not AL32UTF8 or is unknown | `oracle.charset.byte-expansion`: potential overflow if byte declarations remain unchanged; no measured overflow is claimed |
| CHAR semantics | `oracle.charset.char-byte-limit`: verify target type byte limits and `MAX_STRING_SIZE`; CHAR is not proof of safety |
| BYTE semantics with source already AL32UTF8 | `oracle.charset.same-encoding`: no encoding expansion is expected for valid data, but target DDL and values still need validation |
| NCHAR/NVARCHAR2/NCLOB | `oracle.charset.national-character-set`: assess `NLS_NCHAR_CHARACTERSET` separately |
| CLOB/LONG and other character types | `oracle.charset.large-character-data`: review conversion and LOB handling separately |

Column findings retain catalog, schema, table, and column as separate identity
fields. Model semantics may be inherited from parent metadata; verify them
against the actual column `CHAR_USED` and import DDL before remediation. An
inherited NLS default is not proof of how an existing column was created.
Oracle `UTF8` is not treated as an alias for `AL32UTF8`.

These are metadata checks. They do not scan rows or original bytes, detect
replacement characters, calculate a fixed expansion multiplier, or report an
overflow count or required width. The source `octetLength` is not assumed to be
the destination limit. Index key lengths, byte-oriented SQL/PLSQL, client
buffers, and invalid source bytes remain explicit validation items. No DDL or
Schema data is transformed.

When `dataSource` is configured, the assessment executes the following
read-only metadata queries. `:owner` is the exact Oracle schema name.

```sql
SELECT parameter, value
FROM nls_database_parameters
WHERE parameter IN ('NLS_CHARACTERSET', 'NLS_NCHAR_CHARACTERSET');

SELECT owner, table_name, column_name, data_type,
       char_used, char_length, data_length
FROM all_tab_columns
WHERE owner = :owner
  AND data_type IN ('CHAR', 'VARCHAR2', 'NCHAR', 'NVARCHAR2', 'CLOB', 'NCLOB', 'LONG')
ORDER BY table_name, column_id;
```

`CHAR_USED` identifies B (BYTE) or C (CHAR) where applicable. Changing target
`NLS_LENGTH_SEMANTICS` alone does not override explicitly BYTE-qualified DDL.
See [Oracle length semantics](https://docs.oracle.com/en/database/oracle/oracle-database/26/refrn/NLS_LENGTH_SEMANTICS.html)
and [Unicode migration considerations](https://docs.oracle.com/en/database/oracle/dmu/23.1/dumag/ch1_overview.html).

Report format version 1 gains optional `targetCharacterSet` and object identity
`table` fields. Existing Java constructors and the three-argument provider entry
point remain available. Omitting the option retains the earlier checks. A
provider that does not implement character-set assessment rejects the explicit
option rather than silently ignoring it. Existing Schema XML is unchanged.

## Java entry point and extension boundary

```java
var command = new AssessMigrationCommand();
command.setSchemaFile(new File("schemas/oracle-source.xml"));
command.setTargetVersion("26ai");
command.setMigrationMethod(MigrationAssessment.Method.LOGICAL_MIGRATION);
command.setTargetCharacterSet("AL32UTF8");
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
