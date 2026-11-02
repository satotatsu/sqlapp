# MariaDB dialect enhancement

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
- The 10.2.7+ CHECK metadata query joins by schema, constraint name and table
  name, preventing expressions from other tables with the same constraint name
  from being attached to the requested table. Retrieval remains one bulk query.
  Mocked reader/XML/DDL regression coverage includes 10.2.7, 10.5, 11.5 and 12.1.

Changes stay in sqlapp-core-mariadb, preserve existing public signatures and
Schema XML formats, and add no dependencies. The resolver and supported version
selection are unchanged. INFORMATION_SCHEMA.SEQUENCES remains confined to 11.5+.
MariaDB 10.3–11.4 uses a separate reader: one filtered TABLES discovery followed
by UNION ALL batches of at most 100 sequence storage tables. It preserves start,
increment, bounds, cache size and cycle without advancing the sequence. Missing
storage rows and cache sizes exceeding the shared model's Integer range fail.
The cached allocation boundary is not treated as the last issued value.
Sequence-specific SELECT privileges are required; query failures propagate.
Engine/comment options and current runtime position are not reconstructed.

Reference: [MariaDB INFORMATION_SCHEMA.SEQUENCES](https://mariadb.com/docs/server/reference/system-tables/information-schema/information-schema-tables/information-schema-sequences-table).
Sequence syntax: [MariaDB CREATE SEQUENCE](https://mariadb.com/docs/server/reference/sql-structure/sequences/create-sequence).
CHECK catalog reference: [MariaDB INFORMATION_SCHEMA.CHECK_CONSTRAINTS](https://mariadb.com/docs/server/reference/system-tables/information-schema/information-schema-tables/information-schema-check_constraints-table).

## Investigation scope

The 10.2.7+ table reader now uses a MariaDB-owned catalog query restricted to
BASE TABLE, SYSTEM VERSIONED and TEMPORARY. Views continue through ViewReader;
SEQUENCE rows are no longer also exposed as ordinary tables. Sequence metadata
before 11.5 is retrieved by the separate batched reader. Query rendering tests cover the type
filter and optional schema/table batches across 10.2, 10.3, 11.4, 11.5 and 12.1.
Older readers and the dialect resolver are unchanged.

Reference: [MariaDB INFORMATION_SCHEMA.TABLES](https://mariadb.com/docs/server/reference/system-tables/information-schema/information-schema-tables/information-schema-tables-table).

- Live verification of batched sequence storage reads on 10.3–11.4.
- Generated/invisible columns, CHECK constraints and MariaDB-specific metadata.
- Type, default, quoting and version boundaries inherited from MySQL.
- Metadata-to-XML-to-DDL behavior and explicit validation boundaries.

Local tests use mocked metadata and SQL generation. No Docker/container or
external database was used in this initial work; live compatibility remains
unverified.

The inherited index reader has dedicated MariaDB metadata/XML/DDL tests for
IGNORED state, same-named indexes on separate tables, compound key order and
prefix lengths, with one bulk metadata query. The absence of IGNORED on 10.5
keeps indexes enabled. No production change was required for this audit.
See [Ignored Indexes](https://mariadb.com/docs/server/ha-and-performance/optimization-and-tuning/optimization-and-indexes/ignored-indexes)
for the 10.6 feature boundary. Broader limitations are tracked in
[ENHANCEMENT_STATUS.md](ENHANCEMENT_STATUS.md).
