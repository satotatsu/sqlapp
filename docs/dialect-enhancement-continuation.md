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
- H2 2.x metadata round-trip coverage for Domain, Constant, Sequence, Table,
  Column, View, Index, UniqueConstraint, CheckConstraint, ForeignKey,
  Function/argument, Trigger, and linked-table objects.
- Version-specific H2 2.x metadata SQL for Domain, Column, Table, Setting,
  Constant, Sequence, Index, UniqueConstraint, CheckConstraint, ForeignKey,
  Function/argument, Trigger, and TableLink readers.
- H2 1.x metadata SQL remains selected for pre-2.x servers.

### SAP HANA

- BOOLEAN, JSON, and REAL_VECTOR types.
- HANA Cloud version split.
- Vector functions and HNSW vector-index DDL.
- Platform full-text indexes and Cloud fuzzy-search indexes.
- HANA Cloud `VECTOR_INDEXES` metadata reader with index type, distance type,
  and build/search configuration recovery.
- Fixed unique and compressed index-type recovery from `INDEXES`.

### Cloud Spanner

- Primary-key suffix DDL and UNIQUE constraints as unique indexes.
- NULL_FILTERED, STORING, search, and vector indexes.
- Commit timestamps, generated columns, and ON UPDATE expressions.
- Identity columns and bit-reversed-positive sequences.
- Sequence CREATE/read/next-values support.
- View CREATE/read/drop with INVOKER/DEFINER security behavior.
- Table, column, and index storage/locality options.
- Reader recovery for search/vector index types and index options.
- Reader recovery for commit timestamp, vector length, identity, generated
  columns, table/column locality, columnar policy, and full-text dictionary
  options.
- Fixed invalid Index metadata SQL and Column filter/parent identity handling.
- Unsupported generic options are ignored.

### Vertica

- Modern UUID type and version split.
- Table/View existence clauses and `CREATE OR REPLACE VIEW`.
- Sequence CREATE/ALTER/DROP and multi-value NEXTVAL generation.
- Vertica IDENTITY syntax including start, increment, and cache.
- IDENTITY metadata recovery from `V_CATALOG.SEQUENCES`.
- Fixed the column metadata joins and column-name filter.
- Corrected current `V_CATALOG` Table, View, ViewColumn, UniqueConstraint, and
  ForeignKey queries and object recovery.
- Table/View comments, IDs, timestamps, temporary/inheritance properties, and
  constraint comments/enabled state are retained where exposed.

### Shared bug fix

`AbstractColumn.setIdentityCacheSize(int)` incorrectly wrote the identity
start value. It now writes the cache size, with a regression test in
`TableTest`.

## Next work requiring database environments

### 1. Verify SAP HANA metadata against real Platform and Cloud systems

Verify `FULLTEXT_INDEXES`, `VECTOR_INDEXES`, and normal `INDEXES` recovery.
The unit tests cover type mapping, but no SAP HANA server is available in the
normal test environment.

### 2. Add Cloud Spanner metadata integration coverage

Verify round trips for search/vector indexes, locality/storage options,
sequences, identity, generated columns, commit timestamps, and view security.
Use emulator-compatible tests where possible. Do not model interleaving,
change streams, or property graphs until the shared design is agreed.

### 3. Verify Vertica metadata against a real current catalog

Verify the corrected Table, View, Column, ViewColumn, Sequence, and constraint
queries. Projection, segmentation, KSAFE, flex-table, and external-table work
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

- H2 2.x exposes linked-table identity through `INFORMATION_SCHEMA.TABLES`,
  but not its connection definition. The Reader recovers the TableLink object;
  driver, URL, credentials, and remote table name remain unavailable.
- SAP HANA, Cloud Spanner, and Vertica metadata queries have unit/module
  coverage but have not been executed against real database catalogs in this
  workspace.
- Vertica does not expose the original IDENTITY start value after values have
  been generated; only the current distributed value is available.
- Features requiring new shared objects are listed in `docs/roadmap.md` and
  should not be represented as unrelated strings merely to emit DDL.
