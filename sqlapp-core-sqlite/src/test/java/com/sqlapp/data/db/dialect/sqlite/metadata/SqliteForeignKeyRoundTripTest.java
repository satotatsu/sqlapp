/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.metadata;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;

class SqliteForeignKeyRoundTripTest {
	@Test
	void readDeferredKeysAreCheckedAtCommitAndUnsupportedStandaloneCreationFails() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("PRAGMA foreign_keys=ON");
			statement.execute("CREATE TABLE parent(id INTEGER PRIMARY KEY)");
			statement.execute("""
					CREATE TABLE child(id INTEGER CONSTRAINT "fk ""delayed" REFERENCES parent(id)
					/* NOT DEFERRABLE */ DEFERRABLE INITIALLY DEFERRED)
					""");
			var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
			reader.setSchemaName("main");
			reader.setObjectName("child");
			var child = reader.getAllFull(connection).get(0);
			var key = child.getConstraints().getForeignKeyConstraints().get(0);
			assertEquals(com.sqlapp.data.schemas.Deferrability.InitiallyDeferred, key.getDeferrability());
			assertEquals("fk \"delayed", key.getName());
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(key, SqlType.CREATE));
			Table restored = SchemaUtils.readXml(new java.io.StringReader(child.asXml()));
			statement.execute("DROP TABLE child");
			for (var operation : registry.createSql(restored, SqlType.CREATE))
				statement.execute(operation.getSqlText());
			connection.setAutoCommit(false);
			statement.execute("INSERT INTO child VALUES (1)");
			statement.execute("INSERT INTO parent VALUES (1)");
			connection.commit();
			statement.execute("INSERT INTO child VALUES (2)");
			assertThrows(SQLException.class, connection::commit);
			connection.rollback();
			key.getRelatedColumns().clear();
			key.setRelatedTableSchemaName("other");
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(child, SqlType.CREATE));
		}
	}

	@Test
	void readsMultipleTableAndInlineDeclarationsWithoutUsingPragmaIdOrder() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE parent(a INT,b TEXT,PRIMARY KEY(b,a))");
			statement.execute(
					"""
							CREATE TABLE child(
							 x TEXT DEFAULT 'REFERENCES fake DEFERRABLE INITIALLY DEFERRED', y INT,
							 z TEXT CONSTRAINT inline_key REFERENCES parent(b) NOT DEFERRABLE INITIALLY DEFERRED,
							 CONSTRAINT deferred_key FOREIGN KEY(x,y) REFERENCES parent DEFERRABLE INITIALLY DEFERRED,
							 CONSTRAINT immediate_key FOREIGN KEY(x,y) REFERENCES parent(b,a) ON DELETE SET NULL DEFERRABLE INITIALLY IMMEDIATE
							)
							""");
			var context = new ParametersContext();
			context.put("schemaName", "main");
			context.put("tableName", "child");
			var reader = new SqliteForeignKeyConstraintReader(DialectHolder.defaultDialect);
			for (int minor : List.of(15, 53)) {
				var keys = reader.doGetAll(connection, context,
						new ProductVersionInfo().setMajorVersion(3).setMinorVersion(minor));
				assertEquals(3, keys.size());
				var byName = keys.stream().collect(java.util.stream.Collectors.toMap(k -> k.getName(), k -> k));
				assertEquals(com.sqlapp.data.schemas.Deferrability.NotDeferrable,
						byName.get("inline_key").getDeferrability());
				assertEquals(com.sqlapp.data.schemas.Deferrability.InitiallyDeferred,
						byName.get("deferred_key").getDeferrability());
				assertEquals(com.sqlapp.data.schemas.Deferrability.InitiallyImmediate,
						byName.get("immediate_key").getDeferrability());
			}
		}
	}

	@Test
	void resolvesImplicitCompositeParentInKeyOrderAndRecreatesAttachedChild() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("PRAGMA foreign_keys=ON");
			statement.execute("ATTACH ':memory:' AS other");
			statement.execute("CREATE TABLE other.parent(a TEXT, b INTEGER, PRIMARY KEY(b,a))");
			statement.execute(
					"CREATE TABLE other.child(x INTEGER, y TEXT, FOREIGN KEY(x,y) REFERENCES parent ON DELETE CASCADE)");
			var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
			reader.setSchemaName("other");
			reader.setObjectName("child");
			var child = reader.getAllFull(connection).get(0);
			var key = child.getConstraints().getForeignKeyConstraints().get(0);
			assertEquals("b", key.getRelatedColumns().get(0).getName());
			assertEquals("a", key.getRelatedColumns().get(1).getName());
			Table restored = SchemaUtils.readXml(new java.io.StringReader(child.asXml()));
			statement.execute("DROP TABLE other.child");
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			for (var operation : registry.createSql(restored, SqlType.CREATE))
				statement.execute(operation.getSqlText());
			assertThrows(SQLException.class, () -> statement.execute("INSERT INTO other.child VALUES (1,'a')"));
			statement.execute("INSERT INTO other.parent VALUES ('a',1)");
			statement.execute("INSERT INTO other.child VALUES (1,'a')");
			statement.execute("DELETE FROM other.parent");
			try (var rs = statement.executeQuery("SELECT count(*) FROM other.child")) {
				assertTrue(rs.next());
				assertEquals(0, rs.getInt(1));
			}
		}
	}

	@Test
	void batchesImplicitParentsAndKeepsLegacyResolutionAvailable() throws Exception {
		try (var raw = DriverManager.getConnection("jdbc:sqlite::memory:"); var statement = raw.createStatement()) {
			statement.execute("CREATE TABLE parent(a INT, b TEXT, PRIMARY KEY(b,a))");
			for (int i = 0; i < 6; i++)
				statement.execute("CREATE TABLE child" + i + "(x TEXT,y INT, FOREIGN KEY(x,y) REFERENCES parent)");
			var queries = new java.util.concurrent.atomic.AtomicInteger();
			var connection = (java.sql.Connection) java.lang.reflect.Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[] { java.sql.Connection.class }, (proxy, method, args) -> {
						if (method.getName().equals("prepareStatement"))
							queries.incrementAndGet();
						try {
							return method.invoke(raw, args);
						} catch (java.lang.reflect.InvocationTargetException e) {
							throw e.getCause();
						}
					});
			var reader = new SqliteForeignKeyConstraintReader(DialectHolder.defaultDialect);
			var context = new ParametersContext();
			context.put("schemaName", "main");
			context.put("tableName", List.of("child0", "child1", "child2", "child3", "child4", "child5"));
			var keys = reader.doGetAll(connection, context,
					new ProductVersionInfo().setMajorVersion(3).setMinorVersion(53));
			assertEquals(6, keys.size());
			assertEquals(1, queries.get());
			context.put("tableName", "child0");
			var legacy = reader.doGetAll(raw, context, new ProductVersionInfo().setMajorVersion(3).setMinorVersion(15));
			assertEquals("b", legacy.get(0).getRelatedColumns().get(0).getName());
			assertEquals("a", legacy.get(0).getRelatedColumns().get(1).getName());
		}
	}

	@Test
	void unresolvedImplicitParentFailsWithActionableError() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE parent(a INT,b INT,PRIMARY KEY(a,b))");
			statement.execute("CREATE TABLE child(x INT REFERENCES parent)");
			var context = new ParametersContext();
			context.put("tableName", "child");
			var reader = new SqliteForeignKeyConstraintReader(DialectHolder.defaultDialect);
			for (int minor : List.of(15, 53)) {
				var error = assertThrows(IllegalArgumentException.class, () -> reader.doGetAll(connection, context,
						new ProductVersionInfo().setMajorVersion(3).setMinorVersion(minor)));
				assertTrue(error.getMessage().contains("child -> parent"));
			}
		}
	}

	@Test
	void preservesColumnCollationsThroughXmlAndTableRecreation() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute(
					"CREATE TABLE t(value TEXT /* , COLLATE fake */ COLLATE \"NOCASE\" UNIQUE, other TEXT DEFAULT 'COLLATE fake')");
			var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
			reader.setSchemaName("main");
			reader.setObjectName("t");
			var table = reader.getAllFull(connection).get(0);
			assertEquals("NOCASE", table.getColumns().get("value").getCollation());
			assertNull(table.getColumns().get("other").getCollation());
			var context = new ParametersContext();
			context.put("schemaName", "main");
			context.put("tableName", "t");
			assertEquals("NOCASE", new SqliteColumnReader(DialectHolder.defaultDialect)
					.doGetAll(connection, context, new ProductVersionInfo().setMajorVersion(3).setMinorVersion(15))
					.get(0).getCollation());
			Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
			statement.execute("DROP TABLE t");
			for (var operation : DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(restored,
					SqlType.CREATE))
				statement.execute(operation.getSqlText());
			statement.execute("INSERT INTO t(value) VALUES ('a')");
			assertThrows(SQLException.class, () -> statement.execute("INSERT INTO t(value) VALUES ('A')"));
			assertNull(com.sqlapp.data.db.dialect.sqlite.util.SqliteDdl
					.columnCollation("value TEXT CHECK(value COLLATE BINARY <> 'COLLATE fake')"));
		}
	}
}
