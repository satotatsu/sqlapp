# Vertica dialect enhancement

The next local enhancement target after MariaDB is Vertica. The Java/module
spelling remains Virtica/sqlapp-core-virtica to preserve existing APIs.

Initial sequence metadata changes apply to the existing shared reader selected
across supported versions (7.x onward); resolver/version classes are unchanged.
Sequences are explicitly represented as BIGINT. Cache sizes use exact Integer
conversion and reject negative values rather than silently narrowing catalog
values. Metadata retrieval remains one catalog query for the requested set.

CURRENT_VALUE is stored in specifics as CURRENT_VALUE instead of lastValue:
it represents distributed allocation state, not a reliably observed last-issued
value. START is not inferred because the catalog does not expose original start
metadata. Generated CREATE can recreate a definition using the server default
start but does not reproduce the original start or runtime position. This is a
known recreation limitation pending further investigation.

Public signatures, Schema XML format and dependencies are unchanged. Field
representation for the allocation value changes intentionally. Local tests cover
metadata/XML/DDL and cache boundaries; no Vertica server/container was accessed.

Reference: [Vertica 11.1 SEQUENCES catalog](https://docs.vertica.com/11.1.x/en/sql-reference/system-tables/v-catalog-schema/sequences/).

Next audit: projection/index metadata, native types, broader identifier handling
and version-specific catalog boundaries. Live qualification
requires a separately authorized disposable target.

## Stored-object pass

- SQL function FUNCTION_DEFINITION is a RETURN body, now stored in statement
  with separately modeled arguments/return type. CREATE uses Vertica's singular
  RETURN type clause and AS BEGIN / RETURN / END form. Complete executable
  definitions remain authoritative. UDx or missing-body cases require full DDL.
- Function/procedure argument splitting respects quoted identifiers and SQL
  strings, doubled quotes and nested type precision; malformed lists fail.
  Quoted argument names are decoded into the model and quoted again for SQL.
  Argument direction is emitted once instead of both before and after the type.
- 11.1.1+ USER_PROCEDURES has no body column. Procedure CREATE therefore requires
  complete executable definition instead of synthesizing incomplete SQL.
- View CREATE retains explicit output names and LOCAL TEMPORARY state. Queryless
  views require complete definition. The final view pass below adds schema
  privilege inheritance replay on the 11.1.1+ resolver path.
- Blank definitions, blank queries, missing function argument/return types and
  unsupported function bodies fail with actionable errors.

These changes remain in this module and preserve resolver/public signatures,
dependencies and XML syntax. The function body representation intentionally
changes: old XML with a RETURN body in definition should be re-read or moved to
statement. Complete CREATE definitions in definition retain their behavior.
The existing reader version ranges remain unchanged; stored SQL function syntax
has been checked against the vendor reference, not a running server.

References: [USER_FUNCTIONS](https://docs.vertica.com/24.3.x/en/sql-reference/system-tables/v-catalog-schema/user-functions/),
[CREATE FUNCTION (SQL)](https://docs.vertica.com/23.4.x/en/sql-reference/statements/create-statements/create-function-statements/create-function-sql/),
[USER_PROCEDURES](https://docs.vertica.com/23.3.x/en/sql-reference/system-tables/v-catalog-schema/user-procedures/).

## Text index pass

CREATE now uses CREATE TEXT INDEX rather than the generic CREATE INDEX. This
applies to all existing Vertica resolver paths (7.x onward); resolver and catalog
queries are unchanged. Use IndexType.FullText and columns in vendor order:
unique ID first, text second, then optional payload columns. The attached Table
provides source identity; INDEX_SCHEMA_NAME, when present, provides the separate
index schema. Identifiers are quoted. Both direct index CREATE and table CREATE
use the same factory. The server still validates primary key and projection
requirements. No dependencies or public signatures were changed.

The bulk TEXT_INDICES reader exposes only TEXT_COL, not the original ID/payload
list or tokenizer/stemmer input signatures. Its results remain useful metadata
but cannot reproduce the original text index. CREATE rejects incomplete columns,
custom tokenizer/stemmer metadata, ordinary/unique indexes and unsupported
INCLUDE/WHERE/descending-column options instead of emitting misleading SQL.
Preserve vendor-exported DDL separately for these cases. No new catalog queries
or per-index queries were added. Local tests cover one query for multiple indexes,
cross-schema identity through table XML, old/current resolver paths and both SQL
generation entry points. No Vertica database was accessed.

Projection metadata/recreation remained outside the initial index pass. Projections are physical
objects with sorting, segmentation and buddy/KSAFE state; they are not ordinary
indexes. Vendor EXPORT_OBJECTS can export a scoped object set in one top-level
SELECT, including projection DDL. The shared model extension described below now
provides a faithful independent object; bulk export handling remains pending, rather than a per-projection query or
an invented CREATE INDEX mapping.

References: [CREATE TEXT INDEX](https://docs.vertica.com/26.2.x/en/sql-reference/statements/create-statements/create-text-index/),
[TEXT_INDICES](https://docs.vertica.com/26.3.x/en/sql-reference/system-tables/v-catalog-schema/text-indices/),
[EXPORT_OBJECTS](https://docs.vertica.com/11.1.x/en/sql-reference/functions/meta-functions/catalog-management-functions/export-objects/).

## Native type and identity pass

The shared Vertica type registration now supports NUMERIC/DECIMAL precision and
scale up to 1024, retaining defaults 37/15. Previously the declared maximum was
31, below even the default precision. LONG VARBINARY is registered as its native
shared type with maximum length 32000000. LONG VARCHAR now retains explicit
length in CREATE instead of discarding it. Both long types default to 1048576
bytes. The existing legacy LONG VARCHAR FOR BIT DATA alias is retained.
These registrations apply through existing 7.x-and-later resolver paths; tests
exercise old/current paths. Vendor documentation was checked for 11.1/current
releases; historical servers have not been qualified in this pass.

Identity column CURRENT_VALUE now follows sequence handling: it is preserved as
specifics CURRENT_VALUE, not identityLastValue, because it reflects distributed
allocation. Original START and last-issued values are not inferred. Cache counts
use exact conversion, reject negative/overflow values and retain SQL NULL.
Column reads still use one bulk query and the existing 24.1 comment-catalog
boundary. XML syntax, public signatures, dependencies and resolver are unchanged;
type output and identity metadata representation change intentionally. Local
tests cover type/XML/DDL sizes, bulk identity metadata and cache boundaries.

References: [NUMERIC](https://docs.vertica.com/11.1.x/en/sql-reference/data-types/numeric-data-types/numeric/),
[Long data types](https://docs.vertica.com/11.1.x/en/sql-reference/data-types/long-data-types/),
[SEQUENCES](https://docs.vertica.com/11.1.x/en/sql-reference/system-tables/v-catalog-schema/sequences/).

## Routine script splitting pass

The module splitter recognizes CREATE [OR REPLACE] FUNCTION bodies lexically,
including AS BEGIN on the same line and complete one-line functions. Nested CASE
expressions are counted independently of the outer BEGIN/END. Quoted identifiers,
strings and comments do not contribute keywords or delimiters. Header comments
are supported. Ordinary DDL is not scanned forward into a following function.
Generated SQL functions now round-trip through the script splitter as one
statement followed by independently split statements.

Vertica's untagged $$ string literals are also protected by the shared tokenizer's
quoted-text extension, including stored-procedure bodies containing internal
semicolons. Unterminated dollar strings or function blocks fail explicitly. This
is SQL-function block handling, not a general unquoted PL/vSQL control-flow
parser; stored-procedure bodies should remain quoted. No new public settings,
shared-core changes, catalog queries or database operations were introduced.
Tests cover generated functions, inline/multiline forms, nested CASE, quoting,
comments, following DDL and malformed input. Existing resolver paths are unchanged.

References: [Creating SQL functions](https://docs.vertica.com/26.2.x/en/extending/user-defined-sql-functions/creating-user-defined-sql-functions/),
[Dollar-quoted string literals](https://docs.vertica.com/26.3.x/en/sql-reference/language-elements/literals/string-literals/dollar-quoted-string-literals/).

## Constraint pass

Foreign-key aggregation now includes the source table in its identity. Two tables
in one schema can retain their same-named composite foreign keys independently,
including ordered source/reference column pairs and referenced schema. Shared
column/owner assembly is reused after the single bulk query; there are no new
per-constraint queries. This correction applies across existing resolver paths.

For Vertica 7.2+ CHECK metadata uses TABLE_CONSTRAINTS.PREDICATE, joined to TABLES
for schema identity, rather than trying to reconstruct an expression from column
names. This also includes checks without catalog column rows. The older reader
query is retained for pre-7.2 resolver paths and does not reference PREDICATE.
PK, UNIQUE and CHECK CREATE emit explicit ENABLED/DISABLED on 7.2+ so target-server
defaults do not alter the modeled enforcement state. Direct ALTER TABLE ADD and
table CREATE use the same factories. Pre-7.2 key syntax is retained and CHECK
generation is rejected. Missing CHECK expressions fail instead of producing
empty CHECK clauses; old XML without an expression should be re-read or supplied
with an expression.

Local tests cover same-named composite foreign keys in one query, referenced
schema and XML/DDL, CHECK expression retrieval in one query, both enforcement
states and generation entry points, and the pre-7.2 boundary. Public signatures,
XML syntax, dependencies and resolver classes are unchanged. Vendor catalog and
syntax references were checked against 11.1; historical/live server execution
is still unverified. No external database was accessed.

References: [TABLE_CONSTRAINTS](https://docs.vertica.com/11.1.x/en/sql-reference/system-tables/v-catalog-schema/table-constraints/),
[Table constraints](https://docs.vertica.com/11.1.x/en/sql-reference/statements/create-statements/create-table/table-constraint/),
[FOREIGN_KEYS](https://docs.vertica.com/11.1.x/en/sql-reference/system-tables/v-catalog-schema/foreign-keys/).

## Table attribute pass

Bulk table metadata retains IS_TEMP_TABLE and IS_FLEXTABLE independently, so a
temporary flex table does not lose its temporary nature when tableType is Flex.
Normal CREATE emits FLEX for a Flex model and PARTITION BY for a nonblank native
PARTITION_EXPRESSION. The partition expression is vendor SQL preserved verbatim;
projection sort/segmentation and partition grouping/active-count options are not
reconstructed in this pass. Local temporary CREATE accepts explicit specifics
COMMIT_ACTION DELETE/PRESERVE, rejecting other values; when absent, existing
TableOptions defaults remain in effect. The catalog does not reliably expose this
setting in TABLES, so it is not inferred from IS_TEMP_TABLE.

Ordinary CREATE rejects temporary models, pointing callers to CREATE_TEMPORARY.
It does not guess original global/local scope. Temporary flex tables and Flex
models containing automatically generated __raw__/__identity__ columns require
separate vendor DDL; those columns are not silently stripped or duplicated.
Nonblank TABLE_DEFINITION identifies an external COPY definition and is rejected
by both native/local-temporary paths to avoid converting it to an ordinary table.
Fresh Flex models without automatic columns can be generated; faithful recreation
of catalog Flex tables remains limited.

The shared TableType parser now checks exact enum/display names before legacy
patterns: Flex previously matched File's F.* pattern during XML reading. Existing
short forms retain their fallback behavior. This required a small sqlapp-core
fix because Table XML parsing is shared, not dialect-owned. No signatures or XML
syntax changed; Flex now round-trips correctly. Tests cover all TableType values,
legacy abbreviations, table metadata/XML, generated Flex/partition SQL, commit
actions and rejection of incomplete special-table representations.

References: [TABLES](https://docs.vertica.com/11.1.x/en/sql-reference/system-tables/v-catalog-schema/tables/),
[CREATE FLEXIBLE TABLE](https://docs.vertica.com/11.1.x/en/sql-reference/statements/create-statements/create-flexible-table/),
[CREATE TEMPORARY TABLE](https://docs.vertica.com/11.1.x/en/sql-reference/statements/create-statements/create-temporary-table/).

## Native name and routine argument pass

Function argument reading first attempts to resolve the entire text as a known
native type before splitting a name/type pair. Unnamed UDx arguments such as
DOUBLE PRECISION, LONG VARCHAR, LONG VARBINARY and time-zone types retain their
complete type instead of becoming an argument named DOUBLE/LONG/TIME. Named and
quoted-name arguments continue through the existing parsing path; unknown native
types retain the previous fallback behavior. No per-function queries were added.

BIGINT, DOUBLE and DOUBLE PRECISION are retained alongside their registered
aliases. Previously the alias matcher replaced and lost those native names.
TIMESTAMP aliases now extend the default matcher rather than replace precision
and WITHOUT TIME ZONE support. Tests cover native/alias mappings and XML on
old/current resolver paths, TIMESTAMP(6) DDL, and both named and unnamed routine
arguments including long type lengths. These changes stay in sqlapp-core-virtica;
public signatures, dependencies, resolver paths and XML syntax are unchanged.
No Vertica server was accessed. UDx executable recreation still requires full
vendor CREATE DDL; recognizing its argument types does not remove that limitation.

## Final view and schedule metadata pass

Permanent views emit INCLUDE/EXCLUDE SCHEMA PRIVILEGES when INHERIT_PRIVILEGES is
available on the 11.1.1+ resolver path. This is a conservative implementation
boundary, not a claim that the feature first appeared in that release; older
resolver output is retained. Complete definitions remain authoritative. Local
temporary views are emitted without schema qualification or inheritance clauses,
matching their separate syntax.

Scheduled-event metadata (12.0.4+) retains TRIGGER_ENABLED and SCHEDULE_ENABLED
independently, in addition to the effective Event.enable conjunction. CRON maps
to Recurring. DATE_TIME_LIST and unknown formats retain their raw specifics and
leave EventType unset rather than pretending they are a single OneTime event.
The reader still uses one joined query. Schedule/trigger CREATE is not implemented:
the shared Event abstraction does not fully model the separate schedule object,
procedure identity/signature, ownership and lifecycle.

Tests cover privilege true/false, older/current resolver boundaries, local view
syntax, complete-definition precedence, all four enable combinations, raw date
lists and XML preservation. No APIs, dependencies or catalog SQL were changed in
this pass. See [ENHANCEMENT_STATUS.md](ENHANCEMENT_STATUS.md) for unresolved work.

References: [CREATE VIEW](https://docs.vertica.com/24.3.x/en/sql-reference/statements/create-statements/create-view/),
[CREATE LOCAL TEMPORARY VIEW](https://docs.vertica.com/25.3.x/en/sql-reference/statements/create-statements/create-local-temporary-view/),
[USER_SCHEDULES](https://docs.vertica.com/24.3.x/en/sql-reference/system-tables/v-catalog-schema/user-schedules/).

## Independent Projection model and HTML

Projection is now a schema-owned shared object, with source table identity,
ordered output/sort names, statement, definition, specifics, XML and diffs. It
does not inherit Table/View/Index. See [the model guide](../docs/schema/projections.md).
The optional ProjectionReader extension defaults to null for existing dialects.
Vertica 11.1.1+ opts in to one joined bulk projection/column query, collapsing
same-named node rows and retaining differently named buddies as separate named
catalog objects. Source tables and sort positions are preserved; a SELECT body
or full CREATE is not guessed from catalog flags. CREATE requires authoritative
definition, and catalog generation puts projections after tables across schemas.
Live export/recreation remains unverified. Named DROP and rename-only ALTER are
described in the lifecycle pass below.

HTML adds a separate Projection list/detail menu, schema links and resolved
source-table links, including output/sort names and statement/definition/specifics.
No projection appears as a duplicate table or ER entity. Local model, bulk reader
and generated-HTML tests cover XML, clone/diff, missing/ambiguous sources, query
count, node duplicates, ordering, dependency order, links and escaping.
No Vertica database or container was accessed.

## Projection lifecycle and bulk export

Projection DROP uses the modeled name, optional IF EXISTS and the existing
schema-decoration setting. It never substitutes PROJECTION_BASENAME, adds CASCADE,
or silently drops all buddy projections. Server K-safety/superprojection/constraint
restrictions still apply. ALTER supports an original/target name-only difference;
layout, definition, source and other changes fail with a redesign error. It does
not infer physical replacement. Direct ALTER without a difference fails clearly.

`VirticaDdlExporter.exportObjects(connection, objects)` retrieves a requested set
using one top-level `SELECT EXPORT_OBJECTS('', ?, false)` with a bound scope.
Identifiers are quoted, duplicates removed and schema/object names required.
Empty input does not call the server. Commas in identifiers and routine targets
requiring overload signatures are rejected. The exporter closes its statement and
result set, leaves the caller's connection open, performs no server file write,
and does not execute returned DDL. Blank, missing or multiple result rows fail.

The returned value is the whole dependency-preserving script, not an individual
Projection definition. The assignment API below extracts per-object CREATEs;
assigning the full export to each projection would duplicate dependency creation.
Local JDBC mock tests cover query count, scope binding, quoting, empty/invalid
input, result validation and resource ownership. SQL tests cover named buddy DROP,
deleted-object dispatch, quoting, pure rename and rejection of layout changes.
No real Vertica connection was used; the EXPORT_OBJECTS prepared-statement path
requires live qualification before claiming server compatibility.

References: [DROP PROJECTION](https://docs.vertica.com/24.2.x/en/sql-reference/statements/drop-statements/drop-projection/),
[ALTER PROJECTION](https://docs.vertica.com/24.2.x/en/sql-reference/statements/alter-statements/alter-projection/),
[EXPORT_OBJECTS](https://docs.vertica.com/11.1.x/en/sql-reference/functions/meta-functions/catalog-management-functions/export-objects/).

## Atomic Projection definition assignment

`exportProjectionDefinitions(connection, projections)` exports the set once,
validates all matched CREATE statements, assigns each Projection.definition and
returns the full dependency script. `applyProjectionDefinitions(script, projections)`
performs the same assignment offline. Neither executes the script. Missing,
duplicate, ambiguous and malformed candidate statements fail before any model
mutation. Duplicate target identities fail before querying in the high-level path.

The existing dialect splitter separates statements, respecting literals, dollar
quotes and SQL function blocks. A header lexer reads quoted/escaped identifiers,
schema/catalog qualification and comments. Column lists and basename hints remain
verbatim in the matched definition; a basename is never substituted for a buddy
name. Each definition receives a terminator on a new line so trailing line
comments do not consume it. Unqualified names, session-context commands and
follow-up ALTER PROJECTION statements are rejected instead of discarding required
execution context. This is header/identity validation, not a complete SQL parser.

Tests cover one-query export plus assignment, atomic failure with existing
definitions, dependencies, extra projections, buddy names, quoting/case, column
lists, literal semicolons, trailing comments, catalog/schema ambiguity and
XML-to-CREATE recreation. The public extension stays in this module, with no new
dependencies or shared-model/XML changes. Live EXPORT_OBJECTS qualification and
the full-read connection described below require live qualification.

Reference: [Vertica identifiers](https://docs.vertica.com/25.1.x/en/sql-reference/language-elements/identifiers/).

## Full-reader integration

The filtered Projection full-read set now obtains definitions through the bulk
detail hook: one catalog query plus one scoped export, with no export for empty
sets. Normal reads remain metadata-only. `loadFull` populates the Schema only
after complete definition mapping succeeds, so failures do not partly append
objects. Local tests cover ordinary/full reads, filtered scopes, empty/excluded
sets and existing Schema preservation on a missing-definition failure.

The real database round-trip test is isolated in the explicit
`projectionIntegrationTest` Gradle task and excluded from ordinary `test` runs.
It passed against an explicitly authorized, dedicated local Vertica 25.1.0-0
container on 2026-11-03, covering single-node UNSEGMENTED projections, quoted
names, XML/DDL recreation, bulk full reads and rename. The test schema, container
and anonymous volume were removed afterward. Other layouts and older versions
remain unqualified. See [INTEGRATION_TESTS.md](INTEGRATION_TESTS.md).

The expanded live run also passed single-node hash-segmented/KSAFE 0 projections
with RLE encoding and independent sort order, plus View/Sequence/overloaded SQL
function and typed-table XML/DDL recreation. Real verification exposed fixes for
DROP FUNCTION argument signatures, Identity syntax and fractional-second loss.
Temporal DDL now uses Vertica's default precision of six; explicit precision is
retained. These fixes apply through existing resolver paths without public API,
XML-format or dependency changes. Multi-node buddy/KSAFE 1+ remains unverified.

Reference: [Vertica date/time precision](https://docs.vertica.com/25.1.x/en/sql-reference/data-types/datetime-data-types/).

## Stored procedure full reads and explicit table definitions

The existing 11.1.1+ ProcedureReader full-read hook now performs one scoped
EXPORT_OBJECTS call for the filtered procedure set, after the bulk catalog read.
Normal reads remain metadata-only; empty full-read sets do not export. The scope
uses quoted identities and resolved native argument base types, without column
size defaults. Overloads are matched by schema/name/type signature, not specific
name text. Complete CREATE PROCEDURE statements are assigned to `definition`
only when every target is matched exactly once. Context/ALTER statements,
duplicate or missing definitions and unresolved argument types fail explicitly.
Exported bodies are retained as opaque DDL; this is not a general SQL validator.

`VirticaDdlExporter.exportProcedureDefinitions(connection, procedures)` is the
optional explicit entry point; `applyProcedureDefinitions(script, procedures)`
supports offline mapping. The returned export keeps the complete dependency
script for review; individual definitions contain only the matching CREATE.
DROP PROCEDURE now includes the modeled base-type signature, including `()`
for zero arguments. Existing public APIs and XML formats are retained; these
exporter methods are additive. Full reads now require vendor-export privileges.
Cross-catalog duplicate overload targets and non-native argument types are
rejected rather than inferred. Older reader resolver paths remain unchanged.

An explicitly supplied nonblank `Table.definition` is authoritative for permanent
CREATE TABLE, including Flex/external tables. Modeled columns and constraints are
supplemental and are not emitted again. Temporary tables retain their explicit
session-semantics guard. Automatic acquisition of special-table definitions is
not enabled: Flex keys/views/projections and external COPY dependencies need
coordinated handling. Do not assign an entire dependency export to each table.
The existing HTML definition display consumes these same Schema model fields.

On Vertica 25.1.0-0, the opt-in integration task verified INT/VARCHAR procedure
overloads through full-read/XML/DROP/CREATE/CALL, plus explicitly defined Flex
and `/dev/null` external-table recreation and SELECT. No external data source was
accessed. The module's 92 unit tests passed. See INTEGRATION_TESTS.md.

Reference: [Exporting stored procedures](https://docs.vertica.com/25.4.x/en/data-export/db-export-and-import/using-export-functions/exporting-objects/).

## Text-index full reads

Real qualification on 25.1.0-0 confirmed that scoped EXPORT_OBJECTS returns
CREATE TEXT INDEX, including original ID/text/payload columns and complete
tokenizer/stemmer syntax. Text-index full reads on server major versions 25+
now acquire these definitions in one export for the filtered set, after the
bulk catalog query. Empty sets do not query. Earlier servers retain catalog-only
full reads; normal metadata reads are unchanged. No additional per-index SELECT
is issued. Export permission is required by these new full reads.

`VirticaDdlExporter.exportTextIndexDefinitions(connection, indexes)` is the explicit
bulk entry point; `applyTextIndexDefinitions(script, indexes)` supports offline
mapping. Matching uses the index's own INDEX_SCHEMA_NAME and name, which can
differ from its source table schema. Missing, duplicate, catalog/source-mismatched
or context-dependent definitions fail without partial assignments. The mapper
validates headers and source identity, not the complete vendor SQL grammar.

Nonblank `Index.definition` is authoritative for direct CREATE and table CREATE;
incomplete supplemental catalog columns are not emitted again. Without a complete
definition, existing strict modeled-input validation remains. DROP uses DROP TEXT
INDEX with the index's own schema, quoting and optional IF EXISTS. Public APIs are
additive; the Schema XML format is unchanged. Tokenizer/library deployment and
grants must still exist on the target; export does not package them.

The opt-in live test reads a quoted text-index name through TableReader full
metadata, XML-round-trips it, executes generated DROP/CREATE and checks a keyword
search and payload value. This passed on 25.1.0-0, including the exported multi-type
tokenizer signature. A prior explicit-definition round-trip also passed.

## Schema dependency scripts and Flex restore investigation

`new VirticaDdlExporter().exportSchemaDefinition(connection, schema)` captures one
scoped EXPORT_OBJECTS script in the existing `Schema.definition` field and returns
it for review. This is an explicit operation; normal metadata reads do not export
an entire schema. It exports definitions, not row data, grants or library binaries.
Failure leaves the previous definition unchanged. The schema scope currently
requires a simple name; a quoted schema scope failed resolution on 25.1.0-0.
An attached catalog, when present, must match the connected database.

The Vertica schema CREATE factory treats a nonempty definition as authoritative,
splits it with the dialect splitter, preserves its statement order and suppresses
supplemental child CREATE generation. Empty/comment-only definitions fail. Without
a definition, existing model-based schema generation is retained. These changes
are confined to Vertica; public exporter API is additive and XML is unchanged.

The live test passed complete export/XML/DROP SCHEMA/recreation for the ordinary
test schema, including tables, external tables, views, projections, text indexes
and routines. It also established a concrete Flex limitation: CREATE FLEX TABLE
implicitly creates a superprojection, while EXPORT_OBJECTS emits CREATE for that
same projection. Direct replay fails with an existing-object error. An experiment
creating a replacement projection first still failed when dropping the implicit
projection because history after AHM would be lost. No global AHM adjustment was
made; that replacement approach is not included in the implementation.

Schema-definition generation therefore rejects scripts containing both Flex
CREATE and projection CREATE statements before returning any operations. The raw
export remains available for review. Flex keys/view links cannot be inferred from
name suffixes: recreating objects with those names does not restore internal links.
Direct full-script Flex replay remains unsupported. The guarded executor's
generated-default verification path below now covers matching default designs
without changing history or silently accepting different physical DDL.

Reference: [Flex dependencies and internal links](https://docs.vertica.com/11.1.x/en/sql-reference/functions/meta-functions/flex-table-data-functions/).

## Scheduling metadata and schema-export limits

On servers 25+, explicit `exportSchemaDefinition` now issues one additional
schema-bound UNION query for USER_SCHEDULES and STORED_PROC_TRIGGERS, retaining
independent enabled states (including unattached schedules) in Schema specifics
`VERTICA_SCHEDULING_STATES`. This is a review snapshot, not executable DDL or a
standalone Schedule model. Quoted identities and escaped control characters are
stored on one line so XML round-trips preserve the value. Query failure or invalid
state rows leave the previous definition and snapshot unchanged. Older server
exports keep the previous query path and remove stale scheduling snapshots.
Normal metadata reads and existing 12.0.4+ Event representation remain unchanged.

Live tests found that vendor schema export emits unqualified procedure references
in trigger CREATE and does not preserve independent enable states. The enable
functions also reject an unchanged state. A conditional DO restoration experiment
lost its database connection and the disposable server stopped accepting queries;
the cause was not established. That experimental restoration code was removed.

The schema CREATE preflight now rejects CREATE SCHEDULE / CREATE TRIGGER before
returning any operations, preventing apparent success with changed execution
behavior. Raw export and the state snapshot remain available for review. A complete
solution initially lacked reliable procedure/DDL lifecycle handling. Ordinary
CREATE SQL generation still rejects scheduled scripts; the guarded high-level
restorer documented below now covers fresh-schema restoration. The
investigation above did not change the scheduler-global setting; the separate
Java-side state-restoration step below was qualified afterward.

### Restoring states of existing schedules and triggers

The Java-side state-restoration step is now available independently of full DDL
replay. Its high-level entry point is:

```java
var result = new VirticaSchedulingStateRestorer().restore(connection, schema);
```

Use the exported Schema model (including its XML-preserved
VERTICA_SCHEDULING_STATES snapshot), a dedicated auto-commit connection to the
intended database, and already-created schedules/triggers. Before invoking it,
the administrator must provide an exclusive maintenance window, stop the global
scheduler and drain in-flight work. The API checks the database scheduler setting
and refuses to mutate while it is enabled or unknown. It never stops/restarts the
scheduler, executes procedures, changes DDL or closes the caller's connection.
The administrator must keep other actors from changing objects or configuration
during this operation; the initial scheduler check does not provide a lock.

The snapshot is strictly parsed, scoped to the Schema and checked for duplicate
identities. One bulk query reads the current set; missing or extra targets fail
before mutation. Only changed states receive a top-level prepared ENABLE_TRIGGER
or ENABLE_SCHEDULE call, with triggers first and schedules last. A second bulk
read verifies the final states. Metadata retrieval does not grow per object;
mutations necessarily use one vendor function call per changed object.
An unchanged set is a no-op, avoiding the vendor unchanged-state error.

`Result.applied()` lists completed changes and `unchanged()` counts skipped
objects. Mutations are not transactional. On a mutation or final-verification SQL
failure, `RestorationException.getCompleted()` retains completed changes and
`getFailedChange()` identifies the failed mutation (null for verification failure).
A failed JDBC call can have an uncertain outcome; inspect the target before retry.
No rollback is claimed. Existing APIs/XML remain unchanged; the restorer API is
additive and qualified on Vertica 25.1.0-0.

Live verification restored deliberately changed trigger/schedule states, checked
rejection while the scheduler was running, and repeated restoration with zero
changes. The test stopped/restarted the scheduler only inside its dedicated
disposable container and confirmed its original setting afterward. This success
does not remove the ordinary SQL-generation guard described above.

### Restoring a fresh schema including scheduling DDL

`VirticaSchemaRestorer` now composes the ordinary schema SQL factories with the
state restorer. It supports exported definitions on Vertica 25+, restoring into
an absent schema only. Use a dedicated auto-commit connection during the same
exclusive, scheduler-stopped and drained maintenance window described above.
It does not stop/restart the scheduler, drop an existing target, restore row data
or execute the scheduled procedures.

```java
// Only needed when vendor trigger DDL omits the procedure schema. Supply this
// resolved identity from trusted source information; it is never guessed.
schema.getEvents().get("trigger_one").getSpecifics()
    .put("PROCEDURE_SCHEMA_NAME", "app");
var restorer = new VirticaSchemaRestorer();
var result = restorer.restore(connection, schema);
```

The source Schema must retain its complete definition and
VERTICA_SCHEDULING_STATES snapshot. `preview(schema)` is an optional offline
inspection using the same preflight. It requires an initial unconditional
CREATE SCHEMA matching the model and checks that created objects stay within
that new schema. The scheduling CREATE set must exactly match the state snapshot.
Each trigger must reference a preceding schedule CREATE in the same schema.
Unqualified schedule names are resolved against that validated set. An unqualified
procedure name requires a matching Event with explicit PROCEDURE_SCHEMA_NAME;
existing qualified procedure references are retained. Procedures in the new schema
must have preceding CREATE definitions. Explicit external procedure references
require those procedures to exist already; cross-schema schedules are unsupported.

All supported DDL is planned before checking the database. Preflight rejects unsupported Flex batch ordering,
additional schemas, arbitrary SQL, unknown CREATE forms, and ALTER forms other
than table ADD CONSTRAINT. Tables, external tables, views, SQL functions, stored
procedures, sequences, projections and text indexes use the existing Schema
factory path; schedules and resolved triggers are created afterward. The executor
checks the stopped scheduler and absent target before DDL, then delegates final
enable-state restoration and bulk verification to VirticaSchedulingStateRestorer.
Ordinary CREATE SQL generation keeps its guard because it cannot check these
connection/lifecycle prerequisites.

`Result.completedStatements()` records executed DDL and `schedulingStates()`
retains state-change details. On failure, RestorationException exposes completed
statements and the failed statement (null for state-phase failure); its cause
retains detailed state-restoration progress. DDL/functions are not transactional,
and no automatic cleanup or rollback is attempted. A failed JDBC call may have an
uncertain outcome; inspect the target before retrying. The caller owns/closes the
connection and must keep the maintenance window exclusive throughout.

The 25.1.0-0 live test XML-round-tripped a schema with tables, routines, views,
projections, a text index, two triggers and three schedules (one unattached).
It verified running-scheduler/target-exists rejection, restored the absent schema,
checked independent disabled states and zero automatic procedure inserts, then
successfully called a restored procedure. The dedicated container's scheduler was
returned to its original setting. This is additive API; public model/XML formats
are unchanged. Flex support is bounded by generated-default verification below;
library deployment, grants, older live versions and multi-node qualification remain
outside this path.

### Native Flex generated-default verification

The high-level restorer now supports a contiguous batch of native CREATE FLEX
TABLE statements before their generated child/projection DDL. It still requires
an absent target Schema, a stopped scheduler and an exclusive drained maintenance
window. Temporary/external Flex and arbitrary interleaving of Flex creates with
child DDL are not supported. Ordinary SQL generation retains its direct-replay
guard; this behavior is available through the guarded executor only.

After creating the Flex batch, the executor bulk-exports the current schema once
and compares its generated objects with saved CREATE definitions. Object identity
comes from qualified DDL headers, not `_keys`/`_view` suffix guesses. Explicit
prefix objects are excluded from the generated set. Only exact matching CREATEs
are verified and omitted from later execution; additional custom projections are
created normally. Differences, missing source definitions and duplicate identities
stop restoration before the remaining DDL. Comparison normalizes line endings and
outer whitespace only, preserving literals, hints and layout semantics. Different
vendor formatting can therefore fail conservatively.

On 25+, schema export also reads all native Flex `__raw__` lengths/nullability in
one catalog query. It retains each complete Flex CREATE in the existing Table
definition and stores implicit raw-column attributes in the existing Column model.
Existing supplemental columns remain; otherwise components can be partial because
the complete CREATE definition is authoritative. These fields survive Schema XML
and use existing table/definition HTML display. No shared model or XML-format
extension was required. Earlier exports without raw metadata must be re-exported
before Flex restoration; the executor does not guess a raw width from target defaults.
At the generated-object checkpoint, one bulk raw-column query checks the exact
Flex table set, source widths and nullability, rejecting a target-default mismatch.
Neither read count grows per Flex object.

No projection is dropped/replaced and no AHM/history function is called. The newly
created Flex table retains its automatic keys/view links. This path preserves
matching exported default designs; it does not restore row data, populated keys,
or implement a replacement policy for altered generated designs. Failure after
the Flex batch leaves a partially created schema for inspection, without automatic
cleanup. The caller must inspect it before retrying.

The original `restore` entry point and two-component Result record remain intact.
For detailed verified omissions use:

```java
var details = restorer.restoreWithDetails(connection, schema);
var executed = details.result().completedStatements();
var verified = details.verifiedGeneratedStatements();
```

Failures expose `getPhase()` (DDL, GENERATED_OBJECTS or SCHEDULING_STATES), completed
DDL and already-verified generated statements. `preview` includes the source DDL
candidates; the exact omission set is determined at runtime after verification.
An explicitly present empty scheduling snapshot is supported after XML round-trip;
an absent snapshot key still fails.

Live qualification on 25.1.0-0 passed multiple Flex tables with and without explicit
columns, an added custom projection, unchanged projection identities and linked
keys/views. It verified post-restore INSERT, virtual-field view rebuilding and
implicit Identity. A source raw-width mismatch was deliberately introduced into
the test model and rejected before remaining DDL; the correct model then restored
successfully. No AHM modification was needed.

UDx metadata retains `PROCEDURE_TYPE` and raw argument/return signatures in
`Function.specifics`. Non-scalar specific names include the kind to distinguish
otherwise identical signatures. DROP uses FUNCTION, TRANSFORM FUNCTION,
AGGREGATE FUNCTION, ANALYTIC FUNCTION, PARSER, FILTER or SOURCE as appropriate.
Argument dimensions are omitted from DROP overload identities; polymorphic
transform `Any` uses an empty DROP signature. Load functions require empty
arguments and `dropIfExists=false`. Unknown kinds fail explicitly.
Non-scalar CREATE requires a complete executable `definition` and an already
deployed library. Vertica 25.1 was tested with a dedicated Parser using the
image-bundled FlexTableLib: metadata, XML, DROP and CREATE passed. Library binaries,
dependencies and grants are not exported. No shared Schema API or resolver changed;
historical server compatibility and the other UDx kinds remain unit-test-only.

Multi-column UDx return signatures remain in `FUNCTION_RETURN_TYPE`; they do not
populate the single-value `FunctionReturning` model. Commas inside type precision
are preserved as part of a single type. Bulk-reader tests verify one query for
same-name/same-signature scalar, transform, aggregate and analytic functions, and
that all four survive Schema XML round-trip. Polymorphic `Any` arguments and raw
multi-column returns also survive XML and retain the transform DROP behavior.
