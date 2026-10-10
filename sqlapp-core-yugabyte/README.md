# YugabyteDB YSQL

`com.sqlapp:sqlapp-core-yugabyte` supports ordinary relational schema metadata,
SQL recreation and bulk migration on the PostgreSQL **11 and 15 YSQL engines**.
It reuses `sqlapp-core` and `sqlapp-core-postgres`; supply a PostgreSQL-compatible
JDBC driver separately. YCQL is outside this scope.

## Getting started

Use the usual resolver with the connected database:

```java
import java.sql.Connection;
import java.sql.SQLException;

import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkInsertResolver;
import com.sqlapp.jdbc.bulk.BulkOption;
import com.sqlapp.jdbc.bulk.BulkUpsertOption;
import com.sqlapp.jdbc.bulk.BulkUpsertResolver;

class YsqlUsage {
    static Schema readSchema(Connection connection, String schemaName) throws SQLException {
        var dialect = DialectResolver.getInstance().getDialect(connection);
        var reader = dialect.getCatalogReader().getSchemaReader();
        reader.setSchemaName(schemaName);
        return reader.getAllFull(connection).stream()
                .filter(schema -> schemaName.equals(schema.getName()))
                .findFirst().orElseThrow();
    }

    static long copyRows(Connection target, Table table) throws SQLException {
        return BulkInsertResolver.execute(target, table, BulkOption.defaults());
    }

    static long upsertRows(Connection target, Table table) throws SQLException {
        return BulkUpsertResolver.execute(target, table, BulkUpsertOption.defaults());
    }
}
```

Metadata reading does not load row data. Populate `table.getRows()` yourself,
for example with `table.readData(resultSet)` from an explicitly chosen source
query. The target table must already exist and match the supplied Schema model.
UPSERT needs a modeled primary/unique conflict key; advanced options can choose
keys, duplicate handling and insert/update actions. Use the shared
[bulk migration workflow](../docs/migration/README.md) for checkpoint/resume,
verification and repair rather than building those around the example methods.

The resolver recognizes JDBC product `PostgreSQL` when its version retains the
`-YB-` marker, such as `11.2-YB-2.20.1.1-b0` or `15.2-YB-2025.1.0.0-b0`.
Identification uses JDBC metadata without an extra query. Drivers stripping the
marker cannot be automatically distinguished from PostgreSQL; database names
and JDBC URLs are not used as product evidence.

For explicit resolution, `getDialect("YugabyteDB", 15, 2, null)` takes the
**PostgreSQL engine version**, not the YugabyteDB release. Only engine majors
11 and 15 are accepted; unknown majors and ambiguous release numbers fail.
Inspect the connected engine during upgrades. Ordinary PostgreSQL resolution
and its cache remain separate from YSQL identification.

## Supported and verified scope

| Area | Coverage on both YSQL baselines | Boundary |
|---|---|---|
| Schema | Tables, PK/FK/unique/check constraints, indexes, views, sequences, comments; metadata/XML/executable recreation | No arbitrary dependency graph or cyclic recreation guarantee |
| Types | Common scalar types, enum/domain/composite types, numeric/length/temporal modifiers, bit/interval metadata, qualified quoted custom types | Advanced type dependency graphs and extension types are unverified |
| Constraints/indexes | Composite keys, covering keys, partial/expression indexes, explicit null ordering, all FK actions, MATCH FULL/SIMPLE, deferred FKs, NOT VALID CHECK/FK | Deferred PRIMARY KEY/UNIQUE is unsupported; inheritance is outside scope |
| Routines | Overload identity, complex/default/OUT/INOUT/VARIADIC/TABLE arguments, executable definitions, local SET, comments and EXECUTE privilege generation | No automatic owner/ACL replay; signature changes require explicit migration |
| Routine differences | Volatility, null-input behavior, security and comments; verified catalog-backed SQL/PLpgSQL body replacement from cloned or independent XML snapshots | Mixed header/body edits are rejected; no live drift check |
| Triggers/views | Trigger timing/state/WHEN, statement/TRUNCATE and INSTEAD OF view triggers; view CHECK OPTION/security_barrier and column comments | DDL transaction guarantees and RLS/predicate-leakproofness are outside scope |
| Bulk | COPY, staging ON CONFLICT UPSERT, null/empty/Unicode/binary/decimal values, identities and tested arrays | Transaction ownership and array limits below apply |
| Migration | Checkpoint/resume, composite keyset, generated parent/child keys, verification/repair, lease fencing, savepoints and write conflicts | Single-node functional qualification; no multi-node failover/performance claim |
| Placement | Tablespace replica policy, table/index references, Colocation, grouped Hash keys, Range keys and split clauses through XML and CREATE | Target topology and colocated database must exist; no live node/tablet identity replay |

