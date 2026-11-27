# Spanner local enhancement audit

Scope: GoogleSQL, Java 25, repository Gradle Wrapper. This closes the local
enhancement pass; it is not live-service certification or complete Spanner
feature coverage. The initial pass used no database or emulator; later sections
record disposable local emulator checks. No hosted database or production data
was accessed. The Spanner PostgreSQL interface is outside this dialect.

## Coverage

| Area | Local coverage | Boundary |
| --- | --- | --- |
| Resolution | Existing single-version resolver, reader wiring | Reported versions do not prove service capability |
| Columns | Native types, arrays, nullability, DEFAULT, generated/hidden/identity metadata, selected options, XML/DDL | New optional catalog fields are read from `col.*`; missing values stay absent |
| Primary keys | Composite order, ASC/DESC, ordinal/name validation, XML/DDL | Key changes require a separate migration |
| CHECK | Expression/state reading, CREATE, add/drop/replace, XML/DDL | Internal NOT NULL catalog exposure needs live verification |
| Foreign keys | Composite pairing, references, rules/enforcement/state, CREATE and table diffs | Only represented native rules are supported |
| Indexes | Key/STORING separation, UNIQUE/NULL_FILTERED, native FILTER or nullability fallback, SEARCH partition/order, ordinary/SEARCH interleave, selected options, create/drop/diffs | Layout fields must be exposed by catalog; VECTOR interleave and unmodeled options are unsupported |
| Sequences | Start/skip/kind metadata, XML/DDL, next-value SQL | Current allocation counter is not restored; generic options are not all native |
| Views | Statement/security reading, XML, CREATE/DROP/replacement | Raw definitions are not rewritten for replacement; privileges need separate handling |
| Table synonyms | Modeled CREATE/DROP, target replacement through Schema diffs, explicit alias-name diffs | Target must be supplied; replacement uses separate DROP and ADD statements |
| Column ALTER | Add/drop, defaults, supported nullability, scalar STRING/BYTES length, commit-timestamp options and ON UPDATE, identity skip range | Identity counter changes and unsupported generated/key/type/rename changes fail |
| Delete all | DELETE FROM ... WHERE TRUE | DML limits/transactions apply; this is not native TRUNCATE |
| Table deletion | Native DROP TABLE with optional IF EXISTS after modeled user-index drops; managed FK indexes excluded | Complete index metadata is required; external dependencies are not removed automatically |
| Table recreation and interleave | IN/IN PARENT recreation, parent-first creation, same-parent relationship and deletion-rule changes | Ambiguous metadata and parent changes fail; the server validates data and key compatibility |
| Table dependency schema differences | Parent/referenced-table-first additions and child/referencing-table-first deletions for INTERLEAVE and FK-only chains | External dependencies are not created/removed automatically; cyclic batches fail |
| Table renaming | Name-only Table diffs, quoted schema-qualified ALTER TABLE RENAME TO, retained data/indexes | No inferred collection-level rename, schema move, combined structural change or view rewrite |
| Legacy temporary path | Existing factory/string tests retained | CREATE_TEMPORARY is outside the supported scope; no native execution claim |

## Final-audit corrections

Table interleave follow-up: TABLES.* now retains optional INTERLEAVE_TYPE.
Known IN PARENT and IN relationships render with quoted parents; legacy delete
metadata can identify IN PARENT unambiguously. Unknown/contradictory settings
fail. User-authored explicit IN PARENT defaults to NO ACTION, while missing
delete metadata on a catalog-reported IN PARENT row is rejected. CREATE table
collections order modeled parents and FK dependencies before children and
reject cycles. Table OPTIONS gain their required comma separator. ALTER remains
outside this change. No numeric service version assumption or new physical
column reference is added.

Managed-index follow-up: optional SPANNER_IS_MANAGED is retained and validated
through XML. Table recreation omits derived managed CREATE INDEX operations
while retaining foreign keys and user indexes. Direct CREATE/DROP/detail and
managed-index diff changes fail explicitly; ownership is not inferred from
names. Constraint diffs with unchanged derived metadata still generate their
normal DDL. Simultaneously changing derived managed snapshot objects is rejected
until a planner can resolve shared backing-index ownership safely. No new
catalog column references, queries, dependencies or resolver paths are added.

SEARCH layout follow-up: optional INDEXES.* fields FILTER,
SEARCH_PARTITION_BY and SEARCH_ORDER_BY are now consumed. Native FILTER takes
precedence over the earlier nullability fallback. Partition columns use shared
Partitioning; single-column order and parent names use optional vendor specifics.
INDEX_COLUMNS rows for grouping/sorting are removed from TOKENLIST keys and
preserve direction. Native SEARCH internal uniqueness is normalized without
emitting unsupported UNIQUE SEARCH syntax. Normal/SEARCH interleave renders
with quoted parent names; unsupported layouts fail. JDBC array values are freed.
No newer physical catalog columns are explicitly named and query counts remain
unchanged. Live exposure/semantics and cross-schema parent naming need checking.

Follow-up: WHERE metadata is part of ordinary recreation coverage, not an
advanced feature deferred wholesale. The bulk query now compares base/index
column IS_NULLABLE and builds an equivalent quoted IS NOT NULL conjunction in
shared Index.where for regular, SEARCH and VECTOR indexes. It includes STORING
columns, avoids duplicating global NULL_FILTERED keys and refuses unavailable
nullability. No per-index lookup or newer physical catalog column was added.
The local 120-index mock covers XML/DDL and changed-predicate replacement.
Original redundant predicates on base NOT NULL columns are not recoverable
from this representation; live partial-index catalog semantics remain unverified.

- Removed explicit references to newer generated/hidden/identity/ON UPDATE
  physical columns from the column query. Existing `col.*` exposes fields when
  available, and ExResultSet handles missing fields. No probe or per-column
  queries were added. This reduces a concrete old-catalog query failure risk;
  it does not establish compatibility with every historical service.
- Optional identity numeric fields are checked for presence before numeric
  reads, avoiding false zero values from an absent field and stale JDBC wasNull.
- Unknown generated/storage/hidden flags fail instead of changing column shape.
- CREATE TABLE refuses interleaved metadata whose parent cannot be rendered.
- UNIQUE constraint index names honor schema decoration; missing names and
  empty keys fail rather than emitting invalid SQL.

