# Logging and diagnostics

sqlapp reports activity through two separate channels:

- command console output controlled by `ConsoleOutputLevel`; and
- Log4j 2 logging used by core, JDBC, metadata, dialect, and command classes.

Gradle adds its own lifecycle logging and stack traces around those channels.
Configure each layer for its purpose instead of enabling every debug option at
once.

## Command console output

`AbstractCommand` defaults to `INFO`. The supported levels are `ERROR`, `INFO`,
and `DEBUG`, ordered from least to most command output.

```java
import com.sqlapp.data.db.command.ConsoleOutputLevel;
import com.sqlapp.data.db.command.ExportSchemaXmlCommand;

ExportSchemaXmlCommand command = new ExportSchemaXmlCommand();
command.setConsoleOutputLevel(ConsoleOutputLevel.DEBUG);
```

The string setter is also available:

```java
command.setConsoleOutputLevel("DEBUG");
```

Level names are case-insensitive. An unrecognized string currently falls back
to `INFO`; validate external configuration before passing it to the command so
a spelling error is not silently treated as normal information output.

Command output defaults to `System.out` and `System.err`. Applications can
redirect these streams with the command setters when they need to capture
human-readable progress separately from application logs. The streams are
caller-owned and should be closed by the caller when appropriate.

## Gradle task output

Every sqlapp task exposes `consoleOutputLevel`:

```groovy
tasks.named('exportSchemaXml') {
    consoleOutputLevel = 'DEBUG'
}
```

Kotlin DSL:

```kotlin
tasks.named<ExportSchemaXmlTask>("exportSchemaXml") {
    consoleOutputLevel.set("DEBUG")
}
```

This property controls messages emitted by the underlying sqlapp command. It
does not change Gradle's own logging level.

Use Gradle options independently:

| Option | Purpose |
|---|---|
| `--stacktrace` | Show the exception chain for a failed task |
| `--info` | Show Gradle task selection, inputs, dependency resolution, and additional lifecycle detail |
| `--debug` | Show very verbose Gradle internals; may expose environment or connection details |
| `--scan` | Publish a build scan according to the build's Gradle/Develocity configuration and organizational policy |

Start with `--stacktrace --info`. Use `--debug` only in a controlled environment
after checking that logs will not capture passwords, tokens, JDBC properties,
or production data.

## Log4j 2 configuration

sqlapp classes use the Log4j 2 API. An application controls appenders, formats,
destinations, retention, and logger levels through its Log4j configuration.
A minimal `src/main/resources/log4j2.xml` is:

```xml
<?xml version="1.0" encoding="UTF-8"?>
<Configuration status="WARN">
  <Appenders>
    <Console name="Console" target="SYSTEM_ERR">
      <PatternLayout pattern="%d{ISO8601} %-5level %logger{36} - %msg%n"/>
    </Console>
  </Appenders>
  <Loggers>
    <Logger name="com.sqlapp" level="info"/>
    <Root level="warn">
      <AppenderRef ref="Console"/>
    </Root>
  </Loggers>
</Configuration>
```

Raise `com.sqlapp` to `debug` temporarily when diagnosing dialect resolution,
metadata selection, file handling, or SQL generation. Restore a lower level
after diagnosis; verbose JDBC and SQL logging can be large and may contain
schema names, SQL literals, or data values.

Library consumers should keep one coherent Log4j configuration in the
application. Do not package a second default configuration in a reusable
library merely to make sqlapp verbose.

## What to capture for common failures

| Failure | Useful evidence |
|---|---|
| Task is absent | `./gradlew tasks --all`, applied plugin version, owning project |
| Driver is missing | `runtimeClasspath` dependency report and first class-loading exception |
| Dialect is unexpected | JDBC product name/version, dialect artifact, resolved dialect class |
| Metadata selection is empty | Target catalog/schema, include/exclude settings, JDBC-reported names |
| File is missing | Absolute resolved path, producer task, task dependency order |
| SQL execution fails | Database product/version, SQL type, transaction boundary, vendor error code/SQLState |
| Migration resume fails | Plan/report identifier, fingerprint, checkpoint/lease state, failed phase |

Preserve the first relevant cause and any vendor error code. A final Gradle
`TaskExecutionException` by itself is rarely enough to diagnose the underlying
problem.

## Credentials and sensitive data

Before sharing logs or attaching them to an issue, remove:

- usernames, passwords, tokens, and secret-provider output;
- JDBC URLs containing credentials or private hostnames;
- SQL literals or bind values containing personal or production data;
- environment variables unrelated to the reproduction;
- Schema XML row data produced with `dumpRows = true`; and
- migration reports or checkpoints containing internal identifiers that the
  organization treats as sensitive.

Prefer a minimal reproduction against a disposable database. Keep the database
product/version, JDBC driver version, dialect artifact, SQLState, and sanitized
stack trace because those details are needed to reproduce dialect and driver
behavior.

## Distinguish progress from verification

Console and Log4j messages describe execution. They are not a durable success
record. Verify the output appropriate to the operation:

- parse generated Schema XML;
- inspect generated SQL or documentation files;
- check migration success/failure reports and fingerprints;
- query the target database after a data operation; and
- retain reviewed approval artifacts where the workflow supports them.

For Gradle-specific diagnosis, continue with
[Gradle plugin troubleshooting](gradle-plugin/troubleshooting.md). For direct
command behavior and exception handling, see
[Command API getting started](command-api-getting-started.md).
