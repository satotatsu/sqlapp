# Dialect data type regression checks

The MySQL, Oracle, SQL Server, SAP HANA, Db2, H2 and HSQLDB modules have
`*DataTypeMatrixTest` tests for native type resolution, exact type SQL and public
CREATE TABLE generation. They also check portable `DataType.DOUBLE` without a
source native type name and with source DOUBLE/DOUBLE PRECISION names retained.
H2 and HSQLDB execute the generated table DDL in local
in-memory databases. These checks do not connect to external databases.

Representative resolver versions are MySQL 5.7/8.0/8.4/9.0, Oracle 11.2/19/23,
SQL Server 2005/2008/2022, SAP HANA 1/2 and Cloud (major 4), Db2 LUW
10.5/11.5/12.1, H2 1.4/2.2 and HSQLDB 2.1/2.7. SQL Server DATE is explicitly
checked as unavailable before SQL Server 2008.

## Corrected mappings

| Dialect | Behavior |
| --- | --- |
| MySQL | Unspecified FLOAT precision uses 24; REAL resolves to DOUBLE under the default SQL mode. Explicit FLOAT precision remains available. |
| Oracle | FLOAT defaults to precision 126; XMLType is emitted without a length. |
| SQL Server | FLOAT defaults to precision 53, including the portable DOUBLE fallback; XML is emitted without a length from SQL Server 2005. |
| SAP HANA | TIME is emitted without a fractional precision; native SMALLINT remains SMALLINT. Portable signed TINYINT still emits SMALLINT. |
| Db2 | TIME has no precision modifier. Native XML and legacy XMLVARCHAR/XMLCLOB model spellings resolve to SQLXML and emit XML. |
| H2 | BIT and BOOL resolve to BOOLEAN, and BLOB retains its own matcher. |
| HSQLDB | No production changes were needed for the tested declarations. |

The corrections stay in the owning dialect modules. Public Java APIs,
configuration formats, metadata queries and resolver version boundaries are
unchanged. Existing incorrectly classified stored models are not rewritten;
re-read metadata where necessary. MySQL REAL_AS_FLOAT mode is not inferred by
these offline checks.

## Validation

Run the smallest scope with the repository wrapper and Java 25, for example:

```powershell
.\gradlew.bat :sqlapp-core-mysql:test --tests '*DataTypeMatrixTest'
```

Repeat for `oracle`, `sqlserver`, `saphana`, `db2`, `h2` and `hsql`, then run the
seven module test tasks without a test filter. When combining filtered tasks,
specify `--tests` separately after each task.

These tests supplement existing metadata, precision, vendor-specific and version
boundary tests. A passing offline matrix is not a claim that every database
version, SQL mode, extension type or JDBC conversion has been tested on a server.

Four existing H2 test classes also use statement branches instead of expressions
that merge different recursive Schema generic types. This avoids excessive
Java 25 compiler work while retaining the same test inputs and assertions.

### Review results (2026-11-01)

