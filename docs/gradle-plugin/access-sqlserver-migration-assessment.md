# Access to SQL Server migration assessment

[Generic task and setup](database-migration-assessment.md) · [Task reference](task-reference.md)

Use `assessDatabaseMigration` with `targetDatabase = 'sqlserver'` and an explicit
`targetVersion` of `2016`, `2017`, `2019` or `2022`. Add a matching
`sqlapp-core-sqlserver` module to the task runtime classpath. The generic task's
properties, JSON format and blocker policy are unchanged. No new dedicated task
or database connection is required.

```groovy
dependencies {
    runtimeOnly 'com.sqlapp:sqlapp-core-sqlserver:<sqlapp-version>'
}

tasks.named('assessDatabaseMigration') {
    inputFile = layout.projectDirectory.file('input/customer.accdb')
    targetDatabase = 'sqlserver'
    targetVersion = '2022'
    outputFile = layout.buildDirectory.file('reports/access-sqlserver.json')
}
```

Run `gradlew.bat assessDatabaseMigration` on Windows, using Java 21 and a build
applying `java` and `com.sqlapp.db`. For standalone Java, use
`AssessDatabaseMigrationCommand` with the same properties and include both MDB
and SQL Server modules at runtime. The SQL Server module's existing JDBC driver
dependency is unchanged; the assessment does not use it to connect.

## Diagnostic coverage

The provider consumes the shared Schema and Access native type IDs; it does not
modify the Schema or generate conversion SQL. All type suggestions are review
items, not proof that a chosen target mapping preserves every source value.

| Source type | Candidate and review |
|---|---|
| Yes/No | `bit`; explicit truth-value conversion and client behavior |
| Byte / Integer / Long / Large Number | `tinyint` / `smallint` / `int` / `bigint`; range and referencing-key checks |
| Currency / Decimal | `decimal(19,4)` / `decimal(p,s)`; exactness and totals |
| Single / Double | `real` / `float(53)`; comparison tolerances |
| Date/Time / Date/Time Extended | `datetime2(p)`; fractional precision and client compatibility |
| Short Text / Long Text | `nvarchar(n)` / `nvarchar(max)`; Unicode sizing, formatting and collation |
| GUID | `uniqueidentifier`; stable key representation |
| Binary / OLE | `varbinary(n)` or `varbinary(max)`; payload sizing and OLE container handling |

Rule IDs begin with `access.sqlserver.`:

- `type`, `datetime`, `text-semantics`: candidate conversions and unverified
  value semantics. Text review preserves the distinction between NULL and empty
  strings rather than applying Oracle's empty-string rule. `nvarchar` sizing
  must account for UTF-16 byte-pairs. Date advice favors checking `datetime2`
  before choosing legacy `datetime`; timezone assumptions are not inferred.
- `autonumber`: numeric keys need an explicit identity/loading/reseeding plan,
  accounting for random-number source behavior. GUID AutoNumber receives GUID
  generation advice instead of a numeric `IDENTITY_INSERT` recipe.
- `key`, `index-nulls`, `index-size`: missing stable keys, nullable uniqueness,
  IgnoreNulls and target key-width checks. Filtered indexes need an explicit
  design, especially for composite keys; the report does not assert duplicates
  or an actual key-width overflow without data and a resolved target mapping.
- `foreign-key`, `cascade`: mapped type/size and parent-key checks plus cascade
  graph review. SQL Server supports cascading updates/deletes; cycles, multiple
  paths and trigger interactions need verification.
- `check-expression`, `column-expression`: Access expressions require T-SQL or
  application-rule translation and regression tests.
- `identifier`, `identifier-length`: naming/escaping review and a blocker for
  names exceeding 128 characters. Reserved words, name collisions and collation
  remain environment review items, not exhaustively validated here.
- `complex-type`: attachments/multi-value columns block scalar migration until
  an explicit extraction mapping exists. Missing or unknown native types also
  block under `type`.
- `environment`: target release, edition, compatibility level, database/schema
  mapping, collation, client support, permissions and cutover remain unverified.

The report also retains the source provider's findings for linked tables,
saved queries and omitted application assets. Source values, SQL bodies and
linked connection details are not included. Links are not opened. If linked
tables exist, relationship collection is skipped and reported explicitly.
Rows, forms, reports, VBA and macros are not inspected; migration and
post-load reconciliation are not executed.

The supported target versions describe diagnostic coverage, not Microsoft
support lifecycle or an upgrade recommendation. The existing DialectResolver,
metadata readers and SQL factories are unchanged. Other versions (including
2025) and Azure SQL require explicit additional coverage and are rejected.

References checked for these rules:
[Access/SQL Server types](https://support.microsoft.com/en-us/access/comparing-access-and-sql-server-data-types),
[Access incompatibilities](https://learn.microsoft.com/en-us/sql/ssma/access/incompatible-access-features-accesstosql),
[datetime2](https://learn.microsoft.com/en-us/sql/t-sql/data-types/datetime2-transact-sql),
[Unicode text](https://learn.microsoft.com/en-us/sql/t-sql/data-types/nchar-and-nvarchar-transact-sql),
[key constraints](https://learn.microsoft.com/en-us/sql/relational-databases/tables/primary-and-foreign-key-constraints),
[capacity limits](https://learn.microsoft.com/en-us/sql/sql-server/maximum-capacity-specifications-for-sql-server).
