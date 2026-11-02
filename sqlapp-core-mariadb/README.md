# MariaDB dialect enhancement

Real-server tests are available through the explicit `mariadbIntegrationTest`
task and excluded from ordinary `test`. See [INTEGRATION_TESTS.md](INTEGRATION_TESTS.md)
for the required target authorization, environment variables and disposable-schema
lifecycle. The six real-server cases have been run on isolated local Docker
MariaDB 10.5, 11.4 and 11.8. Version-applicable cases all pass; containers and
anonymous storage were removed. Scenarios cover sequence settings, application periods with generated/
invisible columns and ignored indexes, and ordinary procedure/function replay.

The local pass includes period-key enrichment within the 11.4+ unique-constraint
reader, supporting direct reader use and optional filter extensions without
changing the shared default predicate policy. See
[ENHANCEMENT_STATUS.md](ENHANCEMENT_STATUS.md) for exact status and coverage limits.

MariaDB is the next target after the Phoenix local enhancement pass. Existing
SQL generators and MySQL inheritance are retained; this pass starts by checking
metadata preservation and recreation rather than treating shared MySQL coverage
as proof of MariaDB behavior.

## Initial fixes

- MariaDB 11.5+ sequence metadata now maps DATA_TYPE to the shared DataType as
  well as retaining its native name. The type survives Schema XML and CREATE.
- Sequence numeric metadata uses exact integer conversion. Unexpected
  fractional values fail rather than being silently truncated.
- CREATE SEQUENCE honors the existing createIfNotExists option instead of
  always emitting IF NOT EXISTS.
- CREATE SEQUENCE emits NOCACHE for cache=false or cacheSize=0. An unspecified
  enabled cache keeps server defaults; negative sizes and cache=false with a
  positive size fail explicitly. XML and version tests cover 10.3 through 12.1.
- CREATE_OPTIONS strips only standalone, unquoted partitioned catalog flags;
  quoted option strings, embedded commas/quotes and replacement characters
  retain their original text through XML and CREATE TABLE.
- CREATE SEQUENCE emits AS integer type only on 11.5+. On 10.3–11.4,
  explicit signed BIGINT uses the server's default type without AS; other
  explicit types fail with a version-specific error. Non-integer sequence types
  fail rather than producing invalid SQL. Catalog precision is not emitted as
  an integer display width, and unsigned types retain their signedness.
- The 10.2.7+ column reader uses locale-independent INVISIBLE detection.
  Regression tests cover Turkish locale, virtual/stored generated expressions,
  XML preservation and native default expressions.
- Temporal CREATE TABLE clauses distinguish the SYSTEM_TIME keyword from
  quoted application-period identifiers. Missing or repeated period boundary
  columns fail explicitly instead of silently omitting a requested period.
  These generator changes apply to the MariaDB registry used from 10.3 onward;
  callers remain responsible for choosing a server version supporting the
  requested temporal feature. XML and SQL regression tests use 11.5.
  CREATE and ALTER column-definition builder entry points both retain ROW
  START/ROW END roles after XML; ordinary columns receive no period role.
- MariaDB 11.4+ application-time periods use one INFORMATION_SCHEMA.PERIODS
  query and exact catalog/schema/table matching. Period names and start/end
  columns survive XML and CREATE TABLE. Pre-11.4 application periods remain
  outside this implementation. KEY_PERIOD_USAGE is fetched in one additional
  bulk query, and its period reference survives XML in constraint specifics
  (mariadb.withoutOverlapsPeriod). On 11.4+, modeled CREATE/ALTER replaces the
  trailing full start/end columns with the period WITHOUT OVERLAPS key part,
  preserving primary/unique semantics without modifying the source model.
  Unresolved periods, unsupported key shapes and older dialects fail explicitly.
  Ordinary unique constraints retain the inherited behavior.
- The 10.2.7+ CHECK metadata query joins by schema, constraint name and table
  name, preventing expressions from other tables with the same constraint name
  from being attached to the requested table. Retrieval remains one bulk query.
  Mocked reader/XML/DDL regression coverage includes 10.2.7, 10.5, 11.5 and 12.1.

