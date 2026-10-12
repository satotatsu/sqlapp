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
