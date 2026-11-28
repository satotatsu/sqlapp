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
properties.

ALTER also permits expansion of existing bounded VARCHAR, NVARCHAR
and VARBINARY columns while versioning stays enabled. The upper bound is 4000
for NVARCHAR and 8000 for VARCHAR/VARBINARY. Nullability and collation are restated
explicitly; existing defaults and checks are retained without recreation.
Shrinking, transitions to/from MAX, generated/masked/sparse columns,
memory-optimized tables, and unrelated changes are rejected.

NOT NULL may be relaxed to NULL for ordinary columns of the same supported
built-in model types. Type, length, scale and collation are preserved. Primary-key
and period columns cannot be made nullable, and NULL-to-NOT-NULL changes require
explicit verification of current and historical data and are rejected here.

While versioning stays enabled, one table diff may combine ordinary nullable
column additions, bounded length expansions, nullability relaxation and period
Hidden changes. The complete diff is validated before any SQL is returned. The
plan emits existing-column alterations, appended columns, then Hidden changes;
versioning is never disabled. Generation leaves both input Schema models intact.
Execute all returned operations in order. The caller manages transactions and
SQL execution errors; generation neither guarantees atomicity nor hides failures.

For example, with a metadata-read `original` table and its clone `target`:

```java
target.getColumns().get("Description").setLength(200).setNotNull(false);
target.getColumns().get("ValidFrom").setHidden(true);
target.getColumns().add(new Column("ExtraNote").setDataType(DataType.NVARCHAR).setLength(100));
SqlFactory<Table> alter = registry.getSqlFactory(target, SqlType.ALTER);
List<SqlOperation> plan = alter.createDiffSql(original.diff(target));
```

Replacing a period, deleting/renaming columns, changing data types, changing
history identity, or modifying unrelated table properties is rejected explicitly.
Structural changes cannot be combined with a versioning enable/disable transition
or period addition/removal. Such migrations must disable versioning and migrate
current and history tables explicitly. Ledger
features and restoration of historical row data are outside this support.
SQL Server versions before 2016 reject temporal configuration; their metadata
queries remain unchanged. Period columns require matching datetime2 precision
from 0 to 7; blank history identifiers and self-referencing history are rejected.

## History retention (SQL Server 2017 and later)

Use the optional dialect helper; the setting is stored in the existing
`SystemVersioning.specifics` and survives Schema XML serialization:

```java
SqlServerTemporalOptions.historyRetentionPeriod(table.toSystemVersioning(), "6 MONTHS");
```

Accepted values are `INFINITE` or a positive integer followed by DAYS, WEEKS,
MONTHS or YEARS (singular forms and case/whitespace variations are normalized).
An omitted setting on initial creation leaves SQL Server's INFINITE default.
SQL Server 2016 rejects this option; its metadata SQL does not reference the
new catalog columns. SQL Server 2017, 2019 and 2022 readers fetch the setting
in the existing table query without additional per-table queries.

An active temporal table's retention can be changed with the same table ALTER
entry point, including in a combined column/Hidden change plan. This emits
`SYSTEM_VERSIONING = ON (HISTORY_RETENTION_PERIOD = ...)` without disabling
versioning or reassigning the history table. To remove a finite limit, explicitly
set INFINITE; silently deleting the setting from a changed model is rejected.

SQL Server resets retention when versioning is disabled. If the disabled model
is read again, supply the desired history identity and retention explicitly
before re-enabling; the former retention cannot be recovered from that metadata.
Re-enabling may explicitly restore or choose the desired retention together with
the history identity. Disabling may clear the retention setting, reflecting the
engine reset, but cannot apply a new policy while versioning is off.
A table with a SYSTEM_TIME period and no systemVersioning object is treated as
versioning disabled; it may be enabled through the same ALTER entry point.
Unknown SystemVersioning specifics and configuration without a period are rejected
rather than silently ignored.

A finite policy makes aged history eligible for SQL Server's background cleanup.
Cleanup also depends on the database-level TEMPORAL_HISTORY_RETENTION flag and
an appropriate history-table clustered index. The dialect does not change the
flag, invent a finite policy, run SQL or delete data. SQL Server validates the
existing history index when the generated SQL is executed.

