/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Schema;

class SqliteSchemaObjectAlterTest {
	private static final List<String> DATABASES = List.of("main", "temp", "other\" db");

	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void directObjectDifferenceRenamesViewOrTriggerWithoutChangingItsBody(boolean trigger) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
			fixture(connection);
			Schema schema = read(connection, "main");
			com.sqlapp.data.schemas.AbstractSchemaObject<?> original = trigger ? schema.getTriggers().get("tr")
					: schema.getViews().get("v");
			var target = original.clone();
			target.setName("renamed\" object");
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			var difference = original instanceof com.sqlapp.data.schemas.Trigger oldTrigger
					? oldTrigger.diff((com.sqlapp.data.schemas.Trigger) target)
					: ((com.sqlapp.data.schemas.View) original).diff((com.sqlapp.data.schemas.View) target);
			var operations = trigger ? registry.createSql(difference, SqlType.ALTER) : registry.createSql(difference);
			assertEquals(List.of(SqlType.DROP, SqlType.CREATE),
					operations.stream().map(SqlOperation::getSqlType).toList());
			execute(connection, operations);
			try (var statement = connection.createStatement()) {
				statement.execute("INSERT INTO main.source VALUES(7)");
				try (var rows = statement
						.executeQuery("SELECT val FROM main." + quote(trigger ? "v" : target.getName()))) {
					assertTrue(rows.next());
					assertEquals(7, rows.getInt(1));
				}
				try (var rows = statement.executeQuery("SELECT message FROM main.audit")) {
					assertTrue(rows.next());
					assertEquals("old", rows.getString(1));
					assertFalse(rows.next());
				}
				try (var rows = statement.executeQuery(
						"SELECT count(*) FROM main.sqlite_master WHERE name='" + original.getName() + "'")) {
					assertTrue(rows.next());
					assertEquals(0, rows.getInt(1));
				}
			}
		}
	}

	@Test
	void missingDefinitionsAndSupplementalTriggerEditsFailBeforeAnySqlIsReturned() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
			fixture(connection);
			Schema original = read(connection, "main");
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			Schema noView = original.clone();
			noView.getViews().get("v").getStatement().clear();
			assertThrows(IllegalArgumentException.class,
					() -> registry.createSql(original.diff(noView), SqlType.ALTER));
			Schema noTrigger = original.clone();
			noTrigger.getTriggers().get("tr").getDefinition().clear();
			assertThrows(IllegalArgumentException.class,
					() -> registry.createSql(original.diff(noTrigger), SqlType.ALTER));
			var changed = original.getTriggers().get("tr").clone();
			changed.setActionTiming("BEFORE");
			assertTrue(assertThrows(IllegalArgumentException.class,
					() -> registry.createSql(original.getTriggers().get("tr").diff(changed), SqlType.ALTER))
					.getMessage().contains("complete CREATE TRIGGER"));
			assertTrue(registry
					.createSql(original.getViews().get("v").diff(original.getViews().get("v").clone()), SqlType.ALTER)
					.isEmpty());
			var unnamedSchema = new com.sqlapp.data.schemas.View("v");
			unnamedSchema.setStatement("SELECT 1");
			var target = unnamedSchema.clone();
			target.setStatement("SELECT 2");
			assertThrows(IllegalArgumentException.class,
					() -> registry.createSql(unnamedSchema.diff(target), SqlType.ALTER));
			try (var statement = connection.createStatement()) {
				statement.execute("INSERT INTO main.source VALUES(7)");
				try (var rows = statement.executeQuery("SELECT val FROM main.v")) {
					assertTrue(rows.next());
					assertEquals(7, rows.getInt(1));
				}
				try (var rows = statement.executeQuery("SELECT message FROM main.audit")) {
					assertTrue(rows.next());
					assertEquals("old", rows.getString(1));
				}
			}
		}
	}

	@Test
	void viewDefinitionPrecedenceAndOwnedTriggersAreNotSilentlyDiscarded() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
			fixture(connection);
			var original = read(connection, "main");
			var target = original.clone();
			var oldView = original.getViews().get("v");
			oldView.setDefinition("CREATE VIEW v AS SELECT value AS val FROM source");
			var changed = oldView.clone();
			changed.setStatement("SELECT value*10 AS val FROM source");
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			assertTrue(assertThrows(IllegalArgumentException.class,
					() -> registry.createSql(oldView.diff(changed), SqlType.ALTER)).getMessage()
					.contains("takes precedence"));
			try (var statement = connection.createStatement()) {
				statement.execute(
						"CREATE TRIGGER main.view_write INSTEAD OF INSERT ON main.v BEGIN INSERT INTO source VALUES(NEW.val); END");
				original = read(connection, "main");
				target = original.clone();
				target.getViews().get("v").setStatement("SELECT value*10 AS val FROM source");
				var difference = original.diff(target);
				execute(connection, registry.createSql(difference, SqlType.ALTER));
				statement.execute("INSERT INTO main.v VALUES(7)");
				try (var rows = statement.executeQuery("SELECT value FROM main.source")) {
					assertTrue(rows.next());
					assertEquals(7, rows.getInt(1));
				}
				statement.execute("DROP VIEW main.v");
				try (var rows = statement.executeQuery("SELECT count(*) FROM sqlite_master WHERE name='view_write'")) {
					assertTrue(rows.next());
					assertEquals(0, rows.getInt(1));
				}
			}
		}
	}

	@ParameterizedTest
	@CsvSource({ "main,false", "temp,false", "other\" db,false", "main,true", "other\" db,true" })
	void schemaDifferenceReplacesViewAndTriggerAndCallerRollbackRestoresBoth(String database, boolean fail)
			throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
			fixture(connection);
			Schema original = read(connection, database);
			Schema target = original.clone();
			target.getViews().get("v").setStatement("SELECT value*10 AS val FROM source");
			target.getTriggers().get("tr").setDefinition("CREATE TRIGGER tr AFTER INSERT ON source BEGIN "
					+ (fail ? "THIS IS INVALID;" : "INSERT INTO audit VALUES('new');") + " END");
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(false);
			var operations = registry.createSql(original.diff(target), SqlType.ALTER);
			assertEquals(List.of(SqlType.DROP, SqlType.CREATE, SqlType.DROP, SqlType.CREATE),
					operations.stream().map(SqlOperation::getSqlType).toList());
			connection.setAutoCommit(false);
			if (fail) {
				assertThrows(SQLException.class, () -> execute(connection, operations));
				connection.rollback();
			} else {
				execute(connection, operations);
				connection.commit();
			}
			connection.setAutoCommit(true);
			try (var statement = connection.createStatement()) {
				for (String name : DATABASES) {
					statement.execute("INSERT INTO " + quote(name) + ".source VALUES(7)");
					boolean changed = name.equals(database) && !fail;
					try (var rows = statement.executeQuery("SELECT val FROM " + quote(name) + ".v")) {
						assertTrue(rows.next());
						assertEquals(changed ? 70 : 7, rows.getInt(1));
						assertFalse(rows.next());
					}
					try (var rows = statement.executeQuery("SELECT message FROM " + quote(name) + ".audit")) {
						assertTrue(rows.next());
						assertEquals(changed ? "new" : "old", rows.getString(1));
						assertFalse(rows.next());
					}
				}
			}
		}
	}

	@ParameterizedTest
	@CsvSource({ "main,false", "temp,false", "other\" db,false", "main,true", "temp,true", "other\" db,true" })
	void viewReplacementPreservesMultipleOwnedTriggersAndRollback(String database, boolean fail) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
			fixture(connection);
			String prefix = quote(database) + ".";
			try (var statement = connection.createStatement()) {
				statement.execute("CREATE TRIGGER " + prefix + "view_insert INSTEAD OF INSERT ON " + prefix
						+ "v BEGIN INSERT INTO source VALUES(NEW.val); END");
				statement.execute("CREATE TRIGGER " + prefix + "view_delete INSTEAD OF DELETE ON " + prefix
						+ "v BEGIN DELETE FROM source; END");
			}
			var original = read(connection, database);
			// Simulate a persisted definition rejected by the engine after earlier
			// operations succeed.
			if (fail)
				original.getTriggers().get("view_insert").setDefinition(
						"CREATE TRIGGER view_insert INSTEAD OF INSERT ON v BEGIN INVALID STATEMENT; END");
			var target = original.clone();
			target.getViews().get("v").setStatement("SELECT value*10 AS val FROM source");
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(false);
			var operations = registry.createSql(original.diff(target), SqlType.ALTER);
			assertEquals(List.of(SqlType.DROP, SqlType.CREATE, SqlType.CREATE, SqlType.CREATE),
					operations.stream().map(SqlOperation::getSqlType).toList());
			connection.setAutoCommit(false);
			if (fail) {
				assertThrows(SQLException.class, () -> execute(connection, operations));
				connection.rollback();
			} else {
				execute(connection, operations);
				connection.commit();
			}
			connection.setAutoCommit(true);
			try (var statement = connection.createStatement()) {
				statement.execute("INSERT INTO " + prefix + "v VALUES(7)");
				try (var rows = statement.executeQuery("SELECT val FROM " + prefix + "v")) {
					assertTrue(rows.next());
					assertEquals(fail ? 7 : 70, rows.getInt(1));
					assertFalse(rows.next());
				}
				try (var rows = statement.executeQuery("SELECT message FROM " + prefix + "audit")) {
					assertTrue(rows.next());
					assertEquals("old", rows.getString(1));
					assertFalse(rows.next());
				}
				statement.execute("DELETE FROM " + prefix + "v");
				try (var rows = statement.executeQuery("SELECT count(*) FROM " + prefix + "source")) {
					assertTrue(rows.next());
					assertEquals(0, rows.getInt(1));
				}
				for (var other : DATABASES) {
					if (other.equals(database))
						continue;
					statement.execute("INSERT INTO " + quote(other) + ".source VALUES(3)");
					try (var rows = statement.executeQuery("SELECT val FROM " + quote(other) + ".v")) {
						assertTrue(rows.next());
						assertEquals(3, rows.getInt(1));
					}
				}
			}
		}
	}

	@Test
	void schemaCoordinatesOwnedTriggerChangesButDirectViewDifferenceRequiresExplicitPlanning() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
			fixture(connection);
			try (var statement = connection.createStatement()) {
				statement.execute(
						"CREATE TRIGGER main.view_insert INSTEAD OF INSERT ON main.v BEGIN INSERT INTO source VALUES(NEW.val); END");
			}
			var original = read(connection, "main");
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			var supplemental = original.clone();
			supplemental.getViews().get("v").setStatement("SELECT value*10 AS val FROM source");
			supplemental.getTriggers().get("view_insert").setActionTiming("BEFORE");
			assertThrows(IllegalArgumentException.class,
					() -> registry.createSql(original.diff(supplemental), SqlType.ALTER));
			var changed = original.clone();
			changed.getViews().get("v").setStatement("SELECT value*10 AS val FROM source");
			changed.getTriggers().get("view_insert")
					.setDefinition("CREATE TRIGGER view_insert INSTEAD OF INSERT ON v BEGIN SELECT 1; END");
			assertThrows(IllegalArgumentException.class, () -> registry
					.createSql(original.getViews().get("v").diff(changed.getViews().get("v")), SqlType.ALTER));
			execute(connection, registry.createSql(original.diff(changed), SqlType.ALTER));
			var removed = changed.clone();
			removed.getTriggers().remove("view_insert");
			execute(connection, registry.createSql(original.diff(removed), SqlType.ALTER));
			var renamed = original.getViews().get("v").clone();
			renamed.setName("renamed");
			assertThrows(IllegalArgumentException.class,
					() -> registry.createSql(original.getViews().get("v").diff(renamed), SqlType.ALTER));
			original.getTriggers().get("view_insert").getDefinition().clear();
			var missing = original.clone();
			missing.getViews().get("v").setStatement("SELECT value*10 AS val FROM source");
			assertThrows(IllegalArgumentException.class,
					() -> registry.createSql(original.diff(missing), SqlType.ALTER));
		}
	}

	@ParameterizedTest
	@CsvSource({ "main,false", "temp,false", "other\" db,false", "main,true", "temp,true", "other\" db,true" })
	void schemaCoordinatesViewAndOwnedTriggerAddModifyDeleteAndRollback(String database, boolean fail)
			throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
			fixture(connection);
			String prefix = quote(database) + ".";
			try (var statement = connection.createStatement()) {
				statement.execute("CREATE TRIGGER " + prefix + "view_write INSTEAD OF INSERT ON " + prefix
						+ "v BEGIN INSERT INTO source VALUES(NEW.val); END");
				statement.execute("CREATE TRIGGER " + prefix + "view_remove INSTEAD OF DELETE ON " + prefix
						+ "v BEGIN DELETE FROM source; END");
				statement.execute("CREATE TRIGGER " + prefix + "view_keep INSTEAD OF UPDATE ON " + prefix
						+ "v BEGIN INSERT INTO audit VALUES('keep'); END");
				statement.execute("INSERT INTO " + prefix + "source VALUES(1)");
			}
			var original = read(connection, database);
			var target = original.clone();
			target.getViews().get("v").setStatement("SELECT value*10 AS val FROM source");
			target.getTriggers().get("view_write")
					.setDefinition("CREATE TRIGGER view_write INSTEAD OF INSERT ON v BEGIN "
							+ (fail ? "INVALID STATEMENT;" : "INSERT INTO source VALUES(NEW.val*2);") + " END");
			target.getTriggers().remove("view_remove");
			var added = new com.sqlapp.data.schemas.Trigger("view_added").setSchemaName(database);
			added.setTable(new com.sqlapp.data.schemas.Table("v").setSchemaName(database));
			added.setDefinition("CREATE TRIGGER view_added INSTEAD OF DELETE ON v BEGIN DELETE FROM source; END");
			target.getTriggers().add(added);
			target = com.sqlapp.data.schemas.SchemaUtils.readXml(new java.io.StringReader(target.asXml()));
			String originalXml = original.asXml(), targetXml = target.asXml();
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(false);
			var operations = registry.createSql(original.diff(target), SqlType.ALTER);
			assertEquals(originalXml, original.asXml());
			assertEquals(targetXml, target.asXml());
			assertEquals(List.of(SqlType.DROP, SqlType.CREATE, SqlType.CREATE, SqlType.CREATE, SqlType.CREATE),
					operations.stream().map(SqlOperation::getSqlType).toList());
			connection.setAutoCommit(false);
			if (fail) {
				assertThrows(SQLException.class, () -> execute(connection, operations));
				connection.rollback();
			} else {
				execute(connection, operations);
				connection.commit();
			}
			connection.setAutoCommit(true);
			try (var statement = connection.createStatement()) {
				statement.execute("INSERT INTO " + prefix + "v VALUES(7)");
				try (var rows = statement.executeQuery("SELECT MAX(val) FROM " + prefix + "v")) {
					assertTrue(rows.next());
					assertEquals(fail ? 7 : 140, rows.getInt(1));
				}
				statement.execute("UPDATE " + prefix + "v SET val=3");
				try (var rows = statement
						.executeQuery("SELECT count(*) FROM " + prefix + "audit WHERE message='keep'")) {
					assertTrue(rows.next());
					assertEquals(2, rows.getInt(1));
				}
				for (String name : List.of("view_remove", "view_added")) {
					try (var rows = statement.executeQuery(
							"SELECT count(*) FROM " + prefix + "sqlite_master WHERE name='" + name + "'")) {
						assertTrue(rows.next());
						assertEquals(name.equals("view_remove") == fail ? 1 : 0, rows.getInt(1));
					}
				}
				statement.execute("DELETE FROM " + prefix + "v");
				try (var rows = statement.executeQuery("SELECT count(*) FROM " + prefix + "source")) {
					assertTrue(rows.next());
					assertEquals(0, rows.getInt(1));
				}
				for (var other : DATABASES) {
					if (other.equals(database))
						continue;
					statement.execute("INSERT INTO " + quote(other) + ".source VALUES(3)");
					try (var rows = statement.executeQuery("SELECT val FROM " + quote(other) + ".v")) {
						assertTrue(rows.next());
						assertEquals(3, rows.getInt(1));
					}
				}
			}
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "main", "temp", "other\" db" })
	void schemaDropsViewAndOwnedTriggersWithoutDroppingThemTwice(String database) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
			fixture(connection);
			String prefix = quote(database) + ".";
			try (var statement = connection.createStatement()) {
				statement.execute("CREATE TRIGGER " + prefix + "view_write INSTEAD OF INSERT ON " + prefix
						+ "v BEGIN INSERT INTO source VALUES(NEW.val); END");
			}
			var original = read(connection, database);
			var target = original.clone();
			target.getViews().remove("v");
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			assertThrows(IllegalArgumentException.class,
					() -> registry.createSql(original.diff(target), SqlType.ALTER));
			target.getTriggers().remove("view_write");
			var operations = registry.createSql(original.diff(target), SqlType.ALTER);
			assertEquals(1, operations.size());
			assertEquals(SqlType.DROP, operations.get(0).getSqlType());
			execute(connection, operations);
			try (var statement = connection.createStatement()) {
				try (var rows = statement.executeQuery(
						"SELECT count(*) FROM " + prefix + "sqlite_master WHERE name IN ('v','view_write')")) {
					assertTrue(rows.next());
					assertEquals(0, rows.getInt(1));
				}
				statement.execute("INSERT INTO " + prefix + "source VALUES(7)");
				try (var rows = statement.executeQuery("SELECT message FROM " + prefix + "audit")) {
					assertTrue(rows.next());
					assertEquals("old", rows.getString(1));
				}
			}
		}
	}

	@ParameterizedTest
	@CsvSource({ "main,false,false", "temp,false,false", "other\" db,false,false", "main,true,false", "temp,true,false",
			"other\" db,true,false", "main,false,true", "temp,false,true", "other\" db,false,true", "main,true,true",
			"temp,true,true", "other\" db,true,true" })
	void explicitViewRenameRebindsModeledTriggersAndCallerRollback(String database, boolean schemaDifference,
			boolean fail) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
			fixture(connection);
			String prefix = quote(database) + ".", renamedName = "renamed\" ON view";
			try (var statement = connection.createStatement()) {
				statement.execute("CREATE TRIGGER " + prefix + "\"a ON write\" INSTEAD OF INSERT ON " + prefix
						+ "v WHEN NEW.val>0 BEGIN INSERT INTO source VALUES(NEW.val); INSERT INTO audit VALUES('literal ON v'); END");
				statement.execute("CREATE TRIGGER " + prefix + "b_delete INSTEAD OF DELETE ON " + prefix
						+ "v BEGIN DELETE FROM source; END");
				if (schemaDifference)
					statement.execute("CREATE VIEW " + prefix + "dependent AS SELECT val FROM v");
			}
			var original = read(connection, database);
			var target = original.clone();
			var renamed = target.getViews().get("v");
			renamed.setName(renamedName);
			renamed.setStatement("SELECT value*10 AS val FROM source");
			if (schemaDifference)
				target.getViews().get("dependent").setStatement("SELECT val FROM " + quote(renamedName));
			for (String name : List.of("a ON write", "b_delete")) {
				var trigger = target.getTriggers().get(name);
				trigger.setTable(new com.sqlapp.data.schemas.Table(renamedName).setSchemaName(database));
				trigger.setDefinition("CREATE TRIGGER " + quote(name) + (name.equals("a ON write")
						? " INSTEAD OF INSERT ON /* ON ignored */ " + prefix + quote(renamedName)
								+ " WHEN NEW.val>0 BEGIN INSERT INTO source VALUES(NEW.val); INSERT INTO audit VALUES('literal ON v'); END"
						: " INSTEAD OF DELETE ON " + prefix + quote(renamedName) + " BEGIN "
								+ (fail ? "INVALID STATEMENT;" : "DELETE FROM source;") + " END"));
			}
			String originalXml = original.asXml(), targetXml = target.asXml();
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(false);
			var operations = schemaDifference ? registry.createSql(original.diff(target), SqlType.ALTER)
					: registry.createSql(original.getViews().get("v").diff(renamed), SqlType.ALTER);
			assertEquals(originalXml, original.asXml());
			assertEquals(targetXml, target.asXml());
			connection.setAutoCommit(false);
			if (fail) {
				assertThrows(SQLException.class, () -> execute(connection, operations));
				connection.rollback();
			} else {
				execute(connection, operations);
				connection.commit();
			}
			connection.setAutoCommit(true);
			try (var statement = connection.createStatement()) {
				String viewName = fail ? "v" : renamedName;
				statement.execute("INSERT INTO " + prefix + quote(viewName) + " VALUES(7)");
				try (var rows = statement
						.executeQuery("SELECT val FROM " + prefix + quote(schemaDifference ? "dependent" : viewName))) {
					assertTrue(rows.next());
					assertEquals(fail ? 7 : 70, rows.getInt(1));
					assertFalse(rows.next());
				}
				try (var rows = statement
						.executeQuery("SELECT count(*) FROM " + prefix + "audit WHERE message='literal ON v'")) {
					assertTrue(rows.next());
					assertEquals(1, rows.getInt(1));
				}
				try (var rows = statement.executeQuery(
						"SELECT count(*) FROM " + prefix + "sqlite_master WHERE type='view' AND name='v'")) {
					assertTrue(rows.next());
					assertEquals(fail ? 1 : 0, rows.getInt(1));
				}
				statement.execute("DELETE FROM " + prefix + quote(viewName));
				try (var rows = statement.executeQuery("SELECT count(*) FROM " + prefix + "source")) {
					assertTrue(rows.next());
					assertEquals(0, rows.getInt(1));
				}
				for (var other : DATABASES) {
					if (other.equals(database))
						continue;
					statement.execute("INSERT INTO " + quote(other) + ".source VALUES(3)");
					try (var rows = statement.executeQuery("SELECT val FROM " + quote(other) + ".v")) {
						assertTrue(rows.next());
						assertEquals(3, rows.getInt(1));
					}
				}
			}
		}
	}

	@Test
	void renamedViewRejectsStaleTriggerBindingsAndDefinitionsBeforeReturningSql() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
			fixture(connection);
			try (var statement = connection.createStatement()) {
				statement.execute(
						"CREATE TRIGGER main.view_write INSTEAD OF INSERT ON main.v BEGIN INSERT INTO source VALUES(NEW.val); END");
			}
			var original = read(connection, "main");
			var target = original.clone();
			var renamed = target.getViews().get("v");
			renamed.setName("new view");
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			var trigger = target.getTriggers().get("view_write");
			assertThrows(IllegalArgumentException.class,
					() -> registry.createSql(original.getViews().get("v").diff(renamed), SqlType.ALTER));
			trigger.setTable(new com.sqlapp.data.schemas.Table("new view").setSchemaName("main"));
			assertThrows(IllegalArgumentException.class,
					() -> registry.createSql(original.diff(target), SqlType.ALTER));
			trigger.setDefinition(
					"CREATE TRIGGER view_write INSTEAD OF INSERT ON temp.\"new view\" BEGIN INSERT INTO source VALUES(NEW.val); END");
			assertThrows(IllegalArgumentException.class,
					() -> registry.createSql(original.getViews().get("v").diff(renamed), SqlType.ALTER));
			assertThrows(IllegalArgumentException.class,
					() -> registry.createSql(original.diff(target), SqlType.ALTER));
			try (var statement = connection.createStatement()) {
				statement.execute("INSERT INTO main.v VALUES(7)");
				try (var rows = statement.executeQuery("SELECT val FROM main.v")) {
					assertTrue(rows.next());
					assertEquals(7, rows.getInt(1));
				}
			}
		}
	}

	private void fixture(Connection connection) throws SQLException {
		try (var statement = connection.createStatement()) {
			statement.execute("ATTACH ':memory:' AS " + quote(DATABASES.get(2)));
			for (String name : DATABASES) {
				String prefix = quote(name) + ".";
				statement.execute("CREATE TABLE " + prefix + "source(value INT)");
				statement.execute("CREATE TABLE " + prefix + "audit(message TEXT)");
				statement.execute("CREATE VIEW " + prefix + "v AS SELECT value AS val FROM source");
				statement.execute("CREATE TRIGGER " + prefix + "tr AFTER INSERT ON "
						+ (name.equals("temp") ? "temp." : "") + "source BEGIN INSERT INTO audit VALUES('old'); END");
			}
		}
	}

	private Schema read(Connection connection, String database) {
		var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader();
		reader.setSchemaName(database);
		return reader.getAllFull(connection).get(0);
	}

	private void execute(Connection connection, List<SqlOperation> operations) throws SQLException {
		for (var operation : operations)
			try (var statement = connection.createStatement()) {
				statement.execute(operation.getSqlText());
			}
	}

	private static String quote(String value) {
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}
}
