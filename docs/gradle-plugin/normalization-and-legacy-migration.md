# Normalization and legacy-migration Gradle tasks

This page is the property reference for the normalization and legacy-migration
tasks registered by `com.sqlapp.db`. The sqlapp Schema XML model is the
canonical representation passed between the tasks.

## Recommended workflow

```text
PL/I declaration
  -> pliSchemaImport
  -> Schema XML and migration mapping
  -> generateNormalizationPlan
  -> reviewed normalization rules
  -> columnRuleTransform / firstNormalForm
  -> normalized Schema XML and updated mapping
  -> generateLegacyMigrationContract
  -> generatePliCsvExtractor
  -> generateLegacyRdbLoader
  -> loadLegacyHierarchy
```

Not every project needs every stage. For example, an existing Schema XML can
start at `generateNormalizationPlan`, and CSV extraction can be implemented
outside sqlapp while still using the contract and loader.

## `generateNormalizationPlan`

Generates a YAML file containing normalization candidates for human or AI
review. It can also generate a preview Schema XML so that before/after HTML
documentation and ER diagrams can be compared.

| Property | Type | Required | Convention | Description |
|---|---|---:|---|---|
| `targetFile` | `RegularFileProperty` | yes | — | Source Schema XML |
| `migrationMappingFile` | `RegularFileProperty` | no | — | Existing migration mapping to augment |
| `outputDirectory` | `DirectoryProperty` | yes | — | Plan and preview output directory |
| `minimumColumnCount` | `Property<Integer>` | yes | `2` | Minimum repeating-column count considered a candidate |
| `variableCharacterMinimumLength` | `Property<Long>` | yes | `20` | Character-length threshold used by type recommendations |
| `previewSchemaEnabled` | `Property<Boolean>` | yes | `true` | Generate preview Schema XML |
| `locale` | `Property<String>` | yes | JVM locale | Language tag used for review questions, such as `ja` or `en` |

## `columnRuleTransform`

Applies reviewed YAML column transformation rules to Schema XML. This changes
schema metadata only; data conversion belongs to the migration process.

| Property | Type | Required | Convention | Description |
|---|---|---:|---|---|
| `targetFile` | `RegularFileProperty` | yes | — | Source Schema XML |
| `outputDirectory` | `DirectoryProperty` | yes | — | Transformed Schema XML directory |
| `rulesFile` | `RegularFileProperty` | yes | — | Reviewed YAML transformation rules |
| `migrationMappingEnabled` | `Property<Boolean>` | yes | `true` | Generate or update migration mapping |
| `migrationMappingDirectory` | `DirectoryProperty` | no | command default | Mapping output directory |
| `migrationMappingFileName` | `Property<String>` | no | command default | Mapping output filename |
| `migrationMappingFile` | `RegularFileProperty` | no | — | Existing migration mapping |

## `firstNormalForm`

Splits repeating column clusters into child tables. It can also replace
composite primary keys with surrogate keys.

| Property | Type | Required | Convention | Description |
|---|---|---:|---|---|
| `targetFile` | `RegularFileProperty` | yes | — | Source Schema XML |
| `outputDirectory` | `DirectoryProperty` | yes | — | Normalized Schema XML directory |
| `minimumColumnCount` | `Property<Integer>` | yes | `2` | Repeating-column cluster threshold |
| `migrationMappingEnabled` | `Property<Boolean>` | yes | `true` | Generate or update migration mapping |
| `convertCompositePrimaryKey` | `Property<Boolean>` | yes | `false` | Convert composite primary keys to surrogate keys |
| `surrogateKeyGenerationType` | `Property<SurrogateKeyGenerationType>` | yes | `IDENTITY` | Surrogate-key generation mode; `IDENTITY` or `SEQUENCE` |
| `migrationMappingDirectory` | `DirectoryProperty` | no | command default | Mapping output directory |
| `migrationMappingFileName` | `Property<String>` | no | command default | Mapping output filename |
| `migrationMappingFile` | `RegularFileProperty` | no | — | Existing migration mapping |

Advanced naming and datatype strategies are Java/Groovy closures exposed as
task properties:

| Property | Function arguments | Convention |
|---|---|---|
| `childKeyColumnNameStrategy` | child table | `ROW_NO` |
| `childTableNameStrategy` | source table, cluster number | `<table>_DETAIL_<cluster-number>` |
| `surrogatePrimaryKeyColumnNameStrategy` | table | `ID` |
| `surrogatePrimaryKeyDataTypeStrategy` | table | `INT` |
| `surrogateForeignKeyColumnNameStrategy` | referenced table name, original column names | `PARENT_ID` |
| `surrogateSequenceNameStrategy` | table | `SEQ_<table>` |

These closures are marked internal for Gradle up-to-date checking. Builds that
replace a strategy should treat the resulting schema as a new generated
artifact and review the migration mapping.

## `pliSchemaImport`

Parses the ranges selected by a PL/I import configuration and emits Schema XML.
PL/I comments are retained as schema remarks where supported by the importer.

