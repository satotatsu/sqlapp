/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

class SqliteRebuildVirtualNeighborsTest {
	@Test
	void olderVersionKeepsConservativeRejection() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(id INTEGER PRIMARY KEY)");
			statement.execute("INSERT INTO t VALUES(10)");
			statement.execute("CREATE VIRTUAL TABLE search USING fts5(value)");
			var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
			reader.setSchemaName("main");
			reader.setObjectName("t");
			var target = reader.getAllFull(connection).get(0);
			var before = catalog(connection, "main");
			var metadata = connection.getMetaData();
			var olderMetadata = (DatabaseMetaData) Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[] { DatabaseMetaData.class }, (proxy, method, args) -> {
						return switch (method.getName()) {
						case "getDatabaseMajorVersion" -> 3;
						case "getDatabaseMinorVersion" -> 36;
						case "getDatabaseProductVersion" -> "3.36.0";
						default -> invoke(metadata, method, args);
						};
					});
			var older = (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[] { Connection.class },
					(proxy, method, args) -> method.getName().equals("getMetaData") ? olderMetadata
							: invoke(connection, method, args));
			var exception = assertThrows(IllegalArgumentException.class,
					() -> new SqliteTableRebuilder().rebuild(older, target));
			assertTrue(exception.getMessage().contains("shadow tables"));
			assertEquals(before, catalog(connection, "main"));
			assertTrue(connection.getAutoCommit());
		}
	}

	private static Object invoke(Object receiver, java.lang.reflect.Method method, Object[] args) throws Throwable {
		try {
			return method.invoke(receiver, args);
		} catch (InvocationTargetException exception) {
			throw exception.getCause();
		}
	}

	@ParameterizedTest
	@CsvSource({ "main,true", "temp,true", "other\" db,true", "main,false", "temp,false", "other\" db,false" })
	void preservesVirtualTablesAndTheirShadows(String database, boolean success) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("ATTACH ':memory:' AS \"other\"\" db\"");
			String prefix = quote(database) + ".";
			statement.execute("CREATE TABLE " + prefix + "t(id INTEGER PRIMARY KEY AUTOINCREMENT, value TEXT)");
			statement.execute("INSERT INTO " + prefix + "t VALUES(10,'alpha'),(1000,'deleted')");
			statement.execute("DELETE FROM " + prefix + "t WHERE id=1000");
			statement.execute("CREATE VIRTUAL TABLE " + prefix + "search USING fts5(value)");
			statement.execute("INSERT INTO " + prefix + "search(rowid,value) VALUES(5,'alpha beta')");
			statement.execute("CREATE VIRTUAL TABLE " + prefix + "boxes USING rtree(id,minX,maxX)");
			statement.execute("INSERT INTO " + prefix + "boxes VALUES(8,1,9)");
			statement.execute("CREATE VIEW " + prefix
					+ "combined AS SELECT t.id,search.value FROM t JOIN search ON search.rowid=5");
			statement.execute("CREATE TRIGGER " + prefix
					+ "feed AFTER INSERT ON t BEGIN INSERT INTO search(value) VALUES(new.value); END");
			var before = catalog(connection, database);
			var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
			reader.setSchemaName(database);
			var tables = reader.getAllFull(connection);
			assertEquals(3, tables.size()); // No FTS or RTree shadow tables in the model.
			var target = tables.stream().filter(table -> "t".equals(table.getName())).findFirst().orElseThrow();
			target.getColumns().add(
					new Column("note").setDataType(DataType.VARCHAR).setDataTypeName("TEXT").setDefaultValue("'new'"));
			if (!success)
				target.getConstraints().add(new CheckConstraint("reject", "id<5"));
			String xml = target.asXml();
			if (success)
				assertEquals(1, new SqliteTableRebuilder().rebuild(connection, target).rowCount());
			else
				assertThrows(SQLException.class, () -> new SqliteTableRebuilder().rebuild(connection, target));
			assertEquals(xml, target.asXml());
			assertTrue(connection.getAutoCommit());
			if (!success)
				assertEquals(before, catalog(connection, database));
			else
				assertEquals(
						before.stream().filter(row -> row.startsWith("search") || row.startsWith("boxes")).toList(),
						catalog(connection, database).stream()
								.filter(row -> row.startsWith("search") || row.startsWith("boxes")).toList());
			try (var rows = statement
					.executeQuery("SELECT rowid,value FROM " + prefix + "search WHERE search MATCH 'alpha'")) {
				assertTrue(rows.next());
				assertEquals(5, rows.getInt(1));
				assertEquals("alpha beta", rows.getString(2));
				assertFalse(rows.next());
			}
			try (var rows = statement.executeQuery("SELECT id FROM " + prefix + "boxes WHERE minX<=2 AND maxX>=2")) {
				assertTrue(rows.next());
				assertEquals(8, rows.getInt(1));
				assertFalse(rows.next());
			}
			try (var rows = statement.executeQuery("SELECT id,value FROM " + prefix + "combined")) {
				assertTrue(rows.next());
				assertEquals(10, rows.getInt(1));
				assertEquals("alpha beta", rows.getString(2));
				assertFalse(rows.next());
			}
			// Virtual targets and shadow targets stay ineligible, including caller-built
			// models.
			for (String name : List.of("search", "boxes", "search_data", "boxes_node")) {
				var invalid = new Table(name).setSchemaName(database);
				invalid.getColumns().add(new Column("id").setDataType(DataType.INT));
				assertThrows(IllegalArgumentException.class,
						() -> new SqliteTableRebuilder().rebuild(connection, invalid));
			}
			statement.execute("INSERT INTO " + prefix + "t(value) VALUES('gamma')");
			try (var rows = statement.executeQuery("SELECT id FROM " + prefix + "t WHERE value='gamma'")) {
				assertTrue(rows.next());
				assertEquals(1001, rows.getInt(1));
			}
			try (var rows = statement
					.executeQuery("SELECT count(*) FROM " + prefix + "search WHERE search MATCH 'gamma'")) {
				assertTrue(rows.next());
				assertEquals(1, rows.getInt(1));
			}
		}
	}

	private static List<String> catalog(Connection connection, String database) throws SQLException {
		var result = new ArrayList<String>();
		try (var statement = connection.createStatement();
				var rows = statement
						.executeQuery("SELECT name,sql FROM " + quote(database) + ".sqlite_master ORDER BY name")) {
			while (rows.next())
				result.add(rows.getString(1) + "|" + rows.getString(2));
		}
		return result;
	}

	private static String quote(String value) {
		return "\"" + value.replace("\"", "\"\"") + "\"";
	}
}
