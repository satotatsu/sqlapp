# PostgreSQL schema-object metadata and recreation

[Development index](README.md) · [Roadmap](roadmap.md)

The PostgreSQL Catalog/Schema readers retain additional database objects that
the shared Schema model has no dedicated collection for. They are exposed as
typed `PostgresSchemaObject` records through `PostgresSchemaObjects.get(owner)`.
Their authoritative complete DDL and supplemental attributes are stored in the
existing `specifics` entry `postgres.schemaObjects.v1` of the owning Schema or
Catalog. Existing Schema XML serialization, cloning and comparison preserve
the versioned payload. No shared XML elements, dependencies or public APIs were
removed or changed.

This is a PostgreSQL-local representation, not a new cross-database collation,
policy or replication model. Existing shared model classes remain the primary
representation for tables, routines, domains, types and other modeled objects.

## Object coverage

| Object | Owner | Retained definition |
| --- | --- | --- |
| Extension | Catalog | Name, installation schema, version, source owner and comment |
| Collation | Schema | Provider, libc locale categories or ICU/builtin locale, determinism, rules, version, owner and comment |
| Foreign-data wrapper | Catalog | Handler, validator, options, owner, comment and grants |
| Foreign server | Catalog | Wrapper reference, type/version, options, owner, comment and grants |
| User mapping | Catalog | User/PUBLIC, server and options, with external secret substitution |
| Foreign table | Schema | Columns/types, defaults/generated expressions, collations, nullability, checks, inheritance/partition reference, column/table options, owner, comments and table/column grants |
| RLS policy | Schema | Table-qualified identity, command, roles, permissive/restrictive mode, USING/WITH CHECK expressions and comment |
| Row-security state | Schema | Table ENABLE/DISABLE and FORCE/NO FORCE state |
| Extended statistics | Schema | Server-deparsed statistics definition, target, owner and comment |
| Publication | Catalog | ALL TABLES, explicit tables, column lists/row filters, schema membership and publication options |
| Subscription | Catalog | Publications, slot, supported version-specific options, owner/comment and original enabled state; connection supplied externally |

The new reader starts at PostgreSQL 9.1 for extensions, collations and foreign
objects, 9.5 for policies, and 10 for logical replication and extended
statistics. Grants use `aclexplode` from 9.6; grant recreation before 9.6 is
outside this new extension. Older supported dialect paths retain their prior
behavior. Automatic extra catalog reads run for PostgreSQL itself; derived
products keep their existing metadata behavior.

Catalog queries isolate the 10/12/15/16/17/18 collation differences, 15+
publication column filters/schema membership, 18 generated-column publication,
and the 14/15/16/17 subscription additions. PostgreSQL 14/15's boolean streaming
field is handled separately from 16+'s character-valued streaming field.

## Common entry point

```java
var dialect = DialectResolver.getInstance().getDialect(connection);
var reader = dialect.getCatalogReader();
String currentCatalog = connection.getCatalog();
reader.setCatalogName(currentCatalog);
var catalog = reader.getAllFull(connection).stream()
    .filter(c -> c.getName().equals(currentCatalog))
    .findFirst().orElseThrow();

var databaseObjects = PostgresSchemaObjects.get(catalog);
var schemaObjects = PostgresSchemaObjects.get(catalog.getSchemas().get("app"));
String xml = catalog.asXml();

var registry = dialect.createSqlFactoryRegistry();
registry.getOptions().setDecorateSchemaName(true);
SqlFactory<Catalog> factory = registry.getSqlFactory(catalog, SqlType.CREATE);
var operations = factory.createSql(catalog);
```

The registered PostgreSQL CREATE factories compose existing shared factories
with the extra definitions. Schemas precede extension installation; collations
precede user types; FDW handlers/validators precede wrappers and servers;
ordinary tables precede inherited foreign tables; foreign tables precede
dependent views; policies/statistics and replication definitions follow their
prerequisites. Native extension members are marked with `postgres.extension`
and are not recreated a second time by Catalog/Schema CREATE.

Standalone Schema CREATE expects database-wide prerequisites such as extensions,
servers and roles to exist. Select a complete Catalog for cross-schema or
database-wide recreation. The reader does not connect to other databases or
remote FDW/publisher endpoints. Database-wide objects belong only to the
currently connected database, even when catalog enumeration returns other names.

