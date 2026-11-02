# MariaDB enhancement status

The local enhancement pass includes period-key enrichment in the 11.4 unique
constraint reader, so direct constraint retrieval retains the same period
references as table loading. Enrichment precedes optional object filtering;
the shared TableObjectReader default of accepting all constraints is unchanged.
Unit regressions and the six scoped live-server cases now pass on the executed
matrix below. This is not a claim of full MariaDB feature coverage.

## Implemented local coverage

- 10.3–11.4 sequence discovery and storage settings: one catalog query plus
  ceil(sequence count / 100) UNION ALL queries. No NEXT VALUE/NEXTVAL calls.
  Missing rows fail; discovery/read races and privilege errors propagate.
  Batch tests cover 1, 99, 100, 101, 200, 201 and 205 objects. Invalid/duplicate
  batch row IDs, missing rows, negative cache sizes, integer overflow and
  fractional numeric metadata fail rather than yielding partial definitions.
- 11.5+ INFORMATION_SCHEMA.SEQUENCES type mapping and exact numeric conversion.
- 11.5+ cache sizes are read from sequence storage in UNION ALL batches of 100.
  Query count is one catalog query plus ceil(sequence count / 100), including
  the unsigned-type path. Invalid/duplicate IDs, missing rows/cache values,
  negative values, fractional values and Integer overflow fail explicitly.
- Sequence ENGINE and COMMENT are read from TABLES during legacy discovery or
  through a qualified TABLES join on 11.5+. Query counts are unchanged; both
  options survive XML and CREATE SEQUENCE. Invalid ENGINE tokens fail explicitly.
- CREATE SEQUENCE existence options, signed/unsigned integer types and the
  11.5 AS syntax boundary.
- CREATE SEQUENCE preserves explicit cache disabling through NOCACHE; zero
  size disables caching, unspecified enabled caching retains server defaults,
  and negative/conflicting sizes fail. XML/SQL tests cover 10.3, 11.4, 11.5, 12.1.
- 10.2.7+ invisible column locale handling, generated expressions and defaults.
- 10.2.7+ CHECK joins include table identity; table discovery excludes views and
  sequences while retaining system-versioned tables.
- Temporal CREATE syntax and rejection of missing/repeated period boundaries.
- System-period column roles are appended consistently by CREATE and ALTER
  column-definition builder entry points, once per boundary column. XML-based
  regression coverage includes 10.3, 10.11, 11.4, 11.5 and 12.1.
- CREATE_OPTIONS handling preserves quoted strings while dropping standalone
  partitioned catalog flags. Regression cases include doubled/escaped quotes,
  double-quoted strings and literal replacement characters.
- Resolver selection for 11.0–11.3 uses the compatible 10.5 dialect/readers;
  11.4-only catalogs/columns are not selected prematurely. Sequence storage
  metadata and INSERT RETURNING capabilities remain available on early 11.x.
- 11.4+ application-time periods are fetched from INFORMATION_SCHEMA.PERIODS
  in one query for the requested table set, matched by catalog/schema/table.
  Empty selections issue no period query. Period names and boundary columns
  survive Schema XML and CREATE TABLE generation.
  Blank period identities and identical boundary column names are rejected.
- 11.4+ KEY_PERIOD_USAGE is fetched in one query and attached to the resolved
  unique/primary constraint as mariadb.withoutOverlapsPeriod. Unresolved keys
  fail. XML retains the marker; 11.4+ modeled CREATE/ALTER replaces the trailing
  full start/end columns with the period WITHOUT OVERLAPS key part. Primary and
  unique keys reuse inherited key validation. The source model is not mutated.
  Unsupported versions, unresolved periods and unsupported boundary key shapes
  fail explicitly. A complete CREATE TABLE definition remains an alternative.
  Enrichment takes place inside the unique-constraint reader before optional
  filter extensions, rather than after table attachment. Direct constraint
  retrieval retains period metadata; constraintName filters apply to the
  additional query. Empty constraint sets skip that query. The default shared
  table-object predicate policy remains unchanged.
- Inherited index metadata audit: ignored/enabled state, same-named indexes on
  different tables, compound key order and prefix lengths survive XML/DDL.
  Multiple indexes use one metadata query. Tests cover absent IGNORED on 10.5,
  explicit NO on 10.6, and ignored descending compound indexes on 10.8.
  No production change was needed for these cases.

- View metadata/XML/DDL coverage on 11.5 includes MERGE, DEFINER, INVOKER,
  CASCADED CHECK OPTION, query text and output names. Routine coverage includes
  body, security, decimal return and INOUT ENUM arguments containing commas.
