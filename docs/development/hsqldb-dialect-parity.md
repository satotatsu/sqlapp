# HSQLDB metadata and SQL recreation

[Development index](README.md)

The HSQLDB dialect uses the shared Schema model and the existing catalog reader
and SQL factory entry points. This work strengthens the common metadata-to-DDL
path using the same execution-based regression approach as the PostgreSQL
dialect. PostgreSQL-specific objects such as publications, RLS policies and
extensions have no direct HSQLDB equivalent and are not emulated.

## Common entry point

```java
var dialect = DialectResolver.getInstance().getDialect(connection);
var reader = dialect.getCatalogReader().getSchemaReader().getTableReader();
reader.setSchemaName("PUBLIC");
var tables = reader.getAllFull(connection);
var registry = dialect.createSqlFactoryRegistry();
var operations = registry.createSql(tables.get(0), SqlType.CREATE);
```

Generation returns operations; the caller controls execution and transactions.
Use the schema reader's domain, function, procedure or trigger reader for those
objects. Routine name filters select the routine name, with arguments grouped
by specific name to retain overload identity.

## Coverage added

| Area | Behavior |
|---|---|
| Generated columns | Read `GENERATION_EXPRESSION` into `Column.formula`, generate `GENERATED ALWAYS AS`, and advertise formula support on HSQLDB 2.x so shared DML excludes generated columns. |
| Domain columns | Retain NOT NULL independently of domain type resolution. |
| Domains | Group multiple named checks into one domain; retain names, expressions and deferrability; generate their CHECK clauses and retain them through Schema XML. |
| Identity | Preserve ALWAYS/BY DEFAULT, start/increment, minimum/maximum and cycle in CREATE. |
| SQL triggers | Retain transition-row aliases and WHEN conditions in generated SQL; use HSQLDB's filtered trigger query. |
| Routines | Preserve qualified identifiers and string literals, including semicolons; accept multiline definitions; honor complete `definition` without mandatory duplicate components; default omitted language to SQL. |
| Routine security | Use HSQLDB's default definer semantics; explicitly reject unsupported invoker security rather than emit invalid SQL. |
| Table checks | Resolve owning tables from TABLE_CONSTRAINTS and avoid SQL-text LIKE filtering and literal rewriting. |
| ALTER | Generate DROP DEFAULT when removing a default; reject generated-expression changes with an explicit migration error. |
| Resolution | Preserve 1.x and 2.x boundaries; future major versions use the newest known dialect instead of falling back to 2.0 based on their minor number. |
| Distinct types | Read the source representation into `Type.statement`, generate CREATE TYPE, and retain qualified distinct types in table columns. |
| Synonyms | Read schema/name/target identity and recreate quoted local targets. The reader probes SYSTEM_SYNONYMS once per requested set, so older engines without this catalog keep their previous behavior. |
| Domain ALTER | Apply named-check additions, expression replacements and removals, SET/DROP DEFAULT and native rename without replacing dependent columns. The same factory is used through Schema ALTER. |
| Synonym ALTER | Retarget or rename local synonyms with validated DROP/CREATE, without dropping target tables or using CASCADE. |
| Trigger details | Retain OLD/NEW TABLE aliases in specifics and UPDATE OF column lists as a JSON array; read update columns in one joined query. |
| Recreation order | Retain routine/table/view/sequence dependencies from one set-based catalog query per schema. Schema and Catalog CREATE reorder existing shared-factory operations, including dependencies across schemas. GRANT operations follow object creation. |
| Foreign keys | Render `CascadeRule.None` as `NO ACTION`. Schema/Catalog CREATE defers foreign keys until both tables exist, including mutual, self and cross-schema references. Standalone Table CREATE retains inline foreign keys. |

New domain-check metadata uses a single set-based SELECT with joins. A regression
test asserts one prepared query for twelve domains with two checks each.
Existing public signatures, configuration formats and dependencies are unchanged.
The additive `HsqlDdlDependencies` API exposes identities and edges retained in
`hsql.ddlDependencies.v1` Schema specifics. Empty edge sets are omitted. Schema
XML and cloning retain the versioned payload. Invalid versions, unknown fields,
stale schema names and unsupported dependency cycles fail explicitly before a plan is returned.
External prerequisites outside the supplied model are left for the target engine
to validate. Callers constructing models manually may provide dependency edges
through this API. A limited identifier lexer also finds function calls and
NEXT/CURRENT VALUE FOR references in generated expressions, defaults and checks,
without rewriting SQL. It skips quoted literals and comments and conservatively
orders all matching function overloads. It is not a general SQL dependency parser.
Replacing a domain CHECK emits DROP followed by ADD. A failing ADD can leave the
earlier DROP applied under the engine's DDL commit rules; validate existing data
and plan execution accordingly. The generator does not promise atomic DDL.
Read SQL retains schema qualification; moving objects to another schema requires
deliberate rewriting of their SQL rather than global string replacement.