## External connection and secret values

Subscription connection strings are not read into the model. FDW option names
`password`, `sslpassword`, `secret` and `token` are replaced with placeholders.
Generation fails with an actionable error when a required external value is
missing. Vendor-specific option names outside this set must be reviewed by the
caller before distributing exported metadata.

```java
var values = new HashMap<String, String>();
for (var object : PostgresSchemaObjects.get(catalog)) {
    if (object.kind() == PostgresSchemaObject.Kind.SUBSCRIPTION) {
        values.put(object.id(), suppliedConnectionInfo);
    }
    if (object.kind() == PostgresSchemaObject.Kind.USER_MAPPING) {
        values.put(object.id() + ":password", suppliedMappingPassword);
    }
}
var factory = (PostgresCreateCatalogFactory)
    registry.getSqlFactory(catalog, SqlType.CREATE);
var operations = factory.createSql(catalog, values);
```

External values are escaped SQL literals and are never written back to Schema
XML. `PostgresSchemaObjects.createSql(object, values)` also allows explicit
generation of an individual object, including custom foreign-table options.
Generated SQL containing supplied secrets must be handled as sensitive output.

Subscriptions are recreated with `connect=false`, `enabled=false`,
`create_slot=false` and `copy_data=false`. The original enabled state is retained
as an attribute. Starting replication, copying data, creating remote slots or
restoring replication progress requires an explicit deployment decision.
Subscription DROP definitions disable the subscription and detach its remote
slot before dropping the local object; they do not clean up remote slots.

## Changes, validation and limits

Complete DDL is authoritative. Moving/renaming a schema does not rewrite opaque
definitions; stale schema identities are rejected. Duplicate identities,
unknown manifest fields/versions, missing definitions and unreadable user-mapping
options fail explicitly. SQL execution errors and privilege errors propagate.

The registered Schema and Catalog ALTER factories now compare objects by identity
and compose their changes with the shared model's table, routine and view changes.
Additions generate CREATE; removals generate the recorded DROP without CASCADE.
Object ordering alone does not produce SQL. Extension members are handled by
their extension, rather than being dropped or created independently.

Supported changes include:

- FDW handler/validator and FDW/server/user-mapping options (ADD, SET, DROP),
  server version, owner, comments and effective table/column privileges.
- Foreign-table columns, types/collations, defaults, NOT NULL, CHECK constraints,
  table/column options and column comments, preserving the foreign-table object.
  Recreating foreign partitions also recreates their native partitioned parent:
  PostgreSQL 10+ table CREATE emits PARTITION BY from the shared partitioning model.
  Metadata retains `postgres.partitionBy` with the server-deparsed partition key
  so expressions, collation and operator classes survive recreation; remove that
  optional specifics value to regenerate a key from edited shared key columns.
- Policy roles, USING and WITH CHECK expressions; changed command/permissiveness
  or removed expressions use DROP/CREATE. RLS changes include ENABLE/DISABLE and
  FORCE/NO FORCE; removing the RLS entry clears both flags.
  Execute policy replacement in one transaction to avoid an intermediate policy gap.
- Statistics targets use ALTER; changed expressions/kinds use DROP/CREATE.
- Extension version UPDATE and installation-schema changes; collation ownership
  and comments. An extension update still requires a server-installed update path.
- Publication explicit table/schema membership, row filters, column lists and
  publish settings. Subscription publication lists use `refresh=false`; supported
  settings and explicitly changed enabled attributes use ALTER without replacing
  the subscription or resetting replication progress.

```java
var factory = (PostgresAlterCatalogFactory)
    registry.getSqlFactory(original, SqlType.ALTER);
var operations = factory.createDiffSql(original.diff(target));
// Optional secrets/connection replacement; the same keys as CREATE:
var operationsWithSecrets = factory.createDiffSql(original.diff(target), values);
```

The Schema ALTER factory has the same external-values overload. Supplying a
subscription connection explicitly requests CONNECTION replacement; an unchanged
placeholder does not require credentials. Supplying a redacted FDW option value
requests SET for that option even when the exported placeholder is unchanged.
Generated SQL must be reviewed and executed by the caller; generation does not
connect to a database. ALTER input follows the reader's deparsed CREATE form;
SQL comments are rejected and quoted strings/identifiers, dollar quotes and nested
expressions are kept opaque during clause comparison.

