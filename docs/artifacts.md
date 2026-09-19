# Published artifacts and dependency selection

sqlapp is published as a set of focused artifacts under the Maven group
`com.sqlapp`. Use the smallest combination that provides the required API and
database behavior. Keep all sqlapp artifacts on the same version unless a
release note explicitly documents another supported combination.

Examples on this page use version `0.80.0`, matching the current project
version.

## Choose by use case

| Use case | Add these artifacts |
|---|---|
| Work only with the Schema model and shared utilities | `sqlapp-core` |
| Read metadata or generate SQL for a database | `sqlapp-core` plus one `sqlapp-core-{db}` dialect and the JDBC driver |
| Invoke file, HTML, data, or migration commands from Java | `sqlapp-command` plus the required dialect and JDBC driver |
| Use Gradle tasks | Apply `com.sqlapp.db`, then add the required dialect and JDBC driver to the task runtime classpath |
| Generate current ELK-based ER diagrams directly | `sqlapp-elk-svg` |
| Use legacy Graphviz rendering | `sqlapp-graphviz` |
| Reuse sqlapp test helpers | `sqlapp-core-test` in the test configuration only |

`sqlapp-command` exposes `sqlapp-core` and `sqlapp-elk-svg` transitively.
The Gradle plugin exposes `sqlapp-command` and includes the Access/MDB and
SQLite dialects as implementation dependencies. Other target database dialects
must be selected by the consuming build.

## Common Gradle combinations

### Schema model without JDBC

```groovy
dependencies {
    implementation 'com.sqlapp:sqlapp-core:0.80.0'
}
```

This is sufficient for code that constructs, transforms, compares, or
serializes the shared Schema model without connecting to a database or asking
for database-specific SQL.

### Java API with PostgreSQL

```groovy
dependencies {
    implementation 'com.sqlapp:sqlapp-core:0.80.0'
    runtimeOnly 'com.sqlapp:sqlapp-core-postgres:0.80.0'
    runtimeOnly 'org.postgresql:postgresql:<jdbc-driver-version>'
}
```

Use `implementation` instead of `runtimeOnly` for the dialect when source code
directly imports dialect-specific classes. Keep the JDBC driver explicit even
when a dialect currently brings one transitively; this makes driver upgrades
and security maintenance visible in the consuming build.

### Command APIs with a database dialect

```groovy
dependencies {
    implementation 'com.sqlapp:sqlapp-command:0.80.0'
    runtimeOnly 'com.sqlapp:sqlapp-core-postgres:0.80.0'
    runtimeOnly 'org.postgresql:postgresql:<jdbc-driver-version>'
}
```

`sqlapp-command` already exposes the core model and ELK renderer. A direct
`sqlapp-core` declaration is unnecessary unless the build intentionally wants
to document or constrain that dependency separately.

### Gradle plugin

```groovy
plugins {
    id 'java'
    id 'com.sqlapp.db' version '0.80.0'
}

dependencies {
    runtimeOnly 'com.sqlapp:sqlapp-core-postgres:0.80.0'
    runtimeOnly 'org.postgresql:postgresql:<jdbc-driver-version>'
}
```

sqlapp tasks use the Java project's `runtimeClasspath`. For a project without
the Java plugin, configure an explicit task runtime as described in the
[Gradle plugin getting-started guide](gradle-plugin/getting-started.md).

### Test helpers

```groovy
dependencies {
    testImplementation 'com.sqlapp:sqlapp-core-test:0.80.0'
}
```

`sqlapp-core-test` exposes `sqlapp-core` to tests. Do not put it on the
application runtime classpath.

## Core and integration artifacts

| Artifact | Purpose | Important transitive sqlapp dependency |
|---|---|---|
| `sqlapp-core` | Schema model, common converters, JDBC and SQL foundations | None |
| `sqlapp-command` | File handling, HTML, data, generation, synchronization, and migration commands | `sqlapp-core`, `sqlapp-elk-svg` |
| `sqlapp-gradle-plugin` | Plugin implementation for ID `com.sqlapp.db` | `sqlapp-command`; also includes MDB and SQLite dialects |
| `sqlapp-elk-svg` | Current ELK-based SVG ER renderer | `sqlapp-core` |
| `sqlapp-graphviz` | Legacy Graphviz ER renderer | Uses `sqlapp-core` internally |
| `sqlapp-core-test` | Shared test fixtures and utilities | `sqlapp-core` |

