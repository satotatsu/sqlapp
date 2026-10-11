# AlloyDB / AlloyDB Omni dialect

`sqlapp-core-alloydb` adds PostgreSQL engine **15, 16 and 17** baselines for
AlloyDB and AlloyDB Omni. It reuses `sqlapp-core-postgres` readers, SQL factories
and bulk executors instead of maintaining a parallel implementation. Its core
and PostgreSQL API dependencies preserve access to the existing shared model;
no driver or dependency version changes are introduced.

Omni verification uses official `google/alloydbomni` containers. Managed
Google Cloud AlloyDB remains **experimental / real-service unverified**:
Omni tests do not qualify cloud storage, permissions, extensions, connectivity
or failover. PostgreSQL 14 and earlier and 18 and later are outside this
module's declared baseline. Public documentation mentions Omni 18 versions,
but the tested public registry had neither `18.3.0` nor `18.1.0`; no unexecuted
18 branch is advertised as supported.

## Use

Add `com.sqlapp:sqlapp-core-alloydb` at the same version as the other sqlapp
artifacts. Continue using the existing high-level entry point:

```java
Dialect dialect = DialectResolver.getInstance().getDialect(connection);
```

The resolver checks the registered `google_columnar_engine.enabled` flag in
`pg_catalog.pg_settings` on a PostgreSQL JDBC connection. The flag's presence,
not its on/off value, is the identification marker. Omni 16.8.0 reports an
ordinary PostgreSQL `version()` string; endpoint names and version banners
alone cannot identify that server. The columnar engine is not enabled or
changed by resolution. Known YSQL/Cockroach version banners bypass this probe.

The probe performs one read-only SELECT and closes its statement/result set.
It never changes transaction settings, commits or rolls back. Failures are
propagated with their SQL cause. If the marker is unavailable/hidden, automatic
identification cannot establish AlloyDB identity and ordinary PostgreSQL
resolution continues. Adding this artifact adds that catalog probe to normal
PostgreSQL connection-based resolution. Product-name-only resolution does not
open a connection. The marker is a catalog heuristic, not an authentication
mechanism, and does not distinguish managed AlloyDB from Omni.

For offline generation or explicitly selected AlloyDB identity:

```java
Dialect dialect = DialectResolver.getInstance()
    .getDialect("AlloyDB", 16, 0, null);
```

Aliases: `AlloyDB`, `Google AlloyDB`, `AlloyDB for PostgreSQL`, `AlloyDB Omni`
and `alloydb-omni`. Supply the PostgreSQL engine major. Unsupported majors fail
clearly. Explicit product selection does not validate a remote server.
The inherited metadata readers retain the JDBC-reported PostgreSQL product
name in Catalog/Schema product fields. When selecting a dialect from exported
XML without a live connection, explicitly select AlloyDB if that identity is
required; XML product fields alone cannot distinguish this server family.

## Relational scope and boundaries

The corresponding PostgreSQL baseline supplies names/quoting, types,
metadata and CREATE/ALTER/DROP factories. Dedicated ServiceLoader providers
preserve AlloyDB identity while reusing PostgreSQL COPY FROM STDIN,
staging-table UPSERT and set-based snapshot execution. Existing bulk option
validation and caller-owned transaction semantics are preserved.

Tests cover table/index/sequence/view/function metadata, Schema XML and table
recreation, generated ALTER/DROP, COPY escapes/null/empty/binary/arrays,
UPSERT and rollback, COPY failure rollback, and atomic checkpoint failure /
resume. Omni-specific tests exercise identification with columnar disabled
and a non-superuser login. Ordinary PostgreSQL controls guard against false
identification, including an unregistered custom setting placeholder.
Snapshot execution tests also cover SCD2 inserts, changed and missing-row
expiration, unchanged rows, historical timestamps/current flags, caller rollback,
commit-time guard failure and duplicate-source rejection followed by retry.
The history table is created through the shared Schema model and SQL factory.

