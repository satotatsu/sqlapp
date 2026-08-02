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

## Current limitation

UCanAccess 5.1.6 uses Jackcess to access the database file. Some Access index
collations are not writable in that version. In particular, the Japanese
collation used by `src/test/resources/AccessSample.accdb` is reported as an
unsupported sort order and the affected indexes make that database read-only.
The sample is therefore used to verify metadata reads, while write and
AutoNumber generated-key behavior is tested with a newly created Access 2010
database.
