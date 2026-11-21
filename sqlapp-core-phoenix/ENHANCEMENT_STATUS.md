# Phoenix local enhancement completion

Status: the Docker-free pass is complete for the scope below. This is not a
claim of complete Phoenix support or live HBase/Phoenix compatibility.

Latest follow-up (2026-11-20, option comparisons): table ALTER uses CREATE's
validated integer/boolean semantics for SALT_BUCKETS and MULTI_TENANT. Equivalent
spellings no longer block supported column additions; genuine changes/removal
remain refused without inferring defaults. Invalid unchanged options also fail.
DEFAULT_COLUMN_FAMILY retains exact comparison. Changes are confined to Phoenix;
no public API, dependency, metadata query, version or XML-format changes were
introduced. PhoenixDdlOptionsTest passed (6 tests), followed by
`./gradlew.bat :sqlapp-core-phoenix:test --offline --console=plain`:
137 tests passed, no failures/errors/skips. README was updated. No external DB
was accessed; live migration behavior remains unverified. Only normal Gradle
outputs were generated, with no intentional generated-source changes.

Latest follow-up (2026-11-20, identifiers): Phoenix quoting doubles embedded
double quotes on all existing resolver paths; already quoted names retain their
existing handling. CREATE TABLE rejects unresolved primary-key columns and
CREATE INDEX rejects blank ordinary key/INCLUDE identifiers and explicit blank
families. XML and valid quoted families are covered. No public signature,
dependency, catalog query or XML-format changes were added. The first targeted
run exposed missing quote escaping; after fixing Phoenix.doQuote, all four
PhoenixIdentifierValidationTest tests passed. Then
`./gradlew.bat :sqlapp-core-phoenix:test --offline --console=plain` passed:
135 tests, no failures/errors/skips. README documents the behavior. No external
database was accessed or live DDL executed; only normal Gradle outputs were
generated, with no intentional generated-source changes.

Latest follow-up (2026-11-20, boolean headers): IMMUTABLE_ROWS and MULTI_TENANT
now share strict true/false parsing with TRANSACTIONAL instead of JDBC coercion.
Table/view reads retain absent/NULL as unknown, normalize valid case variants,
and reject malformed values with the field name while closing the result.
No additional queries, version-specific columns, public APIs, dependencies or
XML structure changes were introduced. PhoenixHeaderReaderTest passed (9 tests),
then `./gradlew.bat :sqlapp-core-phoenix:test --offline --console=plain` passed
(131 tests, no failures/errors/skips). README documents the behavior. No external
database was accessed; live driver verification remains outstanding. Only normal
Gradle outputs were generated, with no intentional generated-source changes.

Latest follow-up (2026-11-20, transaction safety): optional JDBC TRANSACTIONAL
and TRANSACTION_PROVIDER metadata survive Schema XML without additional queries.
Transactional CREATE refuses to discard semantics or infer a provider; explicit
false is supported from 4.7. Transaction/provider changes through ALTER fail.
STORE_NULLS supports CREATE, XML and the existing set-based catalog read from
4.3; changing it through ALTER remains unsupported. Existing signatures,
dependencies and XML structure are unchanged; constants/capabilities are additive.
Focused tests and then
`./gradlew.bat :sqlapp-core-phoenix:test --offline --console=plain` passed:
130 tests, no failures/errors/skips. No external database was accessed, and
provider-aware transactional recreation and runtime guarantees remain unverified.
Only ordinary Gradle build outputs were produced; generated-source changes are
not intentional.

