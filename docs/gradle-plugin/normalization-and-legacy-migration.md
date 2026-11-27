# Normalization and legacy-migration Gradle tasks

This page is the property reference for the normalization and legacy-migration
tasks registered by `com.sqlapp.db`. The sqlapp Schema XML model is the
canonical representation passed between the tasks.

## Recommended workflow

```text
PL/I declaration or COBOL copybook
  -> pliSchemaImport / cobolSchemaImport
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
| `foreignKeyDefinitionDirectory` | `DirectoryProperty` | no | — | External virtual-FK definitions used to recognize logical parent-child relationships |
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
| `foreignKeyDefinitionDirectory` | `DirectoryProperty` | no | — | External virtual-FK definitions to load before normalization |
| `minimumColumnCount` | `Property<Integer>` | yes | `2` | Repeating-column cluster threshold |
| `migrationMappingEnabled` | `Property<Boolean>` | yes | `true` | Generate or update migration mapping |
| `convertCompositePrimaryKey` | `Property<Boolean>` | yes | `false` | Convert composite primary keys to surrogate keys |
| `surrogateKeyGenerationType` | `Property<SurrogateKeyGenerationType>` | yes | `IDENTITY` | Surrogate-key generation mode; `IDENTITY` or `SEQUENCE` |
| `migrationMappingDirectory` | `DirectoryProperty` | no | command default | Mapping output directory |
| `migrationMappingFileName` | `Property<String>` | no | command default | Mapping output filename |
| `migrationMappingFile` | `RegularFileProperty` | no | — | Existing migration mapping |

Converted virtual relationships are written into the normalized Schema XML.
Do not apply the original pre-normalization virtual-FK definitions again when
generating documentation from that normalized XML.

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

Parses the level declarations selected by a PL/I import configuration and
emits Schema XML and an initial migration mapping. Each selected level-1
structure becomes a root table; non-array groups are flattened into their
table; each array becomes a child table that inherits the parent primary key
and adds a one-based occurrence column. A scalar array such as
`2 SCORES(5) FIXED DEC(5,2)` becomes a child table with one value column.

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

### Import configuration

The minimum configuration names the schema and the primary key of each root
table:

```yaml
schemaName: COMPANY
structures:
  - name: COMPANY_MASTER
    primaryKey: [COMPANY_ID]
