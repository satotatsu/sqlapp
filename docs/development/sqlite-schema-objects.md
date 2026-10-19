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

## Verification and scope

`SqliteSchemaObjectsRoundTripTest` uses isolated in-memory SQLite connections:

- view column names and complete definitions survive XML and ATTACH recreation;
- conditional UPDATE OF triggers retain their observed behavior after recreation;
- quoted AS, comments, omitted timing and INSTEAD OF header syntax are covered;
- eight views use one prepared statement for column metadata;
- missing executable SQL fails clearly, and disabled SQL-reading options are honored.

The implementation stays in `sqlapp-core-sqlite`; dependencies, resolver mappings
and shared model APIs are unchanged. Tables, indexes, ALTER TABLE/rebuild
planning and SQLite extensions are outside this change. PostgreSQL-only objects
such as stored procedures and sequences are not added to SQLite.

SQLite syntax references:
[CREATE VIEW](https://www.sqlite.org/lang_createview.html),
[CREATE TRIGGER](https://www.sqlite.org/lang_createtrigger.html), and
[PRAGMA functions](https://www.sqlite.org/pragma.html#pragfunc).
