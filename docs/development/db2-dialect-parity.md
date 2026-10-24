# Db2 LUW metadata and DDL recreation

This work strengthens the ordinary Schema-model path in `sqlapp-core-db2`.
It targets Db2 LUW, not Db2 for z/OS or Db2 for i. Existing version resolution
from the legacy dialect through 9.1/9.5/9.7/9.8, 10.1/10.5, 11.1/11.5/11.5.5 and
12.1/12.1.2/12.1.5 is retained. It does not claim complete PostgreSQL feature
parity or live-database certification.

## Common path

```java
var dialect = DialectResolver.getInstance().getDialect(connection);
var reader = dialect.getCatalogReader();
var catalog = reader.getAllFull(connection).getFirst();
String xml = catalog.asXml();

var registry = dialect.createSqlFactoryRegistry();
registry.getOptions().setDecorateSchemaName(true);
registry.getOptions().setCreateIfNotExists(false);
SqlFactory<Catalog> factory = registry.getSqlFactory(catalog, SqlType.CREATE);
var operations = factory.createSql(catalog);
```

Generation returns operations; it does not execute them. Review prerequisites,
object dependencies and target privileges before execution. Complete stored
definitions retain their original qualified identifiers; changing a containing
Schema name does not rewrite arbitrary stored SQL.

## Changes and compatibility

| Area | Behavior |
| --- | --- |
| Views | Full definition remains authoritative; columns now use the same version-specific reader as ordinary tables. |
| SQL procedures | Complete `definition` takes precedence. A `statement` is a complete SQL routine body, emitted with `LANGUAGE SQL`, parameter modes before quoted parameter names, and SPECIFIC identity. |
| PL/SQL procedures | Select `language="PLSQL"` for the existing compatibility-mode body/wrapper path. The previous implicit PL/SQL generation is replaced by native SQL as the common default. |
| SQL functions | Empty signatures, scalar/row/table returns, SQL data access, null-call behavior and determinism are generated from the shared model. Complete definitions remain authoritative for external and aggregate routines. |
| Function metadata | Scalar return types are joined from `SYSCAT.ROUTINEPARMS` in the existing query. Return columns and aggregation-state rows are excluded from arguments. Return-column parsing handles quoted names, escaped quotes, commas and nested type parentheses. |
| Routine identity | Metadata preserves literal SPECIFIC names, including dots. DROP uses SPECIFIC FUNCTION/PROCEDURE to distinguish overloads. |
| Indexes | Key ordering and INCLUDE columns are generated separately; INCLUDE requires UNIQUE. Clustered indexes use CLUSTER. Unsupported modeled index kinds fail instead of silently becoming ordinary indexes. |
| Index ownership | Metadata selection uses the table schema; `specifics.INDEX_SCHEMA` retains an independently qualified index schema while allowing attachment to its table. |
| Tables | Indexes are separate CREATE INDEX operations. Table/column comments are separate COMMENT ON operations. Table, index and LOB tablespaces use IN, INDEX IN and LONG IN. No MySQL-style table COMMENT/AUTO_INCREMENT options are emitted. |
| Temporary tables | Follow-up indexes target the generated name in SESSION, including configured prefixes/suffixes. |
| Aliases | Shared Synonym objects retain `specifics.ALIAS_TYPE` (TABLE, SEQUENCE or MODULE). CREATE and DROP ALIAS use the recorded target kind. Missing kind defaults to TABLE. Remote-link targets and invalid kinds fail clearly. |
| Alias version boundary | Before 9.7, the query reads table aliases only. The 9.7+ query includes module and sequence aliases. Every branch filters on synonymName. |
| Privileges | Table/view, column and specific function/procedure grants are read into existing Catalog collections. Each collection uses one bulk query, independent of object count. System-generated SYSIBM grants are excluded. |
| Grant identity | Grantor, grantee, grant option and `specifics.GRANTEE_TYPE` (U/G/R) are retained. Generation explicitly selects USER/GROUP/ROLE or PUBLIC. It requires a type for non-PUBLIC hand-authored grantees rather than guessing. |
| Roles | CREATE ROLE and typed role membership GRANT/REVOKE are registered. Membership retains ADMIN option. The role-membership SQL resource lookup is corrected. |
| Catalog CREATE | Existing factories are composed with the registered grant factories; routine privileges are included after object creation. XML and cloning preserve the existing model and specifics payloads. |
| MQTs | `Schema.mviews` retains the full native CREATE TABLE definition from SYSCAT.VIEWS, versioned columns, indexes, comments and creation context. Ordinary views exclude MQTs. DROP emits DROP TABLE; automatic MQT replacement is rejected. |
| Constraint metadata | Constraint-to-index dependencies use SYSCAT.CONSTDEP rather than assuming matching names. Informational constraints with no index remain readable. Same-named PK/unique/CHECK/FK constraints on different tables remain separate. |
| Constraint DDL | Logical key lists omit physical ASC/DESC attributes. Enforcing backing indexes are not created a second time. NOT ENFORCED unique/primary constraints require 10.5+. Backing-index names/options remain supplemental; the recreated constraint can receive a server-chosen backing index. |
| Row permissions | Db2 10.1+ captures explicit permissions, correlation names, conditions, enabled/valid state, comments, qualifier and function path in `db2.rowPermissions.v1` on the owning Schema. Implicit default permissions are not recreated explicitly. |
| Access-control activation | Row and column activation states are recorded, including inactive states. All Catalog permissions are created before any activation, including cross-schema targets. |
| Row-permission ALTER | Registered Schema/Catalog ALTER factories compose permission drops/recreation with native object changes and explicit activation transitions. Removing an active state for a retained table requires explicit target states; omission never implies DEACTIVATE. |
| Column masks | Schema CREATE/ALTER includes shared Mask objects. Native expressions retain correlation and creation context, full definitions take precedence, and generation does not append a second CASE END. |
| Schema privileges | Catalog full loading captures ALTERIN, CREATEIN and DROPIN in one bulk query into `Catalog.schemaPrivileges`. Db2 11.5.5+ also captures SELECTIN, INSERTIN, UPDATEIN, DELETEIN, EXECUTEIN and SCHEMAADM/ACCESSCTRL/DATAACCESS/LOAD authorities. Legacy queries do not reference these newer columns. |
| Schema privilege DDL | Catalog CREATE and ALTER use native typed GRANT/REVOKE ON SCHEMA. Authorities reject PUBLIC and GRANT OPTION; newer privileges require the 11.5.5+ dialect. Grant-option reductions and principal-type changes require an explicit revoke/regrant plan instead of silently broadening revocation or leaving the old principal authorized. |
| Sequence/index/server privileges | One bulk query captures sequence ALTER/USAGE, index CONTROL and server PASSTHRU into `db2.objectPrivileges.v1` on Catalog. Object kind, identity, principal type, original grantors and grantability survive XML and cloning. Server identity has no schema. Same-named tables, sequences, indexes and servers remain distinct. |
| Additional object privileges (11.1+) | The same one-query extension also reads table space USE, package CONTROL/BIND/EXECUTE, module EXECUTE, global variable READ/WRITE, workload USAGE and XSR USAGE. Direct and Catalog GRANT/REVOKE/ALTER retain each native object scope. |
| Global variables (11.1+) | Schema variables are read in one query into `Schema.constants`. DEFAULT/CONSTANT expressions, type components, string units, binary character data, comments and creation context support native CREATE VARIABLE; DROP uses RESTRICT and modified variables require an explicit plan. |
| Expression/random indexes (11.1+) | Index keys retain raw expressions in ReferenceColumn.statement and RANDOM ordering in specifics. INCLUDE expressions, function path, comments, compression, free-space/reverse-scan/null-key options are recreated; unsupported specialized types require complete native definitions. |
| Function mappings (11.1+) | Two bulk queries read ordered user-defined federation mappings and their function/parameter options into `db2.functionMappings.v1`. Specific-function mappings support native generation after notation is resolved; complete definitions cover built-in signatures and parameter options. |
| Type mappings (11.1+) | One bulk query retains forward/reverse mappings and native type conditions in `db2.typeMappings.v1`. Numeric/date/time mappings, forward remote decimal ranges and comments support native generation before nicknames; ambiguous conditions require full definitions. |
| Range partitions (9.1+) | Metadata preserves low/high inclusivity, quoted keys, SQL expressions and NULL ordering with two bulk queries. CREATE TABLE emits explicit finite EXCLUSIVE bounds where modeled and retains per-partition data/index/LOB placement. |
| Distinct/array types (11.1+) | `Schema.domains` retains native type kind, strong/weak rules, checks, nullability, source and array metadata. Native CREATE/DROP TYPE and complete-definition replay replace generic domain syntax; index-type length is no longer mistaken for associative-array cardinality. |
| Extended privilege DDL | Catalog CREATE grants after native object creation. Catalog ALTER revokes removals before native changes and grants additions/upgrades afterward. Sequence REVOKE includes RESTRICT; index CONTROL rejects GRANT OPTION. Grant-option reductions require an explicit revoke/regrant plan. |
| Database authorities | One query reads supported SYSCAT.DBAUTH columns into `Catalog.userPrivileges`. Separate legacy/9.1/9.7/10.1 query resources retain old catalog compatibility. Database-level authorities include DBADM, SECADM, ACCESSCTRL, DATAACCESS, SQLADM, WLMADM, EXPLAIN and CREATE_SECURE_OBJECT at their supported version boundaries. |
| Database authority DDL | Native GRANT/REVOKE ON DATABASE preserves principal types. DBADM on 9.7+ explicitly uses WITHOUT DATAACCESS WITHOUT ACCESSCTRL; separately captured authorities are replayed separately. PUBLIC restrictions, unsupported ADMIN/GRANT OPTION and cross-9.7 DBADM representation changes fail clearly. |