```

Declaration names, item paths and primary key columns are matched
case-insensitively. An item path is dot separated from the level-1 name, for
example `COMPANY_MASTER.DEPARTMENT_GROUP.EMPLOYEE_LIST`. Every name and path in
the configuration must exist in the source; unknown entries are errors.

| Key | Default | Description |
|---|---|---|
| `formatVersion` | `1` | Configuration format version |
| `schemaName` | required | Target schema name |
| `structures[].name` | required | Level-1 declaration name |
| `structures[].tableName` | declaration name | Root table name |
| `structures[].primaryKey` | required | Root table primary key columns |
| `includeDeclarations` | all with `structures` | Declarations to import; each needs a `structures` entry |
| `excludeDeclarations` | none | Declarations to skip |
| `excludeItems` | none | Item paths not imported; their storage still counts for offsets |
| `columnNames` | item name | Column name per scalar item path, for example to resolve flattened name conflicts |
| `generatedTableNames` | array name | Table name per array item path |
| `occurrenceColumnNames` | pattern | Occurrence column name per array item path |
| `occurrenceColumnNamePattern` | `${groupName}_NO` | `${groupName}` is the array name, `${tableName}` the generated table name |
| `prefixGroupRemarks` | `true` | Prefix column remarks with enclosing non-array group remarks |
| `commentPlacement` | `AUTO` | `AUTO`, `TRAILING` or `LEADING`; see below |
| `margins` | whole line | `[left, right]` source columns, for example `[2, 72]` for sources with sequence numbers |
| `bit1AsBoolean` | `true` | Map `BIT(1)` to `BOOLEAN` instead of `BINARY(1)` |

Comments become table and column remarks. With `AUTO`, a comment that starts
on the line where an item ends belongs to that item, and other comments belong
to the next item unless a blank line separates them, so file banners are not
attached. `TRAILING` attaches comments to the preceding item and `LEADING` to
the following item. Comments inside an item always belong to that item.

Supported data attributes and their default column types:

| PL/I | Column type |
|---|---|
| `CHAR(n)` / `CHAR(n) VARYING` / `VARYINGZ` | `CHAR(n)` / `VARCHAR(n)` |
| `GRAPHIC(n)`, `WIDECHAR(n)` (optionally varying) | `NCHAR(n)` / `NVARCHAR(n)` |
| `BIT(1)` / `BIT(n)` | `BOOLEAN` / `BINARY(ceil(n/8))` |
| `FIXED BIN(p)` (`SIGNED`/`UNSIGNED`) | `SMALLINT`, `INT`, `BIGINT` or `DECIMAL(20)` by value range |
| `FIXED DEC(p,q)` | `DECIMAL(p,q)`; `DECIMAL(p-q,0)` when `q < 0`, `DECIMAL(q,q)` when `q > p` |
| `FLOAT BIN(p)` / `FLOAT DEC(p)` | `REAL` (short) or `DOUBLE` (long) |
| `PIC` numeric picture | `DECIMAL(digits, digits after V)` |
| `PIC` character picture (`X`, `A`, `9`) | `CHAR(n)` |

Attributes may appear in any order (`BIN FIXED(31)`), and PL/I defaults apply
(`FIXED` alone is `FIXED DEC(5)`, `FIXED BIN` is `FIXED BIN(15)`). Storage and
initialization attributes such as `BASED`, `STATIC`, `INIT` and `DEFINED` are
ignored. Unnamed `*` items are not imported but occupy storage. Arrays may use
`(n)`, `(lower:upper)` or `DIM(...)`; the lower bound is recorded in the
mapping. `%INCLUDE` is not expanded and is reported as a warning; other
non-declaration statements are skipped.

Items that cannot become columns, such as `POINTER`, `LIKE`, `UNION`,
`REFER`, multi-dimensional arrays, scaled `FIXED BIN(p,q)`, extended `FLOAT`
and `BIT VARYING`, are errors when imported. Exclude them with `excludeItems`
if they are not needed. Level-1 arrays are rejected; declare a level-1
structure that contains the array instead.

The migration mapping records the source storage layout under each column's
`conversion.storage` (`offset` and `length` in bytes, plus `bitOffset` and
`bitLength` for bit data), the root table's `details.storage.recordLength`,
and each array's `offset` and `elementLength` under `details.occurrence`.
Offsets are relative to the record, or to one array element for items inside
an array. They are calculated only when every item is byte or bit aligned,
which holds for `UNALIGNED` structures and for structures of character,
picture and `FIXED DEC` data. Items that need halfword or larger alignment,
such as `FIXED BIN` and `FLOAT` without `UNALIGNED`, are placed by the
compiler's structure mapping; the import then omits offsets and adds a
warning. Take offsets from the compiler `AGGREGATE` listing in that case.

## `cobolSchemaImport`

Parses a COBOL copybook and emits Schema XML and a migration mapping. The
mapping records the storage layout in the same places as `pliSchemaImport`:
each column's `conversion.storage` (`offset` and `length` in bytes, `usage`,
and for numeric items `digits`, `scale`, `signed`, `signPosition` and
`signSeparate`), the root table's `details.storage` (`recordLength`,
`variableLength`), and each OCCURS table's `offset`, `length`,
`elementLength`, `minimum` and `dependingOn` under `details.occurrence`.
Offsets inside an OCCURS table are relative to one element. The COBOL name,
category, PICTURE and level-88 conditions are recorded in `conversion`.

| Property | Type | Required | Convention | Description |
|---|---|---:|---|---|
| `targetFile` | `RegularFileProperty` | yes | — | COBOL copybook |
| `configurationFile` | `RegularFileProperty` | yes | — | Import configuration (below) |
| `outputDirectory` | `DirectoryProperty` | yes | — | Schema XML output directory |
| `encoding` | `Property<String>` | yes | `UTF-8` | Copybook text encoding |
| `outputFileName` | `Property<String>` | no | command default | Schema XML filename |
| `migrationMappingEnabled` | `Property<Boolean>` | yes | `true` | Generate the initial migration mapping |
| `migrationMappingDirectory` | `DirectoryProperty` | no | command default | Mapping output directory |
| `migrationMappingFileName` | `Property<String>` | no | command default | Mapping output filename |

Configuration:

```yaml
formatVersion: 1
schemaName: HOST
sourceFormat: FIXED            # FIXED (columns 7-72) or FREE
replacing:                     # like COPY ... REPLACING ==:PFX:== BY ==CUST==
  ":PFX:": CUST
