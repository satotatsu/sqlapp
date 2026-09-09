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
disabled. Multi-row inserts that do not require generated-key propagation use
one prepared statement for performance.

UCanAccess 5.1.6 cannot persist `CREATE VIEW`, `DROP VIEW`, `DROP INDEX`,
`DROP COLUMN`, `DROP CONSTRAINT`, table/column renames, or column-definition
changes through JDBC. The MDB SQL registry rejects these operations with an
actionable `UnsupportedOperationException` instead of returning SQL that fails
later. Destructive or modifying schema changes currently require rebuilding the
Access table outside the JDBC dialect.
Existing non-parameterized saved SELECT/UNION queries can still be loaded as
Schema views by `MdbFileLoader`.
