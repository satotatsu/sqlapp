# SQLite schema object recreation

SQLite metadata uses the shared Schema model and the normal dialect entry points:

```java
var dialect = DialectResolver.getInstance().getDialect(connection);
var schemas = dialect.getCatalogReader().getSchemaReader().getAllFull(connection);
var registry = dialect.createSqlFactoryRegistry();
var operations = registry.createSql(view, SqlType.CREATE);
```

## Views

The default reader stores the SELECT in `statement` and reads the view columns.
SQLite CREATE generation includes those column names, preserving explicit view
column aliases through Schema XML and recreation. Header parsing ignores quoted
identifiers, literals and comments when locating AS.

With `ReaderOptions.readDefinition=true`, the original CREATE VIEW is retained
instead. The SQLite factory replaces the declaration's object identity with the
model's schema/name while retaining the rest of the definition. This is needed
because SQLite's catalog removes the database qualifier from stored CREATE SQL.
The default statement path also qualifies the view name so an attached database
is not silently recreated in main. References inside the query are preserved.

On SQLite 3.16 and later, columns for all requested views are fetched with one
table-valued PRAGMA query. Earlier versions keep the existing per-view PRAGMA
path because table-valued PRAGMAs are unavailable. Metadata queries continue to
use the historical `sqlite_master` alias, and dialect resolution is unchanged.
Generated view column lists require SQLite 3.9 or later; older targets can use a
complete definition without a column list. No legacy SQLite runtime is included
in the automated tests.

## Triggers

The default reader retains a complete, executable CREATE TRIGGER in `definition`.
The shared trigger fields cannot fully express UPDATE OF and all SQLite trigger
body syntax, so exporting only the body would lose semantics. `statement` is
empty. This intentionally changes the previous metadata representation; public
method signatures and the XML format are unchanged.

Timing, event, owning table and row orientation remain supplemental metadata.
Timing defaults to BEFORE when omitted. Trigger object filters and schema filters
are honored. Quoted identifiers and comments do not supply timing/event keywords.
Setting both `readStatement` and `readDefinition` to false omits executable SQL;
either option enables the complete-definition fallback.

CREATE generation retains UPDATE OF, WHEN, multiple statements and BEGIN/END
blocks, while qualifying the declaration with the model's schema/name. Trigger
definitions must contain the complete CREATE TRIGGER, including BEGIN/END.
A missing definition fails with an actionable error rather than producing an
incomplete operation. Changing table references inside a definition is outside
this factory's scope. TEMP triggers on tables in a different database require
the original definition to qualify the target table explicitly.

## Tables

The SQLite table CREATE factory composes the shared model and index factories.
Identity columns use SQLite's inline INTEGER PRIMARY KEY syntax; no SQL-standard
GENERATED AS IDENTITY clause is emitted. The reader distinguishes AUTOINCREMENT
from an ordinary rowid alias, and generation retains that distinction. Existing
sequence high-water marks and table data are not exported by this DDL factory.

Generated columns use GENERATED ALWAYS AS with VIRTUAL or STORED, and table
options retain WITHOUT ROWID and STRICT. UNIQUE constraints include generated
columns. Column metadata uses table_xinfo where available (3.26+), while older
column readers retain table_info. Generated columns require SQLite 3.31+ and STRICT requires 3.37+;
the resolver rejects structured SQL generation for unsupported target versions.

The reader preserves ordinary primary-key nullability instead of assuming all
SQLite primary keys imply NOT NULL. INTEGER PRIMARY KEY DESC is retained inline
because moving it to a table-level primary key would change its rowid semantics.
Table option and primary-key keyword detection ignores quoted strings and comments.

Column specifics preserve SQLite syntax that affects behavior:

- `sqlite.declaredType`: original PRAGMA type declaration, including an empty
  declaration or dimensions. It prevents INT or INTEGER(8) from becoming an
  INTEGER rowid alias, and preserves STRICT ANY storage behavior. The table
  factory uses this declaration preferentially; remove or update it when
  intentionally changing a read column's type.
- `sqlite.autoincrement=true`: retain AUTOINCREMENT on an identity column.
- `sqlite.primaryKey.inlineDesc=true`: retain the inline PRIMARY KEY DESC exception.
- `sqlite.notNull.onConflict`: retain a column NOT NULL conflict algorithm
  (ROLLBACK, ABORT, FAIL, IGNORE or REPLACE). Invalid values fail clearly.

These fields survive Schema XML through the existing specifics map; the public
model API and XML format are unchanged. Invalid multiple identity columns or
identities in WITHOUT ROWID tables fail before operations are returned.

Index creation delegates to the dialect index factory, preserving partial and
expression keys and avoiding CREATE operations for SQLite's internal autoindexes.
The table declaration is qualified with its model schema so attached databases
are recreated in the intended database.

## Foreign keys and column collations

When REFERENCES omits parent column names, the reader resolves the parent's
primary key in primary-key order, not column declaration order. The result uses
the shared foreign-key model and survives XML. SQLite 3.16+ uses a single query
joining table-valued PRAGMAs for all requested child tables; missing parent keys
and mismatched column counts produce actionable errors. The legacy path caches
each parent's table_info result: pre-3.16 SQLite cannot join PRAGMAs, so one
additional PRAGMA per distinct implicitly referenced parent is necessary.

CREATE TABLE uses the SQLite foreign-key factory to emit unqualified parent
table names even when schema decoration is enabled. Different attached databases
cannot be referenced by a foreign key. NO ACTION is emitted for the shared
CascadeRule.None value. Explicit model deferrability is emitted as NOT DEFERRABLE
or DEFERRABLE INITIALLY IMMEDIATE/DEFERRED. Standalone foreign-key CREATE fails
with a table-recreation instruction instead of generating unsupported ALTER
TABLE ADD FOREIGN KEY syntax. This is a deliberate error-behavior change.