No public method signatures, dependency versions or module dependencies were
changed. The shared Mask model now compares and serializes its existing target
schema/table/column and enabled properties, so XML and diff operations do not
lose mask semantics. The new attributes are optional existing Schema properties;
older XML remains readable and enabled still defaults to true. Reference setters
retain the other identity components and detach references before retargeting,
rather than renaming a column in its owning table.

The shared `UserPrivilegeCollection.addAll` methods now preserve distinct grant
records as individual additions do. Previously, bulk addition during Catalog
cloning discarded grants with a different grantor or supplemental principal
identity when privilege/grantee names matched. The fix has no database-specific
condition and retains the existing XML model and parent ownership. Callers that
relied on bulk addition discarding grant records will now receive all supplied
records; Db2 SQL generation deduplicates effective grants explicitly.

The additive `Db2_1155` dialect inherits 11.5 behavior; 12.1 now inherits it.
Resolver inputs 11.5.0 through 11.5.4 (and unknown revision) still select 11.5,
while 11.5.5+ select 11.5.5. Existing dialect types remain compatible through
inheritance. `Db2CatalogReader.getSchemaPrivilegeReader()` offers optional direct
access; normal full-catalog loading needs no additional configuration.
Schema privileges reuse the existing XML model, including grantor and grantee
type specifics. No new XML format or module dependency is required.
The additive `Db2_910` dialect similarly distinguishes 9.1 through 9.4 from
the older base dialect; 9.5 inherits it. Existing 9.5+ resolution remains intact.

Explicit schema REVOKE follows Db2's all-grantors semantics. Grantor names are
retained for inspection, not impersonated during replay. Catalog ALTER supports
adding/removing grants and increasing GRANT OPTION. A reduction requires a
separately reviewed REVOKE/regrant plan because revoking the privilege can affect
dependent objects and grants from other grantors. Authorities granted to roles
must also satisfy Db2's indirect-PUBLIC restrictions at execution time.

Generated SQL and operation counts intentionally change where invalid
or incomplete DDL was corrected. Callers must execute the complete operation
list, including indexes and comments, instead of taking only its first entry.

Privileges can be granted by column in Db2, but cannot be revoked individually
by column. Column REVOKE fails with an actionable error; it never broadens the
request into a table-wide revocation. Explicitly revoke at table scope and
regrant retained columns when that is the intended operation. Routine REVOKE
uses RESTRICT. Original grantor chains are metadata, not impersonation commands;
replay runs with the executing user's authority.

## Database authorities

Full Catalog loading includes `getUserPrivilegeReader()` automatically. Database
authorities use existing `UserPrivilege` objects for users, groups and roles,
with `specifics.GRANTEE_TYPE` preserving the principal kind. This is a
database-local authority snapshot, not a list of OS users or instance-level
SYSADM/SYSCTRL/SYSMAINT grants.

The base query reads BINDADD, CONNECT, CREATETAB, DBADM,
CREATE_NOT_FENCED_ROUTINE, IMPLICIT_SCHEMA and LOAD. The 9.1+ query additionally
reads CREATE_EXTERNAL_ROUTINE, QUIESCE_CONNECT and SECADM. The 9.7+ query adds
ACCESSCTRL, DATAACCESS, SQLADM, WLMADM and EXPLAIN; 10.1+ adds
CREATE_SECURE_OBJECT. Reserved LIBRARYADMAUTH is not treated as an executable
authority. The older base profile intentionally does not read the later
profile's catalog columns.

Catalog CREATE emits effective database grants after roles have been created.
Catalog ALTER compares principal type, name and authority, preserving multiple
grantors for inspection without replaying duplicate native grants. Same-named
USER and GROUP principals remain distinct. Grantor-only changes do not
impersonate a grantor. Removal uses Db2's all-grantors REVOKE semantics. DBADM is
revoked before its dependent authorities, and CREATE_NOT_FENCED_ROUTINE before
CREATE_EXTERNAL_ROUTINE. Removing an authority implied by a retained DBADM (or
external-routine authority implied by retained non-fenced authority) is rejected
where the dependency is known. Broader role/group inheritance and current
authorization are checked by the server, including the requirement to retain a
USER holding SECADM. SECADM before 9.7 is supported only for USER principals.

Database authorities have no ADMIN/GRANT OPTION in this implementation; these
flags are rejected rather than silently ignored. PUBLIC cannot receive DBADM,
SECADM, ACCESSCTRL, DATAACCESS or CREATE_SECURE_OBJECT. Role grants require 9.5+;
indirect-PUBLIC restrictions remain execution-time checks.

DBADM defaults changed across Db2 releases. On 9.7 through 11.5, a bare DBADM
grant also grants DATAACCESS and ACCESSCTRL by default; on 12.1 it does not.
For 9.7+ targets the generated SQL always specifies WITHOUT for both, then
replays separately captured DATAACCESS/ACCESSCTRL grants. This does not revoke
additional authorities already held on a destination database.

Metadata records `specifics.DBADM_MODEL` as LEGACY (before 9.7) or SEPARATE
(9.7+). Granting a captured DBADM across that boundary requires an explicit
decision: select the intended target DBADM_MODEL and model DATAACCESS and
ACCESSCTRL as separate grants where required. Changing this source-model marker
inside an ALTER plan is rejected until the target catalog is reread and the
authority changes are explicit. A hand-authored DBADM without this marker uses
the target version's native DBADM semantics, with the explicit minimal scope on
9.7+. Special-build/registry variations in routine authority inheritance are
not inferred.

## Sequence and index privileges

The ordinary full-Catalog reader and Catalog CREATE/ALTER include these grants
automatically for the existing Db2 LUW dialect family. The query uses the
long-standing SYSCAT.SEQUENCEAUTH and SYSCAT.INDEXAUTH columns; no new-version
catalog columns or external connection are required by SQL generation.

```java
var snapshot = Db2ObjectPrivileges.get(catalog);
// Optional: replace these grants explicitly before generating Catalog SQL.
Db2ObjectPrivileges.set(catalog, snapshot.grants());
```

The existing shared ObjectPrivilege model has no object-kind identity. Mixing
same-named table/sequence/index grants there would collide during comparison.
The typed Catalog extension follows the vendor-object approach used by
PostgreSQL and keeps ordinary table grants in `Catalog.objectPrivileges`.
Its public `Grant` record validates object kind, action, principal type, names
and explicit grantability; malformed/unknown payload fields fail clearly.
No shared model, dependency or existing XML format changes are needed.

