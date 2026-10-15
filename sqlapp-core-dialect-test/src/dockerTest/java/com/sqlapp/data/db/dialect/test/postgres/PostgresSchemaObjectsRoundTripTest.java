/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test.postgres;

import static org.junit.jupiter.api.Assertions.*;
import java.io.StringReader;
import java.sql.Connection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.testcontainers.postgresql.PostgreSQLContainer;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.postgres.PostgresSchemaObject;
import com.sqlapp.data.db.dialect.postgres.PostgresSchemaObject.Kind;
import com.sqlapp.data.db.dialect.postgres.PostgresSchemaObjects;
import com.sqlapp.data.db.dialect.postgres.metadata.PostgresSchemaObjectReader;
import com.sqlapp.data.db.dialect.test.ReusableTestcontainers;
import com.sqlapp.data.schemas.Catalog;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;

class PostgresSchemaObjectsRoundTripTest {
	@Test
	void readsXmlAndRecreatesPostgres18ObjectsWithoutConnectingSubscribers() throws Exception {
		verify("postgres:18.4");
	}

	@Test
	void readsAndRecreatesPostgres14CatalogShapes() throws Exception {
		verify("postgres:14.23");
	}

	private void verify(String image) throws Exception {
		var postgres = ReusableTestcontainers
				.configure(new PostgreSQLContainer(image).withCommand("postgres", "-c", "wal_level=logical"));
		ReusableTestcontainers.start(postgres);
		try (Connection connection = postgres.createConnection(""); var statement = connection.createStatement()) {
			statement.execute("CREATE SCHEMA \"Object Data\"");
			statement.execute("CREATE EXTENSION postgres_fdw");
			statement.execute("CREATE EXTENSION hstore WITH SCHEMA \"Object Data\"");
			statement.execute("CREATE EXTENSION pg_buffercache WITH SCHEMA \"Object Data\"");
			statement.execute("CREATE FOREIGN DATA WRAPPER \"Custom Wrapper\" NO HANDLER NO VALIDATOR");
			statement.execute(
					"CREATE SERVER \"Remote Server\" TYPE 'test type' VERSION '1' FOREIGN DATA WRAPPER \"Custom Wrapper\" OPTIONS (host 'example.invalid')");
			statement.execute(
					"CREATE USER MAPPING FOR CURRENT_USER SERVER \"Remote Server\" OPTIONS (user 'remote', password 'test-only-secret')");
			statement.execute("GRANT USAGE ON FOREIGN DATA WRAPPER \"Custom Wrapper\" TO PUBLIC");
			statement.execute("GRANT USAGE ON FOREIGN SERVER \"Remote Server\" TO PUBLIC");
			statement.execute("CREATE COLLATION \"Object Data\".\"Sort C\" (provider=libc,locale='C')");
			statement.execute("CREATE TABLE \"Object Data\".items (id integer PRIMARY KEY, amount integer)");
			statement.execute(
					"CREATE FOREIGN TABLE \"Object Data\".\"Remote Items\" (id integer OPTIONS (column_name 'remote_id') NOT NULL, label text COLLATE \"Object Data\".\"Sort C\", amount integer DEFAULT 2, CONSTRAINT positive CHECK (amount>0)) SERVER \"Remote Server\" OPTIONS (table_name 'items')");
			statement.execute("COMMENT ON COLUMN \"Object Data\".\"Remote Items\".label IS '日本語 column'");
			statement.execute("GRANT SELECT ON TABLE \"Object Data\".\"Remote Items\" TO PUBLIC");
			statement.execute("GRANT UPDATE (label) ON TABLE \"Object Data\".\"Remote Items\" TO PUBLIC");
			statement.execute(
					"CREATE FOREIGN TABLE \"Object Data\".\"Child Items\" (extra text) INHERITS (\"Object Data\".items) SERVER \"Remote Server\"");
			statement.execute(
					"CREATE TABLE \"Object Data\".partitioned (id integer, label text) PARTITION BY RANGE (id)");
			statement.execute(
					"CREATE FOREIGN TABLE \"Object Data\".foreign_partition PARTITION OF \"Object Data\".partitioned FOR VALUES FROM (0) TO (10) SERVER \"Remote Server\"");
			statement.execute(
					"ALTER FOREIGN TABLE \"Object Data\".foreign_partition ALTER COLUMN label OPTIONS (ADD column_name 'old_label')");
			statement.execute(
					"CREATE VIEW \"Object Data\".\"Remote View\" AS SELECT id,label FROM \"Object Data\".\"Remote Items\"");
			statement.execute(
					"CREATE POLICY \"Allow Items\" ON \"Object Data\".items AS RESTRICTIVE FOR ALL TO PUBLIC USING (id>0) WITH CHECK (amount>0)");
			statement.execute("ALTER TABLE \"Object Data\".items ENABLE ROW LEVEL SECURITY");
			statement.execute("ALTER TABLE \"Object Data\".items FORCE ROW LEVEL SECURITY");
			statement.execute(
					"CREATE STATISTICS \"Object Data\".\"Item Stats\" (dependencies,ndistinct) ON id,amount FROM \"Object Data\".items");
			statement.execute("ALTER STATISTICS \"Object Data\".\"Item Stats\" SET STATISTICS 123");
			statement.execute(
					"CREATE PUBLICATION \"Item Publication\" FOR TABLE ONLY \"Object Data\".items WITH (publish='insert,update', publish_via_partition_root=true)");
			if (image.contains(":18")) {
				statement.execute(
						"CREATE PUBLICATION \"Filtered Publication\" FOR TABLE ONLY \"Object Data\".items (id,amount) WHERE (id>1) WITH (publish='insert',publish_generated_columns='none')");
				statement.execute("CREATE PUBLICATION \"Schema Publication\" FOR TABLES IN SCHEMA \"Object Data\"");
				statement.execute(
						"CREATE COLLATION \"Object Data\".\"Fast Unicode\" (provider=builtin,locale='PG_UNICODE_FAST')");
			}
			statement.execute(
					"CREATE SUBSCRIPTION \"Offline Sub\" CONNECTION 'host=example.invalid' PUBLICATION \"Item Publication\" WITH (connect=false,slot_name=NONE)");
			statement.execute("COMMENT ON SUBSCRIPTION \"Offline Sub\" IS 'shared catalog comment'");
			statement.execute("COMMENT ON POLICY \"Allow Items\" ON \"Object Data\".items IS 'policy comment'");
			statement.execute("COMMENT ON SERVER \"Remote Server\" IS 'server comment'");
			var dialect = DialectResolver.getInstance().getDialect(connection);
			var reader = new PostgresSchemaObjectReader(dialect);
			var queryCount = new java.util.concurrent.atomic.AtomicInteger();
			Connection counted = (Connection) java.lang.reflect.Proxy.newProxyInstance(
					Connection.class.getClassLoader(), new Class<?>[] { Connection.class }, (proxy, method, args) -> {
						if (method.getName().equals("prepareStatement"))
							queryCount.incrementAndGet();
						try {
							return method.invoke(connection, args);
						} catch (java.lang.reflect.InvocationTargetException exception) {
							throw exception.getCause();
						}
					});
			var schema = new Schema("Object Data");
			PostgresSchemaObjects.set(schema, reader.readSchema(counted, schema.getName()));
			assertEquals(7, queryCount.get(), "Schema query count must be independent of object and column count");
			queryCount.set(0);
			var catalog = new Catalog(connection.getCatalog());
			PostgresSchemaObjects.set(catalog, reader.readCatalog(counted));
			assertEquals(7, queryCount.get(), "Catalog query count must be independent of object count");
			Schema restored = SchemaUtils.readXml(new StringReader(schema.asXml()));
			Catalog restoredCatalog = SchemaUtils.readXml(new StringReader(catalog.asXml()));
			assertEquals(PostgresSchemaObjects.get(schema), PostgresSchemaObjects.get(restored));
			assertEquals(PostgresSchemaObjects.get(catalog), PostgresSchemaObjects.get(restoredCatalog));
			assertFalse(catalog.asXml().contains("test-only-secret"));
			var objects = new ArrayList<PostgresSchemaObject>();
			objects.addAll(PostgresSchemaObjects.get(restoredCatalog));
			objects.addAll(PostgresSchemaObjects.get(restored));
			for (Kind kind : Kind.values()) {
				assertTrue(objects.stream().anyMatch(o -> o.kind() == kind), kind.toString());
			}
			var overrides = new java.util.HashMap<String, String>();
			for (var object : objects) {
				if (object.kind() == Kind.SUBSCRIPTION)
					overrides.put(object.id(), "host=example.invalid");
				if (object.kind() == Kind.USER_MAPPING)
					overrides.put(object.id() + ":password", "test-only-secret");
			}
			statement.execute("DROP VIEW \"Object Data\".\"Remote View\"");
			var order = List.of(Kind.SUBSCRIPTION, Kind.PUBLICATION, Kind.POLICY, Kind.ROW_SECURITY, Kind.STATISTICS,
					Kind.FOREIGN_TABLE, Kind.USER_MAPPING, Kind.SERVER, Kind.FOREIGN_DATA_WRAPPER, Kind.COLLATION,
					Kind.EXTENSION);
			for (Kind kind : order)
				for (var object : objects)
					if (object.kind() == kind)
						statement.execute(object.dropDefinition());
			var createOrder = new ArrayList<>(order);
			Collections.reverse(createOrder);
			for (Kind kind : createOrder)
				for (var object : objects)
					if (object.kind() == kind)
						for (String sql : PostgresSchemaObjects.createSql(object, overrides))
							statement.execute(sql);
			assertEquals(PostgresSchemaObjects.get(schema), reader.readSchema(connection, schema.getName()));
			assertEquals(PostgresSchemaObjects.get(catalog), reader.readCatalog(connection));
			statement.execute(
					"CREATE VIEW \"Object Data\".\"Remote View\" AS SELECT id,label FROM \"Object Data\".\"Remote Items\"");

			// Exercise the ordinary metadata entry point and registered Catalog CREATE
			// factory.
			var catalogReader = dialect.getCatalogReader();
			catalogReader.setCatalogName(connection.getCatalog());
			catalogReader.getSchemaReader().setSchemaName("Object Data");
			var loaded = catalogReader.getAllFull(connection).stream()
					.filter(c -> c.getName().equals(catalog.getName())).findFirst().orElseThrow();
			var loadedSchema = loaded.getSchemas().get("Object Data");
			assertEquals(PostgresSchemaObjects.get(schema), PostgresSchemaObjects.get(loadedSchema));
			assertTrue(loadedSchema.getFunctions().stream()
					.anyMatch(f -> "hstore".equals(f.getSpecifics().get("postgres.extension"))));
			assertTrue(loadedSchema.getViews().stream()
					.anyMatch(v -> "pg_buffercache".equals(v.getSpecifics().get("postgres.extension"))));
			var planCatalog = new Catalog(catalog.getName());
			planCatalog.getSchemas().add(loadedSchema.clone());
			PostgresSchemaObjects.set(planCatalog, PostgresSchemaObjects.get(catalog));
			var registry = dialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			var factory = (com.sqlapp.data.db.dialect.postgres.sql.PostgresCreateCatalogFactory) registry
					.getSqlFactory(planCatalog, com.sqlapp.data.db.sql.SqlType.CREATE);
			var plan = factory.createSql(planCatalog, overrides);
			assertFalse(plan.stream().anyMatch(
					o -> o.getSqlText().contains("CREATE OR REPLACE FUNCTION") && o.getSqlText().contains("hstore")));
			statement.execute("DROP VIEW \"Object Data\".\"Remote View\"");
			for (Kind kind : order)
				for (var object : objects)
					if (object.kind() == kind)
						statement.execute(object.dropDefinition());
			statement.execute("DROP SCHEMA \"Object Data\" CASCADE");
			for (var operation : plan)
				statement.execute(operation.getSqlText());
			assertEquals(PostgresSchemaObjects.get(schema), reader.readSchema(connection, schema.getName()));
			assertEquals(PostgresSchemaObjects.get(catalog), reader.readCatalog(connection));
			verifyAlter(connection, reader, dialect);
			verifyMixedAlter(connection, reader, dialect);
		} finally {
			ReusableTestcontainers.stop(postgres);
		}
	}

