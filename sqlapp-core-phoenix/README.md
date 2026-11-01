# Apache Phoenix dialect enhancement

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
  registered default expressions and XML-to-column-DDL are tested in both
  dialect versions.

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
Malformed values fail rather than being inserted as raw SQL. ALTER of these
properties currently fails explicitly; this batch covers CREATE. Enabling
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
CREATE definition before recreation. User-authored simple views with a SELECT
statement and no modeled columns can generate CREATE VIEW IF NOT EXISTS.
DROP VIEW honors IF EXISTS. Missing view source fails instead of returning
empty SQL. This intentionally does not claim general view recreation coverage.

Tests cover forty indexes in two queries, keys/INCLUDE/family/direction and
GLOBAL/LOCAL XML-to-DDL, empty sets, version/query-template boundaries, header
properties, view source preservation, complete native view DDL and explicit
recreation failures. Sources:
[Apache QueryUtil catalog queries](https://github.com/apache/phoenix/blob/master/phoenix-core-client/src/main/java/org/apache/phoenix/util/QueryUtil.java),
[native grammar](https://phoenix.apache.org/docs/grammar/).

## Completion boundary

The Docker-free enhancement pass is complete within the tested scope. See
[ENHANCEMENT_STATUS.md](ENHANCEMENT_STATUS.md) for remaining live-server checks
and features deliberately outside this pass. The existing 5.3.1 resolver split,
public method signatures, dependencies and Schema XML format are unchanged.
PhoenixUtilsTest still contains an empty legacy test; its presence is not
evidence of metadata behavior coverage.