The shared `SqlDataAccess` parser now reads the `ModifiesSqlData` enum name
written by Schema XML, as it already did for the other values. This fixes loss
of the procedure's MODIFIES SQL DATA characteristic after XML restoration
without changing the XML format or any existing accepted SQL spelling.
Named identity sequence references now survive XML restoration and generate
HSQLDB 2.1+ GENERATED BY DEFAULT AS SEQUENCE syntax. Resolved routine specific
names containing dots survive XML and cloning; the additive
`Routine.setSpecificNameLiteral` API avoids treating identifier dots as schema
separators, while the existing qualified setter retains its behavior.

## Text tables and storage

HSQLDB 2.x table metadata reads TEXT source settings in the same bulk query as
tables. Source settings are retained in `hsql.textSource.v1` specifics, alongside
the `hsql.tableType` TEXT marker. Reading requires visibility of the engine's
SYSTEM_TEXTTABLES metadata; missing source metadata fails explicitly.
CREATE preserves MEMORY, CACHED and TEXT storage and READ ONLY state. ALTER
supports MEMORY/CACHED conversion and READ ONLY/READ WRITE. TEXT source or
storage conversions require an explicit file migration.

Restoring a configured TEXT table requires an explicit target source binding,
so ordinary CREATE generation never automatically reopens the recorded file.
The same overload is available on the HSQLDB Table, Schema and Catalog factories:

```java
var factory = (HsqlCreateSchemaFactory) registry.getSqlFactory(schema, SqlType.CREATE);
var operations = factory.createSql(schema, Map.of(HsqlTextTableSources.id(table),
    new HsqlTextTableSources.Source("target.csv;fs=|;encoding=UTF-8", false)));
```

The bindings select target files, not data-copy operations. Source paths and
properties remain subject to HSQLDB's own access rules. An unconfigured TEXT
table may be recreated without a binding. Unknown binding IDs and stale recorded
identities fail before a plan is returned. The generator itself performs no file
I/O. Tests use temporary file databases and verify that the original source file
is unchanged while restoration reads the explicitly selected replacement.

## Versions and limits

Existing supported version paths remain in place. Generated-column capability is
enabled from HSQLDB 2.0. Execution tests use disposable memory/file HSQLDB 2.7.4;
older engine binaries and future major versions have not been executed by these
tests. Resolver tests cover capability and version boundaries separately.

This is not a claim of complete feature parity with PostgreSQL.
Destructive generated-expression/type changes require an explicit data migration;
the dialect does not automatically drop columns or dependent objects.
Domain ALTER supports defaults, named checks and rename; distinct type ALTER
is explicitly rejected because the bundled engine rejects ALTER TYPE, including
rename, despite the general rename syntax in the manual. A direct engine test
records this boundary. Other unsupported changes fail with an explicit migration requirement.
SQL scalar functions and procedures with complete modeled signatures and bodies
can break dependency cycles through temporary fail-closed declarations followed
by ALTER SPECIFIC ROUTINE BODY. The bundled engine rejects view-only cycles;
opaque routine definitions require a separate migration. External Java routines still
require their classes and permissions on the target database. Synonym visibility
follows HSQLDB's catalog rules (the SYSTEM_SYNONYMS rows require an administrator).

Transition-table aliases are retained and their declaration is execution-tested.
The HSQLDB 2.7.4 engine rejected a SQL trigger body selecting directly from its
transition alias in local verification; the test uses a statement action that
does not select from the alias. This does not establish support for arbitrary
transition-table queries. Direct engine tests cover rejected plain SELECT,
BEGIN ATOMIC and TABLE-query forms; this is an engine boundary, not missing
SQL generation. These tests are specific to the bundled HSQLDB 2.7.4 engine.

HSQLDB does not expose a CREATE CONSTANT schema object. A direct 2.7.4 engine
test verifies rejection of CREATE CONSTANT; the constant reader stays absent.
Constants are therefore outside the HSQLDB schema-object model.

Synonym replacement validates CREATE before returning DROP/CREATE operations.
It is not atomic under HSQLDB DDL commit rules: target absence or permissions at
execution time can make CREATE fail after DROP. Callers must inspect the returned
operations and validate the execution environment. No CASCADE is generated.
Native domain rename is supported only when the engine permits it. In 2.7.4,
dependent table columns prevent domain rename; execution failures propagate.
Rename is emitted before default/check changes so a rejected rename does not
first modify those characteristics.

