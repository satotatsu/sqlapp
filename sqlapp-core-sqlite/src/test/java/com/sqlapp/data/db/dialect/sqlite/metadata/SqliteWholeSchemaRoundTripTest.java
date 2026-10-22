/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.metadata;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.params.provider.CsvSource;

import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.db.datatype.DataType;

class SqliteWholeSchemaRoundTripTest {
	private static final List<String> DATABASES = List.of("main", "temp", "archive\" db");

	@ParameterizedTest
	@ValueSource(ints = { 15, 37 })
	void typelessColumnsRetainExplicitEmptyTypeMarkerThroughXmlAndRecreation(int minor) throws Exception {
		try (var source = DriverManager.getConnection("jdbc:sqlite::memory:");
				var target = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = source.createStatement();
				var destination = target.createStatement()) {
			statement.execute("CREATE TABLE t(id PRIMARY KEY,value)");
			var context = new com.sqlapp.data.parameter.ParametersContext();
			context.put("schemaName", "main");
			context.put("tableName", "t");
			var columns = new SqliteColumnReader(DialectHolder.defaultDialect).doGetAll(source, context,
					new com.sqlapp.data.schemas.ProductVersionInfo().setMajorVersion(3).setMinorVersion(minor));
			for (var column : columns) {
				assertEquals("true", column.getSpecifics().get("sqlite.declaredType.empty"));
				assertFalse(column.getSpecifics().containsKey("sqlite.declaredType"));
				assertFalse(column.isIdentity());
			}
			Schema original = read(source, "main");
			Schema restored = SchemaUtils.readXml(new java.io.StringReader(original.asXml()));
			assertTrue(original.diff(restored).toDifference().getChangedProperties(DialectHolder.defaultDialect)
					.isEmpty());
			execute(target,
					DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE));
			try (var rows = destination.executeQuery("PRAGMA table_info(t)")) {
				int count = 0;
				while (rows.next()) {
					assertEquals("", rows.getString("type"));
					count++;
				}
				assertEquals(2, count);
			}
			destination.execute("INSERT INTO t(value) VALUES('word')");
			try (var rows = destination.executeQuery("SELECT id,value FROM t")) {
				assertTrue(rows.next());
				assertNull(rows.getObject(1));
				assertEquals("word", rows.getString(2));
			}
		}
	}

	@Test
	void failedWholeSchemaCreateCanRollbackEarlierObjectsWithoutRemovingExistingView() throws Exception {
		Schema schema;
		try (var source = DriverManager.getConnection("jdbc:sqlite::memory:")) {
			prepare(source);
			fixture(source, "main");
			schema = SchemaUtils.readXml(new java.io.StringReader(read(source, "main").asXml()));
		}
		try (var target = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = target.createStatement()) {
			statement.execute("CREATE VIEW a_outer AS SELECT 42 AS kept");
			var operations = DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(schema, SqlType.CREATE);
			target.setAutoCommit(false);
			assertThrows(SQLException.class, () -> execute(target, operations));
			assertFalse(target.getAutoCommit());
			try (var rows = statement.executeQuery("SELECT count(*) FROM sqlite_master WHERE name='z_parent'")) {
				assertTrue(rows.next());
				assertEquals(1, rows.getInt(1));
			}
			target.rollback();
			target.setAutoCommit(true);
			try (var rows = statement.executeQuery("SELECT name FROM sqlite_master WHERE name NOT LIKE 'sqlite_%'")) {
				assertTrue(rows.next());
				assertEquals("a_outer", rows.getString(1));
				assertFalse(rows.next());
			}
			try (var rows = statement.executeQuery("SELECT kept FROM a_outer")) {
				assertTrue(rows.next());
				assertEquals(42, rows.getInt(1));
			}
		}
	}

	@Test
	void emptySchemaCreatesNoDatabaseAndMissingAttachmentIsAnExecutionError() throws Exception {
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		assertTrue(registry.createSql(new Schema("main"), SqlType.CREATE).isEmpty());
		for (String name : java.util.Arrays.asList(null, " ", "bad\0name"))
			assertThrows(IllegalArgumentException.class, () -> registry.createSql(new Schema(name), SqlType.CREATE));
		Schema schema = new Schema("missing");
		Table table = new Table("t");
		table.getColumns().add(new Column("id").setDataType(DataType.INT));
		schema.getTables().add(table);
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			assertThrows(SQLException.class, () -> execute(connection, registry.createSql(schema, SqlType.CREATE)));
			assertTrue(connection.getAutoCommit());
			try (var rows = statement.executeQuery("PRAGMA database_list")) {
				while (rows.next())
					assertNotEquals("missing", rows.getString("name"));
			}
		}
	}

	@ParameterizedTest
	@CsvSource({ "main,false", "main,true", "archive\" db,false", "archive\" db,true" })
	void wholeSchemaDifferencesCommitTogetherOrRollbackBothMigrationPhases(String database, boolean invalidCheck)
			throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
			prepare(connection);
			fixture(connection, database);
			seed(connection, read(connection, database));
			Schema original = read(connection, database);
			Schema phaseOne = SchemaUtils.readXml(new java.io.StringReader(original.asXml()));
			var virtualDiff = original.getTables().get("docs").diff(phaseOne.getTables().get("docs"));
			assertTrue(virtualDiff.toDifference().getChangedProperties(DialectHolder.defaultDialect).isEmpty(),
					virtualDiff.toString());
			var child = phaseOne.getTables().get("a_child");
			child.getColumns().remove("obsolete");
			((CheckConstraint) child.getConstraints().get("positive")).setExpression("value>0");
			var index = child.getIndexes().get("ix_child");
			index.getColumns().clear();
			index.getColumns().add("abs(value)");
			index.getSpecifics().put("sqlite.index.keys", "abs(value)");
			index.setWhere("value>0");
			Schema phaseTwo = phaseOne.clone();
			child = phaseTwo.getTables().get("a_child");
			child.getColumns()
					.add(new Column("tag").setDataType(DataType.VARCHAR).setNotNull(true).setDefaultValue("'ready'"));
			((CheckConstraint) child.getConstraints().get("positive"))
					.setExpression(invalidCheck ? "value>100" : "value>=1");
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			var firstPlan = registry.createSql(original.diff(phaseOne), SqlType.ALTER);
			var secondPlan = registry.createSql(phaseOne.diff(phaseTwo), SqlType.ALTER);
			assertFalse(firstPlan.isEmpty());
			assertFalse(secondPlan.isEmpty());
			connection.setAutoCommit(false);
			execute(connection, firstPlan);
			assertNull(read(connection, database).getTables().get("a_child").getColumns().get("obsolete"));
			if (invalidCheck) {
				assertThrows(SQLException.class, () -> execute(connection, secondPlan));
				assertFalse(connection.getAutoCommit());
				connection.rollback();
				var restored = read(connection, database).getTables().get("a_child");
				assertNotNull(restored.getColumns().get("obsolete"));
				assertNull(restored.getColumns().get("tag"));
				assertEquals("obsolete>0",
						((CheckConstraint) restored.getConstraints().get("positive")).getExpression());
				assertEquals("obsolete>0", restored.getIndexes().get("ix_child").getWhere());
			} else {
				execute(connection, secondPlan);
				connection.commit();
				var changed = read(connection, database).getTables().get("a_child");
				assertNull(changed.getColumns().get("obsolete"));
				assertNotNull(changed.getColumns().get("tag"));
				assertEquals("value>=1", ((CheckConstraint) changed.getConstraints().get("positive")).getExpression());
				assertEquals("value>0", changed.getIndexes().get("ix_child").getWhere());
			}
			connection.setAutoCommit(true);
			try (var statement = connection.createStatement()) {
				String prefix = quote(database) + ".";
				try (var rows = statement.executeQuery(
						"SELECT id,value,derived" + (invalidCheck ? ",obsolete" : ",tag") + " FROM " + prefix
								+ "a_child INDEXED BY ix_child WHERE " + (invalidCheck ? "obsolete>0" : "value>0"))) {
					assertTrue(rows.next());
					assertEquals(10, rows.getInt(1));
					assertEquals(3, rows.getInt(2));
					assertEquals(6, rows.getInt(3));
					if (invalidCheck)
						assertEquals(7, rows.getInt(4));
					else
						assertEquals("ready", rows.getString(4));
					assertFalse(rows.next());
				}
				assertThrows(SQLException.class,
						() -> statement.execute(
								"INSERT INTO " + prefix + "a_child(id,parent_id," + (invalidCheck ? "obsolete," : "")
										+ "value) VALUES(11,1," + (invalidCheck ? "-1," : "") + "0)"));
				statement.execute("INSERT INTO " + prefix + "a_child(id,parent_id," + (invalidCheck ? "obsolete," : "")
						+ "value) VALUES(12,1," + (invalidCheck ? "7," : "") + "4)");
				try (var rows = statement.executeQuery("SELECT count(*) FROM " + prefix + "b_audit")) {
					assertTrue(rows.next());
					assertEquals(2, rows.getInt(1));
				}
				try (var rows = statement.executeQuery("SELECT count(*),sum(derived) FROM " + prefix + "a_outer")) {
					assertTrue(rows.next());
					assertEquals(2, rows.getInt(1));
					assertEquals(14, rows.getInt(2));
				}
			}
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "main", "temp", "archive\" db" })
	void schemaXmlRecreatesDependentObjectsAndNativeBehaviorInAnotherConnection(String database) throws Exception {
		Schema restored;
		try (var source = DriverManager.getConnection("jdbc:sqlite::memory:")) {
			prepare(source);
			fixture(source, database);
			Schema schema = read(source, database);
			assertEquals("INT",
					schema.getTables().get("a_child").getColumns().get("id").getSpecifics().get("sqlite.declaredType"));
			assertFalse(schema.getTables().get("a_child").getColumns().get("id").isIdentity());
			assertTrue(schema.getTables().get("z_parent").getColumns().get("id").isIdentity());
			assertEquals(5, schema.getTables().size());
			assertEquals(2, schema.getViews().size());
			assertEquals(1, schema.getTriggers().size());
			restored = SchemaUtils.readXml(new java.io.StringReader(schema.asXml()));
		}
		try (var target = DriverManager.getConnection("jdbc:sqlite::memory:")) {
			prepare(target);
			try (var statement = target.createStatement()) {
				for (String other : DATABASES)
					if (!other.equals(database)) {
						statement.execute("CREATE TABLE " + quote(other) + ".z_parent(sentinel TEXT)");
						statement.execute("INSERT INTO " + quote(other) + ".z_parent VALUES('untouched')");
					}
			}
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(false);
			execute(target, registry.createSql(restored, SqlType.CREATE));
			Schema reread = read(target, database);
			assertEquals("INT",
					reread.getTables().get("a_child").getColumns().get("id").getSpecifics().get("sqlite.declaredType"));
			assertFalse(reread.getTables().get("a_child").getColumns().get("id").isIdentity());
			assertTrue(reread.getTables().get("z_parent").getColumns().get("id").isIdentity());
			assertEquals(5, reread.getTables().size());
			assertEquals(2, reread.getViews().size());
			assertEquals(1, reread.getTriggers().size());
			seed(target, reread);
			try (var statement = target.createStatement()) {
				String prefix = quote(database) + ".";
				try (var rows = statement
						.executeQuery("SELECT code,derived,payload,typeof(payload) FROM " + prefix + "a_outer")) {
					assertTrue(rows.next());
					assertEquals("One", rows.getString(1));
					assertEquals(6, rows.getInt(2));
					assertEquals("000123", rows.getString(3));
					assertEquals("text", rows.getString(4));
					assertFalse(rows.next());
				}
				assertThrows(SQLException.class,
						() -> statement.execute("INSERT INTO " + prefix + "z_parent(code) VALUES('one')"));
				assertThrows(SQLException.class, () -> statement
						.execute("INSERT INTO " + prefix + "a_child(id,parent_id,obsolete,value) VALUES(11,1,-1,3)"));
				statement.execute("INSERT INTO " + prefix + "docs(body) VALUES('searchable')");
				try (var rows = statement
						.executeQuery("SELECT count(*) FROM " + prefix + "docs WHERE docs MATCH 'searchable'")) {
					assertTrue(rows.next());
					assertEquals(1, rows.getInt(1));
				}
				statement.execute("INSERT INTO " + prefix + "c_map VALUES('key',X'0001FF')");
				try (var rows = statement.executeQuery("SELECT quote(value) FROM " + prefix + "c_map")) {
					assertTrue(rows.next());
					assertEquals("X'0001FF'", rows.getString(1));
				}
				try (var rows = statement.executeQuery("SELECT message FROM " + prefix + "b_audit")) {
					assertTrue(rows.next());
					assertEquals("insert:10", rows.getString(1));
					assertFalse(rows.next());
				}
				statement.execute("DELETE FROM " + prefix + "z_parent WHERE id=1");
				try (var rows = statement.executeQuery("SELECT count(*) FROM " + prefix + "a_child")) {
					assertTrue(rows.next());
					assertEquals(0, rows.getInt(1));
				}
				for (String other : DATABASES)
					if (!other.equals(database))
						try (var rows = statement.executeQuery("SELECT sentinel FROM " + quote(other) + ".z_parent")) {
							assertTrue(rows.next());
							assertEquals("untouched", rows.getString(1));
							assertFalse(rows.next());
						}
			}
		}
	}

	private void prepare(Connection connection) throws SQLException {
		try (var statement = connection.createStatement()) {
			statement.execute("ATTACH ':memory:' AS " + quote(DATABASES.get(2)));
			statement.execute("PRAGMA foreign_keys=ON");
		}
	}

	private void fixture(Connection connection, String database) throws SQLException {
		String prefix = quote(database) + ".";
		try (var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE " + prefix
					+ "z_parent(id INTEGER PRIMARY KEY AUTOINCREMENT,code TEXT COLLATE NOCASE NOT NULL UNIQUE,payload ANY) STRICT");
			statement.execute("CREATE TABLE " + prefix
					+ "a_child(id INT PRIMARY KEY,parent_id INT NOT NULL REFERENCES z_parent(id) ON DELETE CASCADE DEFERRABLE INITIALLY DEFERRED,obsolete INT,value INT NOT NULL DEFAULT 1,derived INT AS(value*2),CONSTRAINT positive CHECK(obsolete>0)) STRICT");
			statement.execute("CREATE INDEX " + prefix + "ix_child ON a_child(abs(obsolete)) WHERE obsolete>0");
			statement.execute("CREATE TABLE " + prefix + "b_audit(message TEXT)");
			statement.execute(
					"CREATE TABLE " + prefix + "c_map(code TEXT PRIMARY KEY,value BLOB) WITHOUT ROWID, STRICT");
			statement.execute("CREATE VIRTUAL TABLE " + prefix + "docs USING fts5(body)");
			statement.execute("CREATE VIEW " + prefix
					+ "z_join AS SELECT p.code,c.derived,p.payload FROM z_parent p JOIN a_child c ON c.parent_id=p.id");
			statement.execute("CREATE VIEW " + prefix + "a_outer AS SELECT * FROM z_join");
			statement.execute("CREATE TRIGGER " + prefix
					+ "audit_insert AFTER INSERT ON a_child BEGIN INSERT INTO b_audit VALUES('insert:' || NEW.id); END");
		}
	}

	private Schema read(Connection connection, String database) {
		var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader();
		reader.setSchemaName(database);
		return reader.getAllFull(connection).get(0);
	}

	private void seed(Connection connection, Schema schema) throws Exception {
		var parent = schema.getTables().get("z_parent");
		var parentRow = parent.getRows().newElement();
		parentRow.put("code", "One");
		parentRow.put("payload", "000123");
		insert(connection, parent, parentRow);
		var child = schema.getTables().get("a_child");
		var childRow = child.getRows().newElement();
		childRow.put("id", 10);
		childRow.put("parent_id", 1);
		childRow.put("obsolete", 7);
		childRow.put("value", 3);
		insert(connection, child, childRow);
	}

	private void insert(Connection connection, Table table, Object row) throws Exception {
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		registry.getOptions().setDecorateSchemaName(false);
		try (var parameters = registry.createSqlNodes(table, SqlType.INSERT).get(0).eval(row);
				var insert = parameters.createStatement(connection)) {
			parameters.setBind(insert);
			assertEquals(1, insert.executeUpdate());
		}
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