Latest follow-up (2026-11-20, tuning): UPDATE_CACHE_FREQUENCY (4.7+) and
GUIDE_POSTS_WIDTH (4.9+) now support table CREATE/ALTER, XML and version-gated
recovery in the same supplemental catalog query. ALWAYS/NEVER and nonnegative
BIGINT values are validated; explicit GUIDE_POSTS_WIDTH=NULL resets the override.
Observed catalog NULL is retained as that explicit no-override state, while
unselected old/unknown metadata stays unset. Ambiguous key removal, overflow,
invalid values and unsupported versions fail. Phoenix4_7/holder/capability APIs
are additive; existing later feature boundaries are preserved. No shared-core,
dependency or XML-format changes were added. Focused tests and then
`./gradlew.bat :sqlapp-core-phoenix:test --offline --console=plain` passed on
Java 25.0.3 / Wrapper 9.8.1: 124 tests, no failures/errors/skips. No external DB,
runtime cache/statistics measurement or live old-server execution was performed.
Only ordinary Gradle build outputs were produced; generated-source changes are
not intentional. View/index tuning and the remaining live/advanced limits stay
outside this batch.

Latest follow-up (2026-11-20, table options): DISABLE_WAL is retained through
the existing set-based catalog query and Schema XML, generated for CREATE and
explicitly changed in both directions by Table diffs. APPEND_ONLY_SCHEMA is
read/generated only from known 4.8+ catalogs/dialects; its ALTER remains refused,
and column deletion from a modeled append-only table fails. Missing settings
remain unset, invalid booleans and ambiguous WAL removal fail, and the query
count remains one supplemental SELECT. No XML/dependency/shared-core change
or resolver boundary change was added; option constants and a capability method
are additive. Focused tests passed, followed by
`./gradlew.bat :sqlapp-core-phoenix:test --offline --console=plain` on Java
25.0.3 / Wrapper 9.8.1: 118 tests, no failures/errors/skips. No external DB or
crash-recovery verification was performed; only normal Gradle outputs were
produced, with no intentional generated-source changes. Further properties,
general view reconstruction, metadata expression recovery, native bulk and
atomic/transactional guarantees remain separate/live-verification work.

Latest follow-up (2026-11-20, functional indexes): known Phoenix 4.3+ dialects
accept user-authored shared IndexType.Function keys. Unresolved ReferenceColumn
names render as SQL expressions; resolved columns retain normal/family quoting.
XML, mixed keys, DESC, INCLUDE and Table/View diff replacement are covered.
Phoenix4_3 and its holder/capability are additive; later resolver boundaries are
unchanged. Blank expressions and ambiguous separate family declarations fail.
Column deletion with a retained expression index requires explicit index removal
because dependencies are not parsed. Metadata expression recovery remains outside
this batch; no catalog queries/shared core/model/XML/dependencies changed.
After focused tests and correcting a whitespace/identifier expectation in the
new SQL assertion, `./gradlew.bat :sqlapp-core-phoenix:test --offline
--console=plain` passed on Java 25.0.3 / Wrapper 9.8.1: 112 tests, no failures,
errors or skips. No external DB was used and no generated-source changes are
intentional. Native expression validity and live execution remain unverified.

Latest follow-up (2026-11-20, index states): PhoenixAlterIndexFactory adds
explicit DISABLE/UNUSABLE generation and conservative state-only Index ALTER
diffs. Table/View diffs share that factory and avoid DROP/CREATE when only
phoenix.indexState changes. Equivalent ACTIVE/a/USABLE forms are no-ops;
known UNUSABLE can return through USABLE. Disabled reactivation requires
explicit SqlType.REBUILD. Unknown/internal states, ambiguous source states,
state removal and standalone structural/owner changes fail explicitly.
Structural Table/View index replacements retain their former path.
No model mutation, dependency, XML-format or shared-core change was added.
Focused PhoenixAlterIndexTest and PhoenixIndexMigrationTest passed (9 tests),
then `./gradlew.bat :sqlapp-core-phoenix:test --offline --console=plain` passed
on Java 25.0.3 / Gradle Wrapper 9.8.1: 108 tests, no failures/errors/skips.
Tests cover quoting, Table/View and standalone diffs, XML, mixed column/state
changes, semantic no-ops, unsupported states and retained replacement order.
The syntax uses the existing common 4.1+ target; no live server, state
transition or rebuild was executed. General raw catalog-state transitions,
functional-index reconstruction and other live/advanced limits remain.

