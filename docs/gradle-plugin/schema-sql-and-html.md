# Schema XML, SQL and HTML tasks

[Task guide](README.md) · [Setup and connection configuration](getting-started.md)

Schema XML is the shared representation between these tasks. Keep database
product/version information in the XML so SQL generation can resolve the
appropriate dialect. Examples below assume the plugin and runtime dependencies
from the setup guide have already been configured.

## Export database metadata

`exportSchemaXml` reads the configured database and writes Schema XML.

```groovy
tasks.named('exportSchemaXml') {
    dataSource {
        jdbcUrl = providers.environmentVariable('SQLAPP_JDBC_URL')
        username = providers.environmentVariable('SQLAPP_DB_USER')
        password = providers.environmentVariable('SQLAPP_DB_PASSWORD')
    }
    includeSchemas.add('public')
    excludeTables.add('temporary_work')
    target = 'catalog'
    dumpRows = false
    outputDirectory = layout.buildDirectory.dir('schema')
    outputFileName = 'Catalog.xml'
}
```

| Property | Type | Behavior |
|---|---|---|
| `dataSource` | Nested configuration | Database to inspect |
| `target` | `Property<String>` | Metadata root; command default is `catalog` |
| `outputDirectory` | `DirectoryProperty` | XML destination directory; set explicitly for predictable output |
| `outputFileName` | `Property<String>` | Filename within the output directory; specify it when another task consumes the file |
| `includeSchemas`, `excludeSchemas` | `ListProperty<String>` | Schema selection |
| `includeTables`, `excludeTables` | `ListProperty<String>` | Table selection |
| `dumpRows` | `Property<Boolean>` | Command default `true`; use `false` to export metadata only |
| `includeRowDumpTables`, `excludeRowDumpTables` | `ListProperty<String>` | Additional selection for row dumping, separate from metadata selection |