Optional direct access is available from
`Db2CatalogReader.getExtendedObjectPrivilegeReader()`. Its `getAllFull` results
carry `specifics.OBJECT_TYPE` and `GRANTEE_TYPE`; convert them with
`Db2ObjectPrivileges.fromModel`, or use `loadFull(connection, catalog)` to populate
the extension. Do not merge them into the ordinary table-grant collection.
Direct GRANT/REVOKE factories also recognize these tagged results.

Multiple grantors for the same effective privilege remain available in the
snapshot. Replay emits one effective grant, retaining GRANT OPTION when any
source grant had it, under the executing user's authority. Diff compares those
effective privileges; grantor-only changes do not impersonate another grantor or
issue a destructive revoke. Removal revokes the entire typed privilege using
Db2's all-grantors behavior. These operations do not remove authority inherited
through PUBLIC, roles, groups or administrative authority.

Constraint-backed indexes can receive a new server-generated name during table
recreation. When Catalog CREATE includes such a table and a captured grant on
its backing index, generation fails with an explicit physical-index planning
error. A grants-only Catalog can replay grants after the caller has resolved
the actual target identities. Arbitrary table/index rebuilds still require
dependency and grant-reapplication planning; this extension does not infer it.

## Db2 row and column access control

The normal Catalog/Schema reader and CREATE/ALTER factories include these
objects automatically for Db2 LUW 10.1 and later. Advanced callers can inspect
or author typed snapshots without introducing a separate SQL execution API:

```java
var security = Db2RowPermissions.get(schema);
var permissions = security.permissions();
var tables = security.tables();
var ddl = Db2RowPermissions.createSql(schema);
var changes = Db2RowPermissions.diff(originalSchema, targetSchema);
```

`Db2RowPermissions.set(schema, permissions, tables)` validates scope, identities,
required flags and duplicate IDs. XML, cloning and Schema comparison preserve
the versioned payload. Invalid captured permissions cannot be recreated; the
metadata remains inspectable. A schema rename does not silently reassign stored
security objects. Two bulk queries per selected schema capture permissions and
table activation, independent of the number of tables or permissions.
Activation states and same-schema permissions follow the selected table set;
cross-schema permission targets retain their explicit qualified identity.

Creation can require the recorded `CURRENT SCHEMA` and function `PATH` because
catalog expression text contains unqualified references. Generated operation
lists include explicit SET statements; execute the whole list on a dedicated
connection. These session settings are not restored automatically. Paths are
parsed as identifier lists and quoted, not inserted as arbitrary SQL. Permission
conditions and complete native definitions remain trusted source SQL. Column
masks and MQTs use the same creation-context handling.

Schema/Catalog ALTER drops removed or replaced permissions before native object
changes, then recreates changed permissions and applies explicit activation
changes. Replacing a permission on an active table can temporarily deny access;
the planner does not assert transaction/rollback guarantees or bypass RCAC.
Execution requires appropriate SECADM privileges. It does not impersonate the
source owner, migrate table data, refresh an MQT, or automatically drop dependent
objects. For MQT changes, explicitly coordinate a reviewed DROP/CREATE plan.

## Additional object privileges (Db2 LUW 11.1+)

The existing `Db2ObjectPrivileges` API now has six more Kind values. No snapshot
fields or existing enum values change. Readers before 11.1 use their original
query; the 11.1+ query includes all nine scopes with UNIONs/joins in one SELECT.
Direct tagged GRANT/REVOKE and Catalog CREATE/ALTER apply the same validation.
Generation of the six new scopes on older dialects fails explicitly. This is a
conservative coverage boundary, not a claim about feature introduction dates.
Existing payloads remain readable. Consumers must understand the new enum values
before reading snapshots containing these scopes; older binaries cannot parse
those new values.

| Kind | Actions | Identity | GRANT OPTION |
| --- | --- | --- | --- |
| TABLESPACE | USE | Unqualified; schema must be null | Yes; native syntax is USE OF TABLESPACE |
| PACKAGE | CONTROL, BIND, EXECUTE | Schema + name | BIND/EXECUTE only |
| MODULE | EXECUTE | Schema + name | Yes |
| VARIABLE | READ, WRITE | Schema + name | Yes |
| WORKLOAD | USAGE | Unqualified; schema must be null | No |
| XSROBJECT | USAGE | Schema + name, resolved by OBJECTID join | No |

Same-named objects across scopes remain distinct through XML/cloning. Multiple
grantors remain inspection metadata while replay/diff use effective privileges.
Grant-option reductions still need an explicit revoke/regrant plan. Variable
revocations include RESTRICT to prevent invalidating dependent objects; other
new scopes use their native REVOKE syntax without adding an unsupported clause.
Schema filters exclude unqualified table spaces/workloads, as they do servers.
System SYSIBM grants are excluded consistently with the existing reader.

Module members are not recreated by module EXECUTE grants. Package bind/version
artifacts, workload definitions and XSR registrations are also outside this
change: these objects must exist before their grants are applied. Module
variables are excluded from VARIABLE privileges via their catalog identity and
remain governed by module-level access. Existing table/column/routine grants
continue to use the shared Catalog privilege collections.

## Global variables (Db2 LUW 11.1+)

The registered ConstantReader reads user-owned, non-module variables from
SYSCAT.VARIABLES with one set query. Database-maintained variables and SYSIBM
objects are excluded. `Schema.constants` is the existing shared named-object
collection: a Db2 entry carries `specifics.DB2_OBJECT_TYPE=VARIABLE`, its type,
scope and read-only flags in specifics, and its raw default expression in
`statement`. This also represents mutable session variables; it does not mean
every entry is created with the SQL CONSTANT clause.

The CREATE factory uses the shared Db2 type generator for supported built-in
types and emits DEFAULT or CONSTANT according to READONLY. It preserves
character string-unit length, FOR BIT DATA character types, decimal precision/
scale, comments and qualifier/function path. Missing required dimensions or
invalid flags fail explicitly. Ambiguous Unicode/graphic lengths without string
units also require a full definition. `definition` can instead contain complete native
CREATE statements, which take precedence and do not require duplicate type or
expression components. User/module types, cursors, VECTOR types and unsupported
scopes currently require this complete-definition path. This reader never
evaluates the expression or reads a variable's current session value.

Schema CREATE places variables after types and routines but before tables/views;
Catalog variable grants follow object creation. SQL text is not parsed to infer
arbitrary dependencies: mutually dependent routines/variables, cross-schema
references and defaults invoking objects created later need an explicit ordered
plan. Collation/code-page compatibility of the target database must be checked;
this change does not alter database collation settings.

DROP emits DROP VARIABLE ... RESTRICT. Modified variable definitions are rejected
by automatic ALTER; the caller must coordinate recreation and current session
values. Earlier readers retain their previous behavior and do not discover
global variables. Native variable generation is deliberately gated at 11.1.

## Expression and physical index metadata (Db2 LUW 11.1+)

The 11.1+ reader projects INDEXCOLUSE.TEXT/VIRTUAL in its existing set query.
`ReferenceColumn.statement` stores one expression without trimming its SQL text;
the local catalog column name remains supplemental. `specifics.DB2_COLORDER=R`
records RANDOM, which the shared Asc/Desc enum cannot represent. Clear/change
this marker when intentionally changing a random key to an ordered key.
INCLUDE expressions are emitted separately from unique key expressions.
Function paths are emitted before expression-index creation. As with other
native expression bodies, callers must provide the intended schema context for
unqualified references not resolved by the captured function path.

PCTFREE/MINPCTUSED, reverse scans, compression, excluded null keys and index
comments now participate in CREATE. Invalid numeric ranges and catalog flags
fail explicitly. Prior readers retain their original query without TEXT/VIRTUAL.
Statistical-view keys and specialized XML/text/other native index types require
an authoritative `Index.definition`; they no longer silently become ordinary
B-tree indexes. Full definitions take precedence over supplemental keys/options.
The existing vector-specific factory remains in use on its supported version.
Partition/storage extensions and complete specialized-index metadata are still
separate work; supplying a complete definition does not discover that DDL.

## Federation wrappers and servers

