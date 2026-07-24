# sqlapp

sqlapp is a Java-based database utility library and development tool
supporting schema inspection, SQL generation, documentation generation,
test-data generation, and database migration.

## Features

- Database schema metadata extraction
- SQL and DDL generation
- HTML database documentation with ER diagrams
- Test-data generation
- Database migration
- Schema and data comparison
- Support for multiple RDBMS dialects
- Gradle plugin integration

## Requirements

- Java 21
- Gradle Wrapper included in this repository

## Supported databases

| Database | Module | Supported versions | Test level |
|---|---|---|---|
| PostgreSQL | sqlapp-core-postgres | TBD | Unit / Integration |
| MySQL | sqlapp-core-mysql | TBD | Unit / Integration |
| Oracle | sqlapp-core-oracle | TBD | Unit / Manual |
...

## Modules

| Module | Responsibility |
|---|---|
| sqlapp-core | Database models, JDBC access, metadata and SQL foundations |
| sqlapp-command | Documentation, migration and test-data commands |
| sqlapp-gradle-plugin | Gradle tasks backed by sqlapp-command |
| sqlapp-core-{db} | Database-specific dialect implementation |
| sqlapp-elk-svg | ELK-based SVG ER diagram generation |
| sqlapp-graphviz | Legacy Graphviz ER diagram generation |
| sqlapp-core-test | Shared test utilities |

## Quick start

gradlew build

## Documentation

- Architecture
- Build and testing
- Command development
- Dialect development
- ELK-SVG development
- Contributing

## License