	private void verifyAlter(Connection connection, PostgresSchemaObjectReader reader,
			com.sqlapp.data.db.dialect.Dialect dialect) throws Exception {
		var original = snapshot(connection, reader);
		try (var statement = connection.createStatement()) {
			for (String sql : List.of("COMMENT ON EXTENSION hstore IS 'updated extension'",
					"COMMENT ON COLLATION \"Object Data\".\"Sort C\" IS 'updated collation'",
					"ALTER FOREIGN DATA WRAPPER \"Custom Wrapper\" OPTIONS (ADD custom 'value, WITH (quoted)')",
					"ALTER SERVER \"Remote Server\" VERSION '2' OPTIONS (SET host 'changed.invalid', ADD port '9999')",
					"COMMENT ON SERVER \"Remote Server\" IS NULL",
					"ALTER USER MAPPING FOR CURRENT_USER SERVER \"Remote Server\" OPTIONS (SET user 'changed')",
					"ALTER FOREIGN TABLE \"Object Data\".\"Remote Items\" ALTER COLUMN amount TYPE bigint",
					"ALTER FOREIGN TABLE \"Object Data\".\"Remote Items\" ALTER COLUMN amount SET DEFAULT 9",
					"ALTER FOREIGN TABLE \"Object Data\".\"Remote Items\" ALTER COLUMN amount SET NOT NULL",
					"ALTER FOREIGN TABLE \"Object Data\".\"Remote Items\" ALTER COLUMN id OPTIONS (SET column_name 'changed_id')",
					"ALTER FOREIGN TABLE \"Object Data\".\"Remote Items\" ADD COLUMN extra text DEFAULT 'comma, quote''s'",
					"ALTER FOREIGN TABLE \"Object Data\".\"Remote Items\" OPTIONS (SET table_name 'changed_items')",
					"ALTER FOREIGN TABLE \"Object Data\".foreign_partition ALTER COLUMN label OPTIONS (SET column_name 'new_label')",
					"COMMENT ON COLUMN \"Object Data\".\"Remote Items\".label IS NULL",
					"REVOKE UPDATE (label) ON TABLE \"Object Data\".\"Remote Items\" FROM PUBLIC",
					"GRANT INSERT (amount) ON TABLE \"Object Data\".\"Remote Items\" TO PUBLIC",
					"DROP POLICY \"Allow Items\" ON \"Object Data\".items",
					"CREATE POLICY \"Allow Items\" ON \"Object Data\".items FOR SELECT USING (id>3)",
					"CREATE POLICY \"Extra Policy\" ON \"Object Data\".items FOR INSERT WITH CHECK (amount>5)",
					"ALTER TABLE \"Object Data\".items NO FORCE ROW LEVEL SECURITY",
					"ALTER TABLE \"Object Data\".items DISABLE ROW LEVEL SECURITY",
					"ALTER STATISTICS \"Object Data\".\"Item Stats\" SET STATISTICS 321",
					"ALTER PUBLICATION \"Item Publication\" SET (publish='insert')",
					"CREATE PUBLICATION \"Extra Publication\" WITH (publish='insert')",
					"ALTER SUBSCRIPTION \"Offline Sub\" SET PUBLICATION \"Extra Publication\" WITH (refresh=false)",
					"ALTER SUBSCRIPTION \"Offline Sub\" SET (synchronous_commit='local',binary=true)"))
				statement.execute(sql);
			if (dialect.compareTo(com.sqlapp.data.db.dialect.postgres.DialectHolder.postgreSQL180) >= 0) {
				statement.execute("ALTER PUBLICATION \"Filtered Publication\" DROP TABLE ONLY \"Object Data\".items");
				statement.execute("ALTER PUBLICATION \"Schema Publication\" SET TABLE ONLY \"Object Data\".items");
			}
		}
		var target = snapshot(connection, reader);
		var registry = dialect.createSqlFactoryRegistry();
		var factory = (com.sqlapp.data.db.dialect.postgres.sql.PostgresAlterCatalogFactory) registry
				.getSqlFactory(original, com.sqlapp.data.db.sql.SqlType.ALTER);
		for (var pair : List.of(List.of(target, original), List.of(original, target))) {
			var plan = factory.createDiffSql(pair.get(0).diff(pair.get(1)));
			assertFalse(plan.isEmpty());
			assertFalse(plan.stream().anyMatch(o -> o.getSqlText().contains("DROP FOREIGN TABLE")));
			try (var statement = connection.createStatement()) {
				for (var operation : plan) {
					try {
						statement.execute(operation.getSqlText());
					} catch (java.sql.SQLException exception) {
						throw new AssertionError(operation.getSqlText(), exception);
					}
				}
			}
			var actual = snapshot(connection, reader);
			assertEquals(PostgresSchemaObjects.get(pair.get(1)), PostgresSchemaObjects.get(actual));
			assertEquals(PostgresSchemaObjects.get(pair.get(1).getSchemas().getFirst()),
					PostgresSchemaObjects.get(actual.getSchemas().getFirst()));
			assertTrue(factory.createDiffSql(pair.get(1).diff(actual)).isEmpty());
		}
	}

