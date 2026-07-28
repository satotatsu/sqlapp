# Roadmap

This document records deferred features that need cross-database design or a
larger extension of the shared Schema model. It is not a release commitment.

## Data use case domains

### Current scope

Oracle Database 23ai supports single-column data use case domains through the
existing `Domain` model.

The Oracle dialect currently generates:

- `CREATE DOMAIN` and `DROP DOMAIN`
- base data type and `STRICT`
- `DEFAULT` and `DEFAULT ON NULL`
- `NOT NULL` and one `CHECK` constraint
- constraint deferrability
- `DISPLAY` and `ORDER` expressions
- object-level schema annotations

Single-column domain definitions and object-level annotations can be loaded
from Oracle metadata when the relevant catalog views are available. Missing
views or privileges produce a warning and leave the remaining metadata intact.

On Oracle 23ai, `Domain` represents a data use case domain. The older Oracle
mapping of `Domain` to `CREATE TYPE ... VARRAY/TABLE OF` is not retained for
that version.

### Deferred scope

The following Oracle features are intentionally deferred:

- multiple-column domains
- flexible domains
- enumerated domains
- domain-column annotations
- multiple named constraints and complete constraint state
- alternative data types for a domain column
- domain association details on table columns
- complete metadata and XML/DDL round trips for multiple-column, flexible and
  enumerated domains
- domain-specific HTML documentation

### Required design work

Before implementing the deferred scope:

1. Compare equivalent concepts in PostgreSQL, DB2, SQL Server and other
   supported databases.
2. Decide which concepts belong in the shared `sqlapp-core` Schema model and
   which remain dialect-specific.
3. Model domain columns, multiple constraints, annotations and flexible-domain
   selection without encoding Oracle SQL as opaque strings.
4. Keep single-column domain configuration concise.
5. Define XML compatibility and rename propagation for every reference between
   domains, domain columns and table columns.
6. Add shared Schema-model tests before adding Oracle metadata and SQL tests.

### Completion criteria

The deferred work is complete when a domain can be read from database
metadata, written to Schema XML, read back, and regenerated as equivalent DDL,
with references surviving object and column renames.

## Oracle AI Database 26ai

`Oracle26ai` has a separate dialect and resolver boundary and currently
inherits the compatible Oracle 23ai behavior.

Before adding 26ai-only SQL:

- register it only in the 26ai dialect or SQL factory registry
- add a 23ai/26ai boundary test
- distinguish features introduced by a 26ai release update when the JDBC
  product version provides enough information
