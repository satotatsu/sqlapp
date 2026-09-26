# Database migration assessment

[Task guide](README.md) · [Task reference](task-reference.md)

`assessDatabaseMigration` is the common entry point for offline migration
diagnosis. Specify a source file and an explicit target database and version.
The source inventory and the target compatibility rules are discovered through
ServiceLoader from the runtime classpath. No DDL/DML is generated or executed.

## Access to Oracle

The initial supported combination is Access MDB/ACCDB to Oracle 19c, 21c, 23ai
or 26ai. A dialect's ability to generate SQL does **not** establish migration
assessment support. Other sources, targets and versions fail explicitly until
a matching assessment provider is installed.

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
}
```

Use matching sqlapp versions. Run `./gradlew assessDatabaseMigration`, or
`gradlew.bat assessDatabaseMigration` on Windows. The plugin already includes
the MDB module for Access file export. The target Oracle module must be added
to the runtime classpath; no Oracle JDBC driver or database installation is
required. See [runtime setup](getting-started.md) for builds without `java`.

| Property | Type | Requirement/default |
|---|---|---|
| `inputFile` | `RegularFileProperty` | Required existing source file supported by exactly one inventory provider |
| `targetDatabase` | `Property<String>` | Required; initially `oracle` (case-insensitive) |
| `targetVersion` | `Property<String>` | Required; Oracle accepts `19c`, `21c`, `23ai`, `26ai` (case-insensitive) |
| `outputFile` | `RegularFileProperty` | Required JSON path distinct from the input |
| `failOnBlockers` | `Property<Boolean>` | Defaults to `true`; blockers fail the task after publishing the report |

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

See [Access-to-Oracle coverage](access-oracle-migration-assessment.md) for rules
and limitations. Access files are read-only; rows and linked sources are not
read. Forms, reports, VBA, macros, data reconciliation and migration execution
remain outside this assessment.

## Java entry point and compatibility

```java
var command = new AssessDatabaseMigrationCommand();
command.setInputFile(new File("input/customer.accdb"));
command.setTargetDatabase("oracle");
command.setTargetVersion("19c");
command.setOutputFile(new File("reports/migration.json"));
command.run();
var report = command.getReport();
```

Import the command from `com.sqlapp.data.db.command.migration.assessment` and
`java.io.File`. Standalone Java applications must include `sqlapp-core-mdb` and
`sqlapp-core-oracle` at runtime. `sqlapp-command` has no runtime dependency on
either dialect. `getReport()` is cleared at the start of each run and becomes
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
- `DatabaseMigrationAssessmentProvider` declares support for an exact source
  product, target database and target version, then assesses the source without
  modifying it or connecting to external resources. It returns target findings
  only; the command combines them with source evidence. Target product naming
  and optional version normalization are supplied by this provider.

Register implementations in the owning module's `META-INF/services` file named
after the corresponding interface. The MDB provider owns Access inventory;
the Oracle provider owns Access-to-Oracle semantics, including the native type
IDs retained in `access.sourceType`. Neither dialect depends on the other.
The command depends only on the shared contracts and handles file validation,
fingerprints, report publication and blocker policy.

Multiple matching providers are an error, not a first-match preference. Add
pair-specific rules and tests before advertising another supported target.
