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

- TIMESTAMPVERSION has no shared TIMESTAMP surrogate for other databases. Adding
  one changes cross-database type/default/update/time-zone semantics and needs a
  separate migration-policy decision. This patch preserves the type ID and does
  not claim to repair every cross-database migration path.
- MariaDB 5.x uses the older base dialect path and does not receive fractional
  TIMESTAMP registration here. Supporting its earlier 5.3 feature boundary needs
  a separate version-specific dialect/resolver change; no 5.x local image is
  available. Plain native TIMESTAMP still benefits from explicit NULL/ON UPDATE.
- Real AWS Aurora, MySQL 5.6, MySQL 9 and MariaDB 12 DB executions are unavailable;
  their inheritance/version boundaries have unit coverage where supported.
- Cross-session time-zone differences and DST behavior were not live-tested.
  UTC roundtrips prove precision preservation, not a universal time-zone policy.

## Primary references

- https://dev.mysql.com/doc/refman/8.4/en/fractional-seconds.html
- https://downloads.mysql.com/docs/refman-5.6-en.pdf (fractional support from 5.6.4)
- https://dev.mysql.com/doc/refman/8.4/en/datetime.html
- https://mariadb.com/docs/server/reference/data-types/date-and-time-data-types/timestamp
