# Database migration assessment

[Task guide](README.md) · [Task reference](task-reference.md)

`assessDatabaseMigration` is the common entry point for offline migration
diagnosis. Specify a source file and an explicit target database and version.
The source inventory and the target compatibility rules are discovered through
ServiceLoader from the runtime classpath. No DDL/DML is generated or executed.

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
Access AutoNumber metadata is retained as `identity: true`. Oracle renders this
as `GENERATED BY DEFAULT AS IDENTITY`; SQL Server renders `IDENTITY(1,1)`.
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
    scanData = true
}
```

`ddlOutputFile` optionally writes an atomic, review-only `CREATE TABLE` preview
from a valid `mappingFile`. Oracle identifiers are double-quoted and SQL Server
identifiers are bracket-quoted. The first comments record the source file
fingerprint, mapping file fingerprint, normalized target database and target
version. Reviewers can compare these values with the JSON report and reject a
DDL file produced from stale or different inputs. The preview contains mapped tables, columns,
target types and explicit nullability. A source primary key is emitted when all
of its columns are mapped. A foreign key is emitted after table creation when
both tables and every participating column are mapped. Access constraint and
index names are preserved when they are nonblank, unique in the target schema,
within the target identifier limit and contain no control characters. Quoting
preserves spaces, Unicode and target delimiter characters. An unusable or
duplicate name falls back to a deterministic ASCII `PK_`, `UK_`, `FK_` or `IX_`
hash within both target limits. Each fallback is listed in comments at the end
of the DDL as an Access-name-to-target-name mapping. Control characters are
escaped in those comments. A non-primary unique constraint is emitted only when every participating
column is mapped and required in both the source Schema and target mapping.
Nullable unique keys remain review items because Access, Oracle and SQL Server
do not share identical NULL uniqueness semantics. Fully mapped non-unique
secondary indexes are emitted after the tables, preserving column order and
descending keys. Their target names use a deterministic ASCII hash so they fit
both Oracle and SQL Server identifier limits without trusting Access names.
Source defaults, conversion expressions and cascade rules remain excluded, and the
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

`nullable` is optional. When it is `false`, `scanData=true` turns observed
source NULLs into blockers; without complete scalar coverage it produces a
review item rather than asserting there are no NULLs. `conversion` records the
chosen conversion intent in JSON and HTML. It is descriptive text: this task
does not execute it or transform values.

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
