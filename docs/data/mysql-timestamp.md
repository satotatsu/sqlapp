# MySQL-family TIMESTAMP precision

MySQL stores DATETIME and native TIMESTAMP with different time-zone behavior.
sqlapp uses DataType.TIMESTAMP for DATETIME and DataType.TIMESTAMPVERSION for
native MySQL TIMESTAMP. The latter is a MySQL-specific timestamp identity; its
name does not mean that every such column is an automatic row-version column.
DEFAULT and ON UPDATE remain separate column properties.

## Fixes

For MySQL 5.6.4 and later, TIMESTAMP(0) through TIMESTAMP(6) now resolve as
TIMESTAMPVERSION with fractional precision in Column.length. They previously
fell back to OTHER. Typed timestamp conversion, JDBC reads/binds and generated
TIMESTAMP(p) DDL now retain that precision. DATETIME remains a different type.
The same registration is inherited by MariaDB 10.x/11.x/12.x and Aurora MySQL
8.0/8.4 dialects. MySQL older than 5.6.4 keeps its nonfractional registration.

A bare modern TIMESTAMP is represented with precision 0 (previously null) and
is emitted as TIMESTAMP(0). Its native type name and TIMESTAMPVERSION type ID
are retained. Existing models with no precision still generate precision 0 in
modern dialects. Explicit unsupported declarations such as TIMESTAMP(7) are not
matched as a supported native type. Generic precision narrowing policies for
manually authored models / other database types are unchanged.

Nullable native TIMESTAMP columns now emit an explicit NULL qualifier. Omitting
it can cause older MySQL servers to apply implicit NOT NULL/default rules and
reject recreated tables with invalid zero defaults. The real MySQL 5.7 probe
reproduced that failure before the fix. This applies to both CREATE and ALTER
column definitions through the common builder hook.

Native TIMESTAMP ON UPDATE is emitted by the base MySQL builder. This fixes the
MariaDB builder inheritance path that read CURRENT_TIMESTAMP(6) correctly but
silently dropped it from regenerated DDL. MySQL 5.6.5+'s existing general datetime
ON UPDATE implementation is preserved. This patch does not add DATETIME ON UPDATE
support to older/shared builder paths.

## Implementation / compatibility

The MySQL-specific precision type reuses TimestampType / PrecisionProperties and
keeps the logical type TIMESTAMPVERSION. TimestampVersionType, the enum, shared
Boolean conversion, TINYINT options and database type surrogates are unchanged.
DbDataTypeCollection.register is widened from protected to public so dialects can
register specialized types **during initialization, before resolving types**.
Its registration logic is unchanged; this is not a runtime cache-refresh API.
No new module dependencies or vendor SQL in core are introduced.

The old no-size TIMESTAMPVERSION class and registration remain in use for older
MySQL dialects. The precision-aware subtype has the same JDBC Timestamp value
class and native TIMESTAMP type name. Model length / generated SQL intentionally
change for modern TIMESTAMP precision. No shared config or Schema XML format
changes are introduced.

## Verification

Regression tests cover precision 0..6 across MySQL 5.6.4/5.6.5/5.7/8.0/8.4/9,
older MySQL fallback, invalid native declarations, microseconds in conversion,
independence from NUMERIC TINYINT mode, nullable and NOT NULL definitions,
older native TIMESTAMP ON UPDATE and MariaDB/Aurora inherited behavior.
The old MySql565 assertions for absent precision were updated to explicit 0.
The fractional-type tests failed with OTHER against the unmodified implementation.
Nullable DDL and MariaDB ON UPDATE failures were then separately reproduced with
regression tests before their fixes.

Executed with Java 25 and the offline repository wrapper:

```
.\gradlew.bat :sqlapp-core-mysql:test --tests '*MySqlTimestampPrecisionTest' --tests '*MySql565Test' :sqlapp-core-mariadb:test --tests '*MariadbTimestampPrecisionTest' :sqlapp-core-aurora-mysql:test --tests '*AuroraMySqlTimestampPrecisionTest' --offline --console=plain
.\gradlew.bat :sqlapp-core:test :sqlapp-core-mysql:test :sqlapp-core-mariadb:test :sqlapp-core-aurora-mysql:test --offline --console=plain
```

Core's complete suite passed after the registration visibility change. The three
dialect-module suites were rerun after the later SQL-builder fixes. Detailed
counts: core 1747 passed; MySQL 406 passed / 4 skipped, MariaDB 112 passed /
4 skipped, Aurora 27 passed. Total 2292 passed, eight existing DB-only geometry
tests skipped, no failures. Logs accompany the local probe artifacts.

