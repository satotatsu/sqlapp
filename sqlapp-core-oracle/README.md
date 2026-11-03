# Oracle external table recreation

Oracle 10g and later table readers identify external tables and include them in
the existing batched `DBMS_METADATA.GET_DDL('TABLE', ...)` capture used for IOT and
composite tables. Use the normal full table/schema read, Schema XML export/import,
and CREATE SQL registry entry points. The saved complete table DDL preserves
`ORGANIZATION EXTERNAL`, all locations, driver access parameters and reject limits.
The existing `OracleTableDefinition` representation and stale-model checks apply;
the captured definition is authoritative and structured components are supplemental.

The external-table reader still uses one catalog query for the requested object
set. All returned location rows are retained in `ExternalTable.specifics`:

- `oracle.external.locationCount`: number of returned locations.
- `oracle.external.location.N.file`: file/location text, with zero-based N.
- `oracle.external.location.N.directory`: directory name, when available.
- `oracle.external.location.N.directoryOwner`: directory owner, when available.
- `oracle.external.location.N.partition`: partition name for partition-level locations.
- `oracle.external.location.N.subpartition`: subpartition name for subpartition-level locations.

On Oracle 12.2+, the same query unions ordinary, partition and subpartition location
views; each entry keeps its owning partition/subpartition identity. Exact server
version information selects the 12.2 query boundary. If the version is unavailable,
the 12c dialect retains its legacy query because it also represents 12.1; an 18c+
dialect selects the newer query. Older servers never reference the newer views.
Rows are sorted by partition/subpartition, directory owner, directory name and location for stable output;
duplicates are retained. Existing `location` and `directoryName` properties describe
the first row, preserving the single-location API and existing XML attributes.
These fields are inventory metadata; editing them does not rewrite the captured
table CREATE definition. No shared model API, resolver, dependency or configuration
format changed. Existing XML remains readable; newly read XML gains specifics.

If complete DDL cannot be obtained because of metadata permissions, catalog
metadata remains available, but CREATE of an identified external table fails
explicitly instead of creating an ordinary heap table. A legacy Table attached to
a Schema with matching external-table metadata is also rejected without complete
DDL. Standalone legacy Table XML without external identification cannot be inferred.
Pre-10g table queries and definition-capture behavior are unchanged.

Directory objects, access privileges and external data files must already exist on
the destination. This change does not copy files, grant access or execute external
drivers. Complete DDL remains the recreation representation. Partitioned and
subpartitioned external tables still need separate live qualification.

Validation uses Java 25 and the repository Gradle Wrapper:

```powershell
.\gradlew.bat :sqlapp-core-oracle:test --tests '*OracleExternalTableRecreationTest'
.\gradlew.bat :sqlapp-core-oracle:test
.\gradlew.bat :sqlapp-core-dialect-test:compileDockerTestJava
```

The new `OracleMetadataReaderTest.externalLocationsSurviveMetadataXmlAndRecreation`
Docker case is compiled separately and requires explicit database-test authorization
before execution. Partitioned and subpartitioned equivalents are also included.
Unit tests cover one-query multi-location metadata including
duplicates and quoted file names, Schema XML, batched definitions, stale definitions,
permission denial, missing definitions, legacy input and version boundaries.

Verification on 2026-11-03: Oracle module 541 tests and selected Command generator
142 tests passed with zero failures/errors/skips. Docker-test compilation passed;
the new database test was not executed and no database was accessed. No tracked
generated output or dependency versions were intentionally changed.

Partition-location follow-up: 543 Oracle module tests and 142 selected Command
generator tests passed. The ordinary/partition/subpartition Docker cases compiled;
none was executed. The new query and metadata mapping are unit-tested, not
qualified against a live 12.2+ catalog in this follow-up.

References: [external tables](https://docs.oracle.com/en/database/oracle/oracle-database/21/admin/managing-tables.html),
[DBMS_METADATA](https://docs.oracle.com/en/database/oracle/oracle-database/19/arpls/DBMS_METADATA.html).
Partition-location references: [ALL_XTERNAL_LOC_PARTITIONS](https://docs.oracle.com/en/database/oracle/oracle-database/12.2/refrn/ALL_XTERNAL_LOC_PARTITIONS.html),
[ALL_XTERNAL_LOC_SUBPARTITIONS](https://docs.oracle.com/en/database/oracle/oracle-database/19/refrn/ALL_XTERNAL_LOC_SUBPARTITIONS.html).
