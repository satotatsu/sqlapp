# Apache Phoenix dialect enhancement

For HFile loading, Phoenix provides the external
`org.apache.phoenix.mapreduce.CsvBulkLoadTool`. A local 5.3.2 probe loaded
CSV into an empty table with `--table`, `--input`, and a fresh `--output`
directory, then verified the rows over JDBC. Use a matching Hadoop
MapReduce client and your HBase configuration. This native command runs
outside sqlapp's JDBC bulk UPSERT API.

Column metadata rejects duplicate column names within one tenant/schema/object
identity, including same-name columns in different families. This prevents
loss during attachment to the shared Schema model, which keys columns by
simple name. Same-name columns in separate tenants remain separate.

Phoenix 5.3+ table diffs support appending one nullable VARCHAR column to an
existing primary key ending in scalar VARCHAR. The table must have no indexes
and the extension must be the only change. Other key changes continue to
require a separate migration.

Phoenix bulk UPSERT uses direct JDBC batches for complete, non-null primary
keys and both insert and update actions. Set `useTransaction=false` explicitly.
The provider writes all writable columns and rejects a custom update subset
or staging table name. Source duplicate handling uses the shared bulk plan.
Earlier batches may already have been applied when a later row fails. Use a
connection for the intended tenant. Atomic table replacement is unsupported.
For a table with tenant metadata, bulk UPSERT verifies the connection's
JDBC catalog against that tenant before preparing any SQL.
Use `Phoenix.executeTenantBulkUpsert` to select a DataSource by the table's
tenant ID. The empty map key represents the global connection. A missing
mapping, a mismatched connection catalog, or a connection without auto-commit
fails before SQL preparation. The method closes the connection it acquires.
For other operations, `Phoenix.withTenantConnection(sources, tenantId, work)`
uses the same selection and verification. Complete JDBC work inside the callback.
The callback controls transaction handling and the connection closes on return
or failure.

Phoenix retains tenant identity in JDBC catalog metadata and Schema XML.
SQL object names contain the schema and object only. Execute the generated SQL
with a JDBC connection whose TenantId matches the intended tenant.

Sequence migrations reject changes to an existing sequence explicitly. CREATE
accepts the native BIGINT type or an omitted type. CREATE, DROP and value
allocation require a nonblank sequence name. Sequence counter restoration
requires a separate migration. CREATE rejects a populated `lastValue` because
silently ignoring it could reuse previously allocated values. For a new
destination sequence, clear `lastValue` and specify the independently verified
next value as `startValue`.
The tested CACHE 10 sequence returned 100, exposed catalog CURRENT_VALUE
110, and then returned 101. The catalog value describes reserved progress
and cannot supply the exact next value of an active cached client.
Sequence allocation honors schema qualification. When a returned first value
is expanded client-side, expansion fails rather than returning calculated
values outside known bounds or guessing a cycle boundary.

Phoenix INSERT and INSERT_ROWS factories preserve explicit column families in
UPSERT targets, as does INSERT_SELECT_NOT_EXISTS. That SQL uses a SELECT
predicate and does not guarantee atomic conflict handling. The same INSERT
factory supplies positional JDBC batch SQL;
added row diffs use dialect SQL literals with the row as their target, so
quoted column names with whitespace do not depend on named bind markers.
Deleted row diffs generate DELETE predicates from all modeled primary-key
columns. Added and deleted row diffs require a modeled primary key and non-null
values for every key column. Added rows must also be able to write all key columns.
batch sizing and binding continue through the shared JDBC executor.

## Semantic comparison of fixed table options

Index diffs also compare explicit SALT_BUCKETS values as integers. Equivalent
spellings avoid DROP/CREATE in table and view migrations. An absent setting
remains distinct from an explicit value, including zero.
Explicit GLOBAL and LOCAL index types compare case-insensitively. A spelling
change can accompany a state transition without triggering index recreation.
Explicit GLOBAL also matches an omitted type, as in CREATE INDEX.
An explicit BTree model type and a blank predicate compare like omitted values,
matching CREATE INDEX. Nonblank predicates still require native DDL.
Sort direction on INCLUDE columns does not trigger index recreation because
Phoenix only renders sort direction on index keys.
Table and view migrations treat an index key or INCLUDE column's explicit
family as redundant when it exactly matches the owning column's family.
Standalone index ALTER diffs use the same rule.
Explicit index key prefix lengths and NULL ordering are rejected during CREATE
and index additions or replacements in migrations.

Table ALTER diffs compare SALT_BUCKETS as a validated integer and MULTI_TENANT
as a validated boolean, using the same rules as CREATE. For example, `008` and
`8`, or `FALSE` and `false`, do not prevent an otherwise supported column
addition. Actual setting changes and removal still require a separate migration;
absence is not equated with an assumed server default. Invalid unchanged values
also fail when generating a column migration. DEFAULT_COLUMN_FAMILY keeps exact,
case-sensitive comparison. No public API, version boundary or XML format changed.

## Identifier validation and escaping

