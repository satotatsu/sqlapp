/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.metadata;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;

class SqliteTableRoundTripTest {
	@Test
	void preservesNotNullConflictPoliciesThroughXmlAndRecreation() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			for (String policy : java.util.List.of("REPLACE", "IGNORE")) {
				statement.execute("CREATE TABLE t(value TEXT NOT NULL /* ignored */ ON CONFLICT " + policy
						+ " DEFAULT 'fallback', other TEXT DEFAULT 'NOT NULL ON CONFLICT FAIL')");
				var table = read(connection, "main", "t");
				assertEquals(policy, table.getColumns().get("value").getSpecifics().get("sqlite.notNull.onConflict"));
				assertNull(table.getColumns().get("other").getSpecifics().get("sqlite.notNull.onConflict"));
				var context = new com.sqlapp.data.parameter.ParametersContext();
				context.put("schemaName", "main");
				context.put("tableName", "t");
				var legacy = new SqliteColumnReader(DialectHolder.defaultDialect).doGetAll(connection, context,
						new com.sqlapp.data.schemas.ProductVersionInfo().setMajorVersion(3).setMinorVersion(15));
				assertEquals(policy, legacy.get(0).getSpecifics().get("sqlite.notNull.onConflict"));
				recreate(connection, table);
				statement.execute("INSERT INTO t(value) VALUES (NULL)");
				try (var rs = statement.executeQuery("SELECT value FROM t")) {
					assertEquals(policy.equals("REPLACE"), rs.next());
					if (policy.equals("REPLACE"))
						assertEquals("fallback", rs.getString(1));
				}
				table.getColumns().get("value").getSpecifics().put("sqlite.notNull.onConflict", "unknown");
				assertThrows(IllegalArgumentException.class,
						() -> DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE));
				statement.execute("DROP TABLE t");
			}
		}
	}

	@Test
	void legacyMetadataPathRetainsIdentityAndNullablePrimaryKeys() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(id INTEGER PRIMARY KEY AUTOINCREMENT, value TEXT)");
			statement.execute("CREATE TABLE other(code TEXT PRIMARY KEY)");
			var context = new com.sqlapp.data.parameter.ParametersContext();
			context.put("schemaName", "main");
			context.put("tableName", "t");
			var version = new com.sqlapp.data.schemas.ProductVersionInfo().setMajorVersion(3).setMinorVersion(15);
			var reader = new SqliteColumnReader(DialectHolder.defaultDialect);
			var id = reader.doGetAll(connection, context, version).get(0);
			assertTrue(id.isIdentity());
			assertTrue(id.isNotNull());
			assertEquals("true", id.getSpecifics().get("sqlite.autoincrement"));
			context.put("tableName", "other");
			assertFalse(reader.doGetAll(connection, context, version).get(0).isNotNull());
		}
	}

	@Test
	void invalidIdentityConfigurationFailsBeforeProducingSql() {
		var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		var table = new Table("t");
		table.getColumns().add(new com.sqlapp.data.schemas.Column("id")
				.setDataType(com.sqlapp.data.db.datatype.DataType.BIGINT).setIdentity(true));
		table.getSpecifics().put("without_rowid", "true");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.CREATE));
		table.getSpecifics().clear();
		table.getColumns().add(new com.sqlapp.data.schemas.Column("other")
				.setDataType(com.sqlapp.data.db.datatype.DataType.BIGINT).setIdentity(true));
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.CREATE));
	}

	@Test
	void retainsDeclaredTypesSoIntAndSizedIntegerDoNotBecomeRowidAliases() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			for (String type : java.util.List.of("INT", "INTEGER(8)")) {
				statement.execute("CREATE TABLE t(id " + type + " PRIMARY KEY, value)");
				var table = read(connection, "main", "t");
				assertFalse(table.getColumns().get("id").isIdentity());
				recreate(connection, table);
				statement.execute("INSERT INTO t(value) VALUES ('a')");
				try (var rs = statement.executeQuery("SELECT id FROM t")) {
					assertTrue(rs.next());
					assertNull(rs.getObject(1));
				}
				assertEquals(type,
						read(connection, "main", "t").getColumns().get("id").getSpecifics().get("sqlite.declaredType"));
				statement.execute("DROP TABLE t");
			}
			statement.execute("CREATE TABLE t(id INT PRIMARY KEY, value ANY) STRICT");
			recreate(connection, read(connection, "main", "t"));
			statement.execute("INSERT INTO t VALUES (1,'000123')");
			try (var rs = statement.executeQuery("SELECT value, typeof(value) FROM t")) {
				assertTrue(rs.next());
				assertEquals("000123", rs.getString(1));
				assertEquals("text", rs.getString(2));
			}
		}
	}

	private Table read(Connection connection, String schema, String name) {
		var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
		reader.setSchemaName(schema);
		reader.setObjectName(name);
		return reader.getAllFull(connection).get(0);
	}

	private void recreate(Connection connection, Table table) throws Exception {
		Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		try (var statement = connection.createStatement()) {
			statement.execute("DROP TABLE \"" + table.getSchemaName() + "\".\"" + table.getName() + "\"");
			for (var operation : DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(restored,
					SqlType.CREATE))
				statement.execute(operation.getSqlText());
		}
	}

	@Test
	void recreatesIdentityGeneratedUniqueAndPartialIndexInAttachedDatabase() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("ATTACH ':memory:' AS other");
			statement.execute("""
					CREATE TABLE other.t(id INTEGER PRIMARY KEY AUTOINCREMENT,
					 value TEXT NOT NULL, derived TEXT GENERATED ALWAYS AS (lower(value)) STORED UNIQUE,
					 virtual_value TEXT AS (upper(value)) VIRTUAL, CHECK(length(value)>0))
					""");
			statement.execute("CREATE INDEX other.ix ON t(value DESC) WHERE id>0");
			var table = read(connection, "other", "t");
			assertEquals("true", table.getColumns().get("id").getSpecifics().get("sqlite.autoincrement"));
			assertTrue(table.getConstraints().getUniqueConstraints().stream()
					.anyMatch(c -> !c.isPrimaryKey() && c.getColumns().get("derived") != null));
			recreate(connection, table);
			statement.execute("INSERT INTO other.t(value) VALUES ('Hello')");
			assertThrows(SQLException.class, () -> statement.execute("INSERT INTO other.t(value) VALUES ('HELLO')"));
			try (var rs = statement.executeQuery("SELECT derived, virtual_value FROM other.t")) {
				assertTrue(rs.next());
				assertEquals("hello", rs.getString(1));
				assertEquals("HELLO", rs.getString(2));
			}
			statement.execute("DELETE FROM other.t");
			statement.execute("INSERT INTO other.t(value) VALUES ('Next')");
			try (var rs = statement.executeQuery("SELECT id FROM other.t")) {
				assertTrue(rs.next());
				assertEquals(2, rs.getInt(1));
			}
			assertNotNull(read(connection, "other", "t").getIndexes().get("ix"));
		}
	}

	@Test
	void recreatesStrictWithoutRowidCompositeKeys() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(a TEXT, b INTEGER, PRIMARY KEY(b DESC,a)) WITHOUT ROWID, STRICT");
			var table = read(connection, "main", "t");
			assertEquals("b", table.getConstraints().getPrimaryKeyConstraint().getColumns().get(0).getName());
			recreate(connection, table);
			assertThrows(SQLException.class, () -> statement.execute("SELECT rowid FROM t"));
			assertThrows(SQLException.class, () -> statement.execute("INSERT INTO t VALUES ('a','wrong type')"));
			assertThrows(SQLException.class, () -> statement.execute("INSERT INTO t VALUES (NULL,1)"));
			statement.execute("INSERT INTO t VALUES ('a',1)");
		}
	}

	@Test
	void preservesNullablePrimaryKeysAndInlineDescendingRowidException() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(id INTEGER PRIMARY KEY DESC, code TEXT)");
			var table = read(connection, "main", "t");
			assertFalse(table.getColumns().get("id").isIdentity());
			assertFalse(table.getColumns().get("id").isNotNull());
			recreate(connection, table);
			statement.execute("INSERT INTO t(code) VALUES ('a'),('b')");
			try (var rs = statement.executeQuery("SELECT count(*) FROM t WHERE id IS NULL")) {
				assertTrue(rs.next());
				assertEquals(2, rs.getInt(1));
			}
			statement.execute("CREATE TABLE ordinary(code TEXT PRIMARY KEY)");
			assertFalse(read(connection, "main", "ordinary").getColumns().get("code").isNotNull());
		}
	}

	@Test
	void tableOptionsIgnoreCommentsAndQuotedKeywords() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute(
					"CREATE TABLE t(id INTEGER PRIMARY KEY, value TEXT DEFAULT 'STRICT WITHOUT ROWID') /* STRICT WITHOUT ROWID */");
			var table = read(connection, "main", "t");
			assertTrue(table.getColumns().get("id").isIdentity());
			assertNull(table.getSpecifics().get("strict"));
			assertNull(table.getSpecifics().get("without_rowid"));
		}
	}
}