The shared XML reader now resolves foreign-key reference columns by schema and
table identity instead of the owning table. Cross-schema references remain
intact even when the referenced schema is absent from a standalone Schema XML.
Reference-column cloning retains an independent column and its referenced table
identity. No public signatures or XML attribute names change.

## Explicit table migration (HSQLDB 2.7.4)

`HsqlTableMigration` provides a dialect-local entry point for generated-expression
and built-in scalar type changes that require table replacement. Ordinary ALTER
generation still refuses these changes. Read complete source Catalog metadata,
including incoming foreign keys, triggers and object/column privileges, clone it,
and modify the selected target table:

```java
var migration = new HsqlTableMigration(dialect);
var target = source.clone();
var table = target.getSchemas().get("APP").getTables().get("T");
table.getColumns().get("G").setFormula("V * 3");
var plan = migration.plan(source, target, table);
// Inspect/persist the immutable plan before execution.
var result = migration.execute(connection, plan, HsqlTableMigration.Access.EXCLUSIVE);
```

The caller must enforce exclusive database access and supply a dedicated,
writable administrator connection in auto-commit mode, as the schema owner or
with its owning role enabled. The helper does not acquire a lock across DDL
commits and does not promise atomic rollback. It checks live source metadata,
incoming foreign keys and privileges before creating a uniquely named backup.
Delegated grant provenance and privilege changes require separate migration.

Backup verification uses bidirectional EXCEPT ALL, retaining duplicate rows and
nulls. LOSSLESS conversion is the default; reverse casts must reproduce original
stored values. ALLOW_LOSSY is an explicit optional policy. Replacement restores
stored columns, identity values, indexes, foreign keys, triggers and privileges;
triggers are installed after data copying. Target rows and privileges are checked
again. The plan has a content fingerprint and survives JSON round trips; results
and failures retain a backup data fingerprint and execution phase.

On a replacement failure, inspect `Failure.getPlan()` and `getPhase()` and call
`migration.recover(connection, failure, Access.EXCLUSIVE)` explicitly. Recovery
verifies that backup data is unchanged before restoring the original table.
The backup is retained after success and recovery. Cleanup commands are separate
and never executed automatically. Low-level consumers must honor each Command's
optional guard query, executing it only when its COUNT is nonzero.

This entry point preserves every existing column identity and requires complete
metadata.
It also supports adding nullable columns, columns with explicit DEFAULT values,
and generated columns. Add the Column to the target Table; no additional migration
option is required. Restoring rows explicitly names only retained stored columns,
so the engine applies its supported added defaults during insertion and computes
generated values. Literal and CURRENT_TIMESTAMP defaults are regression-tested.
Existing-column contents and row multiplicities are verified against the backup. Added values are governed by
the target defaults, expressions and constraints; verification does not reevaluate
stateful defaults. Invalid defaults or constraints can fail during restore and
retain the verified backup for explicit recovery of the original table shape and
rows. An added NOT NULL stored column requires an explicit DEFAULT. Newly added
identity columns, column removal/rename, and a target without retained stored columns remain
planning errors. These rules apply to both single-table and batch entry points.
Dependent views/routines require the batch entry point below;
changes to unrelated schema objects require separate migration. It does not
provision users, roles or passwords.

BLOB/CLOB columns and one-dimensional arrays of built-in elements are supported.
Array metadata retains the element type, length and maximum cardinality in the
shared Column model and through Schema XML. CREATE and conversion CASTs preserve
those components. LOB metadata accepts both the native large-object names and
BLOB/CLOB spellings, and CREATE retains the declared length. Modern 2.x type
registration supports the engine's 1 TiB declaration limit; tests retain a 3 GiB
declaration with small data, without allocating a multi-gigabyte value. Legacy
1.8 type registration remains unchanged.
Omitting a LOB length retains the native engine's 1 GiB default rather than
shrinking the previous unqualified BLOB/CLOB declaration.
Array-backed DOMAIN/DISTINCT columns keep their named identity during metadata,
XML and CREATE. Table migration resolves their built-in element type and maximum
cardinality into independent array backups. Both single-table and batch paths
support unchanged named array types, conversion to another existing named array
type, and recovery after a failed restore. LOSSLESS rejects cardinality reduction
that would remove elements before replacing the original table. NULL arrays,
empty arrays and NULL elements are retained. This does not add support for
multidimensional arrays or unresolved named element types.
LOSSLESS validates contents after a conversion round trip, including NULL and
empty values. HSQLDB rejects IS DISTINCT FROM for these types, so validation uses
equality with explicit NULL handling. Bidirectional EXCEPT ALL verifies restored
rows, retaining duplicate multiplicity. LOB fingerprints stream bytes/UTF-16 code
units; array elements are individually framed. Because LOBs cannot be ordered by
SQL, complex rows use sorted SHA-256 row digests (32 bytes plus Java collection
overhead per row) before the final digest. Scalar-only plan fingerprints keep
their existing format. Recovery detects same-length content changes and tolerates
row reordering without accepting changed values.
The backup and verification read original LOB values without a length-limiting
CAST. HSQLDB can accept a BLOB larger than its declared length; casting both sides
of verification would hide lost bytes. Backup LOB columns use the engine's maximum
capacity, so a regression retains all 20,000 bytes of a source declared BLOB(10).
Such inconsistent source data is rejected during validation, before replacement,
even with ALLOW_LOSSY: recreating the original through SQL cannot reliably retain
an out-of-bounds value. The complete verified backup and original remain available.

