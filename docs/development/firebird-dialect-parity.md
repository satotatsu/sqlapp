# Firebird dialect coverage

The comparison target is PostgreSQL's use of the shared Schema model, versioned
catalog readers and database-specific DDL factories. PostgreSQL-only objects
are not Firebird requirements. Full PostgreSQL-level coverage is not yet claimed.

## Version boundaries

| Server | Dialect | Behavior |
|---|---|---|
| Before 2.0 | `Firebird` | Existing legacy behavior; computed index generation rejected |
| 2.0–2.1 | `Firebird20` | Computed indexes and existing sequence/temporary-table support |
| 2.5 | `Firebird25` | Existing 2.5 metadata and settings support |
| 3.x | `Firebird30` | Native BOOLEAN, PSQL functions, package-aware queries and database-local DDL privileges |
| 4.x | `Firebird40` | INT128, DECFLOAT, time-zone types, precision up to 38 and role system privileges |
| 5.x and later | `Firebird50` | Existing returning support and conditional index catalog queries |

`Firebird40` extends `Firebird30` and reuses its compatible catalog readers.
`Firebird50` now extends `Firebird40`. The resolver returns the new 4.0 class
for 4.x servers; the older resolver boundaries remain intact. The Schema XML
format and existing method signatures are unchanged. Consumers comparing
exact dialect classes should allow the new `Firebird40` class.

## Metadata and recreation

* Column nullability combines column and domain NOT NULL flags. Character
  length is distinct from octet length. Column descriptions and inherited
  domain defaults are retained.
* SMALLINT, INTEGER, BIGINT and INT128 numeric subtypes preserve NUMERIC or
  DECIMAL precision and scale. BOOLEAN and the 4.0 native types are restored.
* Index readers retain function-index identity, key order, activation state,
  and the 5.0 predicate. Earlier catalog SQL uses a NULL predicate alias and
  never references the 5.0-only catalog column.
* Index DDL places ASCENDING/DESCENDING before INDEX, renders COMPUTED BY,
  and supports WHERE on 5.0+. Mixed key directions, explicit NULLS ordering,
  and unsupported version combinations fail with actionable errors. Inactive
  indexes generate a following ALTER INDEX ... INACTIVE operation.
* Trigger metadata decodes single and multiple DML events, database events,
  and 64-bit DDL event masks. DDL uses the correct POSITION clause after the
  events. Database/DDL triggers omit FOR. Missing bodies fail unless a complete
  `definition` is supplied; readStatement=false does not load trigger source.
* PSQL procedures use input arguments followed by a separate RETURNS list.
  PSQL functions use their modeled return type. Both retain body text in
  `statement` and accept complete DDL in `definition`. Argument identifiers
  are quoted by the dialect. Missing routine bodies are errors.
* Firebird 3.0+ standalone routine and argument queries exclude package members,
  which are represented by the existing package header/body readers. This
  avoids collisions between same-name standalone and package routines.
* Procedure parameter queries distinguish pre-2.1 catalogs from 2.1+ catalogs
  using the actual server version (the 2.1 dialect still resolves to Firebird20).
  The legacy query reads defaults/nullability/collation from RDB$FIELDS;
  the newer query retains parameter overrides. Unused 2.5-only columns are
  removed. UTF8 parameter lengths use character counts rather than byte counts.
* Function return types retain character set and collation. Since the shared
  FunctionReturning has no nullability property, its optional `firebird.notNull`
  specific preserves an explicit return NOT NULL constraint.
* Firebird 4.0+ table, trigger and package readers preserve explicit SQL SECURITY
  in `firebird.sqlSecurity` specifics (DEFINER/INVOKER). An inherited setting
  remains unset. Older readers do not access the 4.0 catalog property.
* External UDR triggers retain engine/entrypoint metadata from 3.0 onward.
  Their CREATE factory uses EXTERNAL NAME/ENGINE without requiring a PSQL body.
