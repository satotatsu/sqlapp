# Aurora PostgreSQL dialect (experimental)

`sqlapp-core-aurora` adds Aurora PostgreSQL engine baselines **14, 15, 16 and 17**.
It depends on `sqlapp-core-postgres` to reuse its canonical Schema model,
version-specific metadata readers, SQL factories and bulk executors. It adds
no JDBC dependency version or special driver requirement.

**Aurora real-engine execution is unverified.** Upstream PostgreSQL containers
validate inherited SQL behavior; they do not emulate Aurora storage, roles,
extensions or service behavior. This module remains experimental until the
optional suite is executed on explicitly authorized Aurora targets.

## Use

Add `com.sqlapp:sqlapp-core-aurora` at the same version as your other sqlapp
artifacts. The ordinary entry point stays unchanged:

```java
Dialect dialect = DialectResolver.getInstance().getDialect(connection);
```

On a PostgreSQL JDBC connection, automatic identification first checks
`pg_catalog.pg_settings` for the AWS `rds.extensions` / `aurora_stat_utils`
marker. Only after that marker exists does it locate and call the zero-argument
`aurora_version()` in `pg_catalog` or `public`. The function is qualified to
avoid search-path shadowing. An ordinary PostgreSQL or RDS PostgreSQL server
returns to normal PostgreSQL resolution without calling an absent function.
YSQL and Cockroach version banners bypass these queries.

The resolver performs read-only SELECTs and never changes autocommit, commits,
rolls back or creates savepoints. Query failures propagate with their cause;
there is no silent fallback after a failed probe. Detection needs visibility
of the AWS marker and function. A hidden marker cannot establish Aurora
identity and falls back to ordinary PostgreSQL. No endpoint-name guessing is
performed. Adding this artifact adds a catalog probe to ordinary PostgreSQL
connection-based dialect resolution; product-name-only resolution does not
access a database.

For offline generation, or explicitly selected Aurora identity:

```java
Dialect dialect = DialectResolver.getInstance()
    .getDialect("Aurora PostgreSQL", 16, 0, null);
```

Aliases are `Aurora PostgreSQL`, `Amazon Aurora PostgreSQL` and
`aurora-postgresql`. Supply the **PostgreSQL engine major**, not an unrelated
product release number. Unsupported majors fail clearly; 13 and earlier and
18 and later are outside this module's declared baseline. Aurora MySQL and
Aurora DSQL are separate products and do not match these aliases.

AWS documents the distinction between PostgreSQL and Aurora versions in
[Amazon Aurora versioning](https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/Aurora.VersionPolicy.Versioning.html)
and [aurora_version](https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/aurora_version.html).
The marker-before-function detection follows the approach in the AWS-owned
[pg-collector](https://github.com/awslabs/pg-collector/blob/main/pg_collector.sql).

## Implemented scope

The matching PostgreSQL 14–17 baseline supplies identifiers/quoting, types,
metadata and CREATE/ALTER/DROP factories. Dedicated ServiceLoader providers
preserve Aurora product identity while reusing PostgreSQL `COPY FROM STDIN`,
staging-table UPSERT and set-based migration snapshot executors. No separate
Aurora SQL builder or duplicate transaction implementation is introduced.
The existing option validation and error handling remain in force.

AWS cluster resources, endpoints, IAM authentication, failover/retry policy,
Serverless behavior, physical storage placement, parameter groups and AWS
extension provisioning are outside the Schema dialect scope. PostgreSQL
syntax inheritance does not guarantee Aurora permits privileged operations,
role/owner replay, arbitrary extensions or tablespace/filesystem operations.
These remain subject to Aurora restrictions and the caller's privileges.

## Verification

```shell
./gradlew :sqlapp-core-aurora:test :sqlapp-core-aurora:assemble
./gradlew :sqlapp-core-dialect-test:auroraPostgresCompatibilityTest
```

The matrix uses owned disposable **upstream PostgreSQL** containers pinned to
`postgres:14.22`, `postgres:15.17`, `postgres:16.13` and `postgres:17.9`.
It checks metadata for tables/indexes/sequences/views/functions, generated DDL
recreation, quoted identifiers, COPY text/null/empty/binary/array values,
staging UPSERT and caller-owned rollback, and safe ordinary-PostgreSQL
identification inside a transaction. Resolver unit tests additionally exercise
simulated Aurora identification; those mocks are not real-engine evidence.
Snapshot provider registration is unit-tested; Aurora snapshot execution is
not qualified by this matrix.

### Optional Aurora execution

The `auroraExternalTest` task is separate from unit tests, the Docker suite
and all compatibility aggregates. **It was not executed during implementation.**
Run it only after explicit authorization for a disposable writable Aurora
PostgreSQL database. It creates UUID-named schemas and drops those schemas
with CASCADE in cleanup, and tests INSERT/UPDATE and transaction rollback.
An interrupted process can leave its `sqlapp_aurora_...` schema behind.

Supply these environment variables without putting secrets in build files,
command arguments or logs:

- `SQLAPP_AURORA_JDBC_URL`: `jdbc:postgresql://.../disposable_database`
- `SQLAPP_AURORA_USER` and `SQLAPP_AURORA_PASSWORD`
- `SQLAPP_AURORA_ALLOW_DESTRUCTIVE_TESTS=true`: explicit test opt-in

The connection requests `sslmode=verify-full`; install the trusted AWS CA via
the driver's normal trust configuration. Use a writer endpoint. The test
requires successful automatic Aurora identification and fails on ordinary
PostgreSQL. No AWS resource is created or deleted by the task.

```shell
./gradlew :sqlapp-core-dialect-test:auroraExternalTest
```

Before removing experimental status, record engine and Aurora patch versions,
role/permission setup, identification, metadata/recreation and bulk results
on actual Aurora targets for every major version being qualified. Also
investigate managed-role and extension differences, and AWS driver/wrapper
compatibility if used. No performance or failover guarantee is implied.
