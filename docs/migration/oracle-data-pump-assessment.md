# Oracle Data Pump and character-set assessment

[Migration workflows](README.md) · [Oracle assessment task](../gradle-plugin/oracle-migration-assessment.md)

## Data Pump migration to AL32UTF8 with an unknown source

For a planned Data Pump migration, configure the method and known target only:

```groovy
tasks.named('assessMigration') {
    migrationMethod = MigrationAssessment.Method.LOGICAL_MIGRATION
    targetCharacterSet = 'AL32UTF8'
}
```

A complete two-phase configuration can keep full data scans disabled until an
approved run. Supply the Oracle JDBC driver version approved for the build:

```groovy
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment

dependencies {
    runtimeOnly 'com.sqlapp:sqlapp-core-oracle:<sqlapp-version>'
    runtimeOnly 'com.oracle.database.jdbc:ojdbc11:<approved-driver-version>'
}

tasks.named('assessMigration') {
    schemaFile = layout.projectDirectory.file('schemas/oracle10g-source.xml')
    outputFile = layout.buildDirectory.file('reports/migration/oracle-al32utf8.json')
    targetVersion = '26ai'
    migrationMethod = MigrationAssessment.Method.LOGICAL_MIGRATION
    targetCharacterSet = 'AL32UTF8'

    dataSource {
        jdbcUrl = providers.environmentVariable('SQLAPP_JDBC_URL')
        username = providers.environmentVariable('SQLAPP_JDBC_USER')
        password = providers.environmentVariable('SQLAPP_JDBC_PASSWORD')
    }

    scanCharacterData = providers.gradleProperty('scanCharacterData')
        .map { it.toBoolean() }.orElse(false)
    scanQueryTimeoutSeconds = providers.gradleProperty('scanQueryTimeoutSeconds')
        .map { it.toInteger() }.orElse(300)
}
```

Run `gradlew.bat assessMigration` first for metadata-only online assessment.
After approving the source load and diagnostic window, collect aggregate scan
evidence with:

```text
gradlew.bat assessMigration -PscanCharacterData=true -PscanQueryTimeoutSeconds=600
```

Keep credentials in environment variables or the existing secret provider
rather than Gradle files or command-line properties.

### Online metadata collection

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

Online assessment reads `NLS_CHARACTERSET`, `NLS_NCHAR_CHARACTERSET`, and
`NLS_LENGTH_SEMANTICS` from `NLS_DATABASE_PARAMETERS`, plus `ALL_TAB_COLUMNS` for the
owners present in the reviewed Schema XML. The report records database-derived
evidence separately from Schema evidence, together with JDBC database product
name and version. The report also records whether online assessment and data
scanning were enabled and, when scanning, the per-statement query timeout.
Credentials, connection strings, and row values are never
written to the report. The connected JDBC product must be Oracle, and every
Schema must have an owner name. Use a source account limited to the required
`SELECT` privileges: JDBC read-only mode is a driver hint and is not a database
authorization boundary.

Online evidence includes `SYS_CONTEXT('USERENV','DB_NAME')` as
`oracle.source.database-identity` so it can be compared with the approved Data
Pump export source. If the value cannot be read, the report retains
`oracle.source.database-identity-unavailable` instead of silently omitting the
identity check.
The JDBC database major version, and the minor version when captured in the
Schema, are compared with the source snapshot. A difference produces
`oracle.source.version-mismatch`, which normally indicates a stale Schema
snapshot or the wrong source DataSource.

