# SQLite schema object recreation

SQLite metadata uses the shared Schema model and the normal dialect entry points:

```java
var dialect = DialectResolver.getInstance().getDialect(connection);
var schemas = dialect.getCatalogReader().getSchemaReader().getAllFull(connection);
var registry = dialect.createSqlFactoryRegistry();
var operations = registry.createSql(view, SqlType.CREATE);
```

## Whole-Schema recreation and changes

Use the normal registry entry point to create the contents of a modeled SQLite
database after XML import:

```java
Schema restored = SchemaUtils.readXml(new StringReader(schema.asXml()));
var operations = registry.createSql(restored, SqlType.CREATE);
```

The SQLite Schema factory creates the contained objects using the existing
table/index/view/trigger factories and shared dependency ordering. It emits no
CREATE SCHEMA: main and temp belong to the connection, and any other named
database must already be attached by the caller. The factory does not infer a
filename, open a database, change foreign_keys or control transactions. A missing
attachment remains an execution error. Schema names must be explicit and valid;
an empty, named Schema produces an empty operation list.

Whole-Schema tests export metadata to XML, close the source connection, and
recreate it in another connection for main, temp and a quoted attached name.
The fixture combines FK dependencies, deferred/cascade rules, INTEGER identity
and ordinary INT primary keys, STRICT ANY, a generated column, a CHECK, an
expression/partial index, dependent views, a trigger, WITHOUT ROWID and FTS5.
They execute generated DML and native behavior checks while same-named tables
in other databases remain unchanged. The tests intentionally verify schema
recreation, not export of rows, virtual-table contents or sequence high-water
marks; fixture rows are inserted separately.

Existing Schema ALTER differences are also tested across two migration phases
inside a caller-owned transaction: replacing a CHECK/index and dropping their
old column, then appending a defaulted column and replacing the CHECK again.
Successful phases commit together. A failing second phase rolls both back,
restoring rows, the original column, CHECK and index; views and trigger behavior
remain usable. A CREATE failure at an existing view likewise rolls back objects
created earlier without removing that existing view. This adds no automatic
table-rebuild fallback or implicit rollback behavior in Schema ALTER.

## View and trigger differences

The normal registry also accepts `registry.createSql(original.diff(target), SqlType.ALTER)`
for views and triggers, including modifications inside a Schema difference.
Modified objects produce DROP followed by CREATE using the existing factories;
SQLite has no native ALTER VIEW or ALTER TRIGGER. Explicit database names are
required on both objects. This applies to supported SQLite versions without
adding a new engine syntax requirement or metadata query.

View changes use `statement` and modeled columns, or the complete `definition`.
When a definition is present, editing only statement/columns is rejected: edit
the definition or clear it first. Trigger body changes require the complete
CREATE TRIGGER definition; supplemental timing/event fields alone cannot replace
it. Identity changes preserve the body. Missing definitions and unsupported
catalog/enabled changes fail before operations are returned.

Execute the operations in a caller-owned transaction when atomic replacement is
required. Engine errors remain execution errors, and callers must roll back.
Tests execute replacements and renames in embedded SQLite, including quoted
attached names and rollback after an invalid trigger body.

DROP VIEW also deletes its INSTEAD OF triggers. When both objects belong to
modeled Schemas and retain the same identity, the factory appends CREATE for
each unchanged owned trigger after CREATE VIEW. Complete trigger definitions
must be present before any operations are returned. This also works for Schema
differences, with no extra metadata queries or new SQLite version requirement.
Tests cover multiple INSERT/DELETE triggers in main, temp and a quoted attached
database, including rollback after one trigger has already been recreated.

For a complete Schema difference, the SQLite Schema ALTER factory also coordinates
changes to owned triggers. It omits DROP for triggers implicitly removed by DROP
VIEW and recreates only those present in the target Schema. Added triggers use
the normal CREATE path, modified triggers still require a complete definition,
and unchanged triggers are restored once. Deleting a view together with its
owned triggers emits only DROP VIEW; retaining a trigger on that deleted view
is rejected. Caller models are not mutated, and coordination is local to each
invocation. Tests combine view replacement with trigger addition, modification,
deletion and preservation after Schema XML import, then verify native behavior
and rollback in main, temp and a quoted attached database.

For a same-database view rename, both direct view and complete Schema differences
accept explicitly rebound triggers in the target owning Schema. Change the view
name, set each retained trigger's modeled table/view identity to that new name,
and edit its complete CREATE TRIGGER definition to use that ON target. For
example, after cloning the owning Schema:

```java
var renamed = target.getViews().get("old_view");
renamed.setName("new_view");
var writer = target.getTriggers().get("view_write");
writer.setTable(new Table("new_view").setSchemaName(target.getName()));
writer.setDefinition("CREATE TRIGGER view_write INSTEAD OF INSERT ON new_view "
    + "BEGIN INSERT INTO source VALUES(NEW.val); END");
var operations = registry.createSql(original.diff(target), SqlType.ALTER);
```

A direct view difference requires both owning Schemas and all original owned
triggers to remain under their existing names with explicitly rebound complete
definitions. A complete Schema difference can additionally add/remove triggers.
A stale ON target or an explicit ON database inconsistent with the modeled
binding is rejected before returning operations. Identifier validation uses the
existing SQLite DDL lexer, including quoted names and comments. Trigger bodies
and literals are retained exactly; their references are not rewritten.

When keeping a view's identity, direct view differences still require unchanged
owned triggers; simultaneous changes belong in a complete Schema difference.
Cross-database moves with owned triggers require an explicit recreation plan.
The factories cannot discover dependencies omitted from the model, including
TEMP triggers in another Schema. Callers must explicitly update dependent view
queries and any references inside trigger bodies; use a complete Schema
difference to recreate those changed dependencies together. Tests cover this
path and caller rollback in main, temp and a quoted attached database. This is
DROP/CREATE composition, not native ALTER VIEW or a general SQL-reference rewrite.
Engine validation and transaction rollback remain the caller's responsibility.
No metadata queries or newer engine syntax are introduced; older SQLite dialect
resolution remains unchanged.
## Explicit table reconstruction

SQLite's [generalized ALTER procedure](https://www.sqlite.org/lang_altertable.html#making_other_kinds_of_table_schema_changes)
can now be invoked explicitly for an ordinary table:

```java
// target is the desired Table model with an explicit database and existing name.
var result = new SqliteTableRebuilder().rebuild(connection, target);
long retainedRows = result.rowCount();
var executedOperations = result.operations();
```

The connection must be an exclusively used SQLite JDBC connection in auto-commit
mode, with no caller transaction, active cursors or concurrently issued SQL.
The API resolves the actual dialect and requires SQLite 3.7.16+ for schema-wide
foreign-key verification. It does not replace or weaken normal ALTER validation
and is never an automatic fallback. Target XML/configuration formats and existing
public entry points are unchanged. This is an additive SQLite execution API,
not a persisted or externally replayable migration artifact.

It reads the current owning Schema inside its transaction, creates a uniquely
named replacement with the shared CREATE TABLE factory, copies same-named physical
columns with INSERT OR ABORT, drops the original, renames the replacement and
recreates target indexes with the existing factories. New columns use native
defaults; generated columns are recomputed. Column renames and custom copy
expressions are not inferred. Explicit column renames use the optional target-name
to source-name map:

```java
var result = new SqliteTableRebuilder().rebuild(connection, target,
        Map.of("new_name", "old_name"));
```

Map entries are column identifiers, not SQL expressions. Missing source/target
columns, duplicate resolved target names and generated target columns are
rejected. Unmapped columns retain same-name copying or native defaults. This
also supports explicitly mapped identity-column renames and composite keys;
dependent SQL definitions are not automatically rewritten. Tests preserve native
TEXT/BLOB/INTEGER storage values and roll back invalid STRICT conversions.
For type edits to read metadata, keep
`sqlite.declaredType` consistent with the intended type, since it preserves the
original native declaration. The vendor copy statement includes rowid handling
and OR ABORT, which are not represented by the shared staging INSERT path.

