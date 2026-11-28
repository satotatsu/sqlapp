---
layout: home
title: Your databases. One unified framework.
description: Manage database schemas, migrate related data with verification, and automate SQL workflows through Gradle and Java.
hero:
  name: sqlapp
  text: Your databases. One unified framework.
  tagline: Database Migration, Schema Management and Data Engineering Framework.
  actions:
    - theme: brand
      text: Get started with Gradle
      link: /start
    - theme: alt
      text: Explore live demos
      link: /demos
    - theme: alt
      text: GitHub
      link: https://github.com/satotatsu/sqlapp
features:
  - title: Database migration
    details: Plan cross-database transfers, review type mappings, move related rows and inspect verification results. Guarantees depend on the selected workflow.
    link: /docs/migration/README
  - title: Schema management
    details: Capture metadata in a shared Schema model and XML. Review differences, generate dialect SQL and manage versioned changes.
    link: /docs/schema/README
  - title: Database documentation
    details: Share browsable HTML, table and column details, DDL, and clickable SVG ER diagrams generated from one saved model.
    link: /docs/schema/html-documentation
  - title: Data Import/Export
    details: Exchange files and database rows, with explicit format conversion and transaction controls.
    link: /docs/gradle-plugin/file-transfer
  - title: Data generation
    details: Build relational test datasets using rule templates, lookups and JDBC batch insertion.
    link: /docs/data/test-data-generation
  - title: Data engineering
    details: Define tables and views as SQL models in DuckDB, order their dependencies, and run unit and data tests.
    link: /docs/gradle-plugin/migration-models
  - title: Gradle automation
    details: Make metadata export, SQL generation, documentation and migration repeatable Gradle tasks. Embed Java APIs when you need custom integration.
    link: /docs/gradle-plugin/README
  - title: Desktop GUI
    details: Review schema comparisons, migrations and data transfer plans in the Swing migration assistant. Review database effects before execution.
    link: /sqlapp-swing/README
---

## Start with an actual result

[Browse the generated HTML, ER diagrams, change SQL and migration verification reports](/demos).
The customer/order examples use fictional data and require no external database.

## Choose your database

[Supported databases, version boundaries and verification evidence](/docs/compatibility#database-dialect-matrix)
are maintained in one Compatibility Matrix. An implemented dialect is not a claim that every server version or migration path has been tested.

## One model across your workflow

Start with the [Gradle guide](/docs/gradle-plugin/getting-started), then explore
the [documentation](/docs/README) or [Java API](/docs/getting-started/java-api).
See [artifacts and distribution](/docs/artifacts) for dependency selection.
The repository targets Java 25. Published release requirements may differ; use the documentation matching your selected release.

sqlapp is open source under the [license](/LICENSE). Feature-specific limits and experimental database variants are documented in the guides and matrix.