| Property | Type | Required | Convention | Description |
|---|---|---:|---|---|
| `targetFile` | `RegularFileProperty` | yes | — | PL/I source file |
| `configurationFile` | `RegularFileProperty` | yes | — | Import range and hierarchy configuration |
| `outputDirectory` | `DirectoryProperty` | yes | — | Schema XML output directory |
| `encoding` | `Property<String>` | yes | `UTF-8` | PL/I source encoding |
| `outputFileName` | `Property<String>` | no | command default | Schema XML filename |
| `migrationMappingEnabled` | `Property<Boolean>` | yes | `true` | Generate the initial migration mapping |
| `migrationMappingDirectory` | `DirectoryProperty` | no | command default | Mapping output directory |
| `migrationMappingFileName` | `Property<String>` | no | command default | Mapping output filename |

## `generateLegacyMigrationContract`

Resolves the migration mapping into an explicit CSV extraction and load
contract.

| Property | Type | Required | Convention | Description |
|---|---|---:|---|---|
| `mappingFile` | `RegularFileProperty` | yes | — | Final migration mapping |
| `outputDirectory` | `DirectoryProperty` | yes | — | Contract output directory |
| `outputFileName` | `Property<String>` | no | command default | Contract filename |
| `encoding` | `Property<String>` | yes | `UTF-8` | CSV encoding |
| `delimiter` | `Property<String>` | yes | `,` | CSV delimiter |
| `quote` | `Property<String>` | yes | `"` | CSV quote character |
| `nullValue` | `Property<String>` | yes | empty string | CSV representation of null |
| `header` | `Property<Boolean>` | yes | `true` | Include CSV headers |
| `recordSeparator` | `Property<String>` | yes | `CRLF` | CSV record separator |

## `generatePliCsvExtractor`

Generates PL/I extraction artifacts from the resolved contract.

| Property | Type | Required | Convention | Description |
|---|---|---:|---|---|
| `contractFile` | `RegularFileProperty` | yes | — | Migration contract |
| `outputDirectory` | `DirectoryProperty` | yes | — | Generated PL/I artifact directory |
| `programName` | `Property<String>` | yes | `SQLAPEXT` | Generated PL/I program name |

Generated PL/I must be reviewed against the host compiler, source layouts,
encoding, record format, and operational standards before production use.

## `generateLegacyRdbLoader`

Generates a restartable load plan for staged hierarchical CSV data. The loader
uses sqlapp's schema model, SQL generation, dialect handling, and
`JdbcTreeDataSession`.

| Property | Type | Required | Convention | Description |
|---|---|---:|---|---|
| `contractFile` | `RegularFileProperty` | yes | — | Migration contract |
| `schemaFile` | `RegularFileProperty` | yes | — | Final target Schema XML |
| `outputDirectory` | `DirectoryProperty` | yes | — | Load-plan output directory |
| `tableOperationMode` | `Property<String>` | yes | `INSERT_IGNORE` | Target write operation |
| `rootBatchSize` | `Property<Integer>` | yes | `500` | Root rows handled in one JDBC tree batch |
| `commitEveryRootBatches` | `Property<Long>` | yes | `500` | Root batches committed as one transaction |
| `deleteCommittedRoots` | `Property<Boolean>` | yes | `true` | Delete committed staging roots; otherwise mark them loaded |
| `stagingTablePrefix` | `Property<String>` | yes | `TMP_` | Staging-table prefix |
| `rootCursorStrategy` | `Property<String>` | yes | `DIALECT` | Cursor strategy selection |
| `databaseProductName` | `Property<String>` | no | — | Product used for offline dialect resolution |
| `databaseProductMajorVersion` | `Property<Integer>` | yes | `0` | Product major version |
| `databaseProductMinorVersion` | `Property<Integer>` | yes | `0` | Product minor version |
| `generateRunnerTemplate` | `Property<Boolean>` | yes | `false` | Also generate an optional Java runner template |
| `runnerClassName` | `Property<String>` | yes | `LegacyMigrationLoader` | Runner template class name |
| `viewpointsFile` | `RegularFileProperty` | no | — | Schema viewpoint YAML |
| `viewpointId` | `Property<String>` | no | — | Viewpoint used to limit the load scope |
| `includeViewpointAncestors` | `Property<Boolean>` | yes | `true` | Include required hierarchy ancestors |

The number of roots per commit is
`rootBatchSize * commitEveryRootBatches`. Validate transaction size, JDBC
parameter limits, and staging indexes with production-scale data.

## `loadLegacyHierarchy`

Executes a generated load plan against the configured database.

| Property | Type | Required | Convention | Description |
|---|---|---:|---|---|
| `loadPlanFile` | `RegularFileProperty` | yes | — | Generated load-plan YAML |
| `schemaFile` | `RegularFileProperty` | no | plan value | Optional Schema XML override |
| `dataSource` | nested `DataSourceExtension` | yes | — | JDBC connection configuration |

This task changes database data. Test the generated plan against a disposable
database before running it against a migration environment.
