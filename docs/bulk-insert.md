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