GLOBAL TEMPORARY tables retain their table kind and commit action. Migration
supports ON COMMIT PRESERVE ROWS on the dedicated execution connection and
preserves session-local data. The caller's exclusive access must cover other
sessions using the same global definition. ON COMMIT DELETE ROWS can be recreated,
but migration rejects it because the required auto-commit connection cannot retain
its rows across phases.

TEXT migration requires an explicit empty target file when the target is TEXT:

```java
var table = target.getSchemas().get("APP").getTables().get("T");
var migration = new HsqlTableMigration(dialect, Map.of(
    HsqlTextTableSources.id(table),
    new HsqlTextTableSources.Source("new.csv;fs=|;encoding=UTF-8", false)));
var plan = migration.planAll(source, target);
```

The default constructor remains sufficient for ordinary tables. TEXT targets
must be pre-created empty files and non-descending; bindings for unknown/non-TEXT tables fail
planning. The engine opens the explicit target and the restore phase checks all
TEXT targets read-only for existing rows before enabling writes or copying any
batch data. An occupied file
fails with retained backups. Recovery reconnects the recorded original source
and verifies it against the backup instead of overwriting or appending to it;
missing/changed source data fails explicitly and retains the backups. Caller-held
exclusive access must also cover these files. Migration detaches a TEXT source
before dropping its table and never issues file-deletion calls. HSQLDB itself can
remove an empty writable cache file. The normal CREATE APIs still require their own explicit
target bindings; migration does not change that rule.

Original TEXT sources currently require an embedded file database and locally
readable paths reported by the engine. Preflight reads those paths in one bulk
catalog query and checks file sizes without writing files. HSQLDB 2.7.4 can treat
a file no larger than its encoded platform line terminator as empty and delete
it when a writable cache closes, even when it contains a short row. Such original
files are rejected before backup/modification; migrate them separately. Read-only
target probing protects occupied tiny files from that engine behavior. Source
data changed outside the caller's exclusive window also prevents automatic
recovery. Rebuild old plans after the array/LOB/temporary metadata improvements.

Columns using an existing scalar or built-in-array DOMAIN or DISTINCT type are supported, including
built-in-to-named, named-to-built-in and named-to-named conversions. The complete
Catalog must include each referenced definition; unqualified names resolve in
the owning table's schema and quoted qualified names retain their exact identity.
Named identities must already exist in both source and target Catalogs; definition
replacement is supported under the restrictions below.
The backup uses comparable built-in base types, so it does not depend on the
original named types. Conversion explicitly passes through the target base type
before casting to its named type. Domain checks are enforced by the engine;
failure before replacement leaves the original intact, and failure while restoring
can be recovered with the retained backup and original named definitions.

Plans automatically retain `namedTypes` snapshots. Before backup and recovery,
the helper verifies referenced DOMAIN/TYPE CREATE definitions with at most two
set-based catalog reads, independent of the number of typed columns. This also
detects changed definitions in a different schema. The existing Plan constructor
and fingerprints for plans without named types remain compatible; the optional
snapshot list is included in the fingerprint when present. Tests round-trip these
plans through JSON and verify duplicate/null retention, independent backups,
cross-schema quoted names, incomplete metadata and stale external definitions.

### Replacing a named type definition

The single-table entry point replaces scalar DOMAIN/DISTINCT definitions when every column
using the changed type belongs to the selected table. For shared types, use the
batch entry point below. Change the canonical type
model in the target Catalog, keeping its schema/name, then plan the owning table:

```java
var target = source.clone();
target.getSchemas().get("APP").getTypes().get("MONEY").setStatement("DECIMAL(18,3)");
var plan = migration.plan(source, target, target.getSchemas().get("APP").getTables().get("T"));
var result = migration.execute(connection, plan, HsqlTableMigration.Access.EXCLUSIVE);
```

