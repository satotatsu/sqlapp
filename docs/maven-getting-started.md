# Maven getting started

Maven applications use sqlapp as Java libraries. The `com.sqlapp.db` plugin is
a Gradle plugin and does not register Maven goals. In a Maven build, call the
Schema, SQL, or command APIs from application code or from a project-specific
Java entry point.

sqlapp requires Java 21. The examples use version `0.80.0`; use the version
available in your artifact repository and keep every sqlapp module aligned.

## Schema model and PostgreSQL runtime

This minimal `pom.xml` adds the public core API, the PostgreSQL dialect, and an
application-selected JDBC driver:

```xml
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0
                             https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>

  <groupId>com.example</groupId>
  <artifactId>database-tooling</artifactId>
  <version>1.0.0-SNAPSHOT</version>

  <properties>
    <maven.compiler.release>21</maven.compiler.release>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <sqlapp.version>0.80.0</sqlapp.version>
    <postgresql.version>YOUR_JDBC_DRIVER_VERSION</postgresql.version>
  </properties>

  <dependencies>
    <dependency>
      <groupId>com.sqlapp</groupId>
      <artifactId>sqlapp-core</artifactId>
      <version>${sqlapp.version}</version>
    </dependency>

    <dependency>
      <groupId>com.sqlapp</groupId>
      <artifactId>sqlapp-core-postgres</artifactId>
      <version>${sqlapp.version}</version>
      <scope>runtime</scope>
    </dependency>

    <dependency>
      <groupId>org.postgresql</groupId>
      <artifactId>postgresql</artifactId>
      <version>${postgresql.version}</version>
      <scope>runtime</scope>
    </dependency>
  </dependencies>
</project>
```

Replace the driver placeholder before building. The dialect supplies sqlapp's
database-specific metadata and SQL behavior. The JDBC driver supplies the
connection. Both are needed for a live JDBC workflow.

Maven Central is searched by Maven's default repository configuration. When a
release is stored in another repository, configure that repository in the
organization's Maven settings or project according to its repository policy;
the sqlapp coordinates remain `com.sqlapp:<artifact>:<version>`.

## Choose the API artifact

| Java code uses | Compile dependency | Additional runtime dependencies |
|---|---|---|
| Schema model, XML, shared SQL API | `sqlapp-core` | Dialect and JDBC driver for database-specific or live JDBC work |
| Export, HTML, conversion, generation, or migration commands | `sqlapp-command` | Matching dialect and JDBC driver when the command connects to a database |
| ELK diagram rendering directly | `sqlapp-elk-svg` | Dependencies brought by that artifact |
| Shared test utilities | `sqlapp-core-test` with `test` scope | Driver/dialect required by the test |

`sqlapp-command` exposes the core model and ELK renderer transitively. An
application using command classes does not need to repeat `sqlapp-core` unless
it deliberately wants to document or constrain that dependency.

For example, replace the `sqlapp-core` dependency above when compiling against
command classes:

```xml
<dependency>
  <groupId>com.sqlapp</groupId>
  <artifactId>sqlapp-command</artifactId>
  <version>${sqlapp.version}</version>
</dependency>
```

See [Published artifacts and dependency selection](artifacts.md) for every
database artifact and its current JDBC dependency behavior.

## Compile scope or runtime scope

Use the default compile scope when application source imports classes from an
artifact. Use `runtime` when the artifact is only discovered or loaded during
execution.

The earlier example declares the dialect and driver at runtime because the
application can resolve them through JDBC metadata and Java `ServiceLoader`
without importing vendor-specific classes. Remove `<scope>runtime</scope>` when
source code directly imports a dialect implementation or JDBC driver class.

Declare the JDBC driver explicitly even when the selected dialect currently
brings one transitively. An explicit version keeps driver upgrades, security
maintenance, and compatibility decisions visible in the consuming project.

sqlapp does not currently publish a BOM or Maven dependency-management
artifact. Define one `${sqlapp.version}` property and use it for every sqlapp
dependency so versions cannot drift accidentally.

## Use the Java API

After adding dependencies, follow [Java API getting started](java-api-getting-started.md)
for complete examples of:

- constructing the canonical Schema model;
- writing and reading Schema XML;
- resolving a dialect from a JDBC connection;
- generating database-specific SQL; and
- managing Connection and DataSource ownership.

A source file that imports only `com.sqlapp.data.schemas.*` and shared API
packages compiles against `sqlapp-core`. Dialect discovery occurs at runtime,
so test the packaged application as well as Maven compilation.

## Verify the resolved dependency graph

Display sqlapp and JDBC dependencies:

```shell
./mvnw dependency:tree "-Dincludes=com.sqlapp:*,org.postgresql:postgresql"
```

Use `mvnw.cmd` on Windows when the project has a Maven Wrapper. Without a
wrapper, use the equivalent `mvn` command installed by the development
environment.

Check that:

1. every `com.sqlapp` artifact has the intended version;
2. exactly the required database dialects are present;
3. the selected JDBC driver version wins conflict resolution; and
4. test-only utilities and drivers do not leak into the application runtime.

For a wider diagnostic tree, omit `-Dincludes`. Maven's nearest-definition
conflict resolution can select a transitive version that differs from the one
expected by the application; pin the application-owned JDBC driver directly.

## Package and service discovery

Dialect resolvers are registered under `META-INF/services` and discovered with
Java `ServiceLoader`. Ordinary Maven JAR and classpath packaging preserves
these resources. If an application builds an uber JAR, verify that its shading
or assembly configuration merges service descriptor files instead of keeping
only one dependency's descriptor.

After packaging, run a non-production connection check and confirm that
`DialectResolver.getInstance().getDialect(connection)` returns the expected
dialect class. A successful JDBC connection alone does not prove that the
sqlapp dialect was packaged.

## Common Maven problems

| Symptom | Check |
|---|---|
| `UnsupportedClassVersionError` | Run Maven and the application on Java 21 or later |
| sqlapp artifact cannot be resolved | Version, repository availability, mirrors, proxy, and offline mode |
| JDBC URL has no suitable driver | JDBC driver is present at application runtime |
| Generic dialect is returned | Matching `sqlapp-core-{db}` JAR and preserved `META-INF/services` files |
| Method or class mismatch at runtime | All sqlapp artifacts resolve to one version |
| Code cannot import a dialect/driver class | Move that dependency from runtime scope to compile scope |

The [compatibility matrix](compatibility.md) lists implemented database-version
boundaries and current real-engine test evidence. Validate metadata and
generated SQL against a disposable instance of the same database product and
version before production use.
