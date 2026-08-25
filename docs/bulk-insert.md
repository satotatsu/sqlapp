# Bulk insert

`BulkInsertResolver` selects a database-specific implementation using the JDBC
connection's resolved dialect and Java `ServiceLoader` providers.

```java
long inserted = BulkInsertResolver.execute(connection, table,
        BulkOption.builder()
                .batchSize(10_000)
                .bulkCopyTimeout(120)
                .keepNulls(true)
                .tableLock(true)
                .build());
```

The source is `table.getRows()`, including a lazy `RowIteratorHandler` when one
is configured. Iterators are closed after completion or failure.

## SQL Server

The SQL Server provider uses Microsoft JDBC `SQLServerBulkCopy` and
`ISQLServerBulkData`; it does not generate multi-row `INSERT` SQL. Identity
columns are omitted unless `keepIdentity` is enabled. Hidden and computed
columns are always omitted. Column mappings use destination column names, so
the Schema column order does not need to match the physical table order.

The common options map to `SQLServerBulkCopyOptions`: batch size, timeout,
constraint checking, trigger execution, identity preservation, null
preservation, table locking, internal transactions, and encrypted-value
modification.

## Adding another database

A dialect module such as PostgreSQL should implement `BulkInsertExecutor` and
`BulkInsertProvider`, then register the provider in
`META-INF/services/com.sqlapp.jdbc.bulk.BulkInsertProvider`. Vendor APIs and
options remain in the dialect module; the shared core has no vendor-specific
dependency.

## PostgreSQL

The PostgreSQL provider uses the JDBC driver's `CopyManager` with
`COPY ... FROM STDIN WITH (FORMAT csv)`. Rows are encoded incrementally by a
`Reader`, so the complete data set is not retained in memory. CSV quoting
distinguishes SQL `NULL` from an empty string and supports embedded commas,
quotes, and newlines. Binary values use PostgreSQL hexadecimal `bytea` input;
Java arrays are encoded as PostgreSQL array literals.

Identity columns are omitted unless `keepIdentity` is enabled. Hidden and
computed columns are omitted. Unsupported batch size, COPY timeout, internal
transaction, encrypted-value modification, forced trigger behavior, and table
locking options are rejected rather than silently ignored. PostgreSQL
constraints and triggers retain their normal `COPY` behavior, and transaction
boundaries remain controlled by the supplied JDBC connection.
