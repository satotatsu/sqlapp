# Migration model Gradle task

[Documentation index](../README.md) · [Gradle plugin](README.md)

A migration model is one `SELECT` statement that defines a table or view.
Models reference each other by name, sqlapp works out the build order, and the
whole set is built inside one database engine. The engine is currently
**DuckDB**, so the same model SQL is used regardless of the source and target
database products, and models can read CSV and Parquet files in place.

This is the transformation step of a migration: load or expose the raw data,
build models from it, then load the resulting tables into the target with the
existing transfer or bulk-migration tasks.

## `executeMigrationModels`

```groovy
dependencies {
    runtimeOnly 'com.sqlapp:sqlapp-core-duckdb:<sqlapp-version>'
    runtimeOnly 'org.duckdb:duckdb_jdbc:<duckdb-version>'
}

executeMigrationModels {
    configurationFile = file('migration/models.yaml')
    dataSource { jdbcUrl = 'jdbc:duckdb:build/migration/work.duckdb' }
}
```

In a build without the Java plugin, supply these through an explicit
configuration as described in [Getting started](getting-started.md).

The task runs the unit tests first, builds the models only when every test
passes, and then runs the [data tests](#data-tests) of the built models.

| Property | Type | Required | Convention | Description |
|---|---|---:|---|---|
| `configurationFile` | `RegularFileProperty` | yes | — | Model YAML |
| `dataSource` | data source block | yes | — | Engine in which models are built; a DuckDB database |
| `stateFile` | `RegularFileProperty` | no | — | State artifact written after a complete build |
| `modifiedOnly` | `Property<Boolean>` | yes | `false` | Build only models changed since `stateFile` and their downstream models |
| `unitTestsEnabled` | `Property<Boolean>` | yes | `true` | Run unit tests before building |
| `testOnly` | `Property<Boolean>` | yes | `false` | Run unit tests and build nothing |
| `dataTestsEnabled` | `Property<Boolean>` | yes | `true` | Run data tests before committing the build |
| `fullRefresh` | `Property<Boolean>` | yes | `false` | Rebuild [incremental models](#incremental-models) from all rows |
| `targetMappingFile` | `RegularFileProperty` | no | — | Transfer name mapping with the [declared column types](#declared-columns), written after a complete build |

## Configuration

```yaml
sources:
  - name: raw_customer
    file: data/customer.csv        # relative to this YAML
  - name: raw_order
    table: main.raw_order          # table that already exists in the engine

models:
  - name: stg_customer             # view is the default
    query: |
      SELECT id, upper(name) AS name
      FROM /*$source('raw_customer')*/raw_customer
  - name: customer_sales
    sql: models/customer_sales.sql # relative to this YAML
    materialized: table
    parameters:
      minimumAmount: 100
```

| Key | Description |
|---|---|
| `models[].name` | Relation name and node ID. Letters, digits, `_` and `$`; unique ignoring case |
| `models[].sql` / `query` | SQL file or inline SQL; exactly one is required |
| `models[].materialized` | `view` (default) or `table` |
| `models[].schema` | Optional schema; created when missing |
| `models[].parameters` | Values for sqlapp comment-template parameters in the SQL |
| `sources[].table` | Existing table, `table` or `schema.table`; use `schema` separately when a name contains a dot |
| `sources[].file` | Data file read in place. A glob such as `data/*.parquet` is passed to the engine |
| `sources[].format` | `parquet`, `csv`, `tsv` or `json` when the file extension does not identify it |

Unknown keys are rejected.

## Model SQL and references

Model SQL is a normal sqlapp comment-template. A reference is a replacement
comment followed by a placeholder identifier, so the file can still be run as
ordinary SQL in an editor:

```sql
-- models/customer_sales.sql
WITH big AS (
  SELECT customer_id, amount
  FROM /*$ref('stg_order')*/stg_order
  WHERE amount >= /*minimumAmount*/0
)
SELECT c.id, c.name, sum(big.amount) AS total
FROM /*$ref('stg_customer')*/stg_customer c
JOIN big ON big.customer_id = c.id
GROUP BY c.id, c.name
```

- `/*$ref('model')*/placeholder` reads another model and makes it a
  dependency. Build order comes only from these references.
- `/*$source('source')*/placeholder` reads a declared source.
- The placeholder is required and is discarded. It may be qualified
  (`main.raw_order`) or double-quoted.
- Other comment-template features, such as the bind parameter
  `/*minimumAmount*/0`, work as usual and take their values from `parameters`.
- A trailing `;` is removed, because the query is embedded in a `CREATE`
  statement.

Unknown references, a reference to a source through `ref` (or the reverse),
self-references, duplicate names and circular dependencies are rejected before
a connection is opened.

## Materialization and transactions

| `materialized` | Statement | Behavior |
|---|---|---|
| `view` | `CREATE OR REPLACE VIEW` | Stores the query only |
| `table` | `CREATE OR REPLACE TABLE ... AS` | Replaces all rows on every run |
| `incremental` | `INSERT ... ON CONFLICT DO UPDATE` | Merges new rows by primary key; see [Incremental models](#incremental-models) |

A relation that changes between view and table is dropped and recreated.

A run is one transaction. The models are built in dependency order, then every
[data test](#data-tests) runs on the same connection, and the transaction is
committed only when every model was built and no `error` test failed.
Otherwise everything the run changed is rolled back, including rows merged into
incremental tables and their recorded fingerprints, so the engine keeps the
state of the last successful run. The first failed model stops the build: the
following models are reported as `SKIPPED`, models already built as
`ROLLED_BACK`. `stateFile` and `targetMappingFile` are written only after a
commit.

## Declared columns

A `table` model can declare its output columns and primary key. The
declaration is optional, but when present it is a contract for all columns:
the query must return exactly the declared columns. Leave internal models
undeclared, and declare every model that is loaded into another database:

```yaml
models:
  - name: CUSTOMER_SALES
    materialized: table
    sql: models/customer_sales.sql
    columns:
      - name: CUSTOMER_ID
        type: INT
      - name: NAME
        type: VARCHAR(50)
        notNull: true
      - name: TOTAL
        type: DECIMAL(12,2)
    primaryKey: [CUSTOMER_ID]
```

`type` is a sqlapp `DataType` with optional length and scale, the same notation
as the transfer mapping file. CHAR, VARCHAR, NCHAR and NVARCHAR need a length.
In YAML flow style, quote types that contain a comma:
`{name: TOTAL, type: "DECIMAL(12,2)"}`.

The model query runs once into a temporary table. Its rows are checked
there, and the same rows are inserted into a table created with the declared
columns, NOT NULL constraints, the primary key and a length check for
character columns. A row that does not fit fails the model before anything
reaches the target, and the previous table is kept:

- a value that the declared type would change or cannot hold: a DECIMAL with
  more fraction digits than the declared scale, a fractional or too large
  number for an integer type, a DOUBLE such as `0.1 + 0.2` whose exact value
  has more digits than the scale, a
  TIMESTAMP with a time for a DATE, text that is not in the canonical form of
  the declared type;
- a string longer than the declared length;
- NULL in a `notNull` or primary key column, or a duplicate key;
- a query that returns a column that is not declared, or misses one.

A value is accepted when casting it to the declared type and back gives the
same value, so widening an integer, dropping trailing zeros or parsing
`'2026-11-20'` as a DATE is fine. Conversions that change values belong in the
model SQL, where they are visible and reviewed:

```sql
SELECT customer_id, ROUND(total, 2) AS total, CAST(ordered_at AS DATE) AS order_date
FROM /*$ref('stg_order')*/stg_order
```

The error names the model, the columns, the number of rows and the types; it
contains no data values. Primary key columns are always NOT NULL.

DuckDB keeps the types, NOT NULL and the primary key, but not string lengths
(`VARCHAR(n)` has no effect in DuckDB). With `targetMappingFile`, the task
writes the declared types as a transfer name mapping, so that the transfer to
the target creates the columns with them; see the next section.

## Unit tests

A unit test runs one model query against inline rows instead of its real
inputs. Nothing is created in the database.

```yaml
unitTests:
  - name: sums-orders-above-minimum
    model: customer_sales
    given:
      stg_customer:
        columns: [id, name]
        rows:
          - [1, ALICE]
          - [2, BOB]
      stg_order:
        sql: SELECT * FROM (VALUES (1, 150), (1, 50), (2, 99)) AS v(customer_id, amount)
    expect:
      rows:
        - [1, ALICE, 150]
    # ordered: true
```

- `given` must contain one fixture for every model and source the tested model
  references, and no others.
- A fixture is either `columns` and `rows`, or `sql`. Literal rows keep only the
  types the engine infers from numbers, booleans and text; use `sql` with
  explicit casts when the exact type matters, for example for dates and
  decimals.
- `expect.rows` is compared as text in query column order. Quote values such as
  `'1.50'` whose YAML form would differ from the database text. Row order is
  ignored unless `ordered: true`.
- Fixtures are supplied as common table expressions named after the
  references, so table aliases and column qualifiers in the model keep working.

## Data tests

Unit tests check the SQL against fixtures. Data tests check the built model
against the real data: each rule is a query for the rows that break it, and
the test passes when there are none.

```yaml
models:
  - name: ORDER_FACT
    materialized: table
    query: ...
    dataTests:
      - notNull: [CUSTOMER_ID, AMOUNT]
      - unique: [ORDER_ID]
      - acceptedValues: {column: STATUS, values: [NEW, SHIPPED]}
      - relationships: {column: CUSTOMER_ID, to: customer, field: ID}
      - expression: AMOUNT >= 0
      - rowCount: {min: 1}
      - name: no-cancelled
        severity: warn
        sql: SELECT * FROM /*$ref('ORDER_FACT')*/ORDER_FACT WHERE STATUS = 'CANCELLED'
```

| Rule | Failing rows |
|---|---|
| `notNull: [columns]` | rows with NULL in any of the columns |
| `unique: [columns]` | keys that occur more than once; keys with a NULL are not compared |
| `acceptedValues: {column, values}` | non-NULL values outside the list; add `notNull` to reject NULL |
| `relationships: {column, to, field}` | non-NULL values without a matching row in the model or source `to`; `columns` and `fields` for a composite key |
| `expression: condition` | rows where the condition is not true, including NULL |
| `rowCount: {min, max}` | the number of rows is outside the range; either bound is optional |
| `sql: query` | the rows the query returns; `$ref` and `$source` work as in models |

Each test has exactly one rule. `name` defaults to the model, the rule and
its columns, such as `ORDER_FACT.unique(ORDER_ID)`, and must be unique.
`severity: error` (the default) fails the task; `severity: warn` only reports.
A test query that cannot run fails the task whatever its severity. Columns
named by a rule must be declared when the model [declares
columns](#declared-columns).

The tests run after every model was built, in the transaction of the build, so
a test can read any model of the run, also one built after the tested model. A
failed `error` test rolls back every model of the run and fails the task. A
test query that fails can abort the transaction; the tests after it are
reported as `NOT_RUN`. The log and the error report the number of failing rows for each
test, never the values. A test query that fails is reported with the error
category (exception type, SQLSTATE and error code) only, because the database
message can quote a value, such as a string that cannot be cast; run the
query in the engine to see the message. Every data test runs, also with
`modifiedOnly`: a test that changed runs even when its model was not
rebuilt.

## Incremental models

An incremental model keeps its table and adds to it. Like in dbt, the model SQL
selects the rows to add, and the materialization merges them:

```yaml
models:
  - name: ORDER_FACT
    materialized: incremental
    columns:
      - {name: ORDER_ID, type: INT}
      - {name: AMOUNT, type: "DECIMAL(12,2)"}
      - {name: UPDATED_AT, type: TIMESTAMP}
    primaryKey: [ORDER_ID]
    query: |
      SELECT ORDER_ID, AMOUNT, UPDATED_AT FROM /*$ref('stg_order')*/stg_order
      /*if isIncremental*/
      WHERE UPDATED_AT > (SELECT max(UPDATED_AT) FROM /*$ref('ORDER_FACT')*/ORDER_FACT)
      /*end*/
```

- An incremental model needs declared columns and a primary key.
- It reads its own existing table with a reference to itself, which only an
  incremental model may do.
- The template parameter `isIncremental` is `false` when the table is built
  from all rows and `true` when rows are added.
- The first run, or a run with `fullRefresh`, builds the table from all rows.
  Later runs stage the selected rows once, reject NULL and duplicated keys and
  values that the declared types would change (counts only, no values), and
  merge the rows by primary key: existing keys are updated, new keys inserted.
- Rows are never deleted. A row removed from a source stays in the table until
  `fullRefresh`.

**Definitions that changed.** The table comment records a lineage fingerprint
(`sqlapp-model:1:sha256:…`): the definition of the model and of every model and
source it reads, directly or through upstream models, but not their rows. A
change to any of them, such as an upstream calculation, a declared column or
the table of a source, would leave old rows computed by the old definitions.
The run then stops and asks for `fullRefresh`. It also stops for a table
without a readable fingerprint, such as a table created by hand. The SQL is not
normalized: a changed comment or whitespace also counts as a change. A change
to an unrelated model does not.

**Late data.** A filter on the latest timestamp skips rows that arrive late
with an older timestamp. Read an overlapping window again; the merge by key
makes this safe:

```sql
/*if isIncremental*/
WHERE UPDATED_AT > (SELECT max(UPDATED_AT) - INTERVAL 3 DAY FROM /*$ref('ORDER_FACT')*/ORDER_FACT)
/*end*/
```

**After a failure.** A failed model or `error` data test rolls back the merged
rows, so the latest timestamp in the table does not move and the next run
selects the same rows again.

A unit test runs an incremental model as a first build by default. With
`incremental: true` it runs as when rows are added, and the fixture named after
the model stands for the existing table:

```yaml
unitTests:
  - name: adds-newer-rows
    model: ORDER_FACT
    incremental: true
    given:
      stg_order: {sql: "SELECT 1 AS ORDER_ID, 10.00 AS AMOUNT, TIMESTAMP '2026-01-02' AS UPDATED_AT"}
      ORDER_FACT: {sql: "SELECT TIMESTAMP '2026-01-01' AS UPDATED_AT"}
    expect:
      rows:
        - [1, '10.00', '2026-01-02 00:00:00.0']
```

## Building only what changed

```groovy
executeMigrationModels {
    configurationFile = file('migration/models.yaml')
    stateFile = layout.buildDirectory.file('migration/models-state.json')
    modifiedOnly = true
    dataSource { jdbcUrl = 'jdbc:duckdb:build/migration/work.duckdb' }
}
```

`stateFile` records a fingerprint of every model: its SQL, materialization,
schema, parameters and the definitions of the sources it reads. With
`modifiedOnly`, only added or changed models and everything downstream of them
are built; unselected upstream relations must already exist in the engine.
`modifiedOnly` requires an existing `stateFile`; run once without it first.

A changed fingerprint means the *definition* changed. It does not detect new
source data, so keep `modifiedOnly` off for regular data refreshes.

## End-to-end with the transfer task

The model step sits between two `transferDatabase` jobs. DuckDB is the target
of the first job and the source of the second:

```groovy
tasks.register('loadWork', TransferDatabaseTask) {      // source -> DuckDB
    configurationFile = file('migration/load.yaml')
    sourceDataSource { jdbcUrl = 'jdbc:oracle:thin:@source/app' }
    dataSource { jdbcUrl = 'jdbc:duckdb:build/migration/work.duckdb' }
}
executeMigrationModels {                                  // models in DuckDB
    configurationFile = file('migration/models.yaml')
    dataSource { jdbcUrl = 'jdbc:duckdb:build/migration/work.duckdb' }
}
tasks.register('unloadWork', TransferDatabaseTask) {    // DuckDB -> target
    configurationFile = file('migration/unload.yaml')
    sourceDataSource { jdbcUrl = 'jdbc:duckdb:build/migration/work.duckdb' }
    dataSource { jdbcUrl = 'jdbc:postgresql://target/app' }
}
```

```yaml
# load.yaml
formatVersion: 1
source: src
target: work
tables: [CUSTOMER, ORDERS]
schemaMode: CREATE_MISSING
```

```groovy
executeMigrationModels {
    configurationFile = file('migration/models.yaml')
    targetMappingFile = layout.buildDirectory.file('migration/model-types.yaml')
    dataSource { jdbcUrl = 'jdbc:duckdb:build/migration/work.duckdb' }
}
```

```yaml
# unload.yaml
formatVersion: 1
source: work
target: dst
tables: [CUSTOMER_SALES]
schemaMode: CREATE_MISSING
mappingFile: ../build/migration/model-types.yaml   # relative to this file
```

Rows are written into DuckDB with the DuckDB Appender (see
[Bulk INSERT](../data/bulk-insert.md#duckdb)). Each transfer still requires its
reviewed `expectedConfigurationFingerprint` and `allowExecution`, as
described in [Direct transfer](direct-transfer.md).

Both transfers verify the copied rows. DuckDB offers no SERIALIZABLE isolation
through JDBC; its REPEATABLE READ is snapshot isolation, which the DuckDB dialect
declares and verification uses.

The target table gets the declared types, NOT NULL and the primary key. A model
table loaded without declared columns has no string lengths, and creating its
target table fails; declare the columns, or create the target table yourself
and use `schemaMode: REQUIRE_EXISTING`.

## Publishing into existing tables

The mapping written by `targetMappingFile` marks every table as a contract
(`contract: true`). When the target table already exists, the transfer checks
it against the contract during review, before anything is written, and stops
with every problem it finds:

| Contract | The existing target table must have |
|---|---|
| `VARCHAR(n)`, `CHAR(n)` | a character column of at least `n`, or an unbounded one |
| `DECIMAL(p,s)` | a scale of at least `s` and at least `p - s` integer digits; an integer column only for `s = 0` and a `p` it can always hold |
| `INT`, `BIGINT`, … | an integer column at least as wide, or a decimal with enough integer digits |
| `DOUBLE`, `REAL` | a floating-point column at least as wide |
| `DATE`, `TIMESTAMP`, `TIME` | `DATE`, `TIMESTAMP` or `DATETIME`; at least the declared fractional seconds |
| a nullable column | a nullable column |
| the primary key | no unique key that does not contain the primary key columns |
| — | no other NOT NULL column without a default |

A wider column is fine. A table that the transfer creates is created from the
contract and is not checked.

Lengths are compared as numbers in the target's own unit. A target that counts
bytes, such as Oracle with byte semantics, accepts the check for `VARCHAR2(20)`
and can still reject a row with twenty multi-byte characters. That row fails
the load. To publish into tables that already hold data, use
`targetPreparation: STAGED_REPLACE`:

```yaml
# unload.yaml
formatVersion: 1
source: work
target: dst
tables: [CUSTOMER_SALES, CUSTOMER_NAMES]
schemaMode: CREATE_MISSING
targetPreparation: STAGED_REPLACE
mappingFile: ../build/migration/model-types.yaml
```

The rows are copied into staging tables and verified, then replace the
existing rows in one transaction. When the target rejects a row, the
transaction rolls back and the published rows stay as they were. A table that
the job created for a new model remains, empty. Without `STAGED_REPLACE` the
target must be empty, and rows of chunks committed before a rejected row
remain. A value that a target shortens instead of rejecting it does not match
the source during verification (`verify: true`, the default), so the transfer
fails instead of reporting success.

Select every table of the mapping in the transfer job: a mapping entry outside
the selected tables is rejected.

## One job: `runMigrationPipeline`

`runMigrationPipeline` runs the three steps as one job: the load transfer,
the unit tests and model builds, and the publish transfer. A pipeline file
names the three definitions; paths are relative to it:

```yaml
# migration/pipeline.yaml
formatVersion: 1
load: load.yaml        # optional: source -> DuckDB
models: models.yaml
publish: publish.yaml  # DuckDB -> target, without mappingFile
```

```groovy
runMigrationPipeline {
    configurationFile = file('migration/pipeline.yaml')
    dataSource { jdbcUrl = 'jdbc:duckdb:build/migration/work.duckdb' }  // engine
    sourceDataSource { jdbcUrl = 'jdbc:oracle:thin:@source/app' }
    targetDataSource { jdbcUrl = 'jdbc:postgresql://target/app' }
    // expectedConfigurationFingerprint = 'sha256:...'
    // allowExecution = true
}
```

| Property | Type | Required | Convention | Description |
|---|---|---:|---|---|
| `configurationFile` | `RegularFileProperty` | yes | — | Pipeline file |
| `dataSource` | data source block | yes | — | Engine; a DuckDB database |
| `sourceDataSource` | data source block | with `load` | — | Database read by the load job |
| `targetDataSource` | data source block | to execute | — | Database written by the publish job |
| `expectedConfigurationFingerprint` | `Property<String>` | to execute | — | Approved fingerprint |
| `allowExecution` | `Property<Boolean>` | yes | `false` | Run the pipeline instead of reviewing it |
| `fullRefresh` | `Property<Boolean>` | yes | `false` | Rebuild incremental models from all rows |
| `workspaceDirectory` | `DirectoryProperty` | yes | `build/sqlapp-migration/pipeline` | One directory per run |

Without `allowExecution` the task reads the definitions without connecting and
logs the fingerprint to approve. The fingerprint covers the pipeline file, both
transfer jobs, the model YAML and every model definition, including the SQL
files. Any change needs a new approval, and each step checks again that its
definition is still the approved one.

The steps run in order and the first failure stops the run:

1. **LOAD** runs the load job with verification.
2. **MODELS** runs the unit tests, builds every model and runs the data tests
   in one transaction. It checks after the build that the model definitions did
   not change during it.
3. **DATA_TESTS** reports the [data tests](#data-tests) of that transaction; it
   is `SKIPPED` when the models have none. When a test fails, MODELS is
   `FAILED` with `Rolled back: data tests failed.` and the run stops here.
4. **PUBLISH** starts only after every model of this run was built and every
   `error` data test passed. The
   declared columns of this run are written into the run directory as the
   [contract mapping](#publishing-into-existing-tables) of the published
   tables, and the publish job is run with it and with verification.

The load and publish jobs must copy rows directly and must not turn off
verification; the publish job must not name a `mappingFile`, because the
pipeline supplies the one of the run. Use `targetPreparation: STAGED_REPLACE`
in the publish job to replace rows of existing tables. Set `fullRefresh = true`
on the task to rebuild incremental models from all rows.

`STAGED_REPLACE` publishes every row of the model tables, not only the rows of
the run. When publishing fails after the models were committed, the next run
publishes the rows merged by the failed run too, even when it merges nothing
new. A failed replacement retains its staging tables (`SQLAPP_STG_…`) for
inspection, and a new run stops while they exist; inspect them with the
transfer result of the failed run and drop them before running again.

Every run gets its own directory, named after its start time, with
`pipeline-report.json`. The report is rewritten after each step and records,
for each step, its status (`SUCCEEDED`, `FAILED`, `NOT_RUN`, `SKIPPED`), start
time, duration, the fingerprint of the definition it ran, the transfer review
and result reports, counts and the failure message. Steps after a failure stay
`NOT_RUN`. A failed run is not resumed: fix the cause and run the pipeline
again. Messages come from the failed step; a database error message can
contain values from the failing row.

## Current limitations

- Models run on DuckDB only. Building models inside another target database is
  planned.
- `incremental` materialization is not available yet; `table` rebuilds fully.
- A failed pipeline run starts again from the load step; no step is resumed.
- Only `table` models can declare columns. View models have the types DuckDB
  derives from the query.
- Declared lengths are checked as characters (`length()` in DuckDB). Targets
  that measure lengths in bytes can still reject multi-byte strings during the
  load; see [Publishing into existing tables](#publishing-into-existing-tables).

See the [design notes](../development/migration-models-design.md) for the
planned follow-up work.
