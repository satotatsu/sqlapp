# AlloyDB / AlloyDB Omni dialect

`sqlapp-core-alloydb` adds PostgreSQL engine **15, 16 and 17** baselines for
AlloyDB and AlloyDB Omni. It reuses `sqlapp-core-postgres` readers, SQL factories
and bulk executors instead of maintaining a parallel implementation. Its core
and PostgreSQL API dependencies preserve access to the existing shared model;
no driver or dependency version changes are introduced.

Omni verification uses official `google/alloydbomni` containers. Managed
Google Cloud AlloyDB remains **experimental / real-service unverified**:
Omni tests do not qualify cloud storage, permissions, extensions, connectivity
or failover. PostgreSQL 14 and earlier and 18 and later are outside this
module's declared baseline. Public documentation mentions Omni 18 versions,
but the tested public registry had neither `18.3.0` nor `18.1.0`; no unexecuted
18 branch is advertised as supported.

## Use

Add `com.sqlapp:sqlapp-core-alloydb` at the same version as the other sqlapp
artifacts. Continue using the existing high-level entry point:

```java
Dialect dialect = DialectResolver.getInstance().getDialect(connection);
```

The resolver checks the registered `google_columnar_engine.enabled` flag in
`pg_catalog.pg_settings` on a PostgreSQL JDBC connection. The flag's presence,
not its on/off value, is the identification marker. Omni 16.8.0 reports an
ordinary PostgreSQL `version()` string; endpoint names and version banners
alone cannot identify that server. The columnar engine is not enabled or
changed by resolution. Known YSQL/Cockroach version banners bypass this probe.

The probe performs one read-only SELECT and closes its statement/result set.
It never changes transaction settings, commits or rolls back. Failures are
propagated with their SQL cause. If the marker is unavailable/hidden, automatic
identification cannot establish AlloyDB identity and ordinary PostgreSQL
resolution continues. Adding this artifact adds that catalog probe to normal
PostgreSQL connection-based resolution. Product-name-only resolution does not
open a connection. The marker is a catalog heuristic, not an authentication
mechanism, and does not distinguish managed AlloyDB from Omni.

For offline generation or explicitly selected AlloyDB identity:

```java
Dialect dialect = DialectResolver.getInstance()
    .getDialect("AlloyDB", 16, 0, null);
```

Aliases: `AlloyDB`, `Google AlloyDB`, `AlloyDB for PostgreSQL`, `AlloyDB Omni`
and `alloydb-omni`. Supply the PostgreSQL engine major. Unsupported majors fail
clearly. Explicit product selection does not validate a remote server.
The inherited metadata readers retain the JDBC-reported PostgreSQL product
name in Catalog/Schema product fields. When selecting a dialect from exported
XML without a live connection, explicitly select AlloyDB if that identity is
required; XML product fields alone cannot distinguish this server family.

## Relational scope and boundaries

The corresponding PostgreSQL baseline supplies names/quoting, types,
metadata and CREATE/ALTER/DROP factories. Dedicated ServiceLoader providers
preserve AlloyDB identity while reusing PostgreSQL COPY FROM STDIN,
staging-table UPSERT and set-based snapshot execution. Existing bulk option
validation and caller-owned transaction semantics are preserved.

Tests cover table/index/sequence/view/function metadata, Schema XML and table
recreation, generated ALTER/DROP, COPY escapes/null/empty/binary/arrays,
UPSERT and rollback, COPY failure rollback, and atomic checkpoint failure /
resume. Omni-specific tests exercise identification with columnar disabled
and a non-superuser login. Ordinary PostgreSQL controls guard against false
identification, including an unregistered custom setting placeholder.
Snapshot provider registration is unit-tested; snapshot execution is not
qualified by this suite.

Columnar population/cache state, automatic columnarization, ScaNN/vector
extensions, Google ML integrations, physical placement, cloud IAM/proxies,
managed roles/ownership, replication, backup, performance and failover are
outside this baseline. These are not silently translated into Table specifics
or replayed as DDL. Ordinary PostgreSQL syntax inheritance does not grant
privileged operations or install vendor extensions on a managed service.

## Local tests

```shell
./gradlew :sqlapp-core-alloydb:test :sqlapp-core-alloydb:assemble
./gradlew :sqlapp-core-dialect-test:alloydbCompatibilityTest
```

The fixed matrix uses official `google/alloydbomni:15.17.0`,
`google/alloydbomni:16.8.0` and `google/alloydbomni:17.9.0`, plus an upstream
`postgres:17.9` control. Individual tasks are `alloydb15CompatibilityTest`,
`alloydb16CompatibilityTest`, `alloydb17CompatibilityTest` and
`alloydbPostgresControlTest`. Containers are disposable, use anonymous storage,
and require no external database or Google Cloud credentials. The test runner
allocates 256 MiB shared memory; it does not alter host settings or request
privileged containers. Consult Google's installation prerequisites for CPU,
RAM, disk, Linux runtime and container support. This is functional verification,
not a production platform certification or benchmark.

The 2026-10-10 qualification passed 7 module tests, all 8 cases on each of
these three Omni images (24 total), and 8 PostgreSQL control cases. Assembly
and local publication metadata generation also passed. PostgreSQL regression
and the dependent dialect/command/plugin suites passed; detailed commands and
results are recorded in the [compatibility matrix](../docs/compatibility.md).

## Optional managed AlloyDB tests

`alloydbExternalTest` is separate from default tests, `dockerTest` and the
compatibility aggregate. It was not executed during implementation. Use only
an explicitly authorized disposable writable database and a writer endpoint.
It creates UUID schemas, inserts/updates rows, exercises rollback/checkpoint
behavior and drops its schemas with CASCADE. An interrupted run may leave a
`sqlapp_alloydb_...` schema behind. Preflight verifies AlloyDB identity before
any test writes; ordinary PostgreSQL targets are rejected.

Supply environment variables without recording credentials in source or logs:

- `SQLAPP_ALLOYDB_JDBC_URL`: `jdbc:postgresql://.../disposable_database`
- `SQLAPP_ALLOYDB_USER` and `SQLAPP_ALLOYDB_PASSWORD`
- `SQLAPP_ALLOYDB_ALLOW_DESTRUCTIVE_TESTS=true`

The fixture requests `sslmode=verify-full`; configure trusted certificates and
network access using the PostgreSQL driver's normal mechanisms. Cloud proxy,
connector and IAM integration are not supplied by the dialect itself.

```shell
./gradlew :sqlapp-core-dialect-test:alloydbExternalTest
```

Before qualifying managed AlloyDB, record the PostgreSQL engine version,
service configuration, identification, role/extension differences and the
metadata/recreation/bulk results for each major being claimed. No Google Cloud
resource is created, changed or deleted by the test task.

## References

- [Omni overview](https://docs.cloud.google.com/alloydb/omni/docs)
- [Official container installation](https://docs.cloud.google.com/alloydb/omni/containers/17.9.0/docs/quickstart)
- [AlloyDB columnar flags](https://docs.cloud.google.com/alloydb/docs/reference/columnar-engine-flags)
- [Container architecture and managed-service differences](https://docs.cloud.google.com/alloydb/omni/containers/current/docs/overview)
