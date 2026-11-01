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

Changes stay in sqlapp-core-mariadb, preserve existing public signatures and
Schema XML formats, and add no dependencies. The resolver and supported version
selection are unchanged. INFORMATION_SCHEMA.SEQUENCES remains confined to 11.5+;
the reader is not activated on older servers by these fixes.

Reference: [MariaDB INFORMATION_SCHEMA.SEQUENCES](https://mariadb.com/docs/server/reference/system-tables/information-schema/information-schema-tables/information-schema-sequences-table).

## Investigation scope

- Sequence discovery/recreation before 11.5, with no N+1 catalog retrieval.
- Generated/invisible columns, CHECK constraints and MariaDB-specific metadata.
- Type, default, quoting and version boundaries inherited from MySQL.
- Metadata-to-XML-to-DDL behavior and explicit validation boundaries.

Local tests use mocked metadata and SQL generation. No Docker/container or
external database was used in this initial work; live compatibility remains
unverified.