* Package CREATE uses executable header/body syntax. Package bodies use
  CREATE PACKAGE BODY, not the unsupported CREATE OR ALTER PACKAGE BODY.
  Complete definitions are emitted directly; missing bodies fail explicitly.
  Headers without a body are not exposed as empty PackageBody objects, and
  both package readers honor readStatement=false.
  SqlType.CREATE now creates a package instead of implicitly altering an
  existing header. Callers needing explicit CREATE OR ALTER can supply that
  complete DDL in definition.
* Global temporary tables are modeled as TableType.Temporary. Their connection
  or transaction lifetime is retained in `firebird.onCommit` (`PRESERVE ROWS`
  or `DELETE ROWS`). Normal SqlType.CREATE recreates their original name and
  lifetime. Explicit CREATE_TEMPORARY still honors its prefix/suffix options.
  A metadata-specific lifetime takes precedence over TableOptions; newly
  modeled temporary tables retain the existing TableOptions default.
  Unknown values and ON COMMIT settings on ordinary tables are errors.
  The actual server version selects the relation-type catalog projection:
  2.0 and earlier queries never reference the column introduced in 2.1.
  Firebird20 still represents both 2.0 and 2.1, so generating GTT DDL through
  that dialect requires the caller to target a 2.1+ server.
  To support the tested 4.0/5.0 servers, GTT security uses a subsequent ALTER
  TABLE ... ALTER SQL SECURITY operation. Execute the complete operation list.
* Composite primary/unique and foreign keys retain key order and explicit
  backing-index names/direction. Firebird DDL puts DESCENDING in USING INDEX,
  not after each constraint column. System-generated RDB$ index names are
  left for the engine to regenerate. Mixed directions, NULLS ordering, disabled
  constraints and deferred validation are rejected.
* CHECK metadata includes constant expressions without column dependencies,
  retains unnamed user checks, and applies the constraint-name filter to the
  actual constraint name. These remain set-based reads.

Metadata remains set-based: the changes add catalog projections/filters and
reuse the existing bulk argument reads, without per-object SELECTs.

## Roles and privileges

Catalog readers now separate role memberships (`M`), routine EXECUTE privileges
(`X`), table/object privileges, and column UPDATE/REFERENCES privileges.
Role definitions, role memberships and routine privileges are available through
the existing catalog reader entry point. Each reader uses one set-based query;
the membership regression verifies 50 rows with a single execution.

Use the normal registry API for the common case:

```java
ObjectPrivilege privilege = new ObjectPrivilege();
privilege.setObjectName("orders").setPrivilege("SELECT").setGranteeName("report_reader");
var operations = dialect.createSqlFactoryRegistry().createSql(privilege, SqlType.GRANT);
```

The default target is TABLE (including views), and the default recipient is
USER. A modeled `Role` recipient also resolves to ROLE. Catalog metadata retains
the exact target/recipient types in `firebird.objectType` and
`firebird.granteeType` specifics. Advanced callers can set those keys for
PROCEDURE/FUNCTION/PACKAGE recipients, procedure/function/package EXECUTE
targets, or sequence/exception USAGE targets. Routine privileges default to
PROCEDURE; columns require an explicit column name and never fall back to a
table-wide grant. PUBLIC is emitted as the special recipient rather than as a
quoted user name. Identifiers escape embedded double quotes.