Reference: [Microsoft Learn — Temporal history retention](https://learn.microsoft.com/en-us/sql/relational-databases/tables/temporal/manage-retention).

Unit tests cover DDL, validation, XML preservation and version boundaries.
Opt-in SQL Server Testcontainers metadata audits also exercise recreation against
an existing history table, combined ALTER propagation, history-column length and
nullability, and retention of current/historical row data. They are opt-in and
must be run against an explicitly authorized test database/container.

Syntax reference: [Microsoft Learn — Create a system-versioned temporal table](https://learn.microsoft.com/en-us/sql/relational-databases/tables/temporal/create).

## Partition Function and Scheme changes (SQL Server 2005 and later)

The SQL factory registry supports DROP PARTITION FUNCTION and DROP PARTITION
SCHEME. A deleted PartitionFunction diff produces the same DROP operation as the
explicit DROP entry point. `dropIfExists` uses the matching sys catalog guard,
including on older versions without newer DROP syntax.

Modified PartitionFunction diffs support boundary value changes: removed values
produce MERGE RANGE and added values produce SPLIT RANGE. Name, input type,
length/precision, RANGE LEFT/RIGHT and other non-boundary changes are rejected
explicitly instead of silently yielding incomplete SQL. Input models are unchanged.

DROP never cascades to dependent schemes, tables or indexes. Remove dependencies
explicitly before executing it. SQL Server enforces those dependencies at execution.
A SPLIT on a function used by schemes also requires the appropriate NEXT USED
filegroup configuration; the generator does not guess filegroups or move data.
No SQL is executed by generation. Callers manage transactions and propagate any
execution errors. Opt-in database tests cover boundary changes, scheme dependency
failure, scheme/function deletion and repeated guarded DROP.

### NEXT USED for Partition Schemes

Use explicit settings in the existing PartitionScheme model:

```java
SqlServerPartitionOptions.nextUsed(targetScheme, "FilegroupName");
SqlFactory<PartitionScheme> alter = registry.getSqlFactory(targetScheme, SqlType.ALTER);
List<SqlOperation> plan = alter.createDiffSql(originalScheme.diff(targetScheme));
```

`clearNextUsed(scheme)` explicitly removes the assignment; simply deleting the
Specifics key from a changed model is rejected. Missing configuration means no
explicit override, while an empty NEXT_USED value is an explicit clear.
CREATE restores an explicit setting through a following ALTER operation.
Filegroup identifiers are quoted, including PRIMARY and names with spaces.

The metadata reader preserves the existing destination mapping list and obtains
NEXT USED from the first destination after the function's current partition
count. An absent NEXT USED is retained as an explicit clear so recreating an
ALL TO scheme does not accidentally restore a previously cleared assignment.
This uses the existing set-based query with no additional SELECT per scheme.

ALTER diffs support NEXT USED only; changing the function or existing placement
requires an explicit migration. Execute NEXT USED on every associated scheme
before each SPLIT. A SPLIT can consume the assignment, so multiple SPLITs may need
repeated assignments. Generic Catalog diffs do not plan this sequence or infer
filegroups. The opt-in integration audit covers clear/set, SPLIT with a scheme,
metadata retrieval, and constant query counts for one or many schemes.

### Boundary metadata round trips

SQL Server 2005+ readers retain functions with zero boundaries and distinguish
an absent joined boundary row from a genuine SQL NULL boundary. Values use the
shared dialect literal formatter, preserving Unicode, embedded quotes, trailing
spaces and binary literals. Decimal precision is read from declared precision,
not the storage byte length. The type join selects the built-in type so unrelated
alias types cannot duplicate function rows. Retrieval remains set-based.

The shared PartitionFunction validator recognizes unquoted SQL NULL separately
from quoted text, allowing numeric NULL boundaries to survive Schema XML loading.
No public API or XML structure changes are required. Opt-in database tests exercise
read/XML/recreate/read for empty, NULL, Unicode, binary and decimal boundaries,
plus constant query counts for one or many functions.

### Foreign key metadata (SQL Server 2005+)

Composite foreign key columns retain their declared constraint order, even when
it differs from the table column order. Readers preserve `is_not_trusted` and
`is_not_for_replication` in the existing constraint `specifics`, alongside the
enabled state. These attributes survive Schema XML serialization and DDL
recreation. Metadata retrieval uses the existing set-based query without
additional per-constraint queries.

### CHECK and foreign key DDL (SQL Server 2005+)

Use the existing Schema model and CREATE/ALTER table factories. No additional
configuration wrapper is required. Both CHECK and foreign key constraints use:

```java
constraint.setEnable(false); // omit for an enabled constraint
constraint.getSpecifics().put("is_not_trusted", "true");
constraint.getSpecifics().put("is_not_for_replication", "true");
```

The two specifics accept `true`, `false`, `1`, and `0` (boolean words are case
insensitive). Missing values mean trusted and ordinary replication enforcement;
a disabled constraint with no explicit trust setting is created untrusted.
An explicitly disabled but trusted constraint, invalid boolean text, or an
unnamed constraint requiring a subsequent state change fails clearly.

Standalone constraint CREATE and table ALTER add trusted constraints with
`WITH CHECK`, and untrusted/disabled constraints with `WITH NOCHECK`. Disabled
constraints are then disabled by name. Table CREATE emits inline constraints
and restores their states afterwards; an enabled untrusted constraint is disabled
and re-enabled without validating existing data. NOT FOR REPLICATION is emitted
at the proper position for each constraint kind. Trusted recreation on an
existing table validates data and may fail if the rows violate the constraint.

Column CHECK names and attributes are retained too. Existing column CHECK changes
use named constraint operations, not ALTER COLUMN. State-only changes use
CHECK/NOCHECK in place, and restoring trust uses `WITH CHECK CHECK CONSTRAINT`
so validation failure does not first drop the constraint. Definition/name/replication
changes recreate the constraint; execute that operation list in a transaction
when atomic restoration is required. Their state changes are recognized by
shared Schema differences, including schema-level ALTER. Dropping a column drops
its named column CHECK first. Changing a checked column's stored definition and
its CHECK together is rejected; remove the CHECK, alter the column, then restore
the CHECK in separate migrations. An unnamed existing CHECK must first be read
from the database to resolve its assigned name. Temporal column CHECK alterations
remain unsupported and fail explicitly.

The common Column clone now copies CHECK constraints independently and retains
ownership. The common difference model additionally compares the complete column
CHECK, while preserving the existing expression difference. Public method
signatures are unchanged. Simple column CHECKs retain the existing `check` XML
attribute; named or stateful column CHECKs use an optional `checkConstraint` child
to preserve the full model. Existing XML remains readable. Differences can now report
previously missed column CHECK state/name changes. Unit tests cover resolver
versions 2005 through 2022. Opt-in database tests check metadata/XML/recreation,
constraint enforcement, composite key order, and constant metadata query counts.

### Metadata recreation safeguards

Temporal catalog self-joins qualify current-table storage columns, avoiding
ambiguous-column errors on SQL Server 2016 and later. SQL Server 2008 and later
readers use catalog `scale` for the fractional-second precision of `time`,
`datetime2`, and `datetimeoffset`; storage byte length is not SQL type precision.
Partition scheme reference collections preserve repeated filegroup destinations
in order during bulk addition, cloning, and Schema XML round trips.
Full table metadata reads retain CHECK constraints in the table constraint
collection, using its complete enabled/trust/replication state; duplicate column
projections are removed. Foreign key `None` and `Restrict` actions generate SQL
Server's `NO ACTION` syntax.

### Native type and DDL round-trip corrections

SQL Server 2000 and later resolve both `timestamp` and `rowversion` as binary
row-version columns. SQL Server 2008 and later keep these names distinct from
`datetime2` and `datetimeoffset`. Re-extract metadata previously read as a date/time
type before generating replacement DDL; existing Schema XML is not automatically
reinterpreted.

CREATE preserves explicit IDENTITY seed/increment and, from SQL Server 2008,
nullable SPARSE columns. Unsupported SPARSE combinations fail explicitly.
SQL Server 2012 and later emit FileTable CREATE operations with optional directory
and filename collation settings. These still require the server/database FILESTREAM
configuration. Change-tracking ALTER operations support enable, disable and
`TRACK_COLUMNS_UPDATED` changes from SQL Server 2008.

From SQL Server 2005, column renames use `sys.sp_rename`. DEFAULT changes drop and
add constraints separately from ALTER COLUMN. Readers retain the assigned DEFAULT
name in column `specifics.default_name`; older Schema models can omit it, in which
case the generated SQL resolves it from the current database catalog and reports
an error when the expected constraint is missing. Execute multi-operation changes
in a transaction when atomicity is required.

SQL Server MERGE generation separates composite OUTPUT keys and omits UPDATE when
no mutable columns remain. Identity-only inserts use DEFAULT VALUES. Routine
argument metadata uses character lengths for Unicode types and fractional-second
precision for time types. Public Java signatures and XML configuration formats
are unchanged.

### Metadata comments, table options and MERGE locking

SQL Server 2005 and later read comments from MS_Description only. Table type
columns use the table type's extended property identity. SQL Server 2017 and
later retain graph table flags for validation.

CREATE TABLE does not copy arbitrary specifics into WITH. Configure index
options on indexes. Memory-optimized, graph and ledger tables require dedicated
recreation support and are rejected instead of being emitted as ordinary tables.

SQL Server 2008 and later add HOLDLOCK to the target of MERGE, MERGE_ROWS and
MERGE_TABLE, consistent with native bulk upsert. This can increase blocking
when concurrent transactions contend for the same target key ranges.

Native IMAGE and SQL_VARIANT keep their SQL Server type names. DATE expressions
return DATE from SQL Server 2008. Column masking requires SQL Server 2016 or
later. SQL scripts accept a line comment following GO or its repeat count.
Explicit table locks return a single count, and exclusive locks use TABLOCKX.
The shared MERGE update increments numeric optimistic lock columns using the
target value and honors the configured update coalescing option.

SQL Server 2000 uses the legacy unparenthesized CREATE INDEX options for
PAD_INDEX and FILLFACTOR. Row and page lock options require SQL Server 2005 or
later. CREATE TABLE places temporal period defaults after the generated period marker
and before nullability, as verified on SQL Server 2025.

### Native JSON and VECTOR in SQL Server 2025

The SQL Server 2025 dialect is selected for engine major version 17 and later.
It recognizes native JSON and VECTOR table columns. VECTOR dimensions come
from sys.columns.vector_dimensions and are stored as the Schema model's vector
dimension. Set that field when defining a VECTOR column by hand. FLOAT32 is
the default. FLOAT16 uses the vector_base_type specific and requires the
server's preview feature setting where applicable.

JDBC reads represent JSON and vectors as text in the row model. Prepared
statements accept JSON text and JSON-array vector text, including SQL NULL.
The native vector binding also accepts microsoft.sql.Vector. Native Bulk Copy
supports JSON text and FLOAT32 vectors represented as JSON arrays. It checks
the declared dimensions and rejects malformed or non-finite vector input.
Use JDBC inserts for FLOAT16. The server and driver used for verification
reject the native FLOAT16 bulk protocol.

The SQL Server 2025 dialect also reads and recreates JSON and DiskANN VECTOR
indexes. JSON index paths are stored as a JSON array in the json_index_paths
specific, and optimize_for_array_search preserves the array-search setting.
VECTOR indexes use the Schema model vectorDistanceType property. SQL Server
requires QUOTED_IDENTIFIER ON for JSON index DDL. Enable PREVIEW_FEATURES
where the server requires it for VECTOR index creation. SQL Server build
17.0.4065.4 accepts an explicit filegroup for VECTOR indexes but rejects
the JSON index ON filegroup clause. On that build, let JSON indexes inherit
the table's filegroup.

The optional SqlServer2025NativeTypesTest round-trip requires a disposable local
SQL Server 2025 instance. Set SQLAPP_TEST_SQLSERVER_2025_URL and
SQLAPP_TEST_SQLSERVER_2025_PASSWORD to run it.

To include FLOAT16, enable PREVIEW_FEATURES in the disposable database and
set SQLAPP_TEST_SQLSERVER_2025_FLOAT16=true. The test checks both JDBC
vectorTypeSupport=v1, which returns FLOAT16 as text, and v2, which enables
native FLOAT16 transfer.

Set SQLAPP_TEST_SQLSERVER_2025_INDEX_FILEGROUP to an existing non-default
filegroup in the disposable database to verify that a VECTOR index stays
on that filegroup after recreation.
