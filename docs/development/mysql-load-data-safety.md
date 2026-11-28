# MySQL-family LOAD DATA safety

## Scope and behavior

The MySQL executor now owns the common LOAD DATA implementation. MariaDB overrides
only Connector/J input-stream binding; Aurora MySQL inherits the MySQL executor.
No shared BooleanConverter, metadata type mapping, resolver or UPSERT match-key
contract changes are included.

- Boolean values are encoded as `1` / `0`; null remains `\N`.
- BIT values are validated against the modeled width (default 1, maximum 64),
  loaded through user variables and converted with `CAST(CONV(...,2,10) AS UNSIGNED)`.
  Invalid/wide values fail rather than being truncated. BIT(64) unsigned maxima
  never pass through a signed Java long.
- Binary values retain hex/UNHEX conversion. BigDecimal uses plain decimal text.
- Escape syntax uses `X'5C'` and works with
  `NO_BACKSLASH_ESCAPES`.
- Every SQL warning is a failure, including warnings whose details are suppressed
  by `max_error_count=0`. The executor checks JDBC warnings and the server warning
  count immediately after loading, before unrelated SQL or stream detachment.
- The input must be fully consumed and the large affected-row count must equal
  the number of encoded rows. Staging-load failure prevents final UPSERT DML.

## Transactions and failure contract

The public executor signature, returned long count and BulkOption defaults stay
unchanged. `useTransaction` defaults to false. Warning detection can therefore
report a failure **after partial rows have already committed** under autocommit.
A failure is not an instruction to retry automatically.

`useTransaction=true` is newly supported for verifiable InnoDB targets. A single
information_schema query rejects unknown engines, other engines (including NDB)
and temporary tables before loading. With autocommit initially true the executor
starts a transaction, validates the load, detaches/closes resources, then commits.
On failure it rolls back only the transaction it owns. With autocommit initially
false it never commits, rolls back or changes autocommit; the caller owns recovery.
The InnoDB check concerns the target, not nontransactional trigger side effects.
Concurrent DDL/engine changes and trigger writes require application coordination.

SQLException messages include encoded/affected counts, input-completion status
and outcome: AUTOCOMMIT, CALLER_TRANSACTION, ROLLED_BACK, ROLLBACK_FAILED,
COMMIT_UNKNOWN or COMMITTED. JDBC SQLState/vendor code, causes and cleanup failures
are retained. After rollback failure autocommit is not restored (restoring it could
commit remaining work). Commit failure has an unknown outcome even if a subsequent
rollback succeeds. A reset failure after successful commit is explicitly reported
as COMMITTED. Inspect/discard an uncertain connection rather than retrying blindly.

This does not recover values already converted to null by a Row converter before
LOAD DATA. TINYINT(1)/BooleanConverter policy remains a separate compatibility task.

## Verification

Regression tests were added first: 11 failures reproduced against the old code.
The stream and executor tests cover Boolean/BIT/NULL, oversized BIT, warnings with
matching counts, missing warning details, mismatched counts, incomplete input,
transaction ownership, commit/reset uncertainty, rollback and detach failures,
resource cleanup before commit, engine rejection and staging-failure propagation.
Existing catalog-UPSERT test doubles now simulate real input consumption.
All 504 module tests passed (MySQL 370, MariaDB 109, Aurora MySQL 25).

Commands (Java 25, repository wrapper, offline):

```
.\gradlew.bat :sqlapp-core-mysql:test --tests '*MySqlBulkDataInputStreamTest' --tests '*MySqlBulkInsertSafetyTest' --offline --console=plain
.\gradlew.bat :sqlapp-core-mysql:test :sqlapp-core-mariadb:test :sqlapp-core-aurora-mysql:test --offline --console=plain
```

Disposable local Docker databases: MySQL 5.7.44, 8.0.46, 8.4.11; MariaDB
10.5.29, 11.4.12, 11.8.8. Connector/J 9.7.0 and MariaDB Connector/J 3.5.9.
Checks include Boolean/NULL, BIT(64) maximum, NO_BACKSLASH_ESCAPES, successful
owned commit, duplicate-warning rollback, caller rollback, max_error_count=0
and truncation warnings even when inserted and sent counts match.
Aurora dialects were exercised against upstream MySQL 8.0/8.4; no real AWS Aurora
cluster was available. Older supported releases and MySQL 9 were not available
as local DB images; no new server syntax/version boundary is introduced.
No external/production database or persistent database volume is used.

Probe sources, argument files, disposable-container runner and per-version logs:
`C:/Users/satot/.codex/visualizations/2026/11/17/01a1e752-547f-7360-b6ee-49e6314aacb1/mysql-load-data/`.
