# Resumable chunk migration

[Documentation index](../README.md) · [Migration workflows](README.md)

## Resumable chunk migration

`ChunkedBulkMigrationExecutor` splits a Schema `Table` row iterator into
bounded chunks and sends each chunk through the existing bulk INSERT or UPSERT
provider. `ChunkedBulkMigrationOption` identifies the migration, selects the
operation and chunk size, and can carry source and target fingerprints.

Duplicate-key selection for UPSERT applies to the complete migration, not
independently to each chunk. `ERROR` therefore detects the same non-null key in
different chunks, while `KEEP_FIRST` and `CUSTOM` retain their selected source
row across chunk boundaries. These strategies keep one entry per distinct
non-null key in memory. `KEEP_LAST` needs no global key set: later chunks
naturally overwrite earlier rows and is consequently the bounded-memory choice
for large inputs.

Progress is saved through `BulkMigrationCheckpointStore` only after a chunk
write succeeds. A subsequent execution with the same migration ID and matching
fingerprints continues with the next chunk. The `Table` overload skips the
recorded number of source rows for backward compatibility.

For mutable or very large sources, implement `BulkMigrationKeysetSource` and
use its executor overload. After every successful chunk, the executor stores
the opaque token returned for the last row. On retry it passes that token back
to `iterator`, which must query rows strictly after the complete key. The key
must be unique, immutable, non-null and ordered consistently; composite keys
should be encoded together in the token. Token encoding and database-specific
`WHERE`/`ORDER BY` SQL belong to the source implementation.

```java
BulkMigrationKeysetSource source = new JdbcBulkMigrationKeysetSource(
		sourceConnection, customerTable); // uses the complete primary key
var result = ChunkedBulkMigrationExecutor.execute(targetConnection, source,
		options);
```

`JdbcBulkMigrationKeysetSource` generates a portable lexicographic predicate
for single or composite ascending keys and streams the `ResultSet`. Pass key
column names explicitly when using a unique key other than the primary key.
The default JSON token codec supports standard Schema column converters;
provide a `BulkMigrationKeysetCodec` for vendor-specific key types or a custom
token format.

Count and keyset checkpoints cannot be interchanged after progress has been
recorded. Changing key columns, ordering, collation or token format requires a
new migration ID or source fingerprint.

Count-based resume rereads skipped source rows to reconstruct the duplicate-key
history required by `ERROR`, `KEEP_FIRST`, and `CUSTOM`. A keyset source starts
strictly after its stored token and cannot reconstruct that history. Resuming a
partially processed keyset UPSERT therefore requires `KEEP_LAST`; use a new
migration without the old checkpoint when another duplicate strategy is
required. A fresh keyset execution may use every strategy until it is paused or
fails and needs to resume.

While rereading a count-based source, the executor also hashes the rows at the
last completed chunk boundary and compares them with `lastChunkHash`. Missing
or changed boundary data and progress inconsistent with the configured chunk
size fail before another target row is written. The chunk size is stored in the
checkpoint and cannot be changed when resuming. This boundary check supplements
the caller-provided source fingerprint; it does not prove that rows in older
chunks are unchanged.

`DATABASE` is the default checkpoint mode. The overload without an explicit
store creates `SQLAPP_BULK_MIGRATION_CHECKPOINT` in the target database and
commits each data chunk and its checkpoint in the same JDBC transaction. The
control-table name can be changed with `checkpointTableName`. The executor
requires an initially auto-commit connection because it owns the per-chunk
transaction boundaries.

Set `checkpointMode` to `FILE` and pass a
`FileBulkMigrationCheckpointStore` from `sqlapp-command` to keep one atomically
replaced checkpoint file per migration. File mode cannot atomically commit the
target write and checkpoint, so it provides at-least-once replay semantics.
`CUSTOM` accepts caller-managed stores; `InMemoryBulkMigrationCheckpointStore`
is intended primarily for embedding and tests.

Database checkpointing needs no explicit store:

```java
var options = ChunkedBulkMigrationOption.builder()
        .migrationId("customer-v2")
        .chunkSize(10_000)
        .build(); // checkpointMode defaults to DATABASE
var result = ChunkedBulkMigrationExecutor.execute(connection, table, options);
```

File checkpointing is selected explicitly:

```java
var options = ChunkedBulkMigrationOption.builder()
        .migrationId("customer-v2")
        .chunkSize(10_000)
        .checkpointMode(BulkMigrationCheckpointMode.FILE)
        .build();
var store = new FileBulkMigrationCheckpointStore(checkpointDirectory);
var result = ChunkedBulkMigrationExecutor.execute(connection, table, options, store);
```

The mode and store must agree. `DATABASE` requires a transactional store using
the target connection, while `FILE` requires an external store. Invalid
combinations fail before the first source chunk is written.

The count-based source iterator must produce a deterministic order that remains
unchanged between attempts. For JDBC sources, prefer keyset resume using a
unique ordering such as the complete primary key. Changing the source, target
mapping, ordering, or transformation requires a new fingerprint or migration
ID.

There is an unavoidable interval between committing a data chunk and saving a
checkpoint in `FILE` mode. UPSERT safely replays that chunk and is therefore the
default migration operation. INSERT with `FILE` is suitable only when duplicate
replay is acceptable or recoverable.

Some UPSERT providers create staging objects with transaction-breaking DDL.
Such providers reject `DATABASE` checkpoint mode instead of claiming atomic
resume guarantees; use `FILE` mode until that provider can use externally
managed or transaction-safe staging. Oracle and SAP HANA currently have this
restriction for UPSERT. Vertica UPSERT has the same restriction because its
temporary-table/COPY lifecycle does not roll back atomically with the caller's
checkpoint transaction. Sybase ASE also rejects database-checkpoint UPSERT
because its staging-table cleanup DDL is not permitted inside the caller-owned
transaction. Vertica also rejects database-checkpoint INSERT because COPY does
not roll back with the checkpoint transaction. Oracle, SAP HANA, and Sybase
bulk INSERT remain eligible for database checkpoints.

| Database | INSERT with `DATABASE` | UPSERT with `DATABASE` |
| --- | --- | --- |
| PostgreSQL, SQL Server, DB2, MySQL, MariaDB, SQLite, Firebird, Informix | supported | supported |
| Oracle, SAP HANA, Sybase ASE | supported | use `FILE` |
| Vertica | use `FILE` | use `FILE` |