No additional options are required. The verified backup already uses built-in
types. Replacement drops the table, drops/recreates each changed type without
CASCADE, restores USAGE grants, then recreates the table and restores its data,
indexes, triggers, identity state, incoming keys and table/column grants. LOSSLESS
remains the default. A complete definition must agree with its scalar model;
conflicting opaque DDL cannot bypass conversion validation.

The plan retains `typeReplacements`, source/target CREATE snapshots, guarded
recovery commands and grant signatures. Existing constructors and old JSON plans
remain supported; the new optional list participates in the content fingerprint
when nonempty. The domain reader retains native source spelling in
`hsql.domainSourceType.v1` for exact recovery guards. This derived field is omitted
from the migration's canonical schema fingerprint to preserve existing plans.

Model validation rejects use by another supplied table. A single bulk live column
read also rejects other users of the changed type before backup, even if the
supplied Catalog omitted their schema. Type privileges must be read completely,
unchanged, and granted by the schema owner; the executing connection must have
the owning role enabled. The existing administrator, auto-commit and exclusive
access requirements apply to every affected schema.

Other dependencies can still make restrictive DROP fail after the original table
has been removed. For example, a routine returning the distinct type prevents its
replacement. This failure propagates with the verified backup and recovery plan.
Recovery skips dropping a type whose original definition remains intact, reverses
completed type replacements, and restores the original table. Tests cover a
partially completed multi-type replacement with such a routine, and recovery after
new DOMAIN checks reject existing rows. Backups remain retained in both cases.

Unnamed target domain CHECK/NOT NULL constraints receive deterministic names in
the replacement SQL; the supplied model is not mutated. Definition verification
compares SQL tokens while preserving string/quoted-identifier contents and numeric
expression semantics. It tolerates formatting and numeric literal DEFAULT scale
normalization such as 8 versus 8.000; emitted SQL is not rewritten.

The single-table path does not rename a type, change unrelated schema objects,
migrate routine/view dependencies, or support
multi-dimensional arrays or unresolved named array element types. Native ALTER TYPE remains explicitly unsupported.

### Coordinated table and shared-type migration

`planAll` infers changed tables and all supplied tables whose columns use a
changed DOMAIN/DISTINCT definition. It uses the same conversion, backup,
metadata and privilege validators as the single-table path:

```java
var plan = migration.planAll(source, target);
var result = migration.execute(connection, plan, HsqlTableMigration.Access.EXCLUSIVE);
```

The default is LOSSLESS; `planAll(source, target, ConversionPolicy.ALLOW_LOSSY)`
is available explicitly. An optional overload accepts target tables and a
conversion policy for a constrained selection. Tables using a changed type outside
that selection cause a planning or preflight error. Table identities must remain
unchanged. Schema-only changes with no affected table require separate migration.

All source metadata and privileges are checked before any backup. Every member
backup and conversion is verified before any original table is removed. Backups
have deterministic names that include table identity, so tables in the same schema
receive distinct backups. The batch drops affected incoming/outgoing foreign keys,
drops selected tables, replaces each shared type once, and creates tables without
foreign keys. It copies all data before adding the target foreign keys and then
restores triggers, grants and read-only settings. Cyclic and cross-schema foreign
keys are supported, including references from an unselected table.

For HSQLDB 2.7.4, the batch also includes unchanged dependent views and routines, following
the metadata dependency graph transitively across supplied schemas. It drops
views in reverse dependency order before replacing tables and recreates them
after data and foreign keys, preserving view triggers and column/object grants.
Triggers owned by affected tables/views are removed before the views and restored
after all views exist, so their bodies can reference dependent views without
blocking a restrictive DROP or replaying trigger side effects during data copy.
Dependent view schemas and complete privileges are verified before backup;
delegated grants and changed view definitions or grants require a separate plan.
`BatchPlan.dependentViews()` exposes their identities, source schema fingerprints
and privilege snapshots. The optional list preserves existing constructors and
JSON fingerprints when empty. Recovery recreates the original views and grants.
The single-table entry point continues to reject dependent views/routines.

Routine dependencies follow table/view/routine usage transitively, including
cross-schema functions and procedures. Routines whose CREATE DDL mentions a
changed named type are conservatively included as well; identifier discovery
preserves quoted spelling and excludes strings/comments. This can include an
unqualified same-name identifier that is not actually a type use. The source and
target routine definitions must otherwise remain unchanged. `dependentRoutines()`
exposes logical/specific identities, source schema fingerprints and complete
routine-family privilege snapshots. The optional list preserves previous batch
constructors and JSON fingerprints when empty. The full visible dependency graph
is checked in one additional bulk query before backup, rejecting dependents in
schemas missing from the supplied catalog.
When replacing named types, another bulk scan of visible routine CREATE definitions
checks type use outside the selected routine set before any backup.

