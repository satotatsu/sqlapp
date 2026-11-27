# sqlapp-core-mdb

This module supports Microsoft Access `.mdb` and `.accdb` databases through
UCanAccess. It does not require the removed JDBC-ODBC bridge, an ODBC data
source, or a local Microsoft Access installation.

Use a UCanAccess JDBC URL with an absolute database path:

```text
jdbc:ucanaccess:///C:/data/example.accdb
```

`sqlapp-core-mdb` supplies UCanAccess as a runtime dependency. The dialect
continues to use Access SQL, data types, identifier quoting, and AutoNumber
semantics; UCanAccess's internal use of HSQLDB is an implementation detail and
does not make an HSQLDB dialect appropriate for Access files.

The Access-specific SQL generator supports:

- Access `COUNTER`/AutoNumber column DDL, including inline primary keys
- `TEXT`, `MEMO`, `OLE`, Boolean, numeric and date/time type generation
- UCanAccess JDBC `TIMESTAMP` metadata maps to the existing Access `DATETIME`
  model/handler, preserving Date/Time columns during JDBC copy. Native `DATETIME`
  names remain supported. This applies to existing Jet/ACE MDB/ACCDB Date/Time
  columns; no SQL-generation or dialect-version boundary changes were introduced.
- Access date/time default functions (`DATE()`, `TIME()` and `NOW()`)
- table, primary-key and index creation without unsupported clauses
- foreign-key creation with cascade update/delete behavior
- schema-difference ALTER support for adding columns, constraints and indexes
- table renaming through `ALTER TABLE ... RENAME TO`, including a rename
  combined with adding columns without rebuilding unchanged indexes
- prepared single-row INSERT, UPDATE, SELECT and DELETE generation
- ordinal-based prepared parameter expressions, allowing Access column names
  containing spaces, Japanese text or punctuation in single-row CRUD
- conditional `INSERT ... SELECT ... WHERE NOT EXISTS` through a UCanAccess
  one-row `VALUES` source
- AutoNumber retrieval through JDBC `Statement.RETURN_GENERATED_KEYS`
- multi-row prepared INSERTs through UCanAccess `VALUES (...), (...)`
- truncate semantics through `DELETE FROM`, because Access has no `TRUNCATE`
- Access cascade-rule capability reporting and mixed-case identifiers

Generated SQL is intended for UCanAccess 5.2.0 and Access `.mdb`/`.accdb`
files. UCanAccess accepts multi-row `VALUES` even though older native Access
interfaces do not expose all of the same syntax.

## Current limitation

Direct JDBC transfer can copy keyless Access tables with full-scan INSERT and
duplicate-preserving unordered hash verification. It warns about missing source
keys; MERGE and resume remain unavailable. Optional
`CREATE_MISSING_WITH_IDENTITY` creates a target-only `ID` identity PK by default
on new keyless tables through the target dialect, without changing the source file.
Set `generatedKeyColumn` in the shared job YAML, or edit **Generated PK column**
in the GUI, to use another name. Existing source/mapped columns with the chosen
name cause an actionable collision error before DDL; choose an unused name.
Set `generatedKeyColumn: SQLAPP_ROW_ID` to retain the earlier generated name.
CSV package eligibility still retains its existing key/type requirements.
Advanced Data Job YAML can explicitly use full-scan INSERT, or select an existing
modeled NOT NULL unique keyset for the keyed path; naming arbitrary columns does
not establish uniqueness.

The UCanAccess 5.2.0 upgrade also corrects JDBC metadata loading: direct
UCanAccess connections are recognized before checking JDBC `Wrapper` methods
(which delegate to HSQLDB), so Access AutoNumber flags are restored. Primary
keys, foreign keys and secondary indexes are read from the physical Access
file through the connection's Jackcess handle. This preserves original primary-key names,
composite key order, index names, uniqueness, sort order and IgnoreNulls flags
rather than exposing internal HSQLDB indexes. Readers support a single table
or all tables and exact, case-insensitive constraint/index-name filters.
Direct connections and JDBC wrappers exposing `unwrap(UcanaccessConnection.class)`
use the same path. Other connections retain the generic JDBC fallback.
UCanAccess table loading reads all columns with one JDBC metadata call, then
loads keys, indexes and relationships in bulk from the file. It does not issue
JDBC key/index queries per table. Physical relationship names, paired column
order, cascade rules, `SET NULL` and referential-integrity enablement are
retained. Relationship backing indexes (including hidden `.rB` indexes) are
represented by the relationships instead of duplicated as independent indexes;
Jackcess recreates them when rebuilding the file.

