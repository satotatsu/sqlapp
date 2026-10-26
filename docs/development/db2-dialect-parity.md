# Db2 LUW metadata and DDL recreation

Module dependency/routine continuation (LUW 11.1+): supported earlier published
types in the same module can now be used as ordinary/associative array element
types and row field types, as well as variable types. Row -> array -> nested row
-> variable chains reuse the existing Domain/Type/Constant factories. Additive
protected source/field rendering hooks keep standalone type rules unchanged.
Private, forward, recursive and external module type references fail explicitly.
Creation-time ordering checks now include type-source/row-field references and
routine members; missing/equal timestamps prevent inferred replay when multiple
members may depend on one another. No extra SELECT per member is introduced.

SQL routine members can replay a complete native ALTER MODULE definition from
ROUTINE_TEXT when scope, ADD/PUBLISH visibility, kind, local routine name and an
explicit local SPECIFIC name before LANGUAGE SQL match the member inventory.
Quoted identifiers, nested header comments and default string literals are
handled by a header-only scanner. The body remains unchanged, including internal
semicolons and blank lines. Function/Procedure definitions and schema/path
restoration compose the existing native routine replay helper; resulting member
operations retain the owning Package and ALTER operation type. Routine remarks
are now retained in bulk metadata and require complete parent DDL rather than
being silently lost.

This is deliberately limited to matching implemented LANGUAGE SQL routines with
complete native ALTER MODULE source. Generic CREATE text, body-only text,
prototypes, external/PLSQL/wrapped forms without the required header, generated
support functions and member comments still require complete parent definitions.
Routine references inside opaque SQL bodies are not parsed into a dependency
graph; cyclic/prototype-first module construction requires explicit ordered parent
DDL. Actual catalog text forms and recreation remain subject to the live gate.
Cursor type return-row reconstruction remains open: the DATATYPES header alone
is insufficient to infer its full declaration faithfully.

Nine additional tests cover nested type chains, associative index validation,
wrong/private/forward/self references, bulk ordering and query counts, complete
SQL function/procedure replay, overload SPECIFIC identities, Schema CREATE,
XML/clone, quoted names/comments/default literals and mismatched/incomplete
headers. The existing 11.5/12.1.5 fixture now includes a nested type chain and SQL
function/procedure, then invokes the recreated function; it has only been
compiled and has not been executed against a database.

