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

## Emulator verification

The opt-in integration test reads a schema through JDBC, round-trips it
through XML, applies the generated DDL to a separate disposable emulator
database and reads it back. It checks a TTL policy, a partial index, search
partitioning and descending order, primary key order and the two-step
interleave change to CASCADE.
The test also checks vector length, the index filter and stored column, and
confirms the distance type, tree depth and leaf count in the target's
database DDL. Where the emulator does not expose those options through
`INDEX_OPTIONS`, the test supplies the known source values before XML export.
Another test recreates same-named tables and indexes in two named schemas
with schema decoration enabled. It verifies parent-first creation and
preserves each schema's own delete action, index ordering and filter.

Generate the schema and its objects together with
`registry.createSql(schema, SqlType.CREATE)`. Named schemas emit
`CREATE SCHEMA` before their objects. Keep schema decoration enabled when
recreating a named schema. For the unnamed default schema, generation starts
with its objects because the namespace already exists. Use a target where the
named namespace does not yet exist, since GoogleSQL's schema statement does
not support `IF NOT EXISTS`.

Use `registry.createSql(schema, SqlType.DROP)` to generate `DROP SCHEMA` for
a named user schema. Remove its objects first. The generator honors the
existing `dropIfExists` option and rejects the default and system schemas.

```
.\gradlew.bat :sqlapp-core-dialect-test:dockerTest --tests '*spanner.SpannerMetadataReaderTest' -PtestcontainersReuse=false
```

GoogleSQL catalog fields selected through `*` use their native uppercase
JDBC labels. The CHECK reader skips reserved `CK_IS_NOT_NULL_` entries
because their semantics are already represented by column nullability.
Hosted-service option discovery requires separate verification.

## Table synonyms

Add a table alias to a Schema with its target table name:

```java
schema.getSynonyms().add(new Synonym("LegacyOrders").setObjectName("Orders"));
```

Schema CREATE emits `ALTER TABLE … ADD SYNONYM` after creating its tables.
Use the same Synonym with `SqlType.DROP` to emit
`ALTER TABLE … DROP SYNONYM`. An omitted target schema means the synonym's
own schema. Cross-schema targets and database links are outside this path.
Spanner allows one synonym per table.

Use `registry.createSql(original.diff(target))` to change a modeled synonym's
target table or alias name. The models must retain their Spanner dialect.
Generation validates both sides and composes the native DROP and ADD factories,
in that order. An equivalent explicit target schema produces no SQL. Namespace
moves and other unrepresented changes fail explicitly. This is a two-statement
replacement: the alias is unavailable between statements, and failure of ADD
can leave it absent. No atomicity or automatic rollback is implied. A destination
table must exist and have no other synonym; the service checks those conditions.

For a containing Schema diff, all changed or removed synonyms are dropped before
the shared schema operations, and all new or replacement synonyms are added
afterwards. This permits swapping aliases between tables and changing an alias's
target while removing its old table and creating its new table in the same
schema migration. Table deletion still deletes its data; this ordering does not
copy data between the tables. Unchanged aliases are left in place.