Phoenix identifier quoting now doubles embedded double quotes, retaining the
existing handling of already quoted names. This applies to all Phoenix resolver
versions without changing public signatures or the Schema XML format.
CREATE TABLE rejects primary-key references absent from the table's case-sensitive
column collection. CREATE INDEX rejects blank ordinary key/INCLUDE names and
explicit blank column families. Valid family identifiers retain normal quoting
through XML. No catalog queries or dependencies were added. Native syntax follows
the [quoted-name grammar](https://phoenix.apache.org/docs/grammar/).

## Strict optional boolean headers

IMMUTABLE_ROWS, MULTI_TENANT and TRANSACTIONAL use the same strict header
validation for tables and views. Case-insensitive true/false is retained through
Schema XML; absent fields and SQL NULL remain unknown. Other strings fail with
the field name instead of relying on JDBC boolean coercion. The existing single
getTables call and result closure on failure are preserved on all resolver paths.
No catalog columns, dependencies, public signatures or XML structure changed.
Live-driver behavior remains unverified.

## Transaction metadata and null storage safety

Phoenix 5.3.2 rejects in-place conversion from a nontransactional table to
Omid (error 1093). In the isolated probe, a new Omid table accepted
`UPSERT INTO destination (ID,VAL) SELECT ID,VAL FROM source`. The copied row
and the source row were both preserved, and subsequent commit and rollback
worked on the destination. For an actual migration, coordinate writes to
the source, verify the copied data and dependencies, and switch consumers
explicitly. This does not provide atomic cutover or provider conversion.

For an explicitly transactional table, set `phoenix.transactionalDefinition`
to a trusted, complete CREATE TABLE statement for the destination. On Phoenix
4.7+ it is emitted as supplied, with modeled indexes still emitted separately.
The caller specifies the provider and verifies that it is available on the
destination. Existing tables carrying this definition allow index-only modeled
changes. Other changes require an explicit native migration so the stored
CREATE statement cannot silently become stale.

Phoenix's optional getTables TRANSACTIONAL and TRANSACTION_PROVIDER fields now
survive header retrieval and Schema XML, including unknown/retired provider names.
No query or transaction-manager connection is added for these fields; older
driver projections with neither field remain supported. Malformed booleans or
blank provider names fail, and the JDBC result is closed on failure.

Known transactional tables now refuse modeled CREATE instead of silently
omitting transaction semantics. This deliberately includes known provider names:
provider availability, version-specific configuration and transaction guarantees
need a provider-aware migration, which is outside this local batch. No implicit
Tephra/Omid selection, downgrade or transaction conversion is generated.
Explicit TRANSACTIONAL=false can be rendered on 4.7+; a provider without a
transactional table is rejected. ALTER refuses transaction-flag/provider changes
or removal, while ordinary column additions to an unchanged transactional table
remain available and subject to Phoenix's execution checks.

STORE_NULLS (4.3+) is recovered in the existing supplemental table-property query
only on supported catalog versions and emits a validated boolean CREATE option.
SQL NULL is retained as unknown rather than an inferred flag. Setting it uses
PhoenixDdlOptions.STORE_NULLS; transaction metadata uses TRANSACTIONAL and
TRANSACTION_PROVIDER constants. All values use the existing specifics/XML model.
Explicit STORE_NULLS changes on nontransactional tables are supported by
the 5.3+ dialect. Removal requires an explicit replacement value.

These additions stay in sqlapp-core-phoenix, with additive constants/capabilities
and no existing signatures, dependencies or XML structure changed. Local tests
cover optional-field fallback, resource closure, XML, CREATE guards, read version
boundaries and safe failure paths. No live cluster or transaction behavior was
tested. The explicit DDL path generates a recreation plan; transaction execution
and provider availability remain destination-specific.

References: [native transaction semantics](https://phoenix.apache.org/docs/features/transactions/),
[STORE_NULLS and transaction versions](https://phoenix.apache.org/docs/grammar/),
[provider evolution](https://issues.apache.org/jira/browse/PHOENIX-4605).

## Metadata cache and guidepost tuning

UPDATE_CACHE_FREQUENCY (Phoenix 4.7+) and GUIDE_POSTS_WIDTH (4.9+) now use the
existing specifics map for CREATE, Table diffs and supplemental catalog recovery.
Use PhoenixDdlOptions.UPDATE_CACHE_FREQUENCY with ALWAYS, NEVER or a nonnegative
BIGINT millisecond count, and PhoenixDdlOptions.GUIDE_POSTS_WIDTH with a nonnegative
BIGINT byte count or explicit `NULL` to remove a table override. Absent keys emit
no CREATE setting. Removing a previously explicit key from a diff is ambiguous
and fails; specify the intended value instead. Case variants and numeric leading
zeros normalize without unnecessary SQL.

```java
target.getSpecifics().put(PhoenixDdlOptions.UPDATE_CACHE_FREQUENCY, "ALWAYS");
target.getSpecifics().put(PhoenixDdlOptions.GUIDE_POSTS_WIDTH, "NULL");
var operations = dialect.createSqlFactoryRegistry()
        .createSql(original.diff(target), SqlType.ALTER);
```

The existing set-based catalog SELECT projects each column only on supported
versions, with no additional query. Observed GUIDE_POSTS_WIDTH SQL NULL is retained
as explicit `NULL` (no table override); unselected older/unknown metadata stays
unset. Phoenix4_7 and its holder constant are additive, with nearest-version
inheritance. OFFSET/append-only 4.8, DEFAULT 4.9 and multi-row VALUES 5.3.1 keep
their existing boundaries. Invalid values, overflow and unsupported versions fail.

This batch covers tables; View/index-specific tuning, runtime caching/statistics
behavior and live old-version execution remain unverified. No shared-core, XML
structure or dependency changes were added. Local tests cover SQL/XML, metadata
version boundaries, explicit resets, invalid inputs and no-op normalization.

References: [table options](https://phoenix.apache.org/docs/grammar/),
[statistics/reset semantics](https://phoenix.apache.org/docs/features/statistics-collection/).

## WAL and append-only schema properties

The existing table specifics map now supports `PhoenixDdlOptions.DISABLE_WAL`
(`phoenix.disableWal`) and `PhoenixDdlOptions.APPEND_ONLY_SCHEMA`
(`phoenix.appendOnlySchema`). Both accept explicit true/false values, retain
their values through Schema XML, and generate native CREATE TABLE options.
Absent keys emit no setting. No durability or schema-mode default is inferred.

```java
table.getSpecifics().put(PhoenixDdlOptions.DISABLE_WAL, false);
table.getSpecifics().put(PhoenixDdlOptions.APPEND_ONLY_SCHEMA, true);
```

DISABLE_WAL changes use the ordinary Table diff and emit ALTER TABLE ... SET
DISABLE_WAL=TRUE or FALSE. Equivalent boolean spellings produce no SQL. Removing
an explicitly recorded value fails as ambiguous; set the intended value instead.
These operations can accompany existing column/IMMUTABLE_ROWS changes.
DISABLE_WAL=TRUE intentionally trades away WAL durability: Phoenix documents
possible data loss on region-server failure. sqlapp only generates SQL and never
enables it implicitly or promises transaction/rollback guarantees.

APPEND_ONLY_SCHEMA requires a known 4.8+ dialect. Existing Phoenix4_8 and later
classes expose its capability without changing resolver boundaries. Altering or
removing this setting is not generated in this batch. When it is true, a Table
diff that drops columns is refused; column additions remain available. View
property ALTER/reconstruction limitations remain unchanged.

For known 4.1+ catalogs the existing supplemental table-header SELECT also reads
DISABLE_WAL; it selects APPEND_ONLY_SCHEMA only from known 4.8+/5+ catalogs.
No additional SELECT is introduced. SQL NULL remains unset, malformed boolean
metadata fails, and all results are validated before publishing recovered
properties. Old/unknown version and view paths retain their existing behavior.
The common native DISABLE_WAL syntax targets the module's existing 4.1+ scope;
live catalog, crash recovery and older-version execution are not certified.

No dependencies, existing signatures, XML structure or shared-core behavior
changed. Local tests cover CREATE/XML, WAL changes in both directions, mixed
diffs, invalid values, append-only restrictions and catalog version boundaries.

References: [native options and ALTER syntax](https://phoenix.apache.org/docs/grammar/),
[catalog fields](https://github.com/apache/phoenix/blob/5.2/phoenix-core-client/src/main/java/org/apache/phoenix/jdbc/PhoenixDatabaseMetaData.java).

## User-authored functional indexes

Phoenix 4.3+ now accepts the shared IndexType.Function representation, matching
the PostgreSQL convention: an unresolved key ReferenceColumn name is SQL expression
text, while a key resolving to a modeled Column retains ordinary identifier and
family quoting. Expressions can mix with ordinary keys, descending order and
INCLUDE columns, and survive Schema XML without new fields or specifics keys.

```java
Index index = new Index("upper_name").setIndexType(IndexType.Function);
index.getColumns().add("UPPER(name)", Order.Desc);
table.getIndexes().add(index);
var operations = dialect.createSqlFactoryRegistry().createSql(index, SqlType.CREATE);
```

Select a known 4.3+ dialect through the ordinary resolver. The additive Phoenix4_3
class/holder constant preserves the later OFFSET, DEFAULT and VALUES boundaries;
older/unknown legacy dialects refuse functional indexes. Native LOCAL/GLOBAL,
INCLUDE, existence options and active-state restrictions continue to apply.
Family qualification for an expression belongs inside the expression itself;
a separate family specifics value on that expression is rejected. Blank keys
are rejected. Expressions are user-authored SQL; function availability, syntax,
types and dependencies are validated by Phoenix at execution, not by a new parser.

The existing Table/View diff path adds/drops/replaces these indexes through the
native factories. Since expression dependencies are not parsed, any column drop
with a retained expression index fails clearly: explicitly remove the index first.
This prevents a plan from silently retaining an index Phoenix may remove as a
side effect. Structural replacements remain non-atomic DROP/CREATE operations.

This batch does not reconstruct expressions from SYSTEM.CATALOG's internal key
names. The metadata reader's existing unsupported-functional/internal-key guard
is retained; live catalog expression recovery is still separate work. No metadata
queries, shared model/core, XML formats, dependencies or existing signatures were
changed. Local SQL/XML/version/diff tests do not certify live Phoenix execution.

References: [functional index introduction in 4.3](https://phoenix.apache.org/recent-improvements/),
[functional index semantics](https://phoenix.apache.org/docs/features/secondary-indexes/).

## State-only index ALTER

Index `SqlType.ALTER` now supports explicit `phoenix.indexState=DISABLE` or
`UNUSABLE` through the existing specifics map. The ordinary Table/View diff
entry point also recognizes state-only changes and emits ALTER INDEX instead
of DROP/CREATE. Changes to key columns, INCLUDE or other declarations retain
the structural replacement path; standalone index ALTER refuses those changes.

```java
Table target = original.clone();
target.getIndexes().get("idx_events").getSpecifics()
        .put(PhoenixAlterIndexFactory.STATE, "UNUSABLE");
var operations = dialect.createSqlFactoryRegistry()
        .createSql(original.diff(target), SqlType.ALTER);
```

The supported diff states are ACTIVE (also `a` or USABLE), UNUSABLE and DISABLE.
Equivalent ACTIVE spellings produce no operation. A known UNUSABLE source may
return to ACTIVE/USABLE using native USABLE. A DISABLE source cannot be enabled
or made maintained through this path: choose SqlType.REBUILD explicitly.
Missing states, removal of a state, raw catalog states other than `a`, internal
INACTIVE/pending/build states and unsupported transitions fail rather than
guessing whether the physical index is maintained or complete. Direct USABLE
without a source diff is refused for the same reason. This conservative subset
does not claim automatic round-trip transitions for every catalog state.

Table/View identity, quoting and schema decoration are preserved. State changes
can accompany supported table/view column changes; the source and target models
are not mutated. No IF EXISTS, automatic rebuild, transaction or execution is
introduced. Native syntax follows the module's existing common 4.1+ target;
server-state validation, concurrent rebuild activity and live legacy-version
compatibility remain unverified.

Reference: [Phoenix ALTER INDEX semantics](https://phoenix.apache.org/docs/grammar/).

## Default-family metadata and explicit index rebuild

For known Phoenix 4.1+ server versions, table header retrieval now supplements
the existing JDBC getTables result with one set-based SYSTEM.CATALOG query for
DEFAULT_COLUMN_FAMILY. It binds the returned table names, tenant IDs and schema
names, including global/default-schema objects, then matches the full identity.
Forty tables still require only one JDBC header call and one catalog SELECT.
The JDBC header result is closed before the supplemental query. No extra query
is issued for empty sets, views, pre-4.1 servers or unknown versions. Those
older/unknown paths retain their prior metadata limitations.

A catalog NULL remains unknown/unset rather than guessing a default family.
Missing requested headers, duplicates, blank family values and query/permission
failures are surfaced. Existing specifics/XML/CREATE TABLE handling preserves
the recovered family and escapes its literal. Catalog visibility must include
the requested headers; concurrent table removal or visibility differences fail
instead of silently exporting incomplete metadata. No additional dependency or
per-table SELECT is introduced.

An existing Index attached to a Table or View can now be rebuilt explicitly:

```java
var operations = dialect.createSqlFactoryRegistry()
        .createSql(table.getIndexes().get("idx_events"), SqlType.REBUILD);
```

This generates `ALTER INDEX idx_events ON <parent> REBUILD`, preserving schema
decoration and identifier quoting. It does not recreate the table/index, update
the in-memory state, infer a rebuild from a state difference, or execute SQL.
There is no implicit IF EXISTS: a missing server index must fail visibly. Index
name and parent identity are required. This is synchronous native syntax; build
permissions, runtime availability, concurrent writes and costs are checked by
Phoenix when the caller executes it. General state transitions remain separate.

Local tests cover JDBC mocks, SQL, XML, version gates, failure/resource handling
and Table/View rebuild entry points. No Phoenix/HBase catalog query or rebuild
has been executed against a live server; pre-4.1 native rebuild compatibility
is not newly certified.

References: [Phoenix catalog column](https://github.com/apache/phoenix/blob/5.2/phoenix-core-client/src/main/java/org/apache/phoenix/jdbc/PhoenixDatabaseMetaData.java),
[ALTER INDEX](https://phoenix.apache.org/docs/grammar/).

## Value preservation and DML safety

Phoenix DATE/TIME and their unsigned names retain epoch milliseconds in scalar
JDBC reads/binds, Schema Row conversion, XML and SQL literals. They use a
Phoenix-specific converter and a full UTC `yyyy-MM-dd HH:mm:ss.SSS` representation,
including for TIME because Phoenix TIME also stores an epoch value. Local Java
date/time inputs and strings without an offset are interpreted as UTC; inputs
with an offset retain the same instant. Sub-millisecond values and invalid inputs
fail explicitly. Set the Schema/Catalog product identity when serializing rows
so XML readers can resolve Phoenix; detached table XML without product identity
cannot infer vendor-specific DATE/TIME semantics. Arrays are outside this change.
Existing explicit Column converters/formatters retain precedence.

The shared Row XML writer now honors the existing optional dialect column-value
converter/formatter hook. Without an opt-in its serialization behavior is unchanged.
This also applies to DuckDB's existing opt-in; no database-specific condition is
added to core. XML structure, public method signatures and dependencies are unchanged.

Generic UPDATE and MERGE requests (including table/row and diff entry points)
now fail with an actionable UnsupportedOperationException. Phoenix does not have
these SQL statements. The implementation does not infer an UPSERT that could
insert an absent row, discard an optimistic-lock condition or change conflict
semantics. Ordinary INSERT continues to emit UPSERT, and DELETE remains available.

`SELECT_FOR_APP` supports `_row` as LIMIT and `_offset` as OFFSET. Both are bound
parameters, omitted for count queries; missing values leave the query unpaged.
Nonnegative integral values are required, including zero for an empty LIMIT.
OFFSET requires Phoenix 4.8+, selected by the ordinary resolver. Older/unknown
versions allow LIMIT and a zero offset, and reject nonzero OFFSET at template
evaluation. Limit and sequence capability flags now reflect the native factories.
The default legacy dialect still does not assume OFFSET or DEFAULT support.

Phoenix 5.3+ resolves VARBINARY_ENCODED to the shared VARBINARY/byte[] model while
preserving the native name through XML and DDL. Ordinary VARBINARY keeps its
existing default mapping. Encoded binary SQL literals accept byte[] and emit
`X'...'`; JDBC uses VARBINARY and leaves physical encoding to Phoenix. Earlier
versions and encoded arrays fail explicitly. The 5.3.1 multi-row VALUES boundary
and 4.9 DEFAULT boundary remain intact. Phoenix4_8/Phoenix5_3 and holder constants
are additive API; the existing version classes inherit from the nearer version.

These changes are verified locally, not against a Phoenix/HBase cluster. General
view reconstruction, additional catalog/HBase properties, functional index and
index-state migrations, native bulk transport, transaction guarantees and atomic
replacement remain separate work requiring catalog/driver or migration design
verification. No external database or schema/data migration is performed here.

References: [DATE/TIME](https://phoenix.apache.org/docs/datatypes/),
[4.8 OFFSET](https://blogsarchive.apache.org/phoenix/entry/announcing_phoenix_4_8_released),
[VARBINARY_ENCODED 5.3](https://phoenix.apache.org/docs/features/varbinary-encoded/),
[SQL grammar](https://phoenix.apache.org/docs/grammar/).

## Index diffs for tables and views

The ordinary Table/View diff entry point now delegates index additions,
deletions and replacements to the same native CREATE/DROP factories used by
standalone index operations. DROP includes Phoenix's required ON parent-table
or parent-view clause. Changed indexes are dropped before column operations
and recreated after new columns, avoiding generic DROP syntax and incorrect
replacement order. LOCAL/GLOBAL, families, descending keys, INCLUDE, salting,
state validation and existence options retain the native factory semantics.
Existing active-index recreation restrictions still apply.

Unsupported modeled index types, predicates and blank names now fail rather
than silently losing declarations. Constraint-owned additions that yield no
CREATE operation require a separate migration. A target index still referencing
a column explicitly deleted by the diff fails; remove or update that index in
the target model. Phoenix can automatically drop indexes or remove covered
columns when their data-table columns are removed, so the target Schema model
must describe the intended final indexes. This validation does not discover
unmodeled server indexes or parse functional-key expressions.

Index changes can accompany supported column changes for both tables and
views. View query/property/key restrictions below are unchanged. Replacements
use DROP/CREATE rather than in-place ALTER INDEX or state-transition inference;
they are not atomic, and a failed recreation may leave the index absent.
Phoenix/HBase checks permissions, dependencies, index-build state and tenant
visibility at execution. No cluster is contacted by SQL generation.

The module's legacy, 4.9 and 5.3.1 paths are tested locally. This does not
certify LOCAL or other features on older servers. No existing signatures,
configuration/XML, dependencies, metadata queries or resolver boundaries are
changed by this batch. Live cluster tests remain unperformed.

References: [Phoenix index grammar](https://phoenix.apache.org/docs/grammar/),
[index removal](https://phoenix.apache.org/docs/single-page/).

## DEFAULT version boundary

Column DEFAULT declarations require Phoenix 4.9+. Version resolution now selects
the legacy Phoenix dialect before 4.9 (also for unknown versions), Phoenix4_9
from 4.9 through 5.3.0, and Phoenix5_3_1 from 5.3.1 onward. The 5.3.1 boundary
for multi-row VALUES is unchanged. Phoenix5_3_1 inherits the nearer 4.9 dialect.
This supersedes earlier batch statements below about an unchanged resolver.

Use the ordinary resolver with the actual server version:

```java
Dialect dialect = DialectResolver.getInstance()
        .getDialect("Apache Phoenix", 4, 9, 0);
```

Existing method signatures and XML formats are unchanged. Phoenix4_9,
DialectHolder.defaultDialect4_9 and Phoenix.supportsColumnDefault() are additive.
The legacy defaultDialect deliberately refuses DEFAULT rather than guessing
a modern server. Existing users explicitly selecting that dialect for DEFAULT
must select a known 4.9+ server version instead. Versions without DEFAULT still
generate ordinary column definitions. Metadata readers remain inherited and
their existing runtime catalog/version fallbacks are unchanged.

CREATE TABLE and ALTER ADD share validation and emit NULL/NOT NULL before
DEFAULT according to Phoenix's parser grammar, without rewriting the model.
Column ON UPDATE and CHECK declarations now fail instead of being ignored or
emitted as unsupported syntax. View-specific DEFAULT/constraint limitations
remain as documented below. Expression validity, constant-expression rules,
types and actual execution remain Phoenix's responsibility; no expression
parser, dependency, new setting or database connection is added.

References: [4.9 release](https://blogsarchive.apache.org/phoenix/entry/announcing_phoenix_4_9_released),
[column grammar](https://github.com/apache/phoenix/blob/master/phoenix-core-client/src/main/antlr3/PhoenixSQL.g).

## ALTER VIEW columns

The standard table-diff entry point also supports View column additions and
deletions, emitting native ALTER VIEW rather than ALTER TABLE or a view rebuild:

```java
View original = new View("extended");
original.setStatement("SELECT * FROM base WHERE id = 100");
Table target = original.clone();
target.getColumns().add(new Column("extra").setDataType(DataType.VARCHAR));
var operations = dialect.createSqlFactoryRegistry()
        .createSql(original.diff(target), SqlType.ALTER);
```

ADD uses the shared CREATE VIEW column validator/type renderer, with nullable
non-key columns, optional column families and arrays. ADD IF NOT EXISTS and
DROP COLUMN IF EXISTS follow the existing factory options. Both baseline
common 4.1+ syntax and the existing 5.3.1 dialect use the same implementation.
No configuration, XML, dependency or resolver change is required. The new
factory and protected rendering hooks in PhoenixAlterTableFactory are additive.

Column-only diffs of metadata-derived views can be altered without supplying a
complete CREATE definition: the existing view is changed, not recreated.
The metadata-only recreation guard remains in place for CREATE. An unchanged
view emits no operations. Changes to SELECT/definition, names, properties,
constraints/keys, existing column types or column names fail explicitly; these
require a separate native migration. A modeled primary-key column cannot be
dropped. Mixed unsupported changes fail instead of returning a partial plan.
Index additions/deletions/replacements use the shared native path described above.

The factory does not parse SELECT or traverse the base/view hierarchy. Phoenix
must validate WHERE-column dependencies, inherited keys missing from the model,
base/child view rules, permissions and column-family conflicts. Dropping a view
column does not delete underlying data according to Phoenix's ALTER semantics;
no additional data operations or commits are generated. Multiple ALTER statements
are not promised to be atomic. Live cluster compatibility remains unverified.

Reference: [Phoenix ALTER and CREATE VIEW](https://phoenix.apache.org/docs/grammar/).

## User-authored views with additional columns

A user-authored view may now declare nullable non-key columns through the
existing Schema model:

```java
View view = new View("extended");
view.setStatement("SELECT * FROM base WHERE id = 100");
view.getColumns().add(new Column("extra").setDataType(DataType.VARCHAR));
var operations = dialect.createSqlFactoryRegistry().createSql(view, SqlType.CREATE);
```

Here `view.columns` lists only newly declared columns, not inherited columns
from the base. The factory emits native column definitions before AS SELECT,
reusing table type/array and column-family rendering, quoting and IF NOT EXISTS.
There is no new configuration or XML field. Both the baseline common 4.1+
syntax and the existing 5.3.1 dialect use this path.

Metadata-derived views remain guarded: VIEW_STATEMENT alone cannot distinguish
inherited and declared columns. Such views still require a complete executable
CREATE VIEW in `definition`. Do not remove the metadata-only marker to bypass
this requirement. Mapped HBase views, constraints/primary-key extensions, NOT
NULL, DEFAULT, CHECK, ON UPDATE, identity and computed declarations require
complete native DDL; incomplete modeled requests fail explicitly. Parent-query
restrictions, column-name conflicts and actual updatability are checked by
Phoenix, not by parsing the SELECT in this factory. Local XML/SQL tests do not
establish live cluster compatibility.

Reference: [Phoenix CREATE VIEW](https://phoenix.apache.org/docs/grammar/).

## Mutable/immutable ALTER

The existing table-diff entry point now supports changing
`PhoenixDdlOptions.IMMUTABLE_ROWS` via `ALTER TABLE ... SET IMMUTABLE_ROWS=TRUE`
or `FALSE`. It shares boolean validation with CREATE; mixed-case booleans are
accepted and equivalent values produce no SQL. Removing the key from a model
that explicitly contained it fails: set true or false to state the intended
server value. No implicit reset is inferred.

This applies to the baseline common 4.1+ syntax and the existing 5.3.1 dialect;
resolver boundaries, signatures, dependencies and Schema XML are unchanged.
Changes may be combined with supported column additions/deletions. Salting,
tenant mode and default-family alterations remain explicitly unsupported.
IMMUTABLE_ROWS changes the application's write/index-maintenance contract;
the caller must ensure its writes satisfy the selected mode. Local XML/diff/SQL
tests do not establish live server or transaction-provider compatibility.
Reference: [Phoenix ALTER and CREATE INDEX](https://phoenix.apache.org/docs/grammar/).

## Delete-all and explicit locking

`SqlType.TRUNCATE` now emits Phoenix `DELETE FROM <table>` through the existing
factory entry point, preserving optional schema decoration and quoting. This is
DML, subject to the connection's commit mode, transaction configuration and
normal deletion costs; it does not truncate the underlying HBase table or add
an implicit commit. Both the baseline dialect (common 4.1+ syntax) and the
existing 5.3.1 dialect use this path. Resolver boundaries are unchanged.

`SqlType.LOCK` now throws UnsupportedOperationException instead of generating
unsupported LOCK TABLE SQL. Transaction isolation must be designed using the
chosen Phoenix transaction provider and connection; no table lock is emulated.
This is an intentional failure-behavior correction with no signature/XML change.
Local tests cover both dialects and direct/diff locking entry points. Live
HBase/Phoenix execution remains unverified.
Reference: [Phoenix SQL grammar](https://phoenix.apache.org/docs/grammar/).

Phoenix is the next enhancement target after the Docker-free SAP ASE work.
Changes in this batch stay in sqlapp-core-phoenix; public APIs, Schema XML,
dependencies and resolver boundaries are unchanged.

## Initial confirmed fixes

- DECIMAL maximum precision and scale are 38 instead of 31. Existing default
  precision/scale are preserved. Tests cover DECIMAL(38,38) through type mapping,
  Schema XML and SQL generation on the baseline and 5.3.1 dialects.
- CHAR charset registration uses UTF-8 instead of UTF-16, matching Phoenix's
  documented encoding. This does not expand CHAR to multibyte characters:
  Phoenix CHAR supports single-byte characters.
- Aggregate SQL uses COUNT rather than COUNT_BIG. Phoenix COUNT already returns
  a Java long according to its function reference.
- Current-value expressions use CURRENT_DATE() and CURRENT_TIME(). The datetime
  expression uses CURRENT_DATE(), whose Phoenix DATE includes time. Timestamp
  uses CAST(CURRENT_DATE() AS TIMESTAMP); this preserves the source's millisecond
  precision and does not invent nanosecond clock precision. DATE/TIME/TIMESTAMP
  registered expressions are tested on the legacy and modern dialects.
  DEFAULT DDL is gated at 4.9 as described above.

Reference: [Apache Phoenix data types](https://phoenix.apache.org/docs/datatypes/).
Function reference: [Apache Phoenix SQL functions](https://phoenix.apache.org/docs/functions/).
These are specification-backed local tests, not HBase/Phoenix server tests.
No container or external database was used.

## Native types and DDL batch

- UNSIGNED_FLOAT, UNSIGNED_DOUBLE, UNSIGNED_DATE, UNSIGNED_TIME and
  UNSIGNED_TIMESTAMP retain their native encoding names while using shared
  scalar DataTypes. Ordinary signed types keep their existing default mapping.
- INTEGER ARRAY / VARCHAR[] and other supported scalar element types map to
  the shared element type plus arrayDimension=1 and survive XML-to-DDL.
  Multidimensional and VARBINARY arrays fail explicitly. A modeled upper bound
  is emitted as ARRAY[n]. Type conversion also reads capacities from ARRAY[n]
  and element[n] declarations. Unknown custom element types are rejected.
- Unbounded VARCHAR emits VARCHAR rather than VARCHAR(0).
- CREATE TABLE honors createIfNotExists. An optional column specifics entry
  `phoenix.columnFamily` emits separately quoted family/column identifiers.
  Primary-key columns cannot have a family, and an ARRAY key must be last.
  The Phoenix column reader recovers families when the driver exposes them.
- CREATE SEQUENCE uses the shared Sequence model for start, increment,
  MINVALUE/MAXVALUE, CYCLE and CACHE, in Phoenix clause order. Generic sequence
  data types do not produce an unsupported AS clause. Out-of-range BIGINT
  values, zero increments and contradictory bounds fail explicitly. CREATE and
  DROP honor the existing existence-clause options.
- CREATE INDEX retains descending keys and INCLUDE columns. DROP INDEX emits
  the required ON parent-table clause. Missing parents/keys and UNIQUE
  secondary indexes fail explicitly; Phoenix secondary indexes cannot provide
  the requested uniqueness guarantee.

Example using existing Schema APIs:

```java
Column payload = new Column("payload").setDataType(DataType.VARCHAR);
payload.getSpecifics().put("phoenix.columnFamily", "details");
table.getColumns().add(payload);
Index index = new Index("idx_events");
index.getColumns().add("created_at", Order.Desc);
index.getIncludes().add("payload");
table.getIndexes().add(index);
```

ARRAY support is documented from Phoenix 3.0/4.0; sequence bounds and cycling
from 3.1/4.1. This batch targets the common 4.1+ syntax and the existing 5.3.1
dialect. It does not establish support for earlier servers. The resolver split
is unchanged. No dependencies, existing public signatures or XML formats changed;
new factories and the optional specifics key are additive.

Specification references:
[SQL grammar](https://phoenix.apache.org/docs/grammar/),
[ARRAY](https://phoenix.apache.org/docs/array-type/),
[sequences](https://phoenix.apache.org/docs/sequences/),
[Apache parser](https://github.com/apache/phoenix/blob/master/phoenix-core-client/src/main/antlr3/PhoenixSQL.g).

Local tests validate conversion, XML, generated SQL and failure boundaries.
Catalog behavior, JDBC binding of native arrays/types and execution on an
HBase/Phoenix cluster remain unverified. Later batches below add table/index
property recovery, basic LOCAL indexes and view-source preservation. Complete
view reconstruction and advanced index cases remain outside this pass.

## Sequence metadata batch

PhoenixCatalogReader and PhoenixSchemaReader extend the JDBC readers and add a
dedicated sequence reader. Column and primary-key reader additions are described below.
SYSTEM."SEQUENCE" is queried once for the requested object set, including start,
increment, cache, bounds and cycling. No NEXT VALUE or CURRENT VALUE expression
is executed. CURRENT_VALUE is not interpreted as the last value actually used:
client caching makes that representation unreliable.

The legacy SQL omits physical MIN_VALUE/MAX_VALUE/CYCLE_FLAG references. These
columns are selected only for known versions 3.1+, 4.1+ and 5+. Unknown versions
use the conservative legacy resource. Resolver selection itself is unchanged.
Tenant IDs occupy the Schema model catalog identity, consistent with Phoenix
JDBC's catalog convention. Without a catalog filter, only global sequences
(TENANT_ID IS NULL) are selected. Explicit catalog filters select tenant IDs;
recreation of such sequences requires the appropriate tenant connection.
Automatic tenant-connection discovery is outside this change.

Tests cover version boundaries, query-template evaluation and bound filters,
40 objects in one query, tenant/schema identity, unknown numeric metadata,
cache overflow and metadata-to-XML-to-CREATE. Catalog permissions, driver labels,
tenant connection visibility and actual recreation remain live-server checks.
No external database was accessed.

Sources: [sequence catalog and version history](https://phoenix.apache.org/docs/sequences/),
[Apache metadata catalog fields](https://apache.googlesource.com/phoenix/+/2cad5cd03e7c838f7e875c1ca9ed9bacfec7043b/phoenix-core/src/main/java/org/apache/phoenix/jdbc/PhoenixDatabaseMetaData.java),
[catalog/tenant labels](https://issues.apache.org/jira/browse/PHOENIX-7147).

## Native ALTER and validation batch

- ALTER TABLE ADD uses Phoenix's ADD [IF NOT EXISTS] column-definition syntax,
  without the unsupported COLUMN token. DROP uses DROP COLUMN [IF EXISTS].
- CREATE and ALTER share column-family name rendering. Family and column names
  are separately quoted and the existing specifics entry survives XML.
- Primary-key drops, table/column renames, column type/modification operations
  and constraint ALTER fail explicitly. Primary-key extension needs a separate
  migration design; this batch does not silently issue generic ALTER SQL.
- Generic IDENTITY/computed-column declarations fail instead of generating
  unsupported clauses or discarding formulas. Use explicit sequences/view
  expressions. Negative sequence caches fail explicitly.

The native ALTER grammar was checked against the Apache parser linked above.
Tests use table diffs and XML, including quoted family identifiers and both
existence options. No database or container was used.

## Placement properties and sized ARRAY batch

Optional `PhoenixDdlOptions` keys in the existing specifics map configure CREATE:

| Key | Objects | Value |
| --- | --- | --- |
| phoenix.saltBuckets | Table / Index | 1..256 for tables, 0..256 for indexes; index 0 disables inherited salting |
| phoenix.immutableRows | Table | true / false |
| phoenix.multiTenant | Table | true / false |
| phoenix.defaultColumnFamily | Table | Nonblank string, emitted as an escaped SQL literal |

Absent keys emit no extra properties. No server settings or defaults are changed.
Malformed values fail rather than being inserted as raw SQL. ALTER of
IMMUTABLE_ROWS is supported as described above; other property changes fail
explicitly. Enabling
multi-tenancy still requires an appropriate tenant key and connection design.
Header readers now recover salting, immutable-row and multi-tenant properties
where exposed. DEFAULT_COLUMN_FAMILY recovery remains outside this pass.

Index key and INCLUDE names now reuse column-family rendering from CREATE/ALTER
when the parent table contains the modeled column. Declared array capacities
such as VARCHAR ARRAY[10], INTEGER[20] and sized decimal arrays survive type
conversion, XML and column DDL. Capacity is represented by the existing Schema
array upper bound; Phoenix does not currently enforce declared array length.
Invalid capacities, integer overflow and nonstandard lower bounds fail.

Tests cover table/index salt boundaries, invalid property values, SQL literal
escaping, property ALTER failure, family-qualified keys/INCLUDE and sized arrays
on both dialect versions. Existing APIs and XML formats remain unchanged. The
new property constants are additive. These changes use the common 4.1+ syntax;
they do not establish support on older servers or replace live execution tests.

Sources: [salting](https://phoenix.apache.org/docs/features/salted-tables/),
[table options](https://phoenix.apache.org/docs/grammar/),
[ARRAY semantics](https://phoenix.apache.org/docs/array-type/).

## Column and primary-key metadata batch

PhoenixTableReader/PhoenixViewReader keep JDBC header retrieval and use
PhoenixColumnReader. Phoenix's getColumns result already exposes COLUMN_FAMILY
and ARRAY_SIZE, so the reader adds no catalog SELECT. One getColumns call reads
the requested set and retains family specifics, array capacity, remarks, raw
default text and unknown octet length. Missing optional vendor fields permit
the older JDBC result format. JDBC ARRAY codes are recognized even when the
type name lacks the ARRAY suffix.

Columns with the same table identity and name in different families cannot
currently share a Schema column collection. Such collisions fail explicitly
instead of overwriting a column; supporting those schemas requires an identity
design beyond this batch. Negative/overflowing array capacities also fail.

PhoenixPrimaryKeyReader uses getPrimaryKeys once and retains Phoenix's optional
ASC_OR_DESC field. Composite keys are sorted by KEY_SEQ, independently of the
JDBC row order. Tenant/schema/table identities remain distinct. Invalid or
incomplete key sequences and inconsistent names/directions fail explicitly.
The table reader selects this reader on both existing dialect versions.

Tests exercise actual JDBC metadata entry points using mock connections and
result sets, including 40-table sets, result closure on failure, metadata-to-XML
and CREATE generation, optional-field fallback, family collisions and descending
composite keys. These are not live driver/server compatibility tests. No database
was contacted, and no additional dependencies or Schema XML fields were added.
The change depends on optional driver result fields, not newer physical catalog
columns, preserving the JDBC fallback on older supported versions.

Source: [Apache Phoenix JDBC metadata projections](https://apache.googlesource.com/phoenix/+/740ce13210cf6d876abfde52a58e10fa407b5018/phoenix-core/src/main/java/org/apache/phoenix/jdbc/PhoenixDatabaseMetaData.java).

## Index and view completion batch

On Phoenix 5.3.2, `PhoenixNativeDefinitions.read(connection, viewOrIndex)`
explicitly obtains a definition using SHOW CREATE TABLE and stores it in
the same model field used for recreation. It requires a global connection
and a global object. It queries only the selected object, since this
Phoenix command accepts one object name. The standard bulk readers do not
invoke it. Mapped HBase views require explicit native DDL. The 5.3.2 extractor
can return a self-referencing SELECT without the mapped columns, which the
API rejects. Other complex definitions remain unverified.
On the tested 5.3.2 deployment, SHOW CREATE TABLE returned error 1012 for
tenant-local views and function indexes even on their matching tenant
connections. Supply the native DDL explicitly and execute it on the intended
tenant connection.
JDBC exposes only client major/minor. Verify that the client and server are
5.3.2 before using the method.

Modeled CREATE VIEW rejects explicit Phoenix view or table options that its
SELECT-based renderer cannot preserve. Complete native view definitions use
the shared definition field and survive XML. The supplied statement controls
qualification, view properties and existence clauses.
The schema migration uses that definition when adding a view. Changing the
definition still requires an explicit migration. Views use the same set-based
index reader as tables. View creation emits attached indexes after the view,
using the same modeled or explicit native CREATE INDEX path as tables.

Catalog names containing parentheses are retained verbatim as opaque index
metadata. The XML round trip preserves the marker that blocks modeled CREATE.
Phoenix's physical key names can differ from the original expressions and
its index keys can include appended parent primary-key columns. Recreating
these indexes requires separately supplied native DDL.
Set `PhoenixCreateIndexFactory.NATIVE_DEFINITION` in the index specifics to a
trusted, complete CREATE INDEX statement for that index and its owner. The
statement takes precedence over catalog key names and is emitted verbatim,
including its own qualification and existence clauses. Retained indexes with
opaque or native definitions block modeled column deletion because their
dependencies cannot be inferred.

An observed zero bucket count on a LOCAL index is treated as no separate
salting clause, allowing metadata-to-DDL recreation. Positive LOCAL bucket
counts remain visible and cannot be recreated by the modeled CREATE factory.
GLOBAL zero still explicitly overrides inherited salting.

Index ALTER diffs accept the catalog state codes `x` as DISABLE and
`d` as UNUSABLE.
An explicit rebuild is still required to re-enable a disabled index.

Index metadata rejects blank index or owner names, out-of-range salt counts,
and duplicate column ordinals across keys and INCLUDE columns. These checks
apply to both existing catalog query paths without increasing query count.

PhoenixIndexReader reads SYSTEM.CATALOG index headers and columns in at most
two set-based queries. It does not call getIndexInfo once per table. Index name
sets use bound parameters; tenant/schema/index identity separates same-named
objects. Native colon-separated source names are decoded to source column names
and family specifics. Key columns and INCLUDE columns are separated using the
index column's COLUMN_FAMILY, matching Apache QueryUtil's interpretation.
ORDINAL_POSITION orders each group; SORT_ORDER preserves descending keys.

Index type and state are retained in phoenix.indexType and phoenix.indexState.
INDEX_TYPE is selected only from known 3.1+/4.1+/5+ catalogs, following
[PHOENIX-1038](https://issues.apache.org/jira/browse/PHOENIX-1038).
Unknown version/type cannot silently become a recreatable global index.
Known GLOBAL/LOCAL indexes generate native CREATE syntax. Unsalted global
indexes explicitly emit SALT_BUCKETS=0 so recreation does not inherit a salted
parent's setting. Non-active index recreation, local salting, unrepresentable
functional/internal keys and duplicate source names fail explicitly.

Table/view headers now preserve the optional JDBC IMMUTABLE_ROWS, SALT_BUCKETS
and MULTI_TENANT fields. No extra header query is added. PhoenixViewReader also
retains raw VIEW_STATEMENT when either text option is enabled and records
VIEW_TYPE. Both text options disabled permit header-only reads.

VIEW_STATEMENT is not a complete CREATE definition: it cannot distinguish
inherited columns from columns declared by the view or recreate mapped HBase
views. Metadata-derived views therefore require a complete user-supplied native
CREATE definition before recreation. User-authored views with a SELECT
statement can generate CREATE VIEW IF NOT EXISTS, optionally with additional
nullable columns as described above.
DROP VIEW honors IF EXISTS. Missing view source fails instead of returning
empty SQL. This intentionally does not claim general view recreation coverage.

Tests cover forty indexes in two queries, keys/INCLUDE/family/direction and
GLOBAL/LOCAL XML-to-DDL, empty sets, version/query-template boundaries, header
properties, view source preservation, complete native view DDL and explicit
recreation failures. Sources:
[Apache QueryUtil catalog queries](https://github.com/apache/phoenix/blob/master/phoenix-core-client/src/main/java/org/apache/phoenix/util/QueryUtil.java),
[native grammar](https://phoenix.apache.org/docs/grammar/).

## Completion boundary

JDBC table/view headers reject missing names and duplicate tenant/schema/name
identities in the existing single getTables call. Same-named objects in other
tenants or schemas remain distinct. Incomplete view headers and header-only
reads remain available, with the metadata-only marker retained through XML so
CREATE still requires a complete native definition. Local tests cover 40 table
and view headers, multiline/Unicode query retention, missing/blank source,
invalid identities and result closure on failure. Existing version paths,
public APIs, catalog queries, dependencies and XML structure are unchanged;
live driver and mapped/inherited-view reconstruction remain unverified.

Sequence metadata rejects missing names, duplicate tenant/schema/name
identities, zero increments, negative caches, reversed bounds and explicit
starts outside known bounds. Negative increments, boundary starts and zero
caches remain representable. Missing numeric metadata is still retained as
unknown instead of being replaced by inferred counters. All existing version
paths use the same reader checks; legacy/modern catalog SQL and resolver
selection are unchanged. Local tests cover descending XML/DDL recreation,
invalid metadata, 40 sequences in one query and duplicate rejection. No
sequence values are allocated, and live-server behavior remains unverified.

Primary-key metadata requires nonblank owning table and column names. Repeated
column names, inconsistent constraint names, duplicate positions and sequence
gaps fail explicitly. Unnamed primary keys remain supported. Column identity
is case-sensitive, preserving distinct quoted names such as Id and id. These
checks apply to the shared Phoenix reader on all existing resolver paths and
use the existing single JDBC metadata call; no catalog columns were added.
Mock tests verify resource closure on failure and separation across tenants
and schemas. Live driver behavior remains unverified.

Index metadata interprets SYSTEM.CATALOG SORT_ORDER as Phoenix serialization
IDs: 1 is DESC and 2 is ASC. A missing value retains the default ASC; other
non-null values fail explicitly instead of silently reversing or inventing a
key direction. This corrects the former comparison-sign interpretation (-1).
The reader still uses two bulk queries. Local tests cover the 3.0, 4.0, 4.1 and
5.3 version paths, XML/DDL retention and invalid IDs. Live catalog execution on
these versions remains unverified; no catalog columns or resolver paths changed.
Reference: [Apache Phoenix SortOrder](https://github.com/apache/phoenix/blob/master/phoenix-core-client/src/main/java/org/apache/phoenix/schema/SortOrder.java).

The Docker-free enhancement pass is complete within the tested scope. See
[ENHANCEMENT_STATUS.md](ENHANCEMENT_STATUS.md) for remaining live-server checks
and features deliberately outside this pass. The existing 5.3.1 resolver split,
public method signatures, dependencies and Schema XML format are unchanged.
PhoenixUtilsTest still contains an empty legacy test; its presence is not
evidence of metadata behavior coverage.