	private Catalog snapshot(Connection connection, PostgresSchemaObjectReader reader) throws Exception {
		var catalog = new Catalog(connection.getCatalog());
		var schema = new Schema("Object Data");
		PostgresSchemaObjects.set(catalog, reader.readCatalog(connection));
		PostgresSchemaObjects.set(schema, reader.readSchema(connection, schema.getName()));
		catalog.getSchemas().add(schema);
		return catalog;
	}

	private void verifyMixedAlter(Connection connection, PostgresSchemaObjectReader reader,
			com.sqlapp.data.db.dialect.Dialect dialect) throws Exception {
		var original = snapshot(connection, reader);
		var target = original.clone();
		var schema = new Schema("New Schema");
		var table = new com.sqlapp.data.schemas.Table("base");
		table.getColumns().add("id", c -> c.setDataType(com.sqlapp.data.db.datatype.DataType.INT));
		schema.getTables().add(table);
		var view = new com.sqlapp.data.schemas.View("foreign_view");
		view.setStatement("SELECT id FROM \"New Schema\".remote");
		schema.getViews().add(view);
		PostgresSchemaObjects.set(schema, List.of(
				new PostgresSchemaObject(Kind.COLLATION, schema.getName(), "sort", null,
						List.of("CREATE COLLATION \"New Schema\".sort (locale='C')"),
						"DROP COLLATION \"New Schema\".sort", Map.of()),
				new PostgresSchemaObject(Kind.FOREIGN_TABLE, schema.getName(), "remote", null, List.of(
						"CREATE FOREIGN TABLE \"New Schema\".remote (label text COLLATE \"New Schema\".sort) INHERITS (\"New Schema\".base) SERVER \"Remote Server\""),
						"DROP FOREIGN TABLE \"New Schema\".remote", Map.of())));
		target.getSchemas().add(schema);
		var registry = dialect.createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(true);
		var factory = registry.getSqlFactory(original, com.sqlapp.data.db.sql.SqlType.ALTER);
		for (var pair : List.of(List.of(original, target), List.of(target, original))) {
			try (var statement = connection.createStatement()) {
				for (var operation : factory.createDiffSql(pair.get(0).diff(pair.get(1)))) {
					try {
						statement.execute(operation.getSqlText());
					} catch (java.sql.SQLException exception) {
						throw new AssertionError(operation.getSqlText(), exception);
					}
				}
			}
			try (var statement = connection.createStatement();
					var rs = statement.executeQuery("SELECT to_regclass('\"New Schema\".foreign_view') IS NOT NULL")) {
				assertTrue(rs.next());
				assertEquals(pair.get(1) == target, rs.getBoolean(1));
			}
		}
	}
}
