# Schema model

The sqlapp Schema model is the canonical representation shared by metadata
readers, XML files, SQL factories, documentation generation, normalization,
and migration features. Build or modify this model when an operation should be
portable across database dialects.

The model is provided by `com.sqlapp:sqlapp-core`. See
[Java API getting started](java-api-getting-started.md) for dependency setup
and [Published artifacts](artifacts.md) when live JDBC or database-specific SQL
also requires a dialect module.

## Object hierarchy

The common relational hierarchy is:

```text
Catalog
└── Schema
    ├── Table
    │   ├── Column
    │   ├── Constraint
    │   ├── Index
    │   └── Row data (when loaded)
    ├── View and materialized view
    ├── Sequence
    ├── Routine
    └── other schema objects
```

A workflow can use the smallest meaningful root. A single-schema application
may start with `Schema`; a metadata snapshot containing multiple schemas
normally uses `Catalog`. Schema XML preserves the selected root type.

Add objects through their owning collections. The collection establishes the
parent relationship used for qualified names, constraint resolution, XML, and
SQL generation.

```java
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;

Catalog catalog = new Catalog("application");
Schema schema = new Schema("public");
Table customer = new Table("customer");

catalog.getSchemas().add(schema);
schema.getTables().add(customer);
```

Construct a child, populate it, and add it to one owner. Do not reuse the same
mutable child instance in multiple parent collections; create or clone an
independent model when two workflows need separate ownership.

## Tables and columns

Columns use the database-neutral `DataType` enum for types that sqlapp can
represent across dialects. Length, precision, scale, nullability, identity,
default, remarks, and dialect-specific details are separate properties.

```java
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

Table orders = new Table("orders");

Column orderId = new Column("order_id")
        .setDataType(DataType.BIGINT)
        .setNotNull(true);
Column customerId = new Column("customer_id")
        .setDataType(DataType.BIGINT)
        .setNotNull(true);
Column description = new Column("description")
        .setDataType(DataType.VARCHAR)
        .setLength(500);

orders.getColumns().add(orderId);
orders.getColumns().add(customerId);
orders.getColumns().add(description);
```

Add columns before constraints and indexes that reference them. Reuse the
column instances retrieved from `table.getColumns()` when creating those
references; this keeps names and parent relationships consistent.

Collections support iteration and name lookup:

```java
Column id = orders.getColumns().get("order_id");

for (Column column : orders.getColumns()) {
    System.out.println(column.getName());
}
```

Identifier case and quoting rules differ between databases. Preserve the names
reported by metadata when round-tripping an existing database, and let the
selected dialect handle SQL identifier quoting.

## Primary and unique constraints

`Table.setPrimaryKey` is the shortest way to create a primary-key constraint:

```java
orders.setPrimaryKey("pk_orders", orderId);
```

For a composite key, pass columns in key order:

```java
orders.setPrimaryKey(
        "pk_orders",
        orders.getColumns().get("tenant_id"),
        orders.getColumns().get("order_id"));
```

Use the constraint collection for another unique constraint:

```java
orders.getConstraints().addUniqueConstraint(
        "uq_orders_external_id",
        orders.getColumns().get("external_id"));
```

The referenced columns must already belong to the table. Constraint names may
be optional in some databases, but stable explicit names make generated diffs
and DDL easier to review.

## Foreign keys

Create both tables and columns before adding their relationship. The child
columns and referenced parent columns must be in matching order.

```java
Table customer = schema.getTables().get("customer");
Table orderTable = schema.getTables().get("orders");

orderTable.getConstraints().addForeignKeyConstraint(
        "fk_orders_customer",
        orderTable.getColumns().get("customer_id"),
        customer.getColumns().get("customer_id"));
```

Composite foreign keys use arrays:

```java
orderTable.getConstraints().addForeignKeyConstraint(
        "fk_orders_account",
        new Column[] {
                orderTable.getColumns().get("tenant_id"),
                orderTable.getColumns().get("account_id")
        },
        new Column[] {
                account.getColumns().get("tenant_id"),
                account.getColumns().get("account_id")
        });
```

Do not rely on a short table name when it is ambiguous across schemas. sqlapp
resolves relationships using catalog, schema, table, and column identity where
that information is available.

## Indexes

Add an index with references to columns already owned by the table:

```java
import com.sqlapp.data.schemas.Index;

orders.getIndexes().add(new Index(
        "idx_orders_customer",
        orders.getColumns().get("customer_id")));
```

Indexes and unique constraints are different model objects. Use a unique
constraint for a relational uniqueness rule and an index for an access path or
database-specific index behavior. A dialect may implement them with related
DDL, but keeping the intended object type improves metadata comparison.

## Find tables from different roots

Use the direct owning collection when the root type is known:

```java
Table table = schema.getTables().get("orders");
Schema publicSchema = catalog.getSchemas().get("public");
```

`Schema.getTable(name)` also checks tables, materialized views, and views in
that order. Use `getTables()` when the caller specifically requires a physical
table.

For code that accepts several XML root types, normalize them to a table list:

```java
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.SchemaUtils;

DbCommonObject<?> root = SchemaUtils.readXml("schema.xml");
for (Table table : SchemaUtils.toTables(root)) {
    System.out.println(table.getName());
}
```

`SchemaUtils.toTables` handles `Catalog`, `SchemaCollection`, `Schema`,
`TableCollection`, and `Table`. Other root types produce an empty list.

## XML round trips

Every `DbCommonObject` can write itself to a file, stream, or writer:

```java
import java.io.File;

File file = new File("schema.xml");
catalog.writeXml(file);

Catalog loaded = SchemaUtils.readXml(file);
```

The reader detects the XML root object type. Assign it to the expected type
when the file contract is known, or read it as `DbCommonObject<?>` and inspect
the type when a tool accepts several roots.

XML is the interchange boundary used by many Gradle tasks. Keep a reviewed XML
snapshot when documentation or SQL generation must be reproducible without a
new database connection. See
[Schema XML, SQL, and HTML tasks](gradle-plugin/schema-sql-and-html.md).

## Dialects and SQL generation

The model describes database objects; a `Dialect` supplies product/version
behavior. Resolve the dialect from the target JDBC connection and generate SQL
through its `SqlFactoryRegistry`:

```java
Dialect dialect = DialectResolver.getInstance().getDialect(connection);
List<SqlOperation> operations = dialect.createSqlFactoryRegistry()
        .createSql(orders, SqlType.CREATE);
```

SQL generation can return multiple operations and does not execute them.
Review all operations before execution. The complete imports and connection
ownership rules are in [Java API getting started](java-api-getting-started.md#generate-database-specific-sql).

## Mutation and concurrency

Schema objects and their collections are mutable. Build and transform a model
within one controlled workflow. Do not let concurrent writers mutate the same
model. When two operations require independent changes, create separate models
or clone the relevant objects before modification.

When changing an existing table, consider every reference to the changed
column: primary and unique constraints, foreign keys, indexes, partitioning,
and database-specific properties. Prefer command/model transformation APIs
that update related objects together instead of editing names in isolation.

## Related documentation

- [Java API getting started](java-api-getting-started.md)
- [Schema viewpoints](schema-viewpoints.md)
- [Gradle Schema XML, SQL, and HTML workflows](gradle-plugin/schema-sql-and-html.md)
- [Architecture](architecture.md)
- [Compatibility matrix](compatibility.md)