The audit includes real ExResultSet wrapping of mocked uppercase JDBC labels,
legacy/more-recent column sets, combined Schema XML and CREATE generation,
unsupported interleave rejection and quoted schema-qualified unique indexes.
Catalog templates are tested locally; none are executed against Spanner.

## Compatibility and API

No public signatures, configuration formats, Schema XML structure, dependency
versions, module dependencies or resolver boundaries changed in the final audit.
Metadata validation is intentionally stricter. Previously accepted malformed
metadata and ambiguous INTERLEAVE definitions now fail explicitly; supported
IN/IN PARENT definitions render their relationship instead of omitting it.
Normal existing paths and the missing optional field path remain covered.

The older column query still depends on its baseline COLUMNS/COLUMN_OPTIONS
fields. Index options retain the existing availability probe: empty sets use
one query, older catalogs without INDEX_OPTIONS use two, available catalogs use
three. Other metadata is fetched for the requested object set without extra
per-object SELECT calls. Permissions and driver OPTION_VALUE formatting can
still affect results; mocks cannot validate these.

## Verification

Table INTERLEAVE follow-up (2026-11-04, Java 25): focused table reader/CREATE
tests passed, followed by 139 full Spanner tests and 4 dependent command tests,
with zero failures/errors/skips. Coverage includes 40 legacy/current headers,
Schema XML, IN/IN PARENT, delete actions, quoted schemas, parent/FK ordering,
cycles, ambiguous metadata and comma-separated table OPTIONS. Existing ALTER
guards remain tested. Diff whitespace checks passed with CRLF notices only.
No external database or generated source changes were used.

Managed backing-index follow-up (2026-11-04, Java 25): focused
SpannerManagedIndexTest and SpannerIndexOptionsReaderTest passed. Full Spanner
tests passed 132 cases, with zero failures/errors/skips. The dependent
TransferSchemaSqlScriptTest passed all 4 cases. Coverage includes managed-flag
XML retention, foreign-key/user-index recreation, direct and diff refusal,
unrelated column changes and constraint-only removal. No external database
was contacted; generated Gradle outputs only. Diff whitespace checks passed.

SEARCH layout/native FILTER/interleave follow-up (2026-11-04, Java 25): focused
SpannerIndexOptionsReaderTest and SpannerSearchIndexLayoutTest passed. The
complete Spanner module passed 127 tests with zero failures/errors/skips, and
the dependent TransferSchemaSqlScriptTest passed all 4 tests. The 40-layout
mock round trip includes descending ordering and index replacement. JDBC
array lifecycle and invalid user-authored layout tests also passed.
`git diff --check -- sqlapp-core-spanner` passed with only CRLF notices.
No external database or generated source change was used.

WHERE follow-up (2026-11-04, Java 25): focused SpannerIndexOptionsReaderTest
passed, then `./gradlew.bat :sqlapp-core-spanner:test` passed 121 tests with zero
failures/errors/skips. The dependent TransferSchemaSqlScriptTest check below
passed all 4 tests again. `git diff --check -- sqlapp-core-spanner` passed with
only CRLF notices. No external database was contacted.

Run focused audit/column/CREATE TABLE tests first, then the complete module:

```powershell
.\gradlew.bat :sqlapp-core-spanner:test --tests '*SpannerFinalAuditTest' --tests '*SpannerCreateTableFactoryTest' --tests '*SpannerColumnReaderTest'
.\gradlew.bat :sqlapp-core-spanner:test
```

Final result (2026-11-04, Java 25): focused tests passed, then the complete
Spanner module passed 117 tests with zero failures/errors/skips. The dependent
command smoke check also passed all 4 tests:

```powershell
.\gradlew.bat :sqlapp-command:test --tests '*TransferSchemaSqlScriptTest'
```

`git diff --check -- sqlapp-core-spanner` passed (only working-copy CRLF
notices). No external database tests were executed. Generated Gradle build
outputs are incidental; no generated source changes are intended. Final-audit
implementation changes are confined to sqlapp-core-spanner; sqlapp-command was
tested without code changes for this audit. The local pass is complete.

## Remaining work outside this pass

1. Run catalog queries and generated DDL on an explicitly authorized disposable
   GoogleSQL database, including schema/identifier quoting, privileges, index
   options, asynchronous DDL readiness and recreation. Repeat on required older
   service/emulator targets; establish actual supported capability boundaries.
2. Verify native FILTER, SEARCH partition/order arrays and interleave parent
   naming on live services, especially older and cross-schema targets. Extend
   unmodeled options before claiming full recreation. Nullability reconstruction
   remains a fallback when FILTER is not exposed.
3. Design separate migrations for identity/generated/key/type/rename operations,
   creation-only table options and changes to interleave parents.
4. Treat current sequence counter restoration, security grants, database options,
   change streams, newer object types and temporary-table behavior as separate
   features, not automatic consequences of this local audit.

See [README.md](README.md) for individual behavior, examples and source links.

## TTL follow-up (2026-11-04)

Added optional TABLES.ROW_DELETION_POLICY_EXPRESSION metadata retention with
no additional SELECTs or explicit newer catalog-column references. Schema XML
preserves the policy; CREATE emits it between INTERLEAVE and OPTIONS. TTL-only
ALTER differences support ADD, REPLACE and DROP. Validation requires the native
OLDER_THAN expression, nonnegative integral DAY interval and an existing scalar
TIMESTAMP column. Mixed TTL/column migrations require separate steps. Public
signatures and XML formats are unchanged; the factory adds one optional specifics
key constant. The single-version GoogleSQL resolver remains unchanged.

Executed the focused SpannerRowDeletionPolicyTest (3 passed), then:

```
.\gradlew.bat :sqlapp-core-spanner:test :sqlapp-command:test --tests '*TransferSchemaSqlScriptTest'
```

Spanner: 142 tests passed, zero failures/errors/skips. Dependent command tests:
4 passed. The forty-table metadata/XML/recreation test also covers TTL presence,
absence and clause ordering with one metadata query. git diff --check passed
(CRLF notices only). No external database was accessed. No intentional generated
source changes. See README for live-service and identifier-validation limits.

## Table options follow-up (2026-11-10)

GoogleSQL table differences now emit SET OPTIONS for locality group, columnar
policy and full-text dictionary staleness on services and editions supporting
those settings. Removing columnar policy restores database inheritance. Other
option removals require explicit replacement values, and the dictionary table
flag is creation-only. Existing table specifics and XML carry the values without
API, resolver or catalog query changes. See README for catalog limitations.

