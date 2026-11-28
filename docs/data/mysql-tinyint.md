# MySQL-family TINYINT(1) values

Use `mysql.tinyInt1Mapping=NUMERIC` on a source connection when TINYINT(1) columns
must retain numeric values such as 2, -1 or 127. The same option is supported by
MySQL, MariaDB and Aurora MySQL dialects. The default remains `BOOLEAN` for
compatibility with existing applications and Schema representations.

```groovy
dataSource {
    dialectOptions.put('mysql.tinyInt1Mapping', 'NUMERIC')
}
```

In a data source YAML file, the existing `dialect` section is used:

```yaml
dialect:
  mysql:
    tinyInt1Mapping: NUMERIC
```

For Java callers or disconnected SQL generation:

```java
var dialect = DialectResolver.getInstance().getDialect(
    "mysql", 8, 4, 0, Map.of("mysql.tinyInt1Mapping", "NUMERIC"));
// Existing schemas can select the same dialect variant:
var configured = DialectResolver.getInstance().applyDialectOptions(
    dialect, Map.of("mysql.tinyInt1Mapping", "BOOLEAN"));
```

| Setting | Physical signed TINYINT(1) metadata | Java value |
|---|---|---|
| `BOOLEAN` (default) | `DataType.BOOLEAN` | `Boolean` |
| `NUMERIC` | `DataType.TINYINT` | `Byte`, preserving -128 through 127 |

TINYINT UNSIGNED stays `DataType.UTINYINT` (`Short`, 0 through 255). Its JDBC
reading, and reading of numeric TINYINT, now bypass the driver's Boolean
conversion in either mode. This also fixes MariaDB Connector/J returning Boolean
for an unsigned column even though the Schema model is already numeric.
NULL is preserved through `wasNull`. Unexpected out-of-range numeric results
raise SQLDataException rather than narrowing/truncating them.

The typed numeric reads are independent of Connector/J's `tinyInt1isBit` and
`transformedBitIsBoolean` settings. Setting `tinyInt1isBit=false` alone is not a
replacement for NUMERIC metadata mode: a legacy BOOLEAN column can still convert
Integer 2 to null before it reaches Row storage. With `tinyInt1isBit=true`, a
legacy Boolean read may instead reduce 2 to true and write it back as 1.

Apply the option **before reading source metadata and rows**. It cannot recover
values already converted in a Row, CSV or Schema XML. An existing Schema XML
column explicitly modeled as BOOLEAN remains Boolean: regenerate its metadata or
apply an intentional column override before reading values. Connection-free
`applyDialectOptions` chooses the correct variant; it does not reinterpret stored
Schema column types or undo earlier conversion.

MySQL/MariaDB BOOLEAN and BOOL are physical aliases for TINYINT(1). NUMERIC mode
therefore also reads such database columns as numeric; their original declaration
cannot reliably distinguish application intent. Explicit `DataType.BOOLEAN` and
`BOOLEAN`/`BOOL` SQL generation remain available. No shared BooleanConverter,
XML/configuration Boolean parsing, or default Boolean-conversion policy changes
are included. Strict Boolean validation and any future default change are separate
compatibility decisions.

Configured variants retain the same database product, concrete version and
next-version chain. Their type collections and equality identities are separated
from the default variants, so selecting a mode for one source does not mutate
another source's default. Unknown options, invalid values and duplicate option
names differing only by case fail explicitly.

## Verification

Regression tests were added first: the unmodified code failed five tests for
unsupported numeric mode and lossy numeric handlers. Added checks also cover
NULL, signed/unsigned bounds, column labels/indices, numeric range failures,
case-insensitive configuration, invalid options, product/version preservation,
next-version chains, and independent equality/configuration identities.

Java 25, repository Gradle wrapper, offline:

```
.\gradlew.bat :sqlapp-core-mysql:test --tests '*MySqlTinyIntMappingTest' :sqlapp-core-mariadb:test --tests '*MariadbTinyIntMappingTest' :sqlapp-core-aurora-mysql:test --tests '*AuroraMySqlTinyIntMappingTest' --offline --console=plain
.\gradlew.bat :sqlapp-core-mysql:test :sqlapp-core-mariadb:test :sqlapp-core-aurora-mysql:test --offline --console=plain
```

The three dialect-module suites passed: 536 successful tests, eight existing
DB-only geometry tests skipped (404 MySQL / 114 MariaDB / 26 Aurora total cases).
Shared resolver/converter and command configuration/SQL-option checks added
31 successful tests: 567 successes in the final related scope, no failures.
The follow-up commands were:

```
.\gradlew.bat :sqlapp-core:test --tests '*DialectResolverTest' --tests '*ConverterRegressionTest' :sqlapp-command:test --tests '*GenerateSqlDialectOptionsTest' --tests '*DataSourceConfigFilesTest' --offline --console=plain
```

Live probes use disposable local MySQL 5.7.44/8.0.46/8.4.11 and MariaDB
10.5.29/11.4.12/11.8.8, Connector/J 9.7.0 / MariaDB Connector/J 3.5.9. Four
combinations of the two driver flags passed per target: 32 profiles across eight
server/dialect targets. All disposable containers were removed. Explicit Aurora
MySQL 8.0/8.4 dialects run against upstream MySQL 8.0/8.4; no real AWS Aurora
cluster is available. Older supported releases, MySQL 9 and MariaDB 12 have
resolver/unit coverage but no available local DB images.

The live path is metadata reader -> ResultSetRowIteratorHandler -> Row -> normal
JDBC INSERT and LOAD DATA -> SQL numeric re-read. It includes BOOLEAN aliases,
TINYINT(4), signed -128/-1/0/1/2/127, unsigned 0/1/2/127/255 and NULL. All
source/target values are compared with SQL null-safe equality.

Probe source, runner and per-version logs:
`C:/Users/satot/.codex/visualizations/2026/11/17/01a1e752-547f-7360-b6ee-49e6314aacb1/mysql-tinyint-fix/`.
No external/production database or persistent volume is used.
