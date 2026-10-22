# SAP HANA dialect

The HANA dialect uses the shared Schema model and SQL factory registry. The
2026-10-22 enhancement covers metadata correctness and SQL regeneration in
`sqlapp-core-saphana`; no module dependency or configuration format changes are
required.

## Versions

The existing resolver boundary is unchanged: HDB versions below major 4 use
`SapHana`, and major 4 and later use `SapHanaCloud`. Cloud JSON, REAL_VECTOR,
vector indexes and fuzzy-search behavior retain the existing Cloud-only path.
The SQL/catalog references used for this enhancement are HANA Platform 2.0 and
HANA Cloud. The older Platform resolver path is retained, but older engines
have not been exercised during this enhancement.

## Supported paths

| Area | Behavior |
| --- | --- |
| Identifiers | Reserved words and leading digits are quoted; embedded double quotes are escaped. |
| Numeric types | Native TINYINT maps to unsigned `UTINYINT`; a signed Schema `TINYINT` generates SMALLINT. Schema NUMERIC generates DECIMAL. Explicit DECIMAL precision is limited to 1–38 and scale to 0–precision; unconstrained DECIMAL is retained. |
| Identity columns | ALWAYS/BY DEFAULT, start, increment, bounds, cycle, cache and ordering are emitted from the shared column properties. Zero increments and negative cache sizes fail clearly. |
| Generated columns | `ALWAYS AS` catalog expressions populate `Column.formula`; CREATE emits `GENERATED ALWAYS AS (...)`. Identity/default conflicts fail clearly. |
| Column changes | ADD, DROP and ALTER use HANA parenthesized syntax. Nullability/default changes and standalone RENAME COLUMN are supported. |
| Table names | RENAME TABLE precedes subsequent column changes. Cross-schema plans require `decorateSchemaName=true` so every subsequent operation addresses the destination schema. |
| Routines and triggers | CREATE factories now replay complete executable catalog `definition` DDL. Supplemental arguments do not have to duplicate information already in that DDL. |
| Modeled SQLScript routines | Quoted IN/OUT/INOUT signatures and scalar/table function returns. A `BEGIN ... END` statement gets `LANGUAGE SQLSCRIPT AS`; explicit language/security/schema suffixes can be preserved in `statement`. Complete `definition` takes precedence. |
| Table parameters and returns | Parameter columns are joined in bulk and rendered as native TABLE types. Table function return columns use the existing `FunctionReturning` table model. |
| User-defined table types | TABLES/TABLE_COLUMNS are read in one joined query into `Type`/`TypeColumn`. Types are excluded from physical tables; CREATE TYPE uses the same model after XML round trips. |
| Foreign keys | Composite columns retain order and referenced owner identity. Equal constraint names on separate tables remain separate, using one catalog query. |
| Metadata filters | Trigger name and subject table filters are independent; unique constraints use the constraint-name filter. |
| Catalog compatibility | Foreign keys do not read nonexistent MATCH_OPTION; procedure arguments do not read nonexistent character-set/collation catalog fields. |
| Standard indexes | BTREE, CPBTREE and INVERTED types precede INDEX, and table schema decoration is honored. Unsupported index types fail clearly. Non-unique INVERTED HASH is rejected on Cloud. |
| Synonyms | CREATE SYNONYM uses the modeled source schema/object, with quoting and missing-target validation. |

## Common usage

```java
Dialect dialect = DialectResolver.getInstance().getDialect("HDB", 2, 0);
SqlFactoryRegistry registry = dialect.createSqlFactoryRegistry();

Table table = new Table("ITEMS");
table.getColumns().add("ID", c -> c.setDataType(DataType.BIGINT)
    .setIdentity(true).setIdentityGenerationType(IdentityGenerationType.ByDefault));
table.getColumns().add("PRICE", c -> c.setDataType(DataType.INT));
table.getColumns().add("QUANTITY", c -> c.setDataType(DataType.INT));
table.getColumns().add("TOTAL", c -> c.setDataType(DataType.INT)
    .setFormula("PRICE * QUANTITY"));
List<SqlOperation> create = registry.createSql(table, SqlType.CREATE);

Table target = table.clone();
target.getColumns().add("LABEL", c -> c.setDataType(DataType.NVARCHAR).setLength(100L));
List<SqlOperation> alter = registry.createSql(table.diff(target), SqlType.ALTER);
```

Advanced identity options use existing `Column.setIdentityStartValue`,
`setIdentityStep`, `setIdentityMinValue`, `setIdentityMaxValue`,
`setIdentityCycle`, `setIdentityCache`, `setIdentityCacheSize` and
`setIdentityOrder`. No HANA-specific wrapper is needed.

