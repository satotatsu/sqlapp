# Spanner local enhancement audit

Scope: GoogleSQL, Java 25, repository Gradle Wrapper. This closes the local
enhancement pass; it is not live-service certification or complete Spanner
feature coverage. No external database, emulator, production data or credentials
were used. The Spanner PostgreSQL interface is outside this dialect.

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
| Column ALTER | Add/drop, defaults, supported nullability, scalar STRING/BYTES length, commit-timestamp options | Identity/generated/key/type/rename and mixed unsupported changes fail |
| Delete all | DELETE FROM ... WHERE TRUE | DML limits/transactions apply; this is not native TRUNCATE |
| Table recreation | Normal/interleaved modeled tables, IN/IN PARENT, deletion rules, parent-first collection order, keys/checks/FKs, unique indexes | Ambiguous type metadata and INTERLEAVE ALTER fail; server key-prefix/ancestry validation remains required |
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
   table options and interleaved tables. Do not infer these from ordinary ALTER.
4. Treat current sequence counter restoration, security grants, database options,
   change streams, newer object types and temporary-table behavior as separate
   features, not automatic consequences of this local audit.

See [README.md](README.md) for individual behavior, examples and source links.
