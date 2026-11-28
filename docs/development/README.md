# Development and verification

[Documentation index](../README.md)

| Goal | Guide |
|---|---|
| Understand modules and dependency direction | [Architecture](architecture.md) |
| Set up Java 21 and run focused checks | [Building and testing](build-and-test.md) |
| Check documentation links and navigation offline | [Documentation checks](build-and-test.md#documentation-checks) |
| Refresh the generated product previews | [Demo previews](build-and-test.md#refresh-readme-demo-previews) |
| Check copied HTML and SVG navigation | [Generated example checks](build-and-test.md#check-the-saved-gradle-html-example) |
| Review planned work | [Roadmap](roadmap.md) |
| Review migration UX and mapping priorities | [Data migration plan](data-migration-plan.md) |
| Review SELECT-defined model design and follow-up work | [Migration models design](migration-models-design.md) |
| Continue dialect implementation and verification | [Dialect enhancement continuation](dialect-enhancement-continuation.md) |
| Review shared SQL generation fixes and verification limitations | [SQL generation review](sql-generation-review.md) |
| Verify data type resolution, ranges and real-engine recreation | [Dialect data types](dialect-data-types.md) |
| Review time zone value handling and the ZonedDateTime memo | [Time zone values](time-zone-values.md) |
| Read and recreate PostgreSQL-only schema objects | [PostgreSQL schema objects](postgresql-schema-objects.md) |
| Review Firebird metadata, version boundaries and DDL recreation | [Firebird dialect coverage](firebird-dialect-parity.md) |
| Compare MySQL metadata, DDL/DML coverage, and remaining gaps | [MySQL dialect comparison](mysql-dialect-parity.md) |
| Snapshot native MySQL/MariaDB DDL and recreate with saved context | [MySQL native recreation](mysql-native-recreation.md) |
| Review Oracle metadata, DDL coverage, and remaining limitations | [Oracle dialect coverage](oracle-dialect-parity.md) |
| Read and recreate SQLite schema objects | [SQLite schema objects](sqlite-schema-objects.md) |
| Review H2 metadata, DDL/DML and native execution coverage | [H2 dialect coverage](h2-dialect-parity.md) |
| Review HSQLDB metadata and DDL recreation | [HSQLDB dialect coverage](hsqldb-dialect-parity.md) |
| Validate Oracle assessment against a source database | [Oracle assessment verification](oracle-assessment-verification.md) |
| Review or rerun JDBC tree real-engine tests | [JDBC tree verification](jdbc-tree-data-real-db-verification.md) |
| Track open migration and review items | [Migration TODO](TODO.md) |
| Review SQL splitter quoting and session policy | [SQL splitter review](sql-splitter-review.md) |
| Review shared utility mutation and conversion rules | [Shared utility behavior](utilities.md) |
| Review the default value migration proposal | [Default value migration](default-value-migration.md) |
| Review Access complex column design and pending verification | [Access complex columns](access-complex-columns.md) |
| Review Db2 LUW metadata and DDL recreation | [Db2 dialect coverage](db2-dialect-parity.md) |
| Check Db2 dialect completion gates | [Db2 dialect completion](db2-dialect-completion.md) |
| Review the Db2 / PostgreSQL parity audit | [Db2 / PostgreSQL audit](db2-postgres-final-audit.md) |
| Review MySQL SQL generation regression fixes | [MySQL SQL generation fixes](mysql-independent-sql-fixes.md) and [stage-one fixes](mysql-stage-one-fixes.md) |
| Review MySQL bulk UPSERT match keys | [MySQL bulk UPSERT match keys](mysql-bulk-upsert-match-keys.md) |
| Review MySQL LOAD DATA safety | [MySQL LOAD DATA safety](mysql-load-data-safety.md) |

Follow the repository [contribution rules](../../CONTRIBUTING.md).
Consult the [compatibility matrix](../compatibility.md) for database version
boundaries and real-engine evidence. Real-database tests require an explicitly
authorized target and credentials.
