# Porting hierarchical COBOL processing to Java

[Documentation index](../README.md) · [Section index](README.md)

This fictional order-pricing batch illustrates a migration; it is not a port of
an existing production program. The Java implementation is in
[JdbcTreeDataSessionCobolMigrationExampleTest](../../sqlapp-core-h2/src/test/java/com/sqlapp/data/db/dialect/h2/examples/JdbcTreeDataSessionCobolMigrationExampleTest.java)
and runs against embedded H2. Business logic is in `priceOrders`, DDL and input
data are in `database`, and test methods verify the results.

## Business rules and hierarchy

```text
SALES_ORDER (order)
  └─ SALES_LINE (order line, linked to its parent by ORDER_ID)
       └─ SHIPMENT_ALLOCATION (shipment allocation, linked by LINE_ID)
```

SQL selects only READY orders. Each allocation amount is its quantity multiplied
by the line's unit price. Allocation amounts accumulate into line amounts, and
line amounts accumulate into the order total. Completed orders become PRICED.
Processing fails if allocation quantities do not sum to the ordered quantity,
or if quantities or prices are invalid. Currency has two decimal places and
quantities are integers; Java calculations use `BigDecimal`. Taxes, discounts
and rounding adjustments are outside the scope of this example.

| Order | Input | Result |
| --- | --- | --- |
| 1 | Line 11: price 100, allocations of 1+2 units. Line 12: price 25, allocations of 1+1 units | 300+50 = 350.00 |
| 2 | Line 21: price 10, allocations of 1+2 units | 30.00 |
| 3 | Line 31: price 15, allocation of 1 unit | 15.00 |
| 4 | HOLD, with a line and allocation | All levels remain unchanged |

## Assumed source program

The following is COBOL-style pseudocode, not compilable COBOL source. File
declarations, SQL host variables, and implementations of READ, UPDATE and error
handling are omitted. READ-NEXT-LINE and READ-NEXT-ALLOCATION are assumed to return
only records belonging to the current parent key, resetting their end flags and
read positions whenever the parent changes.

```cobol
PERFORM READ-NEXT-READY-ORDER
PERFORM UNTIL END-ORDER
    MOVE ZERO TO ORDER-AMOUNT
    PERFORM READ-NEXT-LINE
    PERFORM UNTIL END-LINE
        PERFORM VALIDATE-PRICE-AND-ORDERED-QTY
        MOVE ZERO TO LINE-AMOUNT ALLOCATED-TOTAL
        PERFORM READ-NEXT-ALLOCATION
        PERFORM UNTIL END-ALLOCATION
            PERFORM VALIDATE-ALLOCATION-QTY
            COMPUTE ALLOCATION-AMOUNT = ALLOCATED-QTY * UNIT-PRICE
            ADD ALLOCATED-QTY TO ALLOCATED-TOTAL
            ADD ALLOCATION-AMOUNT TO LINE-AMOUNT
            PERFORM UPDATE-ALLOCATION
            PERFORM READ-NEXT-ALLOCATION
        END-PERFORM
        IF ALLOCATED-TOTAL NOT = ORDERED-QTY
            PERFORM ABORT-BATCH
        END-IF
        PERFORM UPDATE-LINE
        ADD LINE-AMOUNT TO ORDER-AMOUNT
        PERFORM READ-NEXT-LINE
    END-PERFORM
    MOVE 'PRICED' TO ORDER-STATUS
    PERFORM UPDATE-ORDER
    PERFORM READ-NEXT-READY-ORDER
END-PERFORM
PERFORM COMMIT-BATCH
```

## Mapping to Java

| Source responsibility | Java implementation |
| --- | --- |
| Read READY orders | Parameterized `session.select(orders, sql, context)` |
| Read lines and allocations by parent key | Schema foreign keys and `select(lines)` / `select(allocations)` |
| Three nested PERFORMs and record areas | Three nested `while (session.next(table))` loops and `getRow(table)` |
| Accumulators in WORKING-STORAGE | Local variables scoped to each order and line |
| COMPUTE / ADD | `BigDecimal.multiply` / `add` |
| Update a record | `row.put(...)` and `row.update()` |
| Abort processing | Throw an exception identifying the order and line |
| COMMIT / ROLLBACK | The success and failure boundary of `execute` |

Child cursors expose only rows belonging to the current parent, so business code
does not compare parent keys or manage read positions. Line and allocation reads
and writes are grouped by root batch; individual SELECTs or UPDATEs inside each
record loop are unnecessary. `TableOperationMode.NONE` writes only rows explicitly
marked with `update()`.

With `rootBatchSize = 2`, the example processes three orders in two batches,
including the final single order. Periodic commits are disabled, so the whole
execution commits once on success. Use a dedicated connection with auto-commit
disabled. The failure test introduces an invalid allocation quantity in the last
order and verifies rollback at all three levels, including already executed
updates for orders 1 and 2. The failure report distinguishes executed counts from
committed counts.

```powershell
.\gradlew.bat :sqlapp-core-h2:test --tests '*JdbcTreeDataSessionCobolMigrationExampleTest' --console=plain
```

A real migration must also preserve the source program's rounding rules,
NULL and blank handling, record ordering, concurrency control and commit units.
The accumulations in this example do not depend on record order. An order with
no lines is priced with a total of zero.