Latest follow-up (2026-11-20): known 4.1+ table headers now recover
DEFAULT_COLUMN_FAMILY with one bound, set-based SYSTEM.CATALOG query in addition
to getTables. Full tenant/schema/table identity is retained; unknown/older
versions, views and empty sets keep the former path. Missing/duplicate headers,
blank families and SQL failures fail explicitly. The existing Index model and
SqlType.REBUILD now generate synchronous ALTER INDEX ... ON ... REBUILD for
table/view indexes, with quoting and without implicit state-diff inference,
IF EXISTS or data execution. Public changes are additive; no dependencies,
XML formats, shared-core changes or external DB accesses were added in this
follow-up. New focused tests passed after correcting their standalone reader
options and attached diff fixtures; `./gradlew.bat :sqlapp-core-phoenix:test
--offline --console=plain` then passed on Java 25.0.3 / Gradle Wrapper 9.8.1:
104 tests, no failures/errors/skips. Only ordinary local Gradle build outputs
were produced, with no intentional generated-source changes. Live catalog and
rebuild execution, general view reconstruction, further properties/state
transitions and native bulk/atomic operations remain unverified or separate.

Latest follow-up (2026-11-19): scalar DATE/TIME epoch milliseconds survive
JDBC mock read/bind, Row conversion, Schema XML and SQL literal generation.
Generic UPDATE/MERGE now fail explicitly, including row and diff paths.
SELECT_FOR_APP uses validated bound LIMIT/OFFSET, with OFFSET gated at 4.8.
VARBINARY_ENCODED maps to byte[]/VARBINARY with its native name preserved from
5.3 onward. Resolver inheritance adds 4.8 and 5.3 boundaries without moving
DEFAULT (4.9) or multi-row VALUES (5.3.1). See README for API/behavior changes
and the requirement to retain Schema/Catalog product identity for row XML.

The shared Row XML writer now honors the existing optional dialect formatting
hook; this is a nine-line addition in sqlapp-core and also affects DuckDB's
existing opt-in. No public signatures, XML structure or dependencies changed.
After focused tests, Java 25.0.3 / repository Gradle Wrapper 9.8.1 with --offline
passed sqlapp-core:test (1,790), sqlapp-core-phoenix:test (99), selected
DuckDbDataTypeTest/DuckDbJdbcTypeHandlersTest (38), and selected
ExportXmlCommandTest/ExportDataCommandTest (5), all without failures or skips.
Initial test/compile failures were corrected before these successful runs.
One compiler stall was resolved by replacing inferred recursive Class types
with an explicit Class<?> array and restarting this task's Gradle daemon.
Only ordinary Gradle/test outputs were produced; no generated source changes
are intentional. HSQLDB command tests used an in-memory database. No external
DB or Phoenix/HBase server was contacted. Remaining live/advanced work below
is unchanged; this does not claim native ARRAY binding or general metadata
recreation coverage.

Latest local verification (2026-11-04): JDBC table/view headers reject missing
names and duplicate tenant/schema/name identities. Tests cover 40 headers,
source/marker XML retention, incomplete view recreation rejection and resource
closure. Header-only and incomplete-source metadata reads remain supported.
Targeted PhoenixHeaderReaderTest passed, followed by
`./gradlew.bat :sqlapp-core-phoenix:test` on Java 25: 92 tests, no failures,
errors or skips. No public APIs, XML structure, dependencies, JDBC call count,
version boundaries or catalog queries changed. No external database was used.

