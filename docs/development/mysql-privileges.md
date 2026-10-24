# MySQL account and privilege metadata

[MySQL dialect comparison](mysql-dialect-parity.md)

Use the existing catalog reader to obtain users, explicit routine privileges,
and, on MySQL 8+, known roles and direct role memberships:

```java
var dialect = DialectResolver.getInstance().getDialect(connection);
var reader = dialect.getCatalogReader();
var catalog = reader.getAllFull(connection).get(0);
var routineGrants = catalog.getRoutinePrivileges();
var memberships = catalog.getRoleMembers();
```

For just routine grants, `reader.getRoutinePrivilegeReader().getAllFull(connection)`
uses the same metadata mapping without requiring an entire catalog read.
This reader is registered only for MySQL. MariaDB's existing role readers and
routine-metadata availability remain unchanged.

## Account identity and compatibility

MySQL accounts contain a user and a host. Users with the same name and different
hosts must not collapse into one Schema user. MySQL User names now use the
canonical identity `'user'@'host'`, matching the identities retained in role
memberships and global/table/column/routine grants. The old simple lookup
`catalog.getUsers().get("reader")` must become
`catalog.getUsers().get("'reader'@'localhost'")` for metadata obtained by this
reader. `MySqlAccount.name(user, host)` constructs the canonical identity,
including quoted names, backslashes and anonymous users.

This is an intentional **metadata representation change**. Java signatures and
XML property names remain unchanged. Existing stored Schema XML is not rewritten;
comparisons with a newly read catalog can show account identity changes.
MariaDB's existing User name representation is retained.

User filters still accept a simple name to return every matching host. They also
accept a complete quoted account to select exactly one user and host, including
an empty anonymous username. Role-name filters follow the same convention;
role-membership grantee filters require a complete account. Account filters keep
case rather than adopting table-identifier case folding from JDBC metadata.

`INFORMATION_SCHEMA` formats GRANTEE from **raw**, unescaped user/host components.
That string is not parsed as a SQL literal. The reader recognizes its raw
separator and normalizes each component; an ambiguous repeated separator fails
clearly rather than guessing an account. Internally canonical account strings
have escaped components. Generated routine GRANT/REVOKE uses separately escaped
backtick identifiers for both user and host, independent of string-literal SQL
modes. Bare grantees with an implicit host are rejected by the DCL factory.

## Routine grants

`MySqlRoutinePrivilegeReader` reads explicit `mysql.procs_priv` rows in one batch.
It is gated before MySQL 5.0.3. The query selects only Host, User, Db, Routine_name,
Routine_type and Proc_priv. It reads no authentication fields and does not issue
SHOW GRANTS or a SELECT per routine. Read failures, including missing privileges,
propagate to the caller.

EXECUTE and ALTER ROUTINE become separate `RoutinePrivilege` objects. The native
Grant flag becomes `grantable=true` on those privileges; if it is the only flag,
a GRANT OPTION privilege preserves that state. Unknown flags or routine kinds
fail instead of silently losing information. FUNCTION/PROCEDURE kind is retained
in `specifics["ROUTINE_KIND"]` and in `specificName` (`FUNCTION:name` or
`PROCEDURE:name`) so a same-named function and procedure remain distinct in the
catalog collection. The obsolete Grantor/Timestamp columns are not interpreted
as authoritative provenance.

The existing SQL factory registry generates native statements from this model:

```java
var registry = dialect.createSqlFactoryRegistry();
var operations = registry.createSql(routineGrants.get(0), SqlType.GRANT);
// Or SqlType.REVOKE. These calls generate SQL; they do not execute it.
```

For a manually constructed `RoutinePrivilege`, provide schema, routine name,
privilege, a complete grantee account, and ROUTINE_KIND=FUNCTION or PROCEDURE.
There is no safe default for the kind: both kinds can coexist under the same
name. Schema and routine identifiers are quoted and schema-qualified. Unsupported
privileges, missing identities and a privilege state conflicting with the requested
GRANT/REVOKE fail clearly. The shared hierarchy flag has no MySQL syntax and is
not emitted; metadata from this reader sets it false.

## Table, column and global GRANT/REVOKE

MySQL registers native GRANT and REVOKE factories for `ObjectPrivilege`
(a table or view), `ColumnPrivilege` and `UserPrivilege` (global scope). Use the
same registry as routine privileges:

```java
var privilege = new ColumnPrivilege();
privilege.setSchemaName("app");
privilege.setObjectName("orders");
privilege.setColumnName("amount");
privilege.setPrivilege("SELECT");
privilege.setGranteeName("'reader'@'localhost'");
var operations = dialect.createSqlFactoryRegistry().createSql(privilege, SqlType.GRANT);
```

This generates `GRANT SELECT (` followed by the quoted column, schema/table and
separately quoted account components. Table/view targets always require an
explicit schema; global UserPrivilege uses `ON *.*`. An ObjectPrivilege with
objectName `*` is quoted as a literal identifier and never implicitly becomes a
database-wide grant. Each model represents one privilege; generate a collection
through the registry when granting several privileges or columns.

Table/view privileges are validated against MySQL's table privilege set. Column
privileges allow SELECT, INSERT, UPDATE and REFERENCES only. Static global names
use the native global scope, including multiword names. MySQL 8+ additionally
accepts single identifier dynamic privilege names such as BINLOG_ADMIN or
CUSTOM_PLUGIN_ADMIN; the destination server must register and support that name.
Dynamic names and CREATE/DROP ROLE are rejected by older dialect families.
PROXY and ALL shortcuts are intentionally unsupported by these factories, as are
lists or SQL fragments in the privilege name. SQL generation does not infer
effective privileges or expand roles.

Grantable flags emit WITH GRANT OPTION on GRANT, and are not appended to REVOKE.
A conflicting explicit PrivilegeState fails instead of reversing the requested
operation. UserPrivilege.admin is unsupported: use grantable for MySQL's grant
option. The shared ObjectPrivilege hierarchy flag is not emitted because MySQL
does not support the corresponding SQL clause. Missing identifiers, incomplete
accounts and unsupported scope/privilege combinations fail clearly.

The factories are registered only for MySQL. MariaDB's previous selection and
metadata account representation remain unchanged. Existing method signatures,
Schema XML and reader queries are unchanged; the new factories are additive.
They cover the existing MySQL 5.5/5.7 and 8.x/9.x dialect families, with dynamic
privileges restricted to 8+. No new SELECTs, SHOW GRANTS loops, grant-table writes,
authentication clauses or dependencies are introduced.