The seven module suites, MariaDB (MySQL's dependent dialect) and command tests
were run with Java 25 and the wrapper:

```powershell
.\gradlew.bat :sqlapp-core-mysql:test :sqlapp-core-oracle:test :sqlapp-core-sqlserver:test :sqlapp-core-saphana:test :sqlapp-core-db2:test :sqlapp-core-h2:test :sqlapp-core-hsql:test :sqlapp-core-mariadb:test :sqlapp-command:test --continue --max-workers=2 --console=plain
```

The Db2 suite was rerun after updating its old XMLVARCHAR/XMLCLOB expectations to
the corrected XML registration. Across these suites there were 2,840 passes and
five failures, with no skipped tests. The remaining failures also reproduce with
HEAD versions of the MySQL/H2 type registration compiled into separate output
directories; working source files were not reverted for this comparison:

- MySQL `MySqlAlterTableFactoryTest2`: `testGetDdlDropIndex1`,
  `testGetDdlDropIndex3`, `testGetAddConstraint` expect an additional DATETIME
  column MODIFY unrelated to the constraint/index change.
- H2 `H2CatalogCoverageTest.recreatesFiltersAndDropsQualifiedSynonyms`:
  both schema variants fail during the full suite, but pass in isolation.
  Dropped synonyms can still be queried in those full-suite runs.

These were the results before the follow-up fixes described below. The new H2
BOOL/BIT/BLOB regression cases fail with the old registration and pass with the
corrected registration. No external database tests were run, and no tracked
generated files were intentionally changed.

The final filtered run of all seven `*DataTypeMatrixTest` classes passed all
101 cases, including retained source DOUBLE/DOUBLE PRECISION names. Each native
case exercises the representative resolver versions listed above.

## Server qualification and suite fixes (2026-11-02)

The three MySQL index/constraint tests now expect only the requested index or
constraint change. Their unchanged nullable DATETIME column does not require a
MODIFY. The separate primary-key test still checks a real nullability change.

The H2 failures exposed a shared metadata capability cache defect. The same
catalog-reader class serves H2 1.x and 2.x, but its child readers differ by
dialect version. A cache keyed only by reader class could hide 2.x synonyms
after using 1.x, leaving no DROP factory. The cache now also includes the
dialect class and uses a concurrent map. H2 tests check both version orders,
supported types, generated DROP operations and actual synonym removal.
Public APIs, dependencies and serialized model formats are unchanged.

The follow-up range and server tests also corrected these mappings:

- Oracle portable TINYINT uses NUMBER(3,0), and INT uses NUMBER(10,0), so their
  signed minima and maxima fit. Native NUMBER declarations remain DECIMAL,
  including NUMBER(3,0), NUMBER(10,0) and NUMBER(19,0); their wider decimal
  domains must not imply narrower Java integer domains. Oracle 11g and later
  XMLType storage projections reported as virtual without an expression are
  read as ordinary XML columns. Real virtual columns still require a formula.
- SQL Server native TINYINT resolves to UTINYINT (0..255). Portable signed
  TINYINT emits SMALLINT. This applies to all resolver versions inheriting the
  SQL Server 2000 registrations, without changing version boundaries.
- Db2 LUW portable signed TINYINT emits SMALLINT; LUW has no scalar TINYINT.
  Native SMALLINT continues to resolve to SMALLINT. VECTOR INT8 remains separate.
- SAP ASE catalog names USMALLINT, UINT and UBIGINT resolve to the corresponding
  unsigned types and emit UNSIGNED SMALLINT/INT/BIGINT. This fixes recreation
  after a catalog read; the SQL declarations themselves were already recognized.

Existing stored models are not rewritten. Re-read metadata to obtain corrected
native classifications. Oracle integer CREATE/ALTER SQL now uses the wider
precision; SQL Server native TINYINT consumers can receive values above 127.

`DataTypeServerAssertions` adds opt-in generated-DDL acceptance tests to the
existing owned-container suites. Each declaration is resolved, created on the
server, read through the dialect column reader and recreated from that model.
Floating types additionally round-trip 1.25; signed integers exercise their
native minima and maxima, unsigned integer types exercise their maxima, and
character lengths are checked.
Portable DOUBLE is tested without a source name and with retained DOUBLE and
DOUBLE PRECISION names. These are targeted regression declarations, not an
exhaustive qualification of all vendor types and versions.

```powershell
.\gradlew.bat :sqlapp-core-dialect-test:dockerTest --tests '*verifiesDataTypeServerRoundTrips' --tests '*FirebirdSchemaRoundTripTest.dataTypeServerRoundTrips' --console=plain
```

The task uses disposable local database containers and their test credentials.
It creates and drops only test objects inside those containers, with container
reuse disabled by default. It does not connect to an existing external server.
H2, HSQLDB and SQLite retain their local database execution tests.

Informix's native SMALLINT/INTEGER/BIGINT minima are Java's corresponding signed
minimum plus one. Server tests use those documented native limits, rather than
claiming the full Java range is representable. See
[IBM's integer ranges](https://www.ibm.com/docs/en/informix-servers/14.10.0?topic=types-integer-data).
Oracle XMLType's logical column can be reported as virtual over its encoded
storage; the regression recreates the visible XML column, excluding internal
hidden columns. See [Oracle XMLType storage](https://www.oracle.com/technical-resources/articles/quinlan-xml.html).

### Follow-up unit results

Java 25 and the wrapper completed 5,864 tests with no failures or skips across
core (1,258), MySQL (306), H2 (305), MariaDB (62), PostgreSQL (383), Oracle (534),
SQL Server (178), Db2 (387), HANA (127), HSQLDB (180), Firebird (282), Informix
(107), SQLite (827), ASE (108), EDB (19), and command (801).

The broad verification and follow-up commands were:

```powershell
.\gradlew.bat :sqlapp-core:test :sqlapp-core-mysql:test :sqlapp-core-h2:test :sqlapp-core-mariadb:test :sqlapp-core-postgres:test :sqlapp-core-oracle:test :sqlapp-core-sqlserver:test :sqlapp-core-db2:test :sqlapp-core-saphana:test :sqlapp-core-hsql:test :sqlapp-core-firebird:test :sqlapp-core-informix:test :sqlapp-core-sqlite:test :sqlapp-core-sybase:test :sqlapp-command:test --continue --max-workers=2 --console=plain
.\gradlew.bat :sqlapp-core-hsql:test --rerun-tasks --console=plain
.\gradlew.bat :sqlapp-core-oracle:test :sqlapp-core-sqlserver:test :sqlapp-core-db2:test :sqlapp-core-sybase:test :sqlapp-core-edb-oracle:test :sqlapp-command:test --continue --max-workers=2 --console=plain
.\gradlew.bat :sqlapp-core-oracle:test :sqlapp-core-edb-oracle:test --continue --console=plain
.\gradlew.bat :sqlapp-command:test --console=plain
```

Focused cache, factory, type matrix and Oracle metadata tests ran before the
module suites. Initial executions exposed the four new integer-output assertions
and the real-server errors described above; these were fixed and rerun.
Concurrent builds also replaced project JARs during running tests, causing
ClassNotFound/NoClassDefFound failures in HSQLDB and server runs. The affected
checks were rerun with temporary copies of project runtime artifacts. That
verification-only setup adds no repository build settings or dependencies.
No tracked generated files were intentionally changed.

### Follow-up server results

The final combined result is 14 engine cases and 213 native/portable column
declarations passing, with no remaining failures or skips. The tested engines
were:

| Product | Actual server version(s) |
| --- | --- |
| PostgreSQL | 18.4 |
| MySQL | 5.7.44, 8.0.46, 8.4.11 |
| Oracle | 26ai, product version 23.26.2.0.0 |
| SQL Server | 2025, product version 17.00.4065 |
| SAP HANA | 2.00.088.00.1760424921 |
| Db2 LUW | 12.1.5 (SQL120150) |
| Firebird | 2.5.9, 3.0.10, 4.0.2, 5.0.0 |
| Informix | 14.10.FC9W1DE |
| SAP ASE | JDBC product version 16.3 |

The aggregate run finished with 12 passing engine cases and two test-fixture
failures: Oracle's internal XML storage column was not excluded, and the
PostgreSQL integer-array case incorrectly attempted a scalar boundary insert.
The fixture now explicitly excludes hidden columns and limits scalar integer
inserts to scalar columns. The corrected Oracle and PostgreSQL cases both
passed in a separate targeted run:

```powershell
.\gradlew.bat :sqlapp-core-dialect-test:dockerTest --tests '*OracleMetadataReaderTest.verifiesDataTypeServerRoundTrips' --tests '*PostgresMetadataReaderTest.verifiesDataTypeServerRoundTrips' --console=plain
```

The totals above combine the 12 passing aggregate cases with these two passing
reruns; that aggregate invocation itself was not a green build. XML result
files were not edited. The isolated rerun used a separate results directory
(`dockerTest-oracle-postgres`) and a temporary runtime snapshot, both configured
only for this verification. Testcontainers stopped the owned database containers.
No existing external/production databases were contacted. Other vendor versions,
SQL modes, character sets, page sizes and unlisted types remain unqualified by
these server tests; ordinary broader metadata/bulk integration suites were not
run as part of this targeted data-type check.

## PostgreSQL-derived dialect follow-up (2026-11-02)

Aurora PostgreSQL, AlloyDB, YugabyteDB YSQL, CockroachDB and EDB inherit the
corrected PostgreSQL DOUBLE PRECISION registration. None overrides DOUBLE or
FLOAT back to the invalid DOUBLE declaration. EDB adds Oracle-style input
aliases through `EdbOracleTypes`; it does not inherit the Oracle Database
integer registrations discussed above.

Dedicated `*DataTypeMatrixTest` classes now exercise all 16 representative
resolver selections: Aurora 14/15/16/17, AlloyDB 15/16/17, YSQL 11/15,
CockroachDB 24.3/25.4 and EDB 14/15/16/17/18. Their 85 JUnit cases check native
resolution, exact SQL and public CREATE TABLE generation for DOUBLE/FLOAT
aliases and precision boundaries, REAL, NUMERIC, character/temporal types,
UUID, BYTEA and JSONB, including one-dimensional arrays. Portable DOUBLE also
retains correct output with no source name, DOUBLE and DOUBLE PRECISION.

The complete five module suites passed 153 tests, with one skipped optional
external YSQL test because no existing-server connection was configured:

```powershell
.\gradlew.bat :sqlapp-core-aurora-postgres:test :sqlapp-core-alloydb:test :sqlapp-core-yugabyte:test :sqlapp-core-cockroach:test :sqlapp-core-edb-oracle:test --continue --max-workers=2 --console=plain
```

The new server cases run with the requested dialect explicitly supplied for
upstream controls; they do not accidentally test the ordinary PostgreSQL
dialect instead of Aurora or EDB. They also verify catalog array dimensions
and request the created column explicitly, retaining CockroachDB's implicit
hidden rowid in ordinary metadata reading. The initial Cockroach test fixture
expected one column without that filter; the corrected fixture was rerun.
No production implementation, public API, configuration format, dependency or
resolver boundary changed in this follow-up. Changes are tests and documentation.

```powershell
.\gradlew.bat :sqlapp-core-dialect-test:dockerTest --tests '*verifiesPostgresFamilyDataTypes' --tests '*PostgresMetadataReaderTest.verifiesDataTypeServerRoundTrips' --console=plain
```

This command passed all six engine cases (106 column declarations), without
failures or skips. The five derived-dialect cases each check 18 declarations;
the PostgreSQL 18.4 control checks 16 declarations and verifies that the original
connection-resolving test-helper path still works.

| Dialect | Server evidence in this follow-up | Remaining real-product limit |
| --- | --- | --- |
| Aurora PostgreSQL | Aurora 17 dialect on upstream PostgreSQL 17.9 | No AWS Aurora service connection |
| AlloyDB | Actual AlloyDB Omni 16.8 | Managed Google Cloud AlloyDB not tested |
| YugabyteDB YSQL | Actual YugabyteDB 2026.1.2, YSQL 15.12 | YSQL 11 has unit coverage in this follow-up |
| CockroachDB | Actual CockroachDB 25.4.0 | 24.3 has unit coverage in this follow-up |
| EDB Postgres Advanced Server | EDB 17 dialect on upstream PostgreSQL 17.9 | No EPAS product connection or Oracle-compatibility execution |

All checks used owned disposable containers, with runtime artifacts temporarily
copied to avoid concurrent-build interference. No managed-service credentials,
existing external databases, production data, build settings or dependencies
were changed. Other derived-product versions and vendor-only types remain
outside the real-engine evidence recorded here. The initial focused test also
corrected a test-only Aurora simple-name expectation (`aurora-postgresql`).

## Vendor references

- [MySQL numeric types](https://dev.mysql.com/doc/refman/8.4/en/numeric-types.html)
- [Oracle data types](https://docs.oracle.com/en/database/oracle/oracle-database/19/sqlrf/Data-Types.html)
- [SQL Server data types](https://learn.microsoft.com/en-us/sql/t-sql/data-types/data-types-transact-sql)
- [SAP HANA data types](https://help.sap.com/docs/hana-cloud-database/sap-hana-cloud-sap-hana-database-sql-reference-guide/data-types)
- [Db2 XML](https://www.ibm.com/docs/en/db2/11.5.x?topic=overview-xml-data-type)
- [H2 data types](https://h2database.com/html/datatypes.html)
- [HSQLDB data types](https://hsqldb.org/doc/2.0/guide/sqlgeneral-chapt.html)
