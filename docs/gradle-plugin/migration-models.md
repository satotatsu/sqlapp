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

The task runs the unit tests first and builds the models only when every test
passes.

| Property | Type | Required | Convention | Description |
|---|---|---:|---|---|
| `configurationFile` | `RegularFileProperty` | yes | — | Model YAML |
| `dataSource` | data source block | yes | — | Engine in which models are built; a DuckDB database |
| `stateFile` | `RegularFileProperty` | no | — | State artifact written after a complete build |
| `modifiedOnly` | `Property<Boolean>` | yes | `false` | Build only models changed since `stateFile` and their downstream models |
| `unitTestsEnabled` | `Property<Boolean>` | yes | `true` | Run unit tests before building |
| `testOnly` | `Property<Boolean>` | yes | `false` | Run unit tests and build nothing |
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

A relation that changes between view and table is dropped and recreated.

Each model is committed separately. When a model fails, its statement is rolled
back, every downstream model is skipped, unrelated models are still built, and
the task fails after reporting each model's status. Relations of models that
succeeded remain. `stateFile` is not written after a failed run.

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

## Current limitations

- Models run on DuckDB only. Building models inside another target database is
  planned.
- `incremental` materialization is not available yet; `table` rebuilds fully.
- Loading the source database into the engine and loading model tables into the
  target are separate `transferDatabase` jobs; see
  [End-to-end with the transfer task](#end-to-end-with-the-transfer-task).
- Only `table` models can declare columns. View models have the types DuckDB
  derives from the query.
- Declared lengths are checked as characters (`length()` in DuckDB). Targets
  that measure lengths in bytes can still reject multi-byte strings.

See the [design notes](../development/migration-models-design.md) for the
planned follow-up work.
