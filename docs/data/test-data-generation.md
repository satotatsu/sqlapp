# Generate relational test data from an Excel template

[Data processing](README.md)

Use sqlapp to prepare repeatable development, integration-test, and load-test
datasets. Generate an Excel configuration workbook from database metadata,
edit the generation rules, and load rows through JDBC batches. The workbook
contains rules and lookup definitions, so you can describe a million-row dataset
without entering a million rows in Excel.

The two steps are separate: `GenerateDataConfigTask` reads database metadata and
writes templates; `GenerateDataTask` reads those templates and inserts data.
They use the shared Schema model for columns, keys, and SQL generation.

## Generate a template, edit it, then load

After applying the plugin and declaring the JDBC driver and matching dialect
as described in the [Gradle quick start](../gradle-plugin/getting-started.md),
register these tasks in `build.gradle`. The properties file must describe your
disposable development/test database using the documented DataSource format.

```groovy
tasks.register('generateDataConfig', com.sqlapp.gradle.plugins.GenerateDataConfigTask) {
    dataSource {
        properties 'test-datasource.properties'
    }
    outputDirectory = file('src/main/generator')
    fileType = 'EXCEL'
    sqlType = 'INSERT'
    onlyCurrentSchema = true
}

tasks.register('generateData', com.sqlapp.gradle.plugins.GenerateDataTask) {
    dataSource {
        properties 'test-datasource.properties'
    }
    directory = file('src/main/generator')
    includes.add('**/*.xlsx')
    onlyCurrentSchema = true
    dmlBatchSize = 1000
}
```

Run template generation first:

```shell
./gradlew generateDataConfig
```

Edit the generated workbooks, review the selected tables and rules, then run:

```shell
./gradlew generateData
```

On Windows, use `.\gradlew.bat`. These task names are registered by this example;
they are not fixed tasks automatically supplied by the plugin. Keep the steps
separate so a load does not regenerate and overwrite your edited templates.
The example assumes the target tables already exist. Template generation reads
the configured database; loading inserts into it and runs configured SQL hooks.

## What the workbook can express

Each generated Excel workbook has four sheets. Labels and comments can be
localized; the property names below identify the configuration fields.

| Sheet | Common edits | When useful |
|---|---|---|
| `Table` | `dataSourceExpression`, `startValueSql`, `columnMappingExpression` | Control how many values to generate for each starting row and map source values |
| `Column` | `minValue`, `maxValue`, `nextValue`, `formatExpression`, candidate `values` | Set numeric/date bounds, sequences, categories, and formatted identifiers |
| `Query` | `selectSql`, column mapping, selection strategy, offset and limit | Pick valid reference values or related column combinations from database rows |
| `File` | Data-source expression, mapping, and selection strategy | Reuse external lookup data in generation |

The template also exposes initial/final count SQL, initialization SQL, INSERT
SQL, and finalization SQL. Keep the generated SQL for the common case and edit
these advanced fields when the dataset requires it.

For example, an order dataset can reuse actual test-customer and product keys
through lookup groups, cycle a small list of statuses, generate quantities, and
format business codes. Columns in a lookup group can draw related values from
the same lookup record. Generated defaults consider foreign keys; review them
against your database constraints and intended distribution before loading.

`nextValue` and `formatExpression` are MVEL expressions evaluated by sqlapp,
not Excel formulas. Generation context includes `_index`, `_row_number`,
`_previous`, `_min`, and `_max`; formatting additionally uses `_value`.
Candidate lists cycle by index; lookup groups provide their own selection
strategies. If a column has a lookup group or candidate values, those take
precedence over its `nextValue` expression. Initial values from `startValueSql`
also affect the first generated row.

Start by editing row counts and a few columns. SQL/file lookups, mapping
expressions, custom evaluator functions, and custom INSERT SQL can be introduced
as the dataset becomes more specific. The companion example project demonstrates
custom function imports and conversion of generator configurations between
Excel and YAML; see the [example project guide](../gradle-plugin/example-project.md).

## Plan a million-row dataset

In the `Table` sheet, `dataSourceExpression` can use the generated template's
`iterator(n)` pattern. For example, `iterator(1000000)` requests one million
iterations **per starting row** returned by `startValueSql`. One starting row
therefore gives one million generated rows; multiple starting rows multiply
the result. For parent/child data, inspect the generated starting query rather
than assuming the expression is the final table row count.

This supports configurations targeting millions of rows, while the actual
throughput depends on the JDBC driver, database, indexes, constraints, row width,
lookup data, and generation expressions. No million-row timing or throughput
benchmark was executed for this guide.

The INSERT path uses JDBC `addBatch` and `executeBatch` with the configured
`dmlBatchSize`. Batch size controls how many statements are submitted together;
it is independent of the number of rows described by the template. The example's
1,000 is a starting point for measurement, not a universal optimum.

`queryCommitInterval` is an optional execution control for commit boundaries;
it is separate from `dmlBatchSize`. Review it together with the selected generation
path and database transaction behavior before enabling intermediate commits.
Previously committed work may remain after a later failure. This generator does
not provide the bulk migration workflow's checkpoints or verification reports.

Lookup query/file results are cached in memory, so JDBC batching does not imply
constant total memory use. Keep lookup datasets appropriately sized and measure
heap use as well as insertion time when increasing the generated volume.

## Check the result before scaling up

1. Generate a small dataset first and confirm counts, unique keys, and foreign keys.
2. Inspect representative values, nulls, ranges, and category distributions.
3. Increase the iteration count and measure runtime, heap, transaction logs,
   and database storage with the actual driver and constraints.
4. Plan cleanup or recreation of the test database before rerunning inserts;
   rerunning the same template is not an idempotent merge.

Save reviewed generation rules with the test scenario. Random selection and
custom functions can change exact values between runs; use deterministic rules
when exact repeatability is required.

For existing row transfers with resumability and verification, use
[bulk migration](../migration/README.md). For importing already prepared data
files, use the [data import/export tasks](../gradle-plugin/custom-tasks-and-migrations.md).