Disposable DB probes: MySQL 5.7.44/8.0.46/8.4.11 and MariaDB
10.5.29/11.4.12/11.8.8; Connector/J 9.7.0 and MariaDB Connector/J 3.5.9.
Explicit Aurora 8.0/8.4 dialects are tested on upstream MySQL 8.0/8.4.
The probe reads metadata, regenerates and executes CREATE TABLE, re-reads native
precision/ON UPDATE, reads rows through ResultSetRowIteratorHandler, then checks
normal JDBC and LOAD DATA copies with SQL null-safe equality. Values include
precision 0/3/6, .123456 microseconds, DATETIME(6), NULL, CURRENT_TIMESTAMP(6)
and ON UPDATE CURRENT_TIMESTAMP(6). JVM and DB session are explicitly UTC.

Probe sources, runner and per-version logs:
`C:/Users/satot/.codex/visualizations/2026/11/17/01a1e752-547f-7360-b6ee-49e6314aacb1/mysql-timestamp-fix/`.
No external/production database or persistent volume is used.

## Remaining work

- TIMESTAMPVERSION has no implicit shared TIMESTAMP surrogate. Use the explicit
  column migration API below for supported target dialects.
- MariaDB 5.x uses the older base dialect path and does not receive fractional
  TIMESTAMP registration here. Supporting its earlier 5.3 feature boundary needs
  a separate version-specific dialect/resolver change; no 5.x local image is
  available. Plain native TIMESTAMP still benefits from explicit NULL/ON UPDATE.
- Real AWS Aurora, MySQL 5.6, MySQL 9 and MariaDB 12 DB executions are unavailable;
  their inheritance/version boundaries have unit coverage where supported.
- Cross-session time-zone differences and DST transitions still need live tests.

## JVM and session time-zone mismatch

With an Asia/Tokyo JVM, a UTC database session and MySQL Connector/J 9.7.0
configured with `connectionTimeZone=UTC&preserveInstants=true`, a copy within
the same session reproduced two differences on MySQL 5.7, 8.0 and 8.4.
Both paths retained microseconds.

| Path | Native TIMESTAMP(6) | DATETIME(6) |
|---|---|---|
| JDBC row read and bind | Preserved | Shifted by −9 hours |
| LOAD DATA LOCAL | Shifted by +9 hours | Preserved |

With MariaDB Connector/J 3.5.9 on MariaDB 10.5, 11.4 and 11.8, the same
probe observed no source-to-target difference through JDBC and a +9 hour
difference for both types through LOAD DATA. These comparisons use the same
driver and session for source and target reads.