Executed the focused SpannerAlterTableFactoryTest (6 passed), then:

```
.\gradlew.bat :sqlapp-core-spanner:test :sqlapp-command:test --tests '*TransferSchemaSqlScriptTest'
```

Spanner: 144 passed, zero failures/errors/skips. Dependent command: 4 passed.
No live database tests were run and no generated source changes were made.

## Interleave delete action follow-up (2026-11-10)

GoogleSQL table diffs emit SET ON DELETE for action-only changes on existing
IN PARENT relationships. An explicit IN PARENT relationship defaults to NO
ACTION when the action is removed. Legacy catalogs can change between explicit
CASCADE and NO ACTION with no INTERLEAVE_TYPE. Parent, relationship kind and
mixed changes require separate migrations. This uses the existing table specifics,
XML representation and resolver, with no catalog query or public API changes.

Executed SpannerTableInterleaveTest (6 passed), then:

```
.\gradlew.bat :sqlapp-core-spanner:test :sqlapp-command:test --tests '*TransferSchemaSqlScriptTest'
```

Spanner: 146 passed, zero failures/errors/skips. Dependent command: 4 passed.
No live database tests were run and no generated source changes were made.

## Same-parent interleave migration follow-up (2026-11-10)

GoogleSQL diffs between IN and IN PARENT with the same parent now emit SET
INTERLEAVE. A CASCADE target first establishes IN PARENT with NO ACTION and
then emits SET ON DELETE CASCADE as a separate operation. Parent changes and
unrelated table changes are rejected. Existing Schema XML and dialect resolution
are used without changes to the catalog queries or public APIs.

Executed SpannerTableInterleaveTest (8 passed), then:

```
.\gradlew.bat :sqlapp-core-spanner:test :sqlapp-command:test --tests '*TransferSchemaSqlScriptTest'
```

Spanner: 148 passed, zero failures/errors/skips. Dependent command: 4 passed.
No live database tests were run and no generated source changes were made.

## Local emulator round trip (2026-11-10)

Using the cached GoogleSQL emulator image (ad54472fe7b1), the disposable
Testcontainers test reads a source schema via JDBC, serializes it to XML,
executes generated DDL in a separate emulator database, and verifies the
reloaded metadata. It covers TTL, primary key ordering, a partial index,
SEARCH partitioning and ordering, and the two-statement interleave migration.

The emulator exposed exact JDBC label casing and synthetic NOT NULL CHECK
entries that cannot be recreated as named constraints. These are handled in
the Spanner readers without new catalog queries. Empty view security metadata
is retained as UNKNOWN so introspection succeeds and SQL generation requires
an explicit INVOKER or DEFINER choice. Public APIs, XML format and the dialect
resolver are unchanged.

```
.\gradlew.bat :sqlapp-core-spanner:test
.\gradlew.bat :sqlapp-core-dialect-test:dockerTest --tests '*spanner.SpannerMetadataReaderTest' -PtestcontainersReuse=false
```

Spanner unit tests: 149 passed. Emulator tests: 4 passed after the fixes.
The hosted Google Cloud service and vector option recreation were not tested.
No generated source changes were made.

## Vector index emulator round trip (2026-11-10)

The GoogleSQL column reader retains vector length from an annotation in
SPANNER_TYPE or the existing COLUMN_OPTIONS value. Conflicting or invalid
lengths fail explicitly. CREATE VECTOR INDEX requires an explicit distance
metric when the catalog does not provide one. This uses the existing model,
XML, resolver and catalog queries without adding a driver dependency.

The opt-in JDBC round trip now recreates a vector index with a fixed-length
embedding, WHERE filter, stored column, distance metric, tree depth and leaf
count. Missing catalog options are supplied from known fixture settings before
XML serialization. The target database's admin DDL confirms the three options.
The hosted Google Cloud catalog option path still needs verification.

```
.\gradlew.bat :sqlapp-core-spanner:test :sqlapp-core-dialect-test:dockerTest --tests '*spanner.SpannerMetadataReaderTest' -PtestcontainersReuse=false
.\gradlew.bat :sqlapp-core-dialect-test:dockerTest --tests '*spanner.SpannerMetadataReaderTest' -PtestcontainersReuse=false
```

Spanner unit tests: 151 passed. The initial integration run could not start
because the local Docker backend was unavailable. After restarting Rancher
Desktop, all 4 emulator tests passed. No external databases were contacted
and no generated sources were changed.

## Named-schema emulator round trip (2026-11-10)

The GoogleSQL integration suite now recreates two named schemas with
same-named parent tables, child tables and indexes. The test provisions the
schema namespaces, loads their objects via JDBC, restores the model through
XML and executes the generated schema-qualified table and index DDL in a
second emulator database. It verifies parent-first creation and compares
per-schema delete actions, index key order and the presence of the predicate.
This adds integration coverage without changes to the dialect or public APIs.

```
.\gradlew.bat :sqlapp-core-dialect-test:dockerTest --tests '*SpannerMetadataReaderTest.recreatesSameNamedTablesAndIndexesInSeparateSchemas' -PtestcontainersReuse=false
.\gradlew.bat :sqlapp-core-dialect-test:dockerTest --tests '*spanner.*' -PtestcontainersReuse=false
```

Focused test: 1 passed. Full Spanner emulator scope: 6 passed. No external
databases were contacted and no generated sources were changed.

## Schema factory round trip (2026-11-10)

The GoogleSQL registry now uses the shared CREATE schema factory with one
Spanner-specific rule. A null or empty schema name denotes the existing default
namespace, so only its objects are created. A named namespace is created before
its objects. The emulator tests now call the normal Schema CREATE entry point
for both kinds of schema and execute all generated statements in the target DB.
No metadata, resolver, dependency or public API changes are required.

```
.\gradlew.bat :sqlapp-core-spanner:test --tests '*SpannerCreateSchemaFactoryTest'
.\gradlew.bat :sqlapp-core-spanner:test :sqlapp-core-dialect-test:dockerTest --tests '*spanner.*' -PtestcontainersReuse=false
```

Focused test: 1 passed. Spanner unit suite: 152 passed. Local emulator suite:
6 passed. No hosted databases were contacted or generated sources modified.

## Stored index column migrations (2026-11-10)

