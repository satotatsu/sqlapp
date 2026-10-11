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

See [Migration assessment mapping](../migration/assessment-mapping.md#target-table-and-column-mapping).

## Generate an initial data-load job

See [Initial Access data load](../migration/access-initial-load.md#generate-an-initial-data-load-job).

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