Explicit identity values and accessible implicit rowids are retained, including
when a real column shadows the name rowid. An AUTOINCREMENT high-water mark is
retained even for an empty table or after deletion of the largest ID. By default, rowid mode
must remain unchanged, and the identity column must retain its source identity
through either its unchanged name or the explicit mapping. WITHOUT ROWID tables are
supported when that mode is retained. Tests change column order, drop columns,
add defaults, UNIQUE/composite primary/FK constraints and STORED generated columns,
and apply native STRICT affinity conversion. No data export or Java value
conversion is involved.

Self-referencing foreign keys are supported, including multiple self keys,
implicit primary-key parent columns and composite keys on WITHOUT ROWID tables.
The scratch model's self-FK parent name and referenced-column models are explicitly
bound to the final table identity. They never point to the scratch name. Thus the
rebuild does not rely on SQLite automatically rewriting FK references when the
replacement is renamed. Other FK parent identities, actions and deferrability
remain modeled and are recreated by the existing FK generator. Self references
need no extra option or SQL-version boundary.

Tests rebuild self-reference trees, mutual cycles and self loops with deferred
constraints. They verify ON UPDATE CASCADE, ON DELETE CASCADE/SET NULL, incoming
child rows, views/triggers, mixed external/self parents and caller-model
immutability. Invalid replacement self keys fail the final foreign_key_check
and roll back data and DDL together. The matrix covers main, temp and a quoted
attached database, and both legacy_alter_table settings on the current engine;
this does not substitute for testing older engine binaries. Existing support
starts at SQLite 3.7.16, with individual table features retaining their existing
version requirements. Metadata readers and their query counts are unchanged.
The owning Schema's views and triggers and external TEMP views/triggers are
preserved. TEMP triggers retain their database-qualified owner even when main,
temp and attached databases contain same-named tables. Source and TEMP metadata
are fetched once per schema, with additional trigger-owner schemas read only
when required. Before copying, the API drops all involved triggers and then all
involved views, so DROP VIEW cannot implicitly
delete a trigger scheduled for a later DROP. This also prevents copy-time trigger
side effects. It recreates all those views and triggers after the table and
indexes, using the normal factories and retaining their queries/bodies. This
schema-wide treatment includes unrelated objects rather than guessing which SQL
fragments depend on the rebuilt table. No references are rewritten.

CREATE VIEW/TRIGGER alone does not validate every reference. Before commit, each
restored view is prepared with SELECT ... LIMIT 0, and each trigger-owner/event
pair is compiled with EXPLAIN INSERT, UPDATE or DELETE. Only events actually
present are compiled, allowing views with just one INSTEAD OF event. These
checks execute no DML or trigger bodies. UPDATE OF column names are separately
checked against the final owner model, since SQLite accepts nonexistent names
there. A deleted column still used by a view, trigger body or UPDATE OF list
therefore rolls the entire rebuild back. The Table overload preserves existing
dependencies; use the Schema entry point below to replace their definitions in
the same transaction. All validation completes before commit.

Validation prepares each view and distinct trigger-owner/event separately
because SQLite compiles one program per statement. These are validation queries,
not extra per-object catalog reads in a MetadataReader. Existing version-aware
bulk metadata retrieval remains in use. Tests exercise dependent views, ordinary
and INSTEAD OF INSERT/UPDATE/DELETE triggers, unrelated objects and copy-time
side-effect suppression in main, temp and a quoted attached database. They also
verify rollback for broken views, bodies, UPDATE OF lists and early/late failures.

On SQLite 3.37+, ordinary tables can be rebuilt in schemas containing virtual
tables. The existing table reader excludes shadow tables using PRAGMA table_list;
the additive `Sqlite.supportsTableList()` capability records this version
boundary. Unselected virtual tables and their shadows remain untouched.
Tests cover FTS5 and RTree data, native searches, dependent views/triggers,
AUTOINCREMENT and rollback in main, temp and quoted attached databases.
Before 3.37, a schema containing virtual tables remains conservatively rejected
because its shadow tables cannot be classified reliably. A metadata-version
simulation tests that rejection; it is not an older engine binary test.

Remaining exclusions include direct shadow-table reconstruction, unsupported
virtual-table modules, unverified contentless FTS, cross-database foreign keys and preserving
inaccessible rowids shadowed by all three aliases. Column mappings do not rewrite
SQL: supply final view/trigger queries and FK models through the Schema API;
invalid references cause validation failure and rollback. Other attached databases
are not rebuilt.
The API requires CHECK enforcement, writable_schema=OFF and rollback journaling
(journal_mode=OFF is rejected, including TEMP when its dependencies are involved).
It disables foreign
keys before beginning its owned transaction, checks existing and final schema
foreign keys even when enforcement was initially off, verifies row counts and
runs integrity_check before committing. The foreign_keys flag and JDBC auto-commit
are restored after success or successful rollback. Transaction-scoped settings
such as defer_foreign_keys follow SQLite's normal commit/rollback reset behavior.
Copy failures, invalid data, FK violations and late index-name collisions roll
back both data and DDL. IF NOT EXISTS is disabled so collisions cannot silently
omit a target object. Cleanup failures remain exceptions or suppressed exceptions;
a post-commit restoration failure explicitly reports that the rebuild committed;
a failed rollback never triggers an implicit commit, and that connection must
be discarded rather than reused.

The result retains the successfully executed replacement statements and verified
row count. It is not a data fingerprint, dry-run authorization or promise of
nonblocking execution. In-memory tests cover main, temp and quoted attached
names, same-named-table isolation, incoming FK children, sequence/rowid retention,
STRICT and WITHOUT ROWID behavior, new-FK validation and early/late rollback.
Older engine binaries are not executed by these tests; existing metadata version
resolution is retained.

### Coordinated Schema reconstruction

Pass a final Schema model to reconstruct its existing tables and replace its
views/triggers in one transaction:

```java
var result = new SqliteTableRebuilder().rebuild(connection, desiredSchema);
var retainedRowsByTable = result.rowCounts();
var executedOperations = result.operations();
```

The Schema's view and trigger lists are authoritative: omitted definitions are
removed, and new or changed definitions are created after reconstruction. Table
omission does not delete a table. This entry point requires existing table names;
it is not a general table-create/drop/rename migration. It supports one owning
database per transaction, including main, temp or an attached database. Tables,
views and triggers are cloned; the caller's model is unchanged. The actual
connection dialect is used even for Schema models without a dialect property.

Optional controls select a subset of tables and describe renamed source columns:

```java
var options = new SqliteTableRebuilder.Options()
        .tables("parent", "child")
        .sourceColumns("parent", Map.of("new_id", "id"));
var result = new SqliteTableRebuilder().rebuild(connection, desiredSchema, options);
```

Update the incoming child FK's referenced-column model and dependent queries in
`desiredSchema` along with the parent. A complete final TEMP view/trigger list
can be supplied using `options.temporaryDependencies(desiredTempSchema)` when
the owning database is not temp; otherwise the existing TEMP definitions are
preserved. TEMP table models in that option are not reconstructed. Missing or
duplicate selected tables, options for unselected tables and invalid TEMP schema
identities fail explicitly.

The executor stages and copies every selected table before dropping any original
table, then renames the replacements and creates indexes and final dependencies.
This supports incoming FKs, self references, mutually referencing tables and
external-content FTS whose backing table is changed in the same batch. Copy,
index, FK, view, trigger and integrity failures roll back the entire batch,
including TEMP dependencies. Triggers stay disabled throughout data copying.
Native view result metadata from the existing LIMIT 0 validation supplies inferred
column names for final INSTEAD OF trigger compilation; no per-view metadata
SELECT is added. The immutable result retains executed operations and verified
row counts for each selected table, after one successful commit.

### Explicit row identity changes

The default preserves physical row identity and rejects mode or identity-column
changes. For an intentional change, opt in per table:

```java
var options = new SqliteTableRebuilder.Options()
        .rowIdentity("t", SqliteTableRebuilder.RowIdentity.REGENERATE);
var result = new SqliteTableRebuilder().rebuild(connection, desiredSchema, options);
```

