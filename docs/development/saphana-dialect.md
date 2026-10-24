# SAP HANA dialect

The HANA dialect uses the shared Schema model and SQL factory registry. The
2026-10-22 through 2026-10-24 enhancements cover metadata correctness and SQL regeneration in
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
| Routine privileges | Direct function/procedure EXECUTE grants use `RoutinePrivilege`, including grant option and native GRANT/REVOKE. The same grants are excluded from ObjectPrivilege to avoid duplicate replay. |
| Partition CREATE | Single-level HASH/RANGE/ROUNDROBIN; uniform and heterogeneous HASH-RANGE/RANGE-RANGE/RANGE-HASH; uniform HASH-HASH/ROUNDROBIN-RANGE use the existing Partitioning model. YEAR/MONTH/HOUR single-column date expressions are supported. Three-level tables retain executable complete DDL rather than a flattened tree; RANGE-RANGE-HASH and RANGE-RANGE-RANGE replay are live-tested. Parent-specific ranges and RANGE-HASH bucket counts are retained. Unsupported modeled combinations fail explicitly. |
| NSE storage | Table and column PAGE/COLUMN load units are retained from optional catalog fields. RANGE nodes in single-level RANGE, RANGE-RANGE, RANGE-HASH and HASH-RANGE retain partition load units through heterogeneous syntax; HASH buckets inherit the parent unit. Independent HASH load-unit overrides require explicit DDL. |
| RANGE policies and placement | Leaf OTHERS ranges retain dynamic THRESHOLD/INTERVAL policies and PAGE LOADABLE aging distances. RANGE partitions preserve INSERT ON/OFF, PERSISTENT MEMORY ON/OFF, GROUP NAME/TYPE/SUBTYPE and validated NUMA node preferences. Single-level HASH NUMA preferences use CREATE followed by ordinal ALTER PARTITION operations. Unsupported storage, direction, movability and multilevel HASH placement overrides fail explicitly. See the continuation verification below for the tested scope. |
| Sequence ALTER | Explicit settings support restart, increment, bounds, cycle, cache and RESET BY. Schema diffs do not restart an unchanged start value. |
| Role/object grants | Role membership supports ADMIN OPTION; native object/schema privileges support GRANT OPTION. Names are quoted and invalid privilege tokens fail. |
| Users and system privileges | Native CREATE/ALTER/DROP USER, restricted-user CREATE, local role CREATE/DROP, user/role system-privilege readers and GRANT/REVOKE with ADMIN OPTION. Credentials must be explicitly supplied. Invalid grants are retained as metadata but cannot be silently reactivated by GRANT replay. |
| Column privilege boundary | Reader retained; unsupported column GRANT/REVOKE fails explicitly and never expands to a table grant. Native PostgreSQL-style candidate forms fail on Express; a projection view with an ordinary object grant is live-tested as the column-limited access path. |
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

Modeled routines and table types use the same entry point:

```java
Function function = new Function("ITEM_ROWS");
function.getReturning().toTable().getTable().getColumns()
    .add("ID", c -> c.setDataType(DataType.INT));
function.setStatement("BEGIN RETURN SELECT 1 AS ID FROM DUMMY; END");
List<SqlOperation> routineSql = registry.createSql(function, SqlType.CREATE);

Type type = new Type("ITEM_TYPE");
type.getColumns().add(new TypeColumn("ID").setDataType(DataType.INT));
List<SqlOperation> typeSql = registry.createSql(type, SqlType.CREATE);
```

For cross-schema export/recreation, serialize the containing `Schema` so parent
identity is retained, and enable `registry.getOptions().setDecorateSchemaName(true)`.
Direct routine EXECUTE grants read by the catalog reader are represented as
`RoutinePrivilege`; pass them to `registry.createSql(grant, SqlType.GRANT)` or
`SqlType.REVOKE`. Replays execute under the current database session's authority.

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
sequence/generation mode or generated expression require an explicit migration and
are rejected by the column ALTER path. Remote synonyms use `dbLinkName` plus
target schema/object for the native three-part name; HANA 2+ metadata distinguishes
the current database from a remote database. This enhancement does not add
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
| Tables, columns, keys, checks, indexes | Native readers, modeled single/two-level partitions, three-level complete-DDL replay including indexes, expression/NSE/aging options, single-level HASH NUMA recreation, HANA CREATE/ALTER table/index paths | Three-level trees are opaque complete definitions, not editable modeled partition trees. Arbitrary modeled expressions, mixed modeled subpartition types and independent HASH load-unit overrides require explicit DDL. Live Cloud vector/fuzzy options are not verified |
| Views, routines, triggers | Catalog DDL replay, modeled SQLScript signatures and explicit security/default-schema clauses | Non-SQLScript engines and optional external language runtimes |
| User-defined types | HANA table types use shared Type columns and native CREATE TYPE | PostgreSQL enum/range/domain types have no automatic HANA conversion |
| Sequences and synonyms | CREATE/ALTER sequence, RESET BY restart fixture, native synonym namespaces and shared DROP paths | Actual remote database access and live Cloud/older-engine verification |
| Users, roles and privileges | Password-user lifecycle, restricted-user creation, local role lifecycle, user/role system privileges, object/schema/member/routine grants, explicit invalid/column-grant boundaries | External authentication providers, externally mapped roles and vendor authorization policies not represented by the shared model require explicit DDL. Column access uses an explicit projection view rather than silently broadening a grant |
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

## Continuation verification on 2026-10-23

The table-valued routine, table type and routine EXECUTE privilege follow-up is
implemented in `sqlapp-core-saphana`, with real-database coverage in
`sqlapp-core-dialect-test`. No shared Schema API or configuration change was
required. Direct routine EXECUTE grants now appear in RoutinePrivilege rather
than being duplicated in ObjectPrivilege; other routine privileges retain the
existing ObjectPrivilege representation.

Java 21.0.11 and the Gradle Wrapper verified:

| Scope | Tests | Result |
| --- | ---: | --- |
| HANA unit tests | 56 | Passed, no failures/errors/skips |
| HANA metadata Docker tests | 6 | Passed, no failures/errors/skips |
| Command unit tests | 604 | Executed and passed, no failures/errors/skips |
| Gradle Plugin unit tests | 83 | UP-TO-DATE; previous passing result retained |

The six Docker tests use the same HANA Express 2.00.088 image described above.
They verify table type metadata is separate from physical tables, Schema XML
round trips and recreated type usage, modeled scalar/table functions and table
procedure IN/OUT arguments with actual returned values, and EXECUTE privilege
XML replay through GRANT/REVOKE (including grant option) with an isolated role.
Existing representative metadata/DDL checks and native identity/generated-column
ALTER rejection tests also pass. The final Docker run took 5m 2s. No running
containers remained after Testcontainers cleanup.