JDBC applies time-zone rules when binding a Java Timestamp. LOAD DATA instead
receives the text produced by the bulk encoder and interprets it in the
destination session. See the
[Connector/J time instant reference](https://dev.mysql.com/doc/connector-j/en/connector-j-time-instants.html).
The earlier UTC-JVM/UTC-session probe verified both paths for the tested
values. A general fix must preserve the instant for native TIMESTAMP and the
wall-clock fields for DATETIME. This requires defining the representation and
target-session time-zone handling across both paths.

For MySQL and Aurora MySQL, the JDBC DATETIME binder now sends
`java.sql.Timestamp` values as wall-clock text, including the fractional
seconds. The MySQL-family reader now requests `LocalDateTime` from the
driver and converts it to the existing JDBC Timestamp row representation.
MariaDB uses the same reader and binder. This preserves local fields through
JDBC and through the bulk encoder. If a DATETIME falls in a JVM DST gap and
the conversion would normalize it to another time, the reader raises SQL
state 22007.

### LOAD DATA and bulk UPSERT

When the destination session reports a numeric offset such as `+00:00`,
the native loader formats `java.sql.Timestamp` values in native TIMESTAMP
columns using that offset. This preserves the instant represented by the
Java value, including its fractional seconds. The loader reads the offset
once per load when a writable native TIMESTAMP column is present.
Values still represented as text when the stream reads them keep their
previous encoding. The regular `Row.put` API can convert input text to
`Timestamp` before loading, in which case the resulting instant is used.
MySQL 8.0.19+ and the supported Aurora dialects send explicit `+00:00`
values regardless of the destination session setting. Bulk UPSERT also
projects the staging TIMESTAMP as explicit UTC text when assigning it to the
target, preserving distinct instants even when their local fields coincide.
Earlier MySQL and MariaDB use the numeric-offset path for explicit offsets
and the fixed UTC names `UTC` and `Etc/UTC`.
The DATETIME reader correction also resolves the MariaDB bulk discrepancy
in the tested UTC sessions.

The local UTC-session probe verified direct loads and both matched and
unmatched Bulk UPSERT rows on MySQL 5.7/8.0/8.4, MariaDB 10.5/11.4/11.8,
and the Aurora dialects on upstream MySQL 8.0/8.4. The UPSERT checks cover
the ordinary TIMESTAMP and DATETIME columns, including NULL and fractions.

On MySQL 8.4 with `America/New_York`, two instants one hour apart both
displayed as 01:30:00.123456 during the fall transition. Inserting their
local text stored the earlier instant twice. The explicit UTC path was
tested for direct load and UPSERT under `America/New_York` and `SYSTEM`
on MySQL 8.0/8.4 and the Aurora dialects on those servers. For earlier MySQL
and MariaDB, use an explicit numeric session offset or one of the fixed UTC
names for instant-preserving bulk loads. MySQL 5.7 and MariaDB 10.5/11.4/11.8
issued warning 1265 and stored the same value for inputs differing only in
their offset suffix. A named zone with DST or a session reporting `SYSTEM`
still needs an explicit session setting before native timestamp loads.

For those sessions, save `@@session.time_zone` on the destination connection
and run `SET SESSION time_zone = '+00:00'` before loading. Keep UTC through the
entire Bulk UPSERT, including the transfer from staging to the target. Restore
the saved setting on the same connection after the operation, including on
failure. If restoration fails, discard the connection instead of returning it
to a pool. This procedure assumes the input JDBC Timestamp values already
represent the correct instants.

Session time-zone management is the caller's responsibility. The loader does
not change the session setting or reject a load based on that setting.

For the verified workaround, run the JVM in UTC, set both source and
destination sessions to UTC, and make the JDBC driver interpret those
sessions as UTC. This is the configuration used by the original round-trip
probe. MySQL-family bulk UPSERT also uses LOAD DATA for its staging table, so
the same precautions apply to that path.

An automatic switch to JDBC batch would change the execution contract.
The current JDBC batch executor rejects the native bulk transaction option
`useTransaction=true`, and bulk UPSERT calls the native provider directly
for staging. A fallback would need to preserve transaction ownership and
cover staging as well as direct inserts.

The preferred native design is to distinguish instants from local date-time
values at the read boundary and encode each separately. An instant needs a
destination representation that remains unique during a daylight-saving
overlap. Formatting epoch seconds with `FROM_UNIXTIME` in an arbitrary
session zone does not provide that guarantee. See the
[MySQL date and time function reference](https://dev.mysql.com/doc/refman/8.4/en/date-and-time-functions.html).
Compatibility tests should cover legacy text inputs, fractional seconds,
nulls, different source and destination sessions, DST gaps and overlaps,
and both direct and staged loads.

## Primary references

- https://dev.mysql.com/doc/refman/8.4/en/fractional-seconds.html
- https://downloads.mysql.com/docs/refman-5.6-en.pdf (fractional support from 5.6.4)
- https://dev.mysql.com/doc/refman/8.4/en/datetime.html
- https://mariadb.com/docs/server/reference/data-types/date-and-time-data-types/timestamp

## Explicit migration to another dialect

```java
var result = ColumnMigration.convert(mysqlTimestampColumn, postgresDialect);
Column targetColumn = result.requireComplete();
```

`ColumnMigration` is in `com.sqlapp.data.db.dialect`. It asks the target dialect
for a proposal and leaves the source column intact. PostgreSQL maps native
MySQL-family `TIMESTAMP` columns to `TIMESTAMPTZ` with the source precision.
It maps a current-time default to `statement_timestamp()`, which uses
[statement start time](https://www.postgresql.org/docs/current/functions-datetime.html#FUNCTIONS-DATETIME-CURRENT).

Review `result.issues()` for defaults that require a source time zone or
manual translation, and for `ON UPDATE`, which requires a trigger or
application logic. `requireComplete()` rejects incomplete proposals.
`result.candidate()` is available for inspection and manual completion.
Targets without a rule return an issue. This API converts column definitions.
The caller adds the resulting column to the target model and handles row data,
triggers, and dependent constraints separately.

| Target dialect | Proposed type | Current-time default |
|---|---|---|
| PostgreSQL | TIMESTAMPTZ(p) | `statement_timestamp()` |
| Oracle | TIMESTAMP(p) WITH TIME ZONE | `SYSTIMESTAMP` |
| SQL Server 2008 and later | DATETIMEOFFSET(p) | `SYSDATETIMEOFFSET()` |
| H2, HSQLDB 2.x, Vertica | TIMESTAMP(p) WITH TIME ZONE | Review required |
| Firebird 4 and later | TIMESTAMP WITH TIME ZONE, fixed 4 fractional digits | Review required |
| DuckDB | TIMESTAMPTZ, fixed 6 fractional digits | Review required |

If a fixed target precision differs from the source, the API reports the
difference. It omits a candidate when the target cannot store the source
precision. Dialects without an approved native mapping, including SQLite,
report that no rule is available. No conversion falls back to a zone-less
timestamp. The current-time functions selected for Oracle and SQL Server
use the target server clock. Their evaluation behavior follows the target
engine. Automatic update expressions still require explicit migration
decisions in each target.

The storage limits come from the
[Firebird language reference](https://firebirdsql.org/file/documentation/html/en/refdocs/fblangref40/firebird-40-language-reference.html)
and the [DuckDB timestamp reference](https://duckdb.org/docs/current/sql/data_types/timestamp.html).

The cross-dialect regression test checks generated types, default translation,
and unsupported version and precision boundaries. It also executes the
converted types on embedded HSQLDB and DuckDB, checking that different
offsets for the same instant compare equal with six fractional digits.