The whole-schema acceptance case reads metadata and rows, XML-round-trips them,
drops and recreates the schema through the standard CREATE registry, copies
parent then child rows, performs UPSERT and rechecks data and metadata. It
executes recreated functions, a dependent view, triggers, FK and domain checks.
See [acceptance evidence](../docs/compatibility.md#whole-schema-and-data-acceptance-on-ysql).

| Version-specific behavior | YSQL 11 | YSQL 15 |
|---|---|---|
| NULLS NOT DISTINCT option | Ignored; ordinary UNIQUE null semantics retained | Recreated and enforced |
| security_invoker views | Ordinary owner-based behavior | Invoker base-table permission checks verified |
| Negative/excess numeric scale | Unsupported syntax boundary verified | Rounding and overflow verified |
| Procedure security/body changes | Rejected before SQL generation; comments supported | Generated and invoked successfully |
| SQL MERGE | Generated merge uses ON CONFLICT | Generated merge uses ON CONFLICT |

Routine body replacement requires verified catalog DDL and matching headers;
COST, SET, signature or language edits mixed into a body change fail. Both
snapshots must describe the expected state; execution provides no live drift
or transaction guarantee. See [body replacement](../docs/compatibility.md#verified-postgresql-and-ysql-routine-body-replacement)
and [independent snapshots](../docs/compatibility.md#independent-routine-snapshots-for-body-differences).
Routine GRANT/REVOKE generation has explicit grantor-context and visibility
limits; see [EXECUTE privileges](../docs/compatibility.md#routine-execute-privilege-round-trip).

## Bulk transactions and limits

YSQL registers its own bulk providers. COPY uses the PostgreSQL implementation
inside a YSQL transaction wrapper: on an auto-commit connection, one call owns
one transaction, commits on success, rolls back on failure and restores
auto-commit. Multiple calls do not form one transaction automatically. To make
parent/child copies atomic together, the caller must own their transaction.
When a transaction already exists, the executor neither commits nor rolls it
back; after failure, the caller must roll back.

UPSERT copies to a temporary staging table and executes `INSERT ... ON CONFLICT`.
Generated merge SQL also uses ON CONFLICT and needs a modeled primary/unique key.
Set-based SCD2 snapshot acceleration is not enabled; use the shared portable
snapshot path. Distributed SQLExceptions propagate and caller transactions are
not replayed. The lease store retries 40001/40P01 only in transactions it owns,
after rollback and with a bounded retry count.

Array tests cover nested integer/text, primitive-byte smallint arrays, one- and
two-dimensional bytea/UUID/numeric/boolean/date/timestamp arrays, and
two-dimensional varchar arrays, including SQL NULL, empty arrays and null
elements. Other element types, arbitrary dimensions and non-default lower
bounds are unverified. Domains over builtin/enum scalar and array types are
covered; arbitrary dependencies and domain collation/interval qualifiers are
unverified. Multiple domain CHECKs combine into one expression without their
individual names. Composite ALTER and dropped-attribute recreation are unverified.

Sequence metadata preserves configuration, comments and bounds, not live
position or ownership migration. Automatic role/owner/ACL replay, RLS,
automatic topology translation, extension behavior,
multi-node failover and performance qualification are outside this scope.
DDL tests execute outside caller transactions and do not promise PostgreSQL
transactional-DDL semantics. Validate the exact deployment separately.

## Placement metadata and CREATE DDL

YSQL readers retain placement settings in existing `specifics` maps and use the
existing table/index `tableSpaceName` properties for references. No shared model
or XML format change is required. The catalog reader supplies TableSpace objects;
a Schema-only snapshot carries references but not their TableSpace definitions.
Read the needed user tablespaces separately or through the Catalog model, and
create them before tables. Builtin `pg_default`/`pg_global` are not creation targets.

| Object | Specifics key | Meaning |
|---|---|---|
| TableSpace | `YSQL_REPLICA_PLACEMENT` | Catalog JSON placement policy, including replica/zone/leader preferences when present |
| Table | `YSQL_COLOCATION` | Explicit `true` or `false` |
| Table / Index | `YSQL_SPLIT_INTO` | Read-time hash tablet count, emitted as initial `SPLIT INTO` |
| Table / Index | `YSQL_SPLIT_AT_BASE64` | UTF-8/base64 catalog `SPLIT AT VALUES` clause; preserves quoted/newline boundaries through XML |
| Constraint / Index | `YSQL_HASH_COLUMNS` | Number of leading grouped Hash key columns; zero means explicit Range ordering |
| UniqueConstraint | `YSQL_TABLESPACE` | Backing-index reference when independent of the table |

Constraint snapshots also retain backing-index split settings so their CREATE
path can build the placed unique index and attach it using `UNIQUE USING INDEX`.
Primary keys share table storage and use the table's split/tablespace settings;
no duplicate primary index is generated. Table-level hash counts read from the
catalog are supplemental metadata; key DDL is driven by the Constraint/Index.

For a different target topology, edit the Schema model before SQL generation:
set `table.setTableSpaceName("target_space")`, update index/constraint references
as needed, and set `tableSpace.getSpecifics().put("YSQL_REPLICA_PLACEMENT", targetJson)`
with the desired target policy via `put`. No automatic region/zone mapping or
cluster provisioning is performed. A user-created TableSpace requires its
placement JSON; absent placement is an actionable generation error.

A colocated table requires an already colocated target database. Its generated
operations include a database precondition, so an incompatible target fails
instead of silently ignoring `COLOCATION=true`. Colocated relations cannot have
split clauses. Conflicting split settings, invalid booleans/counts and excess
hash-key counts fail during generation. Preserve all returned operations.

The split snapshot describes the layout when read, including splits that may
have occurred automatically; it cannot recover the original CREATE's initial
count. Restored counts/boundaries are initial conditions, not a promise of stable
runtime placement. Live tablet IDs, node addresses, tablegroup/colocation IDs,
automatic ALTER/movement of existing data and SQL-partitioned parent placement
are outside this implementation. Colocation inherits target database/tablegroup
behavior; distinct custom tablegroups are not reconstructed. No distributed
performance or multi-node placement qualification is claimed.

See [placement implementation and validation](../docs/compatibility.md#ysql-placement-metadata-and-create-ddl).

## Verification

The placement regression passed **64 tests per local engine** (128 real-engine
passes), with zero failures/errors. The Yugabyte module passed 10 ordinary tests
with one optional external test skipped; PostgreSQL, command and Gradle plugin
checks retained 880 passes (their unchanged tasks were up-to-date). Yugabyte
assembly succeeded. See the [placement command and results](../docs/compatibility.md#ysql-placement-metadata-and-create-ddl).
The preceding broader regression passed 2,738 ordinary tests; see its
[whole-schema acceptance record](../docs/compatibility.md#whole-schema-and-data-acceptance-on-ysql).

The fixed baselines use PostgreSQL JDBC `42.7.11` and disposable single-node
containers, without external credentials or host data volumes:

- YSQL 11: `yugabytedb/yugabyte:2024.2.11.0-b36`.
- YSQL 15: `yugabytedb/yugabyte:2026.1.2.0-b137`.

```shell
./gradlew :sqlapp-core-yugabyte:test :sqlapp-core-dialect-test:yugabyteCompatibilityTest
```

Module tests cover SPI/resolution, engine boundaries, PostgreSQL/YSQL coexistence,
provider selection and generated SQL. The optional external
`YugabyteMetadataRoundTripTest` requires `SQLAPP_YSQL_JDBC_URL`, `SQLAPP_YSQL_USER`
and `SQLAPP_YSQL_PASSWORD`; only run it against an explicitly authorized
disposable database, as it creates and drops a random schema. Historical
PostgreSQL servers and other YugabyteDB releases were not verified by this matrix.
See the [integration guide](../sqlapp-core-dialect-test/README.md#yugabytedb-ysql-compatibility)
for engine-specific and focused commands. Detailed prior batch evidence remains
in [compatibility notes](../docs/compatibility.md).
