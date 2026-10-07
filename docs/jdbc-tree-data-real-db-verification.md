# JDBC tree data session real-database verification

The following tests were run on 2026-10-08 with Java 21 and the Gradle Wrapper.
Disposable Testcontainers used local Docker images. Existing databases, production
data and persistent volumes were not used. Tests create and drop fixture tables
inside their containers. No verification containers remained running afterward.

| Image | Test class | Passed |
| --- | --- | ---: |
| `postgres:18.4` | PostgresJdbcTreeDataSessionTest | 10 |
| `mysql:8.4` | MySqlJdbcTreeDataSessionTest | 9 |
| `mariadb:11.8` | MariadbJdbcTreeDataSessionTest | 9 |
| `gvenzl/oracle-free:23-slim-faststart` | OracleJdbcTreeDataSessionTest | 10 |
| `mcr.microsoft.com/mssql/server:2022-CU20-ubuntu-22.04` | SqlServerJdbcTreeDataSessionTest | 2 |

The final run passed all 40 tests, with zero failures, errors or skipped tests.
Image tags record the targets used; mutable tags do not pin their future contents.

## Coverage

Existing tests cover generated-key retrieval and parent-child key propagation,
explicit identity values, multiple root batches, uneven child counts, commit
visibility from another connection, and hierarchical INSERTs with an open SELECT,
as applicable to each test class. Coverage differs between products; the existing
SQL Server test covers a hierarchical INSERT with an open SELECT.

All five products now include
`testSessionSelectCursorSurvivesPeriodicCommitsAndFinalPartialBatch`. It reads and
updates three parents and six children with `rootBatchSize = 2` and
`commitEveryRootBatches = 1`, verifying that:

- The session's root SELECT remains usable after an intermediate commit.
- Child SELECTs match the parents in each batch, processing all three parents
  and six children.
- The final batch containing one parent is processed, yielding two completed
  batches and two commits.
- Both parent and child updates are saved in the database.

The first run exposed a missing `autoCommit=false` setting in the existing SQL
Server test, which failed the current API's precondition. The test setup was
corrected and the rerun passed. The first compilation of the added tests also
required correcting the Oracle fixture initialization argument. This verification
required no production-code changes.

## Rerunning the tests

Run from the repository root with Docker available. The default configuration
disables container reuse and stops and removes test containers afterward.

```powershell
.\gradlew.bat :sqlapp-core-dialect-test:dockerTest `
  --tests '*PostgresJdbcTreeDataSessionTest' `
  --tests '*MySqlJdbcTreeDataSessionTest' `
  --tests '*MariadbJdbcTreeDataSessionTest' `
  --tests '*SqlServerJdbcTreeDataSessionTest' `
  --tests '*OracleJdbcTreeDataSessionTest' --console=plain
```

Results are available in
`sqlapp-core-dialect-test/build/reports/tests/dockerTest/index.html` and
`sqlapp-core-dialect-test/build/test-results/dockerTest/`.

Other products, including DB2, Firebird, Informix and SAP HANA, other versions,
fault injection, concurrent conflicts and performance measurements were outside
this run's scope. The added tests cover periodic commits and SELECT continuity;
they do not port the three H2 regressions for child cursors and custom SELECTs
to every product.