For GoogleSQL table diffs that change only the stored columns of a secondary
or vector index, the dialect emits ADD or DROP STORED COLUMN on the existing
index. Stored-column drops precede table column changes and adds follow them.
Managed and constraint-owned indexes reject direct changes. SEARCH indexes
keep the replacement path because the local emulator rejected ALTER SEARCH
INDEX. Key, filter, type and option changes also keep the replacement path.
The public API, XML format, reader queries and resolver are unchanged.

Focused SpannerIndexMigrationTest: 8 passed. The full Spanner unit task passed
all 154 tests. After recovering local Docker and retrying with a fresh Gradle
process, all 6 Spanner emulator tests passed, including applying stored-column
adds and drops to secondary and vector indexes and reading the result back.

```
.\gradlew.bat :sqlapp-core-spanner:test --tests '*SpannerIndexMigrationTest'
.\gradlew.bat :sqlapp-core-spanner:test
.\gradlew.bat --no-daemon :sqlapp-core-dialect-test:dockerTest --tests '*spanner.*' -PtestcontainersReuse=false
```

The emulator retry used the local Docker named pipe. No hosted database tests
were run or generated sources changed.

## Vector search option migration (2026-11-10)

A GoogleSQL table diff limited to a vector index's DISABLE_SEARCH setting
emits ALTER VECTOR INDEX SET OPTIONS with true, false or NULL. It preserves
the existing index and works with snapshots that lack the distance metric.
Other changes still use the existing migration paths. The public API,
Schema XML, catalog reader and resolver are unchanged.

The focused index migration test class passed all 9 tests. Applying the new
SET OPTIONS statement to the local emulator failed because that action is
unsupported there. The new behavior has SQL-generation coverage and needs a
hosted GoogleSQL service that supports the action for execution verification.
The subsequent full regression run passed 155 unit tests and 6 emulator tests.

```
.\gradlew.bat --no-daemon :sqlapp-core-spanner:test :sqlapp-core-dialect-test:dockerTest --tests '*spanner.*' -PtestcontainersReuse=false
```

No hosted databases were contacted or generated sources changed.

## TTL and column migration ordering (2026-11-10)

GoogleSQL table diffs can combine adding or dropping a row deletion policy
with column changes. The policy is dropped before its referenced column is
removed, or added after the new column exists. A target that retains a policy
but lacks its timestamp column is rejected. Replacing a policy alongside a
column change still requires separate migrations. No catalog queries, resolver
boundaries, public APIs or XML formats changed.

Focused verification passed 4 unit tests and the emulator round trip. The
full regression passed 156 unit tests and 6 local emulator tests. The emulator
applied both directions of the TTL/column migration and confirmed the results
through JDBC metadata.

```
.\gradlew.bat --no-daemon :sqlapp-core-spanner:test --tests '*SpannerRowDeletionPolicyTest' :sqlapp-core-dialect-test:dockerTest --tests '*SpannerMetadataReaderTest.recreatesInterleaveTtlAndIndexesFromSchemaXml' -PtestcontainersReuse=false
.\gradlew.bat --no-daemon :sqlapp-core-spanner:test :sqlapp-core-dialect-test:dockerTest --tests '*spanner.*' -PtestcontainersReuse=false
```

No hosted databases were contacted or generated sources changed.

## Modeled table synonyms (2026-11-10)

GoogleSQL generates ALTER TABLE ADD or DROP SYNONYM for explicitly modeled
Schema synonyms. Schema CREATE emits aliases after their tables. The target
schema defaults to the synonym's schema. Cross-schema aliases and database
links are rejected. The documented catalog does not identify the table that
a synonym references, so automatic metadata discovery is outside this path.
Existing Schema XML and SQL factory APIs carry the values. There are no new
reader queries, dependencies or resolver changes.

The two focused factory tests passed, including an XML round trip. The named
schema emulator test executed the generated ADD and DROP statements, read the
row by its alias, and verified the original table and row after the alias was
removed. The full regression passed 158 unit tests and 6 local emulator tests.

```
.\gradlew.bat --no-daemon :sqlapp-core-spanner:test --tests '*SpannerSynonymFactoryTest' :sqlapp-core-dialect-test:dockerTest --tests '*SpannerMetadataReaderTest.recreatesSameNamedTablesAndIndexesInSeparateSchemas' -PtestcontainersReuse=false
.\gradlew.bat --no-daemon :sqlapp-core-spanner:test :sqlapp-core-dialect-test:dockerTest --tests '*spanner.*' -PtestcontainersReuse=false
```

No hosted databases were contacted or generated sources changed.

## Sequence skip range migrations (2026-11-10)

GoogleSQL ALTER SEQUENCE now sets or clears the skip range from a before/after
Sequence diff. Specify the existing sequence's DDL family as CLAUSES or OPTIONS
in SEQUENCE_DDL_STYLE because the catalog does not distinguish them and Spanner
requires the original family. The generator reuses CREATE range validation and
rejects other sequence changes, including start-counter changes. No metadata
queries, resolver boundaries or XML schema changes were needed.

Two focused unit tests and one focused emulator test passed. The emulator
created sequences in both styles, applied both the set and clear differences,
and read back the range and unchanged configured start counter. The full
regression passed 160 unit tests and 7 local emulator tests.

```
.\gradlew.bat --no-daemon :sqlapp-core-spanner:test --tests '*SpannerAlterSequenceFactoryTest' :sqlapp-core-dialect-test:dockerTest --tests '*SpannerMetadataReaderTest.changesSequenceSkipRangesUsingTheOriginalDdlStyle' -PtestcontainersReuse=false
.\gradlew.bat --no-daemon :sqlapp-core-spanner:test :sqlapp-core-dialect-test:dockerTest --tests '*spanner.*' -PtestcontainersReuse=false
```

No hosted databases were contacted or generated sources changed.

## Identity column skip range migrations (2026-11-10)

GoogleSQL table diffs limited to an existing scalar INT64 identity column's
skip bounds emit ALTER IDENTITY SET SKIP RANGE or SET NO SKIP RANGE. Primary
keys are supported. CREATE and ALTER share paired-bound and ordering checks.
Other changes to that column, including its start counter, remain separate.
No reader, XML format, or resolver changes were needed.

The focused ALTER TABLE tests passed (8). The initial integration command
was blocked by an unrelated compilation error in the command module's working
copy. Using the already-built command JAR, the full Spanner regression passed
162 unit tests and 8 emulator tests. The new emulator test applied and cleared
a range on an identity primary key and checked the configured start value after
each change.

