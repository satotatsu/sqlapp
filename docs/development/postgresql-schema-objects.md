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

Generic ALTER rejects a changed PostgreSQL-only payload rather than silently
ignoring it or guessing a destructive replacement. Use the existing specialized
builders or explicit reviewed object DDL for such changes. CREATE statements
and `PostgresSchemaObjects.dropSql(owner)` are available independently; no
CASCADE or execution is automatic. DROP order is reversed within each stored
owner; dependencies across Schema/Catalog owners must be coordinated by the
caller.

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
invalid payloads, stale schema names, explicit ALTER rejection and versioned
catalog SQL. Disposable PostgreSQL 18.4 and 14.23 integration tests read objects,
serialize/restore XML, recreate DDL, compare reread definitions and exercise
the normal Catalog Reader/Factory path. They include extension-member exclusion,
foreign inheritance and views, table/column grants, publication filtering and
schema membership. Prepared SELECT counts are fixed at seven for each requested
schema/database object set on these versions, independent of object count.

```bat
.\gradlew.bat :sqlapp-core-postgres:test
.\gradlew.bat :sqlapp-core-dialect-test:dockerTest --tests "*PostgresSchemaObjectsRoundTripTest"
.\gradlew.bat :sqlapp-core-dialect-test:dockerTest --tests "*PostgresLegacyMetadataRegressionTest"
```

Server catalog references: [Collation](https://www.postgresql.org/docs/18/catalog-pg-collation.html),
[Publication](https://www.postgresql.org/docs/18/catalog-pg-publication.html),
[Subscription](https://www.postgresql.org/docs/18/catalog-pg-subscription.html),
[Policy](https://www.postgresql.org/docs/18/catalog-pg-policy.html).
