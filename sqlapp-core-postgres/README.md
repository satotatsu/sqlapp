# PostgreSQL dialect

## Materialized view ownership

PostgreSQL 9.3+ reads `pg_matviews.matviewowner` into the shared
`Mview.ownerName` property. Schema XML writes an optional `ownerName`
attribute, and cloning and differences retain it.

CREATE generates the view followed by `ALTER MATERIALIZED VIEW … OWNER TO`
when an owner is specified. An ownership-only view diff generates the same
ALTER statement. The owner role must already exist. Removing the owner from
a diff requires an explicit replacement role.
For a modeled query, CREATE emits visible column names as an explicit column
list, preserving aliases through XML recreation. A full `definition` takes
precedence over the query and columns.
Materialized view comments are read into `remarks` with the same catalog
query and restored with `COMMENT ON MATERIALIZED VIEW` before ownership
is transferred.
Comment differences also generate `COMMENT ON MATERIALIZED VIEW`; a null
target comment generates `IS NULL`. When both the comment and owner change,
the comment is applied before transferring ownership.
CREATE also restores the modeled tablespace, indexes and column comments.
Index and column comments preserve literal quote characters. Column-comment
differences and index additions, removals and comment changes are supported
alongside view comments, tablespace changes and ownership changes.
`SET TABLESPACE` requires an explicit existing tablespace, and is applied
before ownership is transferred. Structural changes to existing indexes or
to view columns require an explicit migration.

The disposable `PostgresMviewRoundTripTest` covers PostgreSQL 9.3.25 and 18.4.
It recreates the view through XML, verifies comments and indexes, applies
differences and refreshes the result. It uses UUID schemas, a dedicated role,
random test credentials and a loopback-only published port.
HTML documentation displays the escaped owner name.
The standard registry also supports `SqlType.DROP` and `SqlType.REFRESH`
for materialized views on PostgreSQL 9.3+. DROP uses the default dependency
checks. REFRESH performs an ordinary refresh.
Schema differences place materialized view drops before source table drops
and create them after table prerequisites. Arbitrary dependencies among views
require explicit ordering.
See the PostgreSQL 9.3 references for
[catalog ownership](https://www.postgresql.org/docs/9.3/view-pg-matviews.html)
and [ownership changes](https://www.postgresql.org/docs/9.3/sql-altermaterializedview.html).

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
