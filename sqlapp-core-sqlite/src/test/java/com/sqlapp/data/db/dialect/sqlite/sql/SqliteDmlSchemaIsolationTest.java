/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;

import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.db.sql.SqlFactoryRegistry;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Table;

class SqliteDmlSchemaIsolationTest {
	private static final List<String> SCHEMAS = List.of("main", "temp", "other\" db");

	@ParameterizedTest
	@EnumSource(value = SqlType.class, names = { "DELETE_ALL", "DELETE_FOR_APP" })
	void deleteVariantsRetainExplicitSchemaAndSharedConditions(SqlType type) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
			setup(connection);
			try (var statement = connection.createStatement()) {
				statement.execute("INSERT INTO main.t VALUES(2,'kept')");
			}
			var table = read(connection, "main");
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(false);
			registry.getTableOptions().setDeleteAllCondition((t, builder) -> builder.and().name("id").eq()._add("1"));
			var context = new ParametersContext(table);
			context.put("id", 1);
			context.put("value", "main");
			try (var parameters = registry.createSqlNodes(table, type).get(0).eval(context);
					var query = parameters.createStatement(connection)) {
				parameters.setBind(query);
				assertEquals(1, query.executeUpdate());
			}
			for (String schema : SCHEMAS)
				try (var statement = connection.createStatement();
						var rows = statement.executeQuery("SELECT value FROM " + quote(schema) + ".t")) {
					assertTrue(rows.next());
					assertEquals(schema.equals("main") ? "kept" : schema, rows.getString(1));
					assertFalse(rows.next());
				}
		}
	}

	@ParameterizedTest
	@ValueSource(ints = { 7, 15 })
	void legacyDialectKeepsExplicitSchemaAndUnspecifiedSchemaRetainsNativeResolution(int minor) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
			setup(connection);
			var dialect = com.sqlapp.data.db.dialect.DialectResolver.getInstance().getDialect("SQLite", 3, minor, 0);
			for (String schema : java.util.Arrays.asList("main", null)) {
				var table = read(connection, schema == null ? "temp" : schema);
				table.setSchemaName(schema);
				table.setDialect(dialect);
				assertEquals(schema, table.getSchemaName());
				var registry = dialect.createSqlFactoryRegistry();
				registry.getOptions().setDecorateSchemaName(false);
				var row = table.getRows().newElement();
				row.put("id", 2);
				row.put("value", "legacy");
				write(connection, registry, table, SqlType.INSERT, row);
				assertCount(connection, schema == null ? "temp" : schema, 2);
				row.put("value", "updated");
				write(connection, registry, table, SqlType.UPDATE, row);
				try (var parameters = registry.createSqlNodes(table, SqlType.SELECT).get(0).eval(row);
						var query = parameters.createStatement(connection)) {
					parameters.setBind(query);
					try (var rows = query.executeQuery()) {
						assertTrue(rows.next());
						assertEquals("updated", rows.getString("value"));
						assertFalse(rows.next());
					}
				}
				write(connection, registry, table, SqlType.DELETE, row);
				assertCount(connection, schema == null ? "temp" : schema, 1);
			}
		}
	}

	@ParameterizedTest
	@EnumSource(value = SqlType.class, names = { "UPDATE_TABLE", "UPDATE_FOR_APP" })
	void updateVariantsRetainExplicitSchemaAndSharedConditions(SqlType type) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
			setup(connection);
			var table = read(connection, "main");
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(false);
			registry.getTableOptions().setUpdateAllCondition((t, builder) -> builder.and().name("id").eq()._add("1"));
			var row = table.getRows().newElement();
			row.put("id", 1);
			row.put("value", "updated");
			if (type == SqlType.UPDATE_FOR_APP) {
				var context = new ParametersContext(table);
				context.put("id", 1);
				context.put("value", "main");
				try (var parameters = registry.createSqlNodes(table, type).get(0).eval(context);
						var query = parameters.createStatement(connection)) {
					parameters.setBind(query);
					assertEquals(1, query.executeUpdate());
				}
			} else
				write(connection, registry, table, type, row);
			for (String schema : SCHEMAS)
				try (var statement = connection.createStatement();
						var rows = statement.executeQuery("SELECT value FROM " + quote(schema) + ".t")) {
					assertTrue(rows.next());
					assertEquals(schema.equals("main") && type == SqlType.UPDATE_TABLE ? "updated" : schema,
							rows.getString(1));
					assertFalse(rows.next());
				}
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "main", "temp", "other\" db" })
	void rowSetsAndRootRelationsRetainBothParentAndChildDatabaseIdentities(String schema) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
			setup(connection);
			try (var statement = connection.createStatement()) {
				for (String database : SCHEMAS) {
					statement.execute("CREATE TABLE " + quote(database)
							+ ".child(id INT PRIMARY KEY,parent_id INT REFERENCES t(id),value TEXT)");
					statement.execute("INSERT INTO " + quote(database) + ".child VALUES(10,1,'" + database + "')");
				}
			}
			var parent = read(connection, schema);
			var childReader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
			childReader.setSchemaName(schema);
			childReader.setObjectName("child");
			var child = childReader.getAllFull(connection).get(0);
			var owner = new com.sqlapp.data.schemas.Schema(schema);
			owner.getTables().add(parent);
			owner.getTables().add(child);
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(false);
			var row = parent.getRows().newElement();
			row.put("id", 1);
			parent.getRows().add(row);
			try (var parameters = registry.createSqlNodes(parent, SqlType.SELECT_BY_ROWS).get(0).eval(parent);
					var query = parameters.createStatement(connection)) {
				parameters.setBind(query);
				try (var rows = query.executeQuery()) {
					assertTrue(rows.next());
					assertEquals(schema, rows.getString("value"));
					assertFalse(rows.next());
				}
			}
			var holder = new com.sqlapp.data.schemas.TableRelationTreeHolder(parent, child);
			for (var table : List.of(child, parent)) {
				var relation = holder.getTableRelation(table);
				try (var parameters = registry.createSqlNodes(relation, SqlType.SELECT_BY_ROOT_ROWS).get(0)
						.eval(relation, List.of(row)); var query = parameters.createStatement(connection)) {
					parameters.setBind(query);
					try (var rows = query.executeQuery()) {
						assertTrue(rows.next());
						assertEquals(schema, rows.getString("value"));
						assertFalse(rows.next());
					}
				}
				var delete = registry.createSqlNodes(relation, SqlType.DELETE_BY_ROOT_ROWS).get(0);
				try (var parameters = delete.eval(relation, List.of());
						var query = parameters.createStatement(connection)) {
					parameters.setBind(query);
					assertEquals(0, query.executeUpdate());
				}
				try (var parameters = delete.eval(relation, List.of(row));
						var query = parameters.createStatement(connection)) {
					parameters.setBind(query);
					assertEquals(1, query.executeUpdate());
				}
			}
			for (String database : SCHEMAS) {
				assertCount(connection, database, database.equals(schema) ? 0 : 1);
				try (var statement = connection.createStatement();
						var rows = statement.executeQuery("SELECT count(*) FROM " + quote(database) + ".child")) {
					assertTrue(rows.next());
					assertEquals(database.equals(schema) ? 0 : 1, rows.getInt(1));
				}
			}
		}
	}

	@ParameterizedTest
	@CsvSource({ "main,false", "main,true", "temp,false", "temp,true", "other\" db,false", "other\" db,true" })
	void keyedAndWholeTableDmlKeepExplicitSchemaWithShadowTables(String schema, boolean decorate) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
			setup(connection);
			var table = read(connection, schema);
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(decorate);
			var row = table.getRows().newElement();
			row.put("id", 2);
			row.put("value", "inserted");
			write(connection, registry, table, SqlType.INSERT, row);
			assertCount(connection, schema, 2);
			row.put("value", "updated");
			write(connection, registry, table, SqlType.UPDATE, row);
			try (var parameters = registry.createSqlNodes(table, SqlType.SELECT).get(0).eval(row);
					var query = parameters.createStatement(connection)) {
				parameters.setBind(query);
				try (var rows = query.executeQuery()) {
					assertTrue(rows.next());
					assertEquals("updated", rows.getString("value"));
					assertFalse(rows.next());
				}
			}
			row.put("value", "merged");
			write(connection, registry, table, SqlType.MERGE, row);
			try (var parameters = registry.createSqlNodes(table, SqlType.SELECT_TABLE).get(0)
					.eval(new ParametersContext(table)); var query = parameters.createStatement(connection)) {
				parameters.setBind(query);
				try (var rows = query.executeQuery()) {
					int count = 0;
					while (rows.next())
						count++;
					assertEquals(2, count);
				}
			}
			var context = new ParametersContext(table);
			context.put("_orderBy", "id");
			context.put("_row", 1);
			context.put("_offset", 1);
			try (var parameters = registry.createSqlNodes(table, SqlType.SELECT_FOR_APP).get(0).eval(context);
					var query = parameters.createStatement(connection)) {
				parameters.setBind(query);
				try (var rows = query.executeQuery()) {
					assertTrue(rows.next());
					assertEquals(2, rows.getInt("id"));
					assertEquals("merged", rows.getString("value"));
					assertFalse(rows.next());
				}
			}
			context.setCountSqlMode();
			try (var parameters = registry.createSqlNodes(table, SqlType.SELECT_FOR_APP).get(0).eval(context);
					var query = parameters.createStatement(connection)) {
				parameters.setBind(query);
				try (var rows = query.executeQuery()) {
					assertTrue(rows.next());
					assertEquals(2, rows.getInt(1));
				}
			}
			write(connection, registry, table, SqlType.DELETE, row);
			assertCount(connection, schema, 1);
			for (String other : SCHEMAS)
				try (var statement = connection.createStatement();
						var rows = statement.executeQuery("SELECT value FROM " + quote(other) + ".t")) {
					assertTrue(rows.next());
					assertEquals(other, rows.getString(1));
					assertFalse(rows.next());
				}
		}
	}

	@ParameterizedTest
	@EnumSource(value = SqlType.class, names = { "INSERT_ROWS", "MERGE_ROWS" })
	void batchInsertAndUpsertKeepMainWhenTempShadowsIt(SqlType type) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
			setup(connection);
			var table = read(connection, "main");
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(false);
			for (int id : type == SqlType.INSERT_ROWS ? new int[] { 2, 3 } : new int[] { 1, 3 }) {
				var row = table.getRows().newElement();
				row.put("id", id);
				row.put("value", "batch");
				table.getRows().add(row);
			}
			try (var parameters = registry.createSqlNodes(table, type).get(0).eval(table);
					var insert = parameters.createStatement(connection)) {
				parameters.setBind(insert);
				assertEquals(2, insert.executeUpdate());
			}
			assertCount(connection, "main", type == SqlType.INSERT_ROWS ? 3 : 2);
			assertCount(connection, "temp", 1);
			assertCount(connection, SCHEMAS.get(2), 1);
		}
	}

	private void setup(Connection connection) throws Exception {
		try (var statement = connection.createStatement()) {
			statement.execute("ATTACH ':memory:' AS " + quote(SCHEMAS.get(2)));
			for (String schema : SCHEMAS) {
				statement.execute("CREATE TABLE " + quote(schema) + ".t(id INT PRIMARY KEY,value TEXT)");
				statement.execute("INSERT INTO " + quote(schema) + ".t VALUES(1,'" + schema + "')");
			}
		}
	}

	private Table read(Connection connection, String schema) {
		var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
		reader.setSchemaName(schema);
		reader.setObjectName("t");
		return reader.getAllFull(connection).get(0);
	}

	private void write(Connection connection, SqlFactoryRegistry registry, Table table, SqlType type, Object row)
			throws Exception {
		try (var parameters = registry.createSqlNodes(table, type).get(0).eval(row);
				var query = parameters.createStatement(connection)) {
			parameters.setBind(query);
			assertEquals(1, query.executeUpdate());
		}
	}

	private void assertCount(Connection connection, String schema, int expected) throws Exception {
		try (var statement = connection.createStatement();
				var rows = statement.executeQuery("SELECT count(*) FROM " + quote(schema) + ".t")) {
			assertTrue(rows.next());
			assertEquals(expected, rows.getInt(1));
		}
	}

	private static String quote(String name) {
		return "\"" + name.replace("\"", "\"\"") + "\"";
	}
}
