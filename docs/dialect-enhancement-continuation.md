# Dialect enhancement continuation

This note records the completed H2, SAP HANA, Cloud Spanner, and Vertica
enhancement work and the remaining environment-dependent boundaries. Read it
together with the `Less-covered database dialects` section of `docs/roadmap.md`.

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

## Database verification completed

### SAP HANA Platform

SAP HANA Express 2.0 integration tests exercise the Platform metadata reader,
sequence-based generated-key propagation, prepared-statement reuse, set-based
migration, lease, repair, checkpoint, and LOB paths. Native IDENTITY without an
explicit associated sequence remains an intentional pre-execution error because
the JDBC driver cannot return an ordered key set for a batch.

### Cloud Spanner emulator

Emulator integration tests exercise generated identity alignment,
prepared-statement reuse, metadata loading, checkpoint read-only behavior, and
lease handling. Search/vector index and locality/storage metadata that require
service features unavailable in the emulator still need a Cloud Spanner service
environment. Do not model interleaving, change streams, or property graphs until
the shared design is agreed.

### Vertica

Vertica CE 25.1 integration tests exercise the corrected metadata reader,
sequence-based generated-key propagation, JDBC generated-key capability probe,
COPY, COPY staging with MERGE, lease, repair, and checkpoint paths. Projection,
segmentation, KSAFE, flex-table, and external-table work remains deferred because
it needs shared Schema design.

## Remaining environment-dependent verification

- Verify HANA Cloud-only `VECTOR_INDEXES` and fuzzy-search metadata against a
  HANA Cloud tenant. HANA Express covers the Platform path but not Cloud-only
  catalog views.
- Verify Cloud Spanner service-only search/vector index, locality/storage,
  sequence, generated-column, commit-timestamp, and view-security round trips.
- Run the Phoenix 5.3.1 multi-row UPSERT and sequence-block path against a real
  Phoenix cluster. The Apache project does not publish an official ready-to-run
  Docker image, so the current coverage is module-level SQL generation, version
  resolution, and sequence-block expansion.
- Re-run supported-database integration suites when a JDBC driver is upgraded;
  generated-key limitations are driver capabilities and may change independently
  of server SQL support.

## Verification commands

Run focused tests while editing, then the module suites:

```bat
.\gradlew.bat :sqlapp-core:test --tests com.sqlapp.data.schemas.TableTest
.\gradlew.bat :sqlapp-core-h2:test
.\gradlew.bat :sqlapp-core-saphana:test
.\gradlew.bat :sqlapp-core-spanner:test
.\gradlew.bat :sqlapp-core-virtica:test
.\gradlew.bat :sqlapp-core-phoenix:test
```

Run the disposable real-engine integration suites from
`sqlapp-core-dialect-test` one database package at a time to limit memory use:

```bat
.\gradlew.bat :sqlapp-core-dialect-test:dockerTest --tests "com.sqlapp.data.db.dialect.test.db2.*"
.\gradlew.bat :sqlapp-core-dialect-test:dockerTest --tests "com.sqlapp.data.db.dialect.test.oracle.*"
.\gradlew.bat :sqlapp-core-dialect-test:dockerTest --tests "com.sqlapp.data.db.dialect.test.saphana.*"
.\gradlew.bat :sqlapp-core-dialect-test:dockerTest --tests "com.sqlapp.data.db.dialect.test.virtica.*"
.\gradlew.bat :sqlapp-core-dialect-test:dockerTest --tests "com.sqlapp.data.db.dialect.test.sybase.*"
.\gradlew.bat :sqlapp-core-dialect-test:dockerTest --tests "com.sqlapp.data.db.dialect.test.informix.*"
.\gradlew.bat :sqlapp-core-dialect-test:dockerTest --tests "com.sqlapp.data.db.dialect.test.spanner.*"
```

When Codex runs Gradle, use the repository-local `.gradle-user-home` cache.
The interactive user can run the commands with their normal Gradle cache.

## Known limitations

### Generated-key propagation

`JdbcTreeDataSession` prioritizes set-based INSERT/UPSERT execution and
PreparedStatement reuse. It does not fall back to one statement per row when
the JDBC driver cannot return every generated key in input order.

- SAP HANA native IDENTITY can be used only when the Schema `Column` is also
  associated with an explicit sequence that can be preallocated. The current
  JDBC path cannot safely recover and align every value generated by an
  unassociated native IDENTITY in a batch, and HANA does not expose an
  unambiguous backing sequence for that column. Such inserts fail before
  execution with an instruction to associate an explicit sequence. This can be
  revisited if a supported HANA JDBC API or SQL result-set form can return all
  generated values in input order.
- Sybase ASE IDENTITY inserts requiring generated-key propagation are rejected.
  The tested jTDS path cannot combine `RETURN_GENERATED_KEYS` with
  `executeBatch`, while the supported ASE version has neither a suitable
  multi-row VALUES/RETURNING result set nor named sequences for preallocation.
  Explicit key values remain supported. Revisit this when a supported driver
  provides ordered batch generated keys or ASE gains a usable set-based return
  path.
- Vertica IDENTITY inserts requiring generated-key propagation are rejected.
  The current official Vertica JDBC driver throws
  `SQLFeatureNotSupportedException` for
  `prepareStatement(..., RETURN_GENERATED_KEYS)`, and IDENTITY values cannot be
  supplied explicitly. Use a non-identity key column associated with an
  explicit named sequence; sqlapp preallocates it in one operation and performs
  a multi-row INSERT. Revisit native IDENTITY if the JDBC driver adds ordered
  multi-row generated-key support.
- Apache Phoenix has no general IDENTITY equivalent. Use a key column associated
  with an explicit Phoenix sequence. sqlapp reserves a block with
  `NEXT n VALUES FOR` and uses multi-row UPSERT on Phoenix 5.3.1 and later.
  Phoenix 5.3.1 was not exercised against a real server here because the Apache
  project does not publish an official ready-to-run Docker image; only SQL
  generation, version resolution, and sequence-block expansion have module
  coverage.
- HiRDB and Symfoware are intentionally outside this generated-key pass.
- MDB/Access now uses UCanAccess 5.1.6 instead of the removed JDBC-ODBC bridge.
  UCanAccess metadata is adapted to Access's schema-less catalog and its
  requirement that index and key metadata be read per table. AutoNumber key
  retrieval is covered against a newly created Access 2010 database. The
  supplied `AccessSample.accdb` is metadata-read tested, but its Japanese index
  collation is read-only with the current Jackcess version.

- H2 2.x exposes linked-table identity through `INFORMATION_SCHEMA.TABLES`,
  but not its connection definition. The Reader recovers the TableLink object;
  driver, URL, credentials, and remote table name remain unavailable.
- SAP HANA 2.0, Cloud Spanner emulator, and Vertica CE 25.1 metadata queries
  have been executed against their database engines in addition to module
  coverage.
- Set-based SCD2 providers for SAP HANA 2.0, DB2 12.1.5, Oracle Database Free
  23ai, Vertica CE 25.1, SAP ASE 16, and Informix 14.10 have real-engine
  integration coverage. `SetBasedMigrationSnapshotResolver.find` returns
  an empty result for unsupported dialect/version combinations so callers can
  retain the prepared-statement streaming fallback. See `docs/bulk-insert.md`
  for the provider matrix and temporary-table restrictions.
- Vertica does not expose the original IDENTITY start value after values have
  been generated; only the current distributed value is available.
- Features requiring new shared objects are listed in `docs/roadmap.md` and
  should not be represented as unrelated strings merely to emit DDL.