The batch drops individual SPECIFIC ROUTINE identities, recreates routines and
views in dependency order using the existing CREATE planner, and restores complete
family EXECUTE grants after every overload is present. Unaffected overloads keep
their definitions and grants. Incomplete, changed or delegated privilege metadata
is rejected. Routine definitions and privilege changes after planning fail before
backup; restored grants are verified after execution and recovery.

Cycles containing modeled scalar SQL functions/procedures are detached by ALTER
SPECIFIC ROUTINE BODY with the shared fail-closed initialization body. It raises
SQLSTATE 45000 if called. CREATE uses the existing temporary declarations and body
completion mechanism; recovery detaches any surviving cycle members before
restoring the original graph. View-only cycles and cycles without reliable SQL
signatures/bodies still require separate handling. Table defaults calling affected
functions require separate handling (the engine boundary is described below).
Triggers on unselected tables/views calling affected functions or procedures are
also rejected during planning; include their owning relations or migrate those
triggers separately.

Table CHECK constraints calling affected functions are deferred on selected
tables. Before dropping routines, the batch removes those source checks. It creates
tables without them, restores all data and routine bodies, then adds the target
checks through the shared SQL factory. ADD CHECK validates existing rows; failure
retains the backups and supports recovery even after earlier target checks were
successfully added. Recovery conditionally removes checks from either phase and
restores the original checks after the original data/routines. Unnamed target
checks receive deterministic names in cloned models; user models are not mutated.
Opaque table definitions that prevent this component split are rejected.

Named domain CHECKs calling affected routines are also deferred, including domains
in other supplied schemas and domains used by unselected tables. HSQLDB 2.7.4
retains the domain-to-routine dependency even after DROP CONSTRAINT. Migration
therefore keeps those routine declarations, installs fail-closed bodies before
replacement, and restores executable bodies using ALTER SPECIFIC ROUTINE in the
same dependency order as recreated routines. Generated columns and mutual routine
recursion use that same ordering. The domain's identity, base type, defaults and
USAGE grants remain in place. ADD CHECK validates all existing domain-column rows;
a failure retains backups and supports recovery after partial check restoration.
Domain owner schemas are fingerprinted during preflight and domain definitions
are checked before recovery and after completion using bulk reads. The optional
`BatchPlan.dependentDomains` snapshot participates in the content fingerprint only
when nonempty; earlier constructors and JSON plans remain supported.

Changing such a domain's own definition at the same time, opaque/unnamed domain
checks, non-SQL routines, or retained routine signatures referencing a replaced
named type require a separate migration and fail during planning. These cases
require replacing the domain itself and coordinating all its users; they cannot
use the declaration-preserving path. Copying a shared Domain into an existing
Schema now replaces its named checks without duplicate or stale entries; this
does not change XML or public model APIs.

Generated columns can use affected deterministic scalar functions declared
CONTAINS SQL or NO SQL, with the same restrictions on their routine prerequisites.
The batch retains these routines until referencing tables have been dropped. It
then orders the combined table/view/routine CREATE operations with the existing
DDL planner, initializing routine bodies before copying data. Generated values
are recomputed from the target expression; recovery restores the source expression
and checks all original values against the backup. Pure recursive functions and
coexisting routines/views that read the copied tables are regression-tested.
Functions with SQL data reads, non-deterministic functions and unselected expression
owners are rejected before modification; include owning tables explicitly in a
batch when appropriate.

Preflight reads columns, table checks, domains and triggers in bulk to detect
expression owners omitted from the supplied catalog, including other schemas.
It does not query metadata per expression or table. The bundled 2.7.4 engine rejects
data-reading functions in CHECK and rejects user-defined function calls in column
DEFAULT clauses (including parenthesized calls); direct engine tests preserve
these boundaries. Literal defaults remain unchanged during supported migrations.

HSQLDB 2.x parameter metadata now retains IN/OUT/INOUT modes and literal argument
names. The HSQL builder emits a direction once, before the name/type. Quoted OUT
and INOUT names survive metadata, Schema XML and executable recreation. Legacy
readers do not query the 2.x parameter mode field. Manually authored argument
names retain the existing spelling behavior; metadata argument names are marked
with `hsql.argumentNameLiteral.v1=true` for literal quoting.
When present, modern routine UDT catalog names retain their quoted schema identity.
The 2.7.4 engine can report only the base type for DISTINCT arguments/returns;
complete executable CREATE definitions remain authoritative for these signatures.
HSQLDB 2.x function/procedure readers therefore retain those native definitions
even with the shared `readDefinition=false` default. Statements and arguments
remain supplemental metadata. Clear `definition` explicitly before authoring a
replacement through modeled components; no components are required to duplicate
information already present in executable CREATE DDL. Legacy readers keep their
previous definition-option behavior. Existing metadata fingerprints must be
rebuilt when this improved routine representation differs from an older snapshot.
Cycle initialization preserves the native CREATE header and replaces only its
verified body suffix, rather than rebuilding an unreliable signature. An opaque
definition that cannot be split reliably is not eligible for initialization.