GRANT and REVOKE retain the grantor (`GRANTED BY`, Firebird 2.5+) and grant
option. Role grants retain ADMIN OPTION and, on Firebird 4.0+, DEFAULT and
nested role recipients. Role-member grantors use `firebird.grantor` specifics
because the shared RoleMember model has no grantor field. By default REVOKE
removes the whole membership/privilege. Optional `firebird.revokeOption`
specifics select `GRANT` (GRANT OPTION FOR), `ADMIN` (ADMIN OPTION FOR role
membership), or `DEFAULT` (only the role's default property, Firebird 4.0+).
These controls are rejected on GRANT and invalid object/option combinations
fail rather than removing a broader privilege.

Roles support CREATE, DROP and comments. The legacy role query does not access
the description/system-flag columns introduced in 2.0. Role owners are retained
as `firebird.owner`; CREATE ROLE assigns ownership to the executing user, so
owner restoration requires the appropriate execution identity. These factories
only generate SQL and do not change session identity or execute privileges.

On Firebird 4.0+, role specifics `firebird.role.systemPrivileges` retain a
comma-separated list of named system privileges. CREATE ROLE emits SET SYSTEM
PRIVILEGES TO; ALTER replaces the list in place, preserving memberships and
object grants. Removing this specific from a diff target emits DROP SYSTEM
PRIVILEGES. Empty lists, duplicates and unknown names/bits fail explicitly.
PROFILE_ANY_ATTACHMENT is supported from 5.0. All role bitmaps are read in
one query, with no per-role lookup. SYSTEM PRIVILEGE recipients of object,
column and routine grants are converted from numeric catalog codes to named
recipients, and regenerated with the Firebird-specific recipient syntax.

Firebird 3.0+ exposes database-local DDL grants through the existing
`getCatalogReader().getUserPrivilegeReader()` API. The shared UserPrivilege
stores a full privilege such as `CREATE TABLE`, `ALTER ANY PROCEDURE`, or
`DROP DATABASE`. The reader and factories preserve recipient kind, grantor
and grant option. DDL object-class codes are distinct from individual-object
codes and shifted by one in 4.0, so the readers use separate versioned queries.
CREATE DATABASE is a server-security privilege, stored in SEC$DB_CREATORS.
The reader excludes it by default. To include grants visible in the connection's
security database, cast the reader to `Firebird30UserPrivilegeReader` and call
`setReadDatabaseCreators(true)` (also available on the inherited 4.0/5.0 reader).
This adds one bulk query, respects the user-name filter and propagates access
failures. Visibility follows Firebird's security-table access rules; callers
requiring a complete server export must use a suitably privileged connection.
The resulting UserPrivilege retains USER/ROLE identity and supports GRANT/REVOKE
without grant option or a fabricated grantor. Importing these grants changes
server security, so enabling extraction is an explicit advanced choice.
Role recipients must exist in the security database, even if a role of the
same name already exists in the database being exported. Docker verification
uses its pre-existing `RDB$ADMIN` security role and a distinct USER grant with
the same identifier to verify that recipient kinds survive reconstruction.

XML and configuration formats and existing public method signatures are
unchanged. New readers/factories and the FirebirdPrivilege constants are
additive. Catalog results intentionally classify membership and EXECUTE rows
into their dedicated collections instead of returning them as ObjectPrivilege.

## Verification and remaining work

Run with Java 21 and the repository wrapper:

```powershell
.\gradlew.bat :sqlapp-core-firebird:test --offline --console=plain
```

Run unit tests together with all disposable Firebird Docker tests:

```powershell
.\gradlew.bat :sqlapp-core-dialect-test:firebirdCompatibilityTest --offline --no-parallel --console=plain
```

This opt-in task runs the 2.5/3.0/4.0/5.0 schema round-trip matrix and the
existing Firebird metadata, bulk/migration and JDBC-tree tests. Containers are
automatically removed. It never uses an external database or saved credentials.
Images must already be available locally or downloadable by Docker. The 2.5
image is `jacobalberty/firebird:v2.5.9-ss`; the other matrix images use `v3.0`,
`v4.0` and `v5.0`. The test module's Jaybird 6 driver explicitly enables protocol
12 for its 2.5 connection; production connection settings are unchanged. The
2.5 entrypoint starts and stops a temporary server while initializing its
database, so the test waits for the final-command initialization marker as well
as the listening port before opening its connection.

The regression suite covers version resolution, column metadata, native type
DDL, numeric subtypes, multiline defaults, trigger event masks, trigger DDL,
index DDL and PSQL routine DDL. The unit checks are complemented by the opt-in
Docker matrix, which reads metadata, drops and recreates objects with generated
SQL, rereads the result and executes representative data/routine operations.
Verified on 2026-10-24 with Java 21: 71 unit tests and 13 Docker tests passed,
with no failures or skipped tests. The Docker cases comprise four version
round trips, two existing metadata/sequence tests, six bulk/migration tests
and one JDBC-tree generated-key test. Earlier startup/protocol and SQL failures
were corrected before this successful final run. No external or production
database was accessed.

The native round trips verify column/domain nullability, inherited defaults,
character length, fixed-point precision, identity and modern type DDL,
descending/computed/inactive indexes and 5.0 partial indexes, DML/database/DDL
triggers, procedure/function bodies and input defaults, exclusion of package
members, table/column/routine grants, and admin/default/nested role grants.
Version-inapplicable features are omitted from that version's fixture.
The expanded matrix also recreates multidimensional arrays (including negative
bounds), computed columns, explicit character sets, identity start/increment
options, legacy UDFs (2.5/3.0), and UDR functions/procedures (3.0+). It executes
the recreated routines and columns, and verifies in-place routine ALTER preserves
grants. Routine SQL SECURITY is read only by 4.0+ readers; function determinism
is retained from 3.0 onward. DOUBLE PRECISION is emitted in full, including in
legacy DECLARE EXTERNAL FUNCTION statements.

Additional native checks cover 38 database-local DDL privileges on each of
3.0/4.0/5.0, role system-privilege bitmaps and in-place replacement/removal,
SYSTEM PRIVILEGE recipients, and option-only GRANT/ADMIN/DEFAULT revocations.
The privilege/role regressions each verify 50 objects with one catalog query.
UTF8 procedure/function parameters and return values retain character length,
collation and NOT NULL after recreation. Table and trigger SQL SECURITY and
inactive external-trigger metadata are recreated on applicable versions.
The inactive external-trigger fixture verifies metadata and DDL; its replication
entrypoint is not executed. Package header/body recreation, execution and
readStatement behavior are also checked by the matrix.
Temporary table checks verify both ON COMMIT lifetimes after recreation and
isolation across two connections. Signature ALTER checks change input/output
types, add a defaulted input, execute the altered procedure/function and verify
the original EXECUTE grant survives. Constraint checks recreate composite PK/FK
and unique constraints with named descending indexes and constant CHECKs, then
verify CASCADE updates, SET NULL deletes and CHECK enforcement.

Array bounds use the optional column-specific value `firebird.array.bounds`,
for example `-2:-1,3:5`; the first dimension is also retained in the shared model.
Bounds are retrieved in one bulk query for all selected arrays, with a regression
checking that 50 array columns require only two queries in total. Unknown or
incomplete bounds fail explicitly. Identity generation rejects unsupported
version/options and zero increments. Firebird 3.0 starts its first generated value
at START WITH + 1; Firebird 4.0+ starts at START WITH.

External engines use `firebird.external.engine` and `firebird.external.entrypoint`
in routine specifics. Legacy UDF readers preserve a complete executable
`definition`, with module, entrypoint and parameter passing metadata retained as
supplemental specifics. Scalar-array input arguments retain mechanism 4 and
recreate as `BY SCALAR_ARRAY`; array returns and input `FREE_IT` are rejected.
Docker coverage on 2.5/3.0 verifies declaration/read/recreate/reread only for
scalar arrays: the fixture does not supply or invoke an external array library.
UDF mechanism codes outside the implemented set fail
explicitly. Routine diff ALTER supports body, SQL SECURITY, determinism,
parameter signatures, return types, external engine/entrypoint and routine
comments. Procedure parameter comments (2.0+) and PSQL/UDR function parameter
comments (3.0+) are read in the existing bulk argument query and emitted as
separate COMMENT ON statements after CREATE. Comment-only diffs emit only
COMMENT ON, including IS NULL for removal; body/signature ALTER reapplies
modeled comments after the routine statement. Names and comment text use the
normal identifier/literal quoting. Legacy UDF arguments have no named parameter
comment support. Docker tests cover quoted routine/parameter identifiers,
Japanese text, apostrophes, multiline text, input/output procedure parameters,
comment removal, signature changes and retention of EXECUTE grants on 2.5/3/4/5
(function parameter checks start at 3.0).
Argument changes are limited to rendered signature properties
and remarks;
unsupported metadata changes fail explicitly instead of being ignored.
Function arguments must be input parameters. Routine renames are rejected.
A complete definition must be
replaced with a modeled statement before changing these properties through diff
ALTER, so an old definition cannot silently shadow the requested change.
The metadata-to-DDL regression also checks a single catalog execution for the
computed/partial index path and the trigger readStatement option. Additional
privilege regressions cover metadata-to-DCL recreation, target and recipient
types, quoting, grantor/grant/admin/default options, version boundaries and
rejection of malformed or unsupported privilege combinations.

Docker verification exposed and fixed two issues beyond the unit checks:
`CHARACTER_LENGTH` is a reserved SQL token and cannot be used as the unquoted
column-length alias, and a 2.5 procedure parameter can inherit its default from
its field/domain row. Column queries now use `CHAR_LEN`; procedure arguments
use COALESCE of the parameter and field default source in the same bulk query.

Remaining parity work includes real-server read/recreate/reread coverage across
the pre-2.5 releases and additional object options, execution of scalar-array UDFs,
automatic ownership restoration,
other version-specific object properties.
Complete `definition` can preserve
DDL supplied by callers, but automatic extraction of these remaining details
is not claimed. The procedure-argument boundary was checked against the official
1.5/2.0/2.1/2.5 release catalog definitions and unit-tested; pre-2.5 execution on
a real server remains unverified.

References: [Firebird index grammar](https://www.firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref50/fblangref50-ddl-index.html),
[trigger event masks](https://www.firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref50/fblangref-appx04-triggers.html),
[field types](https://www.firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref50/fblangref-appx04-fields.html).
Privilege references: [catalog codes](https://www.firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref50/fblangref-appx04-userprivs.html),
[REVOKE grammar](https://www.firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref50/fblangref50-security-revoking.html),
[2.0 role catalog changes](https://www.firebirdsql.org/file/documentation/release_notes/html/rlsnotes20.html),
[default-role metadata](https://github.com/FirebirdSQL/firebird/blob/master/src/isql/show.epp).
System privilege references: [role bitmap codes](https://www.firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref50/fblangref-appx04-roles.html),
[CREATE/ALTER ROLE grammar](https://www.firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref50/fblangref50-security-role.html),
[DDL object-class codes](https://github.com/FirebirdSQL/firebird/blob/v5.0.0/src/jrd/obj.h).
Legacy parameter catalog references:
[2.0 definitions](https://github.com/FirebirdSQL/firebird/blob/R2_0_7/src/jrd/relations.h),
[2.1 definitions](https://github.com/FirebirdSQL/firebird/blob/R2_1_7/src/jrd/relations.h).
Table/constraint references: [relation types](https://www.firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref50/fblangref-appx04-relations.html),
[table and USING INDEX grammar](https://www.firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref50/fblangref50-ddl-table.html),
[routine signature ALTER](https://www.firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref50/fblangref50-ddl-function.html).
Server-security references: [security tables](https://www.firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref30/fblangref30-appx06-sectables.html),
[CREATE DATABASE grants](https://www.firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref50/fblangref50-security-granting.html).
Parameter comment references: [2.0+ comments](https://www.firebirdsql.org/refdocs/langrefupd25-ddl-comment.html),
[3.0 routine parameter grammar](https://www.firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref30/fblangref30-ddl-comment.html).
