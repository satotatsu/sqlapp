# Cloud Spanner dialect

This module targets GoogleSQL, using the existing single-version resolver.
It does not implement the Spanner PostgreSQL interface.

The local enhancement pass and final audit are summarized in
[ENHANCEMENT_STATUS.md](ENHANCEMENT_STATUS.md), including a support matrix,
compatibility boundaries and remaining live-server checks. CREATE TABLE renders
known INTERLEAVE relationships and rejects ambiguous metadata. Optional
newer column metadata is obtained from `col.*` without explicitly naming newer
physical catalog columns. UNIQUE index names honor schema decoration. Live
service compatibility is not certified by the local tests.

## Interleaved table creation

Table metadata retains parent_table_name, on_delete_action and optional
interleave_type from TABLES.*. CREATE emits INTERLEAVE IN PARENT with CASCADE or
NO ACTION, or INTERLEAVE IN without an ON DELETE clause. Older catalogs without
INTERLEAVE_TYPE are supported when a known ON DELETE action unambiguously
identifies IN PARENT. A parent with neither type nor delete metadata is ambiguous
and fails; unknown values, self-parenting and IN plus ON DELETE also fail.
Metadata explicitly reporting IN PARENT must include its ON DELETE value.

For a user-authored relationship, specify the parent and relationship kind:

```java
table.getSpecifics().put(SpannerCreateTableFactory.PARENT_TABLE_NAME, "Parents");
table.getSpecifics().put(SpannerCreateTableFactory.INTERLEAVE_TYPE, "IN PARENT");
// Optional for user-authored IN PARENT; NO ACTION is the default.
table.getSpecifics().put(SpannerCreateTableFactory.ON_DELETE_ACTION, "CASCADE");
```

Parent names are quoted and use the child's schema when schema decoration is
enabled. Cross-schema parent naming and primary-key prefix/type compatibility
remain server verification requirements. In a CREATE table collection, modeled
interleave parents and FK dependencies precede children; cycles and duplicate
identities fail. Parents outside the collection must already exist. Calls for
ordinary tables without interleaving keep the existing shared ordering path.
INTERLEAVE changes through ALTER remain unsupported and fail explicitly.

Table OPTIONS now use the native comma separator, including when combined with
INTERLEAVE. Tests read 40 legacy/current table headers in one query, round-trip
Schema XML, generate parents first, preserve deletion behavior, reject cycles
and retain FK ordering. No new physical catalog columns, per-table queries,
module dependencies, existing signatures, XML structure or resolver boundary
are added. Actual service/older-emulator behavior remains unverified.

