# Firebird dialect coverage

The comparison target is PostgreSQL's use of the shared Schema model, versioned
catalog readers and database-specific DDL factories. PostgreSQL-only objects
are not Firebird requirements. The [fixed completion criteria and final verification](firebird-dialect-completion.md)
define the supported scope; this is not a claim of identical engine features.

## Final audit scope (2026-10-27)

This pass closes the implementation and verification of the core object families
below. Completion means the listed paths have version-aware implementations and
regression coverage, not that every Firebird or PostgreSQL feature is supported.
The restrictions at the end of this section are explicit exclusions from that
verified scope; they are not hidden behind the successful test count.

| Area | Verified Firebird scope | Boundary |
|---|---|---|
| Resolution and native types | 2.5 / 3.0 / 4.0 / 5.0; version-specific types, precision, quoting | Earlier versions have catalog/SQL unit checks, not native certification; future majors are not certified |
| Tables, columns, domains | Read/create/reread; defaults, nullability, comments, domain references, arrays, GTT and external tables; supported in-place ALTER | Destructive/ambiguous conversions fail; external file access is limited to disposable fixtures |
| Views and constraints | Query plus column aliases, CHECK OPTION, PK/UNIQUE/FK/CHECK recreation, standalone named DROP and enforcement | Dependencies must exist; unsupported constraint options fail; referenced keys are not cascaded implicitly |
| Indexes | Normal/expression/5.0 partial indexes; direction, state, comments, ALTER/REBUILD/ANALYZE | Statistics mean Firebird selectivity, not PostgreSQL histograms; ANALYZE needs modeled index information |
| PSQL, UDR, packages and triggers | Versioned signatures, bodies/complete definitions, comments, SQL SECURITY and event masks; read/recreate/execute paths | External UDR/UDF code must be installed separately; scalar-array UDF execution is not tested |
| Exceptions and BLOB filters | 2.5 / 3.0 / 4.0 / 5.0 complete definitions, XML retention, exception message/comment ALTER and dependency-checked removal/filter replacement | External filter libraries are not deployed or executed; destructive changes require recorded graphs and released dependencies |
| User-created collations | 2.5 / 3.0 / 4.0 / 5.0 external base, comparison flags, attributes/comments, dependent object recreation and dependency-checked removal/unused replacement | Built-ins are not recreated; matching intl/ICU libraries are required; in-use replacement requires explicit staging and character-set defaults remain separate |
| Publication policy | 4.0 / 5.0 database active flag, automatic membership for future tables and published table identities; XML retention and explicit restore SQL | Default restore leaves publication inactive; server transport configuration, replica modes and actual data delivery are outside this SQL metadata path |
| Schema DDL ordering | 2.5 / 3.0 / 4.0 / 5.0 dependency-aware CREATE and object-level ALTER additions, removals and reference changes; circular foreign keys; 3+ package declarations and bodies | Routine/body cycles and ambiguous interface changes require explicit staging; CREATE prerequisites outside a filtered Schema remain caller-supplied |
| Roles and grants | Versioned membership, object/column/routine/DDL privileges and 4.0+ system privileges | Server CREATE DATABASE grants are opt-in; user/password export and ownership reassignment are outside scope |
| DML and maintenance | Bulk/upsert/migration/JDBC-tree tests; rollback-aware DELETE-based truncation; composite-key predicate fallback; safe table DROP | Firebird has no supported tuple-comparison or cascading table-DROP path; dependencies are not removed implicitly |
| Database-file information | 2.0 context fallback and 2.1+ monitoring query; one metadata read; path/alias retention | Not a PostgreSQL tablespace implementation; pre-2.0 has no reader; 2.0 native execution remains unverified |

The final audit corrected inaccurate capability flags: tuple comparisons and
tuple IN are disabled, so shared `SqlSignature` and row predicates use AND/OR
expressions with the same bound values. Table DROP no longer appends unsupported
`CASCADE CONSTRAINTS`; a dependent view prevents deletion. WITH is advertised
from the 2.5 dialect onward. The combined 2.0/2.1 dialect conservatively returns
false because CTEs were introduced in 2.1, although callers may issue valid 2.1
CTEs explicitly. Resolver boundaries and public method signatures are unchanged.

The audit also corrected the shared core fallback itself: composite equality
now binds every component with AND; inclusive comparisons use strict prefix
comparisons and an inclusive final component. For example `(a,b)>=(x,y)` becomes
`a>x OR (a=x AND b>=y)`. Previously its first branch used `a>=x`, admitting rows
below the boundary. Native tuple paths and single-column predicates retain
their existing behavior. This core correction affects dialects using fallback
predicates; focused shared tests and core/command/PostgreSQL regressions accompany
the Firebird native checks. Public APIs and serialized models are unchanged.

The database-file reader no longer queries MON$DATABASE on 2.0. It uses
RDB$GET_CONTEXT('SYSTEM', 'DB_NAME') and reports an unknown creation date there;
2.1+ uses monitoring metadata. File basenames keep intermediate dots, aliases
keep their full name, and a missing identity is an explicit failure.
The pre-2.0 catalog no longer exposes the previously unusable monitoring reader.
Monitoring also requires the server's appropriate ODS/system-table support.

Known exclusions remaining after this pass:

- Native certification of pre-2.5 servers and future major releases.
- Execution of external scalar-array UDF libraries, automatic ownership
  reassignment, security-database user/password lifecycle, and deployment of
  external libraries.
- Server-side replication transport configuration and data delivery, replica
  database modes, operational cutover and publication ownership reassignment.
- PostgreSQL-specific extension, policy, FDW, type/operator and partition
  representations are not treated as Firebird parity requirements.
- Ambiguous renames, unsupported collation/type conversions and changes that
  would require destructive rebuilding need an explicit migration rather than
  an inferred ALTER. Detailed guards are documented below.

## Native exception, BLOB filter and collation definitions (2.5+)

The schema reader now stores exceptions, declared BLOB filters and user-created collations in the shared
Schema's `specifics` under `firebird.schemaObjects.v1`. `FirebirdSchemaObjects.get`
provides typed immutable `FirebirdSchemaObject` values with complete executable
definitions and explicit DROP statements. XML serialization and cloning retain
the versioned payload without changing the shared XML format. Unknown manifest
fields, unsupported format versions and duplicate object identities fail clearly.
Names remain case-sensitive; identifiers and raw string literals are escaped
separately, including messages whose first and last characters are apostrophes.

There are three catalog queries per schema, independent of the number of objects.
`excludeSystemObjects` is applied to exceptions and filters. Collations always
exclude built-ins because those cannot be recreated as user declarations. `readStatement=false` skips
these executable definitions. Pre-2.5 schema readers retain their previous
behavior; this additional extraction is deliberately limited to the tested
2.5/3/4/5 catalog family.