Previous local verification (2026-11-04): Sequence metadata rejects missing names,
duplicate tenant/schema/name identities, zero increments, negative caches and
contradictory known start/bound values. Missing numeric values retain their
prior unknown representation. Descending sequences and boundary starts retain
XML/DDL coverage. Targeted PhoenixSequenceReaderTest passed, followed by
`./gradlew.bat :sqlapp-core-phoenix:test` on Java 25: 89 tests, no failures,
errors or skips. No catalog SQL, version boundaries, APIs, XML structure or
dependencies changed. No external database or sequence allocation was used.

Previous local verification (2026-11-04): Primary-key metadata rejects missing
table/column identities and repeated columns, with regression coverage for
inconsistent names, resource closure, unnamed keys and case-sensitive columns
across schemas. Targeted PhoenixPrimaryKeyReaderTest passed, followed by
`./gradlew.bat :sqlapp-core-phoenix:test` on Java 25: 86 tests, no failures,
errors or skips. Existing single-call retrieval, APIs, XML, resolver paths and
dependencies are unchanged. No external database was used.

Previous local verification (2026-11-04): Index metadata now decodes native
SORT_ORDER serialization IDs (1 DESC, 2 ASC), retains ASC for missing values,
and rejects unknown non-null IDs. Tests use native IDs rather than comparison
signs and cover version paths, bulk query count and XML/DDL retention. Live
catalog behavior remains unverified. No signatures, XML format, dependencies,
resolver selection or catalog SQL changed. Targeted PhoenixIndexReaderTest
passed, followed by `./gradlew.bat :sqlapp-core-phoenix:test` on Java 25:
83 tests, no failures/errors/skips. The full run was completed after the prior
approval-service usage limit was cleared. No external database was used.

Previous local verification (2026-11-03): Table/View index diffs now use native
CREATE/DROP factories, retain the required DROP ON clause and remove modified
indexes before column changes. Contradictory target references and unsupported
types/predicates fail explicitly. Targeted index/table/view tests passed,
followed by `./gradlew.bat :sqlapp-core-phoenix:test` on Java 25: 82 tests,
no failures/errors/skips. README records non-atomic replacements and the
boundary of server-side index/dependency checks. No external database or
container was used. Existing signatures/XML/dependencies/resolver selection
and metadata queries are unchanged in this batch.

Previous local verification (2026-11-03): DEFAULT declarations are gated at 4.9
through an additive Phoenix4_9 dialect. The 5.3.1 VALUES boundary is retained;
older/unknown versions reject DEFAULT while keeping ordinary column generation.
Column ON UPDATE/CHECK declarations fail explicitly, and NOT NULL precedes
DEFAULT in generated DDL. Targeted version/default/function/resolver tests
passed, followed by `./gradlew.bat :sqlapp-core-phoenix:test` on Java 25:
77 tests, no failures/errors/skips. Existing signatures/XML/dependencies and
metadata queries are unchanged; resolver selection intentionally gains the
4.9 boundary. Earlier claims below of unchanged resolver boundaries describe
previous batches. No external database or container tests were executed.

Previous local verification (2026-11-03): ALTER VIEW ADD/DROP is covered on both
Phoenix dialects, sharing CREATE VIEW column validation and table-diff phases.
Targeted ALTER VIEW/CREATE VIEW/ALTER TABLE tests passed, followed by
`./gradlew.bat :sqlapp-core-phoenix:test` on Java 25: 74 tests, no failures,
errors or skips. README documents unsupported diffs and server-side dependency
checks. No external database or container tests were executed. No generated
source changes are intentional; only ordinary Gradle build outputs were produced.

Previous follow-up (2026-11-03): modeled additional View columns and Spanner CHECK
migrations are covered. Java 25, repository Wrapper,
`./gradlew.bat :sqlapp-core-spanner:test :sqlapp-core-phoenix:test` passed:
Spanner 50 tests and Phoenix 70 tests, no failures/errors/skips. Targeted CHECK,
View and header-reader tests were run first. The CHECK/column ordering fixture
was adjusted because the shared diff model classified the initial replacement
as a rename; explicit deletion and addition cases then passed. `git diff --check`
passed. No external database or container was used; generated source changes
are not intentional, and build outputs are ordinary local Gradle test outputs.