The report records `DatabaseMetaData.getDriverName()` and
`getDriverVersion()` as `oracle.source.jdbc-driver`. An Oracle 10g connection
also produces `oracle.source.jdbc-driver-compatibility`, because connectivity
alone does not prove a vendor-certified driver/JDK/database combination. The
repository Docker test uses `ojdbc11:23.26.2.0.0`; Oracle documents current
23/26ai JDBC drivers for supported 19c, 21c and 23/26ai databases, not 10g.
Use the exact driver approved for the legacy environment and retain a successful
connection rehearsal. See the
[Oracle JDBC downloads and supported database versions](https://www.oracle.com/database/technologies/appdev/jdbc-downloads.html).

`NLS_LENGTH_SEMANTICS` is recorded as the database default, but it is never
used to infer an existing column's semantics. Existing columns are assessed
from `ALL_TAB_COLUMNS.CHAR_USED`; reviewed target DDL should state BYTE or CHAR
explicitly.

### Index and column coverage

Online assessment also reads `ALL_IND_COLUMNS`. A BYTE-semantics `CHAR` or
`VARCHAR2` column used by an index produces
`oracle.charset.indexed-byte-column`, because AL32UTF8 expansion can exceed an
index key limit even when the target column definition itself is valid. If
index metadata cannot be read, the report emits
`oracle.charset.index-metadata-unavailable` rather than claiming index coverage.
The assessment also reads `ALL_INDEXES.INDEX_TYPE`. A selected table with a
`FUNCTION-BASED%` index produces `oracle.charset.function-based-index`, because
the internal column exposed by `ALL_IND_COLUMNS` cannot safely attribute the
expression-derived key to one source column. Review its expression through
`ALL_IND_EXPRESSIONS` and rehearse target index creation. Failure to read this
inventory produces `oracle.charset.function-index-metadata-unavailable`.
These views and function-based-index metadata are documented for
[Oracle Database 10g Release 2 ALL_INDEXES](https://docs.oracle.com/cd/B19306_01/server.102/b14237/statviews_1069.htm)
and the
[10g index-management views](https://docs.oracle.com/cd/B19306_01/server.102/b14231/indexes.htm#i1006714),
so this inventory query does not require a post-10g dictionary column.

Each owner also receives an `oracle.charset.online-coverage` finding with the
number of selected, matched and missing tables, modeled and database character
columns, missing or ambiguous columns, scan candidates, successful scans, and
failed scans. A selected table that is not
visible in `ALL_TABLES` produces an `oracle.charset.source-table-missing`
warning. Exact identifier spelling is preferred; otherwise a unique
case-insensitive match is accepted for ordinary unquoted Oracle names. Multiple
case-sensitive matches produce `oracle.charset.source-table-ambiguous` and are
not scanned. Use these results to verify that the report covers the intended
Schema XML scope; a successful command alone does not imply that every data
column was scanned.

A modeled character column that is absent from the connected character-column
metadata produces `oracle.charset.source-column-missing`; this also catches a
column whose live type is no longer a character type. Non-unique
case-insensitive matches produce `oracle.charset.source-column-ambiguous`.
When modeled BYTE/CHAR semantics disagree with live `CHAR_USED`, the report
adds `oracle.charset.source-semantics-mismatch` and includes the mismatch count
in online coverage.
Differences between modeled `length`/`octetLength` and live
`CHAR_LENGTH`/`DATA_LENGTH` produce `oracle.charset.source-length-mismatch`.
Missing modeled values are treated as unknown rather than mismatches.
Differences between database and national character types, or bounded and
LOB/LONG types, produce `oracle.charset.source-character-type-mismatch` because
they change which character set or migration handling applies.

If the Schema XML or captured Catalog settings name a source character set that
differs from the connected database's `NLS_CHARACTERSET`, the report adds an
`oracle.charset.source-mismatch` warning. Treat this as a stale snapshot or a
wrong DataSource until the source identity is verified.

### Optional character-data scans

`scanCharacterData` defaults to `false`. Set it to `true` only for an approved
diagnostic window. It runs one aggregate full-table query for each BYTE-semantics
`CHAR`/`VARCHAR2` column, using Oracle `CONVERT` and `LENGTHB`, and records only
the maximum converted byte length and overflow row count. Quoted identifiers
are preserved. A failed column scan is retained as a warning with Oracle error
code and SQLState; it is never reported as a successful scan. This scan can be
expensive and does not replace invalid-byte checks with Oracle DMU.
Each aggregate statement uses `scanQueryTimeoutSeconds` (300 seconds by
default). A timeout is recorded separately as
`oracle.charset.data-scan-timeout`; increase it only after reviewing source
load and the diagnostic window. Other SQL failures remain
`oracle.charset.data-scan-failed`.

### Metadata findings and limits

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

### Source queries and privileges

When `dataSource` is configured, the assessment executes the following
read-only metadata queries. `:owner` is the exact Oracle schema name.

```sql
SELECT parameter, value
FROM nls_database_parameters
WHERE parameter IN ('NLS_CHARACTERSET', 'NLS_NCHAR_CHARACTERSET',
                    'NLS_LENGTH_SEMANTICS');

SELECT SYS_CONTEXT('USERENV', 'DB_NAME')
FROM dual;

SELECT table_name
FROM all_tables
WHERE owner = :owner;

SELECT table_name, column_name, index_name
FROM all_ind_columns
WHERE table_owner = :owner
ORDER BY table_name, column_name, index_name;

SELECT table_name, index_name
FROM all_indexes
WHERE table_owner = :owner
  AND index_type LIKE 'FUNCTION-BASED%'
ORDER BY table_name, index_name;

SELECT table_name, column_name, data_type, char_used, char_length, data_length
FROM all_tab_columns
WHERE owner = :owner
  AND data_type IN ('CHAR', 'VARCHAR2', 'NCHAR', 'NVARCHAR2', 'CLOB', 'NCLOB', 'LONG')
ORDER BY table_name, column_id;
```

With `scanCharacterData=true`, each selected BYTE-semantics bounded character
column additionally runs an aggregate shaped as follows. Identifiers are quoted
from resolved catalog metadata, and the source character set comes from
`NLS_DATABASE_PARAMETERS`:

```sql
SELECT MAX(LENGTHB(CONVERT("COLUMN", 'AL32UTF8', 'SOURCE_CHARSET'))),
       SUM(CASE
             WHEN LENGTHB(CONVERT("COLUMN", 'AL32UTF8', 'SOURCE_CHARSET'))
                    > :source_data_length
             THEN 1 ELSE 0
           END)
FROM "OWNER"."TABLE";
```

The database account must be able to connect, execute these `SELECT` statements,
and see the reviewed owners' rows through `ALL_TABLES`, `ALL_TAB_COLUMNS`,
`ALL_IND_COLUMNS`, and `ALL_INDEXES`. Missing catalog visibility is reported as
missing scope or unavailable index evidence; grant only the access required by
the approved source schemas. The task issues no DDL or DML.

For a separate assessment account, start with `CREATE SESSION` and object-level
`SELECT` grants for only the tables in scope. Oracle exposes metadata for
accessible objects through the `ALL_*` views, so broad dictionary roles are not
required by the assessment SQL. The Docker integration test verifies this
arrangement on Oracle Free 23. Confirm the same visibility on the Oracle 10g
source because grants, synonyms and local security policy can differ:

```sql
GRANT CREATE SESSION TO migration_assessor;
GRANT SELECT ON app_owner.example_table TO migration_assessor;
```

Run the metadata-only assessment immediately after provisioning the account.
An account with `CREATE SESSION` but without the object grant can still connect
and produce a report, but the report contains `oracle.charset.source-table-missing`
and coverage such as `matched tables=0; missing tables=1`. Treat either result
as an incomplete assessment and correct the object grants before enabling data
scanning.

### Length semantics and report compatibility

`CHAR_USED` identifies B (BYTE) or C (CHAR) where applicable. Changing target
`NLS_LENGTH_SEMANTICS` alone does not override explicitly BYTE-qualified DDL.
See [Oracle length semantics](https://docs.oracle.com/en/database/oracle/oracle-database/26/refrn/NLS_LENGTH_SEMANTICS.html)
and [Unicode migration considerations](https://docs.oracle.com/en/database/oracle/dmu/23.1/dumag/ch1_overview.html).

The aggregate AL32UTF8 scan is limited to bounded `CHAR` and `VARCHAR2`
columns whose live `CHAR_USED` value is `B`. `CHAR_USED=C` columns remain in
the metadata findings but are not compared with their source `DATA_LENGTH` as
an unchanged BYTE declaration. `NCHAR`, `NVARCHAR2`, `NCLOB`, LOB and LONG
columns require their separately reported national-character-set or type-specific
review. The Oracle Docker test exercises BYTE `VARCHAR2`, CHAR `VARCHAR2` and
`NVARCHAR2` together and verifies that only the BYTE column becomes a scan
candidate.

Report format version 1 uses additive fields for `targetCharacterSet`, database
product identity, online/scan flags, scan timeout and structured table identity.
Existing Java constructors and provider entry points remain available. Omitting
the character-set option retains the earlier offline checks. A provider that
does not implement character-set assessment rejects the explicit option rather
than silently ignoring it. Existing Schema XML is unchanged.