Columnar population/cache state, automatic columnarization, advanced vector
execution/tuning, Google ML integrations, physical placement, cloud IAM/proxies,
managed roles/ownership, replication, backup, performance and failover are
outside this baseline. These are not silently translated into Table specifics
or replayed as DDL. Ordinary PostgreSQL syntax inheritance does not grant
privileged operations or install vendor extensions on a managed service.

## Local tests

```shell
./gradlew :sqlapp-core-alloydb:test :sqlapp-core-alloydb:assemble
./gradlew :sqlapp-core-dialect-test:alloydbCompatibilityTest
```

The fixed matrix uses official `google/alloydbomni:15.17.0`,
`google/alloydbomni:16.8.0` and `google/alloydbomni:17.9.0`, plus an upstream
`postgres:17.9` control. Individual tasks are `alloydb15CompatibilityTest`,
`alloydb16CompatibilityTest`, `alloydb17CompatibilityTest` and
`alloydbPostgresControlTest`. Containers are disposable, use anonymous storage,
and require no external database or Google Cloud credentials. The test runner
allocates 256 MiB shared memory; it does not alter host settings or request
privileged containers. Consult Google's installation prerequisites for CPU,
RAM, disk, Linux runtime and container support. This is functional verification,
not a production platform certification or benchmark.

The 2026-10-10 qualification passed 7 module tests, all 8 cases on each of
these three Omni images (24 total), and 8 PostgreSQL control cases. Assembly
and local publication metadata generation also passed. PostgreSQL regression
and the dependent dialect/command/plugin suites passed; detailed commands and
results are recorded in the [compatibility matrix](../docs/compatibility.md).

## Optional managed AlloyDB tests

`alloydbExternalTest` is separate from default tests, `dockerTest` and the
compatibility aggregate. It was not executed during implementation. Use only
an explicitly authorized disposable writable database and a writer endpoint.
It creates UUID schemas, inserts/updates rows, exercises rollback/checkpoint
behavior and drops its schemas with CASCADE. An interrupted run may leave a
`sqlapp_alloydb_...` schema behind. Preflight verifies AlloyDB identity before
any test writes; ordinary PostgreSQL targets are rejected.

Supply environment variables without recording credentials in source or logs:

- `SQLAPP_ALLOYDB_JDBC_URL`: `jdbc:postgresql://.../disposable_database`
- `SQLAPP_ALLOYDB_USER` and `SQLAPP_ALLOYDB_PASSWORD`
- `SQLAPP_ALLOYDB_ALLOW_DESTRUCTIVE_TESTS=true`

The fixture requests `sslmode=verify-full`; configure trusted certificates and
network access using the PostgreSQL driver's normal mechanisms. Cloud proxy,
connector and IAM integration are not supplied by the dialect itself.

```shell
./gradlew :sqlapp-core-dialect-test:alloydbExternalTest
```

Before qualifying managed AlloyDB, record the PostgreSQL engine version,
service configuration, identification, role/extension differences and the
metadata/recreation/bulk results for each major being claimed. No Google Cloud
resource is created, changed or deleted by the test task.

## References