The Gradle plugin should normally be applied with the `plugins` block. A direct
dependency on `com.sqlapp:sqlapp-gradle-plugin` is intended for plugin
development or advanced embedding, not ordinary build configuration.

## Database dialect artifacts

Each dialect artifact supplies metadata, type, identifier, version-resolution,
and SQL behavior for its database family. It does not replace `sqlapp-core`.

The last column records the build's current runtime dependency behavior. “None”
means the consuming application must supply a suitable JDBC driver. Where a
driver is listed, declaring the application-selected driver explicitly is still
recommended.

| Database family | Artifact | Current bundled JDBC implementation dependency |
|---|---|---|
| IBM Db2 | `sqlapp-core-db2` | None |
| Apache Derby | `sqlapp-core-derby` | None |
| Firebird | `sqlapp-core-firebird` | Jaybird |
| H2 | `sqlapp-core-h2` | None |
| HiRDB | `sqlapp-core-hirdb` | None |
| HSQLDB | `sqlapp-core-hsql` | HSQLDB JDBC driver |
| Informix | `sqlapp-core-informix` | None |
| Microsoft Access / MDB | `sqlapp-core-mdb` | UCanAccess |
| MariaDB | `sqlapp-core-mariadb` | MariaDB Connector/J; also depends on the MySQL dialect |
| MySQL | `sqlapp-core-mysql` | MySQL Connector/J |
| Oracle Database | `sqlapp-core-oracle` | None |
| Apache Phoenix | `sqlapp-core-phoenix` | None |
| PostgreSQL | `sqlapp-core-postgres` | PostgreSQL JDBC driver |
| SAP HANA | `sqlapp-core-saphana` | None |
| Google Cloud Spanner | `sqlapp-core-spanner` | None |
| SQLite | `sqlapp-core-sqlite` | Xerial SQLite JDBC |
| Microsoft SQL Server | `sqlapp-core-sqlserver` | Microsoft JDBC driver; currently a preview driver coordinate |
| Sybase ASE | `sqlapp-core-sybase` | None |
| Symfoware | `sqlapp-core-symfoware` | None |
| Vertica | `sqlapp-core-virtica` | Vertica JDBC driver |

The Vertica artifact ID is currently spelled `sqlapp-core-virtica`; use that
published identifier even though the database product is spelled “Vertica”.

`sqlapp-core-dialect-test` is an internal integration-test project. It is not
part of the Maven Central aggregation and should not be used as an application
dependency.

## Dependency-scope guidance

- Use `implementation` when application source imports classes from the
  artifact.
- Use `runtimeOnly` for a dialect discovered at runtime and for its JDBC driver.
- Use `testImplementation` for `sqlapp-core-test` and database drivers used
  only by tests.
- Avoid version ranges such as `+` in released builds. Pin the sqlapp version
  and JDBC driver version for reproducible schema and migration output.
- Do not add every dialect artifact to one application unless it actually
  connects to every database family. A smaller runtime reduces driver
  conflicts and makes dialect selection easier to diagnose.

## Maven example

The same PostgreSQL Java API setup in Maven is:

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
  <dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <version>YOUR_JDBC_DRIVER_VERSION</version>
    <scope>runtime</scope>
  </dependency>
</dependencies>
```

Maven runtime scope is appropriate only when application source does not import
dialect or driver classes directly.

See [Maven getting started](maven-getting-started.md) for a complete Java 21
POM, command API selection, dependency-tree checks, and service-discovery
considerations for packaged applications.

## Version and compatibility boundaries

Artifact presence describes packaging, not a promise that every feature is
available on every database release. Metadata catalogs, SQL syntax, bulk APIs,
and JDBC behavior vary by product version. Consult database-specific release
notes and tests before production rollout, and validate generated DDL against a
non-production instance of the target database version.

The [compatibility and verification matrix](compatibility.md) distinguishes
version-aware implementation boundaries from server versions exercised by the
repository's current integration-test suites.
