# Apache Phoenix dialect enhancement

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