This permits rowid/WITHOUT ROWID conversion, INTEGER PRIMARY KEY identity
addition/removal/replacement and native assignment of new IDs. Existing mapped
or same-named column values are still copied; implicit rowids are not copied.
New INTEGER PRIMARY KEY columns use SQLite's native ID assignment. Old-to-new
ID correspondence and ordering are not promised. Update dependent keys explicitly;
remaining inconsistencies fail the schema-wide FK check. WITHOUT ROWID primary
keys must satisfy native NOT NULL/uniqueness constraints, and view/trigger queries
that still require rowid fail validation when rowid is removed.

AUTOINCREMENT high-water history is retained when its underlying copied identity
is retained. Adding AUTOINCREMENT initializes it from copied IDs; removing it
deliberately removes the AUTOINCREMENT allocation guarantee. A newly generated
identity does not inherit an unrelated old sequence. No settings or Schema XML
formats change. Ordinary reconstruction still starts at SQLite 3.7.16; feature
specific minimum versions, such as WITHOUT ROWID at 3.8.2, remain enforced.

### FTS and RTree reconstruction

On SQLite 3.37+, the same APIs reconstruct FTS3, FTS4 and FTS5 with recoverable
internal content, FTS4/5 with external content, RTree and RTree_i32.
Keep the complete native CREATE VIRTUAL TABLE DDL
in `definition` and matching visible columns in the Table model. To rename a
virtual-table column, change both and supply the copy mapping. Separate ordinary
constraints/indexes are rejected because the module owns those definitions.
Module changes require the explicit conversion option below. Raw shadow rows are never copied or edited by the
executor; the module creates its own shadow structures from native INSERTs.

FTS rowids/docids and RTree key values are preserved by default. RTree auxiliary data
is copied with SQL, and new columns use module defaults. FTS runtime configuration
(for example rank and automerge) is read once from each selected FTS index's
configuration table and restored with native module commands; these settings are
not represented in CREATE DDL. This execution-time module state retrieval is
separate from the unchanged batched Schema MetadataReaders.

FTS3/4 use their native `integrity-check` command before and after reconstruction;
it also detects stale external FTS4 content. FTS4's hidden languageid is copied
even though ordinary hidden control columns are omitted. Renaming the languageid
option preserves the original language values automatically; an explicit column
mapping can override that source. Removing a configured languageid is rejected
to avoid silently assigning every document to language 0. External FTS4 backing
tables must retain the configured language column and corresponding rowids.
Repeated `notindexed` options and tokenizer arguments remain in native DDL;
scalar content/language options are extracted without flattening the others.

