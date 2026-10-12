# Generator validation and reproducibility

The generator keeps all columns in a lookup group on the same selected source
row for each generated row. This applies to Query and File lookups, including
RANDOM and weighted RANDOM selection, so related values and composite keys
stay together.

## Repeatable lookup selection

```groovy
tasks.register('generateRepeatableData', com.sqlapp.gradle.plugins.GenerateDataTask) {
    dataSource {
        properties 'test-datasource.properties'
    }
    directory = file('src/main/generator')
    includes.add('**/*.xlsx')
    onlyCurrentSchema = true
    dmlBatchSize = 1000
    randomSeed.set(42L)
    baseTime.set('2026-01-02T03:04:00')
}
```

The input directory contains existing generator templates. Run this example
against a disposable development/test database whose properties and schema
match those templates.

For direct Java use, call `setRandomSeed(42L)` and
`setBaseTime(LocalDateTime.of(2026, 1, 2, 3, 4))` on
`GenerateDataInsertCommand` or `GenerateDataConfigAndInsertCommand`.
Individual `TableGeneratorConfig` objects also accept these setters; a non-null
command value overrides the corresponding table value. The fixed time is a
runtime setting; it is not added to the Excel workbook format.

For date/time rules, use `_baseTime` in `minValue`, `nextValue` or
`formatExpression`, for example `_baseTime.plusDays(_index)` as a next-value
expression. Supplying a base time does not rewrite existing date literals or
calls to `now()` in templates.

The seed derives independent random streams from schema name, table name,
lookup kind and group name. Reproduction requires the same identities,
configuration, source values and source order. Use an explicit `ORDER BY` in
lookup queries. Changing unrelated groups does not consume another group's
random stream. Without a seed, selection remains random.

This setting controls built-in lookup selection only. Custom MVEL expressions
using UUIDs, random helpers, current time, external state or side effects must
be made deterministic separately. It does not guarantee identical generated
identity values in databases with different starting states.

## Validation and compatibility

Before generation SQL runs, selected configurations are checked for unknown
columns, missing or ambiguous lookup groups, invalid selection strategies,
missing lookup expressions/SQL, non-positive limits and negative offsets.
For each table, loaded lookup data must be nonempty and initial minimum/maximum
bounds must be ordered before initialization SQL or INSERT is executed.
File lookup data-source expressions must return an Iterable.

Weighted selection accepts non-negative integral weights whose sum fits in a
long. At least one weight must be positive. Zero-weight entries are never
selected; missing/null weights retain the existing default weight of one.

Existing valid settings and constructor signatures remain supported.
Previously ignored invalid settings now fail explicitly; in particular an
unknown selection strategy no longer silently falls back to NEXT_VALUE.
Random output changes because related columns now share one selection.

Validation does not parse arbitrary SQL hooks for side effects, guarantee
CHECK/UNIQUE constraints for arbitrary expressions, or make multiple table
loads atomic. A failure in a later table can occur after earlier commits.
The generator still uses its configured JDBC batch and commit boundaries.


## Resolving tables across schemas

Each generator configuration is resolved to one selected Schema table before
any generation SQL executes. Use the existing `schemaName` and `name` fields:

```yaml
schemaName: SALES
name: ORDERS
# Existing generation rules follow.
```

In Excel these are the schema and table fields on the `Table` sheet. `name`
is a literal table identifier: dots are not split into schema qualifiers.
A schema can be omitted only when the table name resolves uniquely among the
selected tables. Exact identifier case is preferred; case-insensitive fallback
is accepted only when it identifies a single table.

When `useSchemaNameDirectory` is enabled, a missing `schemaName` is inferred
from the configuration file's parent directory, for example
`SALES/ORDERS.xlsx`. An explicit schema must agree with that directory.
Programmatic configurations can instead supply `schemaName` directly without
a directory or file. Missing targets and ambiguous names produce errors with
the configuration file and, for ambiguity, candidate identities. Configurations
outside explicit table/schema include/exclude filters remain excluded.

Two schemas can each have an ORDERS configuration; both are executed against
their resolved table. Multiple configuration files for the same table remain
supported. The command no longer removes all configurations by short table
name after processing the first schema, and it accepts immutable configuration
maps and lists. Catalog selection still uses the existing command settings
(`onlyCurrentCatalog` is true by default). If the same schema/table identity
occurs in several selected catalogs, restrict the catalog rather than relying
on metadata order; this change does not introduce cross-catalog discovery.

When `insertSql` is omitted, generated INSERT SQL includes the resolved schema.
Explicit INSERT SQL and SQL hooks are executed as authored: qualify their table
references when targeting a different schema from the connection's default.
For generated templates, set `generatorConfigFactory.withSchemaName = true`
so INSERT and other generated table statements include schema names. For
example, add this to the existing `GenerateDataConfigTask` registration:

```groovy
generatorConfigFactory.withSchemaName = true
```

Template output filenames still use the short table name. Generate templates
for each schema into separate directories to avoid overwriting files with the
same name; this change concerns loading and resolving those configurations.
Existing configuration fields and Gradle task properties are unchanged.
Ambiguous short names, nonexistent selected targets and conflicting schema
settings now fail instead of silently choosing or skipping a table.


## Exact progress or generation without a count pass

