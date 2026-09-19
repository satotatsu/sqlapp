# Gradle plugin with Kotlin DSL

The main Gradle plugin guides use Groovy DSL because the companion example
project uses `build.gradle`. This page shows the equivalent Kotlin DSL syntax
for the common Schema XML and HTML workflow.

sqlapp task properties use Gradle's lazy `Property`, `ListProperty`,
`DirectoryProperty`, and `RegularFileProperty` APIs. In `build.gradle.kts`, set
them with `set`, `add`, or `addAll` instead of Groovy assignment.

## Apply the plugin and add the database runtime

```kotlin
plugins {
    java
    id("com.sqlapp.db") version "<sqlapp-version>"
}

repositories {
    mavenCentral()
}

dependencies {
    runtimeOnly("com.sqlapp:sqlapp-core-postgres:<sqlapp-version>")
    runtimeOnly("org.postgresql:postgresql:<jdbc-driver-version>")
}
```

The Java plugin supplies the `runtimeClasspath` configuration that sqlapp
tasks use for JDBC drivers and dialect discovery. Keep the sqlapp plugin and
dialect versions aligned, and choose the JDBC driver version explicitly.

## Export Schema XML

Import the task type and configure the existing registered task:

```kotlin
import com.sqlapp.gradle.plugins.ExportSchemaXmlTask

tasks.named<ExportSchemaXmlTask>("exportSchemaXml") {
    dataSource {
        jdbcUrl.set(providers.environmentVariable("SQLAPP_JDBC_URL"))
        username.set(providers.environmentVariable("SQLAPP_DB_USER"))
        password.set(providers.environmentVariable("SQLAPP_DB_PASSWORD"))
    }

    includeSchemas.add("public")
    target.set("catalog")
    dumpRows.set(false)
    outputDirectory.set(layout.buildDirectory.dir("schema"))
    outputFileName.set("Catalog.xml")
}
```

Run `./gradlew exportSchemaXml`, or `.\gradlew.bat exportSchemaXml` in Windows
PowerShell. The example writes `build/schema/Catalog.xml`.

`dumpRows` defaults to the command behavior when it is not set. Set it to
`false` explicitly for a metadata-only export.

## Load DataSource settings from a file

Use the nested DataSource extension's file collection:

```kotlin
tasks.named<ExportSchemaXmlTask>("exportSchemaXml") {
    dataSource {
        properties.from(
            layout.projectDirectory.file("config/dataSource.properties")
        )
        username.set(providers.environmentVariable("SQLAPP_DB_USER"))
        password.set(providers.environmentVariable("SQLAPP_DB_PASSWORD"))
        maximumPoolSize.set(2)
    }
}
```

An example properties file is:

```properties
jdbcUrl=jdbc:postgresql://localhost:5432/app
driverClassName=org.postgresql.Driver
```

The loader also accepts Java-properties XML, JSON, YAML, and YML. Supply more
than one file with additional `properties.from(...)` calls or one call with
multiple arguments. Files are merged in their supplied order; directly set
extension values override loaded values. See
[Configure a DataSource from a file](task-reference.md#configure-a-data-source-from-a-file)
for the complete behavior.

## Generate HTML after export

Keep the task provider returned by `tasks.named` and use it as the dependency:

```kotlin
import com.sqlapp.gradle.plugins.ExportSchemaXmlTask
import com.sqlapp.gradle.plugins.GenerateHtmlDocsTask

val exportSchemaXml = tasks.named<ExportSchemaXmlTask>("exportSchemaXml")

tasks.named<GenerateHtmlDocsTask>("generateHtmlDocs") {
    dependsOn(exportSchemaXml)
    targetFile.set(layout.buildDirectory.file("schema/Catalog.xml"))
    outputDirectory.set(layout.buildDirectory.dir("docs/database"))
}
```

Gradle does not infer the task dependency from matching file paths. Keep the
explicit `dependsOn` when the XML must be refreshed before documentation is
generated. For reviewed offline XML, omit the dependency and point
`targetFile` to that saved file.

## Configure list and map properties

Use Gradle's collection-property methods:

```kotlin
tasks.named<ExportSchemaXmlTask>("exportSchemaXml") {
    includeSchemas.addAll("public", "audit")
    excludeTables.add("temporary_work")
    parameters.put("environment", "development")
}
```

Calling `set(listOf(...))` replaces the property's full value. `add` and
`addAll` preserve values supplied by conventions or earlier configuration.
Use `put` and `putAll` for `MapProperty` values.

## Register a task type without a fixed name

Some public task classes are not registered automatically. Register them with
their concrete type. For example:

```kotlin
import com.sqlapp.gradle.plugins.ConvertDataTask

tasks.register<ConvertDataTask>("convertReferenceData") {
    directory.set(layout.projectDirectory.dir("src/main/data"))
    outputDirectory.set(layout.buildDirectory.dir("converted-data"))
    includes.add("**/*.csv")
    outputFileType.set("json")
    csvEncoding.set("UTF-8")
    recursive.set(false)
    removeOriginalFile.set(false)
}
```

Before copying this example, check the task's inputs and file effects in
[Custom tasks and versioned migrations](custom-tasks-and-migrations.md) and the
[task reference](task-reference.md). Conversion tasks can be configured to
remove their original input files; leave that option disabled unless deletion
is intended.

## Groovy-to-Kotlin property mapping

| Groovy DSL | Kotlin DSL |
|---|---|
| `target = 'catalog'` | `target.set("catalog")` |
| `dumpRows = false` | `dumpRows.set(false)` |
| `includeSchemas.add('public')` | `includeSchemas.add("public")` |
| `outputDirectory = layout.buildDirectory.dir('schema')` | `outputDirectory.set(layout.buildDirectory.dir("schema"))` |
| `targetFile = file('schema.xml')` | `targetFile.set(layout.projectDirectory.file("schema.xml"))` |
| `properties file('config/db.properties')` | `properties.from(layout.projectDirectory.file("config/db.properties"))` |

Use typed `tasks.named<TaskType>(...)` and `tasks.register<TaskType>(...)` so
Kotlin can expose task-specific properties at script compilation time. A
string-only `tasks.named("...")` provider exposes only the base Gradle `Task`
API inside its configuration block.

For task names and all properties, continue with the
[Gradle plugin task guide](README.md). For runtime or path problems, see
[Troubleshooting](troubleshooting.md).
