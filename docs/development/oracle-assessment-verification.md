# Oracle assessment verification

[Development index](README.md) · [Oracle assessment](../gradle-plugin/oracle-migration-assessment.md)

## Oracle 10g source validation runbook

Use a dedicated account with only the catalog and table `SELECT` access needed
for the approved source schemas. Before running, record the expected DB name,
Oracle version, export owner list, maintenance window, and report destination
in the change record. Confirm that the Schema XML was generated from the same
database and has not been edited after review.

First run metadata-only online assessment:

```text
gradlew.bat assessMigration
```

Do not proceed to data scanning until the report satisfies all of these checks:

1. `oracle.source.database-identity` names the approved Data Pump source.
2. `oracle.source.jdbc-driver` records the approved and connection-tested
   driver; any `oracle.source.jdbc-driver-compatibility` warning is resolved.
3. No `oracle.source.version-mismatch` or `oracle.charset.source-mismatch` is
   present.
4. Every intended table is matched; missing or ambiguous table counts are zero.
5. Modeled character columns have no unexplained missing, ambiguous, type,
   semantics, or length mismatch.
6. `NLS_CHARACTERSET`, `NLS_NCHAR_CHARACTERSET`, and
   `NLS_LENGTH_SEMANTICS` have been copied into the change evidence.
7. Any `oracle.charset.indexed-byte-column` findings have named owners for
   target index-key validation.
8. No catalog-visibility warning remains unexplained.

The version and character-set comparisons are independent safeguards. For
example, using a Schema XML that records Oracle 10.2 and `JA16SJIS` against an
Oracle 23 `AL32UTF8` DataSource produces both
`oracle.source.version-mismatch` and `oracle.charset.source-mismatch`. Treat
either finding as evidence that the snapshot or DataSource may represent the
wrong Data Pump source; do not continue based only on a matching owner name.

After the database owner approves the expected full-table read load, run the
aggregate character scan in the agreed window:

```text
gradlew.bat assessMigration -PscanCharacterData=true -PscanQueryTimeoutSeconds=600
```

The scan evidence is complete for the selected bounded BYTE columns only when
`scan candidates = successful scans`, `failed scans = 0`, and there are no
`oracle.charset.data-scan-timeout` or `oracle.charset.data-scan-failed`
findings. Every `oracle.charset.data-overflow` finding requires a reviewed
target-column change or data-cleansing decision followed by another scan.
Indexed BYTE columns still require target index creation rehearsal even when
their individual data scan reports no overflow.

The JSON report records aggregate maximum converted byte lengths and overflow
row counts. It does not record scanned column values or DataSource credentials.
It does contain database identity, schema and object names, version information
and structural findings, so store it as migration evidence with access suitable
for system metadata.

Archive the exact Schema XML and JSON report together. Then perform a Data Pump
export/import rehearsal into an isolated AL32UTF8 target and validate import
logs, invalid objects, row counts, constraints, indexes, application queries,
Access/JDBC/ODBC clients, and representative Japanese data. This assessment is
preflight evidence and does not replace the rehearsal.

## Docker verification scope

The Oracle integration test can be repeated against Oracle Free 23 with:

```text
gradlew.bat :sqlapp-core-dialect-test:dockerTest \
  --tests com.sqlapp.data.db.dialect.test.oracle.OracleMigrationAssessmentDockerTest
```

The test uses a Hikari `DataSource` for a separate account granted only
`CREATE SESSION` and `SELECT` on the table under assessment. It reads the live
NLS and catalog metadata, detects a BYTE-semantics indexed column, scans
Japanese data, and writes the assessment report. It also asks Oracle to convert
`日本語` from `JA16SJIS` to `AL32UTF8` and verifies the expansion from 6 to 9
bytes. The test confirms that the assessment account cannot update the source
table and that its row count and Japanese value remain unchanged after the
assessment. It also verifies that the JSON contains only the expected aggregate
scan evidence and does not contain the Japanese source value, assessment login
name or password. A second login with no table grant verifies that missing
visibility is reported as `oracle.charset.source-table-missing` with zero
matched tables rather than being accepted as complete coverage. A mismatched
Oracle 10.2/`JA16SJIS` snapshot verifies that an Oracle 23/`AL32UTF8` connection
is identified by both version and character-set mismatch findings. The fixture
also verifies Oracle's live `CHAR_USED` distinction for BYTE and CHAR columns
and keeps `NVARCHAR2` outside the database-character-set byte scan. Its
`UPPER(TEXT_VALUE)` function-based index verifies the separate index-expression
review finding.

Oracle Free itself uses `AL32UTF8`. This test therefore verifies the current
Oracle JDBC, SQL and report path, but it does not reproduce an Oracle 10g
database character set or prove compatibility with 10g dictionary views. Run
the metadata-only assessment against the approved 10g source before treating
its character set, length semantics or catalog visibility as known facts.