`BatchPlan` exposes its per-table members and the coordinated replace/restore/
recovery/cleanup SQL. Execute the BatchPlan as a whole; member plans do not express
the coordinated phase order. The immutable plan has a verified content fingerprint
and supports JSON round trips. `BatchResult` and `BatchFailure` retain each verified
backup's table identity, SQL name, row count and data fingerprint.

Call `migration.recover(connection, batchFailure, Access.EXCLUSIVE)` explicitly
after replacement has started. It verifies every backup before changing any table,
restores original type definitions and tables, copies all original rows, and then
restores foreign keys and other details. A failed conversion before replacement
leaves every original table intact and requires no destructive recovery. A
restrictive DROP failure caused by an external routine remains recovery-tested on
the single-table path; the batch now includes modeled named-type return routines.
Backups remain retained; DDL is not atomic and caller-enforced exclusive access
remains mandatory throughout execution and recovery.

Metadata and privilege reads are shared across members: each selected schema is
loaded once, and named types, column usage, foreign keys and privileges are read
in sets. A regression test verifies that one versus five tables in the same schema
with dependent views and functions requires the same number of observed metadata
queries. Row verification and data
copy queries remain per table. Single-table Plan constructors and JSON remain
compatible, including previously persisted backup names.

## Roles, memberships and settings

HSQLDB 2.x reads visible role definitions from AUTHORIZATIONS and direct role
memberships from ROLE_AUTHORIZATION_DESCRIPTORS, using one SELECT per object set.
APPLICABLE_ROLES is not a membership source: for administrators it expands all
applicable roles, including roles never explicitly granted to that administrator.
Implicit PUBLIC membership and the internal _SYSTEM grantee are omitted. Nested
role grants retain their direct edges rather than being flattened into user grants.
Grantee filters preserve literal quoted names. The common CatalogReader entry
point loads these collections, and Catalog XML retains identities and edges.

The native membership IS_GRANTABLE describes the grantee's administrator status;
it is retained in specifics and is not converted into RoleMember.admin. HSQLDB
2.7.4 rejects WITH ADMIN OPTION and per-grant DEFAULT role settings. The role
GRANT/REVOKE factories reject these unsupported model flags explicitly. They also
reject implicit/system memberships and unsupported grantor metadata rather than
emitting a grant with different semantics. CREATE Role generates quoted custom
role names and omits engine-provided built-in definitions. Catalog CREATE creates
custom roles before generating object and membership grants. Users must already
exist; no passwords are read or generated by this addition. Standalone membership
SQL requires both principals to exist and the executing principal to have the
necessary role administration rights.

The settings reader retrieves visible SYSTEM_PROPERTIES in one SELECT and maps
property names/values to Catalog.settings, retaining scope, namespace and class in
specifics. It supports property-name filters and XML retention. Administrator and
nonadministrator visibility follows the engine; a restricted read is not a claim
of a complete database configuration. Settings are metadata only: Catalog CREATE
does not automatically apply SET commands or local file properties.

These additions apply to HSQLDB 2.x and are execution-tested on the disposable
2.7.4 engine. Legacy 1.x keeps its original role query, returns no new memberships
or properties, and never queries these 2.x catalogs. Older JARs remain unexecuted.

## Privileges and executable scripts

HSQLDB 2.x uses separate bulk privilege queries; the legacy object query remains
separate. CREATE Catalog emits supported grants after schema objects. Column
privileges preserve quoted identities and grant options. HSQLDB TABLE_PRIVILEGES
also summarizes column-only rights as table rights, so SELECT/INSERT/UPDATE/
REFERENCES are conservatively reconstructed from COLUMN_PRIVILEGES. This retains
access to existing columns without widening a restricted user to other columns;
future columns do not automatically inherit these reconstructed grants. Explicit
table-level intent can instead be modeled programmatically. Engine-managed role
grants are omitted because the engine recreates them.

