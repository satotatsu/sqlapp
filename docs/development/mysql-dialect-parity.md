# MySQL dialect comparison and verification

[Development index](README.md)

The comparison uses PostgreSQL as a reference for retaining the shared Schema
model and recreating objects through the SQL factory registry. PostgreSQL syntax
and features are not substituted for MySQL features. The ordinary entry points
remain unchanged:

```java
var dialect = DialectResolver.getInstance().getDialect(connection);
var schemaReader = dialect.getCatalogReader().getSchemaReader();
schemaReader.setSchemaName("app");
var schema = schemaReader.getAllFull(connection).get(0);
var registry = dialect.createSqlFactoryRegistry();
var operations = registry.createSql(schema.getTables().get("orders"), SqlType.CREATE);
```

The caller executes returned operations and retains database failures. Metadata
access still requires the database privileges needed by each existing reader.

## Comparison outcome

| Area | MySQL outcome |
|---|---|
| Stored functions, procedures and triggers | Previously corrected body duplication, determinism, SQL SECURITY, definition precedence, trigger syntax and invalid component handling; see [stored object DDL](mysql-stored-object-ddl.md) |
| Routine types | Detailed type declarations retain unsigned types, lengths, numeric and temporal precision, ENUM/SET values; known shared-model limitations are documented in the stored object guide |
| Views | Query stays in `statement`; separately modeled output names, SQL SECURITY and LOCAL/CASCADED CHECK OPTION now generate native DDL. Complete `definition` remains the advanced path |
| View column metadata | Uses the same version-specific column reader as tables, preserving fractional seconds and modern type handling |
| Primary and unique constraints | Bulk statistics join preserves prefix lengths, order, index type, comment and visibility; native DDL retains these attributes |
| Functional unique indexes | Retained exclusively as Index objects when any key part is an expression; a partial UniqueConstraint no longer suppresses the complete index during table creation |
| Index generation | UNIQUE is retained, prefix lengths and descending order are emitted, standalone index names remain local to the table, DROP INDEX includes its owner |
| Foreign keys | Composite key order and actions remain modeled; referenced schema now comes from KEY_COLUMN_USAGE instead of the referenced constraint's schema, which can be NULL |
| CHECK constraints | Actual server version gates catalog access at MySQL 8.0.16; ALTER emits DROP CHECK and replacement clauses. MariaDB retains its catalog query and DROP CONSTRAINT syntax |
| Generated columns | Native GENERATED ALWAYS AS plus VIRTUAL/STORED in CREATE and ALTER; MySQL 5.7+ dialect capability enables exclusion from INSERT/UPDATE/MERGE writes |
| Column defaults | CURRENT_TIMESTAMP remains executable; DEFAULT_GENERATED expressions retain parentheses instead of becoming character literals; ordinary function-like literal strings stay quoted |
| Column attributes | ON UPDATE extraction tolerates other EXTRA flags, NULL EXTRA is accepted, and invisible/generated flags are independent of the process locale |
| Column character attributes | Standalone column definitions emit an explicitly configured character set/collation instead of omitting it |
| Existing object/global/column privileges | Existing readers remain; column privilege queries now honor schemaName to avoid mixing grants on identically named tables in different schemas |
| Maintenance | OPTIMIZE resolves to OPTIMIZE TABLE instead of ANALYZE TABLE |
| ALTER with multiple index drops | Dropping an index already covered by a deleted constraint no longer skips subsequent unrelated index changes |

These changes are in `sqlapp-core-mysql`. `sqlapp-core-mariadb` adds regression
tests for its inherited SQL behavior. There are no new dependencies or changed
public method signatures. `supportsColumnFormula()` now correctly returns true
on MySQL 5.7 and newer dialects. New view factory classes and specifics keys are
additive. Schema metadata can now include previously omitted view options and
index attributes; generated SQL intentionally reflects them.

## View configuration

Only the query is required for an ordinary view. Names are quoted by the dialect:

```java
var view = new View("active orders");
view.setDialect(dialect);
view.setStatement("SELECT id FROM orders WHERE active = 1");
var operations = registry.createSql(view, SqlType.CREATE);
```

Optional advanced settings use the existing specifics map:

```java
view.getSpecifics().put("security_type", "INVOKER"); // DEFINER or INVOKER
view.getSpecifics().put("check_option", "LOCAL");   // NONE, LOCAL or CASCADED
```

Readers populate these settings from INFORMATION_SCHEMA.VIEWS. Invalid values
and missing query/definition fail clearly. Output columns, when present, form
the view's column list. A complete `definition` takes precedence over components.

## Version boundaries and query behavior

- Existing DialectResolver mappings are unchanged: legacy readers, 5.6.4 column
  precision, 5.7 generated expressions, and 8.x/9.x reader inheritance remain.
