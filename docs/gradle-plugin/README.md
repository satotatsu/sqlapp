# Gradle plugin task guide

The `com.sqlapp.db` plugin exposes sqlapp commands as Gradle tasks. This
document is the index and authoritative task-name reference. Runnable project
configurations are maintained in
[`sqlapp-gradle-example`](https://github.com/satotatsu/sqlapp-gradle-example).

## Applying the plugin

```groovy
plugins {
    id 'com.sqlapp.db' version '<sqlapp-version>'
}
```

Use the Gradle Wrapper and Java 21. Run `gradlew tasks` (Windows:
`gradlew.bat tasks`) to inspect the tasks available to a project.

## Tasks registered by the plugin

| Area | Task | Purpose |
|---|---|---|
| Schema inspection | `countAllTables` | Count rows in database tables |
| Schema inspection | `exportSchemaXml` | Export database metadata as sqlapp Schema XML |
| Schema comparison | `diffSchemaXml` | Compare two Schema XML files |
| SQL generation | `generateDiffSql` | Generate SQL from a schema difference |
| SQL generation | `generateSql` | Generate SQL from Schema XML |
| Documentation | `generateHtmlDocs` | Generate HTML documentation and ER diagrams |
| Migration | `migration` | Apply versioned database migrations |
| Migration | `migrationInsert` | Insert migration history |
| Migration | `migrationRepair` | Repair migration history |
| Normalization | `generateNormalizationPlan` | Generate reviewable normalization candidates and a preview schema |
| Normalization | `firstNormalForm` | Split repeating column groups and optionally replace composite primary keys |
| Normalization | `columnRuleTransform` | Apply YAML-based column type and naming rules |
| Legacy migration | `pliSchemaImport` | Convert PL/I declarations to Schema XML and migration mapping |
| Legacy migration | `generateLegacyMigrationContract` | Generate the CSV extraction/load contract |
| Legacy migration | `generatePliCsvExtractor` | Generate PL/I CSV extraction artifacts |
| Legacy migration | `generateLegacyRdbLoader` | Generate a restartable hierarchy load plan |
| Legacy migration | `loadLegacyHierarchy` | Execute a generated hierarchy load plan |

Some public task classes, such as data import/export, data generation, format
conversion, SQL execution, and migration-down tasks, are not registered under
a fixed name. A build may register those task types with a project-specific
name. See the runnable examples for their configuration.

## Choose a workflow

- Schema XML, SQL, migration, HTML, dictionaries:
  [`sqlapp-gradle-example`](https://github.com/satotatsu/sqlapp-gradle-example)
- Normalization and legacy migration:
  [Normalization and legacy-migration tasks](normalization-and-legacy-migration.md)
- Schema viewpoints:
  [Schema viewpoints](../schema-viewpoints.md)
- Building and testing sqlapp itself:
  [Build and test](../build-and-test.md)

## Configuration conventions

Task names and properties are public user-facing APIs. File and directory
properties use Gradle's lazy property types, but Groovy build scripts may use
normal assignment syntax:

```groovy
generateNormalizationPlan {
    targetFile = file('schemas/legacy.xml')
    outputDirectory = file('build/normalization-plan')
}
```

A value listed as a convention is used only when the build does not configure
the property. Required files and directories must be configured before task
execution. Relative paths are resolved against the Gradle project directory.

## Documentation maintenance

When a task is added or its user-facing properties change:

1. Update this index and the applicable task reference.
2. Update a runnable configuration in `sqlapp-gradle-example`.
3. Verify property names, types, conventions, required inputs, and outputs
   against the task implementation.

