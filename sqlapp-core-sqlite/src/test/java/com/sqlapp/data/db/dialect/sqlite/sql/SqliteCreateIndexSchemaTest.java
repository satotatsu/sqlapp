/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Table;

class SqliteCreateIndexSchemaTest {
	@ParameterizedTest
	@ValueSource(booleans = { false, true })
	void mainIndexKeepsItsDatabaseWhenTempShadowsTheTable(boolean decorate) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE main.t(value TEXT)");
			statement.execute("CREATE TEMP TABLE t(value TEXT)");
			var table = new Table("t").setSchemaName("main");
			table.getColumns().add(new Column("value").setDataType(DataType.VARCHAR));
			var index = new Index("ix").setUnique(true);
			index.getColumns().add("value");
			table.getIndexes().add(index);
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(decorate);
			for (var operation : registry.createSql(index, SqlType.CREATE))
				statement.execute(operation.getSqlText());
			statement.execute("INSERT INTO main.t VALUES('same')");
			assertThrows(SQLException.class, () -> statement.execute("INSERT INTO main.t VALUES('same')"));
			statement.execute("INSERT INTO temp.t VALUES('same'),('same')");
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "main", "temp", "other'\" db" })
	void tableXmlRecreationKeepsPartialExpressionIndexInItsOwnDatabase(String schema) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			var schemas = java.util.List.of("main", "temp", "other'\" db");
			statement.execute("ATTACH ':memory:' AS " + quote(schemas.get(2)));
			for (String other : schemas)
				if (!other.equals(schema))
					statement.execute("CREATE TABLE " + quote(other) + ".t(value TEXT, active INT)");
			var table = new Table("t").setSchemaName(schema);
			table.getColumns().add(new Column("value").setDataType(DataType.VARCHAR));
			table.getColumns().add(new Column("active").setDataType(DataType.INT));
			var index = new Index("ix").setUnique(true).setWhere("active=1");
			index.getSpecifics().put("sqlite.index.keys", "lower(value)");
			index.getColumns().add("lower(value)");
			table.getIndexes().add(index);
			Table restored = com.sqlapp.data.schemas.SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(false);
			for (var operation : registry.createSql(restored, SqlType.CREATE))
				statement.execute(operation.getSqlText());
			statement.execute("INSERT INTO " + quote(schema) + ".t VALUES('a',1)");
			assertThrows(SQLException.class,
					() -> statement.execute("INSERT INTO " + quote(schema) + ".t VALUES('A',1)"));
			statement.execute("INSERT INTO " + quote(schema) + ".t VALUES('A',0)");
			for (String other : schemas) {
				if (!other.equals(schema))
					statement.execute("INSERT INTO " + quote(other) + ".t VALUES('a',1),('A',1)");
				try (var rows = statement
						.executeQuery("SELECT count(*) FROM " + quote(other) + ".sqlite_master WHERE name='ix'")) {
					assertTrue(rows.next());
					assertEquals(other.equals(schema) ? 1 : 0, rows.getInt(1));
				}
			}
		}
	}

	@Test
	void unknownSchemaRetainsNativeResolutionAndLegacyDialectCanQualifyMain() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE main.t(value TEXT)");
			statement.execute("CREATE TEMP TABLE t(value TEXT)");
			var dialect = com.sqlapp.data.db.dialect.DialectResolver.getInstance().getDialect("SQLite", 3, 7, 16);
			var registry = dialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(false);
			for (String schema : java.util.Arrays.asList(null, "main")) {
				var table = new Table("t").setSchemaName(schema);
				var index = new Index(schema == null ? "temp_ix" : "main_ix");
				index.getColumns().add("value");
				table.getIndexes().add(index);
				for (var operation : registry.createSql(index, SqlType.CREATE))
					statement.execute(operation.getSqlText());
			}
			for (String schema : java.util.List.of("main", "temp"))
				try (var rows = statement
						.executeQuery("SELECT name FROM " + schema + ".sqlite_master WHERE type='index'")) {
					assertTrue(rows.next());
					assertEquals(schema + "_ix", rows.getString(1));
					assertFalse(rows.next());
				}
		}
	}

	private static String quote(String name) {
		return "\"" + name.replace("\"", "\"\"") + "\"";
	}
}