Sources: [TABLES metadata](https://docs.cloud.google.com/spanner/docs/information-schema),
[GoogleSQL CREATE TABLE](https://docs.cloud.google.com/spanner/docs/reference/standard-sql/data-definition-language).

## Column migrations

Use the ordinary table-diff entry point; no additional configuration is needed:

```java
Table target = original.clone();
target.getColumns().get("quantity").setDefaultValue("0");
var operations = dialect.createSqlFactoryRegistry()
        .createSql(original.diff(target), SqlType.ALTER);
```

The native ALTER factory supports ADD COLUMN (including optional IF NOT EXISTS),
DROP COLUMN, default-only changes using SET DEFAULT / DROP DEFAULT, and
non-key nullability changes as described below.
Scalar STRING/BYTES length changes are also supported.
ADD reuses the same type, generated-expression and options renderer as CREATE.
NOT NULL additions require a scalar column with a default or a stored expression;
NOT NULL array additions are rejected. Identity additions and key-column changes
require a separate migration. DROP COLUMN does not emit unsupported IF EXISTS.

Other column changes and table-level differences (including primary-key/UNIQUE constraints,
placement options and renames) fail explicitly rather than falling back
to generic SQL or losing changes. Index changes use the native factories as
described below. DEFAULT changes on identity, generated or ON UPDATE columns also
fail explicitly. Dependency checks, expression type checks and asynchronous
backfill remain the service's responsibility; no online/atomic migration or
data-validation guarantee is implied by SQL generation.

CREATE and ADD render NOT NULL before DEFAULT and parenthesize default
expressions. Existing parenthesized expressions remain valid; the Schema model
is not rewritten. This targets the current GoogleSQL service grammar; the
existing resolver and older metadata-reader paths are unchanged. Compatibility
with older emulators is unverified.

Reference: [GoogleSQL DDL](https://docs.cloud.google.com/spanner/docs/reference/standard-sql/data-definition-language).

## STRING/BYTES length changes

Change `length` on a non-key scalar VARCHAR (GoogleSQL STRING) or VARBINARY
(BYTES) column in the target Table and use the ordinary diff entry point.
Expansion and shrinking generate one typed ALTER COLUMN; target DEFAULT and
NOT NULL are restated so a length-only change does not clear them. Length,
nullability and default changes can be combined in one statement.

Lengths must be explicit and positive, within the shared dialect type limits.
Null, zero, negative and out-of-range lengths fail rather than selecting a
default size or silently falling back to a smaller native type. For MAX, use
the native representation resolved by `dialect.setDbType("STRING(MAX)", null,
null, targetColumn)` (or BYTES(MAX)); it retains the canonical modeled MAX
length. The factory recognizes these native/MAX name differences while
preserving the shared scalar type. It does not treat other raw type-name
changes as length changes or mutate the caller's model.
The shared STRING(MAX) CREATE format is also corrected: it no longer appends
a second numeric length after MAX, including when used as an array element.

Array element lengths, primary-key lengths, STRING/BYTES conversions, generated,
identity and ON UPDATE columns remain separate migrations. Server-side checks
still apply to dependent objects and existing values. Shrinking may require
background validation and fail when stored values are too long; no truncation,
conversion, preflight query, implicit commit or data rewrite is performed.
This uses current GoogleSQL syntax without changes to APIs, XML, dependencies,
resolver selection or metadata queries. Live-service and older-emulator
compatibility remain unverified.

Reference: [Spanner schema updates](https://docs.cloud.google.com/spanner/docs/schema-updates).

## Commit timestamp option changes

Use the existing specifics flag on a target TIMESTAMP column and the normal
table-diff entry point:

```java
Table target = original.clone();
target.getColumns().get("updated_at").getSpecifics()
        .put(SpannerSqlBuilder.ALLOW_COMMIT_TIMESTAMP, true);
var operations = dialect.createSqlFactoryRegistry()
        .createSql(original.diff(target), SqlType.ALTER);
```

An option-only change on a non-key scalar TIMESTAMP emits
`ALTER TABLE ... ALTER COLUMN ... SET OPTIONS (allow_commit_timestamp=true)`.
Changing true to false, or removing a previously true flag, emits the native
`allow_commit_timestamp=null` reset rather than false. Absent/false and
case-only true/TRUE changes are semantic no-ops. CREATE and ALTER share strict
true/false validation; malformed values no longer silently become false. The
new SpannerSqlBuilder.allowCommitTimestamp accessor is additive; existing
configuration keys, public signatures, XML, dependencies and resolver selection
are unchanged.

The statement does not restate DEFAULT, NOT NULL, type or unrelated options.
Mixed changes to that same column, primary-key columns, arrays, generated,
identity and ON UPDATE columns require separate migrations and fail explicitly.
Different supported columns can still be changed in the same table diff.
Spanner validates existing values when enabling commit timestamps, including
the requirement that values be in the past. The generator does not inspect or
rewrite data, send mutations, or promise an atomic multi-statement change.
This uses current GoogleSQL syntax; live-service and older-emulator validation
remain unperformed.

Reference: [Spanner commit timestamps](https://docs.cloud.google.com/spanner/docs/commit-timestamp?hl=en).

## View replacement

`SqlType.ALTER` for a View now emits native CREATE OR REPLACE VIEW using the
same statement/security renderer as CREATE. An ordinary View diff can change
the query statement or SECURITY_TYPE (INVOKER/DEFINER), while keeping its
identity and other modeled properties unchanged. Existing factory options
control identifier quoting and schema decoration. No new configuration or
Schema XML field is required. A direct View/ALTER request explicitly generates
replacement; a diff with no effective query/security change emits no SQL.
Case-only security changes and absent versus explicit INVOKER do not replace.

Absent SECURITY_TYPE uses the existing safe INVOKER default. Invalid values now
fail in both modeled CREATE and replacement rather than silently becoming
INVOKER. Empty/blank query statements also fail instead of returning empty SQL.
CREATE still accepts a complete raw native definition as before. Replacement
requires a statement without a raw definition; arbitrary CREATE text is not
rewritten. Diffs that change names, schemas, raw definitions, result-column
models, constraints or unrelated properties require separate migration work
and fail explicitly. SELECT validity, strict name resolution, result names and
dependencies are validated by Spanner, not by parsing SQL in the generator.

Spanner's view-management documentation states that existing grants must be
granted again after replacing a view. Returned SQL does not recreate grants,
query permissions or execute against a database. Review the privilege impact
before applying it. The replacement factory and protected rendering hooks are
additive; existing method signatures, dependencies and resolver selection are
unchanged. This targets current GoogleSQL; live-service/older-emulator checks
remain unperformed.

Reference: [Create and manage Spanner views](https://docs.cloud.google.com/spanner/docs/create-manage-views).

## Bulk index option metadata

When the existing INDEX_OPTIONS availability probe succeeds, index reading now
retains the native factory's tree_depth, num_leaves, num_branches, disable_search,
sort_order_sharding, locality_group and columnar_policy options in the existing
specifics map, in addition to the shared vector distance enum. Explicit false
values survive. XML-to-CREATE tests cover vector tree/search flags and native
placement/search SQL generation.

Reading any nonempty requested set uses three set-based queries: index rows,
the existing option-table availability probe and one option-row query. It
does not add per-index queries. If INDEX_OPTIONS is absent, the older two-query
path remains; empty index results now skip both option queries. No newer
physical catalog columns or numeric version assumptions are added, preserving
the runtime availability boundary and existing resolver selection.

Unknown distance values and malformed/out-of-range integer or boolean metadata
fail explicitly instead of being coerced to a different setting. Integer options
currently share the CREATE factory's Java Integer representation; wider values
require a future model/factory change. Null values do not invent defaults.
No existing API signatures, Schema XML fields or dependencies change.

Tests use mocked result rows, not live catalog SQL. Permissions, actual JDBC
OPTION_VALUE formatting and option exposure on older services/emulators still
need live verification. This does not claim full index reconstruction:
optional catalog layout fields are handled as described below, but unmodeled
options remain outside this reader. VECTOR recreation needs complete column metadata, including fixed
embedding length and nullability. No database was contacted.

Reference: [GoogleSQL index options](https://docs.cloud.google.com/spanner/docs/reference/standard-sql/data-definition-language).

## Bulk foreign-key metadata

The foreign-key reader now projects the existing KEY_COLUMN_USAGE ordinal
column and assembles each key by position rather than depending on JDBC row
order. Referencing/referenced column pairs retain their correspondence even
when the referenced unique key uses a different order. Catalog/schema/name
identity keeps same-named constraints in different schemas separate. The
existing shared model assembler still binds the referenced table and columns.

Missing, duplicate, nonpositive, out-of-range or gapped ordinals, duplicate
column identities and inconsistent owners/headers/referenced tables fail
explicitly. ENFORCED must be YES or NO; an unknown value no longer becomes an
enforced constraint. SIMPLE/NO ACTION update semantics and the supported delete
actions are read explicitly; unknown actions fail instead of becoming defaults.
The existing SPANNER_STATE specifics value is retained, not treated as proof of
completed validation.

No per-object queries are added: a mocked 40-key composite set is read with one
query and recreated through Schema XML and native CREATE. Query-template tests
also check bound filters. The query adds only an alias for ORDINAL_POSITION,
which it already referenced for ordering, so no newer catalog column/version
split is introduced. Existing signatures, dependencies, XML formats and resolver
selection are unchanged. This targets GoogleSQL metadata; actual catalog
permissions, driver formats, recreation and historical service/emulator versions
remain live-server checks. No external database was used.

Reference: [Spanner information schema](https://docs.cloud.google.com/spanner/docs/information-schema).

## Nullability changes

Set the existing column's `notNull` property in the target Table and use the
same diff entry point. The factory emits typed `ALTER COLUMN`, using the native
type renderer and restating the target DEFAULT expression. This is required:
GoogleSQL removes DEFAULT, ON UPDATE and NOT NULL when those clauses are absent
from a typed ALTER. A nullability/default change together is one statement;
omitting DEFAULT intentionally removes it when the target model has none.
Models must accurately describe the existing/target default, not just the
property the caller wants to change. No missing default is inferred.

Existing column options are not rewritten in a typed ALTER; SET OPTIONS is a
separate operation. Option changes mixed into this diff fail explicitly, rather
than being silently discarded. Names/schema decoration use the existing options.
Identity, generated, primary-key and ON UPDATE columns, as well as mixed type,
name or other property changes, still require separate migrations. NOT NULL
cannot be added to an existing ARRAY column; removing it is supported through
the same native array renderer.

This generates DDL only. Adding NOT NULL may fail on existing NULL rows even
when a default is present: ALTER COLUMN does not backfill. No UPDATE, implicit
commit, database access or claim of existing-data validation is added. Live
Spanner and older service/emulator compatibility remain unverified; this uses
current GoogleSQL syntax with no resolver, XML, dependency or public signature
changes. Local tests cover XML, default preservation/removal, option separation
and failure boundaries.

## CHECK constraint migrations

Named CHECK constraints use the shared Schema model and ordinary table diffs:

```java
Table target = original.clone();
target.getConstraints().add(new CheckConstraint("positive_quantity", "quantity > 0"));
var operations = dialect.createSqlFactoryRegistry()
        .createSql(original.diff(target), SqlType.ALTER);
```

ADD and DROP each produce one ALTER statement. Expression changes produce
DROP followed by ADD: GoogleSQL does not modify a CHECK expression in place.
Changed CHECK constraints are dropped before column changes and recreated after
them. These statements do not form an atomic operation; a failed ADD can leave
the previous constraint absent. Existing-data validation remains the service's
responsibility. Review and execute the returned operation list accordingly.

CREATE TABLE, standalone CHECK creation and table-diff ADD share the same
renderer and validation. Names/expressions must be nonblank. Disabled or
deferrable constraints fail explicitly; an explicit NotDeferrable value is
accepted without emitting a clause. Primary-key and UNIQUE
alterations remain outside this implementation. No metadata queries, resolver
splits, dependencies or Schema XML fields are added. This targets current
GoogleSQL; older service/emulator compatibility and live execution are unverified.

Reference: [Spanner CHECK constraints](https://docs.cloud.google.com/spanner/docs/check-constraint/how-to).

## Foreign-key migrations

CREATE TABLE, standalone foreign-key CREATE and table-diff ADD now share the
native foreign-key factory. Table diffs support ADD, DROP and DROP/ADD changes.
Modified foreign keys are removed before local column changes and recreated
afterwards, using the same phases as CHECK constraints. Changes are not atomic:
a failed ADD may leave the original constraint absent. This is a single-table
DDL generator, not a cross-table migration/dependency planner.

`CascadeRule.None` emits `ON DELETE NO ACTION`, never the generic `NONE`.
Enforced foreign keys support ON DELETE CASCADE and SET NULL as well. Null or
None update rules emit no ON UPDATE clause; other update actions fail explicitly.
SIMPLE matching and NotDeferrable are accepted without unsupported clauses;
FULL/PARTIAL matching and deferred constraints fail. `enable=false` preserves
the reader's informational foreign-key representation as NOT ENFORCED, with no
delete-action clause. Informational foreign keys with CASCADE/SET NULL fail.
The default `enable=true` retains the server's enforced default.

Use the shared Schema model to resolve the referenced table, or construct the
foreign key using columns bound to the referenced Table. Nonblank names,
nonempty equal-length column lists and a resolved referenced-table identity are
required. An unresolved parent fails rather than becoming an accidental
self-reference. Column types, indexability, nullability, commit-timestamp
restrictions, existing data and other server constraints remain subject to
Spanner validation. Composite-key order and schema-qualified references are
preserved. No metadata queries, XML fields, dependencies or resolver changes
are introduced; older metadata-reading paths are unchanged.

This targets the current GoogleSQL service, including informational foreign
keys and ON DELETE SET NULL. It does not establish these features on older
service/emulator versions or on the PostgreSQL interface. No database was
contacted by the local tests.

References: [GoogleSQL DDL](https://docs.cloud.google.com/spanner/docs/reference/standard-sql/data-definition-language),
[foreign-key semantics](https://docs.cloud.google.com/spanner/docs/foreign-keys/overview).

## Index migrations

Table diffs support index addition, deletion and DROP/CREATE replacement through
the registered native index factories. No additional migration configuration is
needed. Changed indexes are dropped before local column changes and recreated
after them. The same CREATE/DROP SQL is available through standalone index
operations. DROP SEARCH INDEX is used for FullText indexes; regular and vector
indexes use DROP INDEX. DROP IF EXISTS follows the existing option. SEARCH
creation retains its existing grammar without IF NOT EXISTS; regular and vector
creation honor that option.

When schema decoration is enabled, both the index and its table are qualified,
including SEARCH/VECTOR creation. This corrects the previous unqualified index
name on named-schema tables. Descending regular keys, STORING, native options
and ordinary-index WHERE text are retained. WHERE restrictions are checked by
Spanner; the generator does not parse the predicate or infer it from constraints.

Unnamed indexes, missing CREATE parents/keys and unsupported types fail
explicitly. Modeled SEARCH/VECTOR requests cannot carry UNIQUE, NULL_FILTERED
or descending-key declarations that the renderer cannot preserve. Indexes
sharing a modeled constraint's name require a constraint migration; server-side
managed indexes and other dependencies still require server validation.
Key/type/options changes use replacement, not an in-place ALTER optimization.
Replacement is not atomic, can remove uniqueness temporarily and may leave an
index absent if recreation fails. No execution/backfill guarantee is implied.

Public signatures, Schema XML, dependencies, resolver selection and metadata
queries are unchanged. The new DROP factory is additive, and invalid requests
that previously lost semantics now fail. Tests cover XML, native SQL equivalence,
options, type transitions and local column ordering. This targets current
GoogleSQL; older service/emulator and live-server compatibility are unverified.

References: [GoogleSQL index DDL](https://docs.cloud.google.com/spanner/docs/reference/standard-sql/data-definition-language),
[named schemas](https://docs.cloud.google.com/spanner/docs/named-schemas?hl=en).

## Delete-all SQL

`SqlType.TRUNCATE` emits `DELETE FROM <table> WHERE TRUE`. GoogleSQL requires
a WHERE clause for DELETE. Schema decoration and identifier quoting use the
existing SQL factory options. This is DML, so normal transaction, mutation
limits and referential constraints apply; it is not a native TRUNCATE operation.
No implicit commit or constraint disabling is added.

The existing temporary-name deletion path uses the same DELETE syntax, without
the invalid TABLE token. This does not establish temporary-table CREATE support.

Regression tests validate generated SQL locally; live Spanner execution and
older service/emulator compatibility remain unverified. No database is required
for these tests. Existing APIs, dependencies and Schema XML are unchanged.

Reference: [GoogleSQL DML](https://docs.cloud.google.com/spanner/docs/reference/standard-sql/dml-syntax).

## Index-column metadata validation

### Spanner-managed backing indexes

Optional SPANNER_IS_MANAGED metadata is retained in index specifics and XML.
Known true/false values are validated across all rows; a missing field retains
the old path without guessing ownership from names. The existing INDEXES.*
query supplies it without newer physical-column references or extra SELECTs.

CREATE TABLE retains the managed index in the Schema model but omits its
CREATE INDEX operation: the modeled foreign-key constraint controls backing
index creation. User indexes still emit normally, regardless of name prefix.
Direct CREATE/DROP, detail rendering and table-diff operations that modify
managed index objects fail with an instruction to change the constraint.
The optional constant is SpannerCreateIndexFactory.SPANNER_IS_MANAGED; an
invalid explicit value fails instead of becoming user-owned.

Foreign-key changes with unchanged derived index metadata continue through the
constraint factory, without independent backing-index SQL. No one-to-one
constraint ownership is inferred: Spanner may share a backing index between
foreign keys. A diff that also explicitly changes/removes managed index objects
is conservatively rejected, even when a constraint changes in the same diff;
coordinating those derived snapshot changes requires a separate plan.

Tests cover 40 index flags in three queries, XML retention, complete foreign-key
and user-index table recreation, direct/diff rejection and unrelated column
changes. Live backing-index lifecycle and historical flag exposure remain
unverified. Existing public signatures, XML structure, dependencies, resolver
paths and query counts are unchanged.
Reference: [Spanner foreign-key backing indexes](https://docs.cloud.google.com/spanner/docs/foreign-keys/overview).

### WHERE reconstruction

When available, the native INDEXES.FILTER value is now retained directly in
Index.where, ahead of nullability-based reconstruction. This preserves the
catalog expression and works even if base nullability metadata is unavailable.
Optional FILTER and SEARCH layout fields come from INDEXES.*; they are not
explicitly referenced physical columns, so older catalogs without them retain
the legacy path. The following nullability reconstruction applies when no
nonblank native FILTER is exposed.

The existing bulk index query joins base COLUMNS on catalog/schema/table/column
identity and reads IS_NULLABLE from both the base and index column. A base
column that permits NULL while its index column rejects NULL contributes a
quoted `column IS NOT NULL` term to shared `Index.where`. Terms are ordered
deterministically and joined with AND. This reconstructs the effective null
exclusion condition, not the original SQL spelling; redundant predicates on
base NOT NULL columns cannot be distinguished and are unnecessary for the
effective condition. The approach follows the catalog's index nullability
representation, also used for NULL_FILTERED keys in the official emulator.

Normal, SEARCH and VECTOR use the same reader path, including STORING columns.
Global NULL_FILTERED key exclusions remain modeled by IS_NULL_FILTERED rather
than duplicated in WHERE; additional STORING exclusions still become WHERE.
No nullability is guessed: missing/unknown base or index values fail with an
actionable error, including when permissions hide needed base metadata.

Local tests retrieve 40 indexes of each type across two schemas in three total
queries (rows, option availability, bulk options), retain predicates through
XML/CREATE and replace changed indexes through table diffs. Existing empty and
unavailable-option-table counts remain one and two. Only existing catalog
columns are joined; no new physical columns, version assumptions, public APIs,
XML structure or dependencies are added. This is tested SQL generation and
mocked metadata reconstruction; live partial-index nullability, permissions and
older service/emulator behavior still need verification.

Sources: [GoogleSQL index grammar](https://docs.cloud.google.com/spanner/docs/reference/standard-sql/data-definition-language),
[index/base column metadata](https://docs.cloud.google.com/spanner/docs/information-schema),
[emulator catalog implementation](https://github.com/GoogleCloudPlatform/cloud-spanner-emulator/blob/master/backend/query/information_schema_catalog.cc).

### SEARCH layout and index interleave

Optional SEARCH_PARTITION_BY and SEARCH_ORDER_BY JDBC string arrays are read
in the existing index-row query. Their columns are separated from TOKENLIST
keys using their names; the sort direction comes from INDEX_COLUMNS order and
ordinal metadata. JDBC arrays are freed on successful and failed decoding.
Malformed arrays, missing layout columns and contradictory row headers fail
explicitly. A native SEARCH header's internal IS_UNIQUE=true does not become
a user-declared UNIQUE index.

PARTITION BY uses shared Index.partitioning.partitioningColumns, with no
partition type, bounds, subpartitions or partition count. It is a SEARCH grouping
layout, not a physical HASH/RANGE table partition definition. ORDER BY currently
supports one INT64 column using optional specifics SEARCH_ORDER_BY_COLUMN and
SEARCH_ORDER_BY_DIRECTION (ASC by default for user-authored models). Other
layout shapes fail rather than being dropped. These optional keys are exposed
as constants on SpannerCreateIndexFactory; no existing API signatures or Schema
XML structure change.

PARENT_TABLE_NAME retains the index's native interleave parent. Ordinary and
SEARCH CREATE emit `, INTERLEAVE IN <parent>` after WHERE and before OPTIONS.
The name is quoted as one identifier and uses the index schema when schema
decoration is requested. Parent key-prefix/type compatibility and ancestry are
left to Spanner. Explicit false sort-order sharding on an interleaved SEARCH
index is rejected. VECTOR interleave is rejected. Table INTERLEAVE creation is
described above; table INTERLEAVE ALTER remains unsupported.

For a user-authored SEARCH index, add only the optional layout needed:

```java
Partitioning grouping = new Partitioning();
grouping.getPartitioningColumns().add(new ReferenceColumn("tenantId"));
index.setPartitioning(grouping);
index.getSpecifics().put(SpannerCreateIndexFactory.SEARCH_ORDER_BY_COLUMN, "createdAtMicros");
index.getSpecifics().put(SpannerCreateIndexFactory.SEARCH_ORDER_BY_DIRECTION, "DESC");
```

Metadata tests cover 40 SEARCH layouts across two schemas with native FILTER,
STORING, descending order, interleave, XML and table-diff recreation in three
queries. Additional tests cover ordinary interleave, array lifecycle, invalid
layouts and user-authored defaults. Actual array/field exposure, cross-schema
parent naming, DDL execution and older service/emulator behavior remain live
verification items. No per-index query, dependency or resolver boundary is added.

Index headers accept only INDEX, SEARCH and VECTOR types and explicit true/false
UNIQUE/NULL_FILTERED values. Unknown or missing values and contradictory types
or flags across rows fail before option retrieval. Tests retain true flags
through XML and UNIQUE NULL_FILTERED DDL and reject conflicting/malformed
headers. Existing catalog queries and resolver paths are unchanged; these
reader checks apply to all currently supported Spanner paths. Live driver
representations and older-service compatibility remain unverified.

Index keys are assembled by ORDINAL_POSITION independently of result row order;
NULL positions remain STORING columns. Missing names/owners, owner conflicts,
invalid/duplicate/gapped positions, missing keys and repeated columns across
keys/STORING fail explicitly. Normal indexes require a recognized direction;
SEARCH/VECTOR retain the existing allowance for absent directions. Tests read
40 composite indexes across two schemas, interleave STORING with reversed key
rows and retain keys/direction/includes through XML and CREATE DDL. Invalid
rows fail before option queries. Existing option-query counts, catalog SQL,
public APIs, XML structure, dependencies and resolver paths are unchanged.
Live catalog and older-service behavior remain unverified.

## Primary-key metadata validation

The primary-key reader uses the existing INDEX_COLUMNS.ORDINAL_POSITION to
assemble columns explicitly rather than depending on result row order. Missing,
duplicate or gapped positions, repeated columns, missing identities and unknown
sort directions fail explicitly. Local tests read 40 composite keys in one
query across two schemas, reverse the incoming rows and retain key order and
DESC through XML and CREATE TABLE DDL. Only PRIMARY_KEY indexes are read by
this reader; unique secondary index handling is unchanged. No new physical
catalog columns, JDBC calls, APIs, XML structure, dependencies or resolver
boundaries are introduced. Live catalog/older-service behavior remains
unverified.

## View metadata validation

Metadata-derived views require nonblank names and query statements and an
explicit INVOKER or DEFINER security type. Missing or unknown security is an
error rather than silently becoming INVOKER during recreation. Duplicate
catalog/schema/name identities are rejected. This reader-only validation uses
the existing catalog query on all existing resolver paths; user-authored view
defaults, public APIs, dependencies and XML structure are unchanged. Local
tests retrieve 40 views across two schemas in one query and check multiline
queries, comments, identifiers and both security types through XML and CREATE
DDL. Live catalog/permission behavior and older-service compatibility remain
unverified. A shared XML fix preserves supplementary Unicode characters in
view statement CDATA and ordinary XML text; this also benefits other dialects.

Reference: [GoogleSQL VIEWS metadata](https://docs.cloud.google.com/spanner/docs/information-schema#views).

## Sequence metadata validation

Sequence reads reject missing names, duplicate catalog/schema/name identities,
nonpositive explicit start counters, incomplete skip ranges and reversed skip
bounds. Missing optional values retain their existing defaults. A skip range
whose minimum equals its maximum remains valid. Local mock tests retrieve 40
sequences across two schemas in one query and preserve start/skip options
through XML and CREATE DDL.

The same query now reads sequence_kind from the existing SEQUENCE_OPTIONS
table. Raw bit_reversed_positive and the corresponding single/double-quoted
SQL literals normalize to specifics SEQUENCE_KIND=bit_reversed_positive and
survive XML. Other non-null values fail explicitly; a missing option retains
the prior behavior. No new physical catalog columns, JDBC calls, public APIs,
dependencies or resolver boundaries are introduced. The CREATE factory keeps
its existing BIT_REVERSED_POSITIVE generation. Tests cover 40 sequences,
quoted/raw kind values, unknown/malformed values and bound schema/name filters.
Reference: [Google emulator sequence options](https://github.com/GoogleCloudPlatform/cloud-spanner-emulator/blob/master/backend/query/information_schema_catalog.cc).
Live catalog execution, older-service compatibility and restoration of the
current allocation counter remain unverified.

## Column metadata validation

IDENTITY metadata accepts YES/NO flags and known generation modes (BY DEFAULT,
BY_DEFAULT and ALWAYS). Unknown non-null flags, modes and sequence kinds fail
explicitly rather than becoming BY DEFAULT or bit-reversed sequences. The
recognized explicit kind is BIT_REVERSED_POSITIVE_SEQUENCE, matching the
[Google emulator catalog implementation](https://github.com/GoogleCloudPlatform/cloud-spanner-emulator/blob/master/backend/query/information_schema_catalog.cc).
NULL optional identity metadata retains its existing behavior, including an
unspecified kind that depends on the destination database default. Local tests
cover generation mode, start counter and skip range through XML and CREATE
DDL; they do not certify live execution or restore the current sequence counter.
Catalog SQL, version resolution and public APIs are unchanged.

The column reader rejects missing table/column names, missing native types,
unknown or absent YES/NO nullability, duplicate catalog/schema/table/column
identities and generated columns without an expression. These checks use the
existing query and catalog columns on all existing Spanner resolver paths.
Local mock coverage reads 40 columns in one query across two schemas and checks
type, nullability, multiline DEFAULT and stored generated expressions through
Schema XML and CREATE DDL. Missing optional generated/identity fields retain
their existing behavior; live catalog and older-service compatibility remain
unverified. No public APIs, dependencies or XML structure changed.

Reference: [GoogleSQL information schema](https://docs.cloud.google.com/spanner/docs/information-schema).

## CHECK metadata validation

CHECK metadata is fetched in one query for the requested object set. Missing
names, owning tables or expressions and duplicate catalog/schema/name identities
fail explicitly. Expressions containing IS NOT NULL are preserved without
heuristic filtering. Mock tests cover 40 checks, XML/DDL round trips and bound
object filters. Live catalog behavior and internal NOT NULL constraint exposure
remain unverified. No catalog SQL, public API, resolver or Schema XML changes
are introduced by this validation.


CHECK expressions remain XML attributes. The shared XML writer preserves LF,
CR and tab characters using numeric character references. Round-trip coverage
includes a multiline expression with a line comment, CRLF and indentation.

