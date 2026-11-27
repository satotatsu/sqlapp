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

class SqliteConstraintRoundTripTest {
	private Table read(Connection connection) {
		var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
		reader.setSchemaName("main");
		reader.setObjectName("t");
		return reader.getAllFull(connection).get(0);
	}

	private void recreate(Connection connection, Table table) throws Exception {
		Table copy = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		try (var statement = connection.createStatement()) {
			statement.execute("DROP TABLE t");
			for (var operation : DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(copy,
					SqlType.CREATE))
				statement.execute(operation.getSqlText());
		}
	}

	@Test
	void namedKeysRetainCollationOverridesAndConflictPolicies() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("""
					CREATE TABLE t(id TEXT, value TEXT COLLATE BINARY, payload TEXT,
					 CONSTRAINT named_pk PRIMARY KEY(id COLLATE NOCASE DESC) ON CONFLICT REPLACE,
					 CONSTRAINT insensitive UNIQUE(value COLLATE NOCASE DESC) ON CONFLICT IGNORE,
					 CONSTRAINT sensitive UNIQUE(value COLLATE BINARY) ON CONFLICT FAIL)
					""");
			var table = read(connection);
			assertEquals("named_pk", table.getConstraints().getPrimaryKeyConstraint().getName());
			assertEquals("NOCASE",
					table.getConstraints().get("insensitive").getSpecifics().get("sqlite.key.collation.0"));
			assertEquals("FAIL", table.getConstraints().get("sensitive").getSpecifics().get("sqlite.onConflict"));
			recreate(connection, table);
			statement.execute("INSERT INTO t VALUES ('id','a','first')");
			statement.execute("INSERT INTO t VALUES ('other','A','ignored')");
			statement.execute("INSERT INTO t VALUES ('ID','b','replacement')");
			try (var rs = statement.executeQuery("SELECT count(*), min(payload) FROM t")) {
				assertTrue(rs.next());
				assertEquals(1, rs.getInt(1));
				assertEquals("replacement", rs.getString(2));
			}
		}
	}

	@Test
	void inlineIdentityConflictIsKeptBeforeAutoincrement() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute(
					"CREATE TABLE t(id INTEGER CONSTRAINT named_pk PRIMARY KEY ON CONFLICT IGNORE AUTOINCREMENT, value TEXT)");
			recreate(connection, read(connection));
			statement.execute("INSERT INTO t VALUES (1,'kept'),(1,'ignored')");
			try (var rs = statement.executeQuery("SELECT value FROM t")) {
				assertTrue(rs.next());
				assertEquals("kept", rs.getString(1));
				assertFalse(rs.next());
			}
		}
	}

	@Test
	void checksIgnoreLiteralsCommentsAndRetainEachDeclaredName() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("""
					CREATE TABLE t(value TEXT DEFAULT 'CHECK(fake)'
					 /* CHECK(wrong) , UNIQUE(fake) */
					 CONSTRAINT "first ""check" CHECK(length(value)>2)
					 CONSTRAINT second_check CHECK(instr(value,'CHECK(')>0))
					""");
			var table = read(connection);
			assertEquals(2, table.getConstraints().size());
			assertNotNull(table.getConstraints().get("first \"check"));
			assertNotNull(table.getConstraints().get("second_check"));
			recreate(connection, table);
			statement.execute("INSERT INTO t DEFAULT VALUES");
			assertThrows(SQLException.class, () -> statement.execute("INSERT INTO t VALUES ('abc')"));
			assertThrows(SQLException.class, () -> statement.execute("INSERT INTO t VALUES ('x')"));
		}
	}

	@Test
	void constraintReadingDoesNotRequireModernPragmas() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(value TEXT CONSTRAINT named_unique UNIQUE ON CONFLICT IGNORE)");
			var context = new com.sqlapp.data.parameter.ParametersContext();
			context.put("tableName", "t");
			var keys = new SqliteUniqueConstraintReader(DialectHolder.defaultDialect).doGetAll(connection, context,
					new com.sqlapp.data.schemas.ProductVersionInfo().setMajorVersion(3).setMinorVersion(8));
			assertEquals(1, keys.size());
			assertEquals("named_unique", keys.get(0).getName());
			assertEquals("IGNORE", keys.get(0).getSpecifics().get("sqlite.onConflict"));
			keys.get(0).getSpecifics().put("sqlite.onConflict", "invalid");
			var table = new Table("bad");
			table.getColumns().add(new com.sqlapp.data.schemas.Column("value")
					.setDataType(com.sqlapp.data.db.datatype.DataType.VARCHAR));
			table.getConstraints().add(keys.get(0));
			assertThrows(IllegalArgumentException.class,
					() -> DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(table, SqlType.CREATE));
		}
	}
}
