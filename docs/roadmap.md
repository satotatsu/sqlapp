# Roadmap

This document records deferred features that need cross-database design or a
larger extension of the shared Schema model. It is not a release commitment.

## Constraint lifecycle state

`NotNullConstraint` represents a named `NOT NULL` constraint in the
shared Schema model. PostgreSQL 18 DDL, metadata, XML, and HTML documentation
use this property.

Validation and trust state still needs a cross-database design. PostgreSQL has
`NOT VALID` and `convalidated`, Oracle has ENABLE/DISABLE with
VALIDATE/NOVALIDATE, SQL Server distinguishes disabled and untrusted
constraints, and DB2 exposes enforcement and validation-related states.

Before adding a shared property:

1. Separate enabled/enforced, validated/trusted, and inherited state.
2. Apply the model consistently to check, foreign-key, unique, and named
   `NOT NULL` constraints.
3. Define XML defaults that preserve existing documents.
4. Add HTML rendering only when a non-default state exists.
5. Add per-dialect metadata and DDL mappings with explicit version boundaries.

Named `NOT NULL` inheritance and PostgreSQL's catalog validation state are
retained by `NotNullConstraint.noInherit` and
`NotNullConstraint.validated`. These properties are intentionally scoped to
the named `NOT NULL` object; a common lifecycle contract for every constraint
type remains deferred.

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

Implementation status, verification commands, and the ordered continuation
plan are recorded in `docs/dialect-enhancement-continuation.md`.

### H2

- row types and nested row fields
- enum labels as first-class schema objects
- multiset element definitions
- declared aggregate definitions and Java alias implementation details
- compatibility-mode-specific identifiers, types and DDL behavior

### SAP HANA

- `HALF_VECTOR` element precision; the shared vector model currently has no
  half-precision element type
- future vector-index algorithms beyond the currently supported HNSW DDL and
  `VECTOR_INDEXES` metadata
- graph workspaces and knowledge-graph objects
- JSON document collections as objects distinct from relational tables
- HANA Cloud fuzzy-search index search-mode metadata round trips from
  `M_FUZZY_SEARCH_INDEXES`; DDL uses the existing full-text index model
- workload classes, remote sources and virtual tables

### Cloud Spanner

- interleaved-table parent, `ON DELETE` behavior and key ordering
- change streams and their tracked-object configuration
- property graphs, graph node/edge tables and labels
- search-index partitioning, ordering and interleaving definitions; basic
  TOKENLIST-column, STORING, WHERE and sharding-option DDL uses the existing
  column and full-text index model
- mutable vector-index operations; CREATE and metadata recovery use the
  existing vector-index model and Spanner-specific tree options
- locality-group objects and placement keys; table, column and index
  locality/storage options are preserved in dialect specifics
- named schemas for PostgreSQL-dialect databases versus GoogleSQL databases
- `STRUCT`, `PROTO`, named enum and graph value definitions
- generated UUID primary-key strategies
- generated identity behavior that does not map to the shared identity
  properties or Spanner-specific options
- sequence ALTER operations and dependency-aware sequence default-expression
  modeling; CREATE and metadata round trips use the existing Sequence model
- view dependency extraction and structured security semantics shared with
  other databases; Spanner CREATE and metadata round trips currently preserve
  the security type in dialect specifics

### Vertica

- projections, superprojections, segmentation, sort order and `KSAFE`
- flex tables and their key/value materialization
- complex `ARRAY`, `SET`, `MAP` and `ROW` element/field definitions
- external tables and COPY/parser/reject-data configuration
- text indexes and user-defined transform or analytic functions
- storage policies, resource pools and fault-group topology
- temporary-view lifetime and structured schema-privilege inheritance
  (`INCLUDE/EXCLUDE SCHEMA PRIVILEGES`) shared with other databases
- exact IDENTITY start-value round trips after rows have been generated;
  Vertica exposes the current distributed value but not the original start
  value in its sequence catalog

### Shared design requirements

Before implementing these items, compare equivalent concepts in the other
supported databases and add only reusable concepts to `sqlapp-core`. Vendor
syntax that does not affect object identity may remain in dialect-specific
`specifics`, but parent/child references, rename-sensitive column references,
ordered keys, nested fields and dependency relationships need typed Schema
objects with XML round-trip tests.