Earlier follow-up local verification (2026-11-03): explicit IMMUTABLE_ROWS ALTER,
DELETE-based truncation and unsupported LOCK rejection are covered on both
dialects. Java 25 and the repository Wrapper:
`./gradlew.bat :sqlapp-core-spanner:test :sqlapp-core-phoenix:test` passed
(Spanner 46 tests, Phoenix 67 tests; no failures/errors/skips). Targeted tests
preceded the module runs. An initial schema-decoration assertion in the new
Spanner test was corrected to specify the intended option and rerun. No live
server/container tests were executed. Existing APIs/XML/dependencies and
resolver boundaries are unchanged; unsupported operations now fail explicitly.

Previous pass local verification: Java 25, repository Gradle Wrapper,
`./gradlew.bat :sqlapp-core-phoenix:test`: 64 tests, 0 failures, 0 skipped.
Changed-reader/factory tests were run before the full module. Initial test
fixture/format assertions were corrected and rerun successfully. Module diff
whitespace checks also passed. No external database tests were executed.

## Locally covered

- DECIMAL precision, CHAR encoding, native unsigned type names, current-value
  and COUNT expressions.
- One-dimensional ARRAY types/capacity through mapping, XML and DDL.
- CREATE TABLE, column families, optional placement properties, native ALTER
  ADD/DROP, explicit failures for unsupported modifications.
- Sequence CREATE/DROP, bulk metadata, legacy/3.1/4.1 catalog boundaries and
  unknown-value preservation without allocating or inferring sequence values.
- Column metadata, family/capacity preservation, primary-key sequence and sort
  direction from existing JDBC metadata results.
- Set-based index header/column retrieval, INCLUDE versus key separation,
  source families, placement/type/state preservation and guarded native DDL.
- JDBC table/view properties and view statement preservation. Complete native
  view definitions can be recreated; incomplete metadata cannot silently emit
  a misleading or empty CREATE operation.
- User-authored SELECT views with additional nullable columns, using native
  definitions and shared family/type/array rendering. Inherited-column and
  mapped-view metadata reconstruction still requires complete DDL.
- ALTER VIEW column additions/deletions, including column-only metadata-view
  diffs without CREATE reconstruction. Source, property, key, rename and type
  changes fail explicitly; server-side dependencies are not locally inferred.

All changes are confined to sqlapp-core-phoenix. No dependencies, existing API
signatures, resolver selection or XML formats changed. Readers/factories and
optional specifics keys are additive. Generated build/test outputs are normal
Gradle outputs; no generated source changes are intentional.

## Live-server verification still required

No Docker/container or external database was used. Mocked JDBC results and
query-template tests do not execute catalog SQL. A real server is required to
verify exact index column encodings, header/catalog permissions, native array
binding, tenant visibility, effective salting and recreation behavior on the
supported versions. Common native DDL targets 4.1+ and the existing 5.3.1 split;
older syntax/catalog paths are isolated but are not live-certified.

## Deliberately outside this pass

- General reconstruction of inherited/additional view columns and mapped HBase
  views from VIEW_STATEMENT alone; provide complete CREATE DDL.
- Functional/internal index keys, inactive-index state transitions and local
  index storage/salting combinations that cannot be recreated reliably here.
- Multiple source columns with identical simple names in different families:
  current Schema column identity cannot represent these without ambiguity.
- Automatic tenant connection selection and sequence counter restoration.
- New Phoenix types/features requiring further version splits or shared models.
- ALTER of native table properties other than explicit IMMUTABLE_ROWS changes,
  primary-key extension and unsupported
  rename/type/constraint changes; these require a separate migration design.

Remaining limitations fail explicitly where generating SQL would lose required
semantics. Specification links, APIs and examples are recorded in README.md.