The regular schema CREATE factory emits these definitions and their comments
before modeled domains, tables, procedures, packages and triggers, and emits no unsupported
`CREATE SCHEMA` statement for Firebird's unnamed schema. For example:

```java
var reader = dialect.getCatalogReader().getSchemaReader();
reader.getReaderOptions().setExcludeSystemObjects(true);
Schema schema = reader.getAllFull(connection).get(0);
List<SqlOperation> create = dialect.createSqlFactoryRegistry().createSql(schema, SqlType.CREATE);
List<FirebirdSchemaObject> nativeObjects = FirebirdSchemaObjects.get(schema);
```

Schema ALTER adds native prerequisites before the corresponding shared object
changes. Exception messages use `ALTER EXCEPTION`, preserving the exception's
identity and its callers. All three object kinds support comment changes and
explicit comment removal without DROP/recreation. These paths accept the
canonical CREATE/DROP/optional COMMENT representation emitted by the reader;
other complete DDL shapes require an explicit migration rather than guessed
rewrites. Only the fixed leading CREATE keyword of a validated exception
definition becomes ALTER; its quoted message is retained verbatim.

Deleting native objects and replacing filter/collation definitions require
recorded original and target dependency graphs, including an explicit empty
graph when appropriate. Shared DDL first removes or alters the dependents;
native DROP/replacement runs afterward. A surviving target reference, an
out-of-scope dependent, or a comment-only operation cannot authorize removal.
A replacement filter or collation with target dependents still requires explicit
staging; the planner does not rebuild those dependents implicitly. New native
prerequisites remain first and publication activation remains last.

The bulk dependency reader retains exception and collation providers. The shared
model supplements collation references from domains, table/view columns, routine
arguments and function returns. The existing 3+ package-signature UNION also joins
field/collation catalogs to retain public argument/return collations. Query
counts remain one on 2.5 and two on 3+, independent of object counts. Existing
dependency manifests remain readable; the same version-1 payload now has
additional EXCEPTION, BLOB_FILTER and COLLATION identity kinds, so older binaries
may not consume newly enriched exports.

For a manually staged migration, `FirebirdSchemaObjects.dropSql(schema)` supplies DROP statements in
reverse stored order; dependent routines, tables and domains must be removed first. Ownership and
external library deployment remain separate concerns. Filter declarations can
be recreated without the library being loaded, but this does not certify the
external filter implementation or its execution.

Docker coverage recreates quoted exceptions and filters with comments through
Schema XML and the schema CREATE registry on 2.5/3/4/5. A dependent procedure is
recreated in the same schema plan and raises the restored exception. A direct
exception DROP while the procedure exists must fail. Unit coverage also checks
three queries for 150 objects, strict manifest validation and schema ALTER guards.

