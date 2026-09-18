# Compatibility and database verification matrix

This page separates three facts that are easy to confuse:

1. A dialect artifact exists for a database family.
2. The resolver contains version-specific implementations for that family.
3. A particular server version has real-engine integration-test coverage.

A version-specific class shows intentional compatibility behavior in the code;
it does not prove that every feature was executed against every server release
in that range. Conversely, a real-engine test covers the scenarios in that
test suite, not the database product's complete feature set.

This matrix was audited from the repository source and test configuration on
2026-09-19. The test images listed below were not rerun as part of this
documentation change.

## Java and Gradle

| Component | Current baseline | Compatibility statement |
|---|---|---|
| Published Java artifacts | Java 21 | Sources are compiled with a Java 21 toolchain. Consumers should use a Java 21 or later runtime compatible with the published bytecode. |
| Repository build | Gradle Wrapper 9.6.1 | This is the checked-in, supported build path for release verification. |
| Gradle plugin | Java 21; tested by this repository with Gradle 9.6.1 | A lower or upper Gradle compatibility range has not yet been established. Do not infer one from Gradle API compilation alone. |
| Companion example | Java 21; Gradle Wrapper 9.6.1 | The `develop` branch of `sqlapp-gradle-example` uses the same current baseline. |

Before publishing, the release build should run with the checked-in wrapper.
If compatibility with older Gradle releases is promised, add a TestKit matrix
and record the oldest and newest verified versions here.

## Coverage labels

| Label | Meaning |
|---|---|
| Real-engine | A repository integration suite is configured for, or the continuation record documents execution against, the named server or emulator version. |
| Module | Dialect unit/module tests and SQL or resolver tests exist, but this table does not identify a current real-server run. |
| Environment-dependent | Verification needs proprietary software, a cloud service, or another environment not supplied by the routine test suite. |

“Configured” is deliberately weaker than “passed for this release.” The
release checklist must record which integration suites were actually run.

## Database dialect matrix

| Database family | Artifact | Version-aware implementation in source | Current real-engine evidence | Current confidence boundary |
|---|---|---|---|---|
| IBM Db2 | `sqlapp-core-db2` | 9.5, 9.7, 9.8, 10.1, 10.5, 11.1, 11.5, 12.1.0, 12.1.2, 12.1.5 | Db2 Community 11.5.8.0 and 12.1.5.0 test images | Older resolver branches are retained but are not all represented by current container runs. |
| Apache Derby | `sqlapp-core-derby` | Generic Derby resolver | Module tests with Derby 10.17.1.0 dependency | No current container matrix or published minimum server version. |
| Firebird | `sqlapp-core-firebird` | 2.0, 2.5, 3.0, 5.0 | Firebird 3.0 and 5.0 test images | Firebird 2.x compatibility is code-level/module coverage in the current suite. |
| H2 | `sqlapp-core-h2` | Pre-2.x and 2.x split | Module-level H2 tests | The 1.x reader is preserved; current real-engine version evidence is not recorded here. |
| HiRDB | `sqlapp-core-hirdb` | Generic HiRDB resolver | Environment-dependent | Requires a licensed target environment and driver for release verification. |
| HSQLDB | `sqlapp-core-hsql` | 2.0.0, 2.1.0, 2.2.0, 2.3.0, 2.3.4, 2.4.0 | Module/in-process tests; JDBC dependency 2.7.4 | Version branches older than the current driver remain compatibility code. |
| Informix | `sqlapp-core-informix` | Generic Informix resolver | Informix Developer Database 14.10.FC9W1DE image | Older server-version boundaries are not expressed as separate dialect classes. |
| Microsoft Access / MDB | `sqlapp-core-mdb` | Generic Access/MDB resolver through UCanAccess | Access 2010 database creation and metadata tests are documented | Japanese index collation in the supplied sample is read-only with the current Jackcess path. |
| MariaDB | `sqlapp-core-mariadb` | 10.0, 10.0.5, 10.2.0, 10.2.5, 10.2.7, 10.3.0, 10.5.0, 11.4.0, 11.5.0, 11.8.0, 12.1.0 | MariaDB 10.5, 11.4, and 11.8 test images | MariaDB inherits compatible MySQL behavior and overrides version-specific differences. |
| MySQL | `sqlapp-core-mysql` | 5.6.4, 5.6.5, 5.7, 8.0, 8.0.1, 8.4, 9.0 | MySQL 5.7, 8.0, and 8.4 test images | MySQL 9.0 is resolver/module coverage without a current listed container run. |
| Oracle Database | `sqlapp-core-oracle` | 10g, 11g, 11g R2, 12c, 18c, 19c, 21c, 23ai, 26ai | Oracle Database Free 23ai image; optional pinned 26ai image | Oracle 26ai reports product version `23.26.x`; older branches are not all container-tested in the current suite. |
| Apache Phoenix | `sqlapp-core-phoenix` | Generic resolver plus a 5.3.1 feature boundary | Module SQL, resolver, and sequence-block tests | Phoenix 5.3.1 multi-row UPSERT still requires verification against a real cluster. |
| PostgreSQL | `sqlapp-core-postgres` | Explicit branches from 8.2 through 18 | PostgreSQL 14 and 18.4 test images | Historical branches are preserved, but current containers do not rerun every major release. |
| SAP HANA | `sqlapp-core-saphana` | Platform and HANA Cloud split | SAP HANA Express 2.0 (`2.00.088`) image | HANA Cloud vector-index and fuzzy-search catalog behavior requires a HANA Cloud tenant. |
| Google Cloud Spanner | `sqlapp-core-spanner` | Generic Cloud Spanner resolver | Cloud Spanner emulator | Search/vector index, locality/storage, and some service-only metadata require a real service environment. |
| SQLite | `sqlapp-core-sqlite` | Generic SQLite resolver | File/in-process tests with Xerial SQLite JDBC | No server version applies; behavior also depends on the bundled/native SQLite version in the selected driver. |
| Microsoft SQL Server | `sqlapp-core-sqlserver` | 2000, 2005, 2008/R2, 2012, 2014, 2016/SP1, 2017, 2019, 2022 | SQL Server 2017, 2019, 2022, and 2025 test images | SQL Server 2025 currently resolves through the latest compatible implemented dialect unless a newer boundary is added. |
| Sybase ASE | `sqlapp-core-sybase` | Generic Sybase resolver | SAP ASE 16 image | Generated-key propagation for IDENTITY is intentionally rejected on the tested jTDS batch path. |
| Symfoware | `sqlapp-core-symfoware` | Generic Symfoware resolver | Environment-dependent | Requires a licensed target environment and driver for release verification. |
| Vertica | `sqlapp-core-virtica` | 7.2, 8.0, 9.0, 11.1.1, 12.0.4 | Vertica CE 25.1.0-0 image | Modern servers use the latest compatible dialect; projection, segmentation, KSAFE, flex-table, and external-table work remains deferred. |

