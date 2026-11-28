# sqlapp DB Dialect review matrix

Use this as a **coverage menu**, not a fixed requirement to run every possible test. Discover actual module capabilities and the user-defined scope first. For each relevant category, record inspected paths, evidence, and status (`checked`, `partial`, `blocked`, `n/a`). Focus on correctness and loss of information.

| Domain | Where to look / typical failure modes | Effective checks |
| --- | --- | --- |
| Version resolution | DialectResolver, hierarchy, minimum/maximum supported DB versions, patch boundaries, capabilities inherited from base classes | Resolve representative versions on each side of feature introduction; inspect feature flags and generated SQL |
| Type registration | DbDataTypeCollection, native names and aliases, JDBC types, size-based overload selection, DataType conversion | Compare DB-supported type inventory with registered types; check `OTHER`, fixed vs varying, length, precision, unsigned, enum, spatial, interval |
| JDBC conversion and binding | ResultSet readers, bind variables, custom converters, `Period`/`Duration`, binary and temporal types | Round-trip DB value → sqlapp value → JDBC bind → DB value; include null, nonzero days, fractional seconds, unusual values |
| Metadata and schema round-trip | Readers for columns, keys, constraints, indexes, sequences, views, partitioning, trigger/routine definitions | Inspect metadata → Schema XML/model → DDL → recreated DB → metadata; verify lengths, scales, ordering, defaults, identity next values |
| SQL generation and capabilities | Select/paging, INSERT, UPDATE, DELETE, MERGE/UPSERT, truncate temp tables, SQL terminators, identifier escaping | Check feature flags are used by the correct factories; test empty collections, all-key tables, computed columns, quoting, pagination |
| DDL and dependency ordering | CREATE/ALTER/DROP table/index/constraint, cascade, rename, foreign keys, dependent objects, schema diff and migration | Check both directions of change, no-op, modified partition counts vs deltas, correct reverse ordering, data preservation |
| Bulk and transactions | LOAD DATA/COPY/streaming, batch keys, retries, staging, warning handling, commit/rollback, transaction isolation | Detect silent coercions, skipped rows, count mismatches, rejected values, cleanup on failure; test on supported versions if possible |
| Version-sensitive DB features | Invisible/generated columns, identity, partitions, JSON, CTE, default expressions, max precision, driver changes | Verify version claims and syntax with authoritative docs or disposable DB, not only current JDBC version |
| Inherited behavior | Common core helpers, subclass Dialects (e.g. MariaDB/Aurora MySQL), default options | Follow a fix or failure to dependent modules and callers; avoid asserting inheritance compatibility without checking |
| Existing tests and gaps | Expectations that merely match broken output, narrow fixtures, missing integration coverage | Inspect assertions for meaning; run targeted tests and report exact number; identify missing regression tests |

## High-yield invariants

- **Value preservation**: read/write and parse/format must preserve units, magnitude, fractional precision, sign, and null semantics.
- **Schema preservation**: recreated DDL must retain type, length/scale/precision, nullability, identity sequence state, constraints, partition values, and semantics.
- **SQL validity**: generated SQL must match the selected DB version; flags and aliases must not silently disable a supported operation.
- **Failure visibility**: data coercion, warning, count mismatch, partial success, and rollback failure must not be silently reported as an ordinary full success.
- **Compatibility**: the version resolver and inherited defaults must not apply newer syntax or JDBC behavior to older advertised versions.

## Verification strategy

1. First identify dangerous transformations by reading code and call paths. For each candidate, produce a minimal *observable* example.
2. If possible, use a short Java/Gradle probe for generated SQL or type/model conversion. Do not modify tracked source for probes in a review-only task.
3. If an authorized disposable DB is already available, execute representative SQL and compare real data/schema; name the precise engine version.
4. Run relevant repository tests. Passing tests do not validate an uncovered scenario. Treat an incorrect expected SQL string as a problem, not a passing proof.
5. Distinguish `actual DB reproduction`, `generated SQL/model reproduction`, `code and official spec`, and `hypothesis` in the report.