`exportAccessSchemaXml` and `exportSqliteSchemaXml` take `inputFile` and
`outputFile` instead of this JDBC export's output directory and filename.
See the [file export examples](README.md#export-an-access-file) for their
configuration and limitations.

## Compare two schema snapshots

```groovy
tasks.named('diffSchemaXml') {
    originalFile = layout.projectDirectory.file('schemas/before.xml')
    targetFile = layout.projectDirectory.file('schemas/after.xml')
}
```

Run `./gradlew diffSchemaXml`. Both properties are `RegularFileProperty`
inputs: `originalFile` is the old schema and `targetFile` the desired schema.
The task reports the comparison through command logging. It does not write a
migration SQL file. Advanced callers can set `equalsHandler` to customize
schema equality; use the same comparison rules when generating corresponding SQL.

## Generate CREATE SQL

```groovy
tasks.named('generateSql') {
    targetFile = layout.projectDirectory.file('schemas/after.xml')
    sqlType = 'CREATE'
    outputDirectory = layout.buildDirectory.dir('sql/create')
    outputAsMultiFiles = true
    encoding = 'UTF-8'
}
```

Run `./gradlew generateSql`. `targetFile` is required for this workflow.
`sqlType` is a string mapped to sqlapp's SQL type; the command default is
`CREATE`. `includeSchemas`, `excludeSchemas`, `includeTables` and
`excludeTables` can filter generated operations. Available operations depend
on the object and database dialect.

## Generate change SQL

```groovy
tasks.named('generateDiffSql') {
    originalFile = layout.projectDirectory.file('schemas/before.xml')
    targetFile = layout.projectDirectory.file('schemas/after.xml')
    outputDirectory = layout.buildDirectory.dir('sql/diff')
    outputAsMultiFiles = true
    withVersionDown = false
    encoding = 'UTF-8'
}
```

Run `./gradlew generateDiffSql`. Both XML inputs are needed. Set
`withVersionDown` explicitly: it is declared as a non-optional task input
without a convention. With no schema changes, no SQL files are generated.
Both SQL generators write SQL to files or standard output; they do not apply
the generated statements to the database. Review the output before using it
in a migration, especially when removing or changing columns.

### SQL output options

| Property | Type | Behavior when omitted |
|---|---|---|
| `outputDirectory` | `DirectoryProperty` | SQL is sent to standard output |
| `outputAsMultiFiles` | `Property<Boolean>` | `true`: one file per SQL operation |
| `encoding` | `Property<String>` | `UTF-8` for generated SQL files |
| `changeNumberStep` | `Property<Object>` converted to a number | 10 between generated change numbers |
| `numberOfDigits` | `Property<Object>` converted to a number | 19 digits, padded with zeros |
| `lastChangeNumber` | `Property<Object>` converted to a number | Uses the latest existing SQL version in the output directory, otherwise a timestamp-derived number |

An existing output directory's SQL versions take precedence over
`lastChangeNumber`. Generation into a directory with earlier output can
therefore allocate new numbers on subsequent runs. Use a dedicated directory
for each reviewed change set rather than treating it as a deterministic
single-file overwrite.

`generateSql` supports `outputAsMultiFiles = false` to combine operations into
one numbered file. For `generateDiffSql`, prefer the multi-file example above:
its single-file implementation distinguishes an existing directory from a
nonexistent path, while Gradle declares the property as an output directory.
`withVersionDown` is only used by its single-file branch; it has no effect in
multi-file mode. These are current implementation limitations.

## Generate HTML documentation and ER diagrams

```groovy
tasks.named('generateHtmlDocs') {
    targetFile = layout.projectDirectory.file('schemas/after.xml')
    outputDirectory = layout.buildDirectory.dir('docs/database')
}
```

Run `./gradlew generateHtmlDocs`, then inspect the HTML output directory.
This task consumes saved metadata and can run independently of the database.

| Property | Type | Purpose |
|---|---|---|
| `targetFile` | `RegularFileProperty` | Input Schema XML for the single-file workflow |
| `outputDirectory` | `DirectoryProperty` | Generated documentation destination |
| `dictionaryFileDirectory` | `DirectoryProperty` | Optional dictionary files for documentation |
| `foreignKeyDefinitionDirectory` | `DirectoryProperty` | Optional virtual foreign-key definitions |
| `viewpointsFile` | `RegularFileProperty` | Optional external viewpoint definitions |
| `viewpointId` | `Property<String>` | Select a named viewpoint |
| `multiThread` | `Property<Boolean>` | Optional command threading override |
| `renderOptions` | `Property<RenderOptionExtension>` | Advanced rendering configuration |

For a viewpoint-specific document, add the following to the task:

```groovy
viewpointsFile = layout.projectDirectory.file('viewpoints.yaml')
viewpointId = 'sales'
```

See [Schema viewpoints](../schema-viewpoints.md) for the YAML format, table
resolution rules, and reuse with Loader generation. A viewpoint can include
tables from the shared Schema model without changing the underlying XML.

## Implementation and test references

- [ExportSchemaXmlTask](../../sqlapp-gradle-plugin/src/main/java/com/sqlapp/gradle/plugins/ExportSchemaXmlTask.java)
  and [export test](../../sqlapp-gradle-plugin/src/test/groovy/com/sqlapp/gradle/plugins/ExportSchemaXmlTaskTest.groovy)
- [GenerateSqlTask](../../sqlapp-gradle-plugin/src/main/java/com/sqlapp/gradle/plugins/GenerateSqlTask.java)
  and [SQL generation test](../../sqlapp-gradle-plugin/src/test/groovy/com/sqlapp/gradle/plugins/GenerateSqlTaskTest.groovy)
- [GenerateDiffSqlTask](../../sqlapp-gradle-plugin/src/main/java/com/sqlapp/gradle/plugins/GenerateDiffSqlTask.java)
- [GenerateHtmlDocsTask](../../sqlapp-gradle-plugin/src/main/java/com/sqlapp/gradle/plugins/GenerateHtmlDocsTask.java)
  and [HTML generation test](../../sqlapp-gradle-plugin/src/test/groovy/com/sqlapp/gradle/plugins/GenerateHtmlDocsTaskTest.groovy)
