# JDBC tree data sessions

`JdbcTreeDataSession` processes a single rooted tree of Schema tables. It uses
the Schema foreign keys and dialect SQL factories; no database schema changes
are required.

## Safe execution

Run row processing inside `execute`:

```java
connection.setAutoCommit(false);
new JdbcTreeDataSession(connection, parentTable, childTable, grandchildTable)
    .execute(session -> {
        // Configure the session and select or create rows here.
        Row parent = session.newRow(parentTable);
        parent.put("ID", 1);
        parent.delete();
        Row child = session.newRow(childTable);
        child.put("ID", 11);
        child.delete();
        Row grandchild = session.newRow(grandchildTable);
        grandchild.put("ID", 111);
        grandchild.delete();
    });
```

`execute` owns the connection's transaction, including any existing uncommitted
work. Use a connection dedicated to this operation. Auto-commit must be disabled.
On success it flushes pending rows, commits remaining completed batches, and
closes session resources. On SQL, business, or commit failure it closes session
resources and rolls back uncommitted work. A business exception prevents pending
rows from being flushed. Rollback failures are suppressed on the original
exception. The connection remains open.

Periodic commits configured with `setCommitEveryRootBatches` remain durable if a
later batch fails; rollback cannot undo them. A failure in an after-commit callback
also cannot undo the commit that already succeeded.

`execute` is the only row-processing entry point. Reading the next row, getting
the current row, or creating a row outside its callback fails with an actionable
error. Configuration and SELECT registration may be performed before execution.
The session releases resources after each execution and can be configured and
executed again. Reentrant execution and closing inside the callback are rejected.

`close()` only releases resources and discards buffered rows; it never flushes,
commits, or rolls back. Existing code that depended on try-with-resources to
complete writes must move row processing into `execute`. Cleanup also runs when
flushing or committing fails. A caught batch failure cannot be committed by
returning normally from the callback.

`JdbcTreeDataCopySession.execute(copy -> { ... })` uses the same execution
boundaries for its source and target, processing the final source batch before
completing the target. Both connections must have auto-commit disabled. Separate
source and target connections do not provide an atomic distributed transaction.

## Execution results

`execute` returns an immutable `JdbcTreeDataExecutionResult`. Ignoring the return
value remains valid source code. The return-type change requires recompiling
previously compiled clients.

```java
var result = session.execute(s -> {
    s.newRow(table).put("ID", 1);
});
for (var operation : result.operations()) {
    System.out.println(operation.table() + " " + operation.operation());
    System.out.println(operation.executed());
    System.out.println(operation.committed());
}
```

Each operation retains catalog/schema/table identity and the SQL operation type.
`knownAffectedRows` sums nonnegative JDBC counts; `unknownCounts` counts
`SUCCESS_NO_INFO` responses, `failedCounts` counts `EXECUTE_FAILED` responses,
and `unreportedCounts` counts batch entries without a JDBC response. Known rows
are a lower bound when unknown or unreported counts exist. They are not input
row counts, and omit implicit cascades, trigger effects and SQL issued directly
by application callbacks. MERGE is reported as its actual UPDATE and INSERT;
REPLACE includes DELETE_BY_ROOT_ROWS. INSERT RETURNING counts returned rows.

Executed evidence survives rollback. Committed evidence advances only after
`Connection.commit()` returns successfully, before after-commit callbacks run.
Completed root batches include read-only batches; committed root batches identify
the completed-batch boundary at the last confirmed commit. `commits` counts
confirmed commits in this execution. An empty/read-only execution needs no commit
and reports NOT_REQUIRED.
Child writes participate in periodic and final commits even when the root table
uses `TableOperationMode.NONE`.

Once execution begins, failure keeps the original exception, SQLState and vendor code. Retrieve the
immutable partial report with `JdbcTreeDataExecutionFailure.result(failure)`;
it is attached as a suppressed diagnostic. `results(failure)` returns all directly
attached reports for nested copy execution. The failure context identifies the
write operation, commit, cleanup or business phase. Buffered, unexecuted rows
are not counted. A successful rollback reports ROLLED_BACK for the remaining
transaction; a commit exception or rollback exception reports UNKNOWN because
the durable outcome cannot be inferred. Earlier confirmed commits remain visible.

`JdbcTreeDataCopySession.execute` returns source and target reports separately.
On a shared connection, both reports observe the same commits. Separate
connections still do not provide an atomic distributed transaction.

## Explicit deletion order

At each root batch, default row operations are resolved from the table operation
mode. Rows marked with `row.delete()` (or resolved to `TableOperationMode.DELETE`)
are deleted deepest-table first. Remaining operations run parent first, preserving
generated-key propagation for inserts. This supports multiple levels with ordinary
non-CASCADE foreign keys and batches containing both deletes and inserts.

Only deletion-marked rows in the current batch are deleted. Marking a parent does
not automatically mark its children or query additional descendants. Every
referencing row that must be explicitly deleted must be included in that batch.
Unmarked children, references outside the selected tree, and other database
constraints can still cause deletion to fail. `execute` rolls back the uncommitted
batch on such a failure. Database `ON DELETE CASCADE` behavior still applies.

This ordering follows the selected tree edges; it does not solve arbitrary foreign
key graphs, self references, or cycles. `REPLACE` retains its existing root-scoped
replacement behavior and is not a general descendant-deletion operation.
