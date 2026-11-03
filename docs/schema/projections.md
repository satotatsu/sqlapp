# Physical projections

`Projection` is a schema-owned physical representation of a source table. It is
independent of `Table`, `View`, `Mview` and `Index`; it does not describe a logical
derived relation or user row data. Use `Schema.getProjections()` as its high-level
entry point.

```java
Projection projection = new Projection("events_by_day");
projection.setTableSchemaName("app").setTableName("events");
projection.getColumns().add("event_day");
projection.getColumns().add("event_id");
projection.getSortColumns().add("event_day", Order.Asc);
projection.setStatement("SELECT event_day, event_id FROM app.events");
physicalSchema.getProjections().add(projection);
```

The owning Schema identifies the projection, while `tableSchemaName`/`tableName`
identify its source in the same Catalog. `resolveSourceTable()` resolves that
reference without changing the model: missing sources return null, qualified
references search their schema, and an unqualified name must be unique across
the owning catalog. Ambiguity raises an actionable error. A source table is a
reference, not a nested copy of its columns/data. Current source semantics cover
one anchor table; multi-input projection dependencies are not inferred from SQL.

`columns` contains ordered output names; these are optional supplemental metadata
when full executable DDL is available. `sortColumns` contains ordered names and
optional direction/null ordering. Column names are represented with the shared
`ReferenceColumn` model; the initial projection API does not introduce a second
complete typed-column model. `statement` contains SELECT text, and `definition`
contains the full executable CREATE DDL. Vendor encoding/segmentation/redundancy
design can be retained in `specifics`; the common model assigns no portable
meaning to vendor values such as KSAFE or buddy names. Node-specific storage and
runtime observations must not become extra schema objects.

Projection participates in Schema/Catalog XML, standalone XML, cloning, equality,
diffs, object filters and collection traversal. New optional XML uses
`<projections><projection ...>...</projection></projections>`. Existing documents
without projections retain their meaning; empty projection collections are
omitted by Schema serialization. Software predating this extension cannot be
assumed to retain the new XML elements.

CREATE currently requires `definition`. It is passed through as authoritative
vendor DDL; the shared factory does not synthesize physical layout from SELECT
text. Schema/Catalog CREATE emits projection definitions after source tables,
including sources owned by another schema. This is not a general SQL dependency
parser: the caller must preserve any additional dependencies embedded in vendor
DDL. No portable DROP/ALTER projection syntax is provided in the common factory.
Vertica provides named DROP and rename-only ALTER from an original/target diff;
physical layout changes fail rather than silently triggering DROP/CREATE.

Vertica 11.1.1+ reader paths bulk-read named projection/column metadata. Repeated
node rows are collapsed by projection schema/name, while differently named buddy
projections remain distinct named catalog objects. Base names remain specifics;
they are not guessed into a new logical grouping. Column type/encoding metadata
is supplemental specifics, and sort position is retained independently of output
column position. The query does not export executable DDL; therefore read-only
metadata cannot yet be recreated without separately supplied `definition`.
Older Vertica readers and other dialect readers default to no ProjectionReader.
No new external database access is implied by the model or HTML generation.

For an explicitly authorized Vertica connection, `new VirticaDdlExporter()
.exportObjects(connection, objects)` retrieves the requested set in one
`EXPORT_OBJECTS` query. It returns the complete dependency-preserving vendor
script, uses a bound scope and no server output file, and does not execute its
DDL or change the model. An empty set performs no query. Schema/object names
are required; comma-containing identifiers and routine targets requiring overload
signatures are rejected. The script may include source tables and other
dependencies, so do not copy the entire script into every projection definition.
For automatic per-object assignment use:

```java
VirticaDdlExporter exporter = new VirticaDdlExporter();
String completeScript = exporter.exportProjectionDefinitions(
    connection, physicalSchema.getProjections());
// Projection.definition now contains only its matched CREATE statement.
// Keep completeScript separately: dependencies are not copied into each object.
```

For a previously saved script use `applyProjectionDefinitions(script, projections)`;
this offline method never opens a connection. Both paths validate the entire
target set before assigning any definition. Missing, duplicate, ambiguous or
malformed candidate definitions leave all existing definitions unchanged.
Schema-qualified CREATE names are mandatory. Catalog qualifiers, when present,
must match target catalog identity; schema/name matches without a catalog must
identify one target. Matching follows Vertica's case-insensitive identifier rules,
including double-quoted names, and never maps a base-name hint to a missing buddy.
Column-list hints and SELECT text are preserved; each definition is separately
terminated outside trailing line comments. The lexer validates the CREATE header,
not the complete SQL grammar or server-side semantics.
Vertica requires the projection and anchor table to share a schema; the common
model's broader source-reference capabilities do not override vendor restrictions.

Session-context commands and follow-up ALTER PROJECTION statements are rejected
because discarding them could change recreation behavior. Dependencies, other
objects and MARK_DESIGN_KSAFE remain in the complete script, not each definition.
This JDBC path has mock coverage and a successful Vertica 25.1.0-0 live run.

## Full metadata reads

On Vertica 11.1.1+ reader paths, `ProjectionReader.getAllFull(connection)` and
`loadFull(connection, schema)` now acquire definitions automatically. The normal
`getAll`/`load` paths retain metadata-only behavior. Full reading uses one bulk
catalog query and one scoped EXPORT_OBJECTS query for the filtered result set;
there is no per-projection SELECT. Empty/fully excluded sets do not export.
Missing or ambiguous CREATE definitions fail the full read rather than returning
apparently recreatable metadata. Schema population occurs only after definition
assignment succeeds, preserving existing Schema contents on export/mapping failure.
This adds an export query to existing full reads on these Vertica paths; older
resolver paths and other dialects are unchanged.

The full-reader path is locally verified with JDBC mocks. Real qualification is
in `VirticaProjectionIntegrationTest`, excluded from the normal `test` task and
run only through `:sqlapp-core-virtica:projectionIntegrationTest` after explicit
target, credential and cleanup authorization. It requires a loopback JDBC URL
and creates a new random `sqlapp_projection_it_*` schema, table and projection;
it reads, exports, XML-round-trips, drops/recreates the projection and removes
only that newly created schema with CASCADE. On 2026-11-03 this test passed on
a dedicated local Vertica 25.1.0-0 single-node server, including quoted names,
full-schema reads and rename. Test schemas, container and anonymous volume were
removed afterward. An expanded run also passed single-node hash-segmented/KSAFE 0
projections with RLE encoding and independent sort order. Multi-node buddy/KSAFE
1+ layouts and older versions remain unverified against a real server.

HTML documentation adds a Projections menu, list and detail pages, schema-detail
links and source-table links. Details show output/sort columns, SELECT/full DDL,
specifics and statistics, with HTML escaping. An unresolved source is displayed
as text without a dangling source link. Projections do not become ER entities or
appear as duplicate tables/materialized views.