PostgreSQL has no direct ALTER for collation locale/provider/determinism, server
TYPE/FDW, or a foreign table's server. Such changes fail with the object identity
and required explicit migration, rather than replacing dependent objects.
Foreign inheritance/partition attachment, generated-column expression and column
reordering changes also require an explicit migration. Publication FOR ALL TABLES
cannot be switched in place. Missing target values for previously explicit
publication/subscription options fail rather than guessing a reset. Two-phase
subscription ALTER requires PostgreSQL 18; slot/failover/two-phase changes require
the target subscription to be disabled. A transition to disabled is emitted first.
Failover and two-phase changes have PostgreSQL autocommit/slot prerequisites;
the planner does not infer transaction guarantees or alter remote slots.

CREATE statements and `PostgresSchemaObjects.dropSql(owner)` remain available
independently. Standalone DROP order is reversed within each stored owner;
dependencies across owners must be coordinated by the caller. ALTER orders common
schema, extension, routine, FDW, foreign-table and view dependencies. Additional
dependencies outside the supplied model are enforced by PostgreSQL's RESTRICT
behavior; it does not add CASCADE or synthesize unmodeled dependent objects.

Recreation requires the extension binaries and recorded versions, compatible
locale/ICU providers, referenced roles and appropriate target privileges.
Extension owner is supplemental metadata because PostgreSQL has no ALTER
EXTENSION OWNER statement; execute extension creation as the required owner
when ownership must match. Grant recreation preserves grantees, privileges and
grant options, not the original grantor chain or unrelated target default
privileges. Extension configuration-table data is not part of metadata export.
Dedicated HTML object pages and a portable shared object model remain separate
work; the payload remains accessible through the Schema specifics.

## Verification

Unit tests cover XML/clone retention, identifier identity, external values,
invalid payloads, stale schema names, ALTER differences and immutable-change errors, and versioned
catalog SQL. Disposable PostgreSQL 18.4 and 14.23 integration tests read objects,
serialize/restore XML, recreate DDL, compare reread definitions and exercise
the normal Catalog Reader/Factory path. They include extension-member exclusion,
foreign inheritance and views, table/column grants, publication filtering and
schema membership. ALTER integration tests execute forward and reverse plans,
compare reread metadata, and combine native tables/views with added and removed
foreign tables and collations. PostgreSQL 9.6 and 10 regressions also execute
FDW, foreign-column, policy and RLS ALTER round trips. Prepared SELECT counts are fixed at seven for each requested
schema/database object set on these versions, independent of object count.

```bat
.\gradlew.bat :sqlapp-core-postgres:test
.\gradlew.bat :sqlapp-core-dialect-test:dockerTest --tests "*PostgresSchemaObjectsRoundTripTest"
.\gradlew.bat :sqlapp-core-dialect-test:dockerTest --tests "*PostgresLegacyMetadataRegressionTest"
.\gradlew.bat :sqlapp-core-postgres:test :sqlapp-core-aurora-postgres:test :sqlapp-core-edb-oracle:test :sqlapp-core-alloydb:test :sqlapp-core-yugabyte:test :sqlapp-core-cockroach:test :sqlapp-core-dialect-test:dockerTest --tests "*PostgresSchemaObjectsRoundTripTest" --tests "*PostgresLegacyMetadataRegressionTest" --tests "*PostgresMetadataReaderTest"
```

Server catalog references: [Collation](https://www.postgresql.org/docs/18/catalog-pg-collation.html),
[Publication](https://www.postgresql.org/docs/18/catalog-pg-publication.html),
[Subscription](https://www.postgresql.org/docs/18/catalog-pg-subscription.html),
[Policy](https://www.postgresql.org/docs/18/catalog-pg-policy.html).

ALTER syntax references: [Foreign table](https://www.postgresql.org/docs/18/sql-alterforeigntable.html),
[Publication](https://www.postgresql.org/docs/18/sql-alterpublication.html),
[Subscription 15](https://www.postgresql.org/docs/15/sql-altersubscription.html),
[Subscription 18](https://www.postgresql.org/docs/18/sql-altersubscription.html),
[Extension](https://www.postgresql.org/docs/18/sql-alterextension.html).