Column COLLATE clauses are read from the existing catalog DDL and written from
the model's collation property during table creation. Quoted collation names,
comments and nested expressions are distinguished. The legacy column reader
shares a single DDL lookup for collation, identity and generated expressions.
No additional modern per-column or per-table SELECTs are introduced.

`SqliteForeignKeyRoundTripTest` checks implicit composite references, attached
database recreation with schema decoration enabled, foreign-key rejection and
CASCADE behavior, invalid parent keys, one-query bulk reading, legacy branches,
modeled deferred checks at commit, and case-insensitive column uniqueness.

The reader recovers foreign-key names and deferrability by matching catalog DDL
declarations with PRAGMA metadata on child-column order, referenced table and
columns, and update/delete actions. It consumes matching declarations rather
than assuming PRAGMA IDs follow declaration order. Quoted identifiers, comments,
column-level REFERENCES and table-level FOREIGN KEY clauses are supported.
The default bulk path reuses the CREATE SQL already selected by the query and
adds no SELECTs. The legacy path performs one additional CREATE SQL lookup for
the requested child table when it has foreign keys. An unmatched declaration
fails clearly instead of silently assuming immediate enforcement.

Deferrability uses the shared model. NOT DEFERRABLE INITIALLY DEFERRED is read
as immediate, matching SQLite behavior. Tests recreate the named deferred key
through XML, insert a child before its parent within one transaction, and verify
that an unresolved child fails at commit. New metadata may therefore contain
declared constraint names and explicit deferrability where previous exports
used generated names and omitted the timing. Public signatures and XML format
remain unchanged.

Column NOT NULL ON CONFLICT is preserved through the specifics map and emitted
with the NOT NULL constraint. Executable XML round-trip tests cover REPLACE
using a default value and IGNORE skipping the row, including the legacy reader.
Strings and nested expressions containing similar text do not supply a policy.
PRIMARY KEY/UNIQUE policies and per-key COLLATE overrides are described below.

## Complete key and CHECK details

PRIMARY KEY, UNIQUE and CHECK readers use one schema-scoped sqlite_master query
for all requested tables, including legacy versions. They parse column and
table constraints with the shared SQLite tokenizer. Key reading no longer
depends on PRAGMA origin fields or table_xinfo availability. Names, key order,
explicit collation overrides and ON CONFLICT policies are preserved without
storing an opaque CREATE TABLE that would override the model.

Key conflict algorithms use `constraint.specifics["sqlite.onConflict"]`;
explicit key collations use `constraint.specifics["sqlite.key.collation.N"]`
with zero-based key positions. These optional SQLite details survive XML.
When intentionally reordering model key columns, update the positional collation
overrides too. Column-level collation continues to use the shared Column property.
Named keys retain declared names. Unnamed UNIQUE constraints use generated
`sqlite_unique_<table>_<number>` names rather than an internal index name; this
is an intentional metadata representation change.

CREATE TABLE keeps a primary-key conflict algorithm before AUTOINCREMENT on
inline identity keys and emits key-level COLLATE and sort direction on other
keys. Invalid conflict algorithm configuration fails clearly. The key generator
does not emit separate CREATE operations for internal indexes.

CHECK parsing distinguishes quoted strings, identifiers, comments and nested
expressions. Multiple CHECK constraints on one column retain their individual
names. Text such as CHECK(fake) inside a default value does not create a
spurious constraint. Check expressions retain their original SQL text.

## Native ALTER and schema-scoped DROP

The existing registry is the entry point for supported table differences:

```java
var operations = registry.createSql(original.diff(target), SqlType.ALTER);
```

The SQLite factory supports appending ordinary columns, dropping columns,
renaming a table, and adding/removing/recreating ordinary indexes. Added column
definitions use the same SQLite column renderer as CREATE TABLE. Table and
index names target the model's database, including ATTACH databases. Dropping a
leading or middle column does not generate ALTER COLUMN merely because later
column ordinals change.

Column renaming can be requested explicitly without interpreting an ambiguous
delete/add difference as a rename:

```java
var alter = (SqliteAlterTableFactory) registry.getSqlFactory(table, SqlType.ALTER);
var operations = alter.renameColumn(table, "old_name", "new_name");
```

The source column must exist and the target name must be unused. Column rename
requires SQLite 3.25+, DROP COLUMN requires 3.35+, and generated columns require
3.31+. The resolver selects version capabilities and the ALTER factory rejects
operations unavailable on the target. Tests execute
against the bundled current SQLite, not historical binaries. SQLite itself
validates live schema dependencies when operations execute.

Changes to column types/defaults/nullability, constraints, table options or existing
column order are rejected with a rebuild instruction. New columns are appended
by SQLite regardless of their position among existing columns in the target
model; this retains compatibility with existing command/checkpoint upgrades.
Unsupported ADD forms (identity, STORED generation,
NOT NULL without a non-NULL default, parenthesized defaults and CURRENT_* defaults)
are also rejected. Table rename must be separate from other changes. Ambiguous
column delete/add combinations are rejected rather than risking data loss.
The factory conservatively does not expose SQLite 3.53's ALTER COLUMN NOT NULL
extensions yet. Calling ALTER with only a target table fails instead of returning
an empty result. Errors occur before an operation list is returned.

DROP TABLE/VIEW/INDEX/TRIGGER targets the model's schema even with default
decoration options. DROP TABLE does not emit unsupported CASCADE CONSTRAINTS,
and `supportsDropCascade()` now returns false. Shared table dependency sorting
is retained. SQLite internal index drops and standalone constraint CREATE/DROP
requests fail explicitly; inline CREATE TABLE constraint details still work.