- MySQL CHECK_CONSTRAINTS exists from 8.0.16. Older actual versions produce an
  empty check list without querying that catalog, even when using an 8.0 dialect.
  MariaDB is excluded from this MySQL-specific gate.
- MySQL generated column SQL targets the 5.7+ dialect family (released 5.7 GA and
  newer); pre-5.7 dialects reject a formula rather than emitting an ordinary column.
- Expression DEFAULT handling consumes EXTRA metadata on the 8.0 reader path;
  general expression defaults require MySQL 8.0.13+. No new catalog columns are
  selected on older readers. CURRENT_TIMESTAMP handling remains in the base reader.
- Unique constraints join STATISTICS in the existing set query. A NOT EXISTS
  catalog predicate excludes any constraint-backed index having a NULL column
  name (an expression key part), without referencing the newer EXPRESSION column.
- Views, indexes, unique constraints, checks, column privileges and foreign keys
  still use set-based retrieval. No SHOW CREATE per object, metadata-time expression
  evaluation or new per-object SELECT loop is introduced.

## Verification

Run Java 21 and the repository Gradle Wrapper:

```powershell
.\gradlew.bat :sqlapp-core-mysql:test --tests '*MySqlSchemaMetadataRegressionTest' --offline --console=plain
.\gradlew.bat :sqlapp-core-mysql:test --tests '*MySqlSchemaDdlRegressionTest' --offline --console=plain
.\gradlew.bat :sqlapp-core-mariadb:test --tests '*MariadbInheritedSchemaSqlTest' --offline --console=plain
.\gradlew.bat :sqlapp-core-mysql:test :sqlapp-core-mariadb:test --offline --console=plain
```

The focused tests use in-memory JDBC row sets and real metadata mapping methods,
then generate DDL. Query interception verifies one batch execution for composite
unique/foreign key rows and zero CHECK catalog executions before 8.0.16. SQL
generation tests cover CREATE/ALTER, index DROP, expression indexes, INSERT,
UPDATE, MERGE, OPTIMIZE, identifier quoting and Turkish-locale metadata flags.
MariaDB tests check inherited views/generated columns and its IGNORED and DROP
CONSTRAINT syntax. Existing module suites are also run.

The final full-module run passed **133 MySQL tests and 37 MariaDB tests**, with
zero failures or skipped tests. `git diff --check` passed for the affected paths.
Generated build/test reports are ordinary local Gradle output, not intentional
source or checked-in generated-file changes. The command/Gradle-plugin suites
and opt-in `sqlapp-core-dialect-test:dockerTest` suite were not run for this
dialect-local change.

## Remaining boundaries

This is a documented comparison and a set of verified correctness improvements,
not a claim of complete database backup or every PostgreSQL feature on MySQL.

- No real MySQL/MariaDB instance, Docker database test or production database was
  accessed. Server execution, SQL mode combinations and exact historical server
  binaries remain unverified; generated output alone is not execution evidence.
- View ALGORITHM and the exact DEFINER account are not recovered by the bulk
  INFORMATION_SCHEMA view query. SQL SECURITY is retained; recreation without a
  complete definition uses the executing account as definer. Supply a complete
  definition when preserving that identity or algorithm is required.
- Role memberships, default/mandatory roles, dynamic privileges and routine
  privilege metadata are not added. The MySQL catalog reader still has no
  RoleReader, RoleMemberReader or RoutinePrivilegeReader. These are concrete
  MySQL gaps relative to PostgreSQL, not PostgreSQL-only concepts. User@host
  identity and account authentication metadata require separate coordinated work.
- Spatial SRID metadata, FULLTEXT parser options and all physical storage options
  are not made complete by this change.
- The resolver's coarse 8.0 dialect family does not distinguish every SQL feature's
  introduction patch. Metadata CHECK access uses the actual version; callers
  generating CHECK, expression-index or invisible-column DDL must still target a
  server release supporting that feature.
- MySQL does not have PostgreSQL's native materialized views, exclusion constraints,
  range/multirange types, sequence objects or PostgreSQL procedural language model.
  No automatic emulation is introduced for these product differences.

## Primary references

- [CREATE VIEW](https://dev.mysql.com/doc/refman/8.4/en/create-view.html)
- [CHECK_CONSTRAINTS and the 8.0.16 boundary](https://dev.mysql.com/doc/refman/8.0/en/information-schema-check-constraints-table.html)
- [Column default expressions](https://dev.mysql.com/doc/refman/8.0/en/data-type-defaults.html)
- [INFORMATION_SCHEMA.COLUMNS](https://dev.mysql.com/doc/refman/8.4/en/information-schema-columns-table.html)
- [INFORMATION_SCHEMA.KEY_COLUMN_USAGE](https://dev.mysql.com/doc/refman/8.4/en/information-schema-key-column-usage-table.html)
- [CREATE INDEX](https://dev.mysql.com/doc/refman/8.4/en/create-index.html)