Dependency catalog references: [2.5 object types](https://firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref25/fblangref-appx04-dependencies.html)
and [3.0 dependency types and package column](https://www.firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref30/fblangref-appx04-dependencies.html).

Collation extraction uses `FROM EXTERNAL` with the catalog's external base, as
Firebird's isql extraction does. A collation derived from another user collation
therefore does not need its original parent restored first. PAD SPACE/NO PAD,
case and accent sensitivity are emitted explicitly. Specific attributes such as
NUMERIC-SORT and the recorded ICU version are preserved verbatim; unknown flag
bits fail rather than being ignored. Target servers still need matching intl
configuration and libraries. Installing them or changing character-set default
collations is outside this object-recreation path.

Docker coverage reads two related user collations with opposite comparison
flags, a dependent domain, a table with an explicit column collation and an
index. It recreates the full user schema through XML and the regular schema
CREATE factory, then checks case/accent equality, trailing-space behavior and
numeric ordering before and after recreation. Dropping an in-use collation must
fail. Unit coverage checks all eight flag combinations, missing optional
attributes, escaping, unknown flags and the older-version boundary.

Collation references: [2.5 catalog fields](https://www.firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref25/fblangref-appx04-collations.html),
[CREATE COLLATION syntax](https://www.firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref25/fblangref25-ddl-collation.html)
and [isql extraction](https://github.com/FirebirdSQL/firebird/blob/master/src/isql/extract.epp).

## Full Schema CREATE dependencies (2.5+)

The shared collection order creates routines before tables, which cannot restore
a Firebird procedure or function whose body references a table not yet created.
`FirebirdCreateSchemaFactory` now stably orders the operations generated by the
existing object factories using the catalog dependency graph. Object identity
includes kind and exact name, so a function and table with the same name are
distinct. Comments, indexes and constraints remain associated with their owning
object. Native exception/filter/collation prerequisites still precede shared
objects. Foreign keys come from the canonical Schema model and are installed
after the object definitions; package bodies depend on their corresponding headers.

The schema reader retains typed edges under `firebird.dependencies.v1` in Schema
specifics, including XML and cloning. One bulk catalog query handles compiled
dependencies and joins computed columns, expression indexes and constraint
triggers to their owning table or view. On 3+, one additional UNION query reads
public package argument/return declarations: TYPE OF COLUMN and domain references
in package headers are not all present in RDB$DEPENDENCIES. References to packaged
functions/procedures resolve to the package header, not to a same-named global
routine. Private members do not create false public-header dependencies. No
per-object or per-column SELECT is issued. Queries for 2.5 omit package columns;
pre-2.5 readers retain their prior behavior. `readStatement=false` skips export.

User-object recreation should read with `ReaderOptions.excludeSystemObjects=true`;
dependency export then needs no additional configuration. For a manually built
Schema, callers can supply edges with `FirebirdSchemaDependencies.set` when its
routine/body dependencies cannot be inferred from the shared model. Without
catalog edges, the original stable order and modeled relationships remain
available. A filtered Schema must include its needed objects or have
those prerequisites already installed; absent objects are not invented. Cycles
among routine/body dependencies produce an actionable error before any partial SQL
plan is returned. Automatic stub routines and destructive cycle breaking are
not provided. Object-level ALTER ordering is described below. Dependency payload
changes alone do not produce ALTER DDL, and invalid manifests still fail.

### Circular foreign keys

Schema CREATE generates table definitions without foreign keys on private
clones, then installs the keys through the existing constraint factories. This
supports mutually referencing tables without disabling enforcement or changing
input Schema/XML. Primary and unique keys remain in table definitions. Compiled
table-to-table edges corresponding to these modeled foreign keys move to the
constraint phase; unrelated dependencies retain their normal validation.

Schema ALTER uses the same separation for whole-table additions and removals.
Keys owned by removed tables are dropped before ordered object DDL, while keys
owned by new tables are added afterward. Changes to keys on retained tables
still use the ordinary table ALTER factory. Both new tables and a new/existing
table pair can therefore reference each other. A new foreign key cannot refer
to a table removed by the same plan. Publication membership/activation remains
after all constraint additions.

Standalone foreign-key DROP now emits Firebird's `ALTER TABLE ... DROP CONSTRAINT`
through the dialect registry. Explicit index names, descending direction, update
and delete actions, and backing-index comments survive recreation. Backing
indexes are not generated as independent indexes before their constraints.
The existing bulk foreign-key query also reads the backing-index description
into `firebird.indexComment`, without an extra SELECT. Comments on explicitly
named backing indexes restore through standalone constraint CREATE, table CREATE,
CREATE_TEMPORARY and Schema CREATE/ALTER. A comment on an automatically named index is retained,
but recreation requires the caller to supply a stable `firebird.indexName`;
otherwise generation fails clearly instead of guessing the new internal name.
Comment-only table ALTER emits just `COMMENT ON INDEX`, including `IS NULL` for
removal, without dropping/recreating the foreign key. For an installed index it
can use the recorded catalog name. Structural key replacements restore their
comment after adding the replacement constraint.
Both catalog/model `RESTRICT` and `NONE` actions generate Firebird's `NO ACTION`;
CASCADE, SET NULL and SET DEFAULT keep their corresponding clauses. This applies
to inline table constraints as well as standalone constraint DDL.
Ordinary table and constraint validation still runs before a plan is returned,
including external-table restrictions and disabled/deferred constraint guards.
This is a DDL phase separation, not support for deferred constraint enforcement.

Docker coverage reads a table/view/callee/caller chain whose names are in the
opposite order to their dependencies, restores it through Schema XML and executes
the caller. On 3+ it also restores a table-reading function, a function-based
computed column and expression index, a package with TYPE OF COLUMN in its public
header, and a function calling that package. Full recreation and execution are
checked on each engine version, not just isolated object factories.

This uncovered a shared XML issue: IndexType.parse did not accept every enum name
emitted by XML, so `indexType="Function"` became null and a restored expression
index was generated as a normal quoted-column index. Core now recognizes enum
names before its existing vendor alias patterns. XML output, public signatures
and the existing unknown-input behavior are unchanged. Tests round-trip every
index enum value through XML; the correction applies to every dialect using the
shared model, with core/command/PostgreSQL regressions in addition to Firebird.

References: [2.5 dependencies](https://firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref25/fblangref-appx04-dependencies.html),
[3.0 dependencies](https://www.firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref30/fblangref-appx04-dependencies.html)
and [engine package-reference storage](https://github.com/FirebirdSQL/firebird/blob/v5.0-release/src/jrd/met.epp).

## Object-level Schema ALTER dependencies (2.5+)

The normal schema ALTER registry entry now composes the existing object factories
with the same identities and stable topological ordering used by CREATE. It uses
the original graph to remove dependents before their prerequisites, and the
target graph to create prerequisites before new or changed dependents. It can
interleave operation kinds: creating a replacement table, altering a view to
reference it, then dropping the old table is supported without dropping the view
or its callers. Generated SQL bodies are not parsed or reconstructed. Comments,
indexes and constraints retain their owning object's local operation order.

```java
var factory = dialect.createSqlFactoryRegistry().getSqlFactory(before, SqlType.ALTER);
List<SqlOperation> plan = factory.createDiffSql(before.diff(after));
```

Both snapshots should carry their actual dependency graphs when routine/body
references change. `FirebirdSchemaDependencies.set(schema, List.of())` now retains
an explicit version-1 empty manifest, distinguishing known absence of dependencies
from an older artifact with no recorded information. Existing nonempty manifests
and XML remain readable. Relationships already resolved from the shared model,
such as foreign keys, trigger owners, column domains and package body/header
relationships, do not require an extra catalog payload. Opaque expressions still
require recorded dependencies; a legacy snapshot without a graph is not assumed
to have none. Invalid metadata and input snapshots are not silently
discarded, and planning does not modify the input Schema objects.

A removed provider still referenced by a target object is rejected. Known
dependents outside the target plan also prevent inferred removal. Releasing a
compiled dependency requires a structural operation on that dependent and a
recorded target graph; a comment or deleting metadata alone cannot release it.
Remaining cycles among planned object operations fail before a partial SQL plan
is returned; modeled foreign keys use the separate constraint phases above.

Object-level edges do not prove column/index/signature compatibility. Column
removal, rename, type or computed-expression changes, index/key changes, view
output changes, routine argument/return changes, domain type changes and package
header changes require explicit staging when a surviving object still refers to
that provider. The planner does not insert destructive rebuilds or temporary
stub definitions. Ordinary column additions and comment-only changes remain
available. Each underlying ALTER factory retains its existing validation limits.
The publication activation overload still disables publication first and applies
membership/optional activation after the ordered DDL.

Firebird 3+ now has a dedicated `DROP PACKAGE BODY` factory. The generic shared
drop path emitted unsupported `DROP PACKAGEBODY`; the owning registry now uses
the vendor statement and drops the body before its header. Identifier quoting is
preserved, and older dialect reader/registry paths are unchanged.

Docker coverage on 2.5/3/4/5 adds and deletes the full dependency fixture through
Schema diffs, including 3+ package bodies and expression indexes. A separate
retargeting case changes a view to a newly created table before deleting its old
source; the existing caller still executes and the view's explicit grant remains.
Unit checks cover stale/unknown graphs, surviving dependents, interface guards,
explicit empty XML manifests, modeled foreign-key release and cyclic plans.

## Publication policy (4.0+)

`Firebird40SchemaReader` additionally reads the predefined `RDB$DEFAULT`
publication and all its table memberships in one joined query. The active flag,
automatic membership of new tables, and case-sensitive table identities are
stored as `firebird.publication.v1` in Schema specifics. Older readers issue no
publication query; `readStatement=false` skips this policy export. There are
three native-object queries on 2.5/3 and those same queries plus one publication
query on 4/5. Dependency export adds one query on 2.5 or two on 3+, for four,
five and six total auxiliary schema queries respectively, without per-table SELECTs.

`FirebirdPublications.get(schema)` exposes the immutable `FirebirdPublication`.
Schema XML and cloning retain the versioned payload. Duplicate or invalid names,
unknown JSON fields, missing boolean values, unsupported format versions and
unrecognized publication/catalog flag values fail clearly. Table membership is
canonicalized independently of input order.

Ordinary schema CREATE validates this metadata but does not apply publication
policy; the source Schema retains the policy. A changed policy in ordinary schema
ALTER fails with an instruction to use its explicit activation overload, so it
cannot disappear into a generic specifics change. After creating tables on a
fresh target, the standalone publication plan can be applied as follows:

```java
// Use a fresh dedicated target database and a complete target user-table Schema.
List<SqlOperation> objects = dialect.createSqlFactoryRegistry().createSql(schema, SqlType.CREATE);
List<String> policy = FirebirdPublications.restoreSql(schema, dialect);
// Execute objects first, then policy. Policy remains inactive by default.
```

`restoreSql` only returns SQL. It replaces the database-wide membership policy,
so its target must be the intended complete database, not unrelated preexisting
tables. The plan disables publication first, clears existing membership, restores
the automatic-membership setting and exact included/excluded tables, and keeps
publication inactive. Missing published tables or temporary/external tables are
rejected before any SQL is returned. Quoted identifiers are escaped. To restore
the source active state deliberately, use the overload with
`FirebirdPublications.Activation.SOURCE_STATE`; activation, when needed, is last.
Server `replication.conf`, transport paths, replication journals, replica modes,
ownership reassignment and delivery verification remain separate operations.

For a combined ALTER plan, use
`FirebirdAlterSchemaFactory.createDiffSql(difference, Activation.DISABLED)` (or
explicit `SOURCE_STATE`). This composes the shared DDL factories with the same
publication planner: it disables publication before any object changes, then
restores table membership and optional activation after those changes. No edits
to the stored JSON or duplicate schema-diff implementation are required. The
entire target policy and table set are validated before a plan is returned.

Docker coverage on 4/5 checks all four active/auto-enable combinations, full
Schema XML/table recreation, safe inactive restore, explicit source-state
activation, exact exclusions and automatic membership of a subsequently created
table. No replication transport is installed or delivery claimed. The 2.5/3
matrix verifies that existing readers and native-object recreation still work.

References: [publication catalog](https://firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref50/fblangref50-appx04-publications.html),
[membership catalog](https://www.firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref50/fblangref50-appx04-publication-tables.html)
and [publication DDL](https://www.firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref50/fblangref50-ddl.html).

References for the audit fixes:
[table DROP grammar](https://www.firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref50/fblangref50-ddl-table.html),
[predicate grammar](https://www.firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref50/fblangref50-commons-predicates.html),
[2.0 context variables](https://www.firebirdsql.org/refdocs/langrefupd20-get-context.html),
[2.1 monitoring catalog](https://www.firebirdsql.org/rlsnotesh/rnfb210-admin.html).

## Version boundaries

| Server | Dialect | Behavior |
|---|---|---|
| Before 2.0 | `Firebird` | Existing legacy behavior; computed index generation rejected |
| 2.0–2.1 | `Firebird20` | Computed indexes and existing sequence/temporary-table support |
| 2.5 | `Firebird25` | Existing 2.5 metadata and settings support |
| 3.x | `Firebird30` | Native BOOLEAN, PSQL functions, package-aware queries and database-local DDL privileges |
| 4.x | `Firebird40` | INT128, DECFLOAT, time-zone types, precision up to 38 and role system privileges |
| 5.x | `Firebird50` | Existing returning support and conditional index catalog queries; later major versions currently resolve here without native certification |

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
  the newer query retains parameter overrides. A separate 2.5 query reads type
  reference identities without accessing those columns on 2.0/2.1. UTF8
  parameter lengths use character counts rather than byte counts.
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

## Routine type references (2.5+ procedures, 3.0+ functions)

Procedure inputs/outputs and function inputs/returns retain domain, TYPE OF
domain and TYPE OF COLUMN declarations instead of flattening them into scalar
types. This preserves domain checks, nullability and defaults for a direct
domain declaration, while TYPE OF declarations inherit only the type and its
text character set/collation. Local NOT NULL, DEFAULT and COLLATE declarations
remain separate from inherited domain attributes. No per-argument SELECT is
added; the existing versioned argument queries supply the identities and local
attribute values with a collation join.
Firebird 2.5's documented TYPE OF COLUMN input-comparison collation limitation
remains an engine boundary; an explicit local COLLATE is retained.

`FirebirdRoutineTypeReference` exposes a typed immutable reference retained under
`firebird.typeReference.v1` in argument/return specifics. Normal metadata export
and CREATE/ALTER need no extra configuration. For hand-authored models:

```java
var argument = new NamedArgument("AMOUNT");
FirebirdRoutineTypeReference.set(argument.getSpecifics(),
    FirebirdRoutineTypeReference.domain("positive_amount"));
procedure.getArguments().add(argument);
```

`typeOfDomain(name)` and `typeOfColumn(relation, column)` select the other forms.
Setting a null reference removes it. Scalar type/length/character-set fields
remain resolved inspection metadata when a reference is present; the reference
controls the emitted type. In-place ALTER rejects edits to those resolved
fields with an unchanged reference, rather than silently ignoring the requested
change. Change the reference or its provider, or remove the reference to use a
concrete type. Local nullability/default/collation overrides remain available.

Schema CREATE/ALTER infer signature dependencies on modeled domains, tables and
views. Objects therefore precede signatures that reference them, and routine
removal precedes provider removal. A surviving reference prevents provider
removal or an unproved incompatible shape change. Existing guards on complete
definitions, unknown specifics and external callers remain in effect.

Preservation/generation is enabled from the 2.5 dialect for procedures and 3.0
for PSQL/UDR functions. Pre-2.5 metadata queries and legacy UDF mechanisms retain
their prior behavior; the combined Firebird20 resolver does not certify 2.1
reference generation. Unknown declaration mechanisms, malformed manifests and
incomplete identifiers fail clearly. Older Schema XML remains readable; an old
export without the reference cannot recover its original declaration. Newly
enriched exports require the updated Firebird factory to recreate reference
semantics. Existing public method signatures and the shared XML structure are
unchanged.

References: [procedure declaration semantics](https://www.firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref25/fblangref25-ddl-procedure.html)
and [function argument catalog fields](https://www.firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref50/fblangref-appx04-funcargs.html).

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

Table, view and procedure readers retain the catalog owner in `firebird.owner`,
matching the existing role representation. Function, package-header and
package-body readers also retain it on 3.0+. Domain and sequence owners are
retained by the 3.0+ readers as well. Legacy function, domain and sequence
readers do not access the newer owner column; schema-reader selection is tested
on 2.5/3.0/4.0/5.0. Ownership is read from the existing
bulk catalog queries, including when `readStatement=false`; case is preserved
and absent owners remain unspecified. CREATE still assigns ownership to the
executing user: this metadata does not automatically restore ownership or modify
system catalogs. Docker tests check table/procedure owners and view ownership
before and after ALTER on 2.5/3.0/4.0/5.0, plus function and package ownership
on 3.0/4.0/5.0, including package drop/recreate/reread. Domain and sequence
ALTER checks verify ownership on 3.0/4.0/5.0 while 2.5 retains no owner field.
See the catalog definitions for
[relations](https://www.firebirdsql.org/docs/chunk/en/refdocs/fblangref50/fblangref-appx04-relations.html)
and [procedures](https://firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref40/fblangref-appx04-procedures.html),
[3.0 functions](https://www.firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref30/fblangref-appx04-functions.html)
and [packages](https://www.firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref30/fblangref-appx04-packages.html),
[3.0 domains](https://www.firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref30/fblangref-appx04-fields.html)
and [generators](https://www.firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref30/fblangref-appx04-generators.html).

Compilation-state metadata is retained without extra SELECTs. The 2.5+ procedure
and trigger readers and 3.0+ function readers expose `firebird.validBlr` as
`true`/`false`; a NULL catalog flag remains unset (unknown). Pre-2.5 dialect
readers do not access the column, keeping the shared 2.0/2.1 resolver safe.
Although the catalog column was introduced in 2.1, this metadata is currently
exposed only by the 2.5+ dialect readers.
Package/header and package-body readers expose `firebird.validBody` and
`firebird.hasBody` from 3.0 onward. A NULL or zero package-body validity flag is
false; hasBody distinguishes a header without a body from retained invalid body
source. These fields remain available with readStatement=false. They are catalog
observations, not settable DDL attributes, and cannot be changed through diff
ALTER. A true VALID_BLR flag is not proof of executability: not every dependency
invalidation is reflected in that flag. See the official
[VALID_BLR limitations](https://firebirdsql.org/refdocs/langrefupd21-notes-rdbvalid_blr.html)
and [package flags](https://www.firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref30/fblangref-appx04-packages.html).
Docker observes the package transition from a valid body, through header ALTER
with retained but invalid body source, back to valid after body reapplication;
dropping a body is reported separately as hasBody=false.

Routine argument metadata is restricted to the procedures/functions actually
selected by the parent reader. All supported argument catalog query variants
honor the routineName predicate, including standalone argument-reader calls.
Selected names are bound in batches of at most 500, and arguments are grouped by
routine name before assignment instead of repeatedly scanning the full catalog.
An empty routine set issues no argument query. The 1,201-routine unit regression
requires three batch reads and checks names, argument order and unchanged caller
context. These changes add no per-routine SELECTs or public configuration.
The procedure argument reader selects the 3.0+ package-aware query from the actual
server version, excluding packaged members with the same name as standalone
procedures; 1.5/2.0 and 2.1/2.5 keep their respective catalog variants.
Docker checks filtered and missing routines, quoted names, defaults, function
return types and direct argument reads using inherited ReaderOptions.

Constraint generation validates PRIMARY KEY, UNIQUE, FOREIGN KEY and CHECK through
the Firebird factories. Disabled/deferred constraints, unsupported constraint
kinds, and empty CHECK expressions fail before execution in table CREATE,
CREATE_TEMPORARY and table ALTER; column CHECK objects use the same validation.
Standalone constraint CREATE requires an owning table. Foreign keys require
matching non-empty child/parent column lists and support MATCH SIMPLE only.
FULL/PARTIAL are rejected instead of being omitted.

Explicit CREATE_TEMPORARY now emits CHECK and FOREIGN KEY constraints as well as
primary/unique constraints. For foreign keys it requires a modeled temporary
parent (TableType.Temporary or firebird.onCommit metadata), resolves both table
names with the current factory's prefix/suffix functions, and retains parent
column identity even when child/parent column names match. The input Schema
model is not changed. Use per-table prefix/suffix functions when the temporary
tables use different naming rules. Firebird remains responsible for lifetime and
referential-integrity checks at execution. Docker verifies CHECK enforcement,
CASCADE updates/deletes, custom descending FK index names and read/recreate/reread.

IDENTITY requires a scalar SMALLINT/INTEGER/BIGINT or NUMERIC/DECIMAL with scale
zero and precision at most 18. Non-integer types, INT128, high-precision numeric
types, arrays and explicit DEFAULTs fail instead of producing invalid or silently
changed DDL. Existing version boundaries for identity generation modes and
increments remain in force. Docker recreates a NUMERIC(18,0) identity on 3.0/4.0/5.0
and checks generated values and implicit NOT NULL. See the
[Firebird table grammar and identity rules](https://www.firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref50/fblangref50-ddl-table.html).

The dialect implements `SqlType.TRUNCATE` as `DELETE FROM` the actual modeled
table name. Temporary-table prefix/suffix options apply only to
`TRUNCATE_TEMPORARY`, using the same decorated name as CREATE_TEMPORARY.
Both operations use valid Firebird DELETE syntax and normal identifier quoting.
They retain DELETE semantics: triggers run and deletions participate in the
caller's transaction; they do not provide PostgreSQL TRUNCATE semantics.
See [Firebird DELETE](https://www.firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref50/fblangref50-dml-delete.html).
Docker covers the ordinary target alongside a similarly named table, DELETE
trigger effects and rollback, plus temporary deletion rollback and isolation
between two connections using ON COMMIT PRESERVE ROWS.

Named column domains are retained through the existing `Column.domainName`
reference instead of flattened into an independent base type. Base data type,
length and array metadata remain available for value conversion. For a domain
column, `Column.defaultValue`, `notNull` and `collation` describe its local
declaration; inherited attributes belong to `Column.domain` (resolved to the
canonical schema domain when available). For example, effective requiredness
is the column's NOT NULL or its domain's NOT NULL. This intentionally changes
previously flattened metadata and prevents copying an inherited DEFAULT into
a fixed column override. The bulk column query retrieves both sources without
additional per-column SELECTs. CREATE TABLE emits the quoted domain name and
only local overrides, including DEFAULT NULL; array-domain dimensions are not
duplicated after the domain name. Domain CHECK expressions remain attached to
the domain, preserving VALUE tokens inside string literals.

External tables use `TableType.File` and `firebird.external.file`; CREATE emits
EXTERNAL FILE with a quoted SQL string, instead of an ordinary storage table.
Missing paths and incompatible temporary, index/constraint, blob/array,
identity or computed-column configurations fail explicitly. Docker restricts
external-file access to its disposable `/firebird/data` directory and verifies
that file rows remain accessible after DROP TABLE and recreation. No host or
production Firebird configuration is changed.

Object diff ALTER uses `registry.createSql(original.diff(target), SqlType.ALTER)`:

- Packages (3.0+): header statement changes use ALTER PACKAGE and body statement
  changes use RECREATE PACKAGE BODY through the existing CREATE renderers.
  Package comments can change independently without recompilation, including
  explicit removal. Header ALTER preserves or clears the modeled 4.0+ SQL SECURITY
  property; omitting that clause restores the database default. Unknown vendor
  options, renames, body-comment changes and shadowing complete definitions are
  rejected. Package-body remarks mirror the package comment in the catalog and
  must be edited on the Package object. Complete CREATE PACKAGE / CREATE PACKAGE
  BODY definitions are accepted by target-only ALTER; modeled diffs require
  statement instead. Header ALTER retains body source but requires recompiling
  the body before its routines can execute, including default-only and SQL SECURITY
  changes on the tested servers. After every structural header ALTER, execute the
  matching body diff, or `registry.createSql(body, SqlType.ALTER)` when its source
  is unchanged. Header-only packages need no body. Comment-only header diffs do
  not require body recompilation. These factories do not fetch a missing body.
  RECREATE BODY can also create a missing body.
  See [package ALTER](https://www.firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref50/fblangref50-ddl-package.html)
  and [body replacement](https://www.firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref50/fblangref50-ddl-package-body.html).
- Sequences: explicit `lastValue` changes use SET GENERATOR on all versions,
  including zero and negative current values; 3.0+ increment changes use ALTER
  SEQUENCE INCREMENT BY. Comment-only diffs emit only COMMENT ON GENERATOR,
  so a stale snapshot cannot reset a concurrently advanced sequence. Values and
  increments must fit signed 64-bit integers; increments cannot be zero or null.
  Removing lastValue, renaming and changing start metadata or unsupported options
  are rejected. START/RESTART has different semantics across engine versions and
  cannot consistently change stored start metadata; use lastValue for an explicit
  current-value change. The existing target-only ALTER entry point still sets the
  current value from lastValue (falling back to startValue); increment and comment
  changes require a diff. Missing values now fail instead of emitting no SQL.
  This corrects the old 4.0+ target-only path, where RESTART WITH interpreted the
  supplied current value as the next value. See the official
  [sequence ALTER semantics](https://www.firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref50/fblangref50-ddl-sequence.html).
- Tables: shared constraint/column/index ordering with Firebird ADD, DROP,
  ALTER COLUMN TO and TYPE syntax. Scalar type/length/scale and domain changes,
  2.0+ SET/DROP DEFAULT, 3.0+ SET/DROP NOT NULL, table/column comments and
  4.0+ SET/DROP SQL SECURITY are supported. Matched column renames take
  precedence over duplicate added/deleted entries in the shared collection diff,
  retaining existing data. Index state changes use ALTER INDEX without rebuilding.
  Unmatched additions and deletions in one diff are rejected because they may
  represent a rename; split renames, type changes and replacements into separate
  diffs when the shared model cannot match the columns unambiguously.
  Table renames, charset/collation changes, vendor-option changes and type changes
  on arrays or identity columns are rejected. Computed-column ALTER retains
  the expression when changing its type, and supports expression-only changes
  without recreating the table. TYPE changes require
  absent collation or a known catalog collation ID 0 (`firebird.column.collationId`)
  to avoid losing non-default comparison rules. Pass a diff for table ALTER.
  ADD DEFAULT governs subsequent inserts; it does not issue an UPDATE to backfill
  existing rows. Docker checks retain the original row and SELECT grant, insert
  with changed defaults, switch a column to a named domain and restore its
  inherited default, alter index state, add CHECK, and set/clear nullability and
  supported SQL SECURITY.

  Generated-column ALTER uses the same `original.diff(target)` entry point.
  Computed columns support expression-only changes and combined `TYPE ...
  COMPUTED BY (...)` changes (verified on 2.5/3.0/4.0/5.0); type-only changes
  explicitly retain the original expression. Renames and comments compose with
  these changes. Blank expressions, regular/computed conversions and computed
  columns with identity, array, named-domain or DEFAULT options are rejected.
  Existing charset/collation safeguards still apply to TYPE changes.

  Identity columns support `SET GENERATED ALWAYS/BY DEFAULT`, `SET INCREMENT BY`
  and `DROP IDENTITY` on 4.0/5.0. Increment changes require an explicit non-zero
  value and do not restart the generator. DROP IDENTITY does not drop the column
  or its data, and cannot be combined with generation/increment changes in one
  diff. Regular-to-identity conversion remains unsupported. Changes to modeled
  initial/current values remain rejected: the configured START WITH value cannot
  be changed on 4.0+ and must not be mistaken for a request to restart a sequence.
  Docker checks keep existing rows, dependent views, computed-column comments
  and SELECT grants; identity checks switch both generation modes, verify
  positive/negative steps and continued values, enforce ALWAYS on explicit
  inserts, then drop identity and insert a manual value.
  References: [2.5 table ALTER](https://www.firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref25/fblangref25-ddl-tbl.html),
  [4.0 computed/identity ALTER](https://www.firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref40/fblangref40-ddl-table.html),
  [5.0 restart semantics](https://www.firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref50/fblangref50-ddl-table.html).
- Domains: scalar type/length/scale, SET/DROP DEFAULT, replacement/removal of
  CHECK, comments and 3.0+ SET/DROP NOT NULL. The domain is retained so dependent
  columns remain attached. Array type changes, collation/charset changes,
  renames and unrendered properties are rejected. A target alone is insufficient
  for domain ALTER: pass a diff so SET and DROP intent is unambiguous.
  Firebird TYPE also resets a text domain's collation to ID 0. The reader stores
  `firebird.domain.collationId`; type/length changes are allowed only when the
  original collation is absent or its catalog ID is known to be 0. Non-default
  or unverified collations fail before execution, requiring explicit domain
  recreation with dependent objects instead of silently changing comparisons.
- Views: 2.5+ ALTER VIEW from the modeled query and column aliases; comments
  can change independently. Query ALTER reapplies column comments (including
  explicit removal) and retains SELECT grants. View types/nullability come from
  the query; independent changes to those column properties are rejected.
  Firebird 2.5 cannot safely rename columns on a view with existing CHECK OPTION
  triggers; that combination is rejected before execution on pre-3.0 dialects.
  3.0/4.0/5.0 CHECK OPTION alias changes and 2.5+ ordinary alias changes are
  verified separately in Docker.
- Triggers: CREATE OR ALTER TRIGGER (1.5+) reuses the CREATE renderer and
  preserves existing permissions. Body, enable state, timing/events, POSITION,
  supported external-module attributes, comments and 4.0+ SQL SECURITY are
  supported. POSITION 0 is emitted explicitly to reset an earlier position.
  Removing explicit SQL SECURITY emits DROP SQL SECURITY. Trigger renames and
  reassignment to another relation are rejected. As implied by the Firebird
  statement, CREATE OR ALTER can create a missing trigger.
- Indexes: ACTIVE/INACTIVE and comments. Constraint-backed indexes cannot be
  deactivated, but ACTIVE can rebuild them. Key, uniqueness, direction and predicate changes are
  rejected instead of being translated into a DROP/rebuild.

Index CREATE accepts ordinary BTree and computed indexes only; unsupported types
and compression, partitioning, tablespace or vector-distance options fail instead
of silently becoming ordinary indexes. Missing table associations produce an
actionable error. Indexes modeled alongside their owning constraints are not
created twice, including explicitly named `firebird.indexName` indexes.
Regular table CREATE composes the index CREATE factory so constraint suppression,
index comments and inactive state are retained on this high-level path as well.
Explicit CREATE_TEMPORARY also composes index CREATE, using a cloned table with
its resolved prefix/suffix name. Indexes target the physical temporary table;
their own names and constraint names are preserved. Index comments and inactive
state are emitted, and named constraint indexes are not duplicated. Table and
column COMMENT ON statements also target the physical name. The caller's Schema
model remains unchanged. SQL SECURITY continues to follow the temporary-table
CREATE as a separate operation, so execute the full returned operation list.
Docker verifies prefix/suffix names, table/column/index comments, initial inactive
state, activation and primary/unique enforcement, plus independent identical keys
in two connections; SQL SECURITY is checked on 4.0/5.0.
`registry.createSql(index, SqlType.REBUILD)` emits ALTER INDEX ... ACTIVE for both
ordinary and constraint indexes. It activates inactive indexes and rebuilds even
already-active indexes without dropping the index or modifying the input model.
REBUILD accepts a named index, not a structural diff. Unique-key violations during
activation remain execution errors; the caller must resolve duplicate data first.
See [Firebird index maintenance](https://www.firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref50/fblangref50-ddl-index.html).

`registry.createSql(index, SqlType.ANALYZE)` emits `SET STATISTICS INDEX ...`
to recalculate selectivity without rebuilding or activating the index. The
index name uses the normal identifier quoting rules; no table association is
required. An inactive or unnamed index and structural diffs are rejected.

For an indexed table returned by `TableReader.getAllFull`,
`registry.createSql(table, SqlType.ANALYZE)` composes those operations for the
modeled active ordinary, expression, partial (5.0+) and constraint indexes.
Names are deduplicated exactly, preserving distinct quoted identifier case.
Inactive ordinary indexes are skipped and stay inactive. A table containing
only inactive indexes produces no operations. A name-only table or a model
without any index information is rejected instead of implying that statistics
were refreshed; provide loaded metadata or analyze a named Index directly.
Missing constraint backing-index names also fail with an actionable error,
and views cannot be analyzed.

Unique/primary and foreign-key readers retain auto-generated physical index
names under the read-only `firebird.catalog.indexName` when they start with
`RDB$`. This supplements `firebird.indexName` for explicitly named indexes.
The value is read in the existing bulk query (50 composite constraints still
require one query per reader), used for statistics, and ignored by CREATE DDL.
Generated names can change on recreation, so read metadata again before
analyzing recreated objects. SQL generation performs no catalog queries and
does not change the input model. Docker covers selectivity refresh after DML,
expression/partial and automatic PK/FK indexes, quoting, retained comments,
inactive indexes and continued PK/FK enforcement on 2.5/3.0/4.0/5.0.
This updates Firebird index selectivity; table/column histograms are outside
its scope. See the official [SET STATISTICS rules](https://www.firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref50/fblangref50-ddl-index.html).

Comment-only diffs avoid recompiling the view or trigger. Modeled structural
diffs reject complete definitions that would shadow the requested changes.
Docker verifies dependent domain columns, default/CHECK behavior, view grants
and CHECK OPTION, index state, trigger execution and granted trigger privileges.

Domain metadata includes character length (separate from UTF-8 byte length),
character set, collation, default, nullability, check, remarks and all array
dimensions. The domain query joins charset/collation catalogs once; array
domains add one bulk dimension query, independent of the domain count. Missing
or non-contiguous bounds fail explicitly. CREATE DOMAIN uses the shared domain
model and Firebird column-type renderer, including precision 38 on 4.0+;
unsupported ON UPDATE/deferred-domain options fail instead of being ignored.
Generated RDB$ field domains are excluded from the default user-domain export.

CREATE VIEW emits the modeled column list, preserving explicit aliases even
when the SELECT expression names differ. The stored query retains WITH CHECK
OPTION. Docker checks verify that an invalid INSERT through the recreated view
is still rejected. View descriptions and column comments are retained.

The existing `SqlType.SET_COMMENT` entry point now supports tables, views,
columns, domains, indexes, triggers, generators, procedures, external/PSQL
functions, packages and roles. CREATE factories append separately executable
comment operations for modeled non-null remarks. Setting remarks to null and
generating SET_COMMENT emits IS NULL. Procedure/function ALTER restores routine
comments along with parameter comments without duplicating a changed comment.
Comments require 2.0+; PSQL function/package comments require 3.0+.

Full sequence metadata reads current values with GEN_ID(name, 0), using quoted
identifiers and projections batched at 500 sequences. This replaces one query
per sequence with one query per batch; empty sets issue no current-value query.
The 550-sequence regression requires two queries, and Docker verifies quoted
names and that metadata reads do not advance current values. Current-value
reads are observations, not a transactionally consistent snapshot across
concurrent sequence consumers.

Sequence CREATE now restores an explicitly modeled `lastValue` with a separate
SET GENERATOR after creating the sequence, before its comment. This preserves
the current counter when recreating metadata read by getAllFull, including zero
and negative values, without replacing 3.0+ stored start/increment metadata.
When lastValue is absent, 3.0+ uses native START WITH behavior (3.0 starts at
startValue + increment, 4.0+ at startValue). Pre-3.0 has no native START WITH:
startValue initializes its current counter, so NEXT VALUE FOR yields one greater
than startValue; explicit lastValue takes precedence. Pre-3.0 permits only increment 1.
All modeled numeric values must fit signed 64-bit integers and increments cannot
be zero. CYCLE, MINVALUE, MAXVALUE, CACHE SIZE, ORDER and non-BIGINT types are
rejected instead of silently ignored. BIGINT is intrinsic and emits no AS clause.
Execute the complete operation list returned by CREATE, including initialization.

Run with Java 25 and the repository wrapper:

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
Verified on 2026-10-27 with Java 25: 231 Firebird unit tests and 13 Docker tests passed,
with no failures or skipped tests. The Docker cases comprise four version
round trips, two existing metadata/sequence tests, six bulk/migration tests
and one JDBC-tree generated-key test. Earlier startup/protocol and SQL failures
were corrected before this successful final run. No external or production
database was accessed.

The final shared-core regression additionally passed 1,232 core tests, 627
command tests and 305 PostgreSQL unit tests. Together with the latest Firebird
results, verification covers 2,395 unit tests and 13 Docker tests. The shared verification tasks were
`:sqlapp-core:test :sqlapp-command:test :sqlapp-core-postgres:test
:sqlapp-core-dialect-test:firebirdCompatibilityTest` with Java 25, offline
dependency resolution and `--no-parallel`, using the temporary output/cache
isolation described below. The first added Docker check needed an explicit
ReaderOptions on its standalone reader; this fixture setup was corrected before
the successful final run. PostgreSQL verification here is unit-only, not access
to an external PostgreSQL database.

The dependency-order and index XML corrections have coverage from all 231 Firebird unit
tests and the Docker matrix, together with core, command and PostgreSQL unit
tests because IndexType parsing is shared. Unchanged suites reused successful
Java 25 results through Gradle's up-to-date checks. Earlier package-header
ordering, expression-index XML and `DROP PACKAGE BODY` failures were reproduced
and corrected before the final successful matrix. The native cases include
whole-schema additions/removals with domains and view retargeting that preserves
callers and grants. Public method signatures were preserved.

The final foreign-key pass ran `:sqlapp-core-firebird:test` with focused filters
for `FirebirdSchemaForeignKeyOrderTest`, `FirebirdConstraintValidationSqlTest`
and `FirebirdObjectAlterSqlTest`, then
`:sqlapp-core-dialect-test:firebirdCompatibilityTest`, which also runs the full
Firebird unit suite. This pass changes only the Firebird dialect and its native
fixtures; core/command/PostgreSQL counts above come from the earlier Java 25
regression. The native cases verify circular-key XML recreation, whole-table
Schema ALTER additions/removals, update CASCADE, delete SET NULL, rejection of
missing-parent rows and restricted deletes, index names/direction/comments,
comment-only ALTER, and temporary-table index comments. Initial native failures
from unsupported `RESTRICT` syntax and missing index descriptions were corrected
before the successful final matrix. Focused test compilation/expectation issues
were also corrected; the final run has no failures or skipped cases.

The subsequent native-object ALTER pass first ran focused
`FirebirdNativeSchemaAlterTest`, `FirebirdSchemaDependencyReaderTest` and
`FirebirdSchemaObjectsTest`, then the complete `firebirdCompatibilityTest` task.
Both the initial and final Docker matrices passed on 2.5/3/4/5; the final run
also includes unused-collation replacement and 3+ public package argument
collation dependencies. It verifies exception-number retention and execution of
the existing caller after message ALTER, comment removal, filter replacement,
case-insensitive comparison after collation replacement, refusal of retained
exception references, procedure-body reference release before exception DROP,
and whole-schema domain/table/package/native-object removal. All 215 unit tests
and 13 Docker tests passed with no failures or skips. External libraries were
declared as fixture metadata only; no filter implementation was loaded or run.

The 2026-10-27 routine-reference pass ran focused `FirebirdRoutineTypeReferenceTest`,
`FirebirdRoutineTypeMetadataTest`, `FirebirdRoutineArgumentsTest` and
`FirebirdProcedureVersionMetadataTest`, followed by the full
`:sqlapp-core-dialect-test:firebirdCompatibilityTest` task with Java 25,
`--offline --no-parallel` and the isolated outputs/cache. The final results are
227 unit tests and 13 Docker tests, with no failures or skipped cases. Existing
core/command/PostgreSQL totals above remain from the earlier shared regression;
this pass does not change shared core code or dependencies. Native fixtures
exercise direct domain checks/NOT NULL versus TYPE OF domain/column behavior,
procedure input/output and function input/return references, local default and
NOT NULL overrides, absence of an inherited default on TYPE OF, explicit text
collation and Unicode returns. XML-based Schema CREATE and Schema ALTER
addition/removal preserve these behaviors. An in-place procedure reference
change switches between domain validation and type-only behavior. Initial
boundary-test expectations and a duplicate collation assignment in the Firebird
reader were corrected before the successful final run. No external/production
database was accessed, and temporary verification scripts/logs were removed.

The final bounded-scope close-out also executes standalone named PK/UNIQUE/FK/CHECK
DROP/recreation on all four engines, including a referenced-key rejection and
post-restore enforcement. Generic invalid DROP paths were replaced with the
owning-table statement, and NUL identifiers now fail validation. The final
core/command/PostgreSQL/Firebird unit tasks were explicitly rerun with isolated
outputs/cache; none of these four test tasks reused an up-to-date test result.
The fixed acceptance checklist and complete results are in the
[completion record](firebird-dialect-completion.md). Remaining staged-migration
and operational boundaries are documented exclusions, not pending automatic
rebuild implementations in this scope.

Initial runs encountered missing class files and one test report write failure
while concurrent builds shared the checkout during the ownership extension. The final verification
uses a temporary Gradle initialization script to set each project's build
directory before plugin application (`gradle.projectsLoaded`) and a separate
`--project-cache-dir`, both under a dedicated temporary directory. Separating
only output directories is insufficient because shared Gradle project state
can still track the earlier task outputs. The temporary script is removed
after verification; repository build settings and dependency versions are
unchanged.

Sequence ALTER coverage resets quoted generators to zero and negative current
values, executes a dependent procedure, confirms 3.0+ negative increments and
USAGE grants survive, and verifies a comment-only diff from a stale snapshot
does not reset an advanced generator. The native fixture uses getAllFull for
current values; ordinary getAll retrieves catalog metadata only.

Package ALTER coverage on 3.0/4.0/5.0 changes a defaulted argument, reapplies the
retained body, replaces its implementation, executes a dependent view and checks
the original EXECUTE grant. It also removes package comments without recompiling
and sets/changes/clears SQL SECURITY on 4.0/5.0, recompiling the body after each
header change. Body comments are edited through the package header only.

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

The closed verification scope and concrete exclusions are listed in the final
audit table above. A complete `definition` can preserve executable DDL supplied
by callers, but does not imply automatic extraction or restoration of its
external dependencies. The procedure-argument boundary was checked against the official
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
Object ALTER references: [domain attributes and restrictions](https://www.firebirdsql.org/file/documentation/chunk/en/refdocs/fblangref50/fblangref50-ddl-domn.html),
[CREATE OR ALTER TRIGGER introduced in 1.5](https://www.firebirdsql.org/refdocs/langrefupd15-create-or-alter-trigger.html).
