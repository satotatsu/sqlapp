/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.metadata;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Trigger;
import com.sqlapp.data.schemas.View;

class SqliteSchemaObjectsRoundTripTest {
	@Test
	void triggerHeaderHandlesDefaultTimingCommentsAndQuotedNames() {
		var ddl = "CREATE TRIGGER \"AFTER UPDATE\" /* BEFORE DELETE */ INSERT ON t BEGIN SELECT 1; END";
		assertEquals(java.util.List.of("BEFORE", "INSERT"),
				com.sqlapp.data.db.dialect.sqlite.util.SqliteDdl.triggerEvent(ddl));
		assertEquals(java.util.List.of("INSTEAD OF", "DELETE"),
				com.sqlapp.data.db.dialect.sqlite.util.SqliteDdl.triggerEvent(
						"CREATE TRIGGER IF NOT EXISTS other.tr INSTEAD /* timing */ OF DELETE ON v BEGIN SELECT 1; END"));
	}

	@Test
	void viewColumnsUseOneBulkQueryAcrossViews() throws Exception {
		try (var raw = DriverManager.getConnection("jdbc:sqlite::memory:"); var statement = raw.createStatement()) {
			for (int i = 0; i < 8; i++)
				statement.execute("CREATE VIEW v" + i + " AS SELECT " + i + " AS value");
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
			var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getViewReader();
			reader.setSchemaName("main");
			var views = reader.getAllFull(connection);
			assertEquals(8, views.size());
			assertTrue(views.stream().allMatch(view -> view.getColumns().size() == 1));
			assertEquals(1, queries.get());
		}
	}

	@Test
	void missingExecutableDefinitionsFailClearly() {
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(new Trigger("tr"), SqlType.CREATE));
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(new View("v"), SqlType.CREATE));
	}

	@Test
	void viewStatementIgnoresQuotedAsAndComments() {
		assertEquals("/* query */ SELECT 'AS'", SqliteViewReader.extractStatement(
				"CREATE VIEW \"name AS here\" (\"column AS here\") /* AS */ AS/* query */ SELECT 'AS'"));
		assertEquals("SELECT 1", SqliteViewReader.extractStatement("CREATE VIEW v AS\nSELECT 1"));
	}

	@Test
	void recreatesViewColumnNamesInAttachedDatabaseThroughXml() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("ATTACH ':memory:' AS other");
			statement.execute("CREATE TABLE other.source(value TEXT)");
			statement.execute("INSERT INTO other.source VALUES ('hello')");
			statement.execute("CREATE VIEW other.\"view AS name\" (\"renamed column\") AS SELECT value FROM source");
			var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getViewReader();
			reader.setSchemaName("other");
			var view = (View) reader.getAllFull(connection).get(0);
			assertTrue(view.getDefinition().isEmpty());
			assertEquals("renamed column", view.getColumns().get(0).getName());
			View restored = SchemaUtils.readXml(new java.io.StringReader(view.asXml()));
			statement.execute("DROP VIEW other.\"view AS name\"");
			for (var operation : DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(restored,
					SqlType.CREATE))
				statement.execute(operation.getSqlText());
			try (var rs = statement.executeQuery("SELECT \"renamed column\" FROM other.\"view AS name\"")) {
				assertTrue(rs.next());
				assertEquals("hello", rs.getString(1));
			}
			reader.getReaderOptions().setReadDefinition(true);
			view = (View) reader.getAllFull(connection).get(0);
			statement.execute("DROP VIEW other.\"view AS name\"");
			for (var operation : DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(view,
					SqlType.CREATE))
				statement.execute(operation.getSqlText());
			assertEquals(1, reader.getAllFull(connection).size());
		}
	}

	@Test
	void recreatesConditionalUpdateTriggerWithCompleteBodyAndAttachedIdentity() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("ATTACH ':memory:' AS other");
			statement.execute("CREATE TABLE other.source(value TEXT, unrelated INTEGER)");
			statement.execute("CREATE TABLE other.audit(value TEXT)");
			statement.execute("INSERT INTO other.source VALUES ('before', 0)");
			statement.execute("""
					CREATE TRIGGER other.changed AFTER UPDATE OF value ON source
					WHEN NEW.value <> 'skip'
					BEGIN
					 INSERT INTO audit VALUES (CASE WHEN NEW.value = 'yes' THEN 'BEGIN END' ELSE NEW.value END);
					 INSERT INTO audit VALUES ('second');
					END
					""");
			statement.execute("CREATE TRIGGER other.unrelated AFTER INSERT ON audit BEGIN SELECT 1; END");
			var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTriggerReader();
			reader.setSchemaName("other");
			reader.setObjectName("changed");
			var triggers = reader.getAllFull(connection);
			assertEquals(1, triggers.size());
			var trigger = triggers.get(0);
			assertFalse(trigger.getDefinition().isEmpty());
			assertTrue(trigger.getStatement().isEmpty());
			Trigger restored = SchemaUtils.readXml(new java.io.StringReader(trigger.asXml()));
			statement.execute("DROP TRIGGER other.changed");
			for (var operation : DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(restored,
					SqlType.CREATE))
				statement.execute(operation.getSqlText());
			statement.execute("UPDATE other.source SET unrelated=1");
			statement.execute("UPDATE other.source SET value='skip'");
			try (var rs = statement.executeQuery("SELECT count(*) FROM other.audit")) {
				assertTrue(rs.next());
				assertEquals(0, rs.getInt(1));
			}
			statement.execute("UPDATE other.source SET value='yes'");
			try (var rs = statement.executeQuery("SELECT group_concat(value, ',') FROM other.audit")) {
				assertTrue(rs.next());
				assertEquals("BEGIN END,second", rs.getString(1));
			}
			reader.getReaderOptions().setReadStatement(false);
			assertTrue(reader.getAllFull(connection).get(0).getDefinition().isEmpty());
		}
	}
}
