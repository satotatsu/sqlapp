# sqlapp

sqlapp is a Java 21 database engineering toolkit. It provides a shared schema
model, database metadata readers, database-specific SQL generation, HTML
documentation with ER diagrams, data import/export, test-data generation, and
versioned or bulk database migration. The same capabilities are available
through Java APIs, command classes, and the `com.sqlapp.db` Gradle plugin.

## What you can do

- Export database metadata to sqlapp Schema XML.
- Compare schema snapshots and generate database-specific DDL.
- Generate browsable HTML database documentation and ER diagrams.
- Maintain logical names, descriptions, and virtual foreign keys outside the
  physical database.
- Import, export, and convert tabular data in formats such as CSV, TSV, Excel,
  YAML, JSON, and TOML.
- Generate relational test data with foreign-key dependency handling.
- Apply versioned SQL migrations.
- Plan, execute, resume, verify, and audit bulk data migrations.

## Requirements

- Java 21
- A JDBC driver for the database being accessed; declare it explicitly in the
  application so its version remains under application control
- A matching `sqlapp-core-{db}` dialect module for database-specific metadata
  and SQL behavior
- Gradle, preferably through a Gradle Wrapper, when using the Gradle plugin

The JDBC driver and the sqlapp dialect have different roles and are both
needed for JDBC workflows. Some dialect artifacts currently carry a driver as
a runtime dependency, but applications should not rely on that as a stable
driver-version policy. The Gradle plugin does not select a general target
database driver.

## Gradle plugin quick start

The following Groovy DSL example exports PostgreSQL metadata to Schema XML.
Replace the JDBC driver version and connection values for your environment.

```groovy
plugins {
    id 'java'
    id 'com.sqlapp.db' version '0.80.0'
}

repositories {
    mavenCentral()
}

dependencies {
    runtimeOnly 'com.sqlapp:sqlapp-core-postgres:0.80.0'
    runtimeOnly 'org.postgresql:postgresql:<jdbc-driver-version>'
}

tasks.named('exportSchemaXml') {
    dataSource {
        jdbcUrl = providers.environmentVariable('SQLAPP_JDBC_URL')
        username = providers.environmentVariable('SQLAPP_DB_USER')
        password = providers.environmentVariable('SQLAPP_DB_PASSWORD')
    }
    includeSchemas.add('public')
    target = 'catalog'
    dumpRows = false
    outputDirectory = layout.buildDirectory.dir('schema')
    outputFileName = 'Catalog.xml'
}
```

Run it with the project wrapper:

```shell
./gradlew exportSchemaXml
```

On Windows PowerShell, use `.\gradlew.bat exportSchemaXml`. The example writes
`build/schema/Catalog.xml`. Credentials should come from environment variables,
a local untracked properties file, or another secret provider; do not commit
them to the build script.

Continue with the [Gradle plugin getting-started guide](docs/gradle-plugin/getting-started.md)
to generate HTML documentation and SQL from the saved XML. A complete runnable
configuration is available in
[`sqlapp-gradle-example`](https://github.com/satotatsu/sqlapp-gradle-example).

## Java library dependency

For direct Java API use, add the core model and the dialect needed by the
application. Gradle:

```groovy
dependencies {
    implementation 'com.sqlapp:sqlapp-core:0.80.0'
    runtimeOnly 'com.sqlapp:sqlapp-core-postgres:0.80.0'
    runtimeOnly 'org.postgresql:postgresql:<jdbc-driver-version>'
}
```

Maven:

```xml
<dependencies>
  <dependency>
    <groupId>com.sqlapp</groupId>
    <artifactId>sqlapp-core</artifactId>
    <version>0.80.0</version>
  </dependency>
  <dependency>
    <groupId>com.sqlapp</groupId>
    <artifactId>sqlapp-core-postgres</artifactId>
    <version>0.80.0</version>
    <scope>runtime</scope>
  </dependency>
</dependencies>
```

Add the database vendor's JDBC driver separately. Modules are published under
the `com.sqlapp` group. Keep sqlapp module versions aligned unless a release
note explicitly states otherwise.

## Project modules

| Module | Responsibility |
|---|---|
| `sqlapp-core` | Schema model, JDBC foundations, shared metadata and SQL APIs |
| `sqlapp-command` | Documentation, data handling, generation, synchronization, and migration commands |
| `sqlapp-gradle-plugin` | Gradle tasks backed by `sqlapp-command` |
| `sqlapp-core-{db}` | Database-specific metadata readers, dialect resolution, data types, and SQL generation |
| `sqlapp-elk-svg` | Current ELK-based SVG ER diagram rendering |
| `sqlapp-graphviz` | Legacy Graphviz-based ER diagram rendering |
| `sqlapp-core-test` | Shared test support; not required at application runtime |

The repository currently contains dialect modules for DB2, Derby, Firebird,
H2, HiRDB, HSQLDB, Informix, Access/MDB, MariaDB, MySQL, Oracle, Phoenix,
PostgreSQL, SAP HANA, Cloud Spanner, SQLite, SQL Server, Sybase ASE, Symfoware,
and Vertica. Module presence does not by itself guarantee every feature on
every server version; check the relevant dialect documentation and release
notes before production use.

See [Published artifacts and dependency selection](docs/artifacts.md) for the
complete artifact list, common dependency combinations, dialect artifact
names, and current JDBC dependency behavior. See the
[compatibility and verification matrix](docs/compatibility.md) for Java and
Gradle baselines, dialect version boundaries, and real-engine test evidence.

## Documentation

- [Documentation index](docs/README.md)
- [Gradle plugin task guide](docs/gradle-plugin/README.md)
- [Gradle plugin troubleshooting](docs/gradle-plugin/troubleshooting.md)
- [Java API getting started](docs/java-api-getting-started.md)
- [Published artifacts and dependency selection](docs/artifacts.md)
- [Compatibility and database verification matrix](docs/compatibility.md)
- [Architecture](docs/architecture.md)
- [Building and testing](docs/build-and-test.md)
- [Runnable Gradle example map](docs/gradle-plugin/example-project.md)
- [Schema XML, SQL, and HTML workflows](docs/gradle-plugin/schema-sql-and-html.md)
- [Custom tasks and versioned migrations](docs/gradle-plugin/custom-tasks-and-migrations.md)
- [Normalization and legacy-migration tasks](docs/gradle-plugin/normalization-and-legacy-migration.md)
- [Schema viewpoints](docs/schema-viewpoints.md)
- [Bulk insert and bulk migration](docs/bulk-insert.md)
- [Data converters](docs/converters.md)
- [Roadmap](docs/roadmap.md)

The companion
[`sqlapp-gradle-example`](https://github.com/satotatsu/sqlapp-gradle-example)
contains runnable configurations for schema export, schema comparison, SQL
generation, migrations, HTML documentation, dictionaries, logical foreign
keys, data import/export, format conversion, and test-data generation.

## Building this repository

Use Java 21 and the checked-in wrapper:

```shell
./gradlew build
```

On Windows PowerShell:

```powershell
.\gradlew.bat build
```

Some dialect integration tests require their own database environment and are
not implied by this command. Do not point tests or examples at a production
database.

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for the repository's development and
testing rules. Bug reports and focused pull requests should include the
affected module, database product and version when relevant, expected behavior,
actual behavior, and a minimal reproduction.

## License

Copyright © 2007–2026 Tatsuo Satoh.

sqlapp is free software licensed under the
[GNU Lesser General Public License, version 3 or later](LICENSE.md). Individual
modules include their license notice and copies of the GNU LGPL and GPL terms.
There is no warranty; see the license terms for details.