- Routine CREATE on the 10.3+ registry rejects missing/blank body and missing
  function return type with actionable errors. Complete CREATE definitions are
  authoritative after XML and do not duplicate supplemental bodies/arguments.
  Version tests cover 10.3, 10.11, 11.3, 11.4, 11.5 and 12.1. Pre-10.3 factory
  selection is unchanged.
- Whitespace-only full routine definitions and function return definitions
  cannot bypass completeness validation. Full executable definitions continue
  to take precedence over optional supplemental components.

Live qualification on local Docker 10.5, 11.4 and 11.8 is complete for the six
integration cases. All applicable cases pass; see INTEGRATION_TESTS.md for counts.
It exposed and fixed uninitialized batch SqlNode parameters, generated-column
catalog NULL defaults, reversed physical period bounds, and a shared Sequence
UBIGINT limit calculation using a 32-bit bound instead of the signed 64-bit bound.

Changes are in sqlapp-core-mariadb plus shared sqlapp-core fixes for XML value
elements and the unsigned 64-bit sequence bound (2^64 - 1). Public signatures, dependency
versions are unchanged. Resolver selection changes only for 11.0–11.3 as above.
Regression coverage uses mocked
JDBC metadata, rendered catalog SQL and Schema XML/DDL generation.

## Remaining limitations

Final regression commands and results:

- `gradlew.bat :sqlapp-core:test :sqlapp-core-mysql:test :sqlapp-core-mariadb:test`:
  1261 core, 306 MySQL and 96 MariaDB tests; zero failures/skips.
- `gradlew.bat :sqlapp-command:test --tests '*TransferSchemaDifferenceTest'`:
  16 passed, zero failures/skips; embedded HSQL only.
- `scripts/Test-MariaDbDocker.ps1 -AllowSchemaCreate`: applicable live cases pass
  on 10.5/11.4/11.8; see INTEGRATION_TESTS.md for the per-version counts.
  Created containers and anonymous volumes were removed; final container
  inventory has no sqlapp-mariadb-it-* containers.

- Only local Docker 10.5, 11.4 and 11.8 with disposable root credentials were
  qualified. Other versions, restricted privileges, concurrency and failure
  recovery still require qualification.
- Sequence runtime allocation state and table options beyond engine/comment are not read.
  Cache values outside Integer range cannot fit the shared Schema model
  and fail exact conversion. This is a definition reader, not a runtime sequence
  position backup/restore facility.
- Application-time periods before 11.4 are not discovered. WITHOUT OVERLAPS
  generation is scoped to 11.4+ with a resolved application period and scalar
  key columns followed by both full ascending period bounds in either physical
  order. Older temporal metadata
  and all inherited MySQL reader paths have not been qualified end to end.
- Routine/view variants beyond the six live cases, Oracle-mode/package routines
  and uncommon table options remain unqualified. Successful scenarios do not
  establish every metadata round trip.

## Follow-up scope

An opt-in `mariadbIntegrationTest` task and sequence, period-table and routine
metadata/XML/replay scenarios are prepared. The task requires explicit schema-create authorization and target
environment variables; ordinary test excludes its tag. Task dry-run and source
compilation pass. See [INTEGRATION_TESTS.md](INTEGRATION_TESTS.md) for exact scope,
including required sequence storage/routine privileges and the period scenario's
11.4+ boundary. Six cases include WITHOUT OVERLAPS behavior, view CHECK OPTION
and unsigned sequence ranges and have passed on the applicable authorized local
Docker versions. A repeatable isolated runner is provided in scripts/Test-MariaDbDocker.ps1.

The remaining items above are explicit coverage limits and follow-up work;
they do not form part of the completed local pass. Full MariaDB completion
still requires resolving any additional required coverage limits.

A useful live test matrix is 10.3/10.11 (storage sequences), 11.3 (no 11.4
catalogs), 11.4 (periods and keys), 11.5 (typed sequences), and 12.1 (same-named
constraints). Use a disposable database with explicitly authorized credentials
and object cleanup. The executed matrix is 10.5/11.4/11.8. Verify catalog query
counts and replay generated DDL on each applicable version.

## References

- [Sequence storage layout](https://mariadb.com/docs/server/reference/sql-statements/administrative-sql-statements/show/show-create-sequence)
- [Sequence syntax and version boundaries](https://mariadb.com/docs/server/reference/sql-structure/sequences/create-sequence)
- [PERIODS catalog (11.4+)](https://mariadb.com/docs/server/reference/system-tables/information-schema/information-schema-tables/information-schema-periods-table)