## Compatibility and limits

Public method signatures and Schema XML/YAML formats are unchanged. Native
TINYINT metadata now has an unsigned Schema type, so a fresh read can differ
from previously saved signed-type metadata. Identifier quoting and corrected
index syntax can also change generated text.

HANA CREATE INDEX does not support IF NOT EXISTS; index generation emits a
plain CREATE even when the shared create-if-not-exists option is enabled.
An existing index therefore produces a database error rather than being
silently accepted. Schema `IndexType.Hash` is not a HANA index spelling; use
`InvertedHash` where supported, or a supported BTREE/INVERTED type.

Removing a column default emits DEFAULT NULL. Changes to an existing identity
sequence/generation mode or generated expression require explicit DDL and
are rejected by the column ALTER path. Database-link synonym targets also
require an explicit complete `definition`. This enhancement does not add
PostgreSQL-specific extensions or claim that all PostgreSQL features have
HANA equivalents.

The initial unit tests use synthetic catalog rows, SQL rendering and resolver
boundaries. The Docker follow-up below additionally executes representative
DDL and catalog-definition round trips on HANA Express 2.0. HANA Cloud,
older Platform revisions and
features outside the exercised objects remain unverified. No production or
externally hosted database was accessed.

## PostgreSQL comparison and remaining scope

This inventory compares registered readers and factories. A shared factory is
an implementation path, not proof that every advanced vendor option works.

| Area | HANA implementation | Remaining scope |
| --- | --- | --- |
| Resolution, quoting, numeric types | Platform/Cloud resolver tests, SQL tests and Platform Docker round trips | Actual older Platform and Cloud engines |
| Tables, columns, keys, checks, indexes | Native readers and HANA CREATE/ALTER table/index paths | Exhaustive partition, storage, full-text and Cloud vector option combinations |
| Views, routines, triggers | Catalog DDL replay and modeled SQLScript signatures | Every language/security mode and advanced routine option |
| User-defined types | HANA table types use shared Type columns and native CREATE TYPE | PostgreSQL enum/range/domain types have no automatic HANA conversion |
| Sequences and synonyms | Native readers and CREATE factories, shared DROP paths | Sequence ALTER combinations and remote synonym target reconstruction |
| Users, roles and privileges | User/role/member, object and column privilege readers | Dedicated RoutinePrivilege reader/GRANT factory and comprehensive privilege round trips |
| PostgreSQL extensions and special objects | PostgreSQL-specific extension/domain/rule/operator paths have no direct mapping here | Requires a defined HANA feature and representation, not reuse of PostgreSQL SQL |
| Operational settings | Shared operations and HANA lock, temporary-table and sequence batching paths | PostgreSQL catalog settings/maintenance operations do not establish HANA equivalents |

The tested native ALTER COLUMN forms reject replacement identity settings and
generated expressions. The dialect rejects these changes instead of dropping
columns or losing data. A deliberate data migration is a separate task.

No public Schema API, XML format, configuration field or module dependency was
added for table types or table-valued routines. Saved full definitions continue
to take precedence over supplemental modeled metadata.

## Verification on 2026-10-22

Java 21 and the repository Gradle Wrapper were used. Final results were
47 HANA tests (20 added), 604 Command tests, and 83 Gradle Plugin tests,
all passing with no skips. Integration test sources compiled successfully;
integration/database tests were not run in this initial unit verification.
Earlier compile/test failures during
implementation were corrected before the final successful runs.

```powershell
.\gradlew.bat :sqlapp-core-saphana:test :sqlapp-core-dialect-test:compileTestJava :sqlapp-command:test :sqlapp-gradle-plugin:test --continue --console=plain
.\gradlew.bat :sqlapp-core-saphana:test :sqlapp-gradle-plugin:test --rerun --console=plain
```

The second command reran the Plugin test task, which had been up-to-date in
the first run. `git diff --check` passed for the changed HANA sources and
continuation guide. No generated artifacts were intentionally edited or
added to source control. The temporary-table unit fixture now uses a HANA
BTREE index rather than the unsupported generic HASH spelling.

## Docker follow-up on 2026-10-22

Docker Engine 29.5.3 on Rancher Desktop ran
`saplabs/hanaexpress:2.00.088.00.20251110.1` with ngdbc 2.29.7. The disposable
containers use test-only credentials and isolated test schemas. The existing
image startup includes its SAP license acceptance flag.

`SapHanaMetadataReaderTest` passed both tests. The added execution test verifies
native unsigned TINYINT, identity start/increment/cache, generated expressions
and computed values, ADD/ALTER/RENAME/DROP COLUMN, standard index creation,
synonym creation and querying, and reread column metadata. The representative
schema test now drops and recreates the read view, procedure, function and
trigger through the public SQL registry, then executes the recreated function.