These APIs generate SQL only. Recipient accounts must already exist on the
destination, and the executing account must have the required authority. Older
MySQL GRANT statements can create missing accounts depending on server settings;
this SQL generator does not perform an existence check or change those settings.
Proxy grants, partial-revoke restrictions, account creation
and exact synchronization of destination grants remain outside this feature.
See the official [GRANT scopes](https://dev.mysql.com/doc/refman/8.4/en/grant.html)
and [REVOKE syntax](https://dev.mysql.com/doc/refman/8.4/en/revoke.html).

## Database-level grants

The ordinary full-catalog reader now also fills the existing Schema model's
`catalog.getSchemaPrivileges()` collection. Generate GRANT/REVOKE for these
`SchemaPrivilege` objects through the same SQL registry; they produce
`ON ` followed by a separately quoted database scope and `.*`. No wildcard table
placeholder or new model class is required:

```java
var grants = catalog.getSchemaPrivileges();
var operations = dialect.createSqlFactoryRegistry().createSql(grants.get(0), SqlType.GRANT);
```

For focused reads, `MySqlCatalogReader.getSchemaPrivilegeReader()` exposes the
same reader, with an optional schemaName filter. The filter matches the stored
database grant scope exactly, not all patterns that might match a concrete
database. Full-catalog loading queries INFORMATION_SCHEMA.SCHEMA_PRIVILEGES once
for the whole requested set, after users and roles are available. Grants retain
catalog, database scope, full account identity and grantability, and link to the
catalog's existing users where available. Grant-option-only rows, which MySQL
reports as USAGE with IS_GRANTABLE=YES, become explicit GRANT OPTION privileges.

Database scopes have an important server-dependent interpretation: with
`partial_revokes=OFF`, unescaped `_` and `%` are pattern characters; with it ON,
they are literal characters. The reader therefore stores the source setting as
`specifics["mysql_partial_revokes"]` (`true` or `false`) on each grant. MySQL
8.0.16+ retrieves the actual setting in the same SELECT. Older queries use the
known OFF behavior without referencing a nonexistent system variable; pre-5.0
servers skip this new query entirely. The raw TABLE_SCHEMA grant scope, including
backslashes, is preserved without expansion, unescaping or case conversion.

Generation **requires the destination's partial_revokes setting to match the
recorded value**. The generator does not connect to the destination, set the
variable, or translate patterns between modes. In particular, executing a grant
recorded with ON against a destination with OFF could broaden a scope containing
`_` or `%`. Verify the setting before applying returned operations. For a manually
constructed grant whose database name contains `_`, `%` or a backslash, explicitly
set this specifics value; otherwise generation fails instead of guessing the
scope. Simple names such as `app` need no extra configuration. A true value is
rejected by pre-8.0 dialects; when using the coarse 8.0 family, the actual target
must support the variable (8.0.16+).

Schema privileges accept MySQL's database-level static privileges, including
routine, event and temporary-table permissions. Global-only or dynamic privileges
are rejected at this scope. The factories and automatic catalog hook are enabled
only for MySQL; MariaDB retains its previous reader/factory selection. Existing
public signatures and Schema XML remain unchanged; the dialect-specific reader
getter and mode key are additive. No dependencies or per-database SELECT loops
are introduced. The new read is subject to normal INFORMATION_SCHEMA visibility;
an underprivileged connection may see only its own grants. Query failures and
invalid catalog flags propagate instead of becoming apparently complete empty
results. Positive schema grants do not include partial-revoke restrictions
recorded in mysql.user.User_attributes, and no such restrictions are synthesized.
See [SCHEMA_PRIVILEGES](https://dev.mysql.com/doc/refman/8.4/en/information-schema-schema-privileges-table.html),
[partial revoke interpretation](https://dev.mysql.com/doc/refman/8.0/en/partial-revokes.html)
and the [server mapping of database grants](https://github.com/mysql/mysql-server/blob/8.0/sql/auth/sql_authorization.cc).

## Roles on MySQL 8 and later

The existing MySqlCatalog800Reader selects version-specific role readers. Older
MySQL versions and MariaDB do not query MySQL's role tables.

- The role list is a set query over role_edges source accounts and default_roles
  referenced accounts. It lists accounts **known to be used as roles**, not every
  locked account. MySQL does not reliably distinguish a never-granted role from
  an ordinary account by account locking or authentication attributes.
- Memberships read role_edges and LEFT JOIN default_roles in one query. FROM_USER/
  FROM_HOST identify the granted role; TO_USER/TO_HOST identify the recipient.
  All four components participate in the join. Admin option and the per-edge
  default flag populate the existing RoleMember properties.
- Direct edges remain a graph, including roles granted to other roles. No
  recursive expansion or effective-privilege inference is performed.
- Defaults referring only to mandatory-role configuration without an explicit
  role edge are not synthesized into direct grants. Mandatory roles, runtime
  activation and activate_all_roles_on_login are outside this representation.

MySQL 8.0+ registers native `Role` CREATE/DROP and `RoleMember` GRANT/REVOKE
factories. These require complete quoted account identities, escape role and
recipient names independently, and preserve WITH ADMIN OPTION on grants. Older
MySQL and MariaDB keep their previous factory selection. Ordinary membership
generation is additive and never changes default roles.

For explicit default restoration, obtain the native GRANT factory and provide
the **complete direct membership set for each included recipient**:

```java
MySqlGrantRoleMemberFactory factory = (MySqlGrantRoleMemberFactory)
    dialect.createSqlFactoryRegistry().getSqlFactory(new RoleMember(), SqlType.GRANT);
List<SqlOperation> operations = factory.createRestoreSql(catalog.getRoleMembers());
```

This generates all grants first, then one `SET DEFAULT ROLE role1, role2 TO account`
per recipient, or `NONE` if none of its memberships is default. Defaults are
replaced, so a filtered membership collection must not be presented as complete.
Duplicate identical edges are collapsed; conflicting admin/default flags fail.
SQL order is deterministic. Omitted recipients remain untouched, including accounts
with no modeled memberships. Existing grants or admin options absent from the
input are not revoked. Recipient accounts must already exist; create required
roles using the registry before applying these operations. These APIs generate
SQL only and provide no transaction guarantee or automatic account creation.
No authentication defaults are guessed. See the official
[SET DEFAULT ROLE semantics](https://dev.mysql.com/doc/refman/8.0/en/set-default-role.html).

Dynamic global privileges already appear in the existing USER_PRIVILEGES path.
That reader preserves arbitrary privilege names, including plugin-defined ones,
so no duplicate global_grants query is added.

## Database access and verification

Full-catalog reading now also requires SELECT access to mysql.procs_priv and, on
MySQL 8+, mysql.role_edges and mysql.default_roles, in addition to the metadata
access required previously. No grants are issued automatically. A schema-only
reader remains available when only schema objects are needed; missing grant-table
access is an execution failure rather than an empty, apparently complete grant list.
Credentials, authentication plugins and password configuration are not modified.

Java 21 and the repository Gradle Wrapper were used for:

```powershell
.\gradlew.bat :sqlapp-core-mysql:test --tests '*MySqlPrivilegeMetadataTest' --tests '*MySqlRoutinePrivilegeSqlTest' --offline --console=plain
.\gradlew.bat :sqlapp-core-mysql:test --tests '*MySqlRoleSqlTest' --offline --console=plain
.\gradlew.bat :sqlapp-core-mysql:test --tests '*MySqlPrivilegeDclTest' --offline --console=plain
.\gradlew.bat :sqlapp-core-mysql:test --tests '*MySqlSchemaPrivilegeTest' --offline --console=plain
.\gradlew.bat :sqlapp-core-mysql:test :sqlapp-core-mariadb:test --offline --console=plain
```

Tests use in-memory JDBC row sets. They cover account round trips and host
collisions, raw GRANTEE formatting, explicit filters and anonymous accounts,
case-sensitive names, native routine DCL, same-named routine kinds, grantability,
role-edge direction/admin/defaults, version routing, one query for twelve routines,
and failure propagation. MariaDB tests verify that its native role reader remains
selected and that no MySQL role catalog or routine reader is enabled there.

The earlier role-SQL module run passed **158 MySQL tests and 38 MariaDB tests**, with zero
failures or skipped tests. `git diff --check` passed for the affected paths.
The subsequent table/column/global DCL follow-up passed all 7 focused
MySqlPrivilegeDclTest cases and then **200 MySQL tests and 41 MariaDB tests** in
the full two-module run, with zero failures or skips. It used the user-selected
Java 25 and repository wrapper with `--offline --console=plain --no-daemon`, a
process-local JAVA_HOME, command-line `org.gradle.java.home`, and a temporary
init script selecting toolchain 25. Repository settings and dependency versions
were unchanged. The tests exercise actual metadata mapping before SQL generation,
escaped identifiers/accounts, grantability, both operations, supported scopes,
state conflicts, explicit targets, dynamic names, and old-version rejection.
MariaDB verifies that the new factories do not replace its existing selection.
The database-grant follow-up passed all 9 focused MySqlSchemaPrivilegeTest cases,
then **209 MySQL tests and 41 MariaDB tests**, with zero failures or skips, using
the same user-selected Java 25 wrapper setup. It covers one query for twelve
database scopes, bound filters, pre-5.0 query suppression, the 8.0.15/8.0.16
system-variable boundary, catalog integration and user links, multiple schemas
for the same grantee, clone preservation, native GRANT/REVOKE, grant-option-only
rows, ambiguous scope interpretation, invalid flags, and failure propagation.
MariaDB also checks that no MySQL schema-privilege reader or factory is selected.
No checked-in generated files were intentionally changed. The command,
Gradle-plugin and opt-in external/database suites were not run.

No real database, production system or Docker database was accessed. Server
execution, cross-version grant-table permissions, partial revokes and complete
account/security reconstruction remain unverified or outside this change.

## Sources

- [MySQL grant tables](https://dev.mysql.com/doc/refman/8.0/en/grant-tables.html)
- [MySQL GRANT syntax and routine scopes](https://dev.mysql.com/doc/refman/8.4/en/grant.html)
- [MySQL roles and accounts](https://dev.mysql.com/doc/refman/8.0/en/roles.html)
- [MySQL server role-table implementation](https://github.com/mysql/mysql-server/blob/8.0/sql/auth/role_tables.cc)
- [MySQL server privilege rows and raw GRANTEE construction](https://github.com/mysql/mysql-server/blob/8.0/sql/auth/sql_authorization.cc)
