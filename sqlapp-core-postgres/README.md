# PostgreSQL dialect

## Floating-point SQL generation

The canonical `DataType.DOUBLE` model emits `DOUBLE PRECISION`, including array
declarations. This applies to all PostgreSQL versions selected by the existing
resolver (legacy 8.x through 18); it does not introduce a version-specific catalog
query or change the resolver. Input type aliases `DOUBLE`, `DOUBLE PRECISION` and
`FLOAT8` remain recognized, including arrays.

This corrects invalid `DOUBLE DEFAULT ...` DDL when migrating Access or other
source products through the shared Schema. Source model identifiers, native type
names, defaults and primary keys are retained; the target dialect supplies the
executable type spelling. No shared core API or configuration format changes.

Regression tests cover scalar/array resolution and DDL across representative old
and current dialect versions. Live PostgreSQL execution requires separately
authorized database tests.

## Data type regression coverage

`PostgresDataTypeMatrixTest` checks built-in numeric, character, binary,
date/time, UUID, XML, network and geometric types through type resolution,
CREATE TABLE generation and resolution of the generated type. It includes
aliases, precision/scale and zero, one and two array dimensions across every
resolver branch from 8.x through 18. Character, numeric and interval boundary
tests provide additional coverage.

Type registration recognizes REAL, FLOAT (including the 24/25 precision
boundary), CHARACTER VARYING, TIME WITH TIME ZONE, TIMESTAMPTZ and explicit
WITHOUT TIME ZONE spellings. XML emits XML without a length modifier.
Shared type definitions now distinguish LINE from CIRCLE and classify MACADDR
as MACADDR rather than MCHAR. Existing stored models with the old incorrect
classification are not automatically rewritten; re-read their metadata.

From PostgreSQL 12, SMALLDATETIME emits TIMESTAMP(0) WITH TIME ZONE because
abstime was removed. PostgreSQL 11 and older retain the existing abstime output.
The resolver, metadata queries, public APIs and configuration formats are unchanged.

References: [PostgreSQL types](https://www.postgresql.org/docs/18/datatype.html),
[floating-point precision](https://www.postgresql.org/docs/18/datatype-numeric.html),
[date/time declarations](https://www.postgresql.org/docs/18/datatype-datetime.html),
[PostgreSQL 12 removals](https://www.postgresql.org/docs/release/12.0/).
