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
| Continue dialect implementation and verification | [Dialect enhancement continuation](dialect-enhancement-continuation.md) |
| Read and recreate PostgreSQL-only schema objects | [PostgreSQL schema objects](postgresql-schema-objects.md) |
| Review Oracle metadata, DDL coverage, and remaining limitations | [Oracle dialect coverage](oracle-dialect-parity.md) |
| Read and recreate SQLite schema objects | [SQLite schema objects](sqlite-schema-objects.md) |
| Review H2 metadata, DDL/DML and native execution coverage | [H2 dialect coverage](h2-dialect-parity.md) |
| Review HSQLDB metadata and DDL recreation | [HSQLDB dialect coverage](hsqldb-dialect-parity.md) |
| Validate Oracle assessment against a source database | [Oracle assessment verification](oracle-assessment-verification.md) |
| Review or rerun JDBC tree real-engine tests | [JDBC tree verification](jdbc-tree-data-real-db-verification.md) |

Follow the repository [contribution rules](../../CONTRIBUTING.md).
Consult the [compatibility matrix](../compatibility.md) for database version
boundaries and real-engine evidence. Real-database tests require an explicitly
authorized target and credentials.
