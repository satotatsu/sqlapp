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

### Trigger component validation and target identity

Modeled triggers also require a nonblank name, target table and body. Omitted
orientation means ROW; an explicit orientation must be ROW. WHEN/actionCondition,
disabled state and custom OLD/NEW row aliases fail clearly instead of being
silently omitted or converted to an active unconditional trigger. Put conditional
logic inside the body when needed. Complete definitions retain precedence over
these component checks.

The ON target is schema-qualified whenever the model supplies either its schema
or the trigger's schema. With only a target-table schema, the decorated trigger
name uses that same unambiguous schema without mutating the model. Explicit
different trigger/table schema names fail; use the same resolved schema spelling
for both identities. The decorateSchemaName option still controls decoration of
the trigger name. With neither schema supplied, ordinary current-database
resolution remains available.

These checks are in the MySQL factory inherited by MariaDB. They do not parse
the body, convert PostgreSQL trigger conditions, introduce client DELIMITER
commands or change metadata queries/version resolution. Statement restrictions,
SQL mode, trigger execution order and permissions still require separate handling.

The follow-up ran the focused trigger, definer round-trip and inherited MariaDB
tests with the repository wrapper and the authorized temporary Java 25 toolchain
override. Twelve new MySQL cases cover 5.1/5.5/5.7/8.x/9.x, required components,
unsupported conditions/orientation/state/aliases, schema resolution, decoration
and complete-definition precedence. The inherited MariaDB trigger case also
passed. An initial invalid-model test incorrectly assumed setTableName(null)
cleared an existing reference; it was corrected to construct a nameless target.

The complete `:sqlapp-core-mysql:test :sqlapp-core-mariadb:test` scope was requested
with `--no-daemon --offline --console=plain`. After that process session became
unavailable, the final wrapper check completed successfully with both test tasks
UP-TO-DATE. The retained reports contain **261 MySQL tests and 47 MariaDB tests**,
with zero failures, errors or skips. No real database, Docker or command/plugin
suite was run; no generated files were intentionally changed.

## Scope and compatibility

Only `sqlapp-core-mysql` implementation changes. Existing API signatures, configuration formats,
metadata queries and version resolution remain unchanged. These syntax fixes use
existing MySQL stored object syntax, without introducing newer catalog fields or
version-specific SQL. Parameterized SQL-generation tests cover the resolved
MySQL 5.5, 5.7, 8.0, 8.4 and 9.0 dialects. MariaDB inherits these factories, so its
module tests are also included in verification.

This work addresses confirmed recreation defects; it is not full PostgreSQL
feature parity. PostgreSQL-specific features such as procedural languages,
materialized views and exclusion constraints are not mapped onto MySQL. Routine
definers are retained by the follow-up below. The later
[native recreation follow-up](mysql-native-recreation.md) retains creation context
and trigger order and executes Docker verification. Earlier SQL-generation-only
results in this guide remain historical evidence, not server execution evidence.

## Definer preservation

CREATE generation for views, functions, procedures, triggers and events now
preserves an available DEFINER instead of silently assigning the executing account.
Views, triggers and the MySQL 5.5.3+ routine readers retain the raw catalog value
in `specifics["mysql_raw_definer"]`. Events retain their existing raw `definer`
property. Existing set queries already select the field; no additional SELECT,
mysql.user access or new version-specific catalog column is required. Legacy
pre-5.5.3 routine readers and DialectResolver mappings are unchanged.

An ordinary raw `owner@localhost` value is unambiguously split and emitted as
`DEFINER = ` followed by separately backtick-quoted user and host identifiers.
Quotes, backslashes and backticks in raw catalog text are treated as account
characters, not as SQL literal escapes. No trimming or case conversion is applied.
If no definer is modeled, the clause remains absent as before.

An optional explicit account overrides the catalog identity for any of these
object types, using the existing specifics map:

```java
function.getSpecifics().put("mysql_definer", "'owner@work'@'localhost'");
```

Explicit values require separately quoted user and host components, using the
same account syntax as MySQL privilege generation. An unquoted single name fails
instead of guessing its host. Raw metadata with multiple `@` characters is
ambiguous and is retained unchanged by the reader; component generation then
fails with instructions to supply this explicit account or a complete definition.
It never guesses which `@` separates the user and host. An explicit empty string
does not disable the clause; remove definer metadata deliberately if that is the
intended change. Complete `definition` takes precedence over all definer options.

The destination account must exist and the executing account must have the
server-version-specific privileges needed to assign that DEFINER. The library
does not create accounts, grant privileges, or execute the generated SQL.
The later `MySqlStoredObjectContext.create` path applies saved event TIME_ZONE and
routine SQL_MODE and restores the session on success/failure. Connection character
encoding remains the caller's responsibility; see [native recreation](mysql-native-recreation.md).
MariaDB inherits the native generation path;
its event regression test also checks definer preservation.

`MySqlDefinerRoundTripTest` covers five MySQL dialect families, catalog-to-DDL
round trips, unusual account characters, absent identities, explicit overrides,
ambiguous values and definition precedence across all five object types.
`MySqlEventRoundTripTest` additionally verifies that its real event-reader path
passes the existing Event definer through to CREATE generation.
See [stored object access control](https://dev.mysql.com/doc/refman/8.0/en/stored-objects-security.html)
and the [INFORMATION_SCHEMA VIEWS definition](https://dev.mysql.com/doc/refman/8.0/en/information-schema-views-table.html).

The definer follow-up used the user-selected Java 25 with the repository wrapper.
The focused command `:sqlapp-core-mysql:test --tests '*MySqlDefinerRoundTripTest'`
passed all 6 cases; the subsequent `:sqlapp-core-mysql:test :sqlapp-core-mariadb:test`
run passed **180 MySQL tests and 39 MariaDB tests**, with zero failures or skips.
Both commands included `--offline --console=plain --no-daemon`, a command-line
`org.gradle.java.home` pointing to Java 25, and a temporary init script selecting
toolchain 25 as described in the [comparison verification](mysql-dialect-parity.md#verification).
No repository toolchain/dependency setting, generated source or local setting
was intentionally changed. `git diff --check` passed. Java 21, external database,
Docker, command and Gradle-plugin suites were not run for this follow-up.

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
