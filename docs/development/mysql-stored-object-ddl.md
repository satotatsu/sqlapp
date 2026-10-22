# MySQL stored object DDL

The PostgreSQL dialect's stored object factories provide a reference for two
recreation paths: a complete executable `definition`, or a `statement` body with
separately modeled arguments and characteristics. MySQL now follows both paths
for functions and procedures. Triggers already accept complete definitions through
the shared trigger factory.

## Common usage

Use the existing Schema model and factory registry; no new configuration or
module dependencies are needed:

```java
var dialect = DialectResolver.getInstance().getDialect("MySql", 8, 4, 0);
var function = new Function("constant_value");
function.setDialect(dialect);
function.getReturning().setDataType(DataType.INT);
function.setDeterministic(true);
function.setSqlDataAccess("NO SQL");
function.setSqlSecurity(SqlSecurity.Invoker);
function.setStatement("RETURN 1");
var operations = dialect.createSqlFactoryRegistry().createSql(function, SqlType.CREATE);
```

Function bodies are emitted once, after characteristics and inline comments.
Explicit determinism produces `DETERMINISTIC` or `NOT DETERMINISTIC`; an unspecified
value leaves the clause absent. Both functions and procedures emit the MySQL
`SQL SECURITY DEFINER` / `SQL SECURITY INVOKER` syntax.

For advanced declarations that the modeled components cannot fully express, set
`definition` to the complete executable CREATE statement. This takes precedence
over the body, arguments and characteristics. Embedded names in that definition
must be edited when renaming the object. The factory does not add client-side
`DELIMITER` commands; execute the returned operation through JDBC as usual.

Modeled MySQL triggers require one `INSERT`, `UPDATE` or `DELETE` event and
`BEFORE` or `AFTER` timing. Unsupported events, missing events, multiple events
and invalid timing now fail with an actionable exception instead of producing
invalid SQL or silently keeping only one event. Complete trigger definitions
bypass component generation.

## Scope and compatibility

Only `sqlapp-core-mysql` implementation changes. Public APIs, configuration formats,
metadata queries and version resolution remain unchanged. These syntax fixes use
existing MySQL stored object syntax, without introducing newer catalog fields or
version-specific SQL. Parameterized SQL-generation tests cover the resolved
MySQL 5.5, 5.7, 8.0, 8.4 and 9.0 dialects. MariaDB inherits these factories, so its
module tests are also included in verification.

This work addresses confirmed recreation defects; it is not full PostgreSQL
feature parity. PostgreSQL-specific features such as procedural languages,
materialized views and exclusion constraints are not mapped onto MySQL. Routine
definers, execution SQL modes and trigger ordering are outside this change.
No real MySQL or MariaDB server was used, so successful
SQL generation does not constitute server execution verification.

## Routine type metadata

The MySQL 5.5.3+ routine and parameter readers now prefer `DTD_IDENTIFIER` over
the bare `DATA_TYPE`, falling back to the latter when the detailed declaration is
empty. The existing `SELECT *` catalog queries already retrieve this field, so no
additional queries or per-routine SELECTs are introduced. The pre-5.5.3 reader
path and resolver stay unchanged.

The shared dialect type parser retains unsigned integers, DECIMAL precision and
scale, character length, and fractional seconds encoded in DATETIME/TIME type
declarations. ENUM/SET argument values remain in the Schema model's ordered
value list; a MySQL-local literal parser preserves commas, doubled apostrophes
and backslash escapes inside values. The builder emits those literals without
escaping them a second time. An empty modeled ENUM/SET argument list fails clearly.

`FunctionReturning` has no value-list property. For ENUM/SET return types, its
existing `definition` property therefore retains the executable type declaration,
while `dataType` still identifies the type. MySQL's builder honors this return
type definition. Editing such a return type requires updating its definition too.
This is a representation change in generated Schema metadata, with no new XML
properties or public APIs.

Routine argument names use the dialect's identifier quoting, including reserved
words, spaces, backticks and Unicode. OUT/INOUT directions remain intact. Character
set and collation exposed by the model are emitted for routine character types.
The shared model currently hides ENUM/SET argument character sets and collations
through its getters; this change does not resolve that separate shared-model
limitation. ENUM/SET return attributes remain available. SQL mode-dependent escape
interpretation is preserved as read, not translated between execution SQL modes.

`MySqlRoutineTypeMetadataTest` exercises real reader methods with an in-memory
JDBC row set and then creates function/procedure DDL. It covers MySQL 5.5.3,
5.6.4, 5.7, 8.0, 8.4 and 9.0 resolution, including the detailed-type fallback,
unsigned integers, value-list literals, decimal precision, and DATETIME/TIME
fractional seconds on dialects supporting them.

## Verification

With Java 21 and the repository Gradle Wrapper:

```powershell
.\gradlew.bat :sqlapp-core-mysql:test --tests '*MySqlStoredObjectDdlTest' --offline --console=plain
.\gradlew.bat :sqlapp-core-mysql:test --tests '*MySqlRoutineTypeMetadataTest' --offline --console=plain
.\gradlew.bat :sqlapp-core-mysql:test :sqlapp-core-mariadb:test --offline --console=plain
```

These commands passed. Tests use in-process models and SQL strings; no external
database is required.

Syntax references: [CREATE PROCEDURE / FUNCTION](https://dev.mysql.com/doc/mysql/8.0/en/create-procedure.html)
and [CREATE TRIGGER](https://dev.mysql.com/doc/refman/8.4/en/create-trigger.html).
Metadata references: [PARAMETERS](https://dev.mysql.com/doc/refman/8.4/en/information-schema-parameters-table.html)
and [ROUTINES](https://dev.mysql.com/doc/refman/8.4/en/information-schema-routines-table.html).