The first table-return query incorrectly assumed OUT; actual HANA metadata uses
RETURN for the anonymous table return. The final query handles RETURN/OUT and
excludes the anonymous table return from ordinary arguments. An intermediate
type test exported only a parented Type, losing its containing schema identity;
the final test serializes the containing Schema. Earlier fixture compile errors
were corrected before the successful final runs.

```powershell
.\gradlew.bat :sqlapp-core-saphana:test :sqlapp-core-dialect-test:compileDockerTestJava --console=plain
.\gradlew.bat -I "$env:TEMP\sqlapp-hana-snapshot.init.gradle" :sqlapp-core-saphana:test :sqlapp-core-dialect-test:dockerTest --tests '*saphana.SapHanaMetadataReaderTest' --console=plain
.\gradlew.bat :sqlapp-command:test :sqlapp-gradle-plugin:test --console=plain
```

`git diff --check` passed for the HANA implementation, tests and this guide.
Build outputs and temporary runtime snapshots were generated by verification,
not intentionally edited or added to source control. The prior bulk/tree Docker
results were not rerun in this follow-up. Cloud, older Platform revisions and
advanced option combinations in the comparison table remain unverified. Only
the disposable local database was accessed.

## Advanced option verification with Java 25 (2026-10-23)

The follow-up replaces the vague "advanced combinations" limitation with this
concrete executed matrix. It changes only `sqlapp-core-saphana`, its Docker
test and this guide; public APIs, Schema XML formats, dependencies and repository
Java toolchain settings are unchanged.

| Area | Executed Platform Docker coverage |
| --- | --- |
| Storage | ROW and COLUMN tables; table-level NSE PAGE LOADABLE; read, Schema XML round trip, recreate and insert |
| Single-level partitioning | HASH and ROUNDROBIN with two partitions; integer and DATE RANGE boundaries with OTHERS |
| Uniform two-level partitioning | HASH-HASH, HASH-RANGE, RANGE-RANGE and ROUNDROBIN-RANGE; metadata, Schema XML, recreation and inserts across ranges |
| Fulltext | FAST PREPROCESS ON with FUZZY SEARCH INDEX ON and SYNC, actual CONTAINS results; FAST PREPROCESS OFF with LANGUAGE COLUMN and ASYNC FLUSH EVERY/AFTER; read/recreate and native DROP |
| Sequence ALTER | Increment/cache changes without unintended restart; explicit RESTART WITH, bounded CYCLE and NO CACHE |
| SQLScript clauses | DEFINER/INVOKER, DEFAULT SCHEMA and READS SQL DATA; creation and invocation under the test SYSTEM account |
| Authorization | Table SELECT/UPDATE and schema SELECT, GRANT OPTION, role inheritance with ADMIN OPTION, revoke/regrant and effective rights for the role |

Table NSE uses `table.getSpecifics().put("LOAD_UNIT", "PAGE")`. Fulltext options
use `SYNC_MODE` (`SYNC`/`ASYNC`) and optional positive `FLUSH_EVERY_MINUTES` /
`FLUSH_AFTER_DOCUMENTS`. A sequence passed directly to ALTER treats a supplied
start value as an explicit restart; use `original.diff(target)` for ordinary
changes so an unchanged start is not replayed. Schema object grants use
`OBJECT_TYPE=SCHEMA` in specifics and the schema name; a missing object name is
resolved from that schema identity.

The final results are:

| Scope | Tests | Result |
| --- | ---: | --- |
| HANA unit tests | 69 | Executed, passed; no failures/errors/skips |
| HANA metadata Docker tests | 12 | Executed, passed; no failures/errors/skips |
| Command tests | 604 | Executed, passed; no failures/errors/skips |
| Gradle Plugin tests | 83 | Executed on Java 25 earlier in this follow-up, passed; final recheck UP-TO-DATE |

All these runs used Corretto 25.0.3, with a temporary Gradle initialization
script selecting the Java 25 compiler and test launcher and `options.release=21`.
The generated HANA class major version was verified as 65 (Java 21). The final
Docker matrix took 5m 19s on the existing HANA Express 2.00.088 image. Docker
reported no remaining running containers after cleanup. Only disposable local
schemas/roles and embedded test databases were accessed; no remote database was
used. Prior bulk/tree Docker tests were not rerun in this follow-up.

Fixes found by execution include the fulltext shadow/source-column confusion,
DROP FULLTEXT INDEX syntax, synchronization sentinels (0/-1), empty OTHERS
boundaries, and schema privileges with a null OBJECT_NAME. Fulltext source
columns now come from an INDEX_COLUMNS join; there are no per-index queries.
Explicit FAST PREPROCESS ON plus LANGUAGE COLUMN and non-positive async flush
thresholds fail clearly. If LANGUAGE COLUMN is supplied without FAST PREPROCESS,
the generator safely selects OFF.

Cloud-specific unit tests additionally verify that the reader uses
M_FUZZY_SEARCH_INDEXES joined to INDEXES instead of the removed FULLTEXT_INDEXES
view, retains mode/MIME/token options, and folds repeated host/partition rows in
one query. The ordinary Cloud index query excludes fuzzy/vector objects handled
by their dedicated readers. This is catalog/SQL validation, not a live Cloud run.
HANA 2+ synonym metadata retains remote database namespaces while omitting the
current database prefix; the older Platform query remains separate. Remote
three-part names and quoting are unit-tested, without connecting to a remote
source.

The first attempt could not use the missing local Java 21 executable. Per the
user's instruction, verification switched to Java 25 without changing local
settings. An intermediate runtime snapshot also raced with another build
replacing a class file, before any DB test ran. Final builds use separate
temporary output directories and fixed runtime snapshots. Earlier SQL/fixture
failures described above were corrected before the successful final runs.

