# Building and testing sqlapp

This page describes development verification for the sqlapp repository. It
does not cover publishing credentials or release deployment.

## Prerequisites

- Java 21
- The checked-in Gradle Wrapper
- UTF-8 source and generated text
- Docker only when explicitly running `sqlapp-core-dialect-test:dockerTest`

Check the active tools before diagnosing a build failure:

```shell
java -version
./gradlew --version
```

On Windows PowerShell, replace `./gradlew` with `.\gradlew.bat`. The wrapper
currently selects Gradle 9.6.1 and configures a Java 21 toolchain.

## Repository build

Run the normal multi-project build from the repository root:

```shell
./gradlew build
```

The shared Java convention compiles Java and Groovy as UTF-8, creates source
and Javadoc artifacts, runs JUnit Platform tests, and makes builds using that
convention depend on their JaCoCo report. The normal build does not run the
optional Docker database suites.

Use `clean` only when stale build output is relevant to the problem being
investigated:

```shell
./gradlew clean build
```

Build output belongs under each module's `build/` directory and must not be
committed.

## Focused verification

Start with the smallest test scope that proves the change.

Run one test class:

```shell
./gradlew :sqlapp-core:test --tests com.sqlapp.data.schemas.TableTest
```

Run one module's tests:

```shell
./gradlew :sqlapp-core-postgres:test
```

Run a module build, including compilation, tests, Javadoc/source artifacts,
and its normal verification tasks:

```shell
./gradlew :sqlapp-gradle-plugin:build
```

For a shared-core change, expand verification in this order:

1. the changed test class;
2. the changed module;
3. directly affected command, plugin, renderer, or dialect modules;
4. the repository build.

Do not repeatedly run the full build when a focused failure still needs to be
understood.

## Test categories

| Category | Location/task | External database |
|---|---|---|
| Unit and module tests | Each module's `src/test`; `<module>:test` | Normally no production service; some modules use embedded/file databases |
| Gradle plugin tests | `sqlapp-gradle-plugin:test` | Uses project fixtures, HSQLDB, Access/UCanAccess, and Gradle test support as required by the test |
| Dialect resolver and SQL tests | Each `sqlapp-core-{db}:test` | Normally no external server |
| Real-engine integration tests | `sqlapp-core-dialect-test:dockerTest` | Disposable Testcontainers databases or emulators |
| Proprietary/service-only verification | Manual, environment-specific | Explicitly supplied licensed database or cloud service |

`sqlapp-core-dialect-test:test` is disabled. Its opt-in integration source set
is executed through `dockerTest`, so a successful normal `build` is not evidence
that real database containers ran.

## Docker dialect tests

Docker tests may start database containers, initialize schemas, and execute
DDL/DML against those disposable containers. Confirm that Docker points to the
intended local/container environment before running them. Never redirect these
tests to an external or production database.

Run one database package at a time:

```shell
./gradlew :sqlapp-core-dialect-test:dockerTest \
  --tests "com.sqlapp.data.db.dialect.test.postgres.*"
```

Example packages include `db2`, `firebird`, `informix`, `mariadb`, `mysql`,
`oracle`, `postgres`, `saphana`, `spanner`, `sqlite`, `sqlserver`, `sybase`,
and `virtica`. The exact images and coverage are recorded in the
[compatibility matrix](compatibility.md).

Testcontainers reuse is off by default. Enable it only for local iterative
work when retaining containers is intentional:

```shell
./gradlew -PtestcontainersReuse=true \
  :sqlapp-core-dialect-test:dockerTest \
  --tests "com.sqlapp.data.db.dialect.test.postgres.*"
```

Oracle 26ai uses a product-version boundary reported as `23.26.x`. The suite
supports an explicit image and expected dialect:

```shell
./gradlew \
  -PoracleTestImage=gvenzl/oracle-free:23.26.3-slim-faststart \
  -PoracleExpectedDialect=26ai \
  :sqlapp-core-dialect-test:dockerTest \
  --tests "com.sqlapp.data.db.dialect.test.oracle.*"
```

Some suites require large images, additional memory, or licensed/service
environments. An unavailable environment is an unexecuted test, not a pass.

## Test and coverage output

Gradle writes ordinary test reports under:

```text
<module>/build/reports/tests/test/
```

JaCoCo reports are under the corresponding module's `build/reports/jacoco/`
tree. Docker-test reports use the `dockerTest` task name below
`sqlapp-core-dialect-test/build/reports/tests/`.

When reporting verification, include:

- exact commands;
- passed and failed scopes;
- tests intentionally not run;
- every external database, image, or emulator used;
- generated fixtures intentionally changed.

Do not state that the complete build passed when only a module or test class
was executed.

## Dependency and toolchain troubleshooting

Use dependency insight before changing versions:

```shell
./gradlew :sqlapp-gradle-plugin:dependencies --configuration runtimeClasspath
```

Useful diagnostics include:

```shell
./gradlew projects
./gradlew tasks --all
./gradlew :sqlapp-core-postgres:test --stacktrace
```

If dependency download fails, distinguish a network/repository failure from a
compilation or test failure. Do not update dependency versions merely to work
around a transient repository problem.

For reproducible automation, use a dedicated Gradle user home and `--no-daemon`
when the execution environment requires isolation:

```shell
./gradlew --gradle-user-home .gradle-user-home build --no-daemon
```

The local cache directory must remain uncommitted.
