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
| Views | Query stays in `statement`; separately modeled output names, optional ALGORITHM, SQL SECURITY and LOCAL/CASCADED CHECK OPTION generate native DDL. Complete `definition` remains the advanced path |
| View column metadata | Uses the same version-specific column reader as tables, preserving fractional seconds and modern type handling |
| Primary and unique constraints | Bulk statistics join preserves prefix lengths, order, index type, comment and visibility; native DDL retains these attributes |
| Functional unique indexes | Retained exclusively as Index objects when any key part is an expression; a partial UniqueConstraint no longer suppresses the complete index during table creation |
| Index generation | UNIQUE is retained, prefix lengths and descending order are emitted, standalone index names remain local to the table, DROP INDEX includes its owner |
| Index validation | CREATE/ALTER share validation for unsupported functional/visibility features, invalid prefixes and unsupported INCLUDE/NULL ordering; catalog RTREE indexes generate native SPATIAL INDEX |
| Physical options and comments | Table ROW_FORMAT survives bulk metadata and CREATE/ALTER; engine/row-format tokens and compression consistency are validated; removing a table comment emits COMMENT '' |
| Optional FULLTEXT parser | The existing Index specifics map accepts an installed parser plugin name; native WITH PARSER composes with standalone, inline and ALTER generation |
| Index visibility changes | MySQL 8+ ordinary Index visibility-only changes use ALTER INDEX VISIBLE/INVISIBLE rather than DROP/ADD; other index changes retain full recreation |
| Foreign keys | Composite key order and actions remain modeled; referenced schema now comes from KEY_COLUMN_USAGE instead of the referenced constraint's schema, which can be NULL |
| CHECK constraints | Actual server version gates catalog access at MySQL 8.0.16; ALTER emits DROP CHECK and replacement clauses. MariaDB retains its catalog query and DROP CONSTRAINT syntax |
| Generated columns | Native GENERATED ALWAYS AS plus VIRTUAL/STORED in CREATE and ALTER; MySQL 5.7+ dialect capability enables exclusion from INSERT/UPDATE/MERGE writes |
| Column defaults | CURRENT_TIMESTAMP remains executable; DEFAULT_GENERATED expressions retain parentheses instead of becoming character literals; ordinary function-like literal strings stay quoted |
| Column attributes | ON UPDATE extraction tolerates other EXTRA flags, NULL EXTRA is accepted, and invisible/generated flags are independent of the process locale |
| Column character attributes | Standalone column definitions emit an explicitly configured character set/collation instead of omitting it |
| Table character defaults | Bulk table metadata retains the character set through a collation catalog join; CREATE retains both defaults and ALTER changes defaults without implicitly converting all columns |
| Object/global/column privileges | Existing readers retain scope and account identity; native MySQL GRANT/REVOKE generation now covers table/view, column and global grants, with scope validation and MySQL 8+ dynamic privilege names |
| Database-level privileges | Full-catalog loading includes batched SCHEMA_PRIVILEGES in the existing shared collection; native GRANT/REVOKE preserves the source partial_revokes interpretation and requires matching destination semantics |
| Account and routine-grant identity | Complete user@host identity, routine kind and grant option; native routine GRANT/REVOKE generation; see [privilege metadata](mysql-privileges.md) |
| MySQL 8+ roles | Batched known-role/direct-membership metadata; native CREATE/DROP and GRANT/REVOKE; explicit complete-set default restoration |
| Maintenance | OPTIMIZE resolves to OPTIMIZE TABLE instead of ANALYZE TABLE |
| Scheduled events | Schema/catalog identity, interval units and compound values, quoted schedule timestamps, completion and replica-disabled status; complete definition precedence and invalid schedule validation |
| Stored object definers | Views, routines, triggers and events retain available DEFINER identities; ambiguous raw accounts require an explicit quoted override; complete definitions retain precedence |
| Trigger components | Target schema is retained in ON clauses; missing bodies/targets, unsupported WHEN or statement orientation, disabled state and custom row aliases fail clearly instead of being omitted |
| Native snapshots and context | Optional full-DDL snapshots retain table/view native definitions; context-aware execution restores SQL_MODE, event TIME_ZONE and current database; trigger collection creation retains ACTION_ORDER |
| Spatial columns | Native geometry subtype names, existing geometry JDBC handler and MySQL 8.0.3+ column SRID restrictions survive metadata and CREATE/ALTER generation |
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
view.getSpecifics().put("algorithm", "MERGE");      // UNDEFINED, MERGE or TEMPTABLE
```

Readers populate these settings from INFORMATION_SCHEMA.VIEWS. Invalid values
and missing query/definition fail clearly. Output columns, when present, form
the view's column list. A complete `definition` takes precedence over components.

MySQL INFORMATION_SCHEMA.VIEWS supplies security/check settings but has no
ALGORITHM column. Its reader does not infer an algorithm from the query or
updatability flag. Supply the optional `algorithm` setting when known, or a
complete definition. With no setting, SQL generation leaves algorithm selection
to the server. All three values are normalized case-insensitively; blank and
unknown values fail clearly. `TEMPTABLE` combined with LOCAL/CASCADED CHECK OPTION
also fails, because such views cannot be updatable. SQL emits ALGORITHM before
DEFINER and SQL SECURITY, while preserving the existing query and output columns.

MariaDB's inherited view reader retains ALGORITHM when that column is present in
the existing bulk `SELECT *` result. Older results without the column remain
readable. No per-view query, query-text parsing or MySQL-only catalog access is
added. `MySqlCreateViewFactory.ALGORITHM` is an additive constant for the existing
specifics map; public method signatures and default generation are unchanged.
Source names and query mergeability still require validation by the target
server; an algorithm request cannot guarantee that a particular query is mergeable.

See [view algorithms](https://dev.mysql.com/doc/refman/8.0/en/view-algorithms.html),
[view updatability](https://dev.mysql.com/doc/refman/8.4/en/view-updatability.html)
and [MariaDB VIEWS metadata](https://mariadb.com/docs/server/server-usage/views/information-schema-views-table).

The ALGORITHM follow-up first verified the focused generation/metadata classes,
then ran `:sqlapp-core-mysql:test :sqlapp-core-mariadb:test` with the wrapper,
`--no-daemon --offline --console=plain` and the authorized temporary Java 25
toolchain override. The final run passed **234 MySQL tests and 45 MariaDB tests**,
with zero failures, errors or skips. Seven new MySQL cases exercise all algorithms
across 5.1/5.5/5.7/8.x/9.x, option composition, normalization, invalid/conflicting
settings and definition precedence. Two MariaDB cases read three views in one
query, with and without the optional column, then recreate them. Initial test
expectation/return-type errors were corrected before the successful runs.
External database, Docker and command/plugin suites were not run; no generated
files were intentionally changed.

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

## Scheduled events

The existing Schema `Event` and CREATE registry entry remain the high-level API:

```java
var event = new Event("cleanup");
event.setSchemaName("app");
event.setStatement("DELETE FROM audit_log WHERE expired = 1");
event.setIntervalValue(1);
event.setIntervalField("DAY");
var operations = registry.createSql(event, SqlType.CREATE);
```

The generator now emits `EVERY 1 DAY`, or `AT 'yyyy-MM-dd HH:mm:ss'` for a
one-time event. STARTS and ENDS are also quoted. Event type can be omitted when
the schedule determines it; a conflicting explicit type, missing body/schedule,
nonpositive interval, unsupported unit, invalid completion/status or reversed
STARTS/ENDS fails clearly. It no longer adds unconditional `IF NOT EXISTS`, so
an existing event is reported by the server instead of silently keeping its old
definition. A complete `definition` takes precedence over component fields.

The existing shared Integer interval property handles ordinary schedules.
Optional `specifics["mysql_interval_value"]` retains exact compound interval
text (for example `2:10` with MINUTE_SECOND) or values exceeding Integer capacity.
When present it takes precedence over `intervalValue`; remove the specifics key
when switching back to that property. Compound quantities are quoted; units are
validated against the event grammar. Server-specific quantity limits still apply.
The metadata reader retains this text instead of truncating it with JDBC getInt.

`specifics["mysql_event_status"]` optionally retains SLAVESIDE_DISABLED and emits
DISABLE ON SLAVE, using syntax compatible with older MySQL and MariaDB. It must
agree with the existing enable flag. Normal enabled/disabled states continue to
use that flag alone. Metadata now also retains catalog/schema identity and
LAST_EXECUTED, using the existing single INFORMATION_SCHEMA.EVENTS query. No
per-event SHOW CREATE or extra SELECT is added. These fixes target the existing
MySQL 5.5+ dialect families and inherited MariaDB event SQL; historical pre-5.5
server execution is not verified. Resolver mappings and catalog column version
boundaries remain unchanged.

Event DEFINER is retained as raw metadata and emitted by component generation;
see [definer preservation](mysql-stored-object-ddl.md#definer-preservation).
SQL generation alone does not change the session. Use the additive
`MySqlStoredObjectContext.create` execution path to apply saved TIME_ZONE and
SQL_MODE and restore the caller's settings; see [native recreation](mysql-native-recreation.md).
These changes therefore do not constitute complete event/security backup.
Event creation does not enable the server's event scheduler or execute the body.
See the official [CREATE EVENT syntax](https://dev.mysql.com/doc/refman/8.0/en/create-event.html)
and [EVENTS metadata](https://dev.mysql.com/doc/refman/8.0/en/information-schema-events-table.html).

## Spatial columns and SRID restrictions

MySQL's GEOMETRY, POINT, LINESTRING, POLYGON, MULTIPOINT, MULTILINESTRING,
MULTIPOLYGON and GEOMETRYCOLLECTION (plus its GEOMCOLLECTION alias) resolve to the
shared `DataType.GEOMETRY` representation. The original subtype remains in
`dataTypeName` and is emitted by the MySQL builder for columns and routine types.
GEOMCOLLECTION, introduced by MySQL 8.0.11, is emitted as its equivalent
GEOMETRYCOLLECTION spelling to remain compatible with earlier destination versions.
An unspecified subtype still emits GEOMETRY. MySQL now registers its existing
geometry JDBC handler directly: the previous legacy JTS package check could
silently skip registration even though current geometry libraries are already
declared dependencies. No dependency version or shared core utility is changed.

For MySQL 8.0.3 and later, the column reader retains `COLUMNS.SRS_ID` as optional
`specifics["mysql_srid"]`. An absent restriction remains absent; explicit SRID 0
remains a restriction and is never conflated with NULL. The existing batched
`c.*` query already supplies the field. The reader uses the actual product version
and does not access SRS_ID before 8.0.3; no new catalog query or per-column SELECT
is added. Table and view column readers share this behavior.

The existing model and SQL registry are the entry point. An explicit restriction
can be added to a spatial column when needed:

```java
var column = new Column("location");
dialect.setDbType("POINT", null, null, column);
column.getSpecifics().put("mysql_srid", "4326");
table.getColumns().add(column);
var operations = registry.createSql(table, SqlType.CREATE);
```

CREATE, generated-column definitions and ALTER preserve the restriction. Changing
only the specifics value regenerates the column definition; removing it emits a
definition without SRID. Accepted values are decimal unsigned 32-bit integers
(0 through 4294967295). Invalid values, nonspatial columns, pre-8.0 MySQL dialects
and MariaDB reject the MySQL-specific restriction instead of silently dropping
it. MariaDB retains ordinary spatial subtype support through its inherited path.
Existing public method signatures and Schema XML formats are unchanged; the
specifics key and version-aware protected reader hook are additive. Metadata
that previously fell back to OTHER can now resolve to GEOMETRY and use the
existing MySQL binary geometry JDBC handler.

The 8.0 dialect family does not distinguish every patch release. Generation with
that dialect requires callers to target MySQL 8.0.3+ when specifying SRID; metadata
reading gates on the actual server patch. This feature does not create or copy
custom spatial reference systems, transform coordinates, validate existing table
data against a new SRID, or infer a restriction from a SPATIAL index. Required
SRS definitions must already exist on the destination. Server execution, geographic
axis order and spatial index performance remain unverified by these unit tests.
See [MySQL 8.0.3 release notes](https://dev.mysql.com/doc/relnotes/mysql/8.0/en/news-8-0-3.html),
[spatial column semantics](https://dev.mysql.com/doc/refman/8.0/en/spatial-type-overview.html)
and the [SRID specification](https://dev.mysql.com/worklog/task/?id=8592).
The [8.0.11 release notes](https://dev.mysql.com/doc/relnotes/mysql/8.0/en/news-8-0-11.html)
describe the geometry collection alias boundary.

## Verification

Run Java 21 and the repository Gradle Wrapper:

```powershell
.\gradlew.bat :sqlapp-core-mysql:test --tests '*MySqlSchemaMetadataRegressionTest' --offline --console=plain
.\gradlew.bat :sqlapp-core-mysql:test --tests '*MySqlSchemaDdlRegressionTest' --offline --console=plain
.\gradlew.bat :sqlapp-core-mariadb:test --tests '*MariadbInheritedSchemaSqlTest' --offline --console=plain
.\gradlew.bat :sqlapp-core-mysql:test --tests '*MySqlEventRoundTripTest' --offline --console=plain
.\gradlew.bat :sqlapp-core-mysql:test --tests '*MySqlSpatialColumnTest' --offline --console=plain
.\gradlew.bat :sqlapp-core-mysql:test :sqlapp-core-mariadb:test --offline --console=plain
```

The focused tests use in-memory JDBC row sets and real metadata mapping methods,
then generate DDL. Query interception verifies one batch execution for composite
unique/foreign key rows and zero CHECK catalog executions before 8.0.16. SQL
generation tests cover CREATE/ALTER, index DROP, expression indexes, INSERT,
UPDATE, MERGE, OPTIMIZE, identifier quoting and Turkish-locale metadata flags.
MariaDB tests check inherited views/generated columns and its IGNORED and DROP
CONSTRAINT syntax. Existing module suites are also run.

The earlier schema-focused run passed **133 MySQL tests and 37 MariaDB tests**, with
zero failures or skipped tests. `git diff --check` passed for the affected paths.
The scheduled-event follow-up passed **174 MySQL tests and 39 MariaDB tests**,
also with zero failures or skipped tests. Its focused run passed all 16 event
cases and all 5 MariaDB inherited-schema cases, including the new event case.
After Java 21 became unavailable during verification, the user explicitly chose
Java 25 for this run. Both focused and full commands used the wrapper with
`--no-daemon`, a process-local JAVA_HOME, `-Dorg.gradle.java.home` pointing to JDK 25,
and `--init-script` pointing to a temporary script that selects Java toolchain 25
through JavaPluginExtension after project evaluation. The repository's Java 21
source/target/toolchain settings and local configuration were not modified.
Gradle emitted stale-daemon diagnostics referring to the removed Java 21 runtime;
the final two test runs nevertheless ended with BUILD SUCCESSFUL. Initial
compilation/fixture/test-location failures were corrected before those runs.
The subsequent [definer follow-up](mysql-stored-object-ddl.md#definer-preservation)
passed **180 MySQL tests and 39 MariaDB tests**, with zero failures or skips,
using the same temporary Java 25 approach. Existing repository settings remain
unchanged; the six new focused definer cases also passed independently.
The spatial follow-up passed all 13 focused MySqlSpatialColumnTest cases and the
6 MariaDB inherited-schema cases, then passed **193 MySQL tests and 40 MariaDB
tests** in the full two-module run, with zero failures or skips. It used the same
process-local Java 25 setup and wrapper flags. An initial subtype-name loss found
by the focused tests was corrected before the final successful runs. Coverage
includes all spatial subtype names, default GEOMETRY resolution, the geometry
JDBC handler selection, routine types, NULL versus SRID 0, the unsigned maximum,
invalid values, generated columns, SRID-only ALTER/removal, 8.0.2/8.0.3 metadata
boundaries, and one batch query for twelve columns. Older-version tests assert
that SRS_ID is never accessed. MariaDB checks ordinary POINT generation and
rejection of the MySQL-only SRID restriction. No external database tests were run.
The table/column/global privilege SQL follow-up subsequently passed **200 MySQL
tests and 41 MariaDB tests**, with zero failures or skips, using the same temporary
Java 25 setup. Its seven focused metadata-to-DCL cases also passed separately;
see [native privilege SQL](mysql-privileges.md#table-column-and-global-grantrevoke).
The database-level grant follow-up passed **209 MySQL tests and 41 MariaDB tests**,
with zero failures or skips, using the same Java 25 wrapper configuration. Its
nine focused tests also passed separately, including catalog loading, one batch
query for twelve scopes, actual-version query selection and explicit wildcard
mode handling; see [database grants](mysql-privileges.md#database-level-grants).
Generated build/test reports are ordinary local Gradle output, not intentional
source or checked-in generated-file changes. The command/Gradle-plugin suites
and opt-in `sqlapp-core-dialect-test:dockerTest` suite were not run for this
dialect-local change.

## Database character set and collation SQL

`MySqlCreateSchemaFactory` emits both defaults when the Schema model supplies
both a character set and a collation. `MySqlAlterSchemaFactory` reads scalar
differences directly, avoiding the shared schema hook's object-difference cast.
When either default changes, ALTER emits all supplied target defaults so that
changing one does not implicitly reset the other to a server-selected value.
Unchanged schemas and documentation-only changes produce no empty ALTER SQL;
child-object differences still use the shared generator.

These changes apply to the MySQL dialect families and the MariaDB factories
that inherit them. Existing public signatures remain available. Database names
use the existing identifier quoting, while character-set and collation values
must be plain name tokens. Unknown names and incompatible pairs are validated
by the target server. Removing a known default requires an explicit replacement;
the generator does not guess the destination server default. Database rename
differences fail clearly because MySQL has no ALTER DATABASE rename operation.

Changing database defaults does not convert existing table or column data.
See [ALTER DATABASE](https://dev.mysql.com/doc/refman/8.4/en/alter-database.html)
and [database character sets](https://dev.mysql.com/doc/mysql-g11n-excerpt/5.7/en/charset-database.html).

Validation used the repository wrapper with the previously authorized temporary
Java 25 toolchain override, `--no-daemon --offline --console=plain`. The focused
`MySqlSchemaDefaultsTest` passed seven cases, and
`MariadbInheritedSchemaSqlTest` passed eight cases. The final
`:sqlapp-core-mysql:test :sqlapp-core-mariadb:test` run passed **216 MySQL tests
and 42 MariaDB tests**, with zero failures, errors or skips. Early runs exposed
a test compilation error and an incorrectly placed MariaDB resolver test (the
MySQL test classpath has no MariaDB implementation); both were corrected before
these successful runs. No external database tests or command/plugin suites were
run, and no generated files were intentionally changed.

## Table character set and collation

The existing table reader now joins `INFORMATION_SCHEMA.COLLATIONS` in its
single table query to resolve `TABLE_COLLATION` to `CHARACTER_SET_NAME`. It
stores both values in the shared Table model without deriving character-set
names by splitting a collation string. The LEFT JOIN retains objects whose
collation is NULL. Existing schema/table filters remain bound parameters. The
catalog columns are available on the existing INFORMATION_SCHEMA reader path,
including MySQL 5.x; no newer-only column or per-table lookup is introduced.
MariaDB inherits the same query and generation changes.

CREATE TABLE emits a configured table character set even if no collation is
specified. ALTER TABLE emits `DEFAULT CHARACTER SET ... COLLATE ...` using
the supplied target defaults when either changes. It previously emitted
`CONVERT TO CHARACTER SET`, which could convert unchanged columns and alter
their data types. A table default describes defaults for future columns;
existing column charset/collation changes must be expressed in the Column
models and are generated as column changes. No blanket data conversion is
inferred from changing a Table property.

No public signatures or dependencies change. This intentionally changes the
generated ALTER behavior. Callers relying on the previous implicit conversion
must describe each intended column change in the target Schema model. Removed
defaults require an explicit replacement, malformed option tokens fail clearly,
and the server validates supported names and compatible charset/collation pairs.
The generator retains explicitly supplied unchanged target defaults to avoid
resetting them implicitly. It does not inspect the destination server's defaults.

See [table character sets](https://dev.mysql.com/doc/refman/8.4/en/charset-table.html?ff=nopfpls),
[COLLATIONS metadata](https://dev.mysql.com/doc/mysql-infoschema-excerpt/5.7/en/information-schema-collations-table.html)
and [ALTER TABLE conversion](https://dev.mysql.com/doc/refman/8.0/en/alter-table.html).

Validation first ran `MySqlTableDefaultsTest`,
`MySqlSchemaMetadataRegressionTest`, `MySqlAlterTableFactoryTest` and
`MariadbInheritedSchemaSqlTest`, then the complete
`:sqlapp-core-mysql:test :sqlapp-core-mariadb:test` scope. Both runs succeeded
with the repository wrapper, `--no-daemon --offline --console=plain` and the
authorized temporary Java 25 toolchain override. The final reports contain
**227 MySQL tests and 43 MariaDB tests**, with zero failures, errors or skips.
New cases cover MySQL 5.1/5.7/8.x/9.x, a single query for twelve tables, NULL
collations, bound filters, individual defaults, unchanged column character
settings, simultaneous column/default changes and invalid/removal requests.
Existing ALTER SQL expectation resources were intentionally updated; no
generated files were intentionally changed. External database, Docker and
command/plugin suites were not run.

## Index generation validation

Standalone CREATE INDEX, inline CREATE TABLE and index additions through ALTER
TABLE use the same dialect-local validation. Unique/primary constraints validate
their backing Index as well. These checks preserve public signatures and add no
queries, dependencies or Schema properties.

- MySQL 5.x rejects functional key parts and invisible indexes. MySQL 8+ dialect
  families retain functional expressions, uniqueness, order and invisibility.
  Functional key parts require an actual server at least 8.0.13; the coarse 8.0
  resolver family cannot distinguish that patch boundary. MariaDB rejects this
  MySQL expression syntax; use a modeled generated column and an ordinary index.
- Empty keys, blank key names, nonpositive prefix lengths, functional key prefixes,
  FULLTEXT/SPATIAL prefixes, unique FULLTEXT/SPATIAL combinations, unsupported
  vendor index types, INCLUDE columns and explicit NULLS FIRST/LAST fail with an
  actionable exception rather than being ignored or changed silently.
- Functional unique keys use the existing unique Index representation. A
  UniqueConstraint with expression-only key parts fails clearly rather than
  emitting them as quoted column names. Primary indexes cannot be invisible or
  ignored. Implicit InnoDB primary-key selection remains server validation.
- STATISTICS reports spatial indexes as RTREE. The generator accepts the existing
  RTree model value and emits `SPATIAL INDEX`, including on older MySQL versions
  where explicitly specifying `USING RTREE` is unavailable. No metadata query or
  stored model representation changes are necessary.

Storage-engine restrictions, expression semantics, descending-index behavior and
server-specific limits remain target-server checks. MariaDB IGNORED syntax is
unchanged: callers must target MariaDB 10.6+; its existing 10.5 dialect family also
covers newer 10.x releases, so it cannot enforce that precise boundary. Ordinary
indexes and indexes over modeled generated columns remain available.

See [MySQL CREATE INDEX](https://dev.mysql.com/doc/refman/8.0/en/create-index.html),
[invisible indexes](https://dev.mysql.com/doc/refman/8.4/en/invisible-indexes.html)
and [MariaDB ignored indexes](https://mariadb.com/docs/server/ha-and-performance/optimization-and-tuning/optimization-and-indexes/ignored-indexes).

Validation first ran `MySqlIndexValidationTest` (15 cases) and
`MariadbInheritedSchemaSqlTest` (10 cases), then
`:sqlapp-core-mysql:test :sqlapp-core-mariadb:test` with the repository wrapper,
`--no-daemon --offline --console=plain` and the authorized temporary Java 25
toolchain override. The final run passed **249 MySQL tests and 46 MariaDB tests**,
with zero failures, errors or skips. Tests cover the three generation paths,
old/new dialect families, retained generated-column indexes, prefix validation,
unsupported vendor attributes, primary visibility and native spatial SQL.
An initial ambiguous-null compilation error and a SQL whitespace expectation
were corrected before the successful runs. External database, Docker and
command/plugin suites were not run; no generated files were intentionally changed.

The trigger component follow-up verified required fields and schema-qualified
targets; see [trigger components](mysql-stored-object-ddl.md#trigger-component-validation-and-target-identity).
The final Java 25 wrapper check succeeded with retained UP-TO-DATE test results:
**261 MySQL tests and 47 MariaDB tests**, with zero failures, errors or skips.
Focused tests were executed before that check. External database, Docker and
command/plugin suites remain unexecuted for this dialect-local change.

## Physical options, comments and index changes

The table reader retains `ROW_FORMAT` in specifics as well as its existing
statistics entry. The value comes from the existing TABLES result, so there is
no additional query, newer catalog column or per-object lookup. CREATE and ALTER
use the existing `ENGINE` and `ROW_FORMAT` settings. CREATE normalizes row-format
case, while metadata retains the catalog spelling. Names must be plain tokens;
unknown engines/formats and storage-engine compatibility are still checked by
the destination server. Existing ROW_FORMAT statistics remain available.

`compression=true` selects COMPRESSED when no explicit row format is supplied.
It conflicts with an explicitly different format. A compression change now
generates ALTER SQL; disabling compression requires an explicit target format
instead of guessing one. Removing ENGINE or ROW_FORMAT likewise requires an
explicit replacement such as a deliberately selected DEFAULT where supported.
In a metadata-derived target, change the compression flag consistently with
ROW_FORMAT. These settings do not create tablespaces, migrate engine data
externally, set server variables or guarantee a server-specific physical layout.
MariaDB inherits these changes and keeps its own additional table options.

Removing table remarks emits `ALTER TABLE ... COMMENT ''` for either a null or
empty target, including when combined with column changes. Previously the clear
request was omitted. Existing comment text still uses the SQL literal encoder.

For an installed FULLTEXT parser, the existing Index entry point remains:

```java
index.setIndexType(IndexType.FullText);
index.getSpecifics().put(MySqlCreateIndexFactory.PARSER, "my_parser");
```

The additive constant names `mysql_parser`. The value is a plugin identifier,
quoted as one identifier with embedded backticks escaped. Blank values, control
characters and use on a non-FULLTEXT index fail clearly. CREATE INDEX, inline
CREATE TABLE and ALTER additions/recreations share this generation. Changing
only the parser still recreates the index because it changes index semantics.
Bulk STATISTICS metadata does not expose the parser name: callers must supply
the known override or another complete restoration source. No per-index SHOW
CREATE or SQL parsing is introduced. Parser plugins require MySQL 5.1+ syntax;
InnoDB parser support requires MySQL 5.7.3+ (the 5.7 GA family), while older
supported servers require a compatible engine such as MyISAM. MariaDB engine
and plugin restrictions also remain server checks. The generator does not
install a plugin, assume that ngram is available or access the destination.

For MySQL 8+ ordinary Index objects, a diff containing only ENABLE emits native
`ALTER TABLE ... ALTER INDEX ... VISIBLE/INVISIBLE`, preserving the index instead
of dropping and recreating it. Other attributes, including comments, keys or
parser changes, keep the existing recreation path. This optimization excludes
MariaDB and constraint-backed visibility changes; their existing paths remain.
The same index validators run before the native operation. Public method
signatures, dependencies and resolver mappings are unchanged.

See [CREATE TABLE row formats](https://dev.mysql.com/doc/refman/8.4/en/create-table.html),
[parser plugins](https://dev.mysql.com/doc/extending-mysql/8.4/en/plugin-types.html),
[invisible index changes](https://dev.mysql.com/doc/refman/8.4/en/invisible-indexes.html)
and [MariaDB CREATE INDEX](https://mariadb.com/docs/server/reference/sql-statements/data-definition/create/create-index).

This batch first ran `MySqlPhysicalOptionsTest` (19 new cases),
`MySqlSchemaMetadataRegressionTest` and `MariadbPhysicalOptionsTest` (3 new cases).
The metadata cases still assert one query for twelve tables and now check retained
ROW_FORMAT, compression and NULL handling. All focused tests passed, followed by
a successful `:sqlapp-core-mysql:test :sqlapp-core-mariadb:test` run containing
**280 MySQL tests and 50 MariaDB tests**, with zero failures, errors or skips.
Both commands used the repository wrapper, `--no-daemon --offline --console=plain`
and the authorized temporary Java 25 toolchain override. There were no failed
runs in this batch. Public method signatures and dependencies remain unchanged;
the parser constant is additive. No generated files were intentionally changed.
External database, Docker and command/plugin suites were not run.

## Native recreation and Docker follow-up

[Native recreation](mysql-native-recreation.md) adds an explicit full-DDL snapshot
path for table/view attributes missing from the bulk catalogs, saved SQL_MODE and
event TIME_ZONE execution with finally restoration, and ACTION_ORDER-based trigger
creation. Complete table definitions now take precedence like view/routine
definitions. Ordinary bulk-reader queries remain unchanged. The explicit SHOW
CREATE exception, context limitations and failure behavior are documented there.

The final Java 25 scoped check succeeded with **283 MySQL unit tests, 50 MariaDB
unit tests and 50 Docker tests**, with no failures, errors or skips. Docker covers
MySQL 5.7/8.0/8.4 and MariaDB 10.5/11.4/11.8, including actual native DDL execution,
trigger effects, mode-sensitive function results, event schedule preservation,
error-path restoration, bulk loads and tree sessions. Earlier index-count and
fixture-setting failures were corrected. The aggregate build's unrelated SAP HANA
work was isolated with a temporary test scope rather than modified; see the guide
for the exact coverage and image IDs. No production database was accessed.

## Remaining boundaries

This is a documented comparison and a set of verified correctness improvements,
not a claim of complete database backup or every PostgreSQL feature on MySQL.

- Authorized disposable Docker verification now covers MySQL 5.7/8.0/8.4 and
  MariaDB 10.5/11.4/11.8. No external or production database was accessed. Other
  SQL-mode combinations and historical patch binaries remain unverified; see
  [native recreation](mysql-native-recreation.md) for the exercised scope.
- MySQL view ALGORITHM is not recovered by the bulk INFORMATION_SCHEMA query. SQL
  SECURITY and available raw DEFINER identities are retained. Ambiguous raw
  identities require an explicit `mysql_definer` account or complete definition;
  see [definer preservation](mysql-stored-object-ddl.md#definer-preservation).
  Supply the known `algorithm` override or use `MySqlSchemaSnapshot.read` to retain
  complete native table/view definitions, including ALGORITHM and FULLTEXT parser
  clauses. This explicit path uses documented SHOW CREATE queries; ordinary bulk
  readers remain unchanged. MariaDB's available ALGORITHM column is retained.
- [Privilege metadata](mysql-privileges.md) now covers complete user@host identities,
  explicit routine grants, known MySQL 8+ roles and direct memberships. Mandatory
  roles, proxy grants, runtime activation,
  exact membership synchronization, partial revokes and
  complete authentication metadata remain outside the implementation. Dynamic
  global privilege names already flow through USER_PRIVILEGES; no duplicate
  global-grant query is required.
- Custom spatial reference system definitions, automatic FULLTEXT parser metadata
  and all physical storage options are not made complete by this change. Column
  SRID restrictions, explicit parser overrides and ROW_FORMAT are covered above.
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