Dialect changes stay in sqlapp-core-mariadb; a shared sqlapp-core XML value-element
fix preserves commas inside ENUM/SET values after serialization. Existing public signatures and
Schema XML formats, and add no dependencies. Resolver selection for 11.0–11.3
now uses the compatible 10.5 dialect/10.2.7 metadata readers rather than the 11.4
readers, avoiding unsupported PERIODS/KEY_PERIOD_USAGE and system-time columns.
Other selections are unchanged. INFORMATION_SCHEMA.SEQUENCES remains confined to 11.5+.
MariaDB 10.3–11.4 uses a separate reader: one filtered TABLES discovery followed
by UNION ALL batches of at most 100 sequence storage tables. It preserves start,
increment, bounds, cache size and cycle without advancing the sequence. Missing
storage rows and cache sizes exceeding the shared model's Integer range fail.
The cached allocation boundary is not treated as the last issued value.
Sequence-specific SELECT privileges are required; query failures propagate.
On 11.5+, the catalog header query is followed by UNION ALL batches of 100 to
read cache_size from sequence storage, retaining signed/unsigned catalog types.
The full reader therefore uses one catalog query plus ceil(count / 100) storage
queries on both legacy and modern paths. Cache values must fit the shared
Integer model; invalid/missing rows and values fail explicitly.
Batch boundary and malformed-row tests cover duplicate/invalid IDs, missing
rows, negative/overflowing cache sizes and fractional values. Application period
readers reject blank identities and repeated boundary columns.
ENGINE and COMMENT are retained by legacy discovery and by a qualified TABLES
join on 11.5+, without extra per-object queries, and emitted by CREATE SEQUENCE.
Invalid ENGINE tokens fail; comments use the existing SQL literal escaping.
Other sequence table options and current runtime position are not reconstructed.

Reference: [MariaDB INFORMATION_SCHEMA.SEQUENCES](https://mariadb.com/docs/server/reference/system-tables/information-schema/information-schema-tables/information-schema-sequences-table).
Sequence syntax: [MariaDB CREATE SEQUENCE](https://mariadb.com/docs/server/reference/sql-structure/sequences/create-sequence).
CHECK catalog reference: [MariaDB INFORMATION_SCHEMA.CHECK_CONSTRAINTS](https://mariadb.com/docs/server/reference/system-tables/information-schema/information-schema-tables/information-schema-check_constraints-table).

## Investigation scope

The 10.2.7+ table reader now uses a MariaDB-owned catalog query restricted to
BASE TABLE, SYSTEM VERSIONED and TEMPORARY. Views continue through ViewReader;
SEQUENCE rows are no longer also exposed as ordinary tables. Sequence metadata
before 11.5 is retrieved by the separate batched reader. Query rendering tests cover the type
filter and optional schema/table batches across 10.2, 10.3, 11.4, 11.5 and 12.1.
Older readers are unchanged; the 11.0–11.3 resolver boundary is corrected above.

Reference: [MariaDB INFORMATION_SCHEMA.TABLES](https://mariadb.com/docs/server/reference/system-tables/information-schema/information-schema-tables/information-schema-tables-table).

- Live verification of batched sequence storage reads on 10.3–11.4.
- Generated/invisible columns, CHECK constraints and MariaDB-specific metadata.
- Type, default, quoting and version boundaries inherited from MySQL.
- Metadata-to-XML-to-DDL behavior and explicit validation boundaries.

Unit tests use mocked metadata and SQL generation. Authorized local Docker
qualification covers 10.5, 11.4 and 11.8; other versions and production-like
privilege configurations remain unqualified.

The inherited index reader has dedicated MariaDB metadata/XML/DDL tests for
IGNORED state, same-named indexes on separate tables, compound key order and
prefix lengths, with one bulk metadata query. The absence of IGNORED on 10.5
keeps indexes enabled. No production change was required for this audit.
See [Ignored Indexes](https://mariadb.com/docs/server/ha-and-performance/optimization-and-tuning/optimization-and-indexes/ignored-indexes)
for the 10.6 feature boundary. Broader limitations are tracked in
[ENHANCEMENT_STATUS.md](ENHANCEMENT_STATUS.md).

Stored-object tests cover view algorithm/security/check/output names and routine
body/security/decimal returns/INOUT ENUM values through metadata, XML and DDL on
11.5. The shared XML fix keeps explicit value elements intact and retains legacy
comma-separated property input; XML output format is unchanged.

The MariaDB 10.3+ registry validates routine completeness before generating SQL:
missing/blank bodies and missing function return types fail with guidance to
check metadata privileges or supply complete CREATE definitions. Complete
definitions remain authoritative even when supplemental components are present.
Pre-10.3 factories are unchanged; Oracle/package syntax still requires separate
qualification.
Whitespace-only full definitions and function return definitions are rejected
as invalid input, including when a supplemental body is available.
