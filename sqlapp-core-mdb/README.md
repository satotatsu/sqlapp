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
- Access date/time default functions (`DATE()`, `TIME()` and `NOW()`)
- table, primary-key and index creation without unsupported clauses
- foreign-key creation with cascade update/delete behavior
- schema-difference ALTER support for adding columns, constraints and indexes
- prepared single-row INSERT, UPDATE, SELECT and DELETE generation
- ordinal-based prepared parameter expressions, allowing Access column names
  containing spaces, Japanese text or punctuation in single-row CRUD
- conditional `INSERT ... SELECT ... WHERE NOT EXISTS` through a UCanAccess
  one-row `VALUES` source
- AutoNumber retrieval through JDBC `Statement.RETURN_GENERATED_KEYS`
- multi-row prepared INSERTs through UCanAccess `VALUES (...), (...)`
- truncate semantics through `DELETE FROM`, because Access has no `TRUNCATE`
- Access cascade-rule capability reporting and mixed-case identifiers

Generated SQL is intended for UCanAccess 5.1.6 and Access `.mdb`/`.accdb`
files. UCanAccess accepts multi-row `VALUES` even though older native Access
interfaces do not expose all of the same syntax.

## Current limitation

UCanAccess 5.1.6 uses Jackcess to access the database file. Some Access index
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

UCanAccess 5.1.6 cannot persist `CREATE VIEW`, `DROP VIEW`, `DROP INDEX`,
`DROP COLUMN`, `DROP CONSTRAINT`, table/column renames, or column-definition
changes through JDBC. The MDB SQL registry rejects these operations with an
actionable `UnsupportedOperationException` instead of returning SQL that fails
later. Destructive or modifying schema changes currently require rebuilding the
Access table outside the JDBC dialect.
Existing non-parameterized saved SELECT/UNION queries can still be loaded as
Schema views by `MdbFileLoader`.

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
  A per-table target-to-source column-name map supports column renames. Dropped
  columns are omitted and changed definitions are created from the target
  model. The source is never overwritten and an existing target is rejected.

Close all UCanAccess connections to the file before opening the direct writer.
The class prevents two sqlapp Jackcess writer sessions for the same normalized
path within one JVM, but it cannot detect arbitrary UCanAccess or Microsoft
Access processes. Jackcess writes are direct file operations, not JDBC
transactions, so callers should keep a backup for schema work and close the
session promptly. Bulk insert minimizes per-row overhead; primary-key update
and delete still perform one indexed lookup per supplied row because Jackcess
has no set-based update/delete API.

Jackcess exposes saved queries for reading but no supported public writer API,
so saved-query CREATE/ALTER/DROP remains unsupported. Access has no MERGE and
the direct complement does not emulate it. Access file-version restrictions
also apply to generated types; for example, BIGINT requires a sufficiently new
ACCDB format. Complex columns, attachments and multi-value fields remain
outside the flat sqlapp Column model.

Access SQL has no `MERGE` statement. Direct `SqlType.MERGE` generation is
therefore rejected explicitly; use conditional INSERT or the data session's
update-then-insert behavior where its transaction semantics are acceptable.
UCanAccess 5.1.6 cannot resolve a whitespace-containing target column in
`INSERT ... SELECT`; this affects the conditional-INSERT factory only. Regular
prepared INSERT, UPDATE, SELECT and DELETE support such quoted column names.
