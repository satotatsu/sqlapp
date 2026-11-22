# Migration models: design notes

[Development index](README.md) · [User guide](../gradle-plugin/migration-models.md)

Updated: 2026-11-22. This document separates what is implemented from what is
planned. Planned items are not commitments.

## Goal

Add dbt-style transformation to sqlapp migrations: SELECT-defined models with
explicit references, dependency-ordered builds, unit tests and selective
rebuilds. sqlapp already had the surrounding pieces (SCD2 snapshots, data
tests, freshness checks, transformation tests, node manifests); the missing
part was the model itself.

## Decisions

| Topic | Decision | Reason |
|---|---|---|
| Execution engine | DuckDB first; the target database later as a second engine | One SQL dialect for model SQL regardless of source and target products; reads CSV/Parquet in place; unit tests and previews need no server |
| Template language | The existing sqlapp comment-template, not Jinja | Reuses the parser, parameters and conventions users already know; model files stay executable SQL |
| Dependencies | Only from `/*$ref('name')*/placeholder` | sqlapp has no full SQL parser; explicit references are unambiguous across dialects |
| Reference resolution | A text pass before the template parser | No change to the shared, compatibility-sensitive template parser |
| Dialect SQL | `MigrationModelExecutor` SPI in `sqlapp-core`, implemented per dialect | CREATE/REPLACE semantics and file readers differ by product and belong to the dialect module |
| State selection | Reuses `MigrationNodeManifest` and `MigrationNodeStateSelector` | Same artifact and semantics as bulk migration tasks |
| Unit tests | Resolved to `MigrationTransformationTest` and run by the existing runner | One comparison rule for all transformation tests |
| Transactions | One commit per model; downstream of a failure is skipped | A full-refresh table is independent of the others; partial progress stays inspectable |
| dbt compatibility | None: no Jinja, no dbt project files, no packages | Cost is high and the Schema-model-centred design differs |

## Implemented

| Layer | Types |
|---|---|
| `sqlapp-core`, `com.sqlapp.data.schemas.migration` | `MigrationModel`, `MigrationModelSource`, `MigrationModelReferences`, `MigrationModelPlan`, `MigrationModelUnitTest` |
| `sqlapp-core`, `com.sqlapp.jdbc.bulk` | `MigrationModelExecutor`, `AbstractMigrationModelExecutor`, `MigrationModelExecutorProvider`, `MigrationModelExecutorResolver` |
| `sqlapp-core-duckdb` | `DuckDbMigrationModelExecutor` and its provider |
| `sqlapp-command`, `...command.migration.model` | `MigrationModelConfiguration`, `MigrationModelConfigurationResolver`, `MigrationModelRunner`, `ExecuteMigrationModelsCommand` |
| `sqlapp-gradle-plugin` | `ExecuteMigrationModelsTask`, registered as `executeMigrationModels` |

`sqlapp-command` gains `sqlapp-core-duckdb` and the DuckDB JDBC driver as
**test** dependencies only. At run time the executor is discovered through
`ServiceLoader`, so consumers add the DuckDB module and driver themselves.

Verification: unit tests in `sqlapp-core`, executor tests against embedded
DuckDB in `sqlapp-core-duckdb`, and command tests against an embedded DuckDB
file in `sqlapp-command`. No external database is involved.

## Planned

1. **Source ingestion and target load.** Verified 2026-11-16 with the existing
   `transferDatabase` command (HSQLDB to DuckDB, models, DuckDB to HSQLDB;
   `MigrationModelPipelineTest`). Two fixes were needed: transfer phases treat
   `setReadOnly(true)` as a JDBC hint because DuckDB rejects it per connection,
   and the DuckDB DDL no longer writes the `NONE` referential action. Bulk
   INSERT into DuckDB uses the Appender.
   - Verification into DuckDB (decided 2026-11-17): `Dialect` declares whether
     REPEATABLE READ is snapshot isolation; DuckDB does, and transfer
     verification accepts it where SERIALIZABLE is not offered.
   - Target types (decided 2026-11-17): table models declare output columns and
     a primary key. DuckDB creates the table with them and a length CHECK, and
     `targetMappingFile` hands the declared types to the transfer, because
     DuckDB keeps no string lengths. Converting unbounded strings to a
     per-target maximum was rejected: row-size limits (MySQL, Firebird) and LOB
     restrictions (Oracle CLOB, SQL Server MAX) make it unsafe.
   - Lossless declared types (decided 2026-11-20): `columns` is an optional
     contract for all columns. A declared type never changes a value: the
     query result is staged once in a temporary table, every value must
     survive a cast to the declared type and back, and the same staged rows
     are inserted. Rounding, truncation, overflow and failed conversions are
     errors that report counts but no values; conversions belong in the model
     SQL. There is no lenient mode, because the feature was not released with
     rounding.
   - Publishing conditions (decided 2026-11-20, implemented 2026-11-22): an existing
     target table must accept the declared data without changing it (type,
     length in the target's unit, precision and scale, NOT NULL, the whole
     primary key), otherwise the load stops before it starts; a new table is
     created as declared. Byte-length overflow is left to the load, which must
     fail and leave published data unchanged; truncated values are never
     published. Rollback of the data and whether a newly created table remains
     are verified separately.
     Implementation: the model mapping marks tables `contract: true`;
     `TransferTargetContract` compares the projected contract table with the
     target metadata during review. A unique key passes when it contains the
     declared key, because rows unique on the key are then unique on it too.
     `MigrationModelPublishTest` covers the check and a byte-length rejection
     under `STAGED_REPLACE` (published rows kept, the new table left empty);
     that path also needed the snapshot isolation of decision 1A for a DuckDB
     source during the final verification.
   - A single job that runs the three steps is not implemented. It will pass
     the declared columns of the successful model run in memory instead of
     reading a mapping file, start publishing only when that run succeeded,
     and record each step's outcome. A step is offered for restart only when
     its inputs and intermediate results can be shown to be unchanged.
2. **Types.** Declared columns cover the target types of table models. Still
   open: byte-length targets (lengths are checked in characters), CHAR padding,
   time zones, and declared columns for views.
3. **Tests in configuration.** Expose the existing `MigrationDataTest`
   (not-null, unique, relationships, accepted values, custom SQL) and
   `MigrationFreshnessCheck` in model YAML and run them after a build.
4. **Incremental materialization.** Produce the delta in DuckDB and apply it
   with the existing UPSERT path; then implement the remaining
   `BulkMigrationIncrementalStrategy` values, DELETE_INSERT and FULL_REPLACE
   first.
5. **Target engine.** A second `MigrationModelExecutor` for models that should
   run inside the target database, selected per model.
6. **Documentation and GUI.** Model lineage in the HTML documentation; model
   list, dependency view and preview execution in sqlapp-swing.

## Open points

- Working-file location, memory limit and temporary-directory settings for
  DuckDB are left to the JDBC URL and DuckDB settings for now.
- A run report artifact (per-model status, timings, fingerprints) is not written
  yet; results are available from the command object and the task log.
- Row counts are not collected, to avoid an extra scan per model.