```
.\gradlew.bat --no-daemon '-Dorg.gradle.java.compile-classpath-packaging=true' :sqlapp-core-spanner:test :sqlapp-core-dialect-test:dockerTest --tests '*spanner.*' -PtestcontainersReuse=false -x :sqlapp-command:compileJava -x :sqlapp-command:jar
```

No hosted databases were contacted or generated sources changed.

## Commit timestamp ON UPDATE migrations (2026-11-10)

GoogleSQL table diffs limited to ON UPDATE on an existing non-key scalar
TIMESTAMP column now emit SET ON UPDATE or DROP ON UPDATE. SET requires
allow_commit_timestamp=true and matching PENDING_COMMIT_TIMESTAMP() default
and update expressions. DROP preserves the existing default. The dialect
continues to reject mixed changes to the same column. No reader queries,
resolver boundaries, dependencies, public APIs or XML formats changed.

The focused ALTER TABLE tests passed (10). The first integration build was
blocked by an unrelated SQLite test compilation error. The isolated Spanner
integration test then passed with a temporary source filter. The final full
regression ran without that filter and passed 164 unit and 9 emulator tests.
The new integration test applied both operations and verified the retained
column default after removal.

```
.\gradlew.bat --no-daemon :sqlapp-core-spanner:test :sqlapp-core-dialect-test:dockerTest --tests '*spanner.*' -PtestcontainersReuse=false
```

No hosted databases were contacted or generated sources changed.

## Combined commit timestamp expressions (2026-11-11)

A GoogleSQL column diff can set or remove DEFAULT and ON UPDATE together in one
ALTER COLUMN statement. The emulator verified the paired changes and retention
of NOT NULL and allow_commit_timestamp. The full Spanner regression passed
165 unit tests and 9 emulator tests. The ordinary build was blocked by unrelated
TransferFileImportService compilation errors. The passing run used the existing
command JAR via the same Gradle exclusions as the identity range verification.

## Migration boundary and registry audit (2026-11-11)

GoogleSQL DROP SCHEMA and DROP SEQUENCE now use native dialect factories,
including optional IF EXISTS. Schema deletion requires a named user schema;
contained objects must be removed first. Sequence start counters reject values
outside positive INT64. TTL validation requires the original column-name casing,
and policy replacement can follow column additions while retaining the old
timestamp column until a later migration.

Added a regression test for automatic Added/Deleted difference routing of
schemas, sequences and table synonyms. The test uses models with their Spanner
dialect set, as metadata snapshots do. The first test setup omitted that dialect
and failed with a generic SQL builder; after correcting the setup, the focused
test passed. No registry behavior or shared API changes were necessary.

The final full run passed 171 Spanner unit tests and 9 local emulator tests,
including sequence and empty-schema deletion and TTL replacement with a new
column. The run used the existing command JAR to isolate unrelated command
working-copy changes:

```
.\gradlew.bat '-Dorg.gradle.java.compile-classpath-packaging=true' :sqlapp-core-spanner:test :sqlapp-core-dialect-test:dockerTest --tests '*spanner.*' -PtestcontainersReuse=false -x :sqlapp-command:compileJava -x :sqlapp-command:jar
```

The current changes affect only the Spanner module's tests and this status
record. The audited factories target GoogleSQL services supporting the stated
features; DialectResolver and older catalog handling are unchanged. No hosted
database was accessed, and no generated sources were intentionally changed.
Hosted-only vector index option alteration remains unverified by the emulator.

## Name-only table migrations (2026-11-11)

SpannerAlterTableFactory now emits native ALTER TABLE RENAME TO for an explicit
original/target Table diff limited to the table name. It compares the target
clone with the original name restored, so derived index ownership names do not
appear as structural changes. The original models remain untouched. Mixed
column/index changes, schema moves and blank names fail explicitly. No alias is
created, and view statements are not rewritten. Collection-level matching does
not infer renames from an added and removed table.