Db2 LUW full Catalog loading now reads wrappers, servers and category defaults
with three bulk catalog queries, including objects without options. The reader
uses baseline catalog columns without a new version boundary; historical-version
execution remains unverified. `Db2CatalogReader.federation().read(connection)`
returns the same typed snapshot directly.

`Db2Federation.get(catalog)` exposes immutable `Wrapper`, `Server` and `Defaults`
records. `Db2Federation.set(catalog, wrappers, servers, defaults)` stores their
versioned snapshot in `db2.federation.v1`, preserved through Schema XML and cloning.
Catalog CREATE emits wrappers, category options and servers before schemas.
Catalog ALTER supports option ADD/SET/DROP, server version changes and comments.
The derived server options DATE_COMPAT, NUMBER_COMPAT, SAME_DECFLT_ROUNDING and
VARCHAR2_COMPAT are retained separately and never emitted as configurable options.

Known predefined wrappers use Db2's default library; custom wrappers retain the
catalog library, whose availability must be checked on the target platform.
Optional complete CREATE definitions take precedence over modeled components.
Changing embedded options/version alongside an authoritative definition requires
an explicit native plan. Definitions are trusted executable input and are not
rewritten to replace identifiers or credentials.

Db2-family server creation requires an explicit complete definition containing
the intended authorization strategy, except for explicit SSO_AUTH='Y' on 12.1+.
Earlier SSO configurations can use a complete definition. Catalog metadata does
not recover passwords. User mappings are read separately on 11.1+. CREATE SERVER can contact the
remote source; no federation SQL was executed during this work.

Automatic wrapper/server removal or replacement is rejected because DROP can
also remove dependent mappings and nicknames. Library, wrapper association and
server type changes need an explicit recreation plan. Category defaults without
TYPE are preserved, but cannot be replayed without an explicit native plan;
unchanged snapshots still produce an empty ALTER.

### Federation server privileges

`Db2ObjectPrivileges.Kind.SERVER` adds PASSTHRU grants without changing existing
sequence/index snapshot fields. Use a null schema for the database-wide server
identity, for example:

```java
new Db2ObjectPrivileges.Grant(Db2ObjectPrivileges.Kind.SERVER, null, "REMOTE",
    "PASSTHRU", null, Db2ObjectPrivileges.GranteeType.USER, "APPUSER", false);
```

The existing extended-privilege reader includes SYSCAT.PASSTHRUAUTH in its one
bulk query. No extra query per server is performed. Explicit schema filters
exclude servers because they have no schema; object-name filters still apply.
Catalog CREATE/ALTER grants PASSTHRU after server creation; removing a grant
generates REVOKE without dropping the server. Direct tagged ObjectPrivilege
GRANT/REVOKE uses the same native syntax. USER/GROUP/ROLE/PUBLIC are distinct;
multiple original grantors are retained but replayed once per effective grant.
WITH GRANT OPTION and schema-qualified server identities are rejected.

The change applies to Db2 LUW's existing dialect inheritance, using catalog
columns and PASSTHRU syntax rather than adding a resolver version. Resolver
routing and existing sequence/index behavior are unchanged. Historical catalog
execution and server privilege replay still require live validation. Older
clients without the new enum member cannot read snapshots containing SERVER.
No pass-through session or remote SQL is opened by the reader or unit tests.
Nickname reconstruction is described below; arbitrary column options and
nonrelational definitions require an explicit complete definition.

### User mappings (Db2 LUW 11.1 and later)

Full Catalog loading captures SYSCAT.USEROPTIONS in one additional query for all
visible mappings. `Db2CatalogReader.userMappings().read(connection)` returns the
typed snapshot directly. `Db2UserMappings.get(catalog)` and
`Db2UserMappings.set(catalog, mappings)` use a separate `db2.userMappings.v1`
extension, preserving the existing federation snapshot API/format. USER and
PUBLIC identities, server names, options and unavailable-option markers survive
XML and cloning. Older dialects retain their previous loading behavior; the new
reader/generator rejects nonempty user-mapping plans before 11.1. This is a
conservative coverage boundary rather than the introduction version of mappings.

The catalog stores REMOTE_PASSWORD encrypted. The query projects NULL for this
option and the reader does not fetch its value. Instead it records
`unavailableOptions = ["REMOTE_PASSWORD"]`. Catalog CREATE rejects such a
snapshot until the intended value is explicitly supplied or a complete CREATE
definition is provided. For example, after reading a snapshot:

```java
var mappings = Db2UserMappings.get(catalog).mappings().stream()
    .map(mapping -> mapping.withOption("REMOTE_PASSWORD", intendedPassword))
    .toList();
Db2UserMappings.set(catalog, mappings);
```

Resolve each mapping's intended credential separately when they differ. Supplied
option values are persisted in XML and emitted as SQL string literals; they are
not secret references or an encrypted credential store. Optional `definition`
is trusted complete executable CREATE SQL and takes precedence over supplemental
options. Changing that definition or its supplemental options through automatic
ALTER requires an explicit native plan.

Catalog CREATE orders wrappers/servers, user mappings, then schemas/nicknames.
Catalog ALTER supports ADD/SET/DROP options, explicit password replacement and
DROP USER MAPPING. Removed mappings precede federation changes, and new or
altered mappings precede nickname creation. Switching between PUBLIC and
individual mappings drops the old mappings before creating replacements.
PUBLIC and individual mappings cannot coexist on a server; modeled PUBLIC
mappings also conflict with the FED_PROXY_USER server option. SQL inside an
authoritative server definition is not parsed to discover proxy configuration.

Unchanged unavailable passwords are retained during ordinary option changes.
Resolving a catalog password marker emits SET, not ADD. Removing the password,
replacing a known option with an unreadable marker, malformed options and invalid
catalog principal types fail explicitly. Two unreadable passwords cannot be
compared, so password drift/rotation is not detectable from these snapshots and
reverse ALTER cannot restore an unreadable original password.

External user-mapping repositories/plugins are not inspected; only catalog rows
visible to the connected authorization ID are captured. No mapping DDL, remote
authentication or pass-through sessions are executed by the unit tests. Live
11.1/11.5/12.1 mapping round trips remain unverified.

### Nicknames (Db2 LUW 11.1 and later)

Full Table/Schema loading includes SYSCAT.TABLES type N as a shared `Table`, so
columns, comments, table/column grants and Schema XML reuse the existing model.
`Db2Nickname.get(table)` exposes server identity, remote schema/table, wrapper
type, caching and immutable table/column option maps in `db2.nickname.v1`.
`Db2Nickname.isNickname(table)` distinguishes these remote objects from local
tables. Applications selecting tables for data operations must account for
this distinction. Metadata reads query only local catalogs, not remote data.

The 11.1+ table query joins nickname/server/wrapper catalogs. Both table and
column options are fetched together with one additional UNION query for the
requested nickname set; twenty objects across schemas still use one query.
Readers before 11.1 retain their previous table query and do not discover
nicknames. This is a conservative coverage boundary, not a claim that nicknames
or these features were introduced in 11.1. DialectResolver routing is unchanged.

For relational wrappers with no table options or informational constraints,
CREATE emits a quoted CREATE NICKNAME FOR server.schema.table (or server.table),
then restores local column names, modeled column types and caching. Source
remote columns must retain their identity and ordinal membership. Only remote
column names whose generated local spelling is unambiguous are handled:
ordinary uppercase names of at most 30 characters. LOCAL NAME changes use
temporary collision-free names before final names, including column-name swaps.
REMOTE_COLUMN_NAME is treated as mapping metadata rather than an option to SET.
Comments follow; remote index specifications remain inspection metadata and
are never emitted as physical CREATE INDEX statements.

Special/generated remote names, user-defined types, informational constraints,
other nickname/column options, nonrelational wrappers and incomplete catalog
metadata require `Table.definition`, a complete ordered list of executable
statements. It can include CREATE NICKNAME and subsequent native ALTERs:

```java
table.getDefinition().add("CREATE NICKNAME ...");
table.getDefinition().add("ALTER NICKNAME ...");
```