These APIs return SQL operations; they do not execute statements, start
transactions, disable foreign keys or perform an automatic table rebuild.
Execution and transaction boundaries remain with the caller.

## Temporary tables and DELETE-based clearing

`SqlType.CREATE_TEMPORARY` composes the normal SQLite table factory with a
temporary header and a cloned model owned by the temp schema. It uses the same
column, identity, generated-expression, constraint and table-option validation.
The input model is not mutated. Existing temporary name prefix/suffix options
apply to the table and its ordinary index names; index operations target temp
and use the resolved temporary table name. The first operation retains
CREATE_TEMPORARY type and exposes the input and resolved model as original and
target, respectively. Public factory inheritance and method signatures remain
unchanged.

CHECK and foreign-key constraints are now retained, where the earlier temporary
path omitted them. A self-reference follows the resolved temporary identity.
Other foreign keys require an explicit parent in temp; a main/attached parent
cannot be preserved across databases and is rejected with an actionable error.
Provide a shared Schema model with the intended temp parent and referenced
columns, or intentionally remove unwanted foreign keys from the input model.
Parent names for non-self references are not guessed from prefix/suffix options.
Generated columns and STRICT options retain the same version requirements as
ordinary table creation.

SQLite TRUNCATE operations use DELETE rather than unsupported TRUNCATE syntax.
Temporary clearing now emits `DELETE FROM temp.<resolved-name>` and no longer
emits the invalid `DELETE FROM TABLE` form. If the temporary table is absent,
execution fails rather than falling back to a same-named main table. Ordinary
clearing qualifies a specified schema even with default decoration options.
Neither path resets AUTOINCREMENT counters or changes transaction ownership.

Generated AS expressions and their column identifiers now use the same token
handling as other SQLite DDL. Quoted identifiers (including bracket quoting),
leading comments, doubled quotes, parentheses in strings and comments, and
nested CAST AS clauses are distinguished. The reader locates the actual
column-level generated expression instead of text such as AS(fake) in a name
or comment. `SqliteColumnReaderTest` verifies expression preservation through
XML and executable recreation. `SqliteTemporaryTableLifecycleTest` checks
temporary identity/index resolution, constraints and options, source-model
immutability, self/explicit temp parent references, and isolation between
same-named main/temp/attached tables.

## Verification and scope

`SqliteSchemaObjectsRoundTripTest` uses isolated in-memory SQLite connections:

- view column names and complete definitions survive XML and ATTACH recreation;
- conditional UPDATE OF triggers retain their observed behavior after recreation;
- quoted AS, comments, omitted timing and INSTEAD OF header syntax are covered;
- eight views use one prepared statement for column metadata;
- missing executable SQL fails clearly, and disabled SQL-reading options are honored.

`SqliteTableRoundTripTest` covers XML and executable table recreation, generated
column uniqueness, AUTOINCREMENT reuse behavior, partial indexes, strict typing,
WITHOUT ROWID, nullable keys and the rowid type boundaries. It also exercises the
legacy metadata branch on the bundled current SQLite runtime; this does not
substitute for testing against a historical SQLite binary.

The implementation stays in `sqlapp-core-sqlite`; dependencies and shared model
APIs are unchanged. Resolver mappings now distinguish SQLite feature boundaries.
New native ALTER and explicit rename APIs
are additive. Automatic table-rebuild execution and SQLite extensions remain
outside this change. PostgreSQL-only objects
such as stored procedures and sequences are not added to SQLite.

## Version capabilities and DML

Resolve a dialect from the JDBC connection to select the engine's capabilities.
The version classes inherit the nearest compatible predecessor, with these
boundaries:

| SQLite version | Added capability |
| --- | --- |
| 3.8.0 | Partial indexes |
| 3.8.2 | WITHOUT ROWID |
| 3.8.3 | WITH and recursive CTEs |
| 3.9 | Expression indexes and view column lists |
| 3.15 | Row value comparisons and IN |
| 3.24 | Bulk UPSERT |
| 3.25 | Column rename |
| 3.31 | Generated columns |
| 3.32 | Default bind parameter ceiling increases from 999 to 32766 |
| 3.35 | INSERT RETURNING and DROP COLUMN |
| 3.37 | STRICT tables |

`DialectHolder.defaultDialect` retains modern capabilities. An explicitly unknown
version resolves conservatively; SQLite 3.8 without a patch version enables only
3.8.0 capabilities. Concrete dialect classes now vary by version; use the public
capability methods rather than testing an exact implementation class. Existing
metadata readers retain their historical query branches. Before 3.9, views read
with the default statement option retain complete executable definitions to avoid
introducing unsupported view column lists during recreation.

Factories guard structured table options, generated columns, stored expression
and partial indexes, view columns and native ALTER operations. The bulk UPSERT
provider and executor reject targets before 3.24; INSERT RETURNING rejects targets
before 3.35. Complete user-supplied DDL definitions remain an escape hatch and are
not parsed as a general SQL compatibility validator. Custom SQLite builds can
lower the bind parameter limit; the dialect reports the standard version default.

Paging capabilities now advertise LIMIT/OFFSET rather than TOP. Current date,
time and timestamp expressions use SQLite's executable CURRENT_DATE,
CURRENT_TIME and CURRENT_TIMESTAMP keywords (UTC). A timestamp-with-time-zone
expression remains unavailable. `SqliteVersionCapabilitiesTest` covers resolver
boundaries, generation rejection, legacy view recreation and native expressions.
These tests run on the bundled SQLite runtime, not historical engine binaries.

## INSERT and MERGE SQL generation

Use the normal SQL factory registry with the shared Table model:

```java
var registry = dialect.createSqlFactoryRegistry();
var insert = registry.createSql(table, SqlType.INSERT);
var upsert = registry.createSql(table, SqlType.MERGE);
var upsertRows = registry.createSql(table, SqlType.MERGE_ROWS);
```

