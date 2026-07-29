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

## Oracle modern feature follow-up

The current Oracle 23ai/26ai scope includes native JSON, Boolean and Vector
types, AI Vector Search SQL and indexes, SQL macros, scalable and session
sequences, single-column data use case domains, schema annotations, supported
existence clauses, JSON relational duality view DDL, and the corresponding
metadata readers where Oracle exposes suitable catalog views.

The following work is intentionally deferred:

- JSON relational duality view logical-replication DDL and metadata
- Oracle 26ai duality-view table and column detail metadata from
  `ALL_JSON_DUALITY_VIEW_TABS` and related views
- structured modeling of duality-view GraphQL fields, nested objects,
  generated or hidden fields, flex columns and field-level directives
- broader schema-annotation coverage beyond the currently supported table,
  column, index and domain objects
- existence-clause support for additional Oracle object factories where the
  exact database-version boundary and syntax have been verified
- remaining advanced data use case domain forms listed above
- integration tests against real Oracle 23ai and 26ai instances for DDL,
  metadata permissions and Schema XML/DDL round trips

Duality-view definitions remain in the existing `View.statement` property.
Oracle-only flags and discovered metadata remain in `View.specifics`. A shared
structured model should be introduced only if another database exposes an
equivalent feature or sqlapp needs to transform individual definition nodes.

## Less-covered database dialects

The H2, SAP HANA, Cloud Spanner and Vertica dialects now cover additional
modern scalar types that already fit the shared Schema model. Further features
below need shared modeling or a larger cross-database design and are therefore
deferred.

### H2

- row types and nested row fields
- enum labels as first-class schema objects
- multiset element definitions
- declared aggregate definitions and Java alias implementation details
- compatibility-mode-specific identifiers, types and DDL behavior

### SAP HANA

- `HALF_VECTOR` element precision; the shared vector model currently has no
  half-precision element type
- vector-index metadata round trips and future index algorithms beyond the
  currently supported HNSW DDL
- graph workspaces and knowledge-graph objects
- JSON document collections as objects distinct from relational tables
- HANA Cloud fuzzy-search index search-mode metadata round trips from
  `M_FUZZY_SEARCH_INDEXES`; DDL uses the existing full-text index model
- workload classes, remote sources and virtual tables

### Cloud Spanner

- interleaved-table parent, `ON DELETE` behavior and key ordering
- change streams and their tracked-object configuration
- property graphs, graph node/edge tables and labels
- search indexes, vector indexes and embedding options
- locality groups, placement keys and table/index locality
- named schemas for PostgreSQL-dialect databases versus GoogleSQL databases
- `STRUCT`, `PROTO`, named enum and graph value definitions
- generated UUID primary-key strategies
- generated identity behavior that does not map to the shared identity
  properties or Spanner-specific options

### Vertica

- projections, superprojections, segmentation, sort order and `KSAFE`
- flex tables and their key/value materialization
- complex `ARRAY`, `SET`, `MAP` and `ROW` element/field definitions
- external tables and COPY/parser/reject-data configuration
- text indexes and user-defined transform or analytic functions
- storage policies, resource pools and fault-group topology

### Shared design requirements

Before implementing these items, compare equivalent concepts in the other
supported databases and add only reusable concepts to `sqlapp-core`. Vendor
syntax that does not affect object identity may remain in dialect-specific
`specifics`, but parent/child references, rename-sensitive column references,
ordered keys, nested fields and dependency relationships need typed Schema
objects with XML round-trip tests.