The artifact name `sqlapp-core-virtica` retains its historical spelling.

## JDBC versions in the current verification build

These are test or implementation dependencies in the current checkout. They
are not a declaration that applications must use exactly these versions.

| Database | JDBC dependency used by current build/test configuration |
|---|---|
| Derby | `org.apache.derby:derby:10.17.1.0` |
| Firebird | Jaybird `6.0.5` in dialect integration tests; dialect artifact currently uses `5.0.6.java11` |
| HSQLDB | `org.hsqldb:hsqldb:2.7.4` |
| Informix | `com.ibm.informix:jdbc:15.0.1.2` |
| Db2 | `com.ibm.db2:jcc:12.1.4.0` |
| MariaDB | `org.mariadb.jdbc:mariadb-java-client:3.5.9` |
| MySQL | `com.mysql:mysql-connector-j:9.7.0` |
| Oracle | `com.oracle.database.jdbc:ojdbc11:23.26.2.0.0` |
| PostgreSQL | `org.postgresql:postgresql:42.7.11` |
| SAP HANA | `com.sap.cloud.db.jdbc:ngdbc:2.29.7` |
| Cloud Spanner | `com.google.cloud:google-cloud-spanner-jdbc:2.41.0` |
| SQLite | `org.xerial:sqlite-jdbc:3.53.1.0` |
| SQL Server | Microsoft JDBC `13.4.0.jre11` in integration tests; dialect artifact currently uses `13.5.0.jre11-preview` |
| Sybase ASE | jTDS `1.3.1` in integration tests |
| Vertica | `com.vertica.jdbc:vertica-jdbc:25.3.0-0` |

Drivers absent from this table must be supplied from the database vendor or an
appropriate compatible distribution. See
[Published artifacts and dependency selection](artifacts.md) for dependency
scope guidance.

## Feature-specific compatibility

The database matrix does not mean that every sqlapp feature has the same
coverage. Consult these focused references before using advanced operations:

- [Bulk insert and migration provider matrix](bulk-insert.md)
- [Dialect enhancement verification and limitations](dialect-enhancement-continuation.md)
- [Schema viewpoints](schema-viewpoints.md)
- [Gradle plugin task guide](gradle-plugin/README.md)

Generated-key handling, set-based migration, metadata catalogs, temporary
tables, UPSERT syntax, and lease storage each have narrower compatibility
boundaries than basic dialect resolution.

## Release verification expectations

For each published release:

1. Run unit tests for every changed dialect module.
2. Run the real-engine integration suites for changed databases when the
   required environment is available.
3. Record every tested server image and JDBC driver in the release notes.
4. Identify configured suites that were not run.
5. Do not broaden a supported-version claim solely because a newer server
   falls back to the latest known dialect.

Production users should validate metadata export and generated DDL against a
non-production instance matching the exact server and JDBC driver versions
used in production.
