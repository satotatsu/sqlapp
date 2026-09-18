# Getting started with the Gradle plugin

[Task guide](README.md) · [Schema, SQL and HTML reference](schema-sql-and-html.md)

For a complete build containing these workflows, see the
[`sqlapp-gradle-example` project](https://github.com/satotatsu/sqlapp-gradle-example)
and the [project map](example-project.md).

The `com.sqlapp.db` plugin turns sqlapp commands into Gradle tasks. A common
workflow reads database metadata once into Schema XML, then generates HTML or
SQL from that file. The following examples use Groovy DSL and Java 21.

## 1. Apply the plugin and supply the database runtime

In a project with a Gradle Wrapper, create `build.gradle`:

```groovy
plugins {
    id 'java'
    id 'com.sqlapp.db' version '<sqlapp-version>'
}

repositories {
    mavenCentral()
}

dependencies {
    runtimeOnly 'com.sqlapp:sqlapp-core-postgres:<sqlapp-version>'
    runtimeOnly 'org.postgresql:postgresql:<jdbc-driver-version>'
}
```

Replace the version placeholders with your selected releases. Keep the sqlapp
dialect and plugin versions aligned. PostgreSQL is used here as an example;
for another database, supply its `sqlapp-core-{db}` module and JDBC driver.
The dialect supplies sqlapp's metadata and SQL behavior; the driver supplies
the JDBC connection. They serve different purposes.

The companion example selects them through `gradle.properties`:

```properties
db=hsql
jdbc_driver=org.hsqldb:hsqldb:2.7.4
schema_level=catalog
dataSourceProperties=./src/main/config/local/dataSource.properties
defaultEncoding=UTF-8
```

Its `build.gradle` maps those values to `implementation` dependencies because
the example also has a `copyLib` task. For ordinary execution, either
`implementation` or `runtimeOnly` reaches Java's `runtimeClasspath`; use
`implementation` only when your build also needs to compile against the driver
or dialect classes.

The `java` plugin creates `runtimeClasspath`. sqlapp tasks pick up that
configuration when they are constructed. This also works for custom task
types. In a build without the Java plugin, provide an explicit configuration:

```groovy
configurations { sqlappRuntime }
dependencies {
    sqlappRuntime 'com.sqlapp:sqlapp-core-postgres:<sqlapp-version>'
    sqlappRuntime 'org.postgresql:postgresql:<jdbc-driver-version>'
}
tasks.withType(com.sqlapp.gradle.plugins.AbstractTask).configureEach {
    runtimeClasspath.from(configurations.sqlappRuntime)
}
```

Use either runtime setup. Applying `com.sqlapp.db` alone does not create a
general-purpose JDBC dependency configuration.

## 2. Export metadata

Append this configuration to `build.gradle`:

```groovy
tasks.named('exportSchemaXml') {
    dataSource {
        jdbcUrl = providers.environmentVariable('SQLAPP_JDBC_URL')
        username = providers.environmentVariable('SQLAPP_DB_USER')
        password = providers.environmentVariable('SQLAPP_DB_PASSWORD')
    }
    includeSchemas.add('public')
    target = 'catalog'
    dumpRows = false
    outputDirectory = layout.buildDirectory.dir('schema')
    outputFileName = 'Catalog.xml'
}
```

Set those environment variables for your database before running the task.
For a metadata-only export, explicitly set `dumpRows = false`: the command's
default is `true`. `includeSchemas` selects metadata by schema name; it does
not change the connection's default schema.

```shell
./gradlew exportSchemaXml
```

On Windows PowerShell use `.\gradlew.bat exportSchemaXml`. The configured
output is `build/schema/Catalog.xml`.

## 3. Generate HTML from the XML

```groovy
tasks.named('generateHtmlDocs') {
    dependsOn(tasks.named('exportSchemaXml'))
    targetFile = layout.buildDirectory.file('schema/Catalog.xml')
    outputDirectory = layout.buildDirectory.dir('docs/database')
}
```

```shell
./gradlew generateHtmlDocs
```

This runs the export first and writes documentation under `build/docs/database`.
The HTML task itself consumes XML; it does not need a database connection.
For a saved, reviewed snapshot, omit `dependsOn` and set `targetFile` to
`layout.projectDirectory.file('schemas/Catalog.xml')` instead. That workflow
does not refresh the snapshot from the database.

The plugin registers individual tasks, but does not automatically chain export,
comparison, documentation or migration. A matching filename alone is not a
task dependency. Configure `dependsOn` when a task must generate an input.

## Connection settings

`dataSource { ... }` configures a task's Hikari data source. Versioned migration
tasks instead share the `migration` extension described in the
[migration guide](custom-tasks-and-migrations.md#versioned-migrations).

| Property | Type | Meaning |
|---|---|---|
| `jdbcUrl` | `Property<String>` | JDBC URL; use this name, not `url` |
| `driverClassName` | `Property<String>` | Explicit JDBC driver class when needed |
| `username`, `password` | `Property<String>` | Connection credentials |
| `catalog`, `schema` | `Property<String>` | Default connection catalog/schema |
| `maximumPoolSize` | `Property<Integer>` | Defaults to 5 when not set on the extension |
| `minimumIdle` | `Property<Integer>` | Defaults to 0 when not set on the extension |
| `autoCommit` | `Property<Boolean>` | Optional connection setting; command transaction handling still applies |
| `properties` | `ConfigurableFileCollection` | Connection configuration files |

For example, load an existing Hikari properties file and override credentials:

```groovy
tasks.named('exportSchemaXml') {
    dataSource {
        properties file('config/jdbc.properties')
        username = providers.environmentVariable('SQLAPP_DB_USER')
        password = providers.environmentVariable('SQLAPP_DB_PASSWORD')
        maximumPoolSize = 2
    }
}
```

```properties
jdbcUrl=jdbc:postgresql://localhost:5432/app
driverClassName=org.postgresql.Driver
```

Explicit extension values override loaded file values. The pool-size fallbacks
of 5 and 0 are also applied after file loading, so set those two values on the
extension when you need to override the fallbacks. Avoid storing credentials
in a committed build script or properties file.

## Troubleshooting

| Symptom | Check |
|---|---|
| Task name is missing | Run `./gradlew tasks --all`; confirm the plugin version and whether the task type requires custom registration |
| Driver class cannot be loaded | Put the JDBC driver on the task's `runtimeClasspath`, not only a buildscript-unrelated configuration |
| Database dialect cannot be resolved | Include the matching sqlapp dialect module and retain database product/version metadata in Schema XML |
| Missing property or input file | Configure the required input and ensure its producer is a dependency; `--dry-run` checks task order, not file contents |
| Export unexpectedly reads table rows | Set `dumpRows = false` for metadata-only XML |
| SQL appears only in the console | Configure `outputDirectory` on the SQL generation task |
| Offline documentation still connects to a database | Remove the export dependency and point at an existing XML snapshot |

Use `./gradlew <task> --stacktrace` for a failure trace. Task implementations
disable build caching by default; do not assume database state is represented
by Gradle inputs or that an unchanged build script proves the database is unchanged.

These examples are configuration recipes checked against the repository's
implementation and tests, not a claim of execution against your database.
See [Build and test](../build-and-test.md) for testing sqlapp itself.
