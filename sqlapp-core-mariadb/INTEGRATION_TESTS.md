# MariaDB Testcontainers qualification

Tests live in `sqlapp-core-dialect-test/src/dockerTest` and use the shared
`ReusableTestcontainers` lifecycle. They connect only to the MariaDB container
started by the test; no external JDBC URL or credentials are accepted.

Normal `test`, `check` and `build` do not start these containers. Docker must be
available to Gradle. Run the fixed, disposable 10.5/11.4/11.8 matrix explicitly:

```shell
./gradlew :sqlapp-core-dialect-test:mariadbIntegrationTest
```

Run one baseline using `:sqlapp-core-dialect-test:mariadb105CompatibilityTest`,
`mariadb114CompatibilityTest`, or `mariadb118CompatibilityTest`.
The general `:sqlapp-core-dialect-test:dockerTest` task includes these scenarios
on MariaDB 11.8 and supports the existing optional `-PtestcontainersReuse=true`.
The matrix disables reuse regardless of that option.

Compile without starting Docker:

```shell
./gradlew :sqlapp-core-dialect-test:compileDockerTestJava
./gradlew :sqlapp-core-dialect-test:mariadbIntegrationTest --dry-run
```

Each test creates a unique `sqlapp_mariadb_it_<random>` database and drops it
through try-with-resources. Cleanup failures propagate. Testcontainers manages
container startup, mapped ports, readiness and cleanup. Root access is used only
inside the test-owned container for schema creation and metadata reads.
No PowerShell runner or `SQLAPP_MARIADB_*` environment variables are required.

## Current real-server scenarios

Create a sequence with nondefault start/increment/bounds/cache/cycle,
ENGINE=InnoDB and a quoted comment. Read through the resolved dialect, serialize
to Schema XML, generate CREATE SEQUENCE under a new name, execute and read again.
Compare the start, increment, bounds, cycle, type, engine and comment. Compare
cache size on all supported paths, including 11.5+. No NEXTVAL or
NEXT VALUE calls are made.

The second scenario (11.4+) reads an application-period table with a stored
generated column, an invisible column and an IGNORED index, serializes it to XML,
executes regenerated CREATE under a new name, and compares the retrieved period,
column and index attributes. Older versions skip this scenario with a reason
before creating a schema.

The third scenario reads an INOUT ENUM procedure containing a comma inside an
enum literal and a DECIMAL(12,3) function. It recreates both through XML and DDL,
compares parameter/security/return metadata and invokes the copied function to
check its result. Ordinary stored SQL syntax is used; Oracle/package routines
are outside this scenario.

Additional scenarios cover:

- 11.5+: BIGINT UNSIGNED start/max beyond signed BIGINT, XML/replay and NOCACHE.
- 11.4+: WITHOUT OVERLAPS key read/XML/ALTER replay; overlapping rows are rejected
  while adjacent periods are accepted.
- View MERGE/INVOKER/CASCADED attributes and output names after XML/replay;
  an insert violating the view's CHECK OPTION is rejected.

There are six real-server test methods. All table options and all privilege/
concurrency cases remain outside their scope. Both legacy and modern sequence
paths require SELECT access to their storage tables.

Suggested targets: 10.3 or 10.11 for storage reads, 11.3 for the pre-11.4 reader
boundary, 11.4 for the newer dialect path, and 11.5/12.1 for SEQUENCES catalog
joins. The matrix executed so far is 10.5/11.4/11.8. All scenarios use
test-owned containers and independent disposable schemas.

## Historical qualification

Before conversion to Testcontainers, the six scenarios passed on local disposable
Docker targets: 10.5 (3 passed, 3 version-inapplicable skips), 11.4 (5 passed,
1 skip), and 11.8 (6 passed). These historical results do not establish that the
new Testcontainers runner has been executed. Other versions, restricted
privileges, concurrency and failure recovery remain outside this qualification.