The temporary profiles used these commands:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Amazon Corretto\jdk25.0.3_9'
.\gradlew.bat --no-daemon "-Dorg.gradle.java.home=$env:JAVA_HOME" -I "$env:TEMP\sqlapp-hana-java25.init.gradle" -I "$env:TEMP\sqlapp-hana-isolated.init.gradle" -I "$env:TEMP\sqlapp-hana-snapshot.init.gradle" :sqlapp-core-saphana:test :sqlapp-core-dialect-test:dockerTest --tests '*saphana.SapHanaMetadataReaderTest' --console=plain
.\gradlew.bat --no-daemon "-Dorg.gradle.java.home=$env:JAVA_HOME" -Dsqlapp.hana.buildRoot=sqlapp-hana-dependent25 -I "$env:TEMP\sqlapp-hana-java25.init.gradle" -I "$env:TEMP\sqlapp-hana-isolated.init.gradle" :sqlapp-command:test :sqlapp-gradle-plugin:test --console=plain
.\gradlew.bat --no-daemon "-Dorg.gradle.java.home=$env:JAVA_HOME" -I "$env:TEMP\sqlapp-hana-java25.init.gradle" -I "$env:TEMP\sqlapp-hana-isolated.init.gradle" :sqlapp-core-saphana:test --console=plain
```

The Java profile selects toolchain 25 and release 21 for each Java project.
The output profile assigns each project's build directory to a task-specific
temporary tree. The snapshot profile copies the resulting project artifacts
before launching the Docker tests. No generated output was intentionally edited
or added to source control. `git diff --check` passed for the HANA changes.

At that verification point, remaining boundaries included heterogeneous/three-level or expression
partition regeneration and mixed per-column/per-partition NSE load-unit overrides,
RESET BY behavior across a database restart, live Cloud/remote access, and older
Platform engine revisions. The subsequent NSE/RANGE continuation below extends
the column/partition coverage. SQLScript caller privilege isolation
and non-SQLScript external runtimes were not exercised. CREATE support does not
imply automatic repartitioning or storage conversion: those ALTER diffs now
fail with an actionable migration-DDL error instead of returning an empty plan.

## NSE and heterogeneous RANGE continuation (Java 25)

Column metadata now retains the optional `TABLE_COLUMNS.LOAD_UNIT` field using
the existing bulk column query. No extra SELECT is added. Catalogs without this
field remain readable. CREATE preserves explicit PAGE/COLUMN column overrides;
DEFAULT/TABLE retain inheritance. Invalid units and row-store PAGE/COLUMN
requests fail explicitly. Column load-unit ALTER diffs require explicit
migration DDL rather than returning a plan that loses the change.

Heterogeneous RANGE syntax now recreates single-level partition load units and
RANGE-RANGE layouts with different child ranges or child counts for each parent.
Each leaf range retains its PAGE/COLUMN override, and parents without children
remain leaf partitions. The existing Schema Partition/SubPartition and Specifics
representations are reused; public APIs, XML formats and dependencies are unchanged.
Parent partitions with children now receive distinct catalog-derived logical names too.
Previously they were unnamed, so XML deserialization could resolve different
parents to the same object and replace their bounds. A bulk-reader regression
test includes the heterogeneous root row, checks two SELECTs and preservation
of distinct parent/child ranges through XML. The root (logical partition zero)
is excluded from the model; parent-node rows update parent bounds and load units
even when leaf rows have already been read. Parent nodes need no physical PART_ID.
Mixed NSE overrides on HASH/ROUNDROBIN-based schemes remain unsupported and fail
with an explicit-DDL message when they differ from the table's inherited unit.
Three-level, expression, mixed subpartition types and heterogeneous HASH layouts
remain outside this generator's supported matrix.

The Docker coverage includes a PAGE table with a COLUMN column override and
heterogeneous RANGE/RANGE-RANGE tables with PAGE/COLUMN partition overrides.
It checks metadata, Schema XML serialization, DROP/CREATE replay, inserts and
re-read bounds/load units on SAP HANA Express 2.00.088. No external or production
database is involved. Java 25 is selected through temporary Gradle init scripts;
Java compilation still targets release 21. No generated files or local settings
are intentionally changed in the repository.

Focused `SapHanaAdvancedOptionsTest`, `SapHanaMetadataRegressionTest` and
`SapHanaPartitionRoutingTest` runs passed. The final
`:sqlapp-core-saphana:test` run executed 74 tests without failures. The first
Docker rerun passed all 12 existing tests including the new column override;
subsequent 13-test runs exposed the parent/root-node and unnamed-parent XML issues
described above, which were then fixed. `:sqlapp-command:test` and
`:sqlapp-gradle-plugin:test` completed successfully using their existing
UP-TO-DATE results (604 and 83 tests from the preceding Java 25 verification).
The final `:sqlapp-core-dialect-test:dockerTest --tests
'*saphana.SapHanaMetadataReaderTest'` rerun passed all 13 tests with no
failures/errors/skips in 5m 14s after those fixes. Docker reported no running
containers after automatic cleanup. The final scoped `git diff --check` passed.

## RANGE-HASH continuation (Java 25)

RANGE-HASH CREATE now uses the heterogeneous partition grammar. Each parent's
SubPartition collection determines its HASH bucket count, allowing different
counts beneath different ranges. When some parents have buckets, an empty
collection denotes a leaf range. For a count-only model with no bucket entries
anywhere, an explicit positive `subPartitionSize` applies to every parent.
HASH subpartitioning accepts multiple column names and resolves each against
the table model. Missing columns, non-positive counts, range bounds on HASH
buckets, and bucket load units that conflict with their parent's inherited unit
fail explicitly. Parent PAGE/COLUMN load units remain available; a HASH bucket
cannot independently override them.

The implementation stays in the HANA CREATE generator and reuses the existing
Schema model, public APIs and XML format. No metadata SELECTs, dependencies or
version-resolution changes are introduced. The Docker test creates ranges with
two buckets, three buckets and no buckets, using a composite HASH key. It checks
metadata, Schema XML replay, inserts into all three ranges, and re-read bucket
counts and parent PAGE load units on local HANA Express 2.00.088.

The focused `SapHanaAdvancedOptionsTest` run and Docker test compilation passed.
The full `:sqlapp-core-saphana:test` run passed 76 tests on Corretto 25.0.3,
compiling with `release=21`. `:sqlapp-command:test` and
`:sqlapp-gradle-plugin:test` completed successfully with UP-TO-DATE results;
their prior Java 25 executions covered 604 and 83 tests respectively. Build
outputs and runtime classpaths were isolated in temporary directories, with
the repository Gradle Wrapper and the same temporary init scripts described
above. No external/production database or local settings were changed.

The first RANGE-HASH Docker attempt did not execute the test methods: HANA
initialization timed out after the host drive ran short of space. A subsequent
TEMP cleanup also removed an in-progress Gradle result file and the temporary
init scripts. After space was freed, the Java 25 init/snapshot setup was recreated
and a fresh isolated build root was used. All 73 build tasks executed successfully
in 1m 32s, including another successful run of the 76 HANA unit tests and Docker
test compilation. The failed startup is an environment failure, not a verified
RANGE-HASH SQL result.
After the TEMP recovery, Command's 604 tests and Gradle Plugin's 83 tests were
executed again on Java 25 and passed without failures/errors. The dependent
module verification completed in 2m 34s; these recovered results were executed,
not reused from the earlier deleted build root.
The recovered `:sqlapp-core-dialect-test:dockerTest --tests
'*saphana.SapHanaMetadataReaderTest'` run passed all 14 tests with no
failures/errors/skips in 5m 39s, including RANGE-HASH replay. The new generator's
class major version was verified as 65 (Java 21 bytecode). No running Docker
containers remained after the test process exited; no generated output or local
settings were intentionally changed in the repository. Scoped `git diff --check`
passed.

Three-level and expression partitions, heterogeneous HASH-first schemes, and
mixed subpartition types remain outside the supported generator matrix. Cloud
and older Platform engine revisions still require their own live verification.

## Heterogeneous HASH-first continuation (Java 25)

HASH-RANGE CREATE now retains each parent bucket's own RANGE boundaries and
PAGE/COLUMN range load units. Uniform HASH-HASH remains supported; different
HASH-HASH child counts fail explicitly. Composite HASH keys are supported.
The ordered parent collection identifies HASH buckets by their
logical ordinal; catalog partition IDs/names are not emitted as identifiers.

Uniform HASH-first models keep the preceding generator path and SQL syntax.
The heterogeneous HASH-RANGE path is selected for differing ranges or load units.
Invalid HASH parent range bounds, empty HASH-RANGE definitions and independent
HASH parent/bucket load-unit overrides fail explicitly. Count-only uniform
models remain supported. The change stays inside the HANA generator, with no
public API, XML format, dependencies, metadata SELECTs or DialectResolver changes.

The Docker coverage creates a heterogeneous HASH-RANGE table on
HANA Express 2.00.088: different child ranges (100/200),
composite HASH keys and a PAGE RANGE override. It checks metadata,
Schema XML serialization, DROP/CREATE replay, inserts and re-read ranges/counts/
load units. Existing uniform partition tests also exercise the preserved path.
The attempted unbalanced two-level HASH-HASH syntax was rejected by this engine
with SQL error 257 near the second HASH clause. The test records that engine
boundary explicitly; no successful HASH-HASH unbalanced replay is claimed.
Only disposable local schemas are accessed; older Platform and Cloud engines
remain unverified. Three-level, expression and mixed subpartition-type schemes
and unbalanced HASH-HASH remain outside this generator's supported matrix.

Focused `SapHanaAdvancedOptionsTest` and Docker test compilation passed; the
full HANA unit run executed 77 tests without failures/errors. All runs used
Java 25 with release-21 compilation and isolated temporary outputs/runtime
snapshots. An early Docker startup was deliberately stopped to include the
uniform-syntax compatibility refinement in the final verification; it provides
no SQL test result.
The direct-dependent check completed successfully in 1m 36s: Command's 604
tests executed again without failures/errors; Gradle Plugin reused the previous
successful 83-test result (UP-TO-DATE). Scoped `git diff --check` passed.
The final `:sqlapp-core-dialect-test:dockerTest --tests
'*saphana.SapHanaMetadataReaderTest'` run passed all 15 tests without
failures/errors/skips in 4m 59s, including heterogeneous HASH-RANGE replay and
the unbalanced HASH-HASH syntax rejection boundary. The preceding 15-test run
had one failure when that rejected HASH-HASH CREATE was initially treated as a
positive fixture; it was corrected to a negative engine-boundary assertion and
the generator continues to reject that unsupported model. Class major version
65 confirms Java 21 bytecode. Docker reported no running containers after
cleanup. No generated output was intentionally edited in the repository, and
no external/production database was accessed.

## Date partition expressions continuation (Java 25)

The HANA generator accepts a plain column name or a documented single-column
`YEAR`, `MONTH` or `HOUR` partition expression. For example, the existing
partitioning-column collection can contain `YEAR("EventDate")`. The same
resolver is used for HASH, RANGE and supported subpartition paths, including
heterogeneous schemes. It resolves the referenced column, emits only the
allowlisted function token and quotes the real column identifier using the
dialect builder. It never executes an arbitrary supplied expression.

The argument must resolve to a date/timestamp column; TIME-only and non-date
columns fail explicitly. Unknown functions, nested/compound expressions and
missing columns require explicit DDL. YEAR/MONTH boundaries use numeric values;
HOUR boundaries use quoted hour-granularity strings. Heterogeneous catalog
boundaries returned as full timestamps are converted to the corresponding
year/month/hour value only when aligned to the start of that period; unaligned
values fail explicitly. Existing plain-column type-aware boundary quoting
remains unchanged.

The bulk reader still uses the existing expression fields and SELECTs. Quoted
identifiers containing parentheses retain their quotes so literal identifiers
are not silently mistaken for executable expressions. This engine rejects
function arguments whose column names contain whitespace (error 2048), and
interprets a literal column name such as `"YEAR(D)"` as a function (error 260).
These cases fail explicitly in the generator. Quoted mixed-case, reserved-word
and embedded-quote argument names were checked successfully in direct local SQL
probes. Other plain-column handling is unchanged. Public APIs, XML schema and dependencies
are unchanged; the existing reference-name representation is reused.

The Docker case covers YEAR/MONTH/HOUR RANGE, YEAR HASH, heterogeneous HASH-RANGE
and RANGE-RANGE expressions, and an embedded-quote date column across
metadata, Schema XML, DROP/CREATE replay, inserts and re-read partition models.
Only local HANA Express 2.00.088 is targeted; Cloud and older engines require
their own live verification. Three-level and arbitrary expressions remain out
of scope for this generator.

Recreation also exposed a TIMESTAMP issue: catalog LENGTH 27 describes display
width and must not become `TIMESTAMP(27)`. HANA TIMESTAMP now uses its fixed DDL
format, and table-column metadata omits that display width. Unsupported positive
precision requests fail explicitly; intrinsic precision 7 and legacy catalog
width 27 generate plain TIMESTAMP. The type-format fix also applies to routine
and table-type generation through the dialect type mapping.

Two initial Docker runs failed in the new case: first on the spaced function
argument, then on the function-like literal column. Direct SQL/XML/replay probes
in one temporarily retained local container then confirmed all seven supported
table variants, two inserts per table, row counts and re-read partition models.
They also exposed the TIMESTAMP display-width and heterogeneous-boundary issues
fixed above. The diagnostic container was explicitly removed before the final
disposable Docker run.

The focused expression/parser tests and Docker test compilation passed. The
final `:sqlapp-core-saphana:test` run executed 84 tests without failures/errors on
Java 25 with release-21 compilation. The direct-dependent check succeeded in
58s: Command executed 604 tests without failures/errors; Gradle Plugin reused
its preceding successful 83-test result (UP-TO-DATE). Outputs and Docker runtime
artifacts remain isolated in temporary directories. Scoped `git diff --check`
passed.
The final disposable `:sqlapp-core-dialect-test:dockerTest --tests
'*saphana.SapHanaMetadataReaderTest'` run passed all 16 tests with no
failures/errors/skips in 5m 56s. The seven expression tables passed XML replay,
inserts and full re-read partition-model comparison. New class bytecode major
version 65 confirms release-21 compilation. No running containers remained
after the diagnostic container removal and final automatic cleanup; no external
or production database was accessed, and no generated repository output was
intentionally edited.

## Third-level metadata guard (Java 25)

At this checkpoint the HANA reader rejected declared third-level partition types, expressions,
positive counts or leaf IDs before mapping catalog rows into the two-level
Schema model. This prevents silently flattening Cloud three-level trees. The
error identifies the table and asks callers to retain its original CREATE TABLE
DDL. Missing optional columns on older catalogs, empty declarations and zero
counts/IDs remain accepted. The existing set-based two-query reader is retained;
no public API or Schema XML format changed.

This guard covers catalogs exposing the third-level fields documented for
[HANA Cloud three-level partitioning](https://help.sap.com/docs/hana-cloud-database/sap-hana-cloud-sap-hana-database-administration-guide/partitioning-over-three-levels-17633d723f6c44ac9f00c06bd03cbdc7).
It does not add three-level modeling or SQL generation. At this checkpoint, dynamic range policies,
partition INSERT controls, persistent-memory placement and extended storage
still need separate generation/replay verification; advanced-option coverage is
not complete.

Verification used Java 25 and release-21 compilation with the repository
wrapper and temporary output directories. The focused routing class passed,
then `:sqlapp-core-saphana:test` passed all 85 tests. The disposable HANA Express
2.00.088 Docker run passed all 16 metadata tests (no failures/errors/skips;
combined Gradle invocation 5m 32s). This confirms existing two-level behavior on
Platform; the third-level Cloud rejection is covered by catalog-row unit tests,
not a live Cloud database. `:sqlapp-command:test` executed 604 passing tests;
`:sqlapp-gradle-plugin:test` reused its successful 83-test result (UP-TO-DATE).
No running containers remained. No TEMP-deletion errors were observed in these
runs, and no external or production database was accessed.

## Combined advanced-partition verification (Java 25)

HANA RANGE generation now shares one option renderer for balanced and
heterogeneous layouts. Settings use the existing partition Specifics and
Statistics maps; the public API and Schema XML format are unchanged. Ordinary
tables require no new settings. Advanced options are:

| Existing model key | Meaning and validation |
| --- | --- |
| Specifics `INSERT` | TRUE/FALSE or ON/OFF. Explicit child ON overrides parent OFF; required RANGE layouts use heterogeneous syntax. Default ON is omitted in balanced syntax. |
| Specifics `IS_DYNAMIC` | TRUE enables a policy on a leaf OTHERS range with a NOT NULL key. FALSE leaves the policy inactive even when catalog statistics contain a threshold. |
| Statistics `DYNAMIC_RANGE_THRESHOLD` | Optional positive 64-bit row threshold, used only for an active policy on a plain column. The database default applies when omitted. |
| Specifics `DYNAMIC_RANGE_INTERVAL` | Positive integer interval on an integer key, or an interval with YEAR/MONTH/HOUR matching the key's date function. Requires IS_DYNAMIC=TRUE and cannot be combined with THRESHOLD. Other expression/type combinations require explicit DDL. |
| Specifics `PERSISTENT_MEMORY` | TRUE/FALSE or ON/OFF preference on RANGE nodes. This preserves the requested preference; it does not verify availability or use of persistent-memory hardware. |
| Specifics `GROUP_NAME`, `GROUP_TYPE`, `SUB_TYPE` | Quoted placement-group values on RANGE nodes. Embedded single quotes are escaped. `SUB_TYPE` reads the catalog's actual `SUBTYPE` column. |

The partition query now selects partition-specific columns before same-named
table-wide columns, while keeping the table's schema/name first for LEFT JOIN
identity. This prevents a global threshold from masking different thresholds
on separate HASH-RANGE branches. Thresholds are read as 64-bit values rather
than truncated to 32 bits. INSERT ON is retained in metadata because removing
it would lose an explicit override of an INSERT OFF parent. The reader still
uses two set-based queries; no per-partition SELECTs were added.

Non-default EXTENDED storage, NUMA preferences, dynamic-aging distance/load
units, non-increasing dynamic direction and non-default movability require
explicit DDL and fail before generation. Optional newer catalog fields are
retained when present without naming them in the SQL SELECT list, preserving
queries on older catalogs. These guards do not implement Cloud placement or
three-level partitioning. HASH bucket options that cannot be represented by
the supported RANGE grammar also fail explicitly.

Native verification targets the local disposable HANA Express
2.00.088.00.20251110.1 image. The first new Docker case exposed error 7 when
replaying INSERT options with balanced syntax; automatic heterogeneous routing
fixes that case. Direct probes on the same temporary diagnostic container then
confirmed nine source tables through metadata, XML, generated CREATE execution
and full partition-model equality: distinct THRESHOLD policies, integer and
YEAR/MONTH/HOUR INTERVAL policies, parent/child INSERT overrides, both persistent
memory preferences and quoted grouping values. Separate probes confirmed
`ADD PARTITION FROM OTHERS` creates a third range for integer and YEAR intervals
without losing the inserted row. The diagnostic container was explicitly
removed before the final full Docker run.

The documented syntax and catalog interpretation follow the
[Platform TABLE_PARTITIONS reference](https://help.sap.com/docs/PRODUCT_ID/4fe29514fd584807ac9f2a04f6754767/c81d9beea383495c99ad48789b983708.html)
and [heterogeneous CREATE partition clauses](https://help.sap.com/docs/hana-cloud-database/sap-hana-cloud-sap-hana-database-sql-reference-guide/heterogeneous-create-partition-clauses?locale=zh-TW&state=PRODUCTION&version=2026_1_QRC).
Older HANA engines and HANA Cloud are not live-tested. Automatic background
THRESHOLD splitting and persistent-memory hardware behavior are not verified.

Final verification used Java 25 with release-21 compilation and temporary build
outputs: the focused routing/options tests passed, `:sqlapp-core-saphana:test`
passed all 90 tests, and the complete HANA `:sqlapp-core-dialect-test:dockerTest
--tests '*saphana.SapHanaMetadataReaderTest'` invocation passed all 17 cases
(no failures/errors/skips; combined Gradle invocation 5m 18s). No running
containers remained after automatic cleanup, and no TEMP-deletion errors were
observed.
The new case checks the nine-table XML/recreation model comparison, allowed and
rejected INSERTs, and integer/YEAR `ADD PARTITION FROM OTHERS` growth with data
retained. `:sqlapp-command:test` executed 604 passing tests;
`:sqlapp-gradle-plugin:test` reused its successful 83-test result (UP-TO-DATE).
The new options class has bytecode major version 65. Scoped `git diff --check`
passed. No external or production database was accessed, and no generated
repository output was intentionally edited.

## Dynamic aging and NUMA continuation (Java 25)

The existing partition options renderer now supports dynamic aging and RANGE
NUMA preferences in addition to the preceding policy/placement options. The
implementation stays in `sqlapp-core-saphana`; the Docker regression fixture
is in `sqlapp-core-dialect-test`. No public API, Schema XML, configuration format
or module dependency changed.

| Existing Specifics key | Supported behavior |
| --- | --- |
| `DYNAMIC_DISTANCE_INTERVAL` | Positive integer distance for an integer key, or a positive distance with YEAR/MONTH/HOUR matching the date expression. Requires an active dynamic leaf OTHERS policy. Emits `DISTANCE ... PAGE LOADABLE`. |
| `DYNAMIC_DISTANCE_LOAD_UNIT` | PAGE is the supported aging destination. Omitting this value with a distance safely infers PAGE from the fixed HANA grammar; specifying a unit without a distance or specifying another unit fails. |
| `NUMA_NODE_INDEXES` | Validated nonnegative 32-bit node indexes and ascending ranges, such as `0, 1 TO 3, 6`. Catalog compact form `0TO1,3` is accepted. RANGE generation quotes each numeric token individually, for example `NUMA NODE ('0' TO '1', '3')`. |

Dynamic aging validates the nearest explicit load-unit setting: an effective
COLUMN LOADABLE preference would prevent aging and fails clearly. PAGE on a
nearer partition overrides a more distant table preference. Different
HASH-RANGE branches retain their own distances, using heterogeneous syntax.
The interval and distance parsers share type/unit validation and reject zero,
negative, overflowing and malformed values instead of inserting raw SQL.

Balanced RANGE can combine persistent-memory and NUMA preferences without
being unnecessarily converted to heterogeneous syntax. Heterogeneous RANGE
NUMA plus INSERT OFF is also verified on HANA Express 2.00.088. The older
balanced syntax remains available; actual older engines are not live-tested.
HASH bucket NUMA placement, EXTENDED storage, non-increasing dynamic direction
and non-default movability remain explicit-DDL boundaries of this generator.

For older catalogs without IS_DYNAMIC, the reader now infers an active policy
only for an OTHERS range with a positive per-partition threshold or nonempty
interval. It preserves explicit FALSE, leaves bounded ranges unchanged and
does not infer a policy when both policy fields are absent. These compatibility
branches are tested with synthetic metadata. The reader still uses two bulk
queries and does not add per-object SELECTs.

Native probes exposed and corrected NUMA syntax details: unquoted numeric
tokens fail with error 257, a quoted entire range/list is not equivalent to
individually quoted numeric tokens, and metadata uses compact `TO` ranges.
Eight tables then passed catalog/XML/generated-DDL/re-read partition-model
comparison: integer interval and threshold aging, YEAR aging, basic NUMA,
persistent-memory plus NUMA, NUMA ranges/lists, heterogeneous NUMA plus INSERT,
and HASH-RANGE branches with different aging distances. A separate manual
`ADD PARTITION FROM OTHERS` plus `REFRESH DYNAMIC DISTANCE` probe changed the old
partition to PAGE while retaining both inserted rows. The final Docker case
also checks this operation after generated-DDL replay.

On the pinned Express image, the tested NOT MOVABLE, BIDIRECTIONAL dynamic
direction and per-HASH-parent NUMA CREATE forms fail with error 257. This is a
boundary observation for these forms/version, not a statement that all HANA
versions or ALTER forms reject those features. Cloud is not live-tested. NUMA
metadata/recreation tests preserve requested preferences; they do not verify
physical allocation or performance. Manual aging is verified, but the timed
background aging job is not exercised.

Final Java 25/release-21 verification passed: the focused routing/options
classes executed 14 tests; `:sqlapp-core-saphana:test` executed 94 tests; and
`:sqlapp-core-dialect-test:dockerTest --tests
'*saphana.SapHanaMetadataReaderTest'` executed all 18 cases with no
failures/errors/skips (combined Gradle invocation 5m 15s).
`:sqlapp-command:test` executed 604 passing tests;
`:sqlapp-gradle-plugin:test` reused its successful 83-test result (UP-TO-DATE).
The new Docker case passed all eight table-model replays and manual aging
after replay. Temporary outputs/runtime snapshots were used; no generated
repository output was intentionally edited. No TEMP-deletion errors were
observed, and no external or production database was accessed.

Scoped `git diff --check` passed. No running Docker containers remained after
the final cleanup check.

References: [Dynamic aging](https://help.sap.com/docs/HANA_CLOUD_DATABASE_CN/18561c097a0741adaa19a8e1df6cb0dc/97e7c96f8a9d43ed90fc448c0d0b7f09.html),
[Platform partition properties](https://help.sap.com/docs/SAP_HANA_PLATFORM/6b94445c94ae495c83a19646e7c3fd56/0d5ada62bce443fd97853a47ed38ac4f.html?locale=en),
[SPS 08 NUMA extensions](https://help.sap.com/docs/SAP_HANA_PLATFORM/42668af650f84f9384a3337bcd373692/c191ad6ad83b4877a6237688a7b27c10.html?locale=en-).

## Consolidated remaining-scope work (2026-10-24, Java 25)

The implementation work covers table recreation, principal lifecycle and
authorization through the existing registry/model entry points. It does not
equate PostgreSQL-only objects with unrelated HANA features, nor claim live
verification of engines that are unavailable locally.

| Remaining item | Result |
| --- | --- |
| Three-level partitions | Reader stores complete executable table DDL in `Table.definition`, with no misleading two-level partition tree. RANGE-RANGE-HASH and RANGE-RANGE-RANGE including primary keys/secondary indexes are exercised through metadata, XML and recreation. |
| HASH NUMA placement | Single-level HASH bucket preferences generate CREATE followed by ordinal ALTER PARTITION statements. A nonempty bucket collection defines the count in the shared model, and its order defines the newly created bucket ordinals. RANGE bounds on HASH buckets are rejected. Multilevel HASH placement still requires explicit DDL. |
| User lifecycle | CREATE/ALTER/DROP for password users, restricted-user CREATE, password changes, lifetime checks, activation/deactivation and explicit first-password-change policy. Local role CREATE/DROP is registered too. |
| System and schema privileges | Separate bulk readers and GRANT/REVOKE for user/role system privileges, including ADMIN OPTION. System grants are excluded from ordinary object privileges. Schema creation grants, including CREATE ANY, retain native scope and GRANT OPTION. |
| Column grants | Supported read path retained, but unimplemented/native-rejected column GRANT/REVOKE fails explicitly. No automatic widening to a table grant. A projection-view grant is exercised with a user session that can read the projected column and cannot read the underlying hidden column. |
| RESET BY restart | Disposable-container restart fixture verifies sequence NEXTVAL changes from 1 before restart to 100 after the reset-query table maximum is changed to 99. |

### Complete table definitions and the metadata-query exception

`TABLES.PARTITION_SPEC` contains an internal `CompactPartitionSpec2` JSON tree
for these complex tables. It is not executable partition SQL. HANA's
`GET_OBJECT_DEFINITION(schema, object)` returns complete executable DDL but
accepts one object; there is no equivalent bulk executable-DDL source used by
this dialect. Consequently, only tables with third-level catalog fields need
one extra procedure call per table. The result is cached on that table while
processing its partition rows. A query-count regression verifies that eight
partition rows require only one definition call: three calls in total rather
than one call per partition. Ordinary table/partition metadata still uses the
existing two set-based queries. This is a documented per-object exception,
not an additional query for every table, column or index.

The native definition includes secondary-index DDL after the table CREATE.
The existing HANA SQL splitter separates executable operations while retaining
quoted semicolons. Supplemental modeled indexes/constraints are not emitted
again on the definition path. Modeled columns remain available for inspection,
but the complete definition is authoritative for recreation. Table ALTER diffs
on definition-backed tables require explicit migration DDL. Source schema/name
provenance is retained in Specifics; changing the destination identity without
explicitly updating the DDL and provenance fails rather than silently creating
the original table elsewhere. This supports faithful replay, not interactive
editing of a three-level shared partition tree. No Schema/XML API extension or
new dependency was required.

### Principal lifecycle and authorization semantics

The same `registry.createSql(object, SqlType.CREATE/ALTER/DROP/GRANT/REVOKE)`
entry point handles these objects. CREATE USER requires a supplied password;
USERS metadata does not supply reusable credentials and the reader does not
attempt to copy one. For example:

```java
User user = new User("APP_USER").setPassword(System.getenv("APP_USER_PASSWORD"));
List<SqlOperation> operations = registry.createSql(user, SqlType.CREATE);
```

Optional user Specifics are `IS_RESTRICTED`, `IS_PASSWORD_ENABLED`,
`PASSWORD_CHANGE_NEEDED`, `PASSWORD_LIFETIME_CHECK_ENABLED` and
`USER_DEACTIVATED`. The lifetime key maps the actual catalog column
`IS_PASSWORD_LIFETIME_CHECK_ENABLED`. Missing settings retain database defaults.
External authentication, generic admin/expiry/lock fields, externally mapped
roles and unsupported identity changes require explicit DDL. Restricted-user
creation does not enable client connections automatically. DROP USER does not
silently add CASCADE.

Use a User diff for changes to read metadata. Unchanged passwords are omitted;
unchanged lifecycle settings are not replayed unnecessarily. Enabling password
lifetime checks can affect the password-change requirement, so a requested
unchanged FALSE requirement needs a supplied new password and a final
NO FORCE_FIRST_PASSWORD_CHANGE operation. Ambiguous option removal fails with
an explicit TRUE/FALSE instruction. Native tests exercise login, deactivation,
reactivation and password replacement, not just accepted SQL text.

System privilege metadata retains IS_VALID and administrative delegation.
Invalid object, routine and system grants cannot be silently reactivated by a
GRANT replay; explicit REVOKE remains possible. UserPrivilege/RolePrivilege use
ADMIN OPTION, while ordinary object/schema grants use GRANT OPTION. Schema-only
creation privileges are not accepted as table grants. An actual schema CREATE
ANY grant is read and revoked in the lifecycle fixture.

On the pinned HANA Express image, PostgreSQL-style `GRANT SELECT(A)`,
`GRANT UPDATE(A)` and `GRANT SELECT ON T(A)` fail with error 257. The dialect's
column factory therefore returns an actionable error rather than empty SQL or
a broader grant. This is a tested boundary for the exercised HANA forms, not
a claim about every future HANA authorization API.

### Verification boundary

Native verification uses the local disposable HANA Express
2.00.088.00.20251110.1 image. Java 25 executes release-21 bytecode; no Java or
dependency version in repository configuration was changed. Restart tests run
last and sequentially. They refresh Docker's inspected host-port mapping after
restart, because an ephemeral host port can change. An initial diagnostic
retry against the old port was corrected this way before the successful
restart probe and full fixture run.

HANA Cloud and older HANA engines are still not live-tested: no connected tool
or HANA environment configuration was found, and a test target/version plus
authentication-configuration reference is needed. No external or production
database has been accessed. Resolver/optional-catalog compatibility remains
covered by unit tests; it does not substitute for those live environments.

The final scope/schema-privilege check passed 105 HANA unit tests and all 22
HANA Docker cases, with no failures/errors/skips. The combined Gradle invocation
completed in 5m 40s, including the actual container restart. Earlier in this
continuation Command executed 604 passing tests; the final dependency check
reused that result and the preceding successful 83-test Gradle Plugin result
(both UP-TO-DATE, 10s). Intermediate compile checks, the bucket-count test
expectation and the diagnostic stale-port retry were corrected before these
final successful runs. Schema CREATE ANY was added after a preceding successful
104-unit/22-Docker check, then verified by this final full rerun.

The final commands, using the session-only Java 25/release-21 and temporary
runtime-snapshot init script, were:

```powershell
.\gradlew.bat --no-daemon "-Dorg.gradle.java.home=$env:JAVA_HOME" -I "$env:TEMP\sqlapp-hana-recovery25.init.gradle" :sqlapp-core-saphana:test :sqlapp-core-dialect-test:compileDockerTestJava --rerun :sqlapp-core-dialect-test:dockerTest --tests '*saphana.SapHanaMetadataReaderTest' --console=plain
.\gradlew.bat --no-daemon "-Dorg.gradle.java.home=$env:JAVA_HOME" -I "$env:TEMP\sqlapp-hana-recovery25.init.gradle" :sqlapp-command:test :sqlapp-gradle-plugin:test --console=plain
```

The affected modules are `sqlapp-core-saphana`, the HANA Docker fixture in
`sqlapp-core-dialect-test`, and this development document. Existing public
method signatures and XML formats are retained. Complete table definitions
now take precedence in CREATE; unsupported/invalid grants and unsafe user
configuration produce explicit errors. No dependency version or persistent
local setting changed. No generated repository output was intentionally
edited, no TEMP-deletion errors were observed, and no running Docker containers
remained after cleanup. Scoped `git diff --check` passed.

References: [HANA GRANT syntax and schema privileges](https://help.sap.com/docs/hana-cloud-database/sap-hana-cloud-sap-hana-database-sql-reference-guide/grant-statement-access-control),
[USERS catalog fields](https://help.sap.com/docs/SAP_HANA_PLATFORM/4fe29514fd584807ac9f2a04f6754767/21026099751910148e0cdbddc75652b8.html),
[three-level partitioning](https://help.sap.com/docs/hana-cloud-database/sap-hana-cloud-sap-hana-database-administration-guide/partitioning-over-three-levels-17633d723f6c44ac9f00c06bd03cbdc7).

## Complete-definition integrity follow-up (2026-10-24)

CREATE now rejects a definition-backed table whose complete definition was
removed, including after XML round-trip. It cannot silently fall back to an
unpartitioned modeled CREATE. Whitespace-only and comment-only definitions also
fail explicitly instead of returning an empty operation list. Existing public
APIs and XML formats are unchanged. The focused `SapHanaDefinitionTableTest`
passed all four tests with Java 25 and release 21.

The subsequent wrapper run with the same Java 25/release 21 initialization,
`:sqlapp-core-saphana:test :sqlapp-command:test :sqlapp-gradle-plugin:test`,
succeeded in 1m 20s: HANA 107 and Command 604 tests passed; Plugin reused its
83-test successful result (`UP-TO-DATE`). Docker was not rerun for this
validation-only guard; the preceding 22-test native run remains the latest
live result. No external database was accessed.

## ALTER and user-option boundary follow-up (2026-10-24)

The complete-definition marker also guards ALTER when the definition text is
missing. Changing such a table, or converting a modeled table to a marked
definition-backed target, requires explicit migration DDL. The marker cannot
silently bypass the existing complete-definition ALTER boundary.

Explicit blank values for user boolean options are invalid rather than absent:
`IS_RESTRICTED`, `IS_PASSWORD_ENABLED`, `PASSWORD_CHANGE_NEEDED`,
`PASSWORD_LIFETIME_CHECK_ENABLED`, and `USER_DEACTIVATED` require TRUE/FALSE or
ON/OFF when supplied. Omitting an option retains the existing default behavior.
The two focused regression classes passed with Java 25/release 21.
The subsequent `:sqlapp-core-saphana:test` module run succeeded in 14s with
108 tests and no failures/errors. Scoped `git diff --check` passed. Command,
Plugin and Docker were not rerun for these additional validation boundaries;
their preceding verification results remain separate. No external database,
dependency version, public API or XML format changed.

## System-privilege bulk-read regression (2026-10-24)

`SapHanaSystemPrivilegeMetadataTest` exercises user and role system-privilege
readers with 100 grants each. Each reader must execute exactly one catalog
query regardless of row count, keep its USER/ROLE discriminator, and retain
the grantee, grantor, privilege, admin option, and invalid-grant state. Both
focused tests passed with Java 25/release 21. These are mocked reader tests;
they supplement the preceding Express integration run and do not establish
live compatibility with an older HANA engine or HANA Cloud.
The subsequent wrapper `:sqlapp-core-saphana:test` run passed all 110 tests
with no failures/errors in 1m 2s. Scoped whitespace checks passed. This
follow-up changes tests and documentation only; production APIs, XML formats,
dependencies and database contents are unchanged. Docker, Command and Plugin
were not rerun for this test-only addition.

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
- [HANA GRANT](https://help.sap.com/docs/hana-cloud-database/sap-hana-cloud-sap-hana-database-sql-reference-guide/grant-statement-access-control)
- [Platform partition clauses](https://help.sap.com/docs/SAP_HANA_PLATFORM/4fe29514fd584807ac9f2a04f6754767/ca6a99bbfcd94be9ab8020a86f43caae.html)
- [HANA timestamp precision](https://help.sap.com/doc/a2e5971d49834708b52c0fbbebd6c85e/2.0.03/en-US/Remote_Data_Sync_Administration_Guide_en.pdf)
- [Platform NSE load units](https://help.sap.com/docs/SAP_HANA_PLATFORM/6b94445c94ae495c83a19646e7c3fd56/6599685a709c4dab85e5b33e56ec977d.html)
- [Platform NSE partition syntax](https://help.sap.com/docs/SAP_HANA_PLATFORM/6b94445c94ae495c83a19646e7c3fd56/322bb45b63fd4d3c90ca4baf5e7558df.html)
- [RANGE-HASH syntax and HASH bucket semantics](https://help.sap.com/docs/hana-cloud-database/sap-hana-cloud-sap-hana-database-administration-guide/truncating-partitions?locale=en-US)
- [Fulltext synchronization mode encodings](https://help.sap.com/doc/PRODUCTION/95545fb91e744a87a43fae1ca08a61cf/2.0.00/en-US/SAP_HANA_Search_Developer_Guide_en.pdf)
- [Platform DROP FULLTEXT INDEX](https://help.sap.com/docs/PRODUCT_ID/4fe29514fd584807ac9f2a04f6754767/20d66989751910148a64a797565ad4dc.html)
- [Cloud removed Platform features](https://help.sap.com/docs/hana-cloud-database/sap-hana-cloud-sap-hana-database-sql-reference-guide/migration-related-impacts-to-sql-and-sqlscript-due-to-differences-in-feature-support)
- [Cloud fuzzy-index monitoring](https://help.sap.com/docs/hana-cloud-database/sap-hana-cloud-sap-hana-database-search-developer-guide/monitoring-views?locale=en-US)
- [HANA ALTER SEQUENCE](https://help.sap.com/docs/hana-cloud-database/sap-hana-cloud-sap-hana-database-sql-reference-guide/alter-sequence-statement-data-definition)
- [Platform CURRENT_DATABASE](https://help.sap.com/docs/SAP_HANA_PLATFORM/4fe29514fd584807ac9f2a04f6754767/7ddcb499036a483ab18ecb19816e6708.html)
