# MySQL and MariaDB native recreation

[Development index](README.md) · [Dialect comparison](mysql-dialect-parity.md)

The ordinary SchemaReader remains the efficient entry point for component
metadata. For a schema snapshot that also preserves native table and view DDL:

```java
Schema snapshot = MySqlSchemaSnapshot.read(connection, "app");
Table table = snapshot.getTables().get("orders");
var executed = MySqlStoredObjectContext.create(connection, table);
```

`MySqlSchemaSnapshot` is in the MySQL `metadata` package;
`MySqlStoredObjectContext` and `MySqlTriggerOrder` are in its `util` package.
These are additive APIs and work with MySQL and MariaDB connections. Existing
methods, dependency versions and configuration formats are unchanged.

## Component metadata and complete definitions

The snapshot composes the existing bulk schema reader, then issues exactly one
SHOW CREATE TABLE or SHOW CREATE VIEW for each visible table/view in the selected
schema. It stores executable text in the existing `definition` property, keeping
columns, constraints and other component metadata as supplemental information.
Both table and view CREATE factories give a complete definition precedence.
They do not append duplicate columns, indexes or options to that definition.

This explicit full-DDL path is an exception to bulk-only retrieval: MySQL has no
portable bulk catalog containing complete CREATE text or exposing view ALGORITHM
and FULLTEXT parser plugin names. Old versions store physical definitions outside
the public INFORMATION_SCHEMA representation; modern dictionary internals are
not a portable public replacement. The ordinary reader adds no SHOW queries.
The snapshot cost is therefore bulk metadata plus T + V SHOW calls and one
session-settings query, not one query per column/index. Two SET statements are
needed only when SHOW quoting was disabled. Docker tests assert the exact SHOW
count. Query failures and
unavailable/null definitions propagate rather than silently falling back.

The snapshot temporarily enables `sql_quote_show_create` if necessary and restores
it in finally, retaining a restore failure as suppressed when another error is
already active. It records the SQL_MODE used to render native table/view text.
DDL names and bodies are preserved as returned by the server: changing the
model's name or decorateSchemaName option does not rewrite embedded definition
text. Full definitions are for CREATE recreation, not an automatic diff parser
or a database-renaming engine. The schema must already exist when creating an
individual object. Existing objects are not dropped by the helper.

## Creation context and failure handling

Routine, trigger, event and view readers retain available context columns in
the existing specifics map:

| Key | Meaning |
|---|---|
| mysql_sql_mode | Stored-object SQL mode, or native DDL rendering mode |
| mysql_time_zone | Event creation/schedule time zone when available |
| mysql_character_set_client | Original client charset, retained for inspection |
| mysql_collation_connection | Original connection collation, retained for inspection |

`MySqlStoredObjectContext.create(connection, object)` obtains CREATE operations
from the ordinary dialect registry. It applies the saved SQL_MODE and TIME_ZONE,
executes those operations, and restores the original values in finally. When a
different object schema is known, it selects that catalog and restores the
original catalog too. A connection without a current database must select one
before switching catalogs, so restoration has an honest known target.

For a detached trigger with no owning schema, the helper uses its
`tableSchemaName` as the target database, matching the trigger CREATE factory.
This allows a trigger described by its qualified target table to be recreated
while the connection currently selects another database. The original database
is restored after both successful CREATE and failed DDL. When both schema names
are supplied, the existing factory still rejects conflicting identities.

JDBC errors are retained. Failure while applying settings or executing DDL still
restores saved settings; restoration errors are attached as suppressed errors
without hiding the primary failure. The returned operations describe executed
DDL on success. The method executes SQL; the registry alone still only generates
it. DDL can commit implicitly: restoring the session is not transaction rollback,
and earlier successful statements may remain after a later failure.

Client charset/collation values are retained but are not blindly applied through
SET NAMES. Changing the server-side client charset independently of a JDBC
driver's wire encoding could corrupt non-ASCII text. The caller must use a
compatible JDBC encoding and verify any cross-charset migration. Unknown SQL
modes and missing named time zones fail at the destination; they are not removed
or guessed. Authentication, grants and data are not recreated by this helper.

## Trigger activation order

Trigger metadata retains ACTION_ORDER as `mysql_action_order`. The trigger
factory sorts collection CREATE and schema CREATE paths within the same
catalog/schema/table/event/timing group. It does not sort by trigger name or
invent FOLLOWS references to triggers outside the selected group. Separate groups
retain their first-seen order. An entirely unranked group keeps input order.
If a group has multiple triggers and any explicit rank, every member must have
a distinct positive rank. Zero remains readable for a single legacy trigger.

For context-aware execution of a trigger collection:

```java
for (Trigger trigger : MySqlTriggerOrder.sort(new ArrayList<>(snapshot.getTriggers()))) {
    MySqlStoredObjectContext.create(connection, trigger);
}
```

Create into an empty trigger group to recreate its activation order. A single
CREATE cannot synchronize unrelated triggers already present at the target.
MySQL requires a release supporting multiple same-event/timing triggers, and
MariaDB has its own boundary. No automatic emulation on old servers is introduced.

## Docker verification scope

