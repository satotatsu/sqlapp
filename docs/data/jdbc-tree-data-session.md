# JDBC tree data sessions

[Documentation index](../README.md) · [Section index](README.md)

`JdbcTreeDataSession` processes a single rooted tree of Schema tables. It uses
the Schema foreign keys and dialect SQL factories; no database schema changes
are required.

Its central benefit is automatic optimization of database execution while keeping
business code simple: access the tree one record at a time, and the session groups
multiple records for execution using the batching and set-based strategies
supported by the database. Application code expresses what to process; the session
handles how those operations are grouped and executed efficiently.

## Intended uses and benefits

The API is designed for business processing where a parent record and its child
records form a natural unit: orders and order lines, invoices and details,
customers and their related records, or hierarchical staging data. It is also
useful when porting procedural COBOL or PL/1 programs to Java: the application can
keep familiar record-by-record control flow, validation, calculations and branching
while delegating multi-row execution and relationship handling to the session.

Application code accesses the tree directly with `next(table)` and `getRow(table)`
loops, creates records with `newRow(table)`, and marks changes with `row.update()`,
`row.insert()` or `row.delete()`. A child cursor exposes the children of the current
parent. The application does not need to construct a separate batch, manage
`PreparedStatement.addBatch()`, or issue a child query inside every parent loop.
The initial root SQL remains available for business-specific filtering.

When a business rule stops a child loop early, advancing to the next parent
discards that cursor's unread children. Its next iteration starts with children
of the new current parent, including when that parent has no children.

Custom child queries can be registered independently with
`select(childTable, sql, context)`. The supplied context is used for parameter
evaluation on each root batch, including when a prepared statement is reused.
The two-argument child overload retains the root-row list as its implicit context.
Return the relationship columns needed to associate results with their parents.
Rows unrelated to the current batch's loaded parents are excluded from processing.
Use `select(childTable)` for automatic queries restricted to the current root
batch; a custom query must express its own SQL restrictions.

The main benefits are:

- **Straightforward business logic.** Nested loops follow the data hierarchy, so
  business rules remain visible without being expressed as execution callbacks
  or JDBC plumbing.
- **Automatic optimization through multi-row execution.** Marked row operations
  are buffered and grouped by table and operation. The session uses JDBC batches
  or supported multi-row SQL internally, so ordinary record-by-record business
  code benefits from grouped execution without hand-written batching logic.
- **Grouped child reads.** Selected descendants are loaded for a batch of root
  rows and associated with their parents in memory. Traversing those children
  does not require a separate SELECT for each parent.
- **Schema-based relationship handling.** Foreign keys define the tree, generated
  parent keys propagate to children, inserts run parent first, and explicitly
  marked deletes run child first.
- **Explicit execution and observable results.** `execute` supplies the success
  and failure boundary, and its result distinguishes executed work from confirmed
  commits, including partial progress after a failure.

For example, the order-processing sample below expresses selection, nested
iteration, calculations and cancellation using ordinary control flow. The same
code benefits from batching without introducing a second implementation of its
business rules.

## Automatic optimization through multi-row execution

The session separates the application's record-at-a-time view from database
execution. Root records are buffered in batches (`rootBatchSize` defaults to 500).
Selected child data is loaded for those roots, and pending writes are executed
as the batch is processed. The final buffered batch is processed when `execute`
completes successfully.

The optimization is in grouping database work and selecting an appropriate
supported execution strategy. The business code benefits whether that strategy
uses `executeBatch()` or a SQL statement that processes multiple rows at once.

Ordinary INSERT, UPDATE and DELETE paths use JDBC `addBatch()` / `executeBatch()`.
Compatible prepared statements are reused. Depending on the dialect and generated
key requirements, INSERT may instead use a multi-row INSERT, INSERT RETURNING,
sequence preallocation, or smaller supported batches. Root-scoped replacement
deletes use a set-based DELETE. SELECTs use queries with fetch-size hints rather
than JDBC update batches. These execution choices are handled inside the session;
the business loop does not need to change for each strategy.