- [Omni overview](https://docs.cloud.google.com/alloydb/omni/docs)
- [Official container installation](https://docs.cloud.google.com/alloydb/omni/containers/17.9.0/docs/quickstart)
- [AlloyDB columnar flags](https://docs.cloud.google.com/alloydb/docs/reference/columnar-engine-flags)
- [Container architecture and managed-service differences](https://docs.cloud.google.com/alloydb/omni/containers/current/docs/overview)

## Follow-up qualification

The SCD2 follow-up passed all 9 cases per Omni image (27 total), all 9
PostgreSQL control cases and 7 module tests on 2026-10-10.

The PostgreSQL builder now generates unconstrained `VARCHAR` for a
`LONGVARCHAR` column without a length, including arrays, and preserves an
explicit `text` type as `TEXT`. Explicit VARCHAR lengths retain their existing
behavior. This fixes the initial invalid `varchar(0)` fixture; the SCD2 test
again uses the original unbounded model. A separate generated-DDL case checks
60,000-character Japanese strings, arrays with empty/null elements and TEXT.
No public API, configuration or metadata representation changed. The final
character-DDL regression passed all 10 cases per Omni image (30 total) and
all 10 PostgreSQL controls.
Advanced columnar tuning and advanced ScaNN features remain deferred as described above.


## ScaNN table indexes

AlloyDB 15/16/17 now select dedicated table index readers and CREATE factories.
The relational PostgreSQL version branches are preserved; ordinary indexes
continue through the inherited factories. The application must install the
`vector` and `alloydb_scann` extensions separately with appropriate privileges.
Resolving, reading metadata and generating DDL never installs extensions.

A new ScaNN index can use the shared model without SQL fragments:

```java
Index index = new Index("embedding_scann");
index.getColumns().add("embedding");
index.setIndexType(IndexType.Vector);
index.setVectorDistanceType(VectorDistanceType.Cosine);
table.getIndexes().add(index);
SqlFactory<Index> factory = dialect.createSqlFactoryRegistry()
    .getSqlFactory(index, SqlType.CREATE);
```

Supported distances are `Cosine`, `Euclidean` (L2), and `DotProduct` or
`InnerProduct`. New indexes use engine defaults when creation options are
absent. The engine validates training population: the tested Omni 15 image
requires at least 10,000 rows with its default settings, whereas an explicit
`num_leaves='1'` also works with smaller populated test tables. Defaults and
minimum population can vary by extension version; sqlapp does not hide server
errors or silently adjust tuning. Optional advanced settings are held in `Index.specifics`:

| Key | Value |
| --- | --- |
| `alloydb.index.method` | `scann` |
| `alloydb.scann.keys` | Server-deparsed key SQL including schema-qualified operator classes |
| `alloydb.scann.options` | CREATE WITH SQL entries, for example `num_leaves='1', quantizer='FLAT'` |

Metadata sets `IndexType.Vector` and the shared distance property and retains
key SQL/options in these specifics. Key SQL is authoritative when present;
when changing a read index's expression or distance, update/remove this advanced
value as well. Without it, creation requires one modeled column and a supported
distance. Empty keys, unknown methods, unsupported distances, UNIQUE and INCLUDE
are rejected clearly. Creation options are passed to the target engine for
version-specific validation; no undocumented options are synthesized.

The reader joins `pg_index`, `pg_class`, `pg_am` and `pg_opclass` and uses the
server deparser for expressions; it does not parse CREATE INDEX with a regex.
It makes one ordinary index query and at most one supplemental ScaNN query
for the requested schema/table/index set, independent of the number of indexes.
Ordinary known index types require no supplemental query. XML preserves these
specifics; CREATE emits `USING scann`, operator classes, WITH options, optional
named tablespace, predicate and comments. The required extensions, operator
classes, expression dependencies and tablespaces must exist on the destination.

The local suite verifies all three distances, a typed expression with a partial
predicate and FLAT quantizer, new-index generation from the shared model, XML,
DROP/CREATE and equality of server-deparsed DDL. It verifies an exact nearest-neighbor
query result with index scans disabled, but does not certify the optimizer's chosen plan, performance or
recall. Query-count checks compare one index with six indexes and also check a
regular B-tree-only read. Named tablespace SQL is unit-tested, not exercised
with a filesystem-backed tablespace. Materialized-view ScaNN metadata,
automatic tuning/maintenance behavior, new preview options, ScaNN-specific columnar population,
cloud extension permissions and managed-service execution remain unqualified.

Reference: [Omni 16.8 vector search](https://docs.cloud.google.com/alloydb/omni/containers/16.8.0/docs/ai/perform-vector-search).


The final ScaNN qualification on 2026-10-10 passed all 10 module cases, all
11 cases per Omni image (33 total) and all 10 PostgreSQL controls. Assembly,
sources/Javadoc and SQL-resource packaging also passed. No managed/external
AlloyDB connection was used.


## Persistent columnar target configuration (Omni)

A full AlloyDB Catalog read reuses the inherited batch-read `Catalog.settings`
to populate `Table.specifics["alloydb.columnar.columns"]`. It adds **zero JDBC
queries**. The authoritative source is the configured
`google_columnar_engine.relations` flag, not `g_columnar_columns` or the current
cache contents. Named columns are comma-separated and case-sensitive; `*`
means all columns, including future columns. A missing specific means preserve
the captured selection, while `-` means explicitly remove it (an empty value would be omitted by XML).
Engine enablement, memory and auto-columnarization settings already remain in
Catalog.settings/XML; this API generates changes only for the relations flag.

Use the normal catalog reader, edit a table and review the explicit plan:

```java
String catalogName = connection.getCatalog();
Catalog catalog = dialect.getCatalogReader().getAllFull(connection).stream()
    .filter(c -> c.getName().equals(catalogName))
    .findFirst().orElseThrow();
Table table = catalog.getSchemas().get("public").getTables().get("orders");
AlloyDBColumnarConfiguration.setColumns(table, "order_id", "amount");
var plan = AlloyDBColumnarConfiguration.plan(catalog);
// Review plan.originalRelations(), plan.relations() and plan.sqlOperations().
```

Use the catalog reader's `setCatalogName` to restrict the read when appropriate.
`setAllColumns(table)` selects the whole relation; `clear(table)` removes it.
Column names must exist in the model with the exact case. XML retains both
the instance-wide source setting and per-table choices.

Planning requires a captured, current instance-wide relations setting in
Catalog.settings; when absent it fails instead of assuming an empty instance.
It merges only tables with explicit specifics and preserves other schemas,
unread tables, materialized-view entries and other databases from the original
flag. It emits no SQL for an unchanged selection. It never mutates the baseline
setting, opens a connection, applies SQL, enables the engine or restarts it.
Re-read the setting before planning/applying against a live instance and after
application; the reviewed plan is a snapshot, not a concurrency lock.

Changed plans contain an `ALTER SYSTEM SET google_columnar_engine.relations`
operation followed by `SELECT pg_catalog.pg_reload_conf()`. Apply them only
through an explicitly authorized Omni single-server administration connection
with suitable privileges and auto-commit (`plan.requiresAutoCommit()`). Check
SQL failures and the reload result; they are not transactionally atomic.
Ensure referenced tables/columns exist and the columnar engine has already
been enabled with adequate memory. Enabling it requires a separate restart.
Ordinary CREATE TABLE never applies these operations. Managed Google Cloud
AlloyDB uses service-level flag management and is not qualified for this SQL
application path; this module does not invoke cloud APIs or orchestrators.

The tested relations flag grammar accepts case-sensitive ASCII identifiers
with letters/digits/underscores (initial letter/underscore). It does not accept
SQL double quotes: Omni 16 stored a quoted-name flag but its population worker
rejected it. The planner therefore rejects names requiring quotes, Unicode,
spaces, punctuation and duplicate/ambiguous relation entries rather than
producing a configuration that fails later. Invalid/incomplete flag syntax
also fails clearly. These are explicit support boundaries, not SQL identifier
quoting changes elsewhere in the dialect.

The Omni matrix uses a separate disposable columnar-enabled container per
engine, 128 MiB columnar memory and disabled automatic column selection. It
reads a filtered Catalog, preserves an unread table, round-trips XML, applies
the generated plan, reloads and restarts that owned container. It then checks
that both configured tables and the three selected columns are registered.
Actual Docker port bindings are refreshed after restart. The ordinary Omni
fixture stays columnar-disabled and no host/local database settings are changed.
No runtime cache state is persisted as desired configuration. Performance,
refresh timing, unsupported types, live cache removals, materialized-view
metadata enrichment and managed-service provisioning remain outside this test.

References: [Omni persistent columnar targets](https://docs.cloud.google.com/alloydb/omni/containers/17.9.0/docs/columnar-engine/manage-content-manually),
[Omni enablement and memory configuration](https://docs.cloud.google.com/alloydb/omni/containers/17.9.0/docs/columnar-engine/configure).
