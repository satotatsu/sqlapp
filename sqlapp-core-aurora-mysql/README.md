# Aurora MySQL dialect (experimental)

`sqlapp-core-aurora-mysql` supports Aurora MySQL 3.x (MySQL 8.0 compatibility)
and Aurora MySQL 8.4. Aurora 1.x/2.x and other compatibility versions are excluded.
Actual AWS Aurora execution is unverified; upstream MySQL tests do not establish service compatibility.

## Use

Add `com.sqlapp:sqlapp-core-aurora-mysql` at the same version as other sqlapp artifacts.
The dependency on `sqlapp-core-mysql` reuses its Schema model, metadata readers,
SQL factories and bulk executors without a new driver requirement.

```java
Dialect dialect = DialectResolver.getInstance().getDialect(connection);
// Offline: supply the MySQL compatibility version, not the Aurora 3.x release.
Dialect offline = DialectResolver.getInstance().getDialect("Aurora MySQL", 8, 0, null);
Dialect lts = DialectResolver.getInstance().getDialect("aurora-mysql", 8, 4, null);
```

Aliases: `Aurora MySQL`, `Amazon Aurora MySQL`, `aurora-mysql`.
Installing this module adds one read-only `SHOW VARIABLES WHERE Variable_name = 'aurora_version'`
probe to connection-based resolution of JDBC product `MySQL`. An absent variable
falls back to MySQL. A blank, unsupported or inconsistent release fails clearly,
as do SQL errors. No transaction settings, commits, rollbacks or endpoint heuristics
are used. Explicit product-name resolution performs no queries. Connection-specific
identification does not change the ordinary MySQL product-name cache.

MySQL 8.0 uses the existing MySql801 baseline; 8.4 uses MySql840. No new metadata
queries or DDL generation overrides are introduced. Snapshot execution uses the
inherited MySQL provider; dedicated insert/upsert providers retain Aurora identity.
MySQL syntax inheritance does not establish permission to replay privileged
objects, users, grants, plugins or storage options on Aurora.
AWS resource management, IAM authentication, failover/retry, Serverless behavior,
and cluster/parameter provisioning are outside this dialect's scope.

## Validation

```shell
./gradlew :sqlapp-core-aurora-mysql:test :sqlapp-core-aurora-mysql:assemble :sqlapp-core-mysql:test
```

The opt-in upstream compatibility matrix uses disposable local MySQL 8.0 and 8.4
containers, never an AWS endpoint:

```shell
./gradlew :sqlapp-core-dialect-test:auroraMySqlCompatibilityTest
# Individual baselines:
./gradlew :sqlapp-core-dialect-test:auroraMySql80CompatibilityTest
./gradlew :sqlapp-core-dialect-test:auroraMySql84CompatibilityTest
```

These tests explicitly select the Aurora dialect while executing inherited SQL on
ordinary MySQL. They cover native/portable types through column metadata and
regenerated DDL, table primary keys and view recreation, LOAD DATA preservation of
escapes/Unicode/NULL/empty/binary values, and INSERT/UPSERT rollback inside a
caller-owned transaction. Owned-transaction UPSERT is also checked for restored
autocommit and committed changes visible from a second connection. Actual JDBC identification on ordinary MySQL must fall
back to MySQL without committing, rolling back or changing autocommit.
The `sqlapp-core-dialect-test` dependency is test infrastructure only; it adds no
production dependency to the Aurora module.

### Executed upstream validation (2026-11-03)

Java 25 / repository Gradle Wrapper:

```powershell
.\gradlew.bat :sqlapp-core-dialect-test:auroraMySql80CompatibilityTest --console=plain
.\gradlew.bat :sqlapp-core-dialect-test:auroraMySqlCompatibilityTest :sqlapp-core-aurora-mysql:test --console=plain
```

| Container tag | Observed server | Tests | Failures / errors / skips |
| --- | --- | ---: | --- |
| `mysql:8.0` | 8.0.46 | 6 | 0 / 0 / 0 |
| `mysql:8.4` | 8.4.11 | 6 | 0 / 0 / 0 |

The first baseline run passed five tests; the final matrix above includes the
additional owned-transaction UPSERT test and stronger recreation assertions.
The final build succeeded; the 21 Aurora unit tests were UP-TO-DATE from their
previous successful run, not re-executed. No production code or dependency
versions changed for this validation. Generated build outputs are ignored.

Image digests observed for this run (tags may move):

- MySQL 8.0: `sha256:7dcddc01f13bab2f15cde676d44d01f61fc9f99fe7785e86196dfc07d358ae2b`
- MySQL 8.4: `sha256:b3b90af2a6552ae30c266fdb7d5dd55f3afb72404bb78d37fe8a23eb857fd3fb`

### Remaining qualification

No external database is used by unit tests. Actual Aurora qualification remains
required for Aurora-specific catalog differences, permissions, privileged objects,
grants and service restrictions. Positive Aurora identification is covered by
mock JDBC unit tests; upstream containers cannot verify the AWS variable itself.
No real-service compatibility is claimed.

AWS version reference:
https://docs.aws.amazon.com/AmazonRDS/latest/AuroraUserGuide/AuroraMySQL.Updates.Versions.html
