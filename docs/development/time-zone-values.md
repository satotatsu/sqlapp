# Time zone values

[Development index](README.md)

Updated: 2026-11-20.

## Current behavior

`TIME WITH TIME ZONE` and `TIMESTAMP WITH TIME ZONE` values are held as
`java.time.OffsetTime` and `java.time.OffsetDateTime`, matching the default
classes of `DataType.TIME_WITH_TIMEZONE` and `DataType.TIMESTAMP_WITH_TIMEZONE`.

- `JavaTimeJdbcTypeHandler` reads with `ResultSet#getObject(int, Class)` and
  binds the `java.time` value with the JDBC type number. Offsets and fractional
  seconds survive the JDBC round trip.
- There is no per-row fallback. A dialect whose driver cannot read the class
  through `getObject(int, Class)` replaces the handler on its data type
  (DuckDB already uses its own handlers).
- Text output keeps non-zero fractional digits and the offset, for example
  `2026-11-20 12:34:56.1234 +05:30` and `12:34:56.1234 +05:30`. UTC is written
  as `Z`, following the shared offset converter convention. Text output is
  also used for SQL literals.
- A value without zone information, such as `java.sql.Timestamp`,
  `LocalDateTime` or text without an offset, receives the JVM default time
  zone. UTC is not fixed so that the zone can be changed through the JVM
  setting.

Previously the shared handler converted these values to `java.sql.Timestamp`
and `java.sql.Time`, losing the offset (and sub-millisecond digits of `TIME`).
This affected every dialect that used the shared types: Firebird 4.0+,
PostgreSQL, Oracle, SQL Server, H2 and HSQLDB.

## Memo: ZonedDateTime

`OffsetDateTime` keeps the instant and its offset, but not a region. Firebird
4.0+ and Oracle can store a region (`Asia/Tokyo`), which is reduced to an offset
such as `+09:00` when read. This was chosen because JDBC 4.2 standardizes only
`OffsetTime` and `OffsetDateTime` for these types, and driver support for
`ZonedDateTime` varies.

Revisit `ZonedDateTime` when a later JDBC specification or the target drivers
(Jaybird, ojdbc) support it consistently through `getObject(int, Class)` and
`setObject`. A change would need:

- `ZonedDateTime` as the value class for `TIMESTAMP WITH TIME ZONE`, or a
  per-dialect handler for dialects that store regions
- Text formats that write the region (`[Asia/Tokyo]`) in addition to the offset
- A decision for dialects that store only offsets, where a region cannot be
  restored
- Real-engine round-trip tests for region values

## Remaining verification

Real-engine round trips through each driver's `getObject(int, Class)` have not
been run for Firebird, PostgreSQL, Oracle, SQL Server and H2. Whether each
database accepts `Z` in a time zone literal has also not been verified; this
behavior predates the change. The shared tests
cover text conversion, handler calls and an HSQLDB in-memory round trip.