File loading, JDBC metadata and direct Jackcess writers share the same
relationship mapping. Direct `addRelationship` and `rebuild` preserve
`ON DELETE SET NULL` and non-enforced relationships. Unsupported `SET DEFAULT`
and `ON UPDATE SET NULL` rules fail explicitly. JDBC CREATE of a non-enforced
relationship is rejected with a pointer to the direct writer, so it cannot
silently become an enforced foreign key.

UCanAccess 5.2.0 loads existing `SET NULL` relationships as `NO ACTION` in its
internal SQL mirror in the tested MDB/ACCDB cases. The physical metadata still
retains `SET NULL`, but deleting the referenced parent through JDBC fails.
Close JDBC connections and use the direct Jackcess writer for these deletes;
local tests verify that both columns of a composite child key become NULL.

Local regression coverage includes Access 2000 `.mdb` and Access 2010 `.accdb`
JDBC metadata, generated DDL/DML, AutoNumber propagation, and physical-file
verification that explicit NULL stays NULL while an omitted column receives
its default. Table-rename tests also verify retained rows, indexes and foreign
key references after reopening the physical file. The generic Access resolver
and existing public entry points are
unchanged. Newer Access file formats and unsupported native features are not
implied by this driver upgrade.

UCanAccess 5.2.0 exposes short-text columns as JDBC `VARCHAR`. The MDB dialect
recognizes this metadata spelling as `VARCHAR` with the reported length, while
continuing to generate Access `TEXT` DDL. This prevents common transfer jobs
from treating these columns as unsupported native types. The existing native
`TEXT` spelling remains supported; no database-version resolver changed.

UCanAccess 5.2.0 uses Jackcess to access the database file. Some Access index
collations are not writable in that version. In particular, the Japanese
collation used by `src/test/resources/AccessSample.accdb` is reported as an
unsupported sort order and the affected indexes make that database read-only.
The sample is therefore used to verify metadata reads, while write and
AutoNumber generated-key behavior is tested with a newly created Access 2010
database.

UCanAccess returns only the last generated AutoNumber value after a JDBC batch,
not one key per row. Consequently `supportsBatchExecuteGeneratedKeys()` remains
disabled and the dialect reports a generated-key batch limit of one row.
`JdbcTreeDataSession` splits only a larger AutoNumber batch into single-row
executions so every generated key can be propagated safely. The prepared
statement is still reused. Use explicit, non-AutoNumber keys when maximum
parent/child loading throughput is required; those rows continue to use JDBC
batching and reuse one prepared statement per SQL signature.

UCanAccess 5.2.0 cannot persist `CREATE VIEW`, `DROP VIEW`, `DROP INDEX`,
`DROP COLUMN`, `DROP CONSTRAINT`, column renames, or column-definition
changes through JDBC. The MDB SQL registry rejects these operations with an
actionable `UnsupportedOperationException` instead of returning SQL that fails
later. These unsupported schema changes currently require rebuilding the
Access table outside the JDBC dialect. Table renaming is supported and does
not require rebuilding the file.
Existing saved SELECT/UNION queries, including parameter declarations, can be loaded as
Schema views by `MdbFileLoader`.

## Schema changes with a single entry point

`MdbSchemaMigration.rebuild` edits the shared Schema and creates a separate
Access file using the existing direct writer:

```java
var result = MdbSchemaMigration.rebuild(source, target, schema -> {
    var table = schema.getTables().get("ITEMS");
    table.getColumns().get("TXT").setLength(100L);
    table.getColumns().get("TXT").setName("DISPLAY_NAME");
    table.getColumns().remove("OLD");
});
```