Persistent FTS3/4 automerge is read from the native stat-table setting (id=2,
an integer) and restored using `automerge=N`, rather than copying stat/index rows.
FTS3 may have no stat table and FTS4 may have no automerge row; both retain native
defaults. The stat table's existence and setting are read only for selected
indexes during execution, outside the batched MetadataReaders. Unknown or invalid
setting representations fail explicitly. Compression/uncompression functions and
tokenizers named by the DDL must remain registered on the connection; the native
module applies them when reading and copying data. Tests exercise reversible
compression, Unicode tokenizer arguments, quoted names, negative/large docids,
language-specific MATCH queries, settings, views and rollback in main/temp/attached
databases. See the [FTS3/4 module documentation](https://www.sqlite.org/fts3.html)
and [native automerge storage](https://github.com/sqlite/sqlite/blob/master/ext/fts3/fts3_write.c).

The executor runs [FTS5 integrity-check with rank=1](https://www.sqlite.org/fts5.html#the_integrity_check_command)
before and after reconstruction, including checking external content against its
index. An already stale external index fails rather than silently changing its
meaning. Updated external backing columns must match the final FTS definition.
[RTree integrity checks](https://www.sqlite.org/rtree.html#integrity_check_using_the_rtreecheck_sql_function)
use `rtreecheck(schema, name)` before and after copying. Normal row-count,
dependency, FK and owning-schema integrity checks also apply.

Contentless FTS cannot supply its original indexed column values. FTS5 recovery
requires an explicitly authoritative source as described below; without one it
is rejected. FTS4 contentless and FTS5 contentless with columnsize=0 remain
excluded. Custom modules and direct shadow-table edits remain outside this API.
No older engine binaries are needed for the new tests: native
tests cover main/temp/quoted attached names, internal/external FTS content,
FTS settings, RTree searches, dependent objects and full rollback. Historical
engine binaries remain untested.

### Verified authoritative content for contentless FTS5

The Schema entry point can reconstruct contentless FTS5, including
contentless-delete and detail=full/column/none, when the caller explicitly
supplies authoritative content. The source must be an existing ordinary table or
view. By default its columns use the original FTS visible column names:

```java
// Retain the original visible columns while choosing a stored-content target.
desiredSchema.getTables().get("search")
        .setDefinition("CREATE VIRTUAL TABLE search USING fts5(body)");
var options = new SqliteTableRebuilder.Options()
        .tables("search")
        .recoverContent("search", new Table("documents"));
var result = new SqliteTableRebuilder().rebuild(connection, desiredSchema, options);
```

The supplied Table reference contributes its identity; its column metadata is
resolved inside the owned transaction. An omitted source schema means the target
database. An explicit schema can select another attached database. Source schemas
are read in batches, once per distinct source database. Document IDs default to
an INTEGER PRIMARY KEY identity or an accessible implicit rowid; a single integer
primary key on WITHOUT ROWID is also unambiguous. For views or other keys, supply
the document-ID column with `recoverContent("search", source, "document_id")`.
IDs must be native SQLite integers, unique and exactly match the old index's IDs,
including empty documents, negative IDs and large 64-bit values.

When authoritative columns have different names, provide an optional mapping
from original FTS column names to source column names:

```java
options.recoverContent("search", new Table("documents"), "document_id",
        Map.of("body", "document_text"));
```

Unmapped columns keep their original names. Keys must identify visible original
FTS columns; values must identify real source columns, never SQL expressions.
Names are resolved against actual metadata and quoted as identifiers. Unknown,
hidden, repeated or blank column mappings fail explicitly. The mapping is
snapshotted when configured. This mapping selects authoritative input only;
`sourceColumns("search", ...)` independently maps destination columns to original
FTS columns, so recovery and a destination rename can be combined without an
intermediate view or changes to the content source.

The executor captures the source once into typeless TEMP columns, preventing
affinity conversion of IDs or values. That same captured data is used for
verification and reconstruction. Thus a source view in the owning Schema remains
usable even when dependency views and backing columns are replaced later in the
batch. It creates a verification index using the original complete native FTS5
definition and captured content. Bidirectional native EXCEPT queries compare
document IDs and fts5vocab(instance) term/document/column/offset tuples. Native
docsize token-count blobs are compared without decoding or copying shadow rows;
this also detects changed term frequencies with detail=none/column. Native module
integrity checks still apply. Every comparison must pass before replacement DDL.

The source is authoritative for text spelling, whitespace, case and UNINDEXED
values: the original contentless index cannot prove byte-for-byte text identity.
Index equivalence is the validation contract. Equivalent token streams with
different spelling can be accepted, and the supplied text becomes the recovered
data. This API never fabricates missing text. See
[FTS5 contentless behavior](https://www.sqlite.org/fts5.html#contentless_tables)
and [fts5vocab](https://www.sqlite.org/fts5.html#the_fts5vocab_virtual_table_module).

Normal column mappings, module conversion, runtime setting policies and final
dependencies compose with recovery. A stored-content FTS can also be rebuilt
into a contentless FTS5 destination directly from its readable content. Target
contentless tables retain native document-size storage so final row counts can
be verified. contentless with columnsize=0 is rejected because document IDs
cannot be enumerated reliably by a full scan. An option for an unselected or
non-contentless table, missing columns/IDs, duplicate/non-integer IDs, changed
terms, offsets, token counts or document sets fails explicitly.

TEMP rollback journaling is required for captured content and vocabulary tables.
All helper objects are created in the owned transaction and removed on success;
verification, copy and late dependency failures roll them back with the original
data and DDL. Their operations are retained in the result. No per-row/column
metadata queries, new dependencies or XML formats are introduced. FTS5 recovery
uses the existing SQLite 3.37+ virtual reconstruction boundary, with each native
table feature retaining its own engine requirements. Older binaries remain
unexecuted.

`contentless_unindexed=1` reconstruction is currently rejected before DDL. With
the bundled SQLite 3.53.1, a direct native DROP leaves the content shadow table
behind, causing a stored-content replacement's rename to fail. A native regression
test reproduces this separately from the executor. The implementation does not
delete orphan shadow tables or modify sqlite_schema to work around this limitation.
The upstream [FTS5 drop implementation](https://github.com/sqlite/sqlite/blob/master/ext/fts5/fts5_storage.c)
provides relevant module context. No fixed-version boundary is assumed.

### Explicit module conversion

By default a rebuild keeps the existing module. To convert an existing table
within the FTS3/4/5 family or between RTree and RTree_i32, supply the destination
module's complete native definition and matching column models, then opt in:

```java
var options = new SqliteTableRebuilder.Options()
        .tables("t")
        .convertModule("t");
var result = new SqliteTableRebuilder().rebuild(connection, desiredSchema, options);
```

Column mappings and complete final view/trigger/FK definitions use the same
Schema entry point as ordinary reconstruction. No SQL references or module
arguments are automatically translated. In particular, supply destination
tokenizer, prefix, content and UNINDEXED syntax explicitly. Destination MATCH
syntax, ranking functions and query semantics may differ; preservation concerns
the copied data and verified identities, not equivalence of arbitrary searches.
FTS docids are copied using rowid, including negative and large 64-bit IDs.
Source and destination module integrity checks, dependency validation, row-count
checks and the owned transaction still apply. External FTS4/5 content and backing
column changes can be staged in the same batch before any original is dropped.

The default `ModuleSettings.PRESERVE_COMPATIBLE` transfers the effective automerge
setting with the destination module's native command. An unset FTS3/4 automerge
is 0; an unset FTS5 automerge is 4. Other explicitly stored FTS5 settings have no
general FTS3/4 equivalent and cause rejection. To deliberately discard all runtime
settings and use the destination module's defaults, choose:

```java
options.convertModule("t", SqliteTableRebuilder.ModuleSettings.DESTINATION_DEFAULTS);
```

This reset affects runtime settings, not the supplied CREATE definition or copied
data. FTS5 automerge=1 is not ported to FTS3/4, where the same command means 8.
Converting away from FTS4 languageid is allowed only when all rows use language 0;
nonzero language partitions cannot be represented by FTS3/5 and remain rejected,
even with a runtime-settings reset. Contentless FTS5 conversion requires the
verified authoritative-content option above; FTS4 contentless remains excluded.

RTree conversion verifies every copied value with a native EXCEPT query against
the staged replacement, before any source table is dropped. Fractional coordinates,
integer overflow or float32 rounding that changes a copied value fail and roll
back. Auxiliary values and keys are included in the comparison. RowIdentity's
default preserves keys; explicit REGENERATE still controls intentional identity
changes. There is no implicit permission for coordinate precision loss.

No-op conversion options, unselected table names, ordinary/virtual conversion,
FTS/RTree conversion and unknown module families fail explicitly. Supported
conversions retain the SQLite 3.37+ boundary used for virtual-table reconstruction;
older ordinary-table behavior, DialectResolver and metadata queries are unchanged.
See the native [FTS5 automerge](https://www.sqlite.org/fts5.html#the_automerge_configuration_option)
and [RTree coordinate representations](https://www.sqlite.org/rtree.html#integer_valued_r_trees).

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
incomplete operation. For a TEMP trigger with a resolved `tableSchemaName`,
CREATE generation qualifies an unqualified ON target using that schema. An
explicitly qualified ON target remains authoritative. References within the
trigger body retain their original SQL and SQLite name-resolution semantics.
Ordinary triggers and definition-only models without a resolved target retain
their original ON clause.

Trigger metadata distinguishes the trigger's owning schema (`temp` for a TEMP
trigger) from the schema of its ON target. Explicitly qualified targets retain
their schema even when main, temp and attached databases contain same-named
tables. Quoted target names, UPDATE OF columns and header comments are supported.
For unqualified TEMP targets, the reader resolves a unique table or view across
the connection's catalogs using database_list and one combined catalog query.
Reading one or many such triggers uses three queries in total; qualified and
ordinary triggers need only the original catalog query. An ambiguous unqualified
target fails with an instruction to recreate the trigger with ON schema.table,
because the catalog does not expose its bound database reliably after schema
changes. Metadata definitions retain the original ON clause; the CREATE factory
adds the resolved qualifier during SQL generation. This preserves the target
when XML is exported to a connection with additional same-named tables in main,
temp or attached databases. The destination must still attach the resolved
database under the same name. These queries use historical
sqlite_master and database_list interfaces and do not require a newer SQLite
version. Older engine binaries were not executed for this change.

The shared table-reference setters used by triggers and partition parents now
preserve the other identifier when changing a table name or schema. This also
preserves both identifiers when reading existing XML, without changing its
format or public method signatures.

Binding tests read an unqualified TEMP UPDATE OF trigger, export it through XML,
and recreate it in a different connection containing same-named tables in all
three database scopes. They verify that only the resolved table fires the
trigger, WHEN filtering and the body remain effective, and schema names
containing quotes are escaped correctly. SQL-generation tests also cover an
INSTEAD OF header, comments, an already qualified target and definition-only
models. No extra database queries are added by CREATE generation.

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

- `sqlite.declaredType`: original nonempty PRAGMA type declaration, including
  dimensions. It prevents INT or INTEGER(8) from becoming an
  INTEGER rowid alias, and preserves STRICT ANY storage behavior. The table
  factory uses this declaration preferentially; remove or update it when
  intentionally changing a read column's type.
- `sqlite.declaredType.empty=true`: an explicitly absent type declaration.
  A nonempty marker survives XML, unlike the previous empty-string map value.
  This prevents false ALTER differences after XML import, including unchanged
  FTS tables. Remove this marker when intentionally assigning a type.
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

### STRICT ANY values

On SQLite 3.37+, an `ANY` column in a STRICT table preserves the value's storage
class instead of coercing it to a numeric value. The column reader maps this
case to the existing `DataType.ANY_DATA`, and the SQLite dialect supplies an
`ANY` handler using the existing pass-through JDBC converter. For example,
putting the string `"000123"` in a modeled Row and inserting it through generated
DML keeps both the leading zeroes and SQLite's `text` storage class. Integers,
real values, byte arrays and NULL retain their respective native storage
classes. See [SQLite STRICT tables](https://www.sqlite.org/stricttables.html).

The original `sqlite.declaredType` remains available for DDL, and XML preserves
the generic type through the existing model format. Both bulk and legacy
column-reading paths inspect the table definition without extra queries.
Ordinary non-STRICT `ANY` columns retain their existing NUMERIC metadata and
SQLite numeric affinity. A STRICT keyword inside a string or comment does not
enable this behavior. Existing STRICT version validation and DialectResolver
boundaries are unchanged; no shared-core code or public signatures change.

Tests cover XML/DDL recreation followed by generated INSERT for all five SQLite
storage classes, ordinary ANY coercion, and generated UPDATE/SELECT/DELETE that
distinguish a text ANY primary key `"0001"` from an integer key `1`. Legacy-reader
SQL is tested on the bundled engine, not historical SQLite binaries.

### Dynamic view column values

The SQLite column reader uses `DataType.ANY_DATA` for view columns reported with
an empty type, ANY or BLOB. An expression can return different storage classes
per row, and the bundled SQLite engine reports a projected STRICT ANY column
as BLOB. Catalog metadata alone cannot reliably distinguish that projection
from an actual binary column. Pass-through conversion preserves text (including
leading zeroes), integers, real values, byte arrays and NULL in both cases.
The original reported type remains in `sqlite.declaredType`; this intentionally
changes the model type of view BLOB columns, while ordinary table BLOB columns
and known view TEXT/integer types keep their existing mappings.

For empty declarations the `sqlite.declaredType.empty=true` marker is used
instead. The SQL builder renders stored declarations directly, including the
empty case, rather than letting a Schema-inherited dialect normalize INT to
INTEGER. Common identity semantics remain in `Column.isIdentity()`: an INTEGER
rowid alias reads as true and an ordinary INT primary key reads as false.
This is metadata/SQL-generation behavior within the existing XML format;
historical SQLite engine binaries are not required or executed by these tests.

Both bulk and legacy column paths use the object kind from their existing
catalog query. No query is added and view definitions are not parsed to guess
source identities. Reading one or eight views on the bulk path uses one view
catalog query and one column query. XML and public signatures remain unchanged.
Tests round-trip a view through XML, recreate it, read its mixed direct/CASE
values into modeled Rows, and insert them into another STRICT ANY table with
generated parameterized SQL, verifying both storage classes and values. Legacy
reader SQL is executed on the bundled engine; old binaries are not tested.

### Index database identity

An index with a resolved model schema always qualifies its index name, including
`main`, even when `decorateSchemaName=false`. SQLite uses the index-name
qualifier to select the database; the ON table name must stay unqualified.
Without `main` qualification an existing TEMP table of the same name can receive
the index instead, leaving the intended main table without UNIQUE enforcement.
This rule applies to standalone CREATE INDEX, table CREATE/XML recreation and
ALTER index creation through the shared SQLite index factory. A model without
a schema retains SQLite's native resolution; no schema is guessed. No metadata
queries, API signatures, XML formats or version boundaries change.

Embedded-engine tests cover both schema-decoration settings, main/temp/quoted
attached names, partial expression UNIQUE indexes after XML round-trip, and
unchanged enforcement in the other databases. SQL selected by a legacy 3.7.16
dialect is also executed on the bundled engine; historical binaries themselves
are not tested.

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

New columns must form a suffix of the target model's column list. Inserting a
column before or between retained columns is rejected before SQL is returned,
because native ADD COLUMN always appends. Multiple appended columns retain the
model's order. VIRTUAL generated columns may be appended with NOT NULL and no
default; the ordinary-column requirement for a non-NULL default does not apply
to generated values. STORED additions remain rejected. SQLite 3.37+ validates
the new generated-column NOT NULL constraint against existing rows and can reject
execution; the factory does not suppress that error. See
[ADD COLUMN](https://www.sqlite.org/lang_altertable.html#alter_table_add_column).
Tests cover attached-table data, column ordering, recomputed generated values,
failed NOT NULL validation and the generated-column version/storage guards.
The shared checkpoint-store upgrade also appends its new RESUME_TOKEN and
CHUNK_SIZE columns to the original model instead of requesting insertion into
the middle of an existing table. Existing checkpoint identifiers, values and
configuration formats are unchanged.

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

Changes to column types/defaults, non-CHECK constraints, table options or existing
column order are rejected with a rebuild instruction. New columns must form a
suffix of the target column list, matching SQLite's append-only behavior.
Unsupported ADD forms (identity, STORED generation,
NOT NULL without a non-NULL default, parenthesized defaults and CURRENT_* defaults)
are also rejected. Table rename must be separate from other changes. Ambiguous
column delete/add combinations are rejected rather than risking data loss.
Nullability-only changes use native ALTER COLUMN on SQLite 3.53+ and are rejected
on earlier targets. Calling ALTER with only a target table fails instead of returning
an empty result. Errors occur before an operation list is returned.

DROP TABLE/VIEW/INDEX/TRIGGER targets the model's schema even with default
decoration options. DROP TABLE does not emit unsupported CASCADE CONSTRAINTS,
and `supportsDropCascade()` now returns false. Shared table dependency sorting
is retained. SQLite internal index drops and unsupported constraint CREATE/DROP
requests fail explicitly; named CHECK changes are available on 3.53.1+ and inline
CREATE TABLE constraint details still work.

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
are additive. Automatic rebuild fallback from ALTER and SQLite extensions remain
outside this native SQL path; explicit reconstruction is described above. PostgreSQL-only objects
such as stored procedures and sequences are not added to SQLite.

## Version capabilities and DML

Resolve a dialect from the JDBC connection to select the engine's capabilities.
The version classes inherit the nearest compatible predecessor, with these
boundaries:

| SQLite version | Added capability |
| --- | --- |
| 3.7.6 | Explicit WAL checkpoint modes |
| 3.7.16 | Foreign-key diagnostics (`foreign_key_check`) |
| 3.8.0 | Partial indexes |
| 3.8.2 | WITHOUT ROWID |
| 3.8.3 | WITH and recursive CTEs |
| 3.8.8 | WAL checkpoint TRUNCATE |
| 3.9 | Expression indexes and view column lists |
| 3.15 | Row value comparisons and IN |
| 3.18 | PRAGMA optimize |
| 3.24 | Bulk UPSERT |
| 3.25 | Column rename |
| 3.27 | VACUUM INTO |
| 3.31 | Generated columns |
| 3.32 | Default bind parameter ceiling increases from 999 to 32766 |
| 3.35 | INSERT RETURNING and DROP COLUMN |
| 3.37 | STRICT tables |
| 3.46 | Optimize checks for unused tables with temporary analysis limits |
| 3.53 | ALTER COLUMN SET/DROP NOT NULL |
| 3.53.1 | Native named CHECK changes with corrected quoted-name removal |

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

### Explicit database identity for DML

SQLite DML always qualifies a modeled table with its explicit schema, even with
`decorateSchemaName=false`. Otherwise a same-named TEMP table can shadow the
intended main/attached table, causing reads and writes to reach the wrong data.
This covers INSERT/INSERT_ROWS, MERGE/MERGE_ROWS, keyed and application/whole-table
UPDATE, keyed/whole-table/application DELETE, keyed/whole-table/application SELECT, SELECT_BY_ROWS and
SELECT_BY_ROOT_ROWS/DELETE_BY_ROOT_ROWS. Modeled source-table INSERT and MERGE
already require explicit source/target identities and retain that behavior.

A DML-only SQLite builder applies this rule to table names and preserves column
names and aliases. DDL builders remain separate because, for example, CREATE
INDEX requires an unqualified ON table name. Shared factories still provide the
key predicates, empty-row guards, relation traversal, conditions and pagination;
SQLite supplies only the name-rendering policy. Registry options and input
models are not mutated. A model without a schema retains native SQLite name
resolution; the factory does not invent a database identity.

Embedded-engine tests exercise both decoration settings with same-named tables
in main, temp and an attached database containing a quote in its name. They
verify writes and untouched shadow tables, row-set/root-relation reads and
deletes, empty-key deletion guards, paginated and count queries, and application
and whole-table update conditions. Additional tests select legacy 3.7/3.15
dialects and execute their generated SQL on the bundled engine. Historical
binaries are not tested. This changes generated qualification, with no existing
method-signature, XML/configuration-format, dependency or version-boundary change.

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
the conflict target. Expression and partial unique indexes require explicit
selection as described below. If a generated identity is the only key, explicit identity input
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

## Explicit SQLite UPSERT index targets

For a partial or expression unique index, select its modeled name explicitly.
The shared Schema remains the source of its key SQL, collations, ordering and
partial-index predicate:

```java
var registry = (SqliteSqlFactoryRegistry) dialect.createSqlFactoryRegistry();
registry.setMergeConflictIndexName("active_email_unique");
var operations = registry.createSql(table, SqlType.MERGE);
// The same setting applies to MERGE_ROWS, MERGE_TABLE and createSqlNodes.
```

For several tables, use `setMergeConflictIndexSelector(table -> ...)`, returning
the modeled index name for each table or null to retain the shared key strategy.
`setMergeConflictIndexName(null)` restores shared selection. This is optional
SQLite SQL-generation configuration; Schema XML and common TableOptions formats
are unchanged. Direct factory users can use `SqliteMergeFactory.setConflictIndexName`
or `setConflictIndexSelector` before calling that factory's `createSql`. Registry
lookups create fresh factories, so configure the registry when using its normal
SQL/node entry points.

The selected index must exist on the model and be unique. Invalid, blank or
nonunique selections fail without falling back to another key. Expression keys
require the retained `sqlite.index.keys` SQL; partial targets append the modeled
`where` after the conflict key. Metadata readers already preserve both through
XML. Ordinary modeled indexes can also use their physical column list when no
retained key SQL is present. Physical index key columns must be writable inputs;
expression inputs are evaluated by SQLite from the inserted values and defaults.
The factory does not parse expressions to infer their source columns. Writable
expression input columns may be updated through `excluded`, while physical key
columns, primary keys and identity columns remain excluded from assignments.

This requires SQLite 3.24+ like the existing UPSERT implementation. There is no
metadata query, newer syntax requirement or automatic index selection change.
Other uniqueness/FK/check violations still fail, and ordinary transaction rules
apply. In-memory tests cover partial, expression and combined targets for all
three MERGE forms in main, temp and a quoted attached database after XML import.
They verify insert/update behavior, partial-predicate exclusion, preserved IDs
and FK children, multi-row statement atomicity, DO NOTHING and NULL uniqueness.
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

### Native named CHECK changes

SQLite table differences execute dependency drops first, column changes second,
and constraint/index creation last, preserving the order within each phase.
In particular, replacing an existing index while dropping one of its old key
columns removes the old index before DROP COLUMN. This also covers expression
keys and partial-index predicates, including when a CHECK is replaced in the
same difference. The factory still generates the complete plan before returning
it and performs no database queries, execution or transaction management.
Use a caller-owned transaction to roll back all phases if recreation fails.
Tests cover attached-schema isolation, retained row values, replacement CHECK
enforcement, and rollback after both successful and failed combined changes.
DROP COLUMN retains its SQLite 3.35+ requirement; named CHECK changes retain the
3.53.1+ requirement below. Unchanged dependent objects and external view/trigger
dependencies are not automatically rewritten or removed; SQLite execution
errors remain visible. No new API or XML/configuration format is introduced.

SQLite 3.53.1+ supports named CHECK changes through both existing entry points:

```java
var target = original.clone();
target.getConstraints().add(new CheckConstraint("positive", "value>0"));
var operations = registry.createSql(original.diff(target), SqlType.ALTER);
// Or supply a CheckConstraint with schema/table/name identity directly:
var standalone = registry.createSql(target.getConstraints().get("positive"), SqlType.CREATE);
```

CREATE adds a named CHECK, DROP removes it, and a Table difference can add,
remove or replace a CHECK expression. Replacement emits DROP then ADD. Execute
multi-statement changes in a caller-owned transaction if atomicity is needed;
the factory does not commit or roll back. Existing rows are validated by SQLite,
and failure is a native JDBC error. Tests show caller rollback restores the
original CHECK when the replacement fails. Table/constraint names retain attached
schema identity and use the SQLite identifier renderer.

Other constraint types still require rebuilding. Native operations require
explicit schema/table/constraint names and reject virtual targets or deferred
CHECK configuration. Metadata marks anonymous CHECK names with
`specifics["sqlite.check.unnamed"]="true"`; the existing synthetic name remains
available for documentation/XML, but native DROP rejects it because it is not
an actual declared constraint name. Recreating such a CHECK as a named constraint
requires rereading metadata before dropping it by name.

Although CHECK ALTER syntax arrived in 3.53.0, this API conservatively requires
3.53.1 for both directions because quoted DROP names were fixed after 3.53.0.
Unknown 3.53 patch levels remain conservative. The resolver adds 3.53.1 and
`supportsAlterCheckConstraints()`, and defaultDialect now selects that version.
References: [3.53 release](https://www.sqlite.org/releaselog/3_53_0.html),
[quoted-name fix discussion](https://sqlite.org/forum/forumpost/058870f83a?t=h),
[3.53.1](https://www.sqlite.org/releaselog/3_53_1.html).

Tests execute standalone and difference-based changes, quoted attached identities,
failed replacement plus rollback, anonymous-name XML retention and old/unknown
target rejection. This uses existing CheckConstraint fields and SQL types;
there are no shared API, dependency or XML/configuration format changes.

### Native column nullability changes

SQLite 3.53+ table differences support changing a column's `notNull` property
without rebuilding the table. Use the existing Schema model and ALTER entry point:

```java
var target = original.clone();
target.getColumns().get("value").setNotNull(true);
var sql = registry.createSql(original.diff(target), SqlType.ALTER);
```

Setting NOT NULL emits ALTER COLUMN SET NOT NULL; clearing it emits DROP NOT
NULL. Existing `sqlite.notNull.onConflict` policies are rendered and validated
when setting NOT NULL. Only nullability may change in that column operation;
combined type/default/name changes still fail before a SQL list is returned.
Virtual tables retain their module-specific ALTER rejection.

The target schema/table/column identities are quoted through the SQLite builder,
including attached databases. Existing NULL values can make SET NOT NULL fail at
execution; native JDBC errors remain visible and the caller controls transactions.
No data replacement, implicit commit or automatic fallback rebuild is performed.
SQLite can retain redundant NOT NULL constraints after one DROP operation; native
ALTER does not guarantee removal of every duplicate declaration. See
[ALTER COLUMN](https://www.sqlite.org/lang_altertable.html#altertabaltcol).

The resolver adds the [3.53 boundary](https://www.sqlite.org/releaselog/3_53_0.html),
inheriting 3.46 capabilities. `supportsAlterColumnNullability()` gates generation
on older targets; defaultDialect now selects 3.53.1. When working from metadata,
the model's attached dialect participates in factory selection, so assign the
intended target dialect to models when targeting an older engine. No dependencies,
shared APIs, XML or configuration formats change.

Tests execute against the bundled SQLite 3.53.1 engine and verify SET/DROP,
metadata after changes, attached isolation, existing NULL failures, conflict
behavior, rollback and rejection of older/combined requests. Historical binaries
are not tested.

### Database attachment

`SqliteAttachmentBuilder` generates connection-local ATTACH and DETACH SQL:

```java
var attachments = new SqliteAttachmentBuilder(dialect);
var archive = new Schema("archive");
String attachSql = attachments.attach("archive.db", archive);
String detachSql = attachments.detach(archive);
```

Both methods also accept a schema name directly. File and schema names are
required; blank/NUL input and the reserved names main/temp are rejected. Filename
is escaped as a string literal, schema as an identifier. The builder performs no
filesystem or database operations. Execute with JDBC on the connection that will
read or write the attached schema. Attachment does not apply to other connections.
Reusing that Schema name lets the existing metadata readers and SQL factories
target the attachment.

`:memory:` creates a connection-local memory attachment. Relative paths and
enabled SQLite URI filenames retain native SQLite semantics. This entry point
does not expose the empty-filename temporary-database form. It does not manage
files, delete them on detach, configure URI/open flags or change transaction
settings. Duplicate aliases, missing attachments, access errors and locked
attachments remain native JDBC failures. Finish active work before detaching;
the caller controls commit/rollback. Atomicity across multiple database files
retains SQLite's documented restrictions for WAL and in-memory main databases.
See [ATTACH](https://www.sqlite.org/lang_attach.html) and
[DETACH](https://www.sqlite.org/lang_detach.html).

This additive SQLite API uses existing Schema identities, without new
dependencies, XML/configuration changes or resolver boundaries. Tests execute
quoted file/schema names, metadata plus typed SELECT, persistent detach/reattach,
connection isolation, duplicate/missing aliases and caller-controlled rollback.
Legacy selections generate the same SQL; historical engine binaries are not tested.

### Virtual tables and shadow tables

Table metadata now identifies CREATE VIRTUAL TABLE and stores the complete
executable DDL in `definition`, plus `specifics["sqlite.virtual"]="true"`.
Module arguments are preserved verbatim, rather than interpreted as ordinary
table constraints. Available PRAGMA columns, including hidden columns, remain
supplemental model metadata. Recreating a virtual table through the existing
CREATE factory emits one complete statement with the model's current schema and
table name. Supplemental columns/indexes/constraints do not replace the module
definition. Edit the definition to change module arguments.

As with trigger definitions, either readDefinition or readStatement enabled is
enough to retain executable DDL. Disabling both preserves the virtual marker but
omits DDL, so CREATE fails clearly instead of creating an ordinary table.
Generic ALTER and explicit column rename reject virtual tables because their
supported operations depend on the module. The generic ALTER path does not copy
virtual-table data. Use the explicit FTS/RTree reconstruction API described above
when changing a supported module's definition and copying its data atomically.

On SQLite 3.37+, table enumeration excludes objects classified as `shadow` by
`pragma_table_list`, using one schema-filtered catalog query. It retains ordinary
tables with similar names; it does not guess from FTS/RTree filename suffixes.
Full reading batches virtual columns together and avoids treating module
arguments as constraints. Query-count tests keep the same prepared-query count
for one and five FTS tables. See
[table_list](https://www.sqlite.org/pragma.html#pragma_table_list) and
[CREATE VIRTUAL TABLE](https://www.sqlite.org/lang_createvtab.html).

Older runtimes retain catalog enumeration without table_list. Virtual DDL is
still retained, but shadow tables can appear as ordinary catalog entries there:
before 3.37 there is no general public classification covering arbitrary custom
modules, so schema exports containing virtual tables need an explicit object
selection or a newer runtime. No per-object classification queries were added.
The branch uses the connected engine's version, independently of dialect target
selection; resolver boundaries remain unchanged by this metadata change.

This stays in `sqlapp-core-sqlite` with no dependencies, shared model/API or XML
format changes; definitions and specifics use existing model fields. Tests cover
FTS5 search after an attached-schema XML round trip, hidden columns, RTree spatial
behavior, renamed identities, shadow exclusion, ordinary name collisions,
constant query counts, old enumeration and missing-DDL rejection. The destination
must provide the required module/tokenizer. External-content FTS and custom
modules may require separately managed tables, data, functions or registration;
their arguments are not rewritten when renaming the table. Historical binaries
and arbitrary third-party modules are not tested.

#### Virtual-table DML

SQLite INSERT factories now omit `Column.hidden` input columns, including
single/batch INSERT, conditional INSERT and table-source INSERT. UPDATE omits
hidden assignments as well. This prevents FTS control columns such as the table's
name and `rank` from becoming ordinary application inputs. Hidden columns remain
available as supplemental metadata; module control commands require explicit
module-aware SQL rather than ordinary generated DML.

FTS3/4/5 columns with no declared PRAGMA type use VARCHAR for row conversion and
JDBC binding, while retaining the empty `sqlite.declaredType`. This avoids
treating application text as Base64/BLOB input. Both bulk and legacy column
readers apply this mapping; ordinary typeless tables retain BLOB affinity, and
other modules' declared types are unchanged. Module identification parses the
CREATE header, including IF NOT EXISTS, schema qualification and quoted names.
See the [FTS5 documentation](https://www.sqlite.org/fts5.html).

MERGE, MERGE_ROWS and MERGE_TABLE reject virtual targets before generating an
UPSERT, since SQLite does not support UPSERT on virtual tables. The check accepts
either the metadata marker or a complete virtual-table definition. Normal
INSERT, UPDATE and table-source transfer retain the existing factory entry points
and parameter binder. Virtual-table row identity is module-specific: keyed
updates require an explicitly modeled key, such as SQLite rowid when applicable;
the reader does not invent one. See [UPSERT limitations](https://www.sqlite.org/lang_upsert.html#limitations).

Tests execute FTS5 text inserts and rowid-keyed updates with supplied hidden
control values, transfer attached FTS rows from a visible-only staging table,
reject all MERGE entry points, and verify FTS3/4/5 type mappings across modern and
legacy metadata branches. Changes stay in SQLite; no shared APIs, dependencies,
configuration/XML formats or resolver boundaries are changed.

### Statistics optimization

`SqliteOptimizeBuilder` generates `PRAGMA optimize` for one database. The default
schema is `main`; use a name or the shared Schema model for an attached database:

```java
var optimize = new SqliteOptimizeBuilder(dialect);
String sql = optimize.build();
String startupSql = optimize.schema(new Schema("archive"))
    .includeAllTables(true).build();
```

The default retains SQLite's native optimization decisions. Optional
`includeAllTables(true)` considers tables unused by the current connection; it
does not unconditionally re-analyze every table. Its mask is `0x10012`: ANALYZE,
a temporary analysis limit, and consideration of all tables. This retains the
runtime limit when expanding consideration beyond recent query history.
`includeAllTables(false)` returns to native defaults. Schema names are quoted;
blank/NUL names are rejected, and the SQL never implicitly expands to all
attached databases. See [optimize](https://www.sqlite.org/pragma.html#pragma_optimize).

The builder only generates SQL. Execute it with JDBC and retain native errors;
the caller owns execution, transaction boundaries and scheduling. It neither
changes connection settings itself nor promises that a particular index will
receive fresh statistics. SQLite's heuristics and defaults vary by version.
This complements the existing ANALYZE factory rather than replacing explicit
ANALYZE requests.

The resolver adds [3.18](https://www.sqlite.org/releaselog/3_18_0.html) for optimize
and [3.46](https://www.sqlite.org/releaselog/3_46_0.html) for all-table consideration
with temporary limits. Earlier targets reject the unsupported operation.
`DialectHolder.defaultDialect` includes 3.46 optimize capabilities and now selects
3.53.1 as described above. Use a dialect resolved from the connection for older engines.
Changes stay in `sqlapp-core-sqlite`; no dependencies, shared APIs, XML or
configuration formats change.

Embedded tests verify attached-only statistics, unchanged table data, restored
analysis_limit, caller-controlled rollback, native schema errors, quoting and
both version boundaries. Historical engine binaries are not tested.

### WAL checkpoints

`SqliteWalCheckpointBuilder` generates one explicitly scoped checkpoint, with
`main` and nonblocking PASSIVE as defaults:

```java
var checkpoint = new SqliteWalCheckpointBuilder(dialect);
String sql = checkpoint.build();
String attachedSql = checkpoint.schema(new Schema("archive"))
    .mode(SqliteWalCheckpointBuilder.Mode.TRUNCATE).build();
```

`schema(String)` also accepts an attached database name. Names are quoted; blank
or NUL-containing names are rejected. The builder emits SQL only and does not
enable WAL, change busy timeouts, execute, commit or retry. Unlike an unqualified
checkpoint PRAGMA, it never expands the request to every attached database.
The caller controls the connection and inspects the JDBC `executeQuery` result.

The result contains one row with three integers: busy status, log pages and
checkpointed pages. FULL, RESTART and TRUNCATE may report busy as 1. PASSIVE can
report 0 while leaving pages uncheckpointed, so compare the page counts as well.
Counts of -1 indicate no WAL. FULL/RESTART/TRUNCATE may wait according to the
connection's busy handler; TRUNCATE shrinks the WAL only on successful completion.
Non-WAL databases retain SQLite's no-op behavior. Native JDBC errors remain
visible. See [wal_checkpoint](https://www.sqlite.org/pragma.html#pragma_wal_checkpoint).

This API requires SQLite 3.7.6+ for explicit modes; TRUNCATE requires 3.8.8+.
The resolver adds these boundaries while inheriting prior capabilities. Unknown
3.7 revisions reject generation, and unknown 3.8 revisions reject TRUNCATE.
Earlier unparameterized checkpoint syntax and newer NOOP mode are not exposed.
References: [3.7.6](https://www.sqlite.org/releaselog/3_7_6.html),
[3.8.8](https://www.sqlite.org/releaselog/3_8_8.html).

Changes are additive and confined to `sqlapp-core-sqlite`, without dependency,
shared-core, XML or configuration changes. Local file-based tests exercise all
four modes, WAL truncation, attached isolation, partial PASSIVE progress, busy
reader status, caller retry after releasing a snapshot, non-WAL counts, invalid
names and version guards. Historical SQLite binaries are not tested.

### Compact database copies

`SqliteVacuumIntoBuilder` generates SQLite 3.27+ `VACUUM INTO` for a separate,
compacted copy of one database:

```java
String sql = new SqliteVacuumIntoBuilder(dialect)
    .destination("archive-copy.db")
    .build(); // VACUUM main INTO 'archive-copy.db' (schema quoting may vary)
```

The default source is `main`. Select `schema(String)` or `schema(Schema)` for
`temp` or an attached database. Destination is mandatory and is rendered as a
string literal, never a SQL expression; quotes are escaped and blank/NUL input
is rejected. The builder neither accesses the filesystem nor executes SQL.
Relative paths and enabled SQLite URI filenames retain SQLite's own semantics.
Execute the generated SQL with JDBC after closing active statements/results and
outside an explicit transaction. The source stays unchanged; the output contains
the selected database's logical content, including indexes, views and triggers.
It does not include other attached databases. Tables without an explicit INTEGER
PRIMARY KEY may have different rowids in the copy.

SQLite requires the destination to be absent or empty; a nonempty destination,
active transaction, inaccessible path or other execution failure remains a JDBC
error. This API does not remove destination files or retry/commit automatically.
An interrupted operation can leave an incomplete output file; completion and
verification belong to the caller. See
[VACUUM INTO](https://www.sqlite.org/lang_vacuum.html#vacuuminto).

The resolver adds the [SQLite 3.27 boundary](https://www.sqlite.org/releaselog/3_27_0.html)
and `supportsVacuumInto()`, inheriting 3.25 behavior and passing it to 3.31+.
Earlier targets reject SQL generation. Existing VACUUM factory behavior is
unchanged. This additive API stays in `sqlapp-core-sqlite`, without new
dependencies, shared-core changes, XML or configuration formats. Embedded tests
verify copied data, executable views/triggers, source isolation, quoting,
destination conflicts, transaction failures and version boundaries. Historical
SQLite binaries are not tested.

### Index rebuilding

The normal SQL factory registry supports SQLite `SqlType.REBUILD` for a Schema
model `Index`, `Table` or `Schema`:

```java
var registry = dialect.createSqlFactoryRegistry();
var index = new Index("orders_by_date").setSchemaName("archive");
var operations = registry.createSql(index, SqlType.REBUILD);
// REINDEX archive.orders_by_date (identifier quoting may vary)
```

For a table, SQLite rebuilds all indexes on that table; for an index, it rebuilds
only that index. Schema identity can be inherited from the owning model or
explicitly set. A missing schema/name is rejected. Every target is qualified,
avoiding SQLite's precedence of a collation name over an unqualified table or
index name. Collation-wide and SQLite 3.53 `REINDEX EXPRESSIONS`
operations are not exposed. See [REINDEX](https://www.sqlite.org/lang_reindex.html).

Passing a named Schema expands only its modeled ordinary tables into qualified
table REINDEX operations, in model order:

```java
var operations = registry.createSql(schema, SqlType.REBUILD);
```

This rebuilds every index of each selected table, including native UNIQUE and
primary-key indexes absent from the explicit Index collection. Tables omitted
from the model and other attached databases are not selected. Canonical Schema
ownership supplies each table's database identity; the caller's model is not
modified. An empty named Schema produces no operations. Virtual tables identified
by metadata or complete CREATE DDL are skipped because their module-owned indexes
are not native REINDEX targets; this does not issue FTS rebuild commands or touch
their shadow tables. Use the existing explicit Index/Table path for a narrower
selection. Blank or NUL-containing schema/object names fail before SQL is returned.

The factory generates SQL only: the caller executes operations and owns the
transaction. SQLite execution failures, including uniqueness conflicts after
changing a collation, remain visible. This operation rebuilds indexes; it does
not rebuild table definitions or provide ALTER TABLE emulation.

This is confined to `sqlapp-core-sqlite`, uses existing `SqlType.REBUILD` and
Schema APIs, and adds no dependencies, XML/configuration formats or version
boundaries. Legacy/current selections retain the same native SQL. Tests execute
against the bundled engine and cover changed collation ordering, isolated
attached indexes, all indexes on a table, temp objects, quoting, invalid targets,
uniqueness failures and caller-controlled rollback. Old binaries are not tested.
Schema selection additionally tests main, TEMP and quoted attached databases,
unselected tables, native UNIQUE indexes, empty tables, FTS5/RTree exclusion,
model immutability and rollback of an earlier successful operation when a later
table fails. Generated Schema REINDEX SQL retains the SQLite 3.7+ syntax; legacy
resolver generation is tested without executing an older binary.

Expression indexes can also be selected from a Schema model without broadening
the request to every attached database:

```java
var reindex = (SqliteReindexFactory)registry.getSqlFactory(schema, SqlType.REBUILD);
var operations = reindex.createExpressionIndexSql(schema);
```

This emits one qualified REINDEX per modeled expression index, identified from
`sqlite.index.keys`; ordinary indexes are excluded. A selected subset of Schema
tables/indexes stays a subset. Empty selections produce no operations. It requires
SQLite 3.9+ expression-index support, reuses the existing per-index generator,
and adds no metadata queries. Tests change a deterministic function's behavior
and verify stored expression keys are rebuilt in the selected attached database
while the main database's same-named index remains untouched. Native global
REINDEX EXPRESSIONS remains unexposed, including its name-precedence ambiguity.

### Foreign-key diagnostics

`SqliteForeignKeyCheckBuilder` complements full/quick integrity checks, which do
not detect foreign-key violations. It checks every child table in `main` by
default, or one explicitly selected schema/table:

```java
var check = new SqliteForeignKeyCheckBuilder(dialect);
String schemaSql = check.build();
String tableSql = check.table(new Table("order_lines").setSchemaName("archive")).build();
String allArchiveTablesSql = check.allTables().build();
```

`schema(String)` / `schema(Schema)` select a database; `table(String)` selects a
child table within it. `table(Table)` selects both identifiers from the model
and requires its schema name, preventing ambiguous cross-database resolution.
`allTables()` clears the table restriction and retains the schema. Names are
quoted with the SQLite builder. This API only generates SQL; it does not change
`foreign_keys`, commit transactions, repair data or swallow execution failures.

Execute with JDBC `executeQuery`. No result rows means no violations were
reported; each violation has `table`, `rowid`, `parent`, and `fkid` fields.
The rowid is SQL NULL for WITHOUT ROWID child tables. The `fkid` identifies the
constraint by the `id` in `foreign_key_list`; it is not a Schema constraint name.
A table-specific check inspects outgoing REFERENCES clauses only, rather than
all children referencing that table. See
[foreign_key_check](https://www.sqlite.org/pragma.html#pragma_foreign_key_check).

The resolver adds the SQLite 3.7.16 boundary, inheriting the legacy behavior and
adding only `supportsForeignKeyCheck()`. SQLite 3.8.0 inherits that capability;
later boundaries and the default dialect retain their prior capabilities.
Earlier versions and SQLite 3.7 with an unknown revision reject generation,
instead of issuing an unsupported PRAGMA that could be silently ignored.
See the [3.7.16 release notes](https://www.sqlite.org/releaselog/3_7_16.html).
This is additive SQLite API only, with no dependency, shared-core, XML or
configuration changes. Custom SQLite builds must include foreign-key support.

Embedded-engine tests cover valid and invalid references, enabled/disabled
enforcement, deferred violations inside a transaction, caller rollback, temp and
attached databases, composite keys, WITHOUT ROWID, quoted identifiers, model
identity and version boundaries. Historical engine binaries are not tested.

SQLite syntax references:
[CREATE VIEW](https://www.sqlite.org/lang_createview.html),
[CREATE TRIGGER](https://www.sqlite.org/lang_createtrigger.html), and
[PRAGMA functions](https://www.sqlite.org/pragma.html#pragfunc).
Table syntax references: [CREATE TABLE](https://www.sqlite.org/lang_createtable.html)
and [AUTOINCREMENT](https://www.sqlite.org/autoinc.html).
Constraint behavior references: [foreign keys](https://www.sqlite.org/foreignkeys.html)
and [ON CONFLICT](https://www.sqlite.org/lang_conflict.html).
Native migration reference: [ALTER TABLE](https://www.sqlite.org/lang_altertable.html).