Java 21: all 313 DB2 unit tests passed (zero failures/errors/skips), and
`:sqlapp-core-dialect-test:compileDockerTestJava` passed. No real database or
Docker tests ran, no dependencies/shared formats changed, and no generated files
were intentionally edited.
References: IBM [array types](https://www.ibm.com/docs/en/db2/11.5.x?topic=statements-create-type-array),
[ALTER MODULE](https://www.ibm.com/docs/en/db2/11.5.x?topic=statements-alter-module)
and [ROUTINES](https://www.ibm.com/docs/en/db2/11.1.0?topic=views-syscatroutines).


Module row-type continuation (LUW 11.1+): module TYPE members with METATYPE F now
retain their fields as a canonical Schema Type model in TYPE_ROW_MODEL. The
additive Db2ModuleMembers.withRowType/getRowType helpers preserve this model
through the existing member snapshot/XML/clone path and return independent
models. Name, kind, row metatype and module schema must agree before recreation;
embedded standalone DDL cannot override the owning module scope.

The reader uses one additional set-based ROWFIELDS query only when module row
types exist: one query for an empty module set, two for ordinary member sets,
and three including all requested row-type fields. Existing TypeColumn reading
retains field ordinal, literal names, built-in type dimensions, string units,
code page, collation and nested type identity. Gaps, duplicate names, missing
fields and row/module scope drift fail explicitly. These WITH UR queries do not
provide a transactionally consistent snapshot during concurrent DDL.

Package CREATE composes Db2CreateTypeFactory through an additive protected
prefix hook to generate ADD/PUBLISH TYPE AS ROW. Supported published row types
can be referenced by later module variables. Unsupported nested/user-defined
field types, constraints, custom collation schemas, member comments and
ambiguous dependency order require complete module DDL. Cursor types, routine
members and generated type-support function association remain open. Custom
field collation schemas are also rejected by standalone row generation instead
of silently losing them; explicit full definitions remain authoritative there.

Five additional module tests and one standalone collation test cover Schema and
direct CREATE, XML/clone, stale model identity/scope, DDL ownership, field order,
missing/duplicate metadata and 20 modules sharing one bulk field query. The
existing 11.5/12.1.5 live fixture now includes a published row type and variable,
and checks fields after drop/recreation; this fixture has only been compiled.

Java 21 validation passed all 304 DB2 tests (zero failures/errors/skips) and
`:sqlapp-core-dialect-test:compileDockerTestJava`. An initial checked-exception
compilation error in the new XML helper was corrected before successful runs.
No external database or Docker tests ran; no generated files were intentionally
edited. Shared model/configuration formats and dialect resolution are unchanged.
References: IBM [ROWFIELDS](https://www.ibm.com/docs/en/db2/11.1.0?topic=views-syscatrowfields)
and [module example](https://www.ibm.com/docs/en/db2/11.5.x?topic=SSEPGG_11.5.0%2Fcom.ibm.db2.luw.apdv.sample.doc%2Fdoc%2Fsqlpl%2Fs-modules-db2.htm).


Module-type continuation (LUW 11.1+): the existing two-query member reader now
retains native type dimensions, nullability, constraints, string units, array
index types, creation path and collation metadata. Package CREATE composes the
existing Domain renderer to emit ADD/PUBLISH TYPE for supported distinct,
ordinary-array and associative-array types. Module variables can refer to an
earlier published supported type in the same module. External, private and
forward references require complete DDL; missing/equal creation timestamps also
prevent inferred ordering for variables with module type references.

Five additional tests cover type/variable XML, clone and Schema CREATE,
distinct constraints, associative array units, bulk metadata/query counts,
dependency ordering and explicit rejection of unsupported components. The type
prefix and variable type-rendering hooks are protected additive APIs; global
variable rules are unchanged. No shared model or dependency changes were needed.

Generated supporting functions are not silently discarded. Catalog snapshots
containing these functions still require complete module DDL until their exact
association/replay is supported. Row/cursor types, routine members, member
comments, custom collation schemas and PL/SQL packages also remain full-DDL
cases. This batch does not close all module reconstruction gaps.

Java 21 verification: all 298 DB2 tests passed with zero failures/errors/skips;
`:sqlapp-core-dialect-test:compileDockerTestJava` passed. Real database and Docker
tests were not executed. No generated files were intentionally changed.
References: IBM [DATATYPES](https://www.ibm.com/docs/en/ias?topic=views-syscatdatatypes)
and [ALTER MODULE](https://www.ibm.com/docs/en/db2/11.5.x?topic=statements-alter-module).


Module-member batch (LUW 11.1+): module headers and all member inventories are
read in two set-based queries for the requested module set (one header query
when no modules exist). MODULEOBJECTS joins condition, variable, routine and type
catalogs, retaining kind, published/user-defined state, literal names, routine
SPECIFIC identity, source/type/default/context attributes and creation times.
Counts must agree between header and member snapshots. Detected count/scope drift, invalid flags and duplicate identities fail
explicitly; these two WITH UR queries do not establish transactionally consistent
metadata under every concurrent DDL change. Aliases remain
Synonym objects and are not duplicated as modules; old dialects are unchanged.

`Db2ModuleMembers.get(module)` exposes an immutable, versioned snapshot stored in
the existing Package specifics. The XML/clone path retains it without changing
shared Schema formats. Package CREATE automatically replays supported user-defined
CONDITION and built-in VARIABLE members with ADD/PUBLISH visibility. Conditions
retain optional SQLSTATE; variables retain type dimensions, string units,
DEFAULT/CONSTANT expression and creation-time schema/path. A small protected
prefix hook composes the existing global-variable renderer/validators; module
variables do not duplicate those rules. New member operations refer to the
owning Package and have ALTER operation type.

Module member source ordering uses catalog creation times. When multiple members
include variable defaults, missing/equal timestamps mark ordering unknown and
prevent inferred replay; a caller can provide an explicitly ordered snapshot
through `Db2ModuleMembers.set(module, members)`. The setter derives MEMBER_COUNT
from the supplied list, defaults an otherwise untyped Package to a SQL module,
and retains other specifics. Existing PL/SQL markers are not inferred or overwritten.
Unknown member attributes,
unsupported scopes/types, custom collation schemas, system-generated members,
member comments, routines, types and PL/SQL packages require complete parent
CREATE/ALTER MODULE definitions. Their inventory/source information is retained;
this is not automatic routine/type/PLSQL member reconstruction. Full definitions
remain authoritative and do not require duplicate member metadata. No incomplete
empty module is returned as a recreation plan for unsupported members.

Seven new tests cover XML/clone/Schema CREATE, visibility, generic conditions,
variable units and defaults, all member kinds and overloaded routine identities,
immutable snapshots, malformed formats/unknown JSON, scope mismatch, incomplete
counts, invalid flags and ambiguous order. The existing bulk test now checks
20 modules with 60 members using exactly two SELECTs. Global variable and module
recreation tests remain included. Early compilation failures were fixed in the
new metadata error handling and test lambda, with no shared API changes.

The existing disposable 11.5/12.1.5 fixture now creates a condition/variable module,
reads it through Schema XML, recreates it and rereads the member count. This is
source/compile coverage only until an explicitly authorized real test run.
Existing APIs remain compatible; the typed snapshot API and protected variable
prefix hook are additive. No new dependency or generated-file edits are intended.

Final Java 21 validation passed `:sqlapp-core-db2:test` with all 293 tests
(zero failures/errors/skips), plus
`:sqlapp-core-dialect-test:compileDockerTestJava` including module recreation
assertions. `git diff --check` passed for affected files. No external database
or Docker tests were executed and no generated files were intentionally edited.

References: IBM [MODULEOBJECTS](https://www.ibm.com/docs/zh/db2w-as-a-service?topic=views-syscatmoduleobjects),
[CONDITIONS](https://www.ibm.com/docs/es/db2/11.5.x?topic=views-syscatconditions),
[VARIABLES](https://www.ibm.com/docs/ja/db2/11.5.x?topic=views-syscatvariables),
[ROUTINES](https://www.ibm.com/docs/en/db2/11.1.0?topic=views-syscatroutines),
and [ALTER MODULE](https://www.ibm.com/docs/en/db2/11.5.x?topic=statements-alter-module).

Logical XML-index batch (LUW 11.1+): the modern index query starts from
SYSCAT.INDEXES, left-joins key columns, and joins SYSCAT.INDEXXMLPATTERNS in the
same bulk query. Logical XVIL indexes retain XML_PATTERN, XML_TYPEMODEL,
XML_DATATYPE, XML_HASHED, XML_LENGTH and XML_SCALE in specifics. Indexes without
key rows are retained as headers instead of silently disappearing. Their missing
components still require an explicit complete CREATE definition for recreation.
Internal XPTH/XRGN/XVIP support indexes are excluded, matching IBM's distinction
between user-created logical indexes and database-managed physical indexes.
Older query resources/read behavior remain unchanged; no N+1 query is added.

The shared Index plus one XML-column reference now recreates supported logical
XML indexes with GENERATE KEY USING XMLPATTERN ... AS SQL ... . Supported SQL
conversion types are DOUBLE, INTEGER, DATE, TIMESTAMP, DECIMAL(precision,scale),
VARCHAR(length), and VARCHAR HASHED. TYPEMODEL Q/R recreates IGNORE/REJECT INVALID
VALUES respectively. Pattern text, including namespaces, quotes and newlines,
is preserved as a SQL character literal. Existing name quoting, uniqueness,
comments and supported ordinary physical options remain composed with the XML
clause. No shared Schema/API format or module dependency is added. The metadata
fields are optional advanced controls for manually modeled XML indexes; normal
reader-to-CREATE use requires no new configuration.

INCLUDE, descending/random/expression keys, CLUSTER, non-XML source columns and
EXCLUDE NULL KEYS are rejected for modeled XML indexes. Missing/unsupported type
metadata requires a full native definition. Repeated identical pattern rows
are coalesced without duplicating columns; conflicting pattern/type/key-order
rows fail explicitly instead of dropping data. Multiple different patterns,
other specialized index families and arbitrary XML pattern semantic validation
remain outside this implementation. VARCHAR lengths must be positive and fit
the catalog length field; actual key/page-size limits remain database validation.
A supplied full native definition remains authoritative and bypasses component
requirements. XML compression ALTER remains excluded from the regular-index
ALTER path. DialectResolver and historical registry mappings are unchanged.

Five focused tests cover 20 logical plus physical indexes in one query,
XML/clone/native SQL, type and invalid-value modes, hashed values, missing and
invalid components, conflicting rows, full-DDL fallback and historical isolation.
The initial focused compilation caught an invalid compound var declaration in
the new test fixture, which was corrected. The existing 11.5/12.1.5 disposable
ALTER fixture now also inserts XML data, reads/recreates an XML index through
Schema XML, excludes internal index rows and rereads the result. These real-DB
assertions are compiled only; no Docker or external database execution occurred.

Final Java 21 validation passed `:sqlapp-core-db2:test` with all 286 tests
(zero failures/errors/skips), followed by
`:sqlapp-core-dialect-test:compileDockerTestJava` with the XML recreation fixture.
An earlier combined invocation was not executed because automatic approval
review could not complete under the usage limit; retrying the same approval
path succeeded. No external database/Docker tests or intentional generated-file
edits occurred. `git diff --check` passed for affected files.

References: IBM [SYSCAT.INDEXXMLPATTERNS](https://www.ibm.com/docs/en/db2/11.1?topic=views-syscatindexxmlpatterns),
[SYSCAT.INDEXES](https://www.ibm.com/docs/en/db2/11.1?topic=views-syscatindexes),
[logical and physical XML indexes](https://www.ibm.com/docs/en/db2/11.5.x?topic=indexing-logical-physical-indexes-over-xml-data),
and [CREATE INDEX](https://www.ibm.com/docs/en/db2/11.1.0?topic=statements-create-index).

Native ALTER batch (LUW 11.1+): modeled sequences support INCREMENT BY,
MINVALUE/NO MINVALUE, MAXVALUE/NO MAXVALUE, CYCLE/NO CYCLE, CACHE/NO CACHE and
ORDER/NO ORDER changes. The generator emits only changed clauses and never
infers RESTART from START, lastValue or a cache allocation boundary. New CACHE
settings require an explicit size of at least 2. Type/name/START/runtime-state
changes, unsupported specifics and changes to authoritative full definitions
still require explicit plans. NEXTCACHEFIRSTVALUE-only drift is recognized as
allocation metadata and produces no DDL; unrelated specifics are not ignored.
CREATE's existing vendor validation is reused for changed sequence settings.

Regular index compression changes use ALTER INDEX ... COMPRESS YES/NO through
direct and Table/Schema paths rather than DROP/CREATE. The canonical compression
property takes precedence over supplemental raw COMPRESSION metadata. Temporary,
specialized and full-definition index compression alterations fail explicitly.
Physical index reorganization is neither generated nor executed; compression
changes take effect physically only after the separately planned rebuild/REORG.
Structural table-index changes retain the existing recreation path. Direct
structural index ALTER requires an explicit recreation plan.

Comments on sequences, indexes, views, triggers and aliases can be updated,
cleared with IS NULL, or set to an empty string without object replacement.
Shared comment generation retains literal identifier quoting and character
escaping. View/trigger/alias structural changes still require replacement plans.
Unchanged diffs produce no SQL. ALTER factories dispatch added/deleted objects
to CREATE/DROP and reject target-only calls that lack an original model, instead
of accidentally returning CREATE SQL. Existing method signatures/XML are retained;
these target-only calls now fail with an actionable message. Older version
registries and resolver mappings are unchanged. No catalog query was added.

New tests exercise direct, Table, Schema and Catalog diffs, XML round trips,
sequence settings and allocation drift, compression toggling, comment clearing,
unsupported forms, and added/deleted/unchanged lifecycle dispatch. The first
compilation attempts exposed generic type issues in the test helper; these were
corrected without production API changes. Real ALTER assertions were added to
the existing disposable-container 11.5 and 12.1.5 test classes; they are compiled
only in this work, never executed against a database or Docker daemon.

The concrete remaining work and completion criteria are tracked in
[Db2 completion gates](db2-dialect-completion.md). The implementation is still
not a claim of complete PostgreSQL parity or live compatibility.

Final Java 21 validation passed `:sqlapp-core-db2:test` with all 281 tests
(zero failures/errors/skips) and
`:sqlapp-core-dialect-test:compileDockerTestJava`, including the new real-ALTER
fixture. The 11.5/12.1.5 fixtures were compiled, not executed.
`git diff --check` passed for affected files. No generated files were
intentionally edited.

References: IBM [ALTER SEQUENCE](https://www.ibm.com/docs/en/db2/11.1.0?topic=statements-alter-sequence),
[ALTER INDEX](https://www.ibm.com/docs/en/db2/11.1?topic=statements-alter-index),
[index compression](https://www.ibm.com/docs/en/db2/11.1?topic=compression-index),
and [COMMENT](https://www.ibm.com/docs/en/db2/11.1?topic=statements-comment).

Index recreation follow-up (LUW 11.1+): DROP INDEX now resolves the catalog
INDEX_SCHEMA rather than assuming the parent table's schema, including when a
Table diff removes an index. Table-diff CREATE/DROP now delegate to the same
registered index factories, retaining expression context and comments on
recreation rather than using the generic inline renderer. Native schema/index names are individually quoted.
Modeled CREATE escapes embedded quotes in index, table/schema, key and INCLUDE
column identifiers while retaining existing ordinary-name rendering. A distinct
index/table schema keeps the table target qualified even when ordinary schema
decoration is disabled. Full native CREATE definitions can be replayed without
a parent table; modeled indexes without a table still fail with a clear error.

Expression text collections are lines, not distinct expressions. Multiline keys
with blank lines are joined as one parenthesized expression; empty/null/NUL text
is rejected through the existing DB2 text helper. FUNC_PATH is restored for full
native definitions as well as modeled expression keys. Native DDL remains
authoritative for specialized indexes. Legacy expression-version restrictions
are retained. No reader query or N+1 access was introduced, and DialectResolver
mappings are unchanged. The dedicated DROP factory is registered only at 11.1+.
The 12.1.5 VECTOR-specific modeled CREATE renderer is otherwise unchanged.

Public API signatures, shared Schema XML and dependencies are unchanged. Changes
are in sqlapp-core-db2 and this document. Tests cover XML/native schema identity,
embedded quote identifiers, multiline expressions, full-definition creation
without a table, function-path restoration, and index removal through Table diff.
Five new index recreation tests passed on Java 21, together with the existing
expression/VECTOR tests. The initial table-diff removal test exposed the generic
inline DROP bypass; the dedicated delegation fixes both DROP and CREATE paths.
The old expression validation fixture now uses NUL as invalid text; multiple
lines are a supported expression representation. Arbitrary specialized index
reconstruction, physical ALTER execution and dependency planning remain separate.
No external database or Docker execution is authorized by this work.

Final Java 21 validation passed `:sqlapp-core-db2:test` (272 tests,
zero failures/errors/skips) and
`:sqlapp-core-dialect-test:compileDockerTestJava` (compilation only).
`git diff --check` passed for the affected files. No external database/Docker
execution or intentional generated-file edits occurred.

References: IBM [DROP](https://www.ibm.com/docs/en/db2/11.1.0?topic=statements-drop)
and [CREATE INDEX](https://www.ibm.com/docs/en/db2/11.5.x?topic=statements-create-index).

Alias recreation follow-up (LUW 11.1+): dedicated factories quote schema, alias,
and target identifiers individually for TABLE, SEQUENCE and MODULE aliases,
including literal dots and embedded quotes. Private/public aliases retain their
kind through XML/clone and emit target-specific COMMENT ON [PUBLIC] ALIAS ...
FOR TABLE/MODULE/SEQUENCE statements. DROP uses the same identity and kind.
Complete multiline CREATE definitions and explicitly delimited scripts use the
existing DB2 definition replay helper; native definitions do not require duplicate
target fields. Missing targets, unsupported remote database links and unknown
modeled alias kinds fail explicitly. Modified aliases require an explicit
replacement plan for dependencies rather than silently issuing another CREATE.
Alias-chain dependency order is still caller supplied; it is not inferred here.

The alias reader now selects the 9.7+ catalog union when the selected Dialect
supports it, even with empty ProductVersionInfo. The legacy path handles null
major/minor version values safely, retains the older table-only query, and still
uses explicit supplied version information when available. This adds no catalog
columns or per-alias SELECTs. Module aliases remain Synonym objects; the modern
module reader continues excluding them, avoiding duplicate module recreation.
DialectResolver mappings and older alias SQL factories are unchanged.

Focused Java 21 validation passed all 13 related tests (four new recreation tests
plus existing public-alias and object-recreation tests). The initial run exposed
the legacy null-version exception; the reader now handles it explicitly. Tests
cover reader/XML/native SQL/comments, three kinds and both visibility modes,
complete-definition scripts, invalid inputs, direct/Schema replacement guards,
and 30 aliases fetched in one query. Public API signatures, shared Schema XML
and dependencies are unchanged. Implementation changes belong to sqlapp-core-db2;
no generated-file edits or external database/Docker executions are intentional.

Final validation on Java 21 passed `:sqlapp-core-db2:test` (267 tests,
zero failures/errors/skips) and
`:sqlapp-core-dialect-test:compileDockerTestJava` (compilation only).
`git diff --check` passed for affected files.

References: IBM [CREATE ALIAS](https://www.ibm.com/docs/ja/db2/11.5.x?topic=statements-create-alias)
and [COMMENT](https://www.ibm.com/docs/en/db2/11.1?topic=statements-comment).

Sequence recreation follow-up (LUW 11.1+): the versioned sequence query resolves
DATATYPEID against SYSCAT.DATATYPES in one bulk join, restricted to non-module
types. This preserves DECIMAL(5/10/19,0) instead of mistaking precision for the
SMALLINT/INTEGER/BIGINT type, and retains schema-qualified distinct type identity.
Unresolved types fail explicitly. Earlier dialects retain the original query
and precision-based representation; DialectResolver mappings are unchanged.
NEXTCACHEFIRSTVALUE is retained in specifics as an allocation boundary; it is
not exposed as the model's last assigned value. Recreating a sequence uses its
configured START, never an inferred restart based on cached allocation state.

The modern factory emits CREATE SEQUENCE without an implicit OR REPLACE,
quotes schema and sequence names individually, and emits COMMENT ON SEQUENCE.
DROP uses quoted identifiers and explicit RESTRICT. Complete multiline definitions
remain authoritative and can use the existing explicit-terminator script path.
Modeled generation preserves integral numeric types, min/max/start/increment,
cycle/cache/order settings and native distinct types. Built-in type-name-only
models support SMALLINT, INT/INTEGER, BIGINT, DECIMAL/NUMERIC. Conflicting model
and named types, nonzero scale, invalid decimal precision, zero increments and
reversed bounds fail explicitly. Validation reads scale before cloning because
the existing shared Sequence XML/clone representation does not retain scale;
Db2 sequences require scale zero. Shared Schema APIs and XML formats are unchanged.

Sequence modifications through direct or Schema diffs require an explicit ALTER
plan preserving live allocation state; they never implicitly restart or replace
the object. Generic dependency planning and automatic live-state transfer are
not implemented. Distinct types must exist before sequence execution; automatic
reordering for arbitrary type/sequence dependencies is not inferred here.
Manual distinct-type models use DataType.OTHER and SEQUENCE_TYPE_SCHEMA/NAME
specifics. No per-sequence SELECT or external DB execution is introduced.

The focused Java 21 run passed all 11 sequence tests, including six new recreation
tests for reader/XML/native DDL, quoting/comments, exact large values, type-name
models, type conflicts, one query for 20 sequences, historical isolation and
Schema diff guards. The initial invalid-scale test serialized the model before
validation; it now checks the original input directly, and validation was also
moved before cloning. Changes belong to `sqlapp-core-db2` and this document;
no dependency version or generated-file edits are intentional.

Final Java 21 validation passed `:sqlapp-core-db2:test` with all 263 tests
(zero failures/errors/skips) and
`:sqlapp-core-dialect-test:compileDockerTestJava` (compilation only).
`git diff --check` passed for affected files. External databases and Docker
were not executed.

References: IBM [SYSCAT.SEQUENCES](https://www.ibm.com/docs/es/db2/11.1.0?topic=views-syscatsequences),
[SYSCAT.DATATYPES](https://www.ibm.com/docs/en/db2/11.1?topic=views-syscatdatatypes),
[CREATE SEQUENCE](https://www.ibm.com/docs/ja/db2/11.1.0?topic=statements-create-sequence),
and [DROP](https://www.ibm.com/docs/en/db2/11.1.0?topic=statements-drop).

Trigger recreation follow-up (LUW 11.1+): the versioned bulk catalog query now
includes valid, invalid and inoperative triggers, retaining the raw VALID state
as well as the model validity flag. EVENTINSERT/EVENTUPDATE/EVENTDELETE preserve
multiple-event triggers instead of a blank event label. Creation-time QUALIFIER
and FUNC_PATH are restored before authoritative full CREATE TRIGGER replay.
Multiline bodies and internal semicolons use the shared DB2 definition replay
path. Trigger remarks are recreated with COMMENT ON TRIGGER, and DROP/comment
names quote schema and trigger identifiers separately, including literal dots
and embedded quotes. No per-trigger query is added. Older readers and factory
registries retain their prior behavior; DialectResolver mappings are unchanged.

Native TEXT remains the complete definition; REFERENCING/WHEN/transition table
and compound SQL syntax are not inferred from partial modeled bodies. A missing
complete definition fails with an actionable error. Modified triggers require
an explicit replacement plan because replacing them can change activation order.
The modern reader retrieves triggers in creation-time order; collection/Schema
CREATE sorts by creation timestamp when every timestamp is present, retaining
input order for ties or when timestamps are missing. Manual snapshots without
timestamps must supply their desired activation order. Cross-schema activation
order and arbitrary dependencies are not globally inferred by Catalog CREATE.
Restoring invalid objects recreates their DDL; it does not force a successfully
recreated trigger back into its original invalid state. Default string-unit
settings and definer/authorization environments are not restored here.

Public API signatures, Schema XML format and dependencies are unchanged. Changes
are confined to `sqlapp-core-db2` and this document. Focused tests cover reader,
XML/clone, CREATE/DROP/comments, invalid and multiple-event objects, historical
query selection, one query for 20 triggers, explicit replacement errors, and
creation-order replay. An initial focused failure was a mock result-set column
case mismatch for remarks; the corrected five focused tests pass on Java 21.
No external database or Docker execution was performed.

Final validation: Java 21, `:sqlapp-core-db2:test` passed all 257 tests with
zero failures/errors/skips. The dependent
`:sqlapp-core-dialect-test:compileDockerTestJava` check was UP-TO-DATE.
`git diff --check` passed for the affected files. No generated files were
intentionally edited.

References: IBM [SYSCAT.TRIGGERS](https://www.ibm.com/docs/ja/db2/11.1.0?topic=views-syscattriggers),
[CREATE TRIGGER](https://www.ibm.com/docs/ja/db2/11.1?topic=statements-create-trigger),
and [trigger activation order](https://www.ibm.com/docs/en/db2/11.1.0?topic=objects-triggers).

Routine replay and multiline-definition follow-up (LUW 11.1+): function and
procedure readers retain QUALIFIER/FUNC_PATH and procedure execution attributes
from the existing bulk routine query. CREATE restores schema/path before replay;
COMMENT uses SPECIFIC identity. Native names, SPECIFIC names, argument names and
table-return field names escape embedded quotes. SQL procedure body generation
preserves DYNAMIC RESULT SETS, SQL data access, determinism and COMMIT ON RETURN.
Invalid catalog values fail explicitly. For requested bodies whose offset is
unavailable/out of range, PL/SQL dialects, or autonomous routines, the reader
retains complete catalog CREATE text instead. Disabling both statement and
definition reading still suppresses both. Earlier dialects retain the original
routine metadata/generation path; DialectResolver mappings are unchanged.

Schema `definition` and `statement` collections contain **lines**, not statements.
The DB2 factories now join these lines before validating/replaying native DDL,
preserving blank lines and internal semicolons. This corrects earlier per-line
replay for tables, views, MQTs, indexes, types, domains, variables, modules and
nicknames. A variable DEFAULT/CONSTANT expression may also span multiple lines.
A multi-operation definition must start with an explicit Db2 CLP terminator
directive, for example:

```sql
--#SET TERMINATOR @
CREATE MODULE APP.M@
ALTER MODULE APP.M PUBLISH PROCEDURE P() LANGUAGE SQL
BEGIN
  VALUES 'semicolon;inside';
END@
```

The existing Db2 splitter handles such scripts. Without this explicit directive,
the entire definition remains one operation, so a SQL routine body is never split
on its internal semicolons. Line boundaries alone never designate operations.
This clarifies earlier references below to "ordered definitions": they mean
explicitly delimited scripts, not one SQL operation per collection element.
The Schema XML format and public APIs are unchanged; scripts previously supplied
as independent unterminated list elements need explicit terminators. No query per
routine/argument was added. External routines and unsupported SQL forms still
require complete executable definitions; this is not general dependency planning.

Validation for this follow-up: Java 21, `:sqlapp-core-db2:test` passed all
252 tests (zero failures/errors/skips). The dependent
`:sqlapp-core-dialect-test:compileDockerTestJava` task also completed successfully
(compilation only). Focused testing first exposed two invalid
test fixtures (NUL text cannot be XML serialized; multiline text must be set as
lines), which were corrected. One compilation run was interrupted after excessive
inference of heterogeneous recursive Schema types; explicitly targeting the
common named-object type allowed the test compilation to finish. No external
Db2 connection, Docker execution or intentional generated-file edits occurred.

References: IBM [SYSCAT.ROUTINES](https://www.ibm.com/docs/en/db2/11.1.0?topic=views-syscatroutines),
[SQL procedures](https://www.ibm.com/docs/en/db2/12.1.x?topic=statements-create-procedure-sql),
and [COMMENT](https://www.ibm.com/docs/en/db2/11.1?topic=statements-comment).

View creation-context follow-up (LUW 11.1+): the versioned view reader preserves
the creation-time default schema and function path, remarks, timestamps, validity,
read-only state and VIEWCHECK alongside authoritative full CREATE text. The
DB2_VIEW_TYPE marker distinguishes ordinary and typed views; typed views require
complete definitions on recreation. Modern view CREATE/DROP and comment targets
quote identifiers individually, including embedded quotes and literal dots.
The ordinary view factory restores that context before replay, preventing unqualified
source tables/functions from being resolved against a different schema/path.
Manually modeled query views can supply ordered column names and LOCAL/CASCADED
CHECK OPTION through VIEWCHECK L/C (N means none); column data types are not
duplicated in the view column list. View and column comments are emitted
separately. Full definitions own their embedded CHECK OPTION and column clauses.
View/MQT definition collections hold lines of complete native DDL. Multiline
DDL is replayed as one operation. Scripts beginning with an explicit
`--#SET TERMINATOR @` directive are split into ordered SQL operations. MQT definitions and
view bodies are validated for empty/NUL text. Modified views require an explicit
replacement plan for dependent objects and privileges rather than an implicit
replacement. Earlier view SQL/registry paths remain unchanged; metadata queries
are set-based with no added per-view SELECTs.
Tests cover reader/XML/CREATE context replay, literal identifiers, comments,
modeled checks, ordered view/MQT definitions, invalid inputs, replacement guards
and 20 views in one query. This does not add arbitrary dependency ordering,
typed-view reconstruction, default string-unit environment restoration, MQT
refresh execution or modeled query-only MQT creation.
References: IBM [view catalog](https://public.dhe.ibm.com/ps/products/db2/info/vr6/htm/db2d0/db2d0341.htm)
and [CREATE VIEW](https://www.ibm.com/docs/en/db2/11.1.0?topic=statements-create-view).
Validation: Java 21, the five focused Db2ViewRecreationTest tests passed, followed
by all 240 tests in `:sqlapp-core-db2:test` with zero failures. The combined
invocation's dependent `:sqlapp-core-dialect-test:compileDockerTestJava` task
initially failed to read shared-core class files; rerunning that task separately
then compiled successfully. Earlier focused failures exposed embedded-quote
handling and a mock remarks-column label; both were corrected. No real database
or Docker tests ran. Existing API/configuration contracts were retained and no
generated files were manually edited.

Row-table physical options follow-up (LUW 11.1+): table metadata retains the
resolved organization/type, clustering kind, append mode, lock granularity and
volatile cardinality without additional catalog queries. Canonical compression
types distinguish VALUE, ROW STATIC, ROW ADAPTIVE and their VALUE combinations.
For a manually modeled table, `setCompression(true)` alone selects ADAPTIVE,
matching Db2's explicit COMPRESS YES default; optional compression type strings
are case-insensitive. A catalog row with an unresolved mode is not guessed.
CREATE emits explicit row organization and compression mode; the prior 10.1
compression hook is bypassed on modern dialects to prevent duplicate clauses.
PCTFREE (0..99), APPEND ON/OFF, LOCKSIZE ROW/TABLE and VOLATILE CARDINALITY are
emitted as separate ALTER statements immediately after CREATE and before indexes
and comments. Catalog PCTFREE -1 remains unspecified. These are creation replay
operations, not an automatic physical-options migration planner.
Complete table definitions own their options and receive no inferred duplicate
ALTERs. Column organization, typed tables, MDC/insert-time clustering and block
insert locking need complete DDL; they cannot silently become ordinary row
tables. Unknown compression modes and malformed physical flags fail clearly
when generating SQL. Older dialects and nickname generation retain their
previous paths. Tablespace, index-tablespace and LOB-tablespace names remain
separate canonical model properties.
Tests cover reader/XML/SQL recreation, all compression combinations, option
ordering, validation, full DDL precedence and 20 table headers with one query.
References: IBM [SYSCAT.TABLES](https://www.ibm.com/docs/en/db2/11.5.x?topic=views-syscattables),
[compression](https://www.ibm.com/docs/en/db2/11.1.0?topic=compression-creating-table-that-uses)
and [ALTER TABLE](https://www.ibm.com/docs/en/db2/12.1.x?topic=statements-alter-table).
Validation: Java 21, `./gradlew.bat :sqlapp-core-db2:test
:sqlapp-core-dialect-test:compileDockerTestJava --console=plain` succeeded on the
final run: 235 DB2 tests, zero failures; dependent Docker compilation was
up-to-date after a successful retry. An earlier dependent compilation failed
because MySQL/SQL Server classes were unavailable; it was not fixed by changing
those modules. The first focused run also required a correction to the mock
ResultSet's statistics metadata. All five physical-options tests now pass.
No external database or Docker tests ran, public APIs changed, or generated
files were manually edited.

IDENTITY recreation follow-up (LUW 11.1+): column CREATE SQL now honors ALWAYS
versus BY DEFAULT, START WITH, INCREMENT BY, MINVALUE/MAXVALUE, CYCLE/NO CYCLE,
CACHE/NO CACHE and ORDER/NO ORDER. Missing generation mode in manually modeled
columns retains the prior ALWAYS default. Invalid bounds, zero increments,
cache size 1/negative sizes, DEFAULT conflicts, fractional decimal identity
types and bounds outside the column's numeric range fail explicitly. The normal
path supports SMALLINT/INTEGER/BIGINT and DECIMAL/NUMERIC precision 1..31 with
scale zero; distinct numeric identity types need complete table DDL.
Reader values outside Long range are retained exactly in DB2_IDENTITY_START,
DB2_IDENTITY_INCREMENT, DB2_IDENTITY_MINVALUE or DB2_IDENTITY_MAXVALUE specifics.
The corresponding common Long-valued identity property is left unspecified,
avoiding wrapped values in Schema XML. SQL uses a modeled Sequence value when
present, otherwise the exact Db2 specific, so ordinary explicit overrides still
take precedence. This exact-value XML preservation covers reader-produced
snapshots; callers authoring large values should use the exact specifics or
complete DDL rather than common Long-valued XML attributes. Existing older
reader/SQL behavior remains unchanged. No catalog queries or public APIs were
added. Current allocation state is not used as START WITH, and identity RESTART
or automatic ALTER planning is not implemented by this change.
Tests cover reader/XML/CREATE replay, descending sequences, all option flags,
31-digit bounds, invalid combinations and 20 identities with one bulk query.
References: IBM [COLIDENTATTRIBUTES](https://www.ibm.com/docs/en/db2/11.1.0?topic=views-syscatcolidentattributes)
and [CREATE TABLE](https://www.ibm.com/docs/en/db2/11.5.x?topic=statements-create-table).
Validation: Java 21, `./gradlew.bat :sqlapp-core-db2:test
:sqlapp-core-dialect-test:compileDockerTestJava --console=plain` succeeded:
230 DB2 tests, zero failures; dependent Docker test sources compiled.
All five focused identity tests passed. No external database or Docker tests
ran, public APIs changed, or generated files were manually edited.

LOB/XML storage follow-up (LUW 11.1+): modern column readers retain COMPACT,
INLINE_LENGTH and COLLATIONSCHEMA alongside existing LOGGED and collation-name
metadata. DB2_LOB_OCTET_LENGTH preserves the catalog byte length independently
of declared Unicode string units through Schema XML. Column type SQL emits
LOGGED/NOT LOGGED, COMPACT/NOT COMPACT and nonzero INLINE LENGTH before nullability
and generation clauses. Modern XML columns retain the native SQLXML model type
and render as XML rather than a length-selected XMLVARCHAR compatibility type.
Native LOB inline lengths are checked against their
byte-size descriptor minimum; XML requires at least 292 bytes; both are bounded
by 32673. Zero means no explicit inline clause. Invalid flags, malformed lengths
and incompatible types fail clearly. A complete table definition remains the
escape hatch for structured types and distinct-LOB storage whose base type
cannot be resolved safely. Old readers retain their previous LOGGED behavior;
explicit modeled replay is limited to 11.1+. No catalog queries were added.
Collation schema is supplemental read metadata here; this change does not emit
a per-column COLLATE clause or recreate database collation settings.
Tests cover reader/XML/CREATE recreation, Unicode byte versus unit lengths,
LOB/XML boundaries and one query for 20 tables.
References: IBM [CREATE TABLE](https://www.ibm.com/docs/en/db2/11.5.x?topic=statements-create-table)
and [SYSCAT.COLUMNS](https://www.ibm.com/docs/en/db2/12.1.x?topic=views-syscatcolumns).
Validation: Java 21, `./gradlew.bat :sqlapp-core-db2:test
:sqlapp-core-dialect-test:compileDockerTestJava --console=plain` succeeded:
225 DB2 tests, zero failures; dependent Docker test sources compiled.
The initial focused XML test exposed native XML type loss and compatibility
type rendering; both were fixed and all five focused tests then passed.
No external database or Docker tests ran, public APIs changed, or generated
files were manually edited. Storage-option ALTER planning is not added here.

Generated-column/default follow-up (LUW 11.1+): the versioned columns1110 query
uses the current catalog DEFAULT instead of preferring IMPLICITVALUE (the value
recorded when an existing column was added). Earlier readers retain columns.sql.
Generated expression TEXT is mapped from its catalog `AS (...)` wrapper into the
canonical Column.formula and recreates as GENERATED ALWAYS AS (...). Native SQL
also replays IMPLICITLY HIDDEN and GENERATED ALWAYS/BY DEFAULT FOR EACH ROW ON
UPDATE AS ROW CHANGE TIMESTAMP. Row-change columns require TIMESTAMP(6) NOT NULL.
Generated expressions cannot silently coexist with DEFAULT, IDENTITY, temporal
generation or a conflicting generation mode. Missing expressions, system-managed
hidden columns and unknown hidden attributes require complete table DDL rather
than falling back to an ordinary column. The existing temporal model still owns
row-begin/end and transaction-start generation. No per-object queries were added.
Tests cover reader/XML/CREATE recreation, old versus modern default queries,
generation modes, invalid combinations, authoritative complete definitions and
20 generated columns across 20 tables with a single query.
References: IBM [SYSCAT.COLUMNS](https://www.ibm.com/docs/en/db2/11.5.x?topic=views-syscatcolumns)
and [CREATE TABLE](https://www.ibm.com/docs/en/db2/11.5.x?topic=statements-create-table).
Validation: Java 21, `./gradlew.bat :sqlapp-core-db2:test
:sqlapp-core-dialect-test:compileDockerTestJava --console=plain` succeeded:
219 DB2 tests, zero failures, and dependent Docker test sources compiled.
No external database or Docker tests ran, public APIs changed, or generated
files were manually edited. This implements CREATE recreation; arbitrary ALTER
of generated-column expressions remains outside the supported modeled path.

Table-column type follow-up: LUW 11.1+ readers preserve resolved user-defined
type identity as a qualified, individually quoted OTHER type instead of
reclassifying a same-named built-in type. The 10.5+ string-unit reader no longer
overwrites this identity. Native LOB types are retained even at small lengths;
their declared lengths survive Schema XML through the DB2_LOB_LENGTH specific
because the shared XML model omits ordinary length attributes for LOBs. The
specific is updated to the declared unit length for Unicode LOBs.
Db2 column SQL now emits canonical string units (10.5+), FOR BIT DATA for modern
catalog character columns with code page zero, and explicit known LOB lengths.
Incompatible units, missing lengths and unsupported version combinations fail
clearly. Existing VECTOR handling and full table definitions remain available.
No SQL catalog columns or per-column queries were added. Tests exercise reader
to XML to CREATE TABLE recreation, quoted user types, Unicode VARCHAR/VARGRAPHIC,
binary character columns, BLOB/CLOB lengths and one query for 20 tables.
Reference: IBM [SYSCAT.COLUMNS, LUW 11.1](https://www.ibm.com/docs/en/db2/11.1.0?topic=views-syscatcolumns).
The older reader representation is retained; automatic structured/reference
table reconstruction and arbitrary dependency ordering remain limitations.
Validation: Java 21, `./gradlew.bat :sqlapp-core-db2:test
:sqlapp-core-dialect-test:compileDockerTestJava --console=plain` executed
214 DB2 unit tests, zero failures. The combined invocation failed later during
dependent Docker test compilation with unrelated Firebird class-file errors;
the DB2 test task itself succeeded. A separate rerun of
`./gradlew.bat :sqlapp-core-dialect-test:compileDockerTestJava --console=plain`
then compiled successfully without source changes for the Firebird error.
Initial focused failures exposed LOB length omission in XML and small CLOB
reclassification; both were fixed before the successful focused/full runs.
No external database or Docker tests ran, no public API changed, and no
generated files were manually edited.

Function return-type follow-up (LUW 11.1 and later): scalar return metadata now
retains type schema/module, code page, declared string units and locator status
alongside length and scale. Table/row return columns are read in one additional
set-based ROUTINEPARMS query, ordered by schema, SPECIFIC identity and ordinal.
The ordinary modern reader no longer parses CREATE text to discover return
columns; legacy readers and explicit parser calls retain their previous path.
SQL generation shares the argument renderer for qualified user types and native
string units, with complete function definitions still authoritative. Locator
or otherwise unsupported return types require complete DDL. Missing catalog
return columns cannot be reconstructed from a body alone. PL/SQL compatibility
and specialized return types remain outside this modeled path.
Tests cover scalar metadata, native units and qualified types after XML, legacy
parser compatibility, and 20 overloads across two schemas with 40 return columns
using two queries total (function headers plus all return columns).
Reference: IBM [SYSCAT.ROUTINEPARMS](https://www.ibm.com/docs/en/db2/12.1.0?topic=views-syscatroutineparms).
Validation: Java 21, `./gradlew.bat :sqlapp-core-db2:test
:sqlapp-core-dialect-test:compileDockerTestJava --console=plain` succeeded:
210 DB2 tests, zero failures; Docker test source compilation was up-to-date.
The first focused run exposed two explicit-parser regressions; both were fixed
and the nine focused tests and full module subsequently passed. No external
database or Docker test execution, public API change, or manual generated-file
change was performed.

Routine argument type recreation (LUW 11.1 and later): both argument readers
retain the resolved type schema/module/name, length/scale, code page, declared
string units and locator flag in Schema specifics. Native SQL function and
procedure creation reuses the distinct-type/variable type renderer for built-in
arguments, and quotes each resolved user-type identifier separately. For example,
catalog byte length 20 with declared length 5 CODEUNITS32 recreates as
`VARCHAR(5 CODEUNITS32)`, while a user type named VARCHAR remains qualified.
Schema XML retains these details. No catalog queries were added and legacy
reader resources are unchanged. This change covers input/output arguments;
return-type support is described above. The compatibility-mode PL/SQL renderer
remains separate follow-up work. Locator arguments require complete routine definitions.
SQL input argument DEFAULT expressions are now emitted; output/inout defaults
and defaults on RETURNS ROW functions are rejected instead of being omitted.
The modeled DEFAULT path is enabled on LUW 11.1 and later; use complete DDL for
older versions. Complete routine definitions remain authoritative.
References: IBM [routine parameter catalog](https://www.ibm.com/docs/ja/db2/12.1.x?topic=views-syscatroutineparms)
and [CREATE PROCEDURE SQL](https://www.ibm.com/docs/en/db2/11.1?topic=statements-create-procedure-sql).
Validation: Java 21, `./gradlew.bat :sqlapp-core-db2:test
:sqlapp-core-dialect-test:compileDockerTestJava --console=plain` succeeded:
207 DB2 unit tests, zero failures; Docker test compilation was up-to-date.
An initial test compilation used an incorrect registry accessor, which was fixed
before the focused and complete test runs. No real database or Docker tests ran.

Routine argument identity follow-up: function and procedure argument readers
retain catalog SPECIFIC names literally, including dots and embedded quotes,
without changing the owning schema. This applies to legacy and modern Db2 LUW
readers without changing catalog SQL or public APIs. Regression coverage also
checks 20 overloads with 40 arguments using one query per routine kind.
Validation: Java 21, `./gradlew.bat :sqlapp-core-db2:test --console=plain`:
202 tests, zero failures. No external database was accessed.

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
| Row types (11.1+) | `Schema.types` and TypeColumn retain native field identity, order and dimensions/units. CREATE TYPE AS ROW and DROP TYPE are registered; cursor/structured definitions can be replayed verbatim, with unsupported automatic reconstruction rejected. |
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
explicit TIMESTAMP precision, DECIMAL/NUMERIC, character/graphic, binary and LOB source
types when their declared dimensions and units are known. Local source spelling
and dimensions use the shared dialect type generator, with explicit Db2 LOB lengths
where the shared mapping omits them. Strong types retain STRONG
TYPE RULES; data-type constraints are generated only for weak types. Ordinary arrays
require explicit positive cardinality; associative arrays support INTEGER and VARCHAR
indexes. VARCHAR index lengths use the separately declared index string units when
present, independently of the element type and array cardinality. Catalog CREATE
creates these native types before tables that reference them.
Comments use COMMENT ON TYPE, and explicit direct drops use DROP TYPE.

Missing or ambiguous character/graphic string units, DECFLOAT, nested/user-defined
element types and other unsupported components require complete ordered
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

### Character, LOB and associative-index extensions

The type and variable factories now compose the same package-private
`Db2NativeTypeSql` renderer. Unicode declarations use STRINGUNITSLENGTH instead
of byte LENGTH, with OCTETS/CODEUNITS16/CODEUNITS32 checked against the type family.
FOR BIT DATA requires OCTETS; incompatible units fail rather than being ignored.
LOB declarations retain their catalog length on both the variable and native-type
paths. Type declarations use a space before string units, for example
`VARCHAR(5 CODEUNITS32)`, as in the native CREATE TABLE grammar; the earlier
comma-separated output and its unit-test expectation were corrected.
Associative VARCHAR indexes use ARRAYINDEXTYPE_STRINGUNITS and
ARRAYINDEXTYPE_STRINGUNITSLENGTH when available; null index units retain the
catalog's non-Unicode length semantics. Unknown or missing dimensions fail clearly.

Five additional tests cover catalog-to-XML-to-DDL character types, graphic units,
binary/LOB sources on both factories, VARCHAR index units and invalid combinations.
No queries, public APIs, shared model formats or dependency changes were added.
This remains Db2 LUW 11.1+ coverage; no type USAGE grant syntax from Db2 for z/OS
or Db2 for i is introduced into the LUW dialect.

The native grammar reference is [CREATE TABLE](https://www.ibm.com/docs/en/db2/11.5.x?topic=statements-create-table).

Final Java 21 validation passed `:sqlapp-core-db2:test` (194 tests, zero failures).
The selected command tests passed 10 tests earlier in this batch and were confirmed
UP-TO-DATE on the final run; Docker test compilation was also confirmed UP-TO-DATE.
An intermediate LOB assertion exposed the shared mapping's omitted length and passed
after the Db2-native correction. No external database or Docker tests were executed,
and no generated files were intentionally edited.

## Row, cursor and structured type recreation (LUW 11.1+)

`Schema.types` now has registered Db2 CREATE/DROP/ALTER factories. Row types generate
`CREATE TYPE ... AS ROW (...)` from ordered TypeColumn fields. Fields use the same
native type renderer as distinct types, arrays and global variables, preserving
numeric dimensions, declared character/graphic string units and binary/LOB lengths.
Names and comments use native identifier/literal quoting. A simple shared-model
row type with integer fields requires no vendor-specific field properties.

The 11.1+ Type reader retains native METATYPE, creation/alteration times and excludes
module-member/system-owned objects. Its version-specific column query unions
ATTRIBUTES and ROWFIELDS in one bulk query, preserving field schema/module identity,
ordinal, dimensions, code page, string units and collation metadata. Module members
are excluded in both branches. Twenty types and forty fields require one type query
and one field query; there is no per-type SELECT. Legacy resources remain unchanged.
The ROWFIELDS NULLS and DEFAULT columns are reserved by Db2 and are not interpreted
as independently restorable constraints or defaults.

Row field SQL uses `DB2_TYPE_SCHEMA`/`DB2_TYPE_MODULE` specifics to prevent a nested
or module-owned type from accidentally becoming an unqualified built-in type.
User-defined/nested fields, field defaults/checks/NOT NULL, array dimensions and
unsupported field storage options require a complete parent `Type.definition`.
Cursor and structured types also require complete ordered CREATE TYPE statements;
their member metadata alone does not represent all native options and dependencies.
Definitions are authoritative, so optional fields are not required to duplicate them.
Direct DROP emits DROP TYPE; automatic replacement/removal requires an explicit
native dependency plan. Catalog CREATE places types before routines that use them.

Six focused tests cover bulk query counts, row field identity/order/units, module
isolation, historical query selection, XML/clone, native SQL/comments, full-definition
fallback, change guards and Catalog routine ordering. Public API signatures, shared
Schema XML formats and dependencies are unchanged. The implementation coverage starts
at 11.1; this is not a claim about the product introduction version of these types.

Final Java 21 validation passed `:sqlapp-core-db2:test` (200 tests, zero failures).
All six focused row-type tests passed before the module-wide run. Docker test
compilation and the selected HTML-documentation/schema-drift command tests were
confirmed UP-TO-DATE. No external database or Docker tests were executed, and no
generated files were intentionally edited.

References: [ROWFIELDS](https://www.ibm.com/docs/en/db2/11.1.0?topic=views-syscatrowfields),
[ATTRIBUTES](https://www.ibm.com/docs/en/db2/11.1?topic=views-syscatattributes),
[CREATE TYPE row](https://www.ibm.com/docs/en/db2/11.5.x?topic=statements-create-type-row),
[CREATE TYPE cursor](https://www.ibm.com/docs/en/db2/11.1.0?topic=statements-create-type-cursor).

## Remaining parity gaps

PostgreSQL's general vendor-object coverage is still broader. Federation
external user-mapping repositories, fully automatic complex nickname recreation
and fully automatic function/type mapping reconstruction, automatic routine/type/PLSQL module-member reconstruction and member comments,
security labels, instance-level authorities, the newer schema RCAC
backup/restore authority and method privileges still need separate work.
Automatic reconstruction of unsupported XML forms/text/other specialized indexes, all physical index options,
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