`SapHanaBulkInsertTest` passed all seven tests, including 5,000 LOB rows across
20 chunks and a 2,000-row duplicate load, identity batching, local staging and
MERGE, checkpoint safety, lease fencing, parent/child migration and mismatch
repair with reverification.

`SapHanaJdbcTreeDataSessionTest` passed both sequence-key propagation and
clear rejection of generated identities lacking an associated sequence.

| Docker class | Tests | Failures / errors / skips |
| --- | ---: | --- |
| SapHanaMetadataReaderTest | 2 | 0 / 0 / 0 |
| SapHanaBulkInsertTest | 7 | 0 / 0 / 0 |
| SapHanaJdbcTreeDataSessionTest | 2 | 0 / 0 / 0 |
| Total | 11 | 0 / 0 / 0 |

The successful metadata run took 4m 51s, and the remaining nine tests took
9m 4s; most time was HANA container initialization. Containers created by
these runs were removed by Testcontainers. Pre-existing stopped HANA containers
were left untouched. No production implementation fix was required by these
successful Docker executions. The metadata Docker test and this guide were
updated; public APIs, configuration formats and dependencies were unchanged.

The first attempts failed before HANA SQL validation with a MySQL provider
class-loading error. A standalone probe using the same classpath succeeded.
Execution succeeded after project JARs/classes were copied to immutable
temporary runtime paths. Subsequent unrelated MySQL source edits also caused
a compile failure, so follow-up data tests use the already verified compiled
snapshot; no MySQL sources were changed by this work. This isolates HANA
validation from concurrent workspace builds and does not establish that the
entire changing workspace builds successfully.

The ordinary command for a stable checkout is:

```powershell
.\gradlew.bat :sqlapp-core-dialect-test:dockerTest --tests '*saphana.*' --console=plain
```

The isolated runs used temporary Gradle initialization scripts:

```powershell
.\gradlew.bat -I "$env:TEMP\sqlapp-hana-snapshot.init.gradle" :sqlapp-core-dialect-test:dockerTest --tests '*saphana.SapHanaMetadataReaderTest' --console=plain
.\gradlew.bat -I "$env:TEMP\sqlapp-hana-stable.init.gradle" :sqlapp-core-dialect-test:dockerTest --tests '*saphana.SapHanaBulkInsertTest' --tests '*saphana.SapHanaJdbcTreeDataSessionTest' --console=plain
```

## References

- [HANA reserved words](https://help.sap.com/docs/HANA_CLOUD_DATABASE_CN/e982e0f3783f4c099bb1d97cbe445673/28bcd6af3eb6437892719f7c27a8a285.html)
- [Platform TABLE_COLUMNS](https://help.sap.com/docs/SAP_HANA_PLATFORM/4fe29514fd584807ac9f2a04f6754767/2100d33a75191014868bbcd89274199c.html)
- [Platform REFERENTIAL_CONSTRAINTS](https://help.sap.com/docs/SAP_HANA_PLATFORM/4fe29514fd584807ac9f2a04f6754767/20ccc0a175191014901b88e6bc175c44.html)
- [Platform RENAME COLUMN](https://help.sap.com/docs/SAP_HANA_PLATFORM/4fe29514fd584807ac9f2a04f6754767/20fb2b8b7519101499d2c6bd37c81b32.html)
- [Platform RENAME TABLE](https://help.sap.com/docs/SAP_HANA_PLATFORM/4fe29514fd584807ac9f2a04f6754767/20fbadb2751910148b24d8d93aff01d4.html)
- [HANA CREATE SYNONYM](https://help.sap.com/docs/hana-cloud-database/sap-hana-cloud-sap-hana-database-sql-reference-guide/create-synonym-statement-data-definition)
- [Cloud non-unique INVERTED HASH restrictions](https://help.sap.com/docs/hana-cloud/sap-hana-cloud-migration-guide/non-unique-inverted-hash-index)
- [Platform FUNCTION_PARAMETERS](https://help.sap.com/docs/SAP_HANA_PLATFORM/4fe29514fd584807ac9f2a04f6754767/20a4c5c17519101481f89740396d2e7d.html)
- [Platform CREATE TYPE](https://help.sap.com/docs/SAP_HANA_PLATFORM/de2486ee947e43e684d39702027f8a94/c4338e0143c14e46bf751437769cc81b.html)
- [Platform ALTER TABLE](https://help.sap.com/docs/SAP_HANA_PLATFORM/4fe29514fd584807ac9f2a04f6754767/20d329a6751910149d5fdbc4800f92ff.html)
