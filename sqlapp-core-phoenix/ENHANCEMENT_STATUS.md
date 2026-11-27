# Phoenix local enhancement completion

Status: the Docker-free pass is complete for the scope below. This is not a
claim of complete Phoenix support or live HBase/Phoenix compatibility.

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
