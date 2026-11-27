# Shared SQL generation review

Status as of 2026-11-27: this review is closed for now at the user's request.
The latest fixes are implemented; broader verification remains limited as
described below. This is not a claim that the latest full test run passed.

## Latest fixes and verified scope

- Condition-based `UPDATE_TABLE`, `UPDATE_FOR_APP` and `DELETE_FOR_APP` support
  tables without usable keys. Ordinary keyed UPDATE and DELETE still require a
  usable key, and available keys remain excluded from UPDATE assignments.
- Root-row DELETE assigns parent aliases across multiple ancestor levels and
  qualifies the root-row predicate with the root alias.
- The focused command below passed all seven tests. It includes new regression
  tests and existing parent-row DELETE tests. The new execution test uses an
  in-memory HSQLDB database to verify two-, three- and four-level relationships,
  deletion limited to the selected root, and no deletion for an empty root list.

```powershell
.\gradlew.bat :sqlapp-core:test --tests 'com.sqlapp.data.db.sql.ConditionBasedSqlFactoryTest' --tests 'com.sqlapp.data.db.sql.DeleteByParentRowsFactoryTest' --offline --console=plain
```

## Verification limitations

- The latest `:sqlapp-core:test --offline --console=plain` run failed while
  handling its result files with `java.nio.file.NoSuchFileException` for
  `sqlapp-core/build/test-results/test/binary/in-progress-results-generic.bin`.
  The cause of the missing file was not established. The full run must be
  repeated before treating the latest fixes as fully verified.
- The planned PostgreSQL, HSQLDB and SQLite SQL-generation tests and the
  `GenerateSqlDialectOptionsTest` / `TransferSchemaSqlScriptTest` command tests
  were not executed for these latest fixes. Automatic approval review rejected
  the launch because its usage limit prevented review from completing. This
  was a review-service failure, not a determination that the action was unsafe.
- Earlier successful full-core and dependent-module runs preceded these latest
  fixes and do not substitute for their remaining verification.
- No external or production database was accessed. Live vendor-database
  verification remains outside the completed scope.

## Deferred view-column investigation

No view-generation change was made. A statement such as
`SELECT id AS renamed FROM items` already preserves the view column name, so
emitting the modeled column list unconditionally is not justified.

The synthetic combination of modeled column `renamed` and statement
`SELECT id FROM items` generates a view without the modeled column name. Before
classifying this as a database round-trip defect, establish whether a supported
MetadataReader actually returns that combination for a valid view defined with
an explicit column list. If the reader retains the aliases or complete CREATE
definition, that path is unaffected. Investigate a concrete reader and database
version before deciding whether the reader or SQL generator needs a change.