That definition is authoritative; embedded columns/options/index specifications
are supplemental metadata rather than mandatory duplicates. The statements are
trusted input and are not rewritten. Native DROP uses DROP NICKNAME. Modified
nicknames are rejected by Table/Schema/Catalog ALTER rather than becoming ALTER
TABLE or an implicit replacement; supply an explicit native plan that accounts
for dependencies and remote naming. Ordinary tables retain existing behavior.
CREATE NICKNAME can access the remote data source; no nickname DDL was executed
in this validation. Real 11.1/11.5/12.1 federation round trips remain unverified.

## Verification

```powershell
.\gradlew.bat :sqlapp-core-db2:test
.\gradlew.bat :sqlapp-core:test
.\gradlew.bat :sqlapp-core-dialect-test:compileDockerTestJava
.\gradlew.bat :sqlapp-command:test --tests "*GenerateHtmlDocsCommandTest" --tests "*MigrationSchemaDriftAssessorTest"
.\gradlew.bat :sqlapp-core-mysql:test --tests "*MySqlPrivilegeMetadataTest"
.\gradlew.bat :sqlapp-core-saphana:test --tests "*SapHanaSystemPrivilegeMetadataTest"
```

Current local validation after database-authority support: core 1,219 tests,
Db2 101 tests, MySQL privilege metadata 13 tests, SAP HANA system privilege
metadata 2 tests and the selected command tests 10 tests passed on Java 21.
At that stage, Docker-test compilation and selected Firebird privilege tests were blocked
by unrelated Firebird compilation errors: `FirebirdFunctionReader` calls missing
`NamedArgument.isNullable()`, and `Firebird30CreateFunctionFactory` calls missing
`FunctionReturning.isNotNull()`. Those sources were not changed by this work.
After federation support, `:sqlapp-core-db2:test` passed 113 tests (including
12 federation tests), and `:sqlapp-core-dialect-test:compileDockerTestJava`
succeeded with all tasks up to date in the current workspace. The prior Firebird
compilation blocker is no longer present in that workspace; these federation
changes did not modify Firebird sources. Docker/database tests and federation
DDL were not executed. No generated output was edited manually.

After PASSTHRU support, the focused object-privilege/model/reader/SQL tests
passed, followed by `:sqlapp-core-db2:test` (116 tests, zero failures) and
`:sqlapp-core-dialect-test:compileDockerTestJava` on Java 21. Three new tests
cover server snapshot/XML identity, 20 server grants in one query, and Catalog
CREATE/ALTER ordering and revocation. The existing direct GRANT/REVOKE test also
covers SERVER. `git diff --check -- sqlapp-core-db2` passed. No external database
or Docker test execution was performed.

After nickname support, the seven focused `*Db2Nickname*` tests passed, followed
by `:sqlapp-core-db2:test` (123 tests, zero failures) and
`:sqlapp-core-dialect-test:compileDockerTestJava` on Java 21. The new tests cover
the 11.1 catalog boundary, fixed-query metadata loading, XML/cloning, name swaps,
native CREATE/DROP, Schema/Catalog composition, comments, ignored physical index
DDL and explicit rejection of unsupported or malformed mappings. No live
federation database or remote data source was accessed.

After user-mapping support, the ten focused `*Db2UserMapping*` tests passed,
followed by `:sqlapp-core-db2:test` (133 tests, zero failures) and
`:sqlapp-core-dialect-test:compileDockerTestJava` on Java 21. Coverage includes
20 mappings in one query, suppression of encrypted password values, XML/cloning,
PUBLIC validation, failure propagation, credential resolution, reverse option
ALTER, complete-definition guards, DROP ordering and server/mapping/nickname
composition. Docker/database tests were not executed and no generated files
were edited manually.

The combined object-privilege/global-variable/index batch adds 15 focused tests.
Final Java 21 validation passed `:sqlapp-core-db2:test` (148 tests, zero failures),
`:sqlapp-core-dialect-test:compileDockerTestJava`, and the selected command tests
(`*GenerateHtmlDocsCommandTest`, `*MigrationSchemaDriftAssessorTest`: 10 tests).
Query-count cases cover 120 new-scope grants, 20 variables and 20 expression
indexes without per-object SELECTs. XML/cloning, quoted identities, version
boundaries, native syntax, grantability, string/binary/decimal types, expression
keys, physical index options and full-definition fallbacks are covered.
No external database or Docker tests were executed; generated output was not
edited manually. There are no dependency/version or shared-core API changes.

Java 21 is required. Unit tests cover XML/clone retention, complete-definition
precedence, native/PLSQL paths, overload DROP identity, return-column parsing,
version routing, INCLUDE validation, separate indexes/comments, typed grants,
Catalog CREATE integration, and a fixed one-query table-privilege read for
twenty tables. Schema-grant tests cover 20 schemas in one query, legacy/11.5.5
catalog boundaries, malformed flags, resolver selection, XML/cloning, native
Catalog CREATE/ALTER, authority restrictions and grant-option transitions.
Extended-grant tests cover 20 sequences and 20 indexes with one query, same-name
scope isolation, multiple grantors, malformed snapshots, quoted identifiers,
native creation order, forward/reverse Catalog ALTER and unstable backing-index
identity rejection.
Database-authority tests cover one query for 20 principals, versioned catalog
columns, malformed flags, 9.1 resolution, SECADM principal restrictions,
9.7/11.5/12.1 DBADM defaults, cross-boundary rejection, multiple grantors,
same-named USER/GROUP differences, native revoke ordering and XML/cloning.
The shared collection has focused bulk-addition, ownership, clone and XML tests.

`Db2MetadataReaderTest` (12.1.5) and `Db2_1150MetadataReaderTest` (11.5) also call
`Db2RecreationAssertions`: read a dedicated container schema, retain view columns
and scalar returns, serialize to XML, drop/recreate tables/indexes/comments,
views, aliases and routines, replay table/column/routine grants, and reread.
The same test also recreates MQTs and constraints whose source enforcing indexes
have different names (the recreated backing index name is server-selected),
applies column masks and row permissions through Schema CREATE, verifies visible
data, and executes forward/reverse permission changes through Schema ALTER.
It also captures, revokes and replays schema privileges, using SELECTIN only on
11.5.5+ and ALTERIN on older targets.
It recreates a sequence with the same name as a standalone index, replays both
privilege scopes, and executes forward/reverse privilege changes via Catalog
ALTER.
The container test also uses a dedicated role to capture, revoke, replay and
forward/reverse ALTER a BINDADD database grant, then drops that role. It does not
grant or revoke DBADM/SECADM in the recreation scenario.
These are separate Docker tests and have not been executed as part of this work.
No external or production database is accessed by the unit tests.

## SQL modules and module aliases (LUW 11.1+)

`Schema.packages` now represents SQL modules and PL/SQL packages
from `SYSCAT.MODULES`; it does not represent bound executable packages from
`SYSCAT.PACKAGES`. `specifics.DB2_OBJECT_TYPE=MODULE` and `MODULETYPE=M/A/P`
make that distinction explicit. Module aliases are read once through the existing
`Schema.synonyms` path, preventing duplicate CREATE operations from Package and
Synonym readers. Explicitly modeled Package aliases remain supported by the native
factory. The module reader retains dialect, owner, creation time,
remarks and member count. One joined query fetches the entire requested
set, including counts from `SYSCAT.MODULEOBJECTS`, with schema/package-name filters.
The 11.1 boundary is the support boundary for this implementation, not the product's
module introduction version. Older dialect readers and resolver behavior are unchanged.

Empty SQL modules with an explicit zero member count generate `CREATE MODULE`.
Private/public module aliases use `CREATE [PUBLIC] ALIAS ... FOR MODULE`, and alias
comments and drops retain their object kind. Nonempty modules and PL/SQL packages
require complete ordered executable statements in `Package.definition`; the catalog
reader does not infer a complete module body from a count. For example:

```java
var module = new com.sqlapp.data.schemas.Package("API").setSchemaName("APP");
module.getSpecifics().put("DB2_OBJECT_TYPE", "MODULE");
module.getSpecifics().put("MODULETYPE", "M");
module.getDefinition().add("CREATE MODULE APP.API");
module.getDefinition().add("ALTER MODULE APP.API PUBLISH PROCEDURE P() BEGIN END");
```