This targets GoogleSQL services supporting table renaming. The existing
single-version DialectResolver, metadata queries, dependencies, public APIs and
Schema XML format are unchanged. See the
[table name reference](https://docs.cloud.google.com/spanner/docs/table-name-synonym).

The focused ALTER TABLE test class passed 13 tests. The first emulator run
exposed derived index ownership changes; after the comparison fix and unit
coverage for real index changes, all 173 Spanner unit tests and the focused
emulator test passed. The emulator verified a reserved-word target name in a
named schema, retained row data and index metadata, and failure to query the old
name. Hosted service execution and view/foreign-key/interleave dependency
behavior were not tested in this pass.

```
.\gradlew.bat :sqlapp-core-spanner:test --tests '*SpannerAlterTableFactoryTest'
.\gradlew.bat '-Dorg.gradle.java.compile-classpath-packaging=true' :sqlapp-core-spanner:test :sqlapp-core-dialect-test:dockerTest --tests '*SpannerMetadataReaderTest.renamesNamedTablesWithoutRecreatingTheirDataOrIndexes' -PtestcontainersReuse=false -x :sqlapp-command:compileJava -x :sqlapp-command:jar
```

The integration run used the existing command JAR. No hosted database was
accessed and no generated sources were intentionally changed.

## Rename dependency verification (2026-11-11)

Extended the existing named-table rename integration test with an INTERLEAVE IN
PARENT child. After renaming, the child retains its rows, reports the new parent
name and CASCADE action, accepts another row under the existing parent and
rejects a row whose parent is absent. Existing table data and indexes are still
checked. This adds no production implementation, reader queries, public APIs,
resolver changes or dependencies.

A separate foreign-key target rename test exposed an emulator internal
GOOGLESQL_RET_CHECK failure in foreign_key_validator.cc when comparing the old
and new referenced table names. That exact observed failure aborts only this
test with an explicit skip reason. Other execution errors fail normally; when
the emulator accepts the rename, the test checks updated reference metadata,
existing rows and subsequent valid/invalid foreign-key inserts. No generated
DDL workaround or constraint removal was introduced. The documented service
behavior remains unverified on a hosted database.

The focused pair completed with one pass and one skip. The full Spanner emulator
suite completed with 10 passes, one skip and no failures:

```
.\gradlew.bat '-Dorg.gradle.java.compile-classpath-packaging=true' :sqlapp-core-dialect-test:dockerTest --tests '*spanner.*' -PtestcontainersReuse=false -x :sqlapp-command:compileJava -x :sqlapp-command:jar
```

The existing command JAR was used. Unit tests were not rerun in this test-only
follow-up; the preceding implementation pass recorded 173 unit passes. README
now distinguishes the verified INTERLEAVE behavior from the foreign-key emulator
limitation. No hosted databases or production data were accessed, and no
generated sources were intentionally changed.

## Cross-schema named foreign-key identity (2026-11-12)

Fixed ForeignKeyConstraint.getRelatedTableSchemaName in sqlapp-core so an
explicit target schema is retained when referenced columns are not bound to an
actual Table. Previously the reference collection could fall back to its owner
and mask that value. Bound target columns continue to determine their schema,
preserving the existing resolved-model behavior. SpannerTableOrder now checks
schema as well as table name before accepting a resolved target object.

The core fix cannot safely remain in the dialect: the explicit schema was
already hidden by the shared getter and could be serialized incorrectly before
Spanner received the model. The class is final and the same getter is used by
shared SQL/model operations, transfer schema preparation/name mapping and bulk
migration drafts. No vendor conditionals or alternate identity model were added.
Public signatures and XML format remain unchanged; previously incorrect schema
values now retain the user's explicit target. Javadoc and README document the
unresolved/resolved precedence.

Focused core tests passed 7 cases, including explicit schema preservation, XML
round-trip, owner fallback without a hint and retained bound-column precedence.
Focused Spanner DROP tests passed 9 cases, including same-named tables in Sales
and Archive. Initial verification required an XML exception declaration in the
test; a subsequent build encountered missing intermediate class files and passed
when retried in a fresh Gradle process. No unrelated source was changed to work
around that build failure.

Broader unit checks passed: core 1,285, Spanner 192, PostgreSQL 389 and MySQL 306.
The affected command tests passed 24 cases (TransferDirectSchemaTest,
TransferDirectNameMappingTest and BulkMigrationJobDraftBuilderTest), using an
embedded database where needed. The focused emulator case created Sales.Parent
referencing Archive.Parent, then used detached name-only models to remove the
referencing Sales table first. Final Spanner emulator regression passed 18 cases,
with one existing foreign-key-target rename case explicitly skipped for the
observed emulator validator failure.

```
.\gradlew.bat --no-daemon '-Dorg.gradle.java.compile-classpath-packaging=true' :sqlapp-core:test :sqlapp-core-spanner:test :sqlapp-core-postgres:test :sqlapp-core-mysql:test
.\gradlew.bat --no-daemon '-Dorg.gradle.java.compile-classpath-packaging=true' :sqlapp-command:test --tests '*TransferDirectSchemaTest' --tests '*TransferDirectNameMappingTest' --tests '*BulkMigrationJobDraftBuilderTest'
.\gradlew.bat --no-daemon '-Dorg.gradle.java.compile-classpath-packaging=true' :sqlapp-core:test
.\gradlew.bat --no-daemon '-Dorg.gradle.java.compile-classpath-packaging=true' :sqlapp-core-dialect-test:dockerTest --tests '*spanner.*' -PtestcontainersReuse=false
```

Modified modules are sqlapp-core, sqlapp-core-spanner and the integration tests
in sqlapp-core-dialect-test. PostgreSQL, MySQL and command source were unchanged.
The Spanner execution target is GoogleSQL with named-schema support; resolver
boundaries, metadata queries and older catalog handling are unchanged. No
dependency or generated-source changes were intentional. Unlike earlier runs,
command compilation and JAR tasks were not excluded. No hosted or production
database was accessed. Hosted execution and cross-database references remain
outside the verified scope; CREATE still requires reliably resolved references.

## Detached named foreign-key ordering (2026-11-12)

SpannerTableOrder now verifies that a resolved referenced Table matches the
foreign key's named target before using that object as a dependency. Unresolved
reference columns can otherwise fall back to their owning Table, causing the
real dependency to be mistaken for a self reference. In that case the existing
catalog/schema/name lookup resolves the target from the supplied batch. Bound
column references remain usable when the redundant target name is omitted.

The new detached-table test first needed a Column object for its source-column
list, then reproduced incorrect DROP ordering with a named target. After the
fix, the focused DROP TABLE class passed 8 tests. A second test preserves the
previous bound-column behavior with an omitted target name. The emulator test
creates an actual foreign-key pair, then deletes it using detached same-schema
Table models whose reference target is named rather than column-bound.

Final regression passed 191 unit tests and 17 emulator tests, with one existing
foreign-key-target rename case explicitly skipped for the observed emulator
validator failure:

```
.\gradlew.bat :sqlapp-core-spanner:test --tests '*SpannerDropTableFactoryTest'
.\gradlew.bat --no-daemon '-Dorg.gradle.java.compile-classpath-packaging=true' :sqlapp-core-spanner:test :sqlapp-core-dialect-test:dockerTest --tests '*spanner.*' -PtestcontainersReuse=false -x :sqlapp-command:compileJava -x :sqlapp-command:jar
```

Affected modules are sqlapp-core-spanner and its integration tests in
sqlapp-core-dialect-test. README documents detached same-schema DROP ordering.
This changes dependency ordering only; CREATE still requires referenced tables
and columns to resolve reliably. Cross-schema detached-name resolution was not
verified in this pass. The target remains GoogleSQL with the existing resolver,
catalog queries and older metadata handling unchanged. No public API, XML format,
dependency or shared model implementation changed. The run used the existing
command JAR. No hosted database or production data was accessed, no generated
sources were intentionally changed, and hosted execution remains unverified.

## Direct DROP batch dependency ordering (2026-11-12)

SpannerDropTableFactory now applies the existing dependency order in reverse to
both table collections and deletion-difference collections. This extends the
Schema-difference ordering to callers using the factory directly. Each table's
modeled user indexes are still dropped immediately before that table. Duplicate
identities and dependency cycles fail before returning a plan. Shared helper
diagnostics now describe table dependencies without incorrectly labeling every
operation CREATE.

The first new unit test reproduced parent-before-child deletion through the
direct collection API. The focused DROP TABLE class then passed 6 tests,
including both collection entry points, FK-only ordering and duplicate/cycle
rejection. The emulator test reads actual table metadata and removes an indexed
INTERLEAVE child/parent pair through the table collection API, then an indexed
FK-only pair through deletion differences. Both schemas are empty afterwards.

The first emulator attempt failed during Docker environment initialization.
Docker was reachable independently; rerunning in a fresh Gradle process with
--no-daemon passed. Final regression passed all 189 unit tests and 16 emulator
tests, with one existing foreign-key-target rename case explicitly skipped for
the observed emulator validator failure:

```
.\gradlew.bat :sqlapp-core-spanner:test --tests '*SpannerDropTableFactoryTest'
.\gradlew.bat --no-daemon '-Dorg.gradle.java.compile-classpath-packaging=true' :sqlapp-core-spanner:test :sqlapp-core-dialect-test:dockerTest --tests '*spanner.*' -PtestcontainersReuse=false -x :sqlapp-command:compileJava -x :sqlapp-command:jar
```

Affected modules are sqlapp-core-spanner and its integration tests in
sqlapp-core-dialect-test. README documents the collection paths. This targets
GoogleSQL table deletion using modeled INTERLEAVE/FK relationships; the existing
resolver and older metadata queries are unchanged. No public API, XML format,
dependency or shared implementation changes were made. Dependencies outside the
batch are not removed, and table deletion still removes data without an atomic
transaction guarantee. The run used the existing command JAR. No hosted database
or production data was accessed, no generated sources were intentionally changed,
and hosted execution remains unverified.

## Foreign-key-only Schema dependency ordering (2026-11-11)

SpannerAlterSchemaFactory now selects the existing Spanner dependency order when
changed tables contain foreign keys, even without INTERLEAVE metadata. Added
tables are created after their referenced tables; deleted referencing tables
are removed first. Modified tables retain their existing phase. Self references
do not form a cross-table cycle. Mutually dependent table batches are rejected
explicitly and need separate constraint migrations. No new ordering algorithm
or shared implementation was introduced.

The first new unit test reproduced incorrect creation order for an unordered
three-table FK-only chain. After extending the ordering trigger, the focused
Schema test class passed 6 tests, including valid self references and rejection
of mutual foreign-key cycles during both addition and deletion. The emulator
test created the three-table chain from a Schema diff, inserted valid rows,
verified foreign-key enforcement, then read metadata and removed the chain
through the reverse Schema diff.

Final regression passed all 186 unit tests and 15 emulator tests, with one
existing foreign-key-target rename case explicitly skipped for the observed
emulator validator failure:

```
.\gradlew.bat :sqlapp-core-spanner:test --tests '*SpannerAlterSchemaFactoryTest'
.\gradlew.bat '-Dorg.gradle.java.compile-classpath-packaging=true' :sqlapp-core-spanner:test :sqlapp-core-dialect-test:dockerTest --tests '*spanner.*' -PtestcontainersReuse=false -x :sqlapp-command:compileJava -x :sqlapp-command:jar
```

Affected modules are sqlapp-core-spanner and its integration tests in
sqlapp-core-dialect-test. README was updated. This targets GoogleSQL services
supporting the modeled foreign-key syntax; the existing resolver and older
catalog behavior are unchanged. There are no public API, XML format, dependency
or reader-query changes. External dependencies remain outside the generated
table batch, and no data transfer or transaction guarantee is added. The run
used the existing command JAR. No hosted database or production data was accessed,
no generated sources were intentionally changed, and hosted execution remains
unverified.

## Indexed table deletion (2026-11-11)

Added SpannerDropTableFactory and its registry mapping. Direct Table drops and
Schema differences now compose existing native index drops before DROP TABLE.
Ordinary, SEARCH and VECTOR indexes and modeled non-primary UNIQUE constraints
are handled. Duplicate index/constraint names generate one drop. Managed FK
indexes are excluded, including when a same-name modeled UNIQUE entry is present.
Primary keys are left to DROP TABLE. IF EXISTS follows the existing option for
both indexes and tables. Standalone constraint-owned index operations retain
their guard; temporary owner models are used only for full-table removal and
the original model is unchanged.

The focused test class passed 3 tests. The initial implementation used detached
index models, exposing the shared name renderer's requirement for an owning
table; attaching them to temporary owner models fixed the failure. Unit coverage
checks all index types, uniqueness deduplication, management flags, identifiers,
options, Schema routing and invalid values. The focused emulator test reads a
table with ordinary, unique, SEARCH, VECTOR and managed foreign-key indexes,
generates its Schema removal and executes the plan twice with IF EXISTS. Only
four user indexes are explicitly dropped; the table and its FK backing index
disappear while the referenced parent remains.

Final regression passed 184 unit tests and 14 emulator tests, with one existing
foreign-key-target rename case explicitly skipped for the observed emulator
validator failure:

```
.\gradlew.bat :sqlapp-core-spanner:test --tests '*SpannerDropTableFactoryTest'
.\gradlew.bat '-Dorg.gradle.java.compile-classpath-packaging=true' :sqlapp-core-spanner:test :sqlapp-core-dialect-test:dockerTest --tests '*spanner.*' -PtestcontainersReuse=false -x :sqlapp-command:compileJava -x :sqlapp-command:jar
```

Affected modules are sqlapp-core-spanner and its integration tests in
sqlapp-core-dialect-test. README was updated. This targets GoogleSQL DROP TABLE;
the existing single-version resolver, metadata queries and catalog compatibility
remain unchanged. No incompatible API, Schema XML or dependency changes were
made. Complete index metadata is needed; unmodeled indexes and external
dependencies remain the caller/service's responsibility. These statements
remove table data and provide no atomicity or rollback guarantee. The integration
run used the existing command JAR. No hosted database or production data was
accessed, no generated sources were intentionally changed, and hosted execution
remains unverified.

## Interleave ordering in Schema differences (2026-11-11)

SpannerAlterSchemaFactory now uses the existing SpannerTableOrder for added
tables and its reverse for deleted tables when the changed set contains an
interleaved table. Modified tables retain their usual phase afterwards. The
shared schema implementation remains in use for table sets without interleave
metadata and for all other object collections. Synonym removal/addition still
surrounds these table operations.

A new three-level unit test initially reproduced child-before-parent CREATE
ordering through the Schema-difference API. After the fix, the focused schema
test class passed 4 tests, including rejection of cyclic dependencies during
both addition and deletion. The focused emulator test applied a three-table
chain from a deliberately unordered Schema, inserted dependent rows, read the
metadata back and generated the reverse difference to remove the tables. The
named schema remained with an empty table collection.

Final regression passed all 181 unit tests and 13 emulator tests, with the
existing foreign-key-target rename case explicitly skipped once for the
observed emulator validator failure:

```
.\gradlew.bat :sqlapp-core-spanner:test --tests '*SpannerAlterSchemaFactoryTest'
.\gradlew.bat '-Dorg.gradle.java.compile-classpath-packaging=true' :sqlapp-core-spanner:test :sqlapp-core-dialect-test:dockerTest --tests '*spanner.*' -PtestcontainersReuse=false -x :sqlapp-command:compileJava -x :sqlapp-command:jar
```

Affected modules are sqlapp-core-spanner and its integration test in
sqlapp-core-dialect-test. README was updated. This applies to GoogleSQL services
supporting the modeled INTERLEAVE syntax; the single-version resolver and older
catalog handling are unchanged. No shared implementation, public API, XML
format, dependency or reader queries changed. Dependencies outside the changed
table set and unrelated dependent objects still require separate handling.
The integration run used the existing command JAR. Hosted service execution
remains unverified; no hosted database or production data was accessed and no
generated sources were intentionally changed.

## Self-referencing table rename comparison (2026-11-11)

SpannerAlterTableFactory now accepts a name-only rename whose modeled self
foreign key follows the new table name. The comparison normalizes that reference
on a target clone only when both original and target references point to their
own table in the same schema. Referenced-column, target-table/schema and deletion
rule changes remain visible and require a separate migration. The supplied
models are unchanged. The generated operation remains one native ALTER TABLE
RENAME TO; no constraints are removed or recreated.

The initial focused test reproduced an erroneous rejection as a constraint
change. After the fix, the ALTER TABLE test class passed 14 tests. The final full
run passed 174 Spanner unit tests and 10 local emulator tests, with the existing
foreign-key-target rename emulator case explicitly skipped once. Unit coverage
checks XML, forward/reverse SQL, unchanged inputs and rejection of real foreign
key changes. The schema-change test uses a column bound to an external table;
an initial setup changed only the schema hint, which the resolved column correctly
overrode.

```
.\gradlew.bat :sqlapp-core-spanner:test --tests '*SpannerAlterTableFactoryTest'
.\gradlew.bat '-Dorg.gradle.java.compile-classpath-packaging=true' :sqlapp-core-spanner:test :sqlapp-core-dialect-test:dockerTest --tests '*spanner.*' -PtestcontainersReuse=false -x :sqlapp-command:compileJava -x :sqlapp-command:jar
```

This affects GoogleSQL rename SQL generation in sqlapp-core-spanner only; README
was updated. No shared model, public API, XML format, dependency, metadata query
or resolver boundary changed. The integration run used the existing command JAR.
Self-reference execution on a hosted service remains unverified. No hosted
database was accessed and no generated sources were intentionally changed.

## Modeled synonym replacement (2026-11-11)

Added SpannerAlterSynonymFactory and its registry mapping. A Synonym diff can
change the target table or alias name, composing the existing native DROP and
ADD factories in order. Both models are validated before returning operations.
Equivalent explicit target-schema hints produce no SQL. Other properties,
namespace moves, cross-schema targets, database links and missing target names
fail explicitly. Direct ALTER without original/target models is rejected.
Schema-level diffs route same-name synonym target changes through this factory.
Alias-name changes use an explicit original/target Synonym diff; collection
matching does not infer the identity of a renamed alias.

Replacement consists of two independent statements. The alias is absent between
them, and an ADD failure can leave it absent. No automatic rollback or atomicity
is promised. Destination existence and the one-synonym-per-table restriction are
checked by the service. The documented catalog still does not provide the target
mapping, so metadata reconstruction of synonyms is unchanged.

The focused synonym class passed 5 tests. The focused emulator test verified
target replacement, alias renaming, query results and retention of both source
tables' data. The final regression passed all 177 unit tests and 11 emulator
tests, with one existing foreign-key rename test skipped for the observed
emulator validator failure:

```
.\gradlew.bat :sqlapp-core-spanner:test --tests '*SpannerSynonymFactoryTest'
.\gradlew.bat '-Dorg.gradle.java.compile-classpath-packaging=true' :sqlapp-core-spanner:test :sqlapp-core-dialect-test:dockerTest --tests '*spanner.*' -PtestcontainersReuse=false -x :sqlapp-command:compileJava -x :sqlapp-command:jar
```

Affected modules are sqlapp-core-spanner and the Spanner integration test in
sqlapp-core-dialect-test. README was updated. This applies to GoogleSQL services
supporting table synonyms; the existing single-version resolver is unchanged.
There are no incompatible API, Schema XML, dependency or reader-query changes.
The run used the existing command JAR. Hosted service execution remains
unverified; no hosted database or production data was accessed and no generated
sources were intentionally changed.

## Schema synonym dependency ordering (2026-11-11)

Added SpannerAlterSchemaFactory to compose changed synonym drops before the
shared schema migration and synonym additions afterwards. All changed synonyms
are processed together, allowing two aliases to swap tables without violating
the one-synonym-per-table restriction. A replacement can remove the old target
table and create its new target between the alias operations. Unchanged aliases
are not touched. The existing shared schema implementation still generates all
other object operations; shared code was not changed.

Two new unit tests verify table replacement ordering and all-drops-before-adds
for alias swaps. The focused tests, including the existing synonym tests, passed
7 tests. The integration test executes a two-alias swap, checks both result sets,
then replaces one target table through an XML-restored Schema diff and checks
the aliases again. Its initial fixture lost the original Schema dialect when
cloned, producing generic quoted DROP TABLE SQL. Explicitly retaining the
Spanner dialect on that fixture corrected the setup. The focused emulator test
then passed. Final regression passed 179 unit tests and 12 emulator tests, with
one existing foreign-key-target rename test skipped for the observed emulator
validator failure.

```
.\gradlew.bat :sqlapp-core-spanner:test --tests '*SpannerAlterSchemaFactoryTest' --tests '*SpannerSynonymFactoryTest'
.\gradlew.bat '-Dorg.gradle.java.compile-classpath-packaging=true' :sqlapp-core-spanner:test :sqlapp-core-dialect-test:dockerTest --tests '*spanner.*' -PtestcontainersReuse=false -x :sqlapp-command:compileJava -x :sqlapp-command:jar
```

Affected modules are sqlapp-core-spanner and its integration tests in
sqlapp-core-dialect-test. README documents the ordering. GoogleSQL services must
support table synonyms; resolver boundaries and metadata readers are unchanged.
No incompatible public API, XML format or dependency changes were made. This
orders DDL only: table deletion removes its data, no data transfer is inferred,
and alias replacement remains non-atomic. The run used the existing command JAR.
No hosted database was accessed and no generated sources were intentionally
changed.