The source is never replaced and an existing target is rejected. Table and
column renames are inferred from retained Schema object identities. Optional
`plan(source, target, editor)` and `execute(plan)` separate review and execution.
The plan retains target Schema XML without row data, resolved rename mappings,
and SHA-256 fingerprints of the source file and target schema. A changed source
or modified schema fingerprint is rejected before writing. Results retain the
plan, observed schema and per-table row counts. Verification covers table and
column identities, native column types and properties, primary/foreign keys,
indexes, saved-query names and row counts. Copied columns are also compared row
by row in the writer's traversal/insertion order, without buffering whole tables.
Rename mappings are resolved case-insensitively. NULL, binary content, numeric
values and extended datetime precision are compared; lossy numeric conversions
fail verification even when Jackcess accepts the write. Comparison reports the
table, column and copied-row ordinal without including cell values. It stops at
the first value mismatch per table. New columns and recalculated columns are
excluded from source-value comparison; saved-query SQL semantic equivalence is
not verified. A failed post-write verification leaves
the target for inspection and throws `MdbSchemaMigration.VerificationException`,
which retains the plan, observed schema, row counts and all detected issues.
LONG AutoNumber counters are checked against the source, including deleted-value
history and empty tables. The current writer cannot preserve a counter greater
than that reconstructed from copied rows; such a rebuild fails verification
instead of silently allowing reuse of previously issued values. This check uses
the isolated Jackcess 5.2.0 internal LONG generator because its public API has no
persisted-counter accessor. GUID AutoNumber has no comparable sequence counter.
Replacing the source remains an explicit `replaceWithRebuilt` operation with
a caller-selected backup. Close all JDBC connections before this workflow.

Linked tables, hidden queries and action/crosstab queries are rejected by the
high-level planner because they cannot be preserved by this workflow. Update
saved-query definitions explicitly when renaming tables/columns they reference;
the saved-query writer rejects missing source tables, missing columns and
ambiguous unqualified column references. Declared parameters, function calls,
quoted literals and explicit output aliases in ORDER BY are recognized.
This is bounded name resolution, not complete SQL/function/type validation;
references using Access's `!` notation must be written as `table.column`.
Planning validates column compatibility with the source file format and the
supported saved-query grammar/references, reporting detected errors together
before row copying. The lower-level writer repeats these checks for supplied
schemas. The lower-level rebuild
overloads remain available for an explicitly supplied target Schema and maps.

## Column metadata and type preservation

File and JDBC metadata share Access-specific column enrichment: identity,
required/default/description/validation properties, AllowZeroLength and
calculated expressions. Primary-key and AutoNumber columns remain NOT NULL
even when Access's separate Required property is false. `access.sourceType` retains the native type, while the
existing portable Schema types remain available. Rebuild preserves compatible
GUID, MONEY, BIG_INT and EXT_DATE_TIME native types instead of flattening them
to text, NUMERIC or ordinary DATETIME. Native-type hints are ignored when the
logical type is changed incompatibly; GUID length and MONEY precision/scale
changes also select the requested ordinary type. Remove the native hint when
explicitly changing between native types with the same logical representation.
Access file-format restrictions still apply; rebuild retains the source format.

Direct writes restore AllowZeroLength, column/table descriptions, validation
rules, numeric precision/scale and calculated-column expressions. Calculated
values are recomputed. MDB/ACCDB round-trip tests include native GUID, currency,
23-digit NUMERIC values and V2019 BIGINT/extended datetime with sub-millisecond
precision. JDBC text metadata retains its existing VARCHAR representation.

## Direct Jackcess complement

`MdbJackcessSupport.open(path)` provides a direct file-writing path for work
which is slow or unavailable through UCanAccess:

- `insertRows` sends all supplied rows to Jackcess in one bulk call and writes
  every generated AutoNumber value back into the corresponding input map.
- `updateRowsByPrimaryKey` and `deleteRowsByPrimaryKey` use the Access primary
  key index. They deliberately reject tables without a primary key instead of
  falling back to a full scan.
- Direct writers disable Jackcess auto-sync, process update/delete requests in
  primary-key order, and flush once when explicitly requested or closed.