Grouping writes and child reads can reduce database round trips compared with
issuing a SQL statement for each individual record. Reusing prepared statements
also reduces repeated statement creation. This makes the API suitable for large
hierarchical batch jobs while keeping their application code simple. Actual
throughput depends on the JDBC driver, dialect, indexes, query plans, network,
record sizes and number of descendants; the API does not promise a fixed speedup.

For INSERTs with a sequence-backed column, dialects supporting sequence
preallocation (including HSQLDB 2.x) fetch the required sequence values together
for each table in the root batch. Associate the column with its `Sequence` in
the Schema model using `setSequenceName`. With five roots and `rootBatchSize = 3`,
the root sequence is queried for three values, then two; each child sequence is
queried for the actual number of child rows in that batch. Parent values propagate
to child foreign keys before insertion. The final batch does not reserve unused
values. Sequence allocation itself is subject to the database's transaction
semantics, so a rollback may still leave gaps.

Batch size and commit interval have different purposes. `setRootBatchSize` tunes
how many roots are processed together; `setFetchSize` supplies fetch-size hints
for other statement paths. `setCommitEveryRootBatches` controls transaction
boundaries and the amount of work that remains reversible. Increasing batch size
also increases the child data held in memory: a root with many descendants can
still require substantial memory. Dialect parameter and generated-key limits may
split a write into smaller groups.

The model represents one selected rooted tree, with one selected parent
relationship per child table. It is most useful when the business logic needs
to inspect or transform individual records. Arbitrary relationship graphs or
processing that can be expressed entirely as a single database-side operation
may call for a different execution model. SQL issued directly by application
callbacks is outside the session's automatic batching and result aggregation.

## Executable order-processing example

For a three-level procedural migration example (order, line, shipment allocation),
see the [COBOL migration walkthrough](jdbc-tree-data-cobol-migration-example.md).
It includes fictional COBOL-style pseudocode, a Java mapping, and executable
success and rollback tests.

See [JdbcTreeDataSessionOrderExampleTest](../../sqlapp-core-h2/src/test/java/com/sqlapp/data/db/dialect/h2/examples/JdbcTreeDataSessionOrderExampleTest.java)
for a complete, runnable example. The `processOrders` method contains the business
logic; database creation, input data, result printing and assertions are kept in
separate methods. No additional dependencies or external database are needed.

Run from the repository root with Java 21 and the Gradle Wrapper:

```powershell
.\gradlew.bat :sqlapp-core-h2:test --tests '*JdbcTreeDataSessionOrderExampleTest' --console=plain
```

The fixture has an order header and its lines, with an ordinary non-CASCADE
foreign key. A parameterized root SQL query selects only READY and CANCELLED
orders and a minimum order ID. Child rows are selected through the Schema
relationship. The application uses `next` / `getRow` loops, validates quantities,
computes decimal line amounts and header totals, and explicitly marks updates
or deletions. No additional callbacks are required.

| Order | Initial status | Successful processing |
| --- | --- | --- |
| 1 | READY | DONE; two lines total 250.00 |
| 2 | CANCELLED | Explicitly marked lines and header are deleted child first |
| 3 | READY | DONE; one line totals 30.00 |
| 4 | HOLD | Excluded by SQL; header and line remain unchanged |

The success test verifies three completed root batches, one final commit, two
header updates, three line updates, one header deletion and one line deletion.
All affected counts are committed. It checks the final database state as well
as the returned execution report.

The failure test changes order 3's quantity to zero before processing. Its
validation error occurs after the updates for order 1 and deletions for order 2
have already executed. The exception retains a report showing two completed
batches and zero commits. The test verifies that rollback restores order 2 and
its line and undoes order 1's updates. Order 4 is unchanged in both cases.

The example sets root batch size to one to expose these boundaries, and retains
the default commit interval so the entire execution commits only on success.
For larger jobs, changing the batch size changes SQL batching; explicitly enabling
periodic commits also changes what can be rolled back. The example has no automatic
restart: the initial SQL defines the work to process.

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
