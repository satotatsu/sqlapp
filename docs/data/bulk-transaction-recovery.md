# Bulk transaction failure recovery

The existing high-level Bulk UPSERT APIs and options remain unchanged. When an
executor owns a transaction, it now restores autocommit only after successful
transaction completion. If it cannot establish a safe completion boundary, it
stops rather than risking an implicit commit or an automatic retry.

JDBC explicitly commits the current transaction when autocommit mode is changed.
Restoring autocommit after a failed rollback can therefore commit the very rows
that the operation intended to undo. See the [Java 25 Connection specification](https://docs.oracle.com/en/java/javase/25/docs/api/java.sql/java/sql/Connection.html#setAutoCommit(boolean)).

## Outcomes and operator actions

| Outcome | Library behavior | Recovery |
|---|---|---|
| Successful commit | Restore autocommit for the owned transaction | Operation completed; do not repeat it because of a later cleanup error |
| Operation failure and successful rollback | Restore autocommit; preserve the original error | Ordinary configured transient retries remain possible |
| Owned scope closes without completion | Roll back before cleanup SQL; then restore autocommit | Explicit commit is required for success |
| Rollback failure | Keep manual commit mode; skip staging cleanup and autocommit restoration | Recover and verify explicitly, or invalidate/discard the connection |
| Commit failure | Report that commit outcome may be unknown; attempt rollback of remaining work | A later successful rollback does not prove the original commit did not happen |
| Begin/reset failure | Preserve driver details and report the connection/outcome uncertainty | Recover the connection; no automatic retry |

The library does not close or abort a connection it does not own. A failed
rollback may leave pending rows, locks and temporary staging objects. Do not
return that connection to normal use or blindly toggle autocommit. With a pool,
returning a handle is not necessarily physical invalidation: use the data source's
invalidation/recovery mechanism. If rollback is subsequently established as
successful, an operator can explicitly clean up retained staging objects.

The original operation exception stays primary when rollback fails. Rollback
failure details are attached as suppressed exceptions. Commit/begin/reset errors
carry explicit completion context and retain the JDBC SQLState, vendor error code
and original driver error as the cause. Cleanup errors still propagate and can
occur after data was already committed; an exception is not proof that no rows
were stored.

Caller-owned transactions and useTransaction=false are not completed by the
transaction helper. Vendor-specific staging DDL limitations remain unchanged;
this does not make nontransactional engines, implicit-commit DDL or trigger side
effects reversible. For example, Oracle's existing transaction-breaking staging
contract is not changed by this patch.

## Chunk migration and retries

Transactional chunk writes and completion-checkpoint saves now use the same
transaction helper instead of independent unconditional autocommit restoration.
This is necessary because the chunk owns the outer transaction and its inner
Bulk UPSERT executor joins it.

Rollback/begin/reset completion failures and unknown commit outcomes veto
BulkMigrationRetryOption retries, even if a SQLState/error-code selector or
transient exception would ordinarily permit one. The safety check follows causes,
next SQL exceptions and suppressed failures, so a transient checkpoint error
cannot conceal a failed rollback. A checkpoint write acknowledged only by a lost
commit response must be reconciled against durable data/checkpoint state before
an operator resumes the migration.

Ordinary transient chunk/checkpoint failures still retry after rollback and
restoration succeed. Retry defaults, option names and configuration formats are
unchanged. No automatic re-execution is performed after an uncertain completion boundary.
A subsequent rollback can end remaining work; it cannot establish whether a
previous failed commit acknowledgement represented a committed transaction.

## Implementation and compatibility

Changed production code is limited to four classes in sqlapp-core:
BulkUpsertTransaction, BulkUpsertExecutionScope, ChunkedBulkMigrationExecutor and
BulkMigrationRetryOption. Public method signatures, Schema/XML formats and module
dependencies are unchanged. Completion state and retry-failure markers are private
or package-private, not new mandatory public configuration.

Closing an unfinished owned transaction now rolls back instead of implicitly
committing. Low-level callers that previously relied on close to commit must call
commit explicitly. Repeated closes do not repeat cleanup/reset and cannot commit
work subsequently started by the caller. A failed rollback deliberately leaves
staging objects for manual recovery rather than risking a cleanup DDL commit.

## Verification

Tests were added before fixes. The first 13-test reproduction failed nine tests
against the old shared helper, including real HSQLDB pending rows being committed
by close. Additional pre-fix reproductions exposed both chunk paths, unsafe retry
selection and uncertain begin-mode restoration. The final regression coverage
includes real embedded HSQLDB/SQLite transaction effects, injected failed rollback,
server commit followed by lost acknowledgement, caller ownership, cleanup/reset
failures, idempotent close, error preservation and checkpoint consistency.

Java 25, repository Gradle Wrapper, offline. Focused commands included:

```
.\gradlew.bat :sqlapp-core:test --tests '*BulkUpsertTransactionTest' --tests '*BulkMigrationRetryOptionTest' :sqlapp-core-sqlite:test --tests '*ChunkedBulkMigrationTest' --offline --console=plain
.\gradlew.bat :sqlapp-command:test --tests '*BulkMigration*' --offline --console=plain
```

The final broader invocation ran the complete core and MySQL/MariaDB/Aurora
suites, Bulk-related tests for PostgreSQL, SQL Server, DB2, SAP HANA, Vertica,
Informix, Sybase, Oracle, HSQLDB, SQLite, DuckDB, Firebird and H2, plus the
CockroachDB/YugabyteDB module suites. Together with command checks, 2656 tests
passed, 18 existing external-DB tests were skipped and none failed. Exact expanded
commands, per-module counts and logs accompany the local artifacts.

Disposable local DB probes use MySQL 5.7.44/8.0.46/8.4.11 and MariaDB
10.5.29/11.4.12/11.8.8 with Connector/J 9.7.0 / MariaDB Connector/J 3.5.9.
Aurora 8.0/8.4 dialects run on upstream MySQL 8.0/8.4; no real AWS Aurora was used.
Each of eight server/dialect targets verifies:

- A real UPSERT insert conflict after a successful update, followed by an injected
  JDBC rollback failure: no autocommit restoration or staging DROP, then successful
  explicit recovery rollback restores the original data.
- A real server commit followed by an injected lost acknowledgement: committed
  rows remain present and the error states that the outcome is unknown.
- Caller transaction ownership and rollback of an unfinished scope.
- Chunk and completion-checkpoint save failures with injected rollback failure:
  no implicit commit or retry, and manual recovery removes both pending data and
  pending checkpoint changes.

The fault injection occurs at the JDBC call boundary. These are not packet-loss,
server-crash or actual network-partition experiments. Other vendors have embedded
or mock/provider unit coverage, not live remote-server failure tests. No external
or production database or persistent database volume was accessed.

Artifact directory (probes, runner, expanded commands, counts and logs):
`C:/Users/satot/.codex/visualizations/2026/11/17/01a1e752-547f-7360-b6ee-49e6314aacb1/bulk-transaction-fix/`.
