# Dialect enhancement continuation

This note is the handoff point for the ongoing H2, SAP HANA, Cloud Spanner,
and Vertica enhancement task. Read this file together with the
`Less-covered database dialects` section of `docs/roadmap.md`.

## Working rules

- Prefer an existing shared Schema property when it represents the database
  concept without losing meaning.
- Keep vendor-only SQL switches in dialect `specifics` when they do not affect
  object identity or relationships.
- Add a shared Schema object only after comparing equivalent concepts in
  other databases.
- Silently ignore unsupported generic options. Reject only contradictory or
  invalid options specific to the selected dialect.
- Add a version-specific Dialect when a feature has a real server-version
  boundary.
- Preserve unrelated working-tree changes. In particular, do not modify
  `gradle.properties`, `sqlapp-core/memo.txt`, or
  `sqlapp-core/.../migration/JobTable.java` as part of this task.

## Completed scope

### H2

- H2 2.x Dialect split and modern scalar types.
- JSON, ENUM, existence clauses, and modern current-value functions.
- Domain CREATE/DROP DDL using the shared `Domain` model.
- Version-specific H2 2.x Domain, Column, and Table metadata SQL:
  `domains_200.sql`, `columns_200.sql`, and `tables_200.sql`.
- H2 1.x metadata SQL remains selected for pre-2.x servers.

### SAP HANA

- BOOLEAN, JSON, and REAL_VECTOR types.
- HANA Cloud version split.
- Vector functions and HNSW vector-index DDL.
- Platform full-text indexes and Cloud fuzzy-search indexes.

### Cloud Spanner

- Primary-key suffix DDL and UNIQUE constraints as unique indexes.
- NULL_FILTERED, STORING, search, and vector indexes.
- Commit timestamps, generated columns, and ON UPDATE expressions.
- Identity columns and bit-reversed-positive sequences.
- Sequence CREATE/read/next-values support.
- View CREATE/read/drop with INVOKER/DEFINER security behavior.
- Table, column, and index storage/locality options.
- Unsupported generic options are ignored.

### Vertica

- Modern UUID type and version split.
- Table/View existence clauses and `CREATE OR REPLACE VIEW`.
- Sequence CREATE/ALTER/DROP and multi-value NEXTVAL generation.
- Vertica IDENTITY syntax including start, increment, and cache.
- IDENTITY metadata recovery from `V_CATALOG.SEQUENCES`.
- Fixed the column metadata joins and column-name filter.

### Shared bug fix

`AbstractColumn.setIdentityCacheSize(int)` incorrectly wrote the identity
start value. It now writes the cache size, with a regression test in
`TableTest`.

## Next work, in priority order

### 1. Add real H2 2.x metadata round-trip tests

The normal H2 test suite passes, but the new metadata queries need a focused
integration test that:

1. creates a Domain;
2. creates a table using normal, generated, and IDENTITY columns;
3. reads the Schema through `H2SchemaReader`;
4. asserts Domain, Table, Column, identity, generation expression, comments,
   default, and ON UPDATE values;
5. regenerates DDL from the result.

Use this test to correct any actual H2 2.x
`INFORMATION_SCHEMA` column-name differences.

### 2. Audit the remaining H2 2.x metadata readers

Review View, ViewColumn, Sequence, Index, UniqueConstraint, CheckConstraint,
ForeignKey, Trigger, and Function readers. Several still use H2 1.x
`INFORMATION_SCHEMA` layouts. Add `_200.sql` resources only where the layout
changed, and retain the old query for H2 1.x.

### 3. Add metadata round trips for SAP HANA features

DDL tests exist for vector and text-search features. Add Reader coverage for
the properties that current HANA catalogs expose without a shared-model
change. Keep the deferred items in `docs/roadmap.md`.

### 4. Add Cloud Spanner metadata integration coverage

Verify round trips for search/vector indexes, locality/storage options,
sequences, identity, generated columns, commit timestamps, and view security.
Use emulator-compatible tests where possible. Do not model interleaving,
change streams, or property graphs until the shared design is agreed.

### 5. Complete the Vertica metadata audit

Verify the corrected `columns.sql` against a real Vertica catalog. Then audit
Table, View, Sequence, constraint, and index readers for current catalog
columns. Projection, segmentation, KSAFE, flex-table, and external-table work
remains deferred because it needs shared Schema design.

## Verification commands

Run focused tests while editing, then the four module suites:

```bat
.\gradlew.bat :sqlapp-core:test --tests com.sqlapp.data.schemas.TableTest
.\gradlew.bat :sqlapp-core-h2:test
.\gradlew.bat :sqlapp-core-saphana:test
.\gradlew.bat :sqlapp-core-spanner:test
.\gradlew.bat :sqlapp-core-virtica:test
```

When Codex runs Gradle, use the repository-local `.gradle-user-home` cache.
The interactive user can run the commands with their normal Gradle cache.

## Known limitations

- H2 2.x metadata SQL has passed the module tests but does not yet have the
  focused metadata round-trip described above.
- Vertica does not expose the original IDENTITY start value after values have
  been generated; only the current distributed value is available.
- Features requiring new shared objects are listed in `docs/roadmap.md` and
  should not be represented as unrelated strings merely to emit DDL.
