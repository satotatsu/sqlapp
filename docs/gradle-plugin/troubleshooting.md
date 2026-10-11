# Gradle plugin troubleshooting

Use this guide when a sqlapp task is missing, cannot load a driver or dialect,
does not see DataSource settings, reads the wrong file, or produces an
unexpected result. Commands use the project Gradle Wrapper.

## Start with the failure evidence

Run the failing task with its stack trace and Gradle information logging:

```shell
./gradlew <task> --stacktrace --info
```

On Windows PowerShell:

```powershell
.\gradlew.bat <task> --stacktrace --info
```

The first sqlapp or JDBC exception normally identifies the useful cause. Later
Gradle wrapper exceptions often only report that task execution failed. Do not
add `--debug` when logs may contain connection properties or credentials.

## The task is not listed

Inspect every task, including tasks outside the usual groups:

```shell
./gradlew tasks --all
```

Check that `com.sqlapp.db` is applied to the project in which the command is
being run. In a multi-project build, qualify the project and task, for example
`./gradlew :database:exportSchemaXml`.

The plugin registers the fixed tasks listed in the [task reference](task-reference.md).
Data import/export, conversion, generation, SQL execution, and some migration
operations are public task types that the consuming build must register under
its own name.

## A JDBC driver cannot be loaded

The task loads JDBC and dialect implementations from its `runtimeClasspath`.
Inspect that configuration:

```shell
./gradlew dependencies --configuration runtimeClasspath
```

For a multi-project build, qualify the project:

```shell
./gradlew :database:dependencies --configuration runtimeClasspath
```

Confirm that the output contains the selected JDBC driver. A dependency in an
unrelated custom configuration or only in the buildscript classpath is not
automatically visible to sqlapp tasks.

Applying the Java plugin creates `runtimeClasspath`. Without it, create an
explicit resolvable configuration and add it to every sqlapp task as shown in
[Getting started](getting-started.md#1-apply-the-plugin-and-supply-the-database-runtime).
If more than one driver version is present, use Gradle's dependency insight:

```shell
./gradlew dependencyInsight --dependency postgresql --configuration runtimeClasspath
```

Replace `postgresql` with the driver module being investigated.

## The wrong or generic dialect is selected

A JDBC driver establishes the connection; a `sqlapp-core-{db}` artifact
provides database-specific metadata and SQL behavior. Both must be present on
the task runtime classpath. Keep the dialect version aligned with the Gradle
plugin version.

Check these items in order:

1. The matching dialect artifact appears in `runtimeClasspath`.
2. The JDBC URL reaches the intended database instance.
3. The server product name and version reported by JDBC match a supported
   resolver boundary in the [compatibility matrix](../compatibility.md).
4. Saved Schema XML used without a connection contains the expected product
   metadata.

sqlapp discovers dialect resolvers with Java `ServiceLoader`. When no product
resolver matches, `DialectResolver` returns the generic default dialect. That
fallback can generate incomplete or generic behavior, so treat an unexpected
dialect as a configuration error before running DDL or migration tasks.

## DataSource settings are not applied

The property name for a JDBC URL is `jdbcUrl`, not `url`. A direct task setup is:

```groovy
tasks.named('exportSchemaXml') {
    dataSource {
        jdbcUrl = providers.environmentVariable('SQLAPP_JDBC_URL')
        username = providers.environmentVariable('SQLAPP_DB_USER')
        password = providers.environmentVariable('SQLAPP_DB_PASSWORD')
    }
}
```

When loading settings from a file, confirm the resolved path relative to the
Gradle project containing the task:

```groovy
dataSource {
    properties layout.projectDirectory.file('config/dataSource.properties')
}
```

Supported file suffixes are `.properties`, Java-properties `.xml`, `.json`,
`.yaml`, and `.yml`. The loader ignores directories, nonexistent paths that
reach it, and files with any other suffix. It does not report an unsupported
suffix as a configuration error. Check spelling and case, and use `--info` to
inspect Gradle's resolved task inputs.

Files are merged in supplied order. Explicit `dataSource` values are applied
after file values and therefore override them. The pool-size defaults are also
applied after file loading; set `maximumPoolSize` or `minimumIdle` directly on
the extension when different values are required. Full merge rules and file
examples are in [Configure a DataSource from a file](task-reference.md#configure-a-data-source-from-a-file).

## An input file is missing or stale

Gradle task ordering is explicit. Matching the output name of one task to the
input name of another does not create a dependency:

```groovy
tasks.named('generateHtmlDocs') {
    dependsOn(tasks.named('exportSchemaXml'))
    targetFile = layout.buildDirectory.file('schema/Catalog.xml')
}
```

Use `./gradlew <task> --dry-run` to inspect task order. A dry run does not open
or validate file contents. Check the actual path reported in the exception,
especially in multi-project builds where `projectDirectory` differs between
projects.

For a reviewed offline snapshot, remove the producing task dependency and
point `targetFile` at the committed XML explicitly. Otherwise a documentation
or SQL task may refresh the snapshot by connecting to the database first.

## Output is empty or appears in the wrong place

Check the task's `outputDirectory`, `outputFileName`, and input selection
properties in the [task reference](task-reference.md). Relative `File` values
are normally resolved from the owning Gradle project's directory, while
`layout.buildDirectory` resolves under that project's build directory.

For metadata export, `includeSchemas` filters by schema name; it does not set
the connection's default schema. An empty selection can therefore mean that
the filter does not match the names returned by JDBC. Enable `--info`, inspect
the connected product and selected schemas, and first try the smallest safe
metadata-only export with `dumpRows = false`.

SQL generation may write to the console when no output directory is
configured. Set `outputDirectory` when a file artifact is required.

## A task runs again or does not reflect database state

sqlapp task classes disable Gradle build caching by default because database
state and other external state are not fully represented by task inputs. Some
tasks also explicitly disable up-to-date reuse for indirect inputs. Do not use
Gradle's `UP-TO-DATE` result as evidence that the target database is unchanged.

When diagnosing local task-state behavior, run once with `--rerun-tasks`:

```shell
./gradlew <task> --rerun-tasks --info
```

This only changes Gradle task reuse. It does not make a database operation
safe to repeat. Review the task's database effects and idempotency in the
[task reference](task-reference.md) before rerunning an execution or migration
task.

## Connection and permission failures

Separate failures into these stages:

| Stage | Typical evidence | Check |
|---|---|---|
| Dependency resolution | Gradle cannot download a module | Repositories, coordinates, proxy, offline mode |
| Driver loading | Driver class or JDBC scheme is unknown | JDBC driver on `runtimeClasspath` |
| Connection | Timeout, authentication, TLS, or network exception | URL, credentials, network path, certificates |
| Metadata read | Permission or catalog-query exception | Metadata permissions and supported server version |
| SQL execution | Vendor SQL, transaction, or object error | Resolved dialect, generated SQL, target state |

Test against a disposable or explicitly authorized database. Do not diagnose a
configuration by pointing examples or migration tasks at production.

## Information to include in a bug report

Include:

- sqlapp plugin and dialect versions;
- Java and Gradle versions from `./gradlew --version`;
- database product/version and JDBC driver version;
- task name and the smallest relevant configuration with secrets removed;
- the first relevant exception and its `--stacktrace` cause;
- whether the task uses a live connection or saved Schema XML;
- the matching `runtimeClasspath` dependency lines.

Do not include passwords, tokens, full JDBC URLs containing credentials, or
production data.

See [Logging and diagnostics](../logging-and-diagnostics.md) for command output
levels, Log4j configuration, Gradle logging options, and a sanitization
checklist.
