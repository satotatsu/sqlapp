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
rows, appending one conflict clause to the statement. Delete-absent-source and
MERGE RETURNING options are rejected rather than ignored. MERGE_TABLE from a
staging table is not implemented by these factories. Streamed bulk execution
remains available through the existing bulk UPSERT API.

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

SQLite syntax references:
[CREATE VIEW](https://www.sqlite.org/lang_createview.html),
[CREATE TRIGGER](https://www.sqlite.org/lang_createtrigger.html), and
[PRAGMA functions](https://www.sqlite.org/pragma.html#pragfunc).
Table syntax references: [CREATE TABLE](https://www.sqlite.org/lang_createtable.html)
and [AUTOINCREMENT](https://www.sqlite.org/autoinc.html).
Constraint behavior references: [foreign keys](https://www.sqlite.org/foreignkeys.html)
and [ON CONFLICT](https://www.sqlite.org/lang_conflict.html).
Native migration reference: [ALTER TABLE](https://www.sqlite.org/lang_altertable.html).
