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