The bundled 2.7.4 engine grants an entire overload family even for SPECIFIC ROUTINE.
Routine grants therefore require complete family coverage, recorded as
`hsql.routineGrantScope.v1=FAMILY`; partial/unknown scope is rejected. Explicit
TRIGGER grants are rejected because this engine accepts grouped syntax but loses
the right internally. Grants inherited from ALL can expose this limitation too;
table replacement refuses such plans before changing data. Unsupported privilege
scopes fail explicitly. Users must already exist; Catalog CREATE can create custom roles. Callers
executing standalone grant SQL are responsible for the intended grantor context.

The privilege catalog views omit row filters. For nonempty 2.x object/column
privilege sets, readers make a fixed number of additional bulk reads: administrator
status and the schema-only SCRIPT result. They retain only a filter-verification
marker, never script text or credentials. If any filtered grant exists, automatic
grant recreation is refused conservatively. Nonadministrator reads retain UNKNOWN
scope and are also refused for recreation; read complete metadata as an administrator.
This prevents a filtered grant from becoming an unrestricted grant.

Schema/Catalog creation supports mutually dependent scalar SQL functions and
procedures, including routine/view cycles and cross-schema cycles. Temporary
declarations raise SQLSTATE 45000 until final bodies are installed. Missing
signatures, opaque definitions and table-returning functions remain explicit errors.
A direct 2.7.4 engine test confirms that ALTER VIEW rejects a view-only cycle and
retains the original executable view. This is an engine boundary rather than a
remaining recreation phase. SQL export now emits terminators, and the HSQL splitter
retains nested compound bodies, cursor declarations, comments and quoted
semicolons. Tests execute both generated operations and exported/reparsed scripts.
Parameterized DML templates retain their existing shared rendering format.

## Verification

```bat
.\gradlew.bat :sqlapp-core-hsql:test --tests "*HsqlRecreationRegressionTest"
.\gradlew.bat :sqlapp-core-hsql:test --tests "*HsqlAdvancedObjectsTest"
.\gradlew.bat :sqlapp-core-hsql:test --tests "*HsqlTextTablesTest" --tests "*HsqlExpressionRecreationTest"
.\gradlew.bat :sqlapp-core-hsql:test --tests "*HsqlCyclicForeignKeysTest" --tests "*HsqlNativeAlterTest" --tests "*HsqlEngineBoundariesTest"
.\gradlew.bat :sqlapp-core-hsql:test --tests "*HsqlTableMigrationTest" --tests "*HsqlRoutineCyclesTest" --tests "*HsqlPrivilegesTest" --tests "*HsqlSqlSplitterTest"
.\gradlew.bat :sqlapp-core-hsql:test --tests "*HsqlTypeReplacementTest" --tests "*HsqlDefinitionIdentityTest"
.\gradlew.bat :sqlapp-core-hsql:test --tests "*HsqlBatchMigrationTest"
.\gradlew.bat :sqlapp-core:test --tests "*ProcedureTest"
.\gradlew.bat :sqlapp-core-hsql:test
.\gradlew.bat :sqlapp-command:test
```

The regression suite executes generated table, domain, identity, function,
procedure, trigger and default-removal SQL and checks the resulting metadata or
data. It also checks XML retention, complete routine definitions, quoted names,
literal preservation, query count and explicit unsupported-change errors.
The HSQLDB module suite also retains its existing bulk insert/upsert, snapshot,
sequence allocation and JDBC tree tests. No external database is needed.
Advanced tests recreate a Schema after XML restoration, restore a cross-schema
Catalog into another empty in-memory database, exercise domain changes in both
directions while dependent columns retain data, and verify distinct types,
synonyms and UPDATE OF filtering. Common tests check XML round trips for every
SQL data-access enum value.
Expression recreation tests check function arguments with dotted specific names,
named sequence identity values, and generated column values after Schema XML
restoration. Text-table tests exercise file rebinding, XML retention, storage and
read-only changes in both directions without losing table rows, unknown binding
IDs and stale source metadata. The shared model tests retain named sequence
schema, name and identity options and literal routine identifiers through XML
and cloning.
Additional tests recreate cyclic foreign keys within and across schemas after
XML restoration, execute valid relationships and assert rejection of invalid
rows/deletes, and verify that plan generation leaves the supplied model intact.
Native ALTER tests execute synonym retarget/rename in both directions without
changing target rows, and unreferenced domain rename/default changes in both
directions. A domain rename rejected for dependent columns leaves its default
unchanged. Shared model regression tests cover cross-schema XML and cloning.

Syntax references: [Schema objects](https://hsqldb.org/doc/guide/databaseobjects-chapt.html),
[Routines](https://hsqldb.org/doc/guide/sqlroutines-chapt.html),
[Triggers](https://hsqldb.org/doc/guide/triggers-chapt.html).