The authorized tests use disposable Testcontainers, with reuse disabled, against
local mysql:5.7, mysql:8.0, mysql:8.4, mariadb:10.5, mariadb:11.4 and mariadb:11.8
images. Fixtures use generated names in the container's test database and clean
up their own objects. Events are disabled. No external or production database is
used, and no Docker volume/image cleanup is performed outside those containers.

The recreation audit executes native table/view definitions, checks FULLTEXT
parser preservation on MySQL, ROW_FORMAT, a TEMPTABLE view, trigger execution
order despite reversed name/input order, and a NO_BACKSLASH_ESCAPES-dependent
function. It recreates an event at the same +09:00 schedule and checks SQL_MODE,
TIME_ZONE, current database and SHOW quoting restoration. Invalid DDL and an
invalid named time zone exercise error-path restoration. The larger scoped suite
also covers metadata, bulk loading, checkpoints, upserts and hierarchical writes.

## Boundaries

This is schema object recreation, not a complete server backup or replication
configuration restore. Custom global SRS definitions, physical tablespace files,
authentication/plugin secrets, proxy/mandatory-role configuration and partial
revoke restrictions still need separate server-level handling. A complete table
definition can refer to such global resources without containing their creation
DDL. The snapshot sees only objects visible to the connection's privileges.
PostgreSQL-only features are not emulated on MySQL.

## Verification recorded on 2026-10-24

The focused snapshot/context tests passed first, followed by the MySQL 8.4
recreation case and MySQL 8.0 metadata class. The final command was the repository
wrapper equivalent of:

```text
gradlew :sqlapp-core-mysql:test :sqlapp-core-mariadb:test :sqlapp-core-dialect-test:dockerTest --tests '*mysql.*' --tests '*mariadb.*' --no-daemon --offline --console=plain --init-script <temporary-init-script>
```

It completed successfully. The XML reports contain **283 MySQL unit tests,
50 MariaDB unit tests and 50 Docker tests**, with zero failures, errors or skips.
The final check reused the already-passed unit results and executed the Docker
scope again after fixture fixes. Docker scope contains 18 metadata/recreation
cases, 14 bulk cases and 18 tree-session cases across ten classes. The tested
containers were stopped; an unrelated SAP HANA container was left untouched.
No generated files were intentionally edited and no external database was used.

Testing used the explicitly authorized Java 25 runtime/toolchain override in a
temporary Gradle init script. Repository toolchain declarations, dependency
versions and local settings were not changed. An unscoped aggregate attempt
stopped on an unrelated in-progress SAP HANA compilation error. To preserve that
work, the temporary init script selected mysql/mariadb Docker source packages and
their shared helpers, retaining project dependencies on core, command, core-test,
mysql, mariadb and hsql. Other database implementations were excluded only from
that temporary test invocation. This does not establish a green whole-repository
build or cover other database Docker suites. Command/plugin test suites were not
run, though the command implementation compiled as a Docker-test dependency.

Earlier failures were resolved before the final successful run: a stale index
audit counted both unique-constraint and ordinary-index STATISTICS queries as
one; it now asserts two set queries and separately one ordinary-index query.
A test initially bound a boolean session variable as a string; it now binds an
integer. MySQL 8.0's disposable fixture needed the same
`--log-bin-trust-function-creators=1` startup setting as the existing 8.4 fixture.
The scoped init script was also guarded against the separate build-logic build.
Expected invalid-DDL/invalid-time-zone errors in restoration tests are asserted
error paths, not remaining test failures.

Local image IDs (tags are not immutable):

| Image | Image ID prefix |
|---|---|
| mysql:5.7 | 4bc6bc963e6d |
| mysql:8.0 | 7dcddc01f13b |
| mysql:8.4 | b3b90af2a655 |
| mariadb:10.5 | a530aeeefd82 |
| mariadb:11.4 | 4f1d8d202fcf |
| mariadb:11.8 | efb4959ef2c8 |

## Detached-trigger follow-up (2026-10-24)

The context helper now resolves a detached trigger's database from
`tableSchemaName` when its own schema is absent. Each of the six Docker versions
creates such a trigger while the connection selects `information_schema`, checks
that the original database is restored, and checks restoration after deliberately
invalid trigger DDL. Trigger effects still verify the original activation order.
A unit regression also covers lowercase timing/event input and duplicate ranks;
event names were already normalized by the shared model, so no model or sorting
change was required.

The focused `MySqlSnapshotContextTest` passed all four cases. The same scoped
wrapper command recorded above then passed **284 MySQL unit tests, 50 MariaDB
unit tests and 50 Docker tests**, with zero failures, errors or skips, using the
authorized Java 25 override. Public signatures, dependencies and bulk metadata
query counts are unchanged. No external database, whole-repository build or
command/plugin test suite was run in this follow-up; no generated files were
intentionally edited.

## References

- [SHOW CREATE TABLE](https://dev.mysql.com/doc/refman/8.4/en/show-create-table.html)
- [SHOW quoting configuration](https://dev.mysql.com/doc/refman/8.0/en/server-system-variables.html)
- [Event scheduling and time zones](https://dev.mysql.com/doc/refman/8.4/en/events-metadata.html)
- [Trigger metadata and ACTION_ORDER](https://dev.mysql.com/doc/mysql-infoschema-excerpt/5.7/en/information-schema-triggers-table.html)
