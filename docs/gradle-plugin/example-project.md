# Runnable Gradle example project

[Task guide](README.md) · [Getting started](getting-started.md)

The companion
[`sqlapp-gradle-example`](https://github.com/satotatsu/sqlapp-gradle-example)
is the runnable configuration for this plugin. Its
[`build.gradle`](https://github.com/satotatsu/sqlapp-gradle-example/blob/develop/build.gradle)
contains both the tasks registered by `com.sqlapp.db` and custom task-type
registrations. Use it as a working catalog; copy only the blocks needed by
your project. This page reflects the local `develop` checkout, which tracks
the repository's `origin/develop` branch.

## Project configuration

[`gradle.properties`](https://github.com/satotatsu/sqlapp-gradle-example/blob/develop/gradle.properties)
selects the database family, JDBC driver, schema export level, default encoding,
and the path to the local data-source properties file:

```properties
db=hsql
jdbc_driver=org.hsqldb:hsqldb:2.7.4
schema_level=catalog
dataSourceProperties=./src/main/config/local/dataSource.properties
defaultEncoding=UTF-8
```

`db` becomes the suffix of `com.sqlapp:sqlapp-core-${db}`. Supported values
listed by the example are `db2`, `derby`, `firebird`, `h2`, `hsql`, `mariadb`,
`mysql`, `oracle`, `postgres`, `saphana`, `sqlite`, `sqlserver`, and `sybase`.
Set a matching JDBC artifact in `jdbc_driver`.

The referenced `src/main/config/local/dataSource.properties` is local
configuration. Create it for the selected database and keep credentials out of
version control. The task blocks call `dataSource.properties(...)`, so one
connection file is reused by schema export, migration, data movement, and test
data generation.

## Workflow map

| Goal | Example tasks | Detailed guide |
|---|---|---|
| Export database metadata | `exportSchemaXml` | [Schema model](https://github.com/satotatsu/sqlapp-gradle-example/blob/develop/docs/schema-model.md) |
| Compare snapshots and create migration SQL | `diffSchemaXml`, `generateDiffSql`, `generateSql` | [Schema diff](https://github.com/satotatsu/sqlapp-gradle-example/blob/develop/docs/schema-diff.md) |
| Apply versioned SQL | `migration`, custom `migrationDown` | [Migration](https://github.com/satotatsu/sqlapp-gradle-example/blob/develop/docs/migration.md) |
| Generate database documentation | `generateHtmlDocs`, custom `zipHtml` | [HTML documentation](https://github.com/satotatsu/sqlapp-gradle-example/blob/develop/docs/html-documentation.md) |
| Maintain display-name dictionaries | custom `updateDictionaries`, `dictionariesToCsv` | [Dictionary](https://github.com/satotatsu/sqlapp-gradle-example/blob/develop/docs/dictionary.md) |
| Add relationships absent from the database | `foreignKeyDefinitionDirectory` on relevant tasks | [Logical foreign keys](https://github.com/satotatsu/sqlapp-gradle-example/blob/develop/docs/logical-foreign-keys.md) |
| Export and import rows | custom `exportData`, `importData` | [Data import/export](https://github.com/satotatsu/sqlapp-gradle-example/blob/develop/docs/data-import-export.md) |
| Convert data formats | custom `toExcel`, `toJson`, `toYaml`, `toToml`, `toCsv` | [Format conversion](https://github.com/satotatsu/sqlapp-gradle-example/blob/develop/docs/format-conversion.md) |
| Generate relational test data | custom `generateDataConfig`, `generateData` | [Data generation](https://github.com/satotatsu/sqlapp-gradle-example/blob/develop/docs/data-generation.md) |
| Customize expressions and Java functions | generator and converter tasks | [MVEL and Java extensions](https://github.com/satotatsu/sqlapp-gradle-example/blob/develop/docs/mvel.md) |

## Schema lifecycle used by the example

```text
database
  -> exportSchemaXml
  -> schemas/latest/<schema_level>.xml
  -> review and promote to schemas/baseline/<schema_level>.xml
  -> generateDiffSql / generateSql
  -> reviewed versioned SQL
  -> migration

schemas/baseline/<schema_level>.xml
  -> updateDictionaries
  -> generateHtmlDocs
  -> html/
```

The example's `mvSchemaXml` task performs the promotion by moving the latest
file to the baseline location. That task directly deletes or renames files and
is project-specific; prefer a reviewed copy or version-control change in CI so
the baseline transition remains visible and recoverable.

The example does not wire these operations into one automatic task graph.
Run each review boundary deliberately, or add `dependsOn` only where automatic
regeneration is intended. In particular, do not make production migration
implicitly depend on a live schema export or newly generated SQL.

## Useful commands

From the example project directory:

```shell
./gradlew tasks --all
./gradlew exportSchemaXml
./gradlew diffSchemaXml
./gradlew generateDiffSql
./gradlew generateHtmlDocs
```

On Windows PowerShell, replace `./gradlew` with `.\gradlew.bat`. Tasks that
connect to a database use the configured data-source file. XML comparison,
SQL generation from saved XML, format conversion, and HTML generation can run
without a database when all required input files already exist.