Exact progress remains the default (`countRowsBeforeInsert = true`). The
command determines the number of generated inputs per starting row, counts
the Start Value SQL result, then inserts data with a total and percentage.
The built-in `iterator(n)` and Collection sources can now provide their size
without a full traversal; the first row's mapping is still validated. Other
sources, such as file iterables, are traversed to obtain their exact count.
This requires repeatable sources and stable source contents during generation.

For large sources where starting promptly matters more than a percentage,
add this to the existing `GenerateDataTask` registration:

```groovy
countRowsBeforeInsert.set(false)
```

For example:

```groovy
tasks.register('generateLargeData', com.sqlapp.gradle.plugins.GenerateDataTask) {
    dataSource {
        properties 'test-datasource.properties'
    }
    directory = file('src/main/generator')
    includes.add('**/*.xlsx')
    onlyCurrentSchema = true
    dmlBatchSize = 1000
    countRowsBeforeInsert.set(false)
}
```

In Java, call `setCountRowsBeforeInsert(false)` on
`GenerateDataInsertCommand` or `GenerateDataConfigAndInsertCommand`.
No workbook or YAML format changes are required.

With this option disabled, the command does not count the generation source
and executes Start Value SQL once, reading its column metadata from that
execution. It displays progress such as:

```text
Rows processed=[50000]. total=[unknown]. elapsedMs=[10000]. rowsPerSecond=[5000.0]
```

Unknown-total messages are emitted for the first successful batch and at most
once per second thereafter. They contain no percentage or estimated remaining
time. Completion always reports the actual processed input count, including
zero. Exact mode retains total/percentage messages and also includes elapsed
time and throughput; small datasets no longer show 100% on the first partial
batch.

Processed rows count generated inputs submitted to successful JDBC executions,
not affected database rows or committed rows. For example, an INSERT that
skips an existing unique key can process an input without adding a row, and
one input may drive several SQL statements. The row monitor and configured
batch/commit sizes remain available; disabling the count pass uses the general
iterable insertion path, so batch grouping and commit timing can differ from
the single-input fast path. The option does not add transaction guarantees.

This change does not reduce the memory used to retain Query/File lookup
candidates, nor does it eliminate normal source reads needed to generate data.
Start/finish count SQL and other explicitly configured SQL hooks still run.


### Failure diagnostics

Failures during row generation, mapping or row monitoring include the target
schema/table, `startRowIndex` (position in the StartValueSQL result),
`iterateIndex` (position within that start row's Iterate), and
`convertedInputCount` (inputs converted to INSERT parameter contexts).
Indexes are zero-based. The count does not mean committed or database-affected
rows. Deferred iteration retains the diagnostic start position without changing
row-monitor arguments or commit boundaries.

Evaluation failures for `nextValue`, `formatExpression` and
`columnMappingExpression` include the expression; column expressions also
include the column name. `rowNumberArgument` and `generationIndex` describe the
existing arguments to `generateValue`; use the outer `startRowIndex` for the
actual start-row position. Diagnostics do not add row-data contents.

SQL failures include the target table and `convertedInputCount`, preserving
the original cause, SQLState, vendor code and batch update-count array.
The exact failing row within a JDBC batch is not asserted. JDBC exception
chains remain available through the original cause.

Public properties and configuration formats are unchanged. Row-processing
runtime failures are wrapped in `IllegalStateException` with position details;
the original exception remains available as the cause.


### Preview generated values

Use `preview=true` on `GenerateDataInsertCommand`,
`GenerateDataConfigAndInsertCommand`, or `GenerateDataTask` to inspect generated
values before loading data. The default `previewLimit=10` is a positive maximum
per generator configuration (not per StartValueSQL row). Ordinary insertion
remains the default. Gradle displays each sample's table, zero-based start/Iterate
positions, final table-column values and runtime Java types.

```groovy
tasks.register('previewData', com.sqlapp.gradle.plugins.GenerateDataTask) {
    // Configure dataSource and directory as for the existing generateData task.
    preview.set(true)
    previewLimit.set(10)
    randomSeed.set(42L)
    baseTime.set('2026-01-02T03:04:00')
}
```

Java callers can inspect `command.getPreviewRows()` after `run()`. Each
`GeneratorPreviewRow` contains catalog/schema/table identity, the configuration
file when available, zero-based positions, `values()` and `valueTypes()`.
The result list and maps are unmodifiable snapshots; individual mutable cell
objects are not deep-copied. Null values have a null runtime-type entry.
Results reset on each run; partial samples remain available if later evaluation
fails. The config-and-insert facade still generates configuration files.

Preview shares lookup loading, validation, column generation, formatting and
mapping with insertion. It stops generating at the sample limit and closes
source iterators and JDBC resources even on early exit or failure. It never
performs count-only source scans, INSERT, InitializeSQL, FinalizeSQL,
StartCountSQL or FinishCountSQL, and does not call insertion row monitors or
commit callbacks. It still evaluates StartValueSQL and required Query/File
lookups. Lookup `limit`/`offset` retain their existing meaning; `previewLimit`
does not limit lookup loading. User-authored SQL/functions and expressions are
executed as authored, rather than sandboxed.

Samples describe generated table-column values before JDBC conversion, custom
InsertSQL transformations, row-monitor modifications, constraints, triggers or
database-generated values. Preview reads each start row and its Iterate
sequentially; batching-related expression context in insertion (notably the
existing `generateValue` row-number argument) can differ. It is a value preview,
not a guarantee of successful INSERT or an exact simulation of batched execution.
Use a fresh generation configuration when comparing seeded preview and insertion,
since generation advances random/previous-value state.