INSERT emits `DEFAULT VALUES` when there are no writable input columns, including
identity-only tables. An identity column with explicit input row values is retained;
a signature mixing generated and explicit identity values fails clearly. Existing
GENERATED ALWAYS validation is retained. Generated columns are omitted. INSERT_ROWS
retains the shared VALUES expansion markers; default-only batch input is rejected
because SQLite does not support a multi-row DEFAULT VALUES statement. Use individual
INSERT operations for that case.

For SQLite 3.24+, MERGE and MERGE_ROWS now generate INSERT ... ON CONFLICT rather
than unsupported MERGE INTO. Key selection uses the existing
`TableOptions.mergeKeyColumnsMatchingStrategy`. The selected columns must match a
modeled primary key, unique constraint or ordinary non-partial unique index, and
must be writable input columns. Constraint and index collations are retained in
the conflict target. Expression and partial unique indexes alone are not supported
as merge keys. If a generated identity is the only key, explicit identity input
is required to identify an existing row.

On conflict, writable non-key columns are updated through `excluded`, preserving
the existing primary key and identity. Shared insertable/updateable/created-at
column selection and insert value generation remain in use. Optional
`updateTableColumnValue` expressions customize assignments. A key-only input uses
DO NOTHING. Conflict handling does not delete and reinsert the existing row, so
ON DELETE CASCADE children are retained. Other constraint violations still fail;
nullable unique keys follow SQLite's native NULL uniqueness semantics.

MERGE_ROWS uses the shared SQL parser and parameter binder to expand all input
rows, appending one conflict clause to the statement. Delete-absent-source
options are rejected rather than ignored. MERGE_TABLE and optional RETURNING
are described below. Streamed bulk execution remains available through the
existing bulk UPSERT API.

