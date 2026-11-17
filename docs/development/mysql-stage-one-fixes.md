# MySQL stage-one regression fixes

This change fixes three independent paths in `sqlapp-core-mysql`:

- MySQL supplies `DUAL` as its dummy table. Generated conditional INSERT and
  older-version INSERT_ROWS templates no longer use the generic `(VALUES(0))`
  dummy table. The shared core default is unchanged.
- The geometry JDBC handler binds SQL NULL before invoking the WKB encoder.
  Non-null input is still converted and encoded normally; a non-null value
  that converts to null is rejected instead of being silently bound as NULL.
- Bulk UPSERT resolves a MySQL database and table name instead of generating
  a three-part `catalog.schema.table` target. The same resolved target is used
  for staging SELECT, INSERT and update-only execution.

## Database identity rules

| Model catalog | Model schema | Target |
| --- | --- | --- |
| `def` (INFORMATION_SCHEMA placeholder) | `app` | `app.table` |
| absent | `app` | `app.table` |
| `app` (JDBC database catalog) | absent | `app.table` |
| `app` | `app` | `app.table` |
| absent | absent | `table`, using the connection's selected database |
| `def` | absent | `def.table`; a real database named `def` is preserved |
| conflicting non-placeholder names | present | Error before transaction or SQL execution |

Names retain their case and use the existing dialect quoting. The model is not
mutated. Schema/catalog comparison is exact rather than assuming a server's
case-folding configuration. This does not change the shared core naming rules.

Public API signatures, configuration formats, dependencies and resolver version
boundaries are unchanged. Previously invalid three-part targets become valid
two-part targets; conflicting database identifiers now produce an actionable
error. MariaDB and Aurora MySQL inherit the fixes. The previous UUID, TINYBLOB,
empty-update MERGE and temporary-table DELETE fixes are unchanged.

## Regression tests

Tests were added before implementation. The original code reproduced all three
failures, including the inherited MariaDB and Aurora paths. The new tests cover:

- MySQL base 5.x, 5.6.4, 5.6.5, 5.7, 8.0, 8.0.1, 8.4 and 9.0 dummy-table SQL,
  INSERT_ROWS template evaluation and bind counts.
- Geometry NULL binding, invalid non-null input, and non-null WKB/SRID decoding
  by column index and label.
- Bulk target resolution through the executor's staging and final SQL, normal
  and update-only UPSERT, catalog-only models, quoted database names, unchanged
  model identity, and conflicting identifiers rejected before SQL execution.
- Representative MariaDB 10.x/11.x/12.x and Aurora 8.0/8.4 inherited behavior.

With Java 25.0.3 and Gradle Wrapper 9.8.1, the following command passed on
2026-11-17:

```powershell
.\gradlew.bat :sqlapp-core-mysql:test :sqlapp-core-mariadb:test :sqlapp-core-aurora-mysql:test --offline --console=plain
```

Results: MySQL 355 tests, MariaDB 109 tests, Aurora MySQL 25 tests; no failures,
errors or skipped tests. Execution also used an external temporary Gradle init
script to export runtime classpaths for the JDBC probes; no build configuration
or dependency versions were changed.

## Live database verification

JDBC probes execute the generated INSERT_SELECT_NOT_EXISTS and INSERT_ROWS SQL,
bind geometry NULL and a point with SRID 4326, and perform bulk insert/update and
update-only UPSERT from a model read by the actual TableReader. The metadata
model has catalog `def` and a quoted database name; catalog-only models are also
executed. Probes assert stored rows and geometry SRID, rather than only checking
SQL text.

All three probe groups passed with the following matrix:

| Dialect | Server | JDBC driver |
| --- | --- | --- |
| MySQL | 5.7.44, 8.0.46, 8.4.11 | MySQL Connector/J 9.7.0 |
| MariaDB | 10.5.29, 11.4.12, 11.8.8 | MariaDB Connector/J 3.5.9 |
| Aurora MySQL 8.0 / 8.4 | Upstream MySQL 8.0.46 / 8.4.11 | MySQL Connector/J 9.7.0 |

Only existing local images were used, without pulling dependencies or images.
The temporary databases were published on loopback-only ephemeral ports, had
no persistent data volumes, and were removed after execution. No external or
production database was used.

Actual Aurora service behavior, MySQL 9.x and older 5.x servers, and MariaDB
versions outside the live matrix remain unverified on real servers. Upstream
MySQL execution using an explicit Aurora dialect is compatibility evidence,
not an actual Aurora cluster test. Core and command module suites were not
rerun because their source and APIs were not changed.
