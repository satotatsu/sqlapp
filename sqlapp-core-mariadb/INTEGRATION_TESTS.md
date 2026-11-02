# MariaDB real-server qualification

`mariadbIntegrationTest` is separate from `test`. It is not part of the default
build and never runs unless explicitly selected. It requires all of:

- `SQLAPP_MARIADB_ALLOW_SCHEMA_CREATE=true` (explicit permission to create and
  drop the test schema on the selected target).
- `SQLAPP_MARIADB_INTEGRATION_URL`: the explicitly authorized MariaDB JDBC URL.
- `SQLAPP_MARIADB_INTEGRATION_USER` and optionally
  `SQLAPP_MARIADB_INTEGRATION_PASSWORD`.
- MariaDB 10.3+ and privileges to create/drop a database and its sequences,
  tables and routines, read required INFORMATION_SCHEMA/storage metadata, and
  execute the test function. The application-period scenario requires 11.4+.

The test creates a unique `sqlapp_mariadb_it_<random>` database and deletes only
that database through try-with-resources cleanup. Cleanup failures propagate and
are suppressed onto an earlier failure when applicable. Forced JVM termination
can leave the disposable database behind. No existing database is cleared.

After explicitly choosing and authorizing a disposable server, run:

```powershell
# Set the four environment variables above for the selected test target first.
.\gradlew.bat :sqlapp-core-mariadb:mariadbIntegrationTest
```

No endpoint or credentials are supplied by this repository. Normal unit tests
remain:

```powershell
.\gradlew.bat :sqlapp-core-mariadb:test
```

For task configuration inspection without connecting to any server:

```powershell
.\gradlew.bat :sqlapp-core-mariadb:mariadbIntegrationTest --dry-run
```

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
the same explicit authorization gate and independent disposable schemas.

## Verification performed locally

- Dedicated task configuration checked with Gradle `--dry-run`.
- Integration source compiled by the ordinary test task.
- Ordinary unit tests executed with the integration tag excluded.
- Authorized disposable Docker runs on 10.5, 11.4 and 11.8 were performed.

| Local image | Passed | Version-inapplicable skipped | Failed |
| --- | ---: | ---: | ---: |
| mariadb:10.5 | 3 | 3 | 0 |
| mariadb:11.4 | 5 | 1 | 0 |
| mariadb:11.8 | 6 | 0 | 0 |

Initial runs exposed four issues, now fixed with regressions: batch SQL parameter
initialization, catalog NULL defaults on generated columns, reversed internal
period-bound order, and the shared model's incorrect UBIGINT maximum.

## Repeating the local Docker run

Local image inventory includes mariadb:10.5, mariadb:11.4 and mariadb:11.8.
The runner starts each image
sequentially as a uniquely named temporary container, bind an automatically
assigned port only on 127.0.0.1, use a generated temporary root password and no
host bind mounts, and execute the six tagged test methods. Version-specific
cases skip with explicit reasons on older servers. Afterwards, remove only the
created containers and their anonymous storage volumes. No existing databases
or user volumes are included. The generated credentials are for these temporary
targets only and are not stored in repository files.

These three targets and their disposable schema/container cleanup were explicitly
authorized and executed in this session. To repeat with authorization:

```powershell
.\sqlapp-core-mariadb\scripts\Test-MariaDbDocker.ps1 -AllowSchemaCreate
```

The script restores process environment values on exit and retains per-version
Gradle logs and copied JUnit XML under the process TEMP directory. All containers
and anonymous volumes created by the completed runs were removed.