The documented `INFORMATION_SCHEMA.TABLES` can identify a synonym but
does not specify a target table field, so the reader cannot reconstruct
that relationship. Specify the target explicitly in the model or Schema XML.
See [Manage table names](https://docs.cloud.google.com/spanner/docs/table-name-synonym)
and [GoogleSQL catalog metadata](https://docs.cloud.google.com/spanner/docs/information-schema#tables).

## Interleaved tables

Schema differences containing interleaved tables or foreign keys order new tables by their
modeled parent and foreign-key dependencies and remove deleted tables in reverse
dependency order. This uses the same Spanner ordering as batch creation, including
cycle rejection. Dependencies outside the changed table set are not created or
removed automatically. Existing-table modifications retain their usual phase
after removals and additions. This does not copy table data or remove unrelated
dependent objects. Foreign-key-only chains use the same ordering even when no
table has INTERLEAVE metadata; referencing tables are removed before the tables
they reference. Self references do not create a dependency on another table.

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
enabled. The emulator test verifies same-schema parent resolution with two
named schemas. Cross-schema parent naming and other primary-key compatibility
cases require separate verification. In a CREATE table collection, modeled
interleave parents and FK dependencies precede children; cycles and duplicate
identities fail. Parents outside the collection must already exist. Calls for
ordinary tables without interleaving keep the existing shared ordering path.

An ON DELETE-only difference on an existing IN PARENT relationship emits
`ALTER TABLE … SET ON DELETE CASCADE` or `SET ON DELETE NO ACTION`. Removing
the action restores NO ACTION when IN PARENT is explicit. For legacy metadata
without INTERLEAVE_TYPE, supply NO ACTION explicitly to retain the relationship.

With the same parent, changing INTERLEAVE_TYPE between IN and IN PARENT emits
`ALTER TABLE … SET INTERLEAVE`. When the target is IN PARENT with CASCADE,
the generator emits SET INTERLEAVE IN PARENT with NO ACTION first, then SET ON
DELETE CASCADE. Apply the operations in order, proceeding to CASCADE only after
Spanner successfully validates the parent relationship. If the second operation
fails, the relationship remains IN PARENT with NO ACTION.

Adding, removing or changing the parent requires a separate migration. An
interleave change cannot be combined with unrelated table changes.
See the [GoogleSQL ALTER TABLE reference](https://docs.cloud.google.com/spanner/docs/reference/standard-sql/data-definition-language#alter_table).

Table OPTIONS now use the native comma separator, including when combined with
INTERLEAVE. Tests read 40 legacy/current table headers in one query, round-trip
Schema XML, generate parents first, preserve deletion behavior, reject cycles
and retain FK ordering. No new physical catalog columns, per-table queries,
module dependencies, existing signatures, XML structure or resolver boundary
are added. Actual service/older-emulator behavior remains unverified.

Sources: [TABLES metadata](https://docs.cloud.google.com/spanner/docs/information-schema),
[GoogleSQL CREATE TABLE](https://docs.cloud.google.com/spanner/docs/reference/standard-sql/data-definition-language).

## Table deletion

`registry.createSql(table, SqlType.DROP)` drops the table's modeled user indexes
before emitting native DROP TABLE. Schema differences use the same factory.
Ordinary, SEARCH and VECTOR indexes reuse the existing DROP INDEX generators;
modeled non-primary UNIQUE constraints are treated as unique indexes. Duplicate
index/constraint names emit one drop. Managed foreign-key indexes are left to
the service. Standalone index drops retain their constraint-ownership checks.
The existing `dropIfExists` option applies to the indexes and the table.

The DROP TABLE factory's `createSql(Collection<Table>)` and
`createDiffSql(Collection<DbObjectDifference>)` paths also order modeled
INTERLEAVE and foreign-key dependencies with referencing/child tables first.
Each table's indexes are removed before that table. Duplicate table identities
and cyclic dependencies fail before a plan is returned. Dependencies outside
the supplied batch remain unchanged.

For detached tables, DROP batch ordering can use a named foreign-key target
without binding its referenced columns to the target Table. An explicit target
schema distinguishes same-named tables in different schemas. The shared Schema
model preserves that explicit schema for unresolved reference columns; when
actual target columns are bound, their table's schema remains authoritative.
Existing resolved column references remain usable when the redundant target
name is omitted. This changes dependency ordering only: CREATE still requires
the referenced table and columns to be reliably resolved by the Schema model.

Supply complete index metadata, for example from a full Schema read. Unmodeled
indexes are not discovered during generation, and unrelated referencing views,
foreign keys or interleaved objects are not removed automatically. Use a Schema
diff to order deleted INTERLEAVE children before parents. Dropping a table removes
its data; the emitted statements do not provide an atomic transaction or rollback.
See the [GoogleSQL DROP TABLE reference](https://docs.cloud.google.com/spanner/docs/reference/standard-sql/data-definition-language#drop_table).

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
placement options) fail explicitly rather than falling back
to generic SQL or losing changes. Index changes use the native factories as
described below. DEFAULT changes on identity, generated or ON UPDATE columns also
fail explicitly. Dependency checks, expression type checks and asynchronous
backfill remain the service's responsibility; no online/atomic migration or
data-validation guarantee is implied by SQL generation.

A table diff limited to its name emits `ALTER TABLE old_name RENAME TO new_name`,
with schema qualification and identifier quoting controlled by the usual options.
Pass the original and renamed Table models to the registry; collection-level
matching does not infer that an added and a removed table are a rename. Schema
moves and renames combined with structural changes require separate migrations.
The operation does not add an alias for the old name or rewrite view statements.
Update dependent queries and views as needed. This applies to GoogleSQL services
supporting table renaming; the single-version resolver is unchanged.
When a modeled self-referencing foreign key follows the table's new name, that
reference-name difference is accepted as part of the rename. Changes to its
referenced columns, target schema/table or deletion rule still require a
separate migration. Comparison uses copies and leaves the supplied models intact.
See [table name management](https://docs.cloud.google.com/spanner/docs/table-name-synonym).

Local emulator tests verify that renaming an INTERLEAVE parent retains its child
rows, updates the child's parent metadata and continues to enforce parent-row
existence. Renaming a table referenced by a foreign key currently hits an
internal `foreign_key_validator.cc` check in the tested emulator. That case is
recorded as a skipped integration test only for the observed error; other errors
fail the test. Foreign-key rename behavior still requires service verification.

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

## Commit timestamp ON UPDATE

An ON UPDATE-only difference on an existing non-key scalar TIMESTAMP column
emits `ALTER COLUMN … SET ON UPDATE` or `DROP ON UPDATE`. To set the
expression, the column must already have `allow_commit_timestamp=true` and
a `PENDING_COMMIT_TIMESTAMP()` default. The generator uses the stored
default expression's spelling for the update expression. Removing ON UPDATE
alone keeps the existing default.

Setting the matching default and ON UPDATE together, or removing both, emits
one typed ALTER COLUMN. The statement retains the column's nullability and
existing options. Changes to column options or other column properties need
a separate diff.

See the [GoogleSQL ALTER TABLE reference](https://docs.cloud.google.com/spanner/docs/reference/standard-sql/data-definition-language#alter_table).

## Identity column skip range

In a table diff, set `IDENTITY_SKIP_RANGE_MIN` and
`IDENTITY_SKIP_RANGE_MAX` on the target column to change the skip range,
or remove both to clear it. The column must already be a scalar INT64
identity column. The bounds are inclusive and must be ordered.

The diff emits `ALTER COLUMN … ALTER IDENTITY SET SKIP RANGE` or
`SET NO SKIP RANGE`. Identity columns in primary keys are supported.
Changing the start counter, identity generation mode or other properties
of the same column requires a separate migration.
See the [GoogleSQL ALTER TABLE reference](https://docs.cloud.google.com/spanner/docs/reference/standard-sql/data-definition-language#alter_table).

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

The column reader preserves vector length from either the annotation in
`COLUMNS.SPANNER_TYPE` (for example, `ARRAY<FLOAT32>(vector_length=>3)`)
or the existing `COLUMN_OPTIONS` lookup. If both supply a length, they
must agree. When `INDEX_OPTIONS` does not supply the distance type, set
the model's `Index.vectorDistanceType` to the source index's known value
before generating DDL. The distance type is required by GoogleSQL.

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
When the only index difference is the stored column list, table migrations
emit `ALTER INDEX` or `ALTER VECTOR INDEX` with
`ADD STORED COLUMN` or `DROP STORED COLUMN`. A drop precedes removal of
the underlying table column. An add follows creation of the table column.
Managed indexes and indexes owned by constraints reject direct changes.
Search indexes retain the replacement path for emulator compatibility.
This applies to GoogleSQL services that support the corresponding ALTER syntax.

A vector index difference limited to `DISABLE_SEARCH` uses
`ALTER VECTOR INDEX … SET OPTIONS (disable_search = true|false|NULL)`.
Removing the setting emits `NULL`. This does not need the index's original
distance type and does not recreate the index. The local emulator rejects
this SET OPTIONS action. Execution requires a GoogleSQL service that supports
it.

Changes to index keys, type, filter or other options use replacement.
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
described above, including ALTER of the delete action on an existing IN PARENT relationship.

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

Metadata-derived views require nonblank names and query statements. The reader
retains INVOKER or DEFINER when the catalog supplies it. Older catalogs that
return no security type are recorded as UNKNOWN. Set the correct security type
before generating DDL for those views. Invalid nonblank security types and
duplicate catalog/schema/name identities are rejected. This validation uses
the existing catalog query on all existing resolver paths; user-authored view
defaults, public APIs, dependencies and XML structure are unchanged. Local
tests retrieve 40 views across two schemas in one query and check multiline
queries, comments, identifiers and both security types through XML and CREATE
DDL. Live catalog/permission behavior and older-service compatibility remain
unverified. A shared XML fix preserves supplementary Unicode characters in
view statement CDATA and ordinary XML text; this also benefits other dialects.

Reference: [GoogleSQL VIEWS metadata](https://docs.cloud.google.com/spanner/docs/information-schema#views).

## Sequence migrations

Use `SqlType.DROP` with a named Sequence to generate `DROP SEQUENCE`.
The generator honors `dropIfExists` and schema decoration. Remove any
column-default or view references before dropping a sequence.

To set or clear an existing sequence's skip range, use the before/after
Sequence models with the normal SQL factory diff entry point. Set
`SKIP_RANGE_MIN` and `SKIP_RANGE_MAX` together, or remove both to clear
the range. Set `SEQUENCE_DDL_STYLE` on either model to `CLAUSES` for a
sequence created with native clauses or `OPTIONS` for one created with
an OPTIONS list. The catalog does not disclose which form created the
sequence. The Spanner CREATE factory uses `CLAUSES`.

The generator emits SKIP RANGE / NO SKIP RANGE for CLAUSES and
SET OPTIONS for OPTIONS. It requires a diff and rejects changes to the
start counter or other sequence properties. See the
[GoogleSQL ALTER SEQUENCE reference](https://docs.cloud.google.com/spanner/docs/reference/standard-sql/data-definition-language#alter_sequence).

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


## Table option migrations

GoogleSQL table diffs use `ALTER TABLE … SET OPTIONS` for changes to
`LOCALITY_GROUP`, `COLUMNAR_POLICY` and `FULLTEXT_DICTIONARY_STALENESS` in
existing table specifics. The statement includes only changed options.
Removing `COLUMNAR_POLICY` emits SQL `NULL`, restoring inheritance from the
database. To change the locality group or dictionary staleness, supply an
explicit replacement value. Changing the creation-only
`FULLTEXT_DICTIONARY_TABLE` flag fails with an unsupported migration error.

The options require a GoogleSQL service and edition that supports them. The
resolver and metadata queries are unchanged. Set these table specifics
explicitly when building a migration from catalog snapshots, because the
documented `INFORMATION_SCHEMA.TABLES` does not expose these options.
See the [GoogleSQL ALTER TABLE reference](https://docs.cloud.google.com/spanner/docs/reference/standard-sql/data-definition-language#alter_table)
and [columnar engine configuration](https://docs.cloud.google.com/spanner/docs/configure-columnar-engine).

## Row deletion policies (TTL)

GoogleSQL TABLES metadata retains optional ROW_DELETION_POLICY_EXPRESSION
through the existing table specifics and Schema XML. TABLES.* exposes the field
when available without requiring it in older catalogs or adding per-table queries.
CREATE TABLE emits the policy after INTERLEAVE and before OPTIONS.

Set SpannerCreateTableFactory.ROW_DELETION_POLICY_EXPRESSION to, for example,
`OLDER_THAN(CreatedAt, INTERVAL 7 DAY)`. The referenced column must be an existing
scalar TIMESTAMP, using the exact casing of its name. Quote reserved words
with backticks. Zero days is supported; malformed expressions, negative days,
other units and missing or incorrectly typed columns fail explicitly.
Table differences produce ADD, REPLACE or DROP ROW DELETION POLICY. An ADD
or DROP can share a migration with column changes. The policy is added after
the columns are available or removed before any columns are dropped. Keeping
a policy that references a missing column is rejected. REPLACE follows column
changes when the old timestamp column remains available. Remove the old column
in a subsequent migration. Changes to other table properties
must be separate from TTL changes. Escaped backticks in column identifiers are
currently unsupported by the TTL expression validator.

This applies to GoogleSQL services supporting TTL; the existing single-version
resolver is unchanged. Absent metadata leaves legacy behavior unchanged. Live
service capability boundaries, generated-column dependencies and asynchronous
TTL execution are not verified by local tests.

Sources: [TTL management](https://docs.cloud.google.com/spanner/docs/ttl/working-with-ttl)
and [GoogleSQL DDL](https://docs.cloud.google.com/spanner/docs/reference/standard-sql/data-definition-language).
