# JDBC SQL templates and batch updates

[Data processing](README.md)

`JdbcBatchUpdateHandler` accepts a parsed SQL template and a collection of row
contexts. The default batch size is 50. Fixed templates are evaluated once;
subsequent rows refresh bind values through `reEval()` without rebuilding SQL.

```java
SqlNode node = SqlParser.getInstance().parse(dialect,
    "INSERT INTO events(id, name) VALUES (/*id*/0, /*name*/'sample')");
JdbcBatchUpdateHandler handler = new JdbcBatchUpdateHandler(node);
handler.execute(connection, contexts);
```

The default batch path checks the node structure once before preparing any
statement. It accepts ordinary scalar bind variables, SQL text and parameter
markers. It rejects conditionals, loops, variable-length bind lists, SQL
replacement/output, includes and custom node types with an actionable error.
`ParametersContext` operator overrides also require the dynamic path. A SQL
string constructed directly with `new SqlNode(sql)` is treated as fixed text;
use the parser for template directives.

For dynamic templates, opt into evaluation of each row:

```java
SqlNode node = SqlParser.getInstance().parse(dialect,
    "UPDATE events SET name=/*name*/'sample' WHERE id=/*id*/0 "
    + "/*if scoped*/ AND tenant_id=/*tenantId*/0 /*end*/");
JdbcBatchUpdateHandler handler = new JdbcBatchUpdateHandler(node);
handler.setEvaluateSqlPerRow(true);
handler.execute(connection, contexts);
```

This path executes the pending batch whenever the SQL text changes or the batch
size is reached, preserving input order. Consecutive rows with identical SQL
share a prepared statement. With batch size 1 or less, execution already
evaluates each row individually. Neither path commits or rolls back the caller's
transaction. An error can follow earlier successful batches; the caller must
choose the transaction boundary and rollback policy.

The fixed SQL path retains displaced stream values until the pending batch has
executed, then releases values no longer referenced by its parameter template.
The final template values are released on completion or failure. SQL is still
evaluated once; stream cleanup does not require rebuilding it for each row.

`SqlRegistry` expands `/*include 'sqlId'*/` using the registry that supplied the
template. For a directly parsed template, call `node.setSqlRegistry(registry)`.
Conditional templates use `/*if condition*/`, optional `-- else if condition`
lines, an optional `-- else` line, and one closing `/*end*/`. Each branch can
contain bind variables and nested conditionals. The legacy inline form
`-- else SQL text` remains supported.

Inline `else` SQL is parsed as part of that branch, so bind comments and nested
directives are evaluated once. Loop contexts may use immutable maps such as
`Map.of(...)`; the loop works on a separate mutable copy. Array binds inside
loops are created independently for each iteration, including when their sizes
differ.

For `JdbcQueryHandler`, an include may contain the reserved `_row` and `_offset`
binds for native paging. Explicit limit/offset values are supplied before
evaluation, and the expanded binds determine whether client-side paging is
needed. Native paging is not applied again on the client. Plain SQL nodes such
as `new SqlNode("SELECT 1")` have empty parameter metadata and can also be queried.
Statement creation and binding failures release partially created statements;
the original execution error is propagated.

Native paging is detected from evaluated parameter usage, including markers and
literal substitutions in nested includes. A paging clause in an unselected
branch does not disable client-side limit/offset. `SqlParameterCollection`
records this information through `registerEvaluatedParameter` and
`hasEvaluatedParameter`; custom nodes that emit paging literals can record the
reserved name in the same way. Clones have independent SQL-fragment and
evaluated-parameter metadata.

The context-based `createStatement` and scrollable-statement extension points
remain active. Default implementations reuse the evaluated collection instead
of evaluating SQL twice. Row callbacks can execute another query through the
same handler without replacing the outer query's creation scope. The handler's
fetch statistics accumulate successfully handled rows across executions,
including client-side limited queries. This does not make the handler's mutable
statistics or caller contexts safe for concurrent use.

Loop-local variables shadow exact names while other lookups delegate to the
input Map, retaining custom lookup such as `CaseInsensitiveGetMap` without
modifying that input.

`CallableJdbcHandler` binds INOUT parameters and registers them as outputs.
OUT and INOUT parameters require an explicit data type. A generated-key handler
passed to a `JdbcHandler` constructor remains active for ordinary `execute`
calls; an explicit handler argument replaces it.

`SqlParameterCollection.clone()` copies the SQL buffer, holder lists and bind
parameter objects. Schema objects, parameter values and stream resources remain
shared references; cloning does not duplicate streams or transfer their ownership.
