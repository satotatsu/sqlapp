# MariaDB enhancement status

## Implemented local coverage

- 10.3–11.4 sequence discovery and storage settings: one catalog query plus
  ceil(sequence count / 100) UNION ALL queries. No NEXT VALUE/NEXTVAL calls.
  Missing rows fail; discovery/read races and privilege errors propagate.
- 11.5+ INFORMATION_SCHEMA.SEQUENCES type mapping and exact numeric conversion.
- CREATE SEQUENCE existence options, signed/unsigned integer types and the
  11.5 AS syntax boundary.
- 10.2.7+ invisible column locale handling, generated expressions and defaults.
- 10.2.7+ CHECK joins include table identity; table discovery excludes views and
  sequences while retaining system-versioned tables.
- Temporal CREATE syntax and rejection of missing/repeated period boundaries.
- Inherited index metadata audit: ignored/enabled state, same-named indexes on
  different tables, compound key order and prefix lengths survive XML/DDL.
  Multiple indexes use one metadata query. Tests cover absent IGNORED on 10.5,
  explicit NO on 10.6, and ignored descending compound indexes on 10.8.
  No production change was needed for these cases.

These changes are confined to sqlapp-core-mariadb. Public signatures, dependency
versions and resolver choices are unchanged. Regression coverage uses mocked
JDBC metadata, rendered catalog SQL and Schema XML/DDL generation.

## Remaining limitations

- No live MariaDB/container tests were run. Batch storage reads, privileges,
  generated DDL and supported version boundaries require live verification.
- Sequence engine/comment options and runtime allocation state are not read.
  The 11.5+ catalog path does not expose cache size; it is not inferred.
  Pre-11.5 cache values outside Integer range cannot fit the shared Schema model
  and fail exact conversion. This is a definition reader, not a runtime sequence
  position backup/restore facility.
- Application-time period metadata is not discovered. Older temporal metadata
  and all inherited MySQL reader paths have not been qualified end to end.
- Vendor routine/view recreation and uncommon table options
  still need a dedicated metadata audit; existing SQL-generation tests alone
  do not establish lossless metadata round trips.

MariaDB is not yet marked fully complete. The remaining work includes both
local metadata audits and real-server qualification.

## References

- [Sequence storage layout](https://mariadb.com/docs/server/reference/sql-statements/administrative-sql-statements/show/show-create-sequence)
- [Sequence syntax and version boundaries](https://mariadb.com/docs/server/reference/sql-structure/sequences/create-sequence)