Definitions preserve order through XML and clone and are authoritative for member
recreation. Missing or unsupported components fail with an actionable error rather
than silently producing an empty module. Standalone function/procedure/argument and
type/domain queries on 11.1+ exclude module members, preventing their accidental
recreation outside the module. Global variable queries already exclude module members.
Module-member bodies, visibility, conditions and dependency ordering are not yet
reconstructed automatically. Direct explicit DROP is supported; automatic modification
or removal is rejected because DROP MODULE removes all members and invalidates dependents.

Six focused tests verify bulk query counts, version boundaries, standalone-member
filtering, quoting, native alias syntax, full-definition XML/clone replay and replacement
guards. Java 21 `:sqlapp-core-db2:test` passed 154 tests with zero failures;
`:sqlapp-core-dialect-test:compileDockerTestJava` also passed. No database was
accessed to run these tests; Docker tests were compiled, not executed.

References: [MODULES](https://www.ibm.com/docs/en/db2/11.1?topic=views-syscatmodules),
[MODULEOBJECTS](https://www.ibm.com/docs/en/db2/11.1?topic=views-syscatmoduleobjects),
[CREATE ALIAS](https://www.ibm.com/docs/en/ias?topic=statements-create-alias),
[COMMENT](https://www.ibm.com/docs/en/db2/11.1?topic=statements-comment).

## Federation function mappings (LUW 11.1+)

Full Catalog loading includes `Db2CatalogReader.functionMappings()`. It reads
`SYSCAT.FUNCMAPPINGS` joined to `FUNCMAPOPTIONS`, then all matching
`FUNCMAPPARMOPTIONS` in a second query. Query count is independent of mapping count;
the reader never connects to a remote data source. System-owned SYSIBM mappings
are excluded. The typed `Db2FunctionMappings` snapshot retains mapping identity,
specific function identity, server/category/version/wrapper scope, function options,
local/remote parameter options and comments through XML and clone.

Later mappings take precedence in Db2, so list order is semantic. The query orders
by CREATE_TIME and retains an `orderKnown=false` marker when creation times are tied
or missing. Generation requires the caller to select an intended order in that case.
Appending new mappings is supported by Catalog ALTER. Removal, modification or
reordering requires an explicit native plan to avoid silently changing precedence.
The extension is removed from the cloned generic diff inputs to prevent duplicate
or generic handling of its vendor-specific payload.

The documented catalog fields do not reliably expose `WITH INFIX`; the reader
retains `Mapping.infix=null`. For an ordinary specific-function mapping, explicitly
resolve notation with `mapping.withInfix(false)` (prefix) or `true` (infix).
`mapping.withDefinition(completeCreateSql)` supplies authoritative SQL when a built-in
signature or parameter options cannot be reconstructed. Complete definitions do not
require redundant components. Missing information raises an actionable error.
For example, after inspecting notation and precedence:

```java
var snapshot = Db2FunctionMappings.get(catalog);
var resolved = snapshot.mappings().stream()
    .map(mapping -> mapping.withInfix(false)) // only when prefix notation is intended
    .toList();
Db2FunctionMappings.set(catalog, resolved); // explicitly selects this ordered list
```

Catalog CREATE creates mappings after schemas and local functions, and before
extended object grants. 11.1+ standalone function metadata now includes template
and sourced functions with blank LANGUAGE fields. Enable `ReaderOptions.readDefinition`
to retain their authoritative catalog DDL; no routine body is invented.
11.1 is this implementation's coverage boundary, not the introduction version.
Older SQL resources and dialect resolution are unchanged. The public additions are
Db2-only helper/reader APIs and an optional Catalog-specifics payload; no existing
API signatures, dependencies or shared Schema XML format were changed.

Ten focused tests cover XML/clone, invalid snapshots/options, two-query bulk reads,
notation/order resolution, native quoting, definition fallback, Catalog CREATE/ALTER
integration, template definitions and older version rejection. These are unit tests;
real Db2 execution remains unverified. Final Java 21 validation passed
`:sqlapp-core-db2:test` (164 tests, zero failures) and
`:sqlapp-core-dialect-test:compileDockerTestJava`. The selected command tests
`GenerateHtmlDocsCommandTest` and `MigrationSchemaDriftAssessorTest` were confirmed
UP-TO-DATE by Gradle. An intermediate template test failed because its test reader
had not enabled definition reading; it passed after enabling that documented option.
No external database or Docker test execution was performed, and no generated
files were intentionally edited.

References: [CREATE FUNCTION MAPPING](https://www.ibm.com/docs/en/db2/11.1.0?topic=statements-create-function-mapping),
[FUNCMAPPINGS](https://www.ibm.com/docs/en/ias?topic=views-syscatfuncmappings),
[FUNCMAPPARMOPTIONS](https://www.ibm.com/docs/en/db2/11.1?topic=views-syscatfuncmapparmoptions),
[COMMENT](https://www.ibm.com/docs/en/ias?topic=statements-comment).

## Federation type mappings (LUW 11.1+)

Full Catalog loading now includes `Db2CatalogReader.typeMappings()`. One local
`SYSCAT.TYPEMAPPINGS` query reads all user-defined mappings, excluding SYSIBM-owned
entries. `Db2TypeMappings.get(catalog)` exposes an immutable ordered snapshot in
`db2.typeMappings.v1`; it preserves direction, type schema/name, lengths/precision,
scale, lower/upper bounds, scale/precision relationships, bit-data flags,
server/category/version/wrapper scope and remote type metadata through XML and clone.
The native attributes remain available even when automatic generation cannot
represent their conditions. No remote data source is contacted.

Generation supports built-in integer, REAL/DOUBLE, DATE/TIME and exact local
DECIMAL/NUMERIC types. Local declarations use the shared dialect type generator.
Remote DECIMAL/NUMERIC/NUMBER support exact precision/scale and forward ranges,
such as `NUMBER([10..38],2)`. Reverse mappings require exact remote dimensions.
Native syntax deliberately preserves Db2's direction convention:

```sql
CREATE TYPE MAPPING "ORACLE_DATE"
  FROM LOCAL TYPE DATE TO SERVER "ORACLE1" REMOTE TYPE DATE
CREATE TYPE MAPPING "ORACLE_DECIMAL"
  TO LOCAL TYPE DECIMAL(10,2) FROM SERVER "ORACLE1" REMOTE TYPE NUMBER(10,2)
```

Catalog CREATE and ALTER emit type mappings after server creation and before Schema
objects/nicknames, so nickname registration sees the intended mapping. Comments
use `COMMENT ON TYPE MAPPING`. Later mappings take precedence, so creation order is
retained and tied/missing creation times are marked unresolved. Supplying an
explicit ordered list through `Db2TypeMappings.set(catalog, mappings)` resolves the
order. Catalog ALTER supports appending mappings; removal, replacement or reordering
requires an explicit native plan. Generic Catalog diff receives cloned inputs with
this extension removed, preventing duplicate handling.

Character/graphic units, wildcard/default dimensions, timestamp precision, user or
vendor-specific types, unsupported local ranges, scale/precision relationships and
bit-data cases require `mapping.withDefinition(completeCreateSql)`. A full definition
is authoritative and does not require duplicate modeled fields. Components are
never silently approximated. Built-in defaults not present in the user mapping
catalog are not synthesized. The 11.1 boundary is this implementation's support
boundary; older reader resources and dialect resolution are unchanged.

Related integration corrections keep module aliases solely in the Synonym reader
and add the PUBLIC keyword for SYSPUBLIC aliases on CREATE/DROP for every alias kind.
PUBLIC aliases require LUW 9.7+; older dialects fail clearly. The shared Package
alias factory remains usable for explicit native models.

Nine type-mapping tests and one public-alias test cover one-query bulk loading,
directions, raw conditions, XML/clone, version boundaries, numeric ranges, complete
definitions, precedence guards and Catalog CREATE/ALTER integration. Module reader
tests additionally enforce disjoint module/alias selection. Public additions are
Db2-specific helper/reader APIs and optional specifics; no existing API signature,
shared XML format, dependency version or module dependency changed.

Java 21 verification passed `:sqlapp-core-db2:test` (174 tests, zero failures).
`:sqlapp-core-dialect-test:compileDockerTestJava` was confirmed UP-TO-DATE.
Focused type-mapping/alias/module-reader tests passed before the full module run.
No real database or Docker test was executed, and no generated files were
intentionally edited. Live catalog/DDL validation remains outstanding.

References: [TYPEMAPPINGS](https://www.ibm.com/docs/en/db2/11.1?topic=views-syscattypemappings),
[CREATE TYPE MAPPING](https://www.ibm.com/docs/en/db2/11.5.x?topic=statements-create-type-mapping),
[COMMENT](https://www.ibm.com/docs/ja/db2/11.1.0?topic=statements-comment),
[CREATE ALIAS](https://www.ibm.com/docs/en/ias?topic=statements-create-alias).

## Range partition recreation (LUW 9.1+)

Range metadata now retains `LOWINCLUSIVE` and `HIGHINCLUSIVE` from
`SYSCAT.DATAPARTITIONS`; invalid or missing catalog flags fail clearly. The reader
no longer requests the nonexistent `PARTITION_EXPRESSION` column from that result.
Partition keys continue to come from the separate set-based
`SYSCAT.DATAPARTITIONEXPRESSION` query. Quoted keys resolve against shared Table
columns after unquoting; other catalog SQL expressions are kept in
`ReferenceColumn.statement`, rather than becoming quoted column names.
`NULLSFIRST=Y/N` maps explicitly to the shared NULL ordering property.

Generation composes these properties in the registered Partitioning factory used
by CREATE TABLE. Finite excluded bounds emit `EXCLUSIVE`; included bounds use Db2's
inclusive default. This corrects the previous inversion of the low-bound flag and
loss of the high-bound exclusion. MINVALUE/MAXVALUE bounds omit the exclusion
keyword. Mixed finite/infinite exclusive tuples require complete table DDL rather
than guessing. NULLS FIRST/LAST and raw key expressions are emitted as syntax.
Data and LOB table spaces remain available per partition; INDEX IN retains its
9.7+ boundary.

The reader skips the range-catalog queries on dialects before 9.1, when LUW range
partitioning was unavailable. Newer versions retain the existing bulk query
strategy: twenty tables use one key query and one partition query. Version
resolution and SQL resources remain unchanged. Non-RANGE organizations,
subpartitioning, missing keys/bounds, DESC or INCLUDE keys fail clearly in native
range generation. DPF distribution, mixed DPF/range organizations and arbitrary
partition ALTER require a complete native table definition or a separate explicit
plan; they are not silently converted to RANGE.

Ordinary Table CREATE now also honors ordered complete `Table.definition` statements
before component generation. Columns, constraints and organization in that DDL are
authoritative; separately modeled indexes and comments retain their existing paths.
This makes the complete-definition fallback usable for unsupported organizations.

Eight new tests cover all four finite bound combinations, XML/clone, quoted keys,
raw expressions, NULL ordering, infinite bounds, invalid flags, per-partition
spaces, query counts, historical boundaries and complete-definition replay. The existing CREATE TABLE SQL
fixture is intentionally updated with the correct finite upper `EXCLUSIVE` clause.
The shared Schema model, public API signatures and dependencies are unchanged.

Final Java 21 validation passed `:sqlapp-core-db2:test` (182 tests, zero failures).
Docker test compilation and the selected HTML-documentation/schema-drift command
tests were confirmed UP-TO-DATE. Intermediate compilation/test failures exposed
an AbstractPartition API mismatch, an ambiguous test setter, keyword casing and
the legacy-dialect premise of an existing partition-routing test; all were fixed
before this final run. No real database or Docker test execution occurred, and no
generated files were intentionally edited.

References: [DATAPARTITIONEXPRESSION](https://www.ibm.com/docs/en/db2/11.1?topic=views-syscatdatapartitionexpression),
[DATAPARTITIONS](https://www.ibm.com/docs/ja/db2/11.1.0?topic=views-syscatdatapartitions),
[Defining ranges](https://www.ibm.com/docs/en/db2/11.1.0?topic=tables-defining-ranges),
[Version support and organizations](https://www.ibm.com/docs/en/db2/11.5.x?topic=tables-table-partitioning).

## Distinct and array types (LUW 11.1+)

The existing one-query Domain reader now retains Db2 native kind (`METATYPE=T/A/L`)
with `specifics.DB2_OBJECT_TYPE=DATA_TYPE`. It preserves source schema/name/module,
strong/weak type rules, zero scale, nullability, check expressions, string units,
array cardinality/index metadata and function path. Modern queries exclude module
members and system-owned types. Pre-11.1 query resources and metadata behavior
remain separate. The 11.1 boundary is this implementation's coverage boundary,
not the product introduction version for distinct or array types.

Distinct types use the shared Domain nullability/check properties. Ordinary arrays
retain their maximum cardinality. Associative arrays retain index type information
in specifics; their index-type length is no longer set as the maximum array size.
The registered Db2 Domain factories emit native syntax, for example:

```sql
CREATE TYPE "APP"."POSITIVE" AS INTEGER WITH WEAK TYPE RULES
  NOT NULL CHECK (VALUE >= 0)
CREATE TYPE "APP"."AMOUNTS" AS DECIMAL(10,0) ARRAY[50]
CREATE TYPE "APP"."BY_ID" AS INTEGER ARRAY[INTEGER]
```

Automatic reconstruction supports built-in integer, REAL/DOUBLE, BOOLEAN, DATE/TIME,
explicit TIMESTAMP precision and DECIMAL/NUMERIC source types. Local source spelling
and dimensions use the shared dialect type generator. Strong types retain STRONG
TYPE RULES; data-type constraints are generated only for weak types. Ordinary arrays
require explicit positive cardinality; associative arrays currently support INTEGER
indexes. Catalog CREATE creates these native types before tables that reference them.
Comments use COMMENT ON TYPE, and explicit direct drops use DROP TYPE.

Character/graphic string units, LOBs, DECFLOAT, VARCHAR indexes, nested/user-defined
element types and other unsupported or ambiguous components require complete ordered
`Domain.definition` statements. Complete definitions are authoritative and do not
require duplicate components. Unmarked generic domains fail clearly instead of
becoming unsupported CREATE DOMAIN syntax. Direct use of these new factories on
pre-11.1 dialects is rejected; this change does not certify historical type DDL.
Automatic modifications/removals are rejected because dependent columns and routines
need an explicit native plan; additions use the same CREATE factory.

Seven focused tests cover 20-type/one-query retrieval, zero scale and constraints,
index-length/cardinality separation, XML/clone, strong/weak/array SQL, comments,
version boundaries, complete DDL, change guards and Catalog creation order.
No shared Schema API, XML format, dependency version or module dependency changed.

Java 21 validation passed `:sqlapp-core-db2:test` (189 tests, zero failures).
Docker test compilation and the selected HTML-documentation/schema-drift command
tests were confirmed UP-TO-DATE. The focused reader tests initially failed because
their mock lacked nullable numeric/object accessors; those mock gaps were fixed
and all seven focused tests passed before the full module run. No real database or
Docker test execution occurred, and no generated files were intentionally edited.

References: [CREATE TYPE distinct](https://www.ibm.com/docs/en/db2/11.1?topic=statements-create-type-distinct),
[CREATE TYPE array](https://www.ibm.com/docs/en/db2/11.1.0?topic=statements-create-type-array),
[DATATYPES](https://www.ibm.com/docs/en/db2-big-sql/7.1.0?topic=views-syscatdatatypes).

## Remaining parity gaps

PostgreSQL's general vendor-object coverage is still broader. Federation
external user-mapping repositories, fully automatic complex nickname recreation
and fully automatic function/type mapping reconstruction, automatic module-member reconstruction,
security labels, instance-level authorities, the newer schema RCAC
backup/restore authority and method privileges still need separate work.
Automatic XML/text/specialized index reconstruction, all physical index options,
arbitrary dependency planning and comprehensive ALTER round trips also need
separate work. MQT/view dependency order beyond the supplied native Schema
ordering is not inferred from SQL text. Existing CREATE IF NOT EXISTS behavior is unchanged; the example
explicitly disables it for portable LUW recreation. SQL body text parsing is not
a general Db2 SQL parser; complete catalog definitions remain authoritative.

Live execution on 11.5/12.1 and historical-version catalog checks remain required
before declaring PostgreSQL-level parity. Unit success alone cannot establish
catalog compatibility or executable DDL for every supported Db2 version.

## IBM references

- [SYSCAT.TBSPACEAUTH](https://www.ibm.com/docs/en/db2/11.1?topic=views-syscattbspaceauth)
- [SYSCAT.PACKAGEAUTH](https://www.ibm.com/docs/en/db2/11.1.0?topic=views-syscatpackageauth)
- [SYSCAT.MODULEAUTH](https://www.ibm.com/docs/en/db2/11.1?topic=views-syscatmoduleauth)
- [SYSCAT.VARIABLEAUTH](https://www.ibm.com/docs/en/db2/12.1.x?topic=views-syscatvariableauth)
- [SYSCAT.WORKLOADAUTH](https://www.ibm.com/docs/en/db2/11.5.x?topic=views-syscatworkloadauth)
- [SYSCAT.XSROBJECTAUTH](https://www.ibm.com/docs/en/db2-big-sql/7.1.0?topic=views-syscatxsrobjectauth)
- [SYSCAT.VARIABLES](https://www.ibm.com/docs/en/db2/12.1.x?topic=views-syscatvariables)
- [CREATE VARIABLE](https://www.ibm.com/docs/en/db2/11.5.x?topic=statements-create-variable)
- [GRANT table space privileges](https://www.ibm.com/docs/en/db2/11.1.0?topic=statements-grant-table-space-privileges)
- [GRANT module privileges](https://www.ibm.com/docs/en/db2/11.1.0?topic=statements-grant-module-privileges)
- [REVOKE global variable privileges](https://www.ibm.com/docs/en/ias?topic=statements-revoke-global-variable-privileges)
- [SYSCAT.INDEXCOLUSE expressions and RANDOM](https://www.ibm.com/docs/ja/db2/11.1.0?topic=views-syscatindexcoluse)
- [Index free space options](https://www.redbooks.ibm.com/redbooks/pdfs/sg246437.pdf)

- [SYSCAT.WRAPPERS](https://www.ibm.com/docs/en/db2/11.1?topic=views-syscatwrappers)
- [SYSCAT.SERVEROPTIONS](https://www.ibm.com/docs/en/db2/11.1?topic=views-syscatserveroptions)
- [CREATE WRAPPER](https://www.ibm.com/docs/en/db2/12.1.0?topic=statements-create-wrapper)
- [CREATE SERVER](https://www.ibm.com/docs/en/db2/12.1.x?topic=statements-create-server)
- [Registering Db2 server definitions](https://www.ibm.com/docs/en/db2/11.5.x?topic=sources-registering-server-definitions-db2-data)
- [ALTER SERVER](https://www.ibm.com/docs/ja/db2/11.1.0?topic=statements-alter-server)
- [SYSCAT.PASSTHRUAUTH](https://www.ibm.com/docs/en/db2/12.1.x?topic=views-syscatpassthruauth)
- [GRANT server privileges](https://www.ibm.com/docs/en/db2/11.1?topic=statements-grant-server-privileges)
- [REVOKE server privileges](https://www.ibm.com/docs/en/db2-as-a-service?topic=statements-revoke-server-privileges)
- [SYSCAT.NICKNAMES](https://www.ibm.com/docs/zh-tw/db2/11.1.0?topic=views-syscatnicknames)
- [SYSCAT.COLOPTIONS](https://www.ibm.com/docs/en/db2/11.1?topic=views-syscatcoloptions)
- [CREATE NICKNAME](https://www.ibm.com/docs/en/db2/11.1.0?topic=statements-create-nickname)
- [ALTER NICKNAME](https://www.ibm.com/docs/en/db2/11.5.x?topic=statements-alter-nickname)
- [Generated nickname column names](https://www.ibm.com/docs/en/db2/12.1.0?topic=applications-preparing-potential-nickname-column-index-name-changes)
- [SYSCAT.USEROPTIONS](https://www.ibm.com/docs/en/db2/11.1?topic=views-syscatuseroptions)
- [User mappings and encrypted catalog passwords](https://www.ibm.com/docs/en/db2/11.1.0?topic=systems-user-mappings)
- [CREATE USER MAPPING](https://www.ibm.com/docs/en/db2/11.1.0?topic=statements-create-user-mapping)
- [ALTER USER MAPPING](https://www.ibm.com/docs/en/db2/11.1?topic=statements-alter-user-mapping)
- [Public mapping restrictions](https://public.dhe.ibm.com/ps/products/db2/info/vr101/pdf/en_US/iiympe1010.pdf)

- [SYSCAT.ROUTINEPARMS](https://www.ibm.com/docs/en/db2/12.1.0?topic=views-syscatroutineparms)
- [SYSCAT.ROUTINES](https://www.ibm.com/docs/en/db2/11.5.x?topic=views-syscatroutines)
- [SYSCAT.INDEXCOLUSE](https://www.ibm.com/docs/en/db2/11.5.x?topic=views-syscatindexcoluse)
- [CREATE INDEX](https://www.ibm.com/docs/en/db2/11.5.x?topic=statements-create-index)
- [CREATE ALIAS](https://www.ibm.com/docs/en/db2/11.1.0?topic=statements-create-alias)
- [CREATE PROCEDURE (PL/SQL)](https://www.ibm.com/docs/en/db2/11.1.0?topic=plsql-create-procedure-statement)
- [GRANT ROLE](https://www.ibm.com/docs/en/db2/11.5.x?topic=statements-grant-role)
- [SYSCAT.COLAUTH](https://public.dhe.ibm.com/ps/products/db2/info/vr6/htm/db2d0/db2d0287.htm)
- [SYSCAT.VIEWS](https://www.ibm.com/docs/en/db2/11.5?topic=views-syscatviews)
- [SYSCAT.CONSTDEP](https://www.ibm.com/docs/en/db2/11.1?topic=views-syscatconstdep)
- [SYSCAT.CONTROLS](https://www.ibm.com/docs/en/db2/12.1.x?topic=views-syscatcontrols)
- [SYSCAT.TABLES](https://www.ibm.com/docs/en/db2/11.5.x?topic=views-syscattables)
- [SYSCAT.SCHEMAAUTH](https://www.ibm.com/docs/en/db2/11.5.x?topic=views-syscatschemaauth)
- [11.5.5 security enhancements](https://www.ibm.com/docs/en/db2/11.5.x?topic=1155-security-enhancements)
- [GRANT schema privileges](https://www.ibm.com/docs/en/db2/11.1.0?topic=statements-grant-schema-privileges)
- [SYSCAT.SEQUENCEAUTH](https://www.ibm.com/docs/en/db2/11.1.0?topic=views-syscatsequenceauth)
- [SYSCAT.INDEXAUTH](https://www.ibm.com/docs/en/db2/11.1?topic=views-syscatindexauth)
- [GRANT index privileges](https://www.ibm.com/docs/en/db2/11.5.x?topic=statements-grant-index-privileges)
- [REVOKE index privileges](https://www.ibm.com/docs/en/db2/11.1.0?topic=statements-revoke-index-privileges)
- [REVOKE sequence privileges](https://www.ibm.com/docs/en/db2/11.1?topic=statements-revoke-sequence-privileges)
- [SYSCAT.DBAUTH](https://www.ibm.com/docs/en/db2/11.5.x?topic=views-syscatdbauth)
- [DBADM in 11.1](https://www.ibm.com/docs/en/db2/11.1.0?topic=authorities-database-administration-authority-dbadm)
- [DBADM in 12.1](https://www.ibm.com/docs/en/db2/12.1.x?topic=authorities-database-administration-authority-dbadm)
- [SECADM version differences](https://www.ibm.com/docs/en/db2/11.1.0?topic=authorities-security-administration-authority-secadm)
- [GRANT database authorities](https://www.ibm.com/docs/en/db2/11.5.x?topic=statements-grant-database-authorities)
