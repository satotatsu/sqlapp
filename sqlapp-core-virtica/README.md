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
