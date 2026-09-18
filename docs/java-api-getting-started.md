# Java API getting started

This guide shows the smallest direct Java API workflow: build a Schema model,
save and load it as XML, resolve the database dialect, and generate DDL. It is
intended for applications and build tools that need finer control than the
Gradle tasks.

sqlapp requires Java 21. The examples use sqlapp `0.80.0`; keep all sqlapp
modules on the same version.

## Add the dependencies

For Schema model and XML operations only, add `sqlapp-core`:

```groovy
dependencies {
    implementation 'com.sqlapp:sqlapp-core:0.80.0'
}
```

For database metadata or database-specific SQL, also add the matching dialect
and JDBC driver. For PostgreSQL:

```groovy
dependencies {
    implementation 'com.sqlapp:sqlapp-core:0.80.0'
    runtimeOnly 'com.sqlapp:sqlapp-core-postgres:0.80.0'
    runtimeOnly 'org.postgresql:postgresql:<jdbc-driver-version>'
}
```

The dialect module and JDBC driver serve different purposes. The dialect
provides sqlapp's metadata and SQL behavior; the driver provides the JDBC
connection. See [Published artifacts and dependency selection](artifacts.md)
for Maven coordinates and the complete dialect list.

## Build a Schema model

`Schema` is the shared representation used by metadata readers, SQL factories,
documentation generators, and migration features. Collections attach their
children to the model, so add columns to their table and tables to their
schema.

```java
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;

Schema schema = new Schema("public");

Table customer = new Table("customer");
customer.getColumns().add(
        new Column("id").setDataType(DataType.BIGINT).setNotNull(true));
customer.getColumns().add(
        new Column("name").setDataType(DataType.VARCHAR).setLength(200));
customer.setPrimaryKey("pk_customer", customer.getColumns().get("id"));

schema.getTables().add(customer);
```

Names, types, constraints, indexes, comments, and database-specific attributes
remain available on the model. Prefer this model over assembling DDL strings
when the required object is representable by sqlapp.

## Save and load Schema XML

Every `DbCommonObject` can write itself as XML. `SchemaUtils.readXml` detects
the root object type and returns it using its generic return type.

```java
import java.io.File;

import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;

File schemaFile = new File("schema.xml");
schema.writeXml(schemaFile);

Schema loaded = SchemaUtils.readXml(schemaFile);
System.out.println(loaded.getTables().get("customer").getColumns().size());
```

`writeXml` and `readXml` also have stream-based overloads. The stream overloads
are useful when the caller controls storage; the file overloads are the
simplest choice for Gradle tasks and command-line tooling.

## Resolve a dialect from a JDBC connection

Resolve the dialect from JDBC metadata when the application already has a
connection. This selects the implementation for the detected database product
and version.

```java
import java.sql.Connection;
import javax.sql.DataSource;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;

DataSource dataSource = createApplicationDataSource();

try (Connection connection = dataSource.getConnection()) {
    Dialect dialect = DialectResolver.getInstance().getDialect(connection);
    System.out.println(dialect.getClass().getName());
}
```

The matching `sqlapp-core-{db}` module must be on the runtime classpath.
Dialect resolvers are discovered through Java's service-loading mechanism. If
no matching resolver is present, sqlapp returns its generic default dialect;
therefore verify the resolved dialect before relying on vendor-specific
metadata or SQL.

The caller owns the `Connection` and should close it, as in the try-with-
resources example. The caller also owns the `DataSource` and closes it when its
particular implementation requires that.

## Generate database-specific SQL

Use the resolved dialect's `SqlFactoryRegistry`. A database object can produce
more than one operation, so the result is a list even for one table.

```java
import java.sql.Connection;
import java.util.List;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;

List<SqlOperation> operations;
try (Connection connection = dataSource.getConnection()) {
    Dialect dialect = DialectResolver.getInstance().getDialect(connection);
    operations = dialect.createSqlFactoryRegistry()
            .createSql(customer, SqlType.CREATE);
}

for (SqlOperation operation : operations) {
    System.out.println(operation.getSqlText());
}
```

Generating SQL does not execute it. Review the returned operations and use the
application's transaction, error-handling, and approval policy before sending
DDL or DML to a database. An empty list means the selected registry has no SQL
factory for that object and SQL type; handle that case explicitly.

## Use command APIs

Add `com.sqlapp:sqlapp-command:0.80.0` when calling export, documentation,
conversion, generation, or migration commands from Java. Command objects expose
typed setters such as `setDataSource(...)` and file/output settings, then run
the same command implementations used by the Gradle plugin.

Command configuration varies substantially by workflow. Use the
[Gradle plugin task reference](gradle-plugin/task-reference.md) to identify the
corresponding command and properties, then consult that command's Java API.
For an ordinary build, the Gradle plugin remains the shorter entry point.

## Production checklist

- Keep sqlapp modules at one version and pin the JDBC driver version.
- Put the matching dialect module on the runtime classpath.
- Resolve the dialect from the actual target connection and reject an
  unexpected result before database-specific work.
- Close caller-created connections, streams, and closeable DataSource
  implementations.
- Treat generated SQL as a plan: inspect it and execute it within an explicit
  transaction and failure policy.
- Do not share a mutable Schema model between concurrent writers. Copy or
  rebuild the model when independent transformations are required.

For runnable task-based examples, see the
[`sqlapp-gradle-example`](https://github.com/satotatsu/sqlapp-gradle-example)
project and the [Gradle plugin guide](gradle-plugin/README.md).