- `upsertRowsByPrimaryKey` updates rows found through the primary-key index and
  sends all missing rows through one bulk insert; its result reports both
  counts and generated AutoNumber values are written back to inserted maps.
- `addColumn`, `addIndex` and `addRelationship` modify Access schema objects
  directly.
- `rebuild(source, target, schema)` creates a separate Access file, recreates
  tables and relationships from the sqlapp Schema model, and bulk-copies data.
  Copying buffers at most 1,024 rows at a time rather than a whole table.
  Existing columns retain explicit NULL values even when they have defaults;
  newly added columns receive their target defaults. Copied-column defaults
  are temporarily suppressed during copying and restored before verification.
  A per-table target-to-source column-name map supports column renames. Dropped
  columns are omitted and changed definitions are created from the target
  model. The source is never overwritten and an existing target is rejected.
- The rebuild overload accepting a target-to-source table map supports table
  renames. Tables omitted from the target Schema are dropped from the rebuilt
  file, while target tables without a source are created empty.
  Explicit mapping names are case-insensitive. Unknown source/target names,
  blank/null entries, duplicate target names differing only in case and
  copying into calculated columns are rejected instead of silently losing data.
  New tables and columns remain supported without explicit mappings.
  Schema UNIQUE constraints are recreated as Access unique indexes and are
  reported as indexes when read back, including composite keys.
- `replaceWithRebuilt` switches a verified rebuilt file into the source path,
  first moving the original to a caller-selected backup. It never overwrites a
  backup and restores the original if the second move fails.
- `rebuildWithoutIndex` is the safe DROP INDEX path. It recreates a separate
  file without the named secondary index, preserves the source file and rejects
  primary-key removal unless the caller supplies a complete target Schema.
- `createSavedQuery` and `dropSavedQuery` write Access `MSysObjects` and
  `MSysQueries` consistently. The supported grammar is a SELECT over one table,
  or multiple tables joined by INNER/LEFT/RIGHT JOIN with optional aliases
  and parenthesized join groups (including self joins with distinct aliases),
  WHERE, GROUP BY, HAVING and ORDER BY, or a two-part UNION/UNION ALL.
  An optional PARAMETERS clause accepts explicit scalar types (TEXT length
  1–255, BYTE, SHORT, LONG, DATETIME, BOOLEAN, CURRENCY, SINGLE, DOUBLE, GUID).
  Quoting-aware splitting preserves commas and UNION inside string literals.
  Rebuild restores these supported Schema views and
  fails without leaving a target file if a definition cannot be represented.

Close all UCanAccess connections to the file before opening the direct writer.
The class prevents two sqlapp Jackcess writer sessions for the same normalized
path within one JVM, but it cannot detect arbitrary UCanAccess or Microsoft
Access processes. Jackcess writes are direct file operations, not JDBC
transactions, so callers should keep a backup for schema work and close the
session promptly. Bulk insert minimizes per-row overhead; primary-key update
and delete still perform one indexed lookup per supplied row because Jackcess
has no set-based update/delete API.

Jackcess exposes no public saved-query writer API, so sqlapp's direct writer
isolates the necessary catalog updates and deliberately accepts only the
grammar described above. Parameterized queries retain their full executable
definition and the `access.parameters` metadata. CROSS/FULL JOIN,
TOP/DISTINCTROW, SQL comments, crosstabs and action queries remain unsupported
by the saved-query writer. Access has no MERGE and the
direct complement does not emulate it. Access file-version restrictions
also apply to generated types; for example, BIGINT requires a sufficiently new
ACCDB format. Complex columns, attachments and multi-value fields remain
outside the flat sqlapp Column model.

Access SQL has no `MERGE` statement. Direct `SqlType.MERGE` generation is
therefore rejected explicitly; use conditional INSERT or the data session's
update-then-insert behavior where its transaction semantics are acceptable.
UCanAccess 5.2.0 cannot resolve a whitespace-containing target column in
`INSERT ... SELECT`; this affects the conditional-INSERT factory only. Regular
prepared INSERT, UPDATE, SELECT and DELETE support such quoted column names.
