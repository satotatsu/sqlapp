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

## Current limitations

- Models run on DuckDB only. Building models inside another target database is
  planned.
- `incremental` materialization is not available yet; `table` rebuilds fully.
- Loading the source database into the engine and loading model tables into the
  target are separate steps, using the existing export, transfer and
  bulk-migration tasks.
- Result column types are the types DuckDB derives from the query. When a model
  table is loaded into another database, define the target table types in the
  Schema model rather than relying on the derived types.

See the [design notes](../development/migration-models-design.md) for the
planned follow-up work.
