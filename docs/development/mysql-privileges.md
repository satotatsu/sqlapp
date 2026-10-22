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

Role memberships and default flags are metadata support in this change. Native
role/account creation, membership DCL and automatic SET DEFAULT ROLE restoration
are not added. Restoring defaults requires the complete default-role set for each
account; emitting one SET DEFAULT ROLE per edge could overwrite other defaults.

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
.\gradlew.bat :sqlapp-core-mysql:test :sqlapp-core-mariadb:test --offline --console=plain
```

Tests use in-memory JDBC row sets. They cover account round trips and host
collisions, raw GRANTEE formatting, explicit filters and anonymous accounts,
case-sensitive names, native routine DCL, same-named routine kinds, grantability,
role-edge direction/admin/defaults, version routing, one query for twelve routines,
and failure propagation. MariaDB tests verify that its native role reader remains
selected and that no MySQL role catalog or routine reader is enabled there.

The final module run passed **152 MySQL tests and 38 MariaDB tests**, with zero
failures or skipped tests. `git diff --check` passed for the affected paths.
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