SQLite 3.31+ now advertises column formula support to shared DML factories, so
ordinary UPDATE also omits generated columns. CHAR and DECIMAL JDBC type handlers
are registered. Across supported SQLite reader versions, unregistered declarations
such as VARCHAR or custom type names fall back to their SQLite affinity for typed
JDBC binding, while `sqlite.declaredType` retains the original declaration for
XML/DDL recreation. This does not impose fixed-length strings or exact-decimal
storage guarantees on SQLite. `SqliteDmlFactoryTest` executes
default-only and explicit-identity INSERT, attached composite-key MERGE,
collation-aware unique keys, primary-key preservation, child-row preservation,
generated-column UPDATE and multi-row parser/binder expansion on local in-memory
SQLite. See SQLite's [INSERT](https://www.sqlite.org/lang_insert.html) and
[UPSERT](https://www.sqlite.org/lang_upsert.html) syntax documentation.

## Staged MERGE and RETURNING

SQLite 3.24+ supports table-to-table UPSERT through the same registry:

```java
// target includes its schema, columns and primary/unique key metadata.
// Populate temp.<target-name>_temp with the corresponding writable columns first.
var registry = dialect.createSqlFactoryRegistry();
var operations = registry.createSql(target, SqlType.MERGE_TABLE);
```

`TableOptions.tempTableName` selects the source name (default: target name plus
`_temp`). If the target's owning Schema contains that named table, the modeled
table is the source. Otherwise the source is explicitly in `temp`. A missing
temporary source fails at execution; it cannot silently read a same-named main
table. This API generates SQL and does not create, populate or drop staging tables.
Temporary-table creation's prefix/suffix settings are separate; create the source
with the name selected by `tempTableName`.

For an attached database or a source model outside the target's owning Schema,
select the factory and pass both models:

```java
var factory = (SqliteMergeTableFactory)
    registry.getSqlFactory(target, SqlType.MERGE_TABLE);
var operations = factory.createSql(target, stagingTable);
```

Source and target must have explicit schema/table identities and must refer to
different tables. Both identities are always qualified, including when general
schema decoration is disabled. Default column mappings require the source model
to contain each writable target column. Generated target columns are omitted;
identity values come from staging and are preserved on conflict. GENERATED ALWAYS
identity input is rejected. Key validation, collation-aware conflict targets and
update selection use the same implementation as MERGE and MERGE_ROWS.

`insertTableColumnValue` optionally maps input expressions; the source alias is
`_source_`. Nullable source expressions are wrapped with COALESCE when the target
column has a default, matching the shared table-merge convention. SQLite's
INSERT SELECT ambiguity is avoided with `WHERE 1=1` before ON CONFLICT. The source
is retained, and target rows missing from the source are retained. Duplicate
source keys follow native SQLite UPSERT behavior; this SQL generation API does not
validate or order source data. Use the bulk API when input duplicate validation is
required.

For SQLite 3.35+, enable RETURNING before obtaining the factory:

```java
registry.getTableOptions().setMergeTableWithReturning(true);
var operations = registry.createSql(target, SqlType.MERGE_TABLE);
```

The existing option applies to MERGE, MERGE_ROWS and MERGE_TABLE. Return columns
are selected through `returningColumnStrategy` (default: primary key). A missing
selected key fails clearly. Result sets contain inserted and updated rows; DO
NOTHING conflicts produce no result row. Result ordering is unspecified and must
not be used to correlate input rows by position. Old/new row aliases remain
unsupported and fail explicitly. Targets before 3.35 reject RETURNING; before 3.24
all three UPSERT forms are rejected.

`SqliteMergeTableFactoryTest` executes temporary, modeled and attached sources,
empty staging, input expressions, schema shadowing, default handling, identity
preservation and single/multi-row RETURNING. A constraint-failure test verifies
that an unsuccessful multi-row statement leaves no partial target updates.
Version-boundary tests run on the bundled current engine, not historical binaries.

## Appending and conditional inserts

`INSERT_SELECT_TABLE` appends source rows without updating existing target rows:

```java
var registry = dialect.createSqlFactoryRegistry();
var operations = registry.createSql(target, SqlType.INSERT_SELECT_TABLE);
```

It uses the same modeled source resolution, explicit schema identities, generated
column omission, column expressions and default-value handling as MERGE_TABLE.
The source defaults to `temp.<target-name>_temp` unless resolved from the target's
owning Schema. The corresponding factory also accepts an explicit source model:

```java
var factory = (SqliteInsertSelectTableFactory)
    registry.getSqlFactory(target, SqlType.INSERT_SELECT_TABLE);
var operations = factory.createSql(target, stagingTable);
```

The source need not have a key. Selected identity values are copied; to generate
new identities, exclude identity columns with `insertableColumn`. A duplicate key
or another constraint violation fails normally rather than updating or ignoring
the conflicting row. The factory does not create or remove staging data and does
not add a RETURNING clause from MERGE-specific options.

`INSERT_SELECT_NOT_EXISTS` is a single-input-row conditional insert:

```java
var nodes = registry.createSqlNodes(target, SqlType.INSERT_SELECT_NOT_EXISTS);
// Evaluate a node with the input Row and use the normal JDBC parameter binder.
```

SQLite SQL is generated directly as INSERT ... SELECT ... WHERE NOT EXISTS,
without a dummy FROM table. Matching columns use
`insertSelectNotExistsKeyColumnsMatchingStrategy`; conditions resolve reference
keys to the canonical Table columns before rendering parameter types. Matching
columns must be writable input columns. Generated columns are omitted, explicit
identity input is supported, and an unknown generated identity alone cannot be
used as a match key. Invalid or empty matching selections fail before returning
SQL.

If the selected columns match an existing row through SQL equality, that row is
left unchanged. NULL comparisons retain normal SQLite SQL semantics. This is a
selected-column existence check, not suppression of all possible unique-key
conflicts. Other uniqueness, NOT NULL, CHECK and foreign-key violations still
fail. Both forms are available before SQLite's 3.24 UPSERT boundary; they do not
emit ON CONFLICT syntax. `SqliteInsertSelectFactoryTest` exercises parameter
binding, composite and identity keys, existing-row preservation, attached names,
generated values, constraint failures and explicit source expressions. Historical
dialect selections are exercised on the bundled current runtime.

## Key-based UPDATE and DELETE

The standard registry UPDATE and DELETE operations resolve selected matching
references to the canonical Table columns before generating typed parameters.
The same internal resolver is used by conditional INSERT. This avoids depending
on complete object equality between a constraint reference and a table column.
No metadata queries or schema-model mutations are introduced by generation.

UPDATE excludes matching columns by name and omits generated columns, including
when an older dialect selection is used with a model containing formula metadata.
Shared value generation still handles identity, created-at, updated-at and
optimistic-lock columns. Existing `updateableColumn` and parameter-expression
settings remain available. If no writable non-key assignments remain, generation
fails with an actionable error instead of producing an empty SET clause. This
also applies to UPDATE_TABLE and UPDATE_FOR_APP.

Key-based UPDATE and DELETE retain the shared matching-column selection and
optimistic-lock conditions. Numeric version increments and stale-version checks
are preserved. Models without a usable key still fail under the default matching
strategy. Explicitly configured matching strategies retain their existing meaning.
Schema qualification continues to follow the ordinary SQL options.

UPDATE_TABLE retains its existing whole-table operation semantics: the optional
`updateAllCondition` callback supplies its filter, and omitting that callback does
not add a key filter. UPDATE_FOR_APP retains the shared dynamic application
conditions. These operations are not staging-table updates; use MERGE_TABLE to
reflect staged input. No new configuration is required. Existing public APIs and
XML formats are retained; empty-update failure is intentionally stricter.

The changes use traditional SQLite UPDATE/DELETE syntax across supported SQLite
versions and do not change resolver boundaries. `SqliteUpdateDeleteFactoryTest`
executes attached composite keys, generated-column exclusion, typed parameter
binding, stale update/delete checks, explicit whole-table filtering and column
selection. It also covers keyless and empty-update failures and model preservation.
Legacy selections run against the bundled current engine rather than old binaries.

## Application SELECT pagination

`SELECT_FOR_APP` now renders SQLite LIMIT/OFFSET using the existing parameters:

```java
var registry = dialect.createSqlFactoryRegistry();
var node = registry.createSqlNodes(table, SqlType.SELECT_FOR_APP).get(0);
var context = new ParametersContext(table);
context.put("_orderBy", "id");
context.put("_row", 20);
context.put("_offset", 40);
```

Evaluate the node with this context and use the shared JDBC parameter binder.
LIMIT alone, OFFSET alone, zero rows and omitted paging parameters are supported.
An offset without a limit generates `LIMIT -1 OFFSET ...`. Existing dynamic filters
and checked ORDER BY substitution are retained. For predictable pages, supply an
ORDER BY that resolves ties; the factory does not add a new implicit ordering.
See SQLite's [SELECT/LIMIT rules](https://www.sqlite.org/lang_select.html#limitoffset).

`context.setCountSqlMode()` returns the complete filtered count. It omits both
ordering and pagination even when `_row` and `_offset` remain in the context.
Native SQLite limits and parameter-validation errors are retained rather than
silently dropping an invalid LIMIT.

The shared `JdbcQueryHandler.execute(connection, context, limit, offset)` now
passes a non-null limit argument to a declared `_row` SQL parameter, just as it
already did for `_offset`. The explicit limit overrides an existing context limit;
a null argument preserves the context value. SQL without a declared row parameter
retains its client-side limit handling. This shared fix also benefits other
dialects using the standard paging parameters and introduces no SQLite condition
into core.

For the overload receiving already evaluated `SqlParameterCollection`, set paging
values in the context before evaluation. When the SQL declares `_offset`, this
overload now uses a normal result set rather than requesting a scrollable one.
The existing scroll fallback is retained for SQL without a native offset parameter.
This makes both public query-handler entry points usable with SQLite's forward-only
result sets; already evaluated SQL is not regenerated to change its paging values.

No public signatures, XML formats or resolver boundaries change. Native LIMIT
generation applies across supported SQLite versions. `JdbcQueryHandlerPagingTest`
covers common argument transfer and fallback behavior without a database.
`SqliteSelectForAppFactoryTest` executes filtered/count queries, zero/offset-only
pages, descending order and both query-handler paths on in-memory SQLite.

## Key SELECT, identifier quoting and lock requests

The normal registry SELECT operation now uses the same canonical matching-column
resolver as UPDATE, DELETE and conditional INSERT. Composite-key references are
resolved to actual Table columns before typed parameter generation. Key strategy,
projection and ordinary schema-decoration options retain their existing meaning.
Multiple-row and tree-query behavior is described below.

SQLite identifier storage is now reported as preserving mixed case, so
`nativeCaseString` retains the authored spelling rather than uppercasing names.
This does not make SQLite identifier comparisons case-sensitive.

Ordinary generated identifiers keep the existing bracket quoting. An identifier
containing a closing bracket uses double quotes instead, doubling embedded double
quotes. The generated alternate form is recognized as already quoted, and
`unQuote` correctly removes brackets or decodes that alternate form. Existing
handling of literal names enclosed in double quotes or backticks is retained;
these names are not generally reinterpreted as prequoted input. Already quoted
bracket names continue to pass through. See SQLite's
[identifier quoting rules](https://www.sqlite.org/lang_keywords.html).

This quoting is shared by the dialect's DDL, SELECT, other DML and full-name
helpers, including attached schema names. It does not change the shared SQL
parameter expression language. For SQL names that are not usable as expression
variables, the optional `parameterExpression` setting can emit native `?`
placeholders for direct JDBC binding.

`SqlType.LOCK` now fails with an actionable exception instead of returning an
empty operation list. SQLite does not provide LOCK TABLE or SELECT FOR UPDATE
syntax. The caller must choose and begin an appropriate connection transaction;
the factory does not silently begin a transaction, change its mode or invent a
row-lock guarantee. SQLite's [transaction modes](https://www.sqlite.org/lang_transaction.html)
describe the relevant engine behavior.

These changes apply across supported SQLite versions without new resolver
boundaries, dependencies or XML formats. Concrete SELECT and LOCK factories are
additive; mixed-case normalization, quoted-name decoding and unsupported-lock
failure are intentional behavior corrections. `SqliteSelectIdentifiersTest`
executes typed composite-key lookups in an attached database, metadata/XML/DDL
recreation with closing brackets and embedded quotes, mixed-case identifier
storage, quote round trips and old/current dialect lock rejection. Historical
selections use the bundled current SQLite engine.

### Multiple-row keys and tree queries

`SELECT_BY_ROWS` can now evaluate an ordinary `Table` (including its input rows)
without manufacturing a `TableRelation`. Existing relation contexts continue to
resolve current, parent and root key columns as before. Explicit ROOT/PARENT
targets require an actual, resolvable relation and fail with an actionable error
when it is missing.

An empty or absent input row set always contributes `AND 1=0`. It cannot remove
the key restriction from SELECT or DELETE. This is an intentional shared-core
behavior correction affecting every consumer of the `ROWS` SQL template node,
including `DELETE_BY_ROOT_ROWS`.

`SELECT_BY_ROOT_ROWS` and `DELETE_BY_ROOT_ROWS` also accept the root relation
itself: SELECT uses its existing alias, and DELETE directly restricts root keys
instead of trying to join a nonexistent parent. Descendant queries retain their
joins and correlated EXISTS predicates. Foreign-key enforcement and transaction
ownership remain with the connection and caller.

These fixes affect supported SQLite versions without changing resolver
boundaries, dependencies, public signatures or Schema XML. Tests execute
single/composite-key selection, empty inputs, related-child selection/deletion
and root selection/deletion against in-memory SQLite. Historical dialect
selections (3.7 and 3.15) run against the bundled current SQLite engine; old
SQLite binaries are not tested.

### Statistics and database maintenance

Use the existing factory registry as the entry point:

```java
var registry = dialect.createSqlFactoryRegistry();
var analyze = registry.createSql(new Table("orders").setSchemaName("main"), SqlType.ANALYZE);
var vacuum = registry.createSql(new Schema("main"), SqlType.DEFRAG);
```

`ANALYZE` accepts a named Schema, Table or Index. Table/index targets require
their schema (`main`, `temp` or an attached database); the generator always
qualifies them, independently of the decoration option. Missing identity fails
instead of analyzing every database or accidentally targeting a shadowing table.
SQLite syntax is `ANALYZE schema.object`, without a TABLE keyword. Schema targets
analyze the selected database. See [ANALYZE](https://www.sqlite.org/lang_analyze.html).

`DEFRAG` and `DEFRAG_FULL` accept a named Schema and both generate SQLite's
database-wide `VACUUM`. Table/index requests fail: SQLite cannot compact just
one table. Before SQLite 3.15, only `main` is accepted and emits plain `VACUUM`;
3.15+ emits the quoted schema name. This prevents old engines from silently
vacuuming main when another database was requested. `supportsVacuumSchema()`
exposes that capability through the existing version inheritance chain.

The factory only generates SQL. Callers execute VACUUM outside an active
transaction after releasing pending statements; it may fail for locks and can
change implicit rowids. It does not commit, retry, change pragmas or create
backups. See [VACUUM](https://www.sqlite.org/lang_vacuum.html).

No shared-core changes, dependencies or XML/configuration changes are needed.
`SqliteMaintenanceTest` checks statistics in attached/temp schemas, missing
targets, quoted names, transaction failure propagation and 3.14/3.15 resolution.
Execution uses the bundled current engine and in-memory databases; historical
SQLite binaries are not tested.

### Index reading on older engines

Before SQLite 3.16, the index reader now obtains catalog-visible indexes and
their CREATE SQL in one schema-qualified `sqlite_master` query, rather than
using driver-dependent JDBC index enumeration followed by a SELECT per index.
Table-name collections/arrays and the index-name filter apply to that query;
an unspecified schema resolves explicitly to `main`. Same-named indexes in
attached databases remain separate.

User-defined index keys are parsed from their CREATE SQL without extra queries.
Internal index key details use `index_info` before 3.8.9 and `index_xinfo` from 3.8.9 onward.
Unknown versions take the conservative `index_info` path. The boundary follows
the [3.8.9 release notes](https://www.sqlite.org/releaselog/3_8_9.html).
CREATE SQL retains explicit key order, collations, expressions and partial-index
predicates for recreation; trailing comments no longer prevent order parsing.
Quoted identifiers and collation names such as DESC are not order modifiers.
Internal indexes without CREATE SQL retain the key details the engine exposes;
their owning constraints remain the DDL representation, and internal indexes
are not created independently. WITHOUT ROWID primary storage has no separate
catalog index entry and remains represented by the table's primary key.

One key-detail PRAGMA per internal index remains necessary on pre-3.16 engines:
these indexes have no CREATE SQL, and those engines have no table-valued PRAGMAs
to join for bulk retrieval. Modern engines
retain the existing single joined query. See
[PRAGMA functions](https://www.sqlite.org/pragma.html#pragfunc).

Tests assert one catalog SELECT plus PRAGMAs only for internal indexes, cover
3.7/3.8.8/3.8.9/3.15 branches, attached-name collisions, filters and XML-to-DDL
recreation with NOCASE uniqueness and a partial predicate. They use the current
bundled engine, not historical binaries. No shared-core, dependency, resolver
or XML-format changes are needed; default-schema identity is now explicit in
both modern and legacy index results.

### Running the complete SQLite verification

SQLite is embedded: the JDBC in-memory and temporary-file tests execute a real
SQLite engine without a database server or Docker. Use Java 21 and the Gradle
Wrapper from the repository root:

```powershell
.\gradlew.bat :sqlapp-core-sqlite:test --console=plain
.\gradlew.bat :sqlapp-core:test :sqlapp-core-sqlite:test :sqlapp-core-postgres:test :sqlapp-command:test :sqlapp-gradle-plugin:test :sqlapp-core-dialect-test:dockerTest --tests '*sqlite.SqliteMetadataReaderTest' --continue --console=plain
```

Despite the last task's historical `dockerTest` name, the explicitly selected
`SqliteMetadataReaderTest` uses only `jdbc:sqlite::memory:` on the host. It does
not create a container. Do not treat that task name as evidence of Docker
execution, or the resolver's legacy-version branches as tests of old binaries.
The module currently uses sqlite-jdbc 3.53.1.0; native runtime compatibility is
limited to the bundled engine actually executed. No external database,
production data, Docker image or Compose setup is required for these checks.

The combined verification also covers `JdbcTreeDataSession.select(table)`.
That explicit root scan uses the registered `SELECT_TABLE` factory and retains
the configured order columns and optional select-all condition. It does not
reuse an empty `SELECT_BY_ROWS` key predicate. The latter still evaluates to
no matches for empty input, including deletion. Shared HSQL and SQLite tests
cover this distinction; the existing command tests cover copying/deleting
processed source batches and committing child writes with an unchanged root.
Root queries request holdable cursors only when the existing Dialect/JDBC
capability check supports them; SQLite uses close-at-commit cursors. When a
copy must reopen its root cursor after a commit, it consumes the buffered root
batch first and flushes its source/target work before reopening. This prevents
reprocessing loaded roots or losing source-to-target batch correspondence.
`TableRelation.hasBufferedRows()` adds non-advancing runtime introspection;
it does not change Schema XML. Existing public signatures and transaction
ownership are unchanged.

On 2026-10-20, final per-module results were core 1,203, SQLite 155, PostgreSQL
300, command 604, Gradle plugin 83 and host SQLite metadata integration 1:
2,346 tests with zero failures, errors or skips. The first broad command run
exposed four root-selection regressions; the integration test also retained
the old trigger-statement expectation. These were corrected, and the final
integration rerun passed. Trigger checks now round-trip the owning Schema XML
and recreate both main and attached triggers before exercising them. Earlier
failed development runs are not counted as passing results. The temporary
Docker experiment was removed; no Docker source changes are retained.

### Executable SQL scripts and trigger boundaries

`SqliteSqlSplitter` now scans SQLite tokens instead of matching a multiline
uppercase BEGIN/END pattern. It keeps the complete trigger body in one SQL
result, including lowercase/one-line definitions, TEMP/TEMPORARY, comments in
the header, nested CASE expressions and multiple trigger steps. Quoted strings
and double-quoted, bracketed or backtick identifiers keep their embedded
semicolons and comment markers. Standalone comments remain COMMENT results.

Adjacent statements no longer require whitespace after `;`. Transaction BEGIN
and END remain separate statements. A UTF-8 BOM at the start is accepted, and a
complete final SQL statement or trigger can omit its outer delimiter. EXPLAIN
prefixes preserve trigger grouping as well. Unterminated quoted tokens, block
comments and CREATE TRIGGER bodies throw `InvalidTextException` with a line and
position; malformed fragments are not returned as executable trigger steps.
The SQLite engine remains responsible for complete SQL syntax validation.
See [CREATE TRIGGER](https://www.sqlite.org/lang_createtrigger.html).

The public splitter API, Schema XML, dependencies and resolver boundaries are
unchanged across supported SQLite versions. This affects SQL-file execution
and migration consumers through the existing dialect splitter entry point;
no shared-core or command implementation changes are needed.

`SqliteSqlSplitterTest` executes split SQL, checks invalid input and round-trips
metadata through owning Schema XML and generated table/view/trigger SQL into a
fresh in-memory SQLite database. `SqliteScriptExecutionTest` additionally runs
the real `SqlExecuteCommand` against temporary UTF-8 SQL and SQLite files. It
checks trigger effects and confirms that a malformed trigger is rejected during
file parsing before earlier statements in that file execute. This is a parser
preflight check, not a general rollback guarantee for execution-time failures.
Both integration classes in the SQLite package run on the host without Docker:

```powershell
.\gradlew.bat :sqlapp-core-sqlite:test --console=plain
.\gradlew.bat :sqlapp-core-dialect-test:dockerTest --tests '*sqlite.*' --console=plain
```

Historical dialect selections use the bundled current engine; this does not
qualify old SQLite binaries. External databases and production data are not used.

### Query plans and bytecode diagnostics

`SqliteExplainBuilder` provides the SQLite counterpart to PostgreSQL's EXPLAIN
builder. Its default is a high-level query plan:

```java
var explain = new SqliteExplainBuilder(dialect)
    .statement("SELECT value FROM main.orders WHERE id=?");
String planSql = explain.build();             // EXPLAIN QUERY PLAN ...
String bytecodeSql = explain.bytecode().build(); // EXPLAIN ...
explain.queryPlan();                          // return to the default mode
```

It generates SQL only. Bind values and execute it with normal JDBC query APIs.
SQL generated from the Schema model can be supplied using a factory operation's
`getSqlText()`; existing sqlapp parameter expressions can then be evaluated with
the normal SQL parser/binder. Tests cover typed keys and attached database
identity through that path.

The builder reuses the dialect splitter and requires exactly one SELECT, WITH,
INSERT, UPDATE, DELETE or REPLACE statement. Leading/trailing comments and quoted
semicolons are accepted. Empty input, multiple SQL statements, an existing
EXPLAIN prefix, DDL, transaction commands and PRAGMA are rejected before SQL is
returned. Lexically incomplete input retains the splitter's positioned error.
SQLite notes that some PRAGMAs take effect while preparing SQL, which is why
this entry point excludes them. See [EXPLAIN](https://www.sqlite.org/lang_explain.html).

Plan/bytecode rows remain native JDBC results for interactive diagnosis; their
text and layouts are not converted into a supposedly stable application model.
SQLite can change diagnostic output across releases. See
[EXPLAIN QUERY PLAN](https://www.sqlite.org/eqp.html).

This is an additive SQLite API, with no new dependencies, shared-core changes,
XML/configuration changes or resolver boundaries. It does not validate every
SQL construct or the payload's version compatibility. Tests execute parameterized
plans and bytecode, CTEs and DML diagnostics, and check that the explained DML
does not insert, update or delete the fixture rows. Old/current dialect selections
retain the same prefix generation; historical engine binaries are not tested.

### Integrity diagnostics

`SqliteIntegrityCheckBuilder` generates a full, schema-wide check by default:

```java
var check = new SqliteIntegrityCheckBuilder(dialect);
String sql = check.build(); // PRAGMA main.integrity_check (identifier quoting may vary)
String attachedSql = check.schema(new Schema("archive")).maxErrors(20).build();
String quickSql = check.quick().build();
check.integrity(); // return to full checking, retaining the schema and limit
```

Execute the generated SQL with JDBC `executeQuery` and inspect every returned
row. Full/quick checks report `ok` when no errors are found, otherwise diagnostic
messages. The optional positive `maxErrors` limits reported errors; omission
retains SQLite's default. Schema names are quoted by the SQLite SQL builder;
`main` is the default, and `temp` or an attached database can be selected by name
or by the shared Schema model. Unknown schemas remain execution errors.

The quick check skips UNIQUE and index/table consistency checks. Neither mode
detects foreign-key violations: those require a separate `foreign_key_check`.
These methods generate diagnostic SQL only; they neither repair data nor change
constraint enforcement. They do not offer the newer per-table check syntax.
See [integrity_check](https://www.sqlite.org/pragma.html#pragma_integrity_check)
and [quick_check](https://www.sqlite.org/pragma.html#pragma_quick_check).

This additive API is confined to `sqlapp-core-sqlite`, with no dependency,
resolver, existing API, XML or configuration changes. The schema-wide syntax is
retained for legacy/current dialect selections; historical engine binaries have
not been tested. Local embedded-engine tests cover full/quick execution,
constraint diagnostics, error limits, attached-schema isolation, unusual names,
injection-shaped names, invalid options and unchanged fixture data.

SQLite syntax references:
[CREATE VIEW](https://www.sqlite.org/lang_createview.html),
[CREATE TRIGGER](https://www.sqlite.org/lang_createtrigger.html), and
[PRAGMA functions](https://www.sqlite.org/pragma.html#pragfunc).
Table syntax references: [CREATE TABLE](https://www.sqlite.org/lang_createtable.html)
and [AUTOINCREMENT](https://www.sqlite.org/autoinc.html).
Constraint behavior references: [foreign keys](https://www.sqlite.org/foreignkeys.html)
and [ON CONFLICT](https://www.sqlite.org/lang_conflict.html).
Native migration reference: [ALTER TABLE](https://www.sqlite.org/lang_altertable.html).
