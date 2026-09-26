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
| `failOnBlockers` | `Property<Boolean>` | Defaults to `true`; report is written before failing on blockers |

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
- Row data is not scanned (`dataScanned=false`). Empty values, byte lengths,
  numeric/date ranges, duplicates, orphans and post-load reconciliation remain
  unverified. No row values, SQL text, connection strings or linked file paths
  are included in the report.
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
command.run();
var report = command.getReport();
```

Import the command from `com.sqlapp.data.db.command.migration.assessment`.
`getReport()` is reset at the start of each run and is populated only after a
report is written. It remains available if the blocker gate subsequently fails.

The earlier `assessAccessOracleMigration` task and
`AssessAccessOracleMigrationCommand` remain Oracle-preset compatibility aliases
of the same generic implementation.

Type references: [Oracle 19c types](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlqr/Data-Types.html),
[Oracle NULL behavior](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/Nulls.html),
[Oracle 23 SQL BOOLEAN](https://docs.oracle.com/en/database/oracle/oracle-database/23/nfcoa/oracle-database-23c-new-features-guide.pdf).
