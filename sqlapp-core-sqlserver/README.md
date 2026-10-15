# SQL Server dialect

## System-versioned temporal tables (SQL Server 2016 and later)

Temporal metadata uses the existing shared Schema model; no additional core API
or XML format is required:

```java
Table table = new Table("Account").setSchemaName("dbo");
table.getColumns().add(new Column("id").setDataType(DataType.INT).setNotNull(true));
table.setPrimaryKey("PK_Account", table.getColumns().get("id"));
table.getColumns().add(new Column("ValidFrom").setDataType(DataType.TIMESTAMP)
        .setNotNull(true).setHidden(true));
table.getColumns().add(new Column("ValidTo").setDataType(DataType.TIMESTAMP)
        .setNotNull(true).setHidden(true));
table.getTemporalPeriods().add(new TemporalPeriod("SYSTEM_TIME")
        .setPeriodType(TemporalPeriodType.SYSTEM_TIME)
        .setStartColumnName("ValidFrom").setEndColumnName("ValidTo"));
table.toSystemVersioning();
```

Use the dialect SQL factory registry with `SqlType.CREATE`. The period columns
must be distinct, non-null `datetime2` columns; the current table requires a
primary key. `DataType.TIMESTAMP` maps to `datetime2` in this dialect.

By default SQL Server creates an anonymous history table. To preserve a specific
history identity, set both `historyTableSchemaName` and `historyTableName` on
`SystemVersioning`. The history schema must exist. An existing compatible history
table is reused with `DATA_CONSISTENCY_CHECK = ON`.

CREATE produces separate table creation and versioning enable operations. Execute
all returned operations, in order. Table collection, Schema and Catalog CREATE
plans defer versioning enable operations until their tables have been created,
including history tables modeled explicitly or in another schema. Individually
generated table plans should likewise create explicit history tables first.

The metadata readers obtain period column names and history identity with joins
in the existing table query, without per-table queries. Hidden flags are read
from the column query. A period retained with versioning disabled remains in the
model with `enable=false`; a history table is read as a regular table, with
`temporal_type=HISTORY_TABLE` retained as supplemental metadata.

Table DROP disables versioning before dropping the current table, and retains
history unless its deletion is explicitly requested. For a DROP table collection,
all versioning disable operations precede the table drops. Generation does not
execute SQL or start a transaction.

Temporal ALTER supports toggling `SystemVersioning.enable` on an existing
SYSTEM_TIME period and toggling `Column.hidden` for its start/end columns. Hidden
changes use `ALTER COLUMN ... ADD/DROP HIDDEN` without interrupting versioning.
Re-enabling requires an explicit history schema and table name. If the disabled
model was freshly read, supply the former history identity explicitly; SQL Server
no longer exposes that relationship after versioning is disabled. An already
recorded history identity cannot be changed by this automatic ALTER path.

ALTER also supports adding a SYSTEM_TIME period on existing, non-null datetime2
columns, optionally enabling versioning afterward. Removing the period disables
versioning first when needed and removes HIDDEN flags before DROP PERIOD. To
remove a period, clear `temporalPeriods`, set `systemVersioning` to null, and set
its former period columns to `hidden=false`. The columns, their values, and the
history table are retained. Existing row values are validated by SQL Server;
the generator does not backfill or rewrite them. Execute the plan in a transaction
if the application requires the multi-statement transition to be atomic.

While versioning remains enabled, ALTER can also append ordinary nullable columns
of these built-in model types: INT, BIGINT, VARCHAR, NVARCHAR, DECIMAL, DATE,
TIMESTAMP (datetime2), VARBINARY and BOOLEAN (bit). Length and scale settings are
supported. SQL explicitly includes NULL; SQL Server propagates the addition to the
history table. Existing current/history values are retained and the new values
are NULL. This path excludes memory-optimized tables, explicit vendor type names,
defaults, computed expressions, identity, constraints and other advanced column
properties. Column additions cannot be combined with other changes.

ALTER also permits length-only expansion of existing bounded VARCHAR, NVARCHAR
and VARBINARY columns while versioning stays enabled. The upper bound is 4000
for NVARCHAR and 8000 for VARCHAR/VARBINARY. Nullability and collation are restated
explicitly; existing defaults and checks are retained without recreation.
Shrinking, transitions to/from MAX, generated/masked/sparse columns,
memory-optimized tables, and simultaneous unrelated changes are rejected.

Replacing a period, other existing column structure changes, history identity or
unrelated table properties is rejected explicitly. Such migrations must disable
versioning, migrate current and history tables together, then re-enable
versioning explicitly. History retention settings, Ledger features and restoration
of historical row data are outside this support. SQL Server versions before 2016
reject temporal configuration; their metadata queries remain unchanged.

Unit tests cover DDL, validation, XML preservation and version boundaries.
Opt-in SQL Server Testcontainers metadata audits also exercise recreation against
an existing history table and verify that updates record historical rows.

Syntax reference: [Microsoft Learn — Create a system-versioned temporal table](https://learn.microsoft.com/en-us/sql/relational-databases/tables/temporal/create).