recordName: CUSTOMER-REC       # for a copybook without a level-01 entry; default: file name
includeRecords: []
excludeRecords: []
records:
  - name: CUSTOMER-REC
    tableName: CUSTOMER        # default: record name with '-' replaced by '_'
    primaryKey: [CUST-ID]      # COBOL or column names
redefines:                     # redefined item (path or name): selected alternative
  BIRTH-DATE: BIRTH-DATE-N
columnNames:                   # item path: column name
  CUSTOMER-REC.ORDERS.ORDER-NO: ORDER_ID
generatedTableNames:           # OCCURS item path: table name
  CUSTOMER-REC.ORDERS: CUSTOMER_ORDER
occurrenceColumnNamePattern: ${groupName}_NO
prefixGroupRemarks: true
```

- Each record becomes a table. Group items are flattened; COBOL names become
  column names with `-` replaced by `_`. Two items with the same name in one
  table are rejected; give one of them a name in `columnNames`.
- An item with OCCURS becomes a child table keyed by the parent key and an
  occurrence number, as with PL/I arrays.
- FILLER items are not imported; named items inside a FILLER group are.
- For REDEFINES, the redefined item is imported and the redefining items are
  reported as warnings, unless `redefines` selects another alternative.
- Level-88 condition names are recorded in the mapping; comments become remarks.
- The mapping's source column is the column name, because the contract uses it
  as the staging column; the COBOL name stays in `sourcePath` and
  `conversion.cobolName`.
- Configuration entries that match nothing are rejected.

| PICTURE / USAGE | Column type | Storage bytes |
|---|---|---|
| `X(n)`, `A(n)` | `CHAR(n)` | n |
| `N(n)` (NATIONAL), `G(n)` (DISPLAY-1) | `NCHAR(n)` | 2n |
| `9(p)V9(s)` DISPLAY | `DECIMAL(p+s,s)` | p+s, +1 with SIGN SEPARATE |
| `9(p)V9(s)` COMP-3 / PACKED-DECIMAL | `DECIMAL(p+s,s)` | (p+s)/2+1 |
| `9(n)` COMP / COMP-4 / BINARY | `SMALLINT` (n≤4), `INT` (n≤9), `BIGINT` (n≤18) | 2, 4, 8 |
| `9(n)` COMP-5 | type that holds the full binary range of its storage | 2, 4, 8 |
| `9(p)V9(s)` COMP, COMP-5 with decimals | `DECIMAL(p+s,s)` | 2, 4, 8 |
| `9(p)P(n)` / `VP(n)9(p)` | `DECIMAL` with the scaled range | as above |
| COMP-1 / COMP-2 | `REAL` / `DOUBLE` | 4 / 8 |
| Edited pictures (`ZZ9.99-`, `99/99/99`) | `CHAR(display length)` with a warning | display length |

Rejected because the layout would not be computed correctly: SYNCHRONIZED,
USAGE POINTER, OCCURS DEPENDING ON followed by other items or inside another
OCCURS, and a redefining item longer than the item it redefines. Level 66
RENAMES is skipped with a warning.

Reading host data sets (EBCDIC, packed decimal) directly with this layout is
planned; until then, extract CSV on the host as for PL/I.

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
| `verifyCounts` | `Property<Boolean>` | yes | `true` | With `INSERT`, verify staging and inserted row counts before every commit |
| `stagingTablePrefix` | `Property<String>` | yes | `TMP_` | Staging-table prefix |
| `rootCursorStrategy` | `Property<String>` | yes | `DIALECT` | Cursor strategy selection |

The Gradle properties remain strings for concise build-script configuration,
but they are parsed at the command boundary into the typed load-plan enums.
`tableOperationMode` accepts `INSERT`, `INSERT_IGNORE`, `MERGE`, or `REPLACE`;
`rootCursorStrategy` accepts `DIALECT`, `HOLD`, or `REOPEN`. Generated YAML
stores those stable enum names and rejects unknown values when read.
The generated transaction policy is typed in the same way: `commitUnit` is
`ROOT_BATCH`, `stagingDeleteTiming` is `BEFORE_COMMIT`, and `restartUnit` is
`ROOT`. These values describe the loader's atomic restart contract and unknown
policy names are rejected rather than interpreted loosely.
Contract and load-plan field `action` values likewise use the shared
`ColumnAction` enum. Their YAML representation remains the stable names such as
`COPY`, `GENERATE`, and `DROP`; an unknown name is rejected during loading.
Contract collection properties such as `ancestorKeys` and field
`indexedSources` must be present even when empty. Explicit `null` values are
rejected at the contract boundary instead of failing later during planning or
provenance comparison.
Repeated-column extraction uses the typed `occurrenceSourceMode` value
`NUMBERED_COLUMNS`, which is serialized by that stable name and also rejects
unknown names during contract loading.
Occurrence lineage is validated as one unit: the mapping must contain exactly
one `GENERATE` column with the `OCCURRENCE_NUMBER` conversion, the occurrence
configuration must name that same staging and target column and declare a
positive maximum, and every numbered source-column index must be within that
maximum. The same rules apply when a generated contract is read, so incomplete
or contradictory occurrence metadata is rejected before extraction or loading.
The contract CSV `recordSeparator` is also typed and accepts only `CRLF` or
`LF`; the Gradle and command properties remain strings at their configuration
boundary and are converted before the contract is written.
Generated-key lineage records use the typed `generationType` values `IDENTITY`
and `SEQUENCE`; mappings retain these names in YAML and reject unknown values.
Generated-key lineage is validated as one unit: its column must be part of the
target primary key and have a matching `GENERATE` column mapping and target
data type; `SEQUENCE` requires a sequence name while `IDENTITY` must not declare
one.
Mapping diagnostics require a typed severity, actionable code/message/action,
and a valid table mapping reference when one is present. Diagnostic summary
counts are checked against the actual warning, skipped, and error collections.
Transformation history must be in strictly increasing sequence order. Its first
and last fingerprints must match the mapping source and target, and adjacent
steps with recorded boundary fingerprints must form a continuous chain.
Mapping headers require a migration ID, an offset-based `generatedAt`, generator
identity, and source/target schema file names and fingerprints. Optional source
definition-file entries require both path and type, so incomplete provenance is
rejected when the mapping is read rather than during a later generation step.
Hierarchical relationships are validated bidirectionally: every child
`parent` declaration must have exactly one matching hierarchical relationship,
and every hierarchical relationship must have the corresponding child parent
declaration. Parent reference pairs are validated with the relationship data.
The source and resolved parent-reference pairs must match the relationship's
source and target key pairs in the same order, preventing a structurally valid
but semantically different join from reaching contract generation.
When a contract records `mappingFile` and `mappingFingerprint`, validation also
checks every non-skipped data set and its source/target identity, hierarchy,
keys, occurrence settings, fields, and ancestor joins against that exact
mapping. CSV file names and staging table names remain operational overrides;
lineage semantics cannot be changed while retaining the mapping provenance.
Load-plan roots must not declare `targetForeignKey` or `parentJoinKeys`; those
properties are valid only for child data sets and malformed plans are rejected
before a database connection is opened.
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

Each load data set copies its extracted, non-generated fields from the staging
column to the target column; other staging columns, including the load-status
columns, are not copied. A staging column mapped to two target columns is
rejected.

The task logs the row counts of every data set: staging rows read, rows written
to the target, and rows the JDBC driver reported as inserted.

- With `tableOperationMode: INSERT` and `verifyCounts: true` (the default), the
  three counts must be equal before every commit. A mismatch, or a JDBC driver
  that does not report batch insert counts, stops the load and rolls back the
  uncommitted transaction; earlier committed root batches remain, and their
  staging roots are already completed, so a rerun continues after them. Set
  `verifyCounts` to `false` only for drivers without batch insert counts.
- With `INSERT_IGNORE`, rows whose target row already existed are reported as
  ignored with a warning instead of failing the load.
- `MERGE` and `REPLACE` report read and written rows only.
