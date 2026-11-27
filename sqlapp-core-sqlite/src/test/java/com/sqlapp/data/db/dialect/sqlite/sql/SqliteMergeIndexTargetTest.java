/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.EnumSource;

import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;

class SqliteMergeIndexTargetTest {
	private static final List<String> DATABASES = List.of("main", "temp", "other\" db");

	static Stream<Arguments> targets() {
		return DATABASES.stream().flatMap(
				database -> Stream.of(SqlType.MERGE, SqlType.MERGE_ROWS, SqlType.MERGE_TABLE).flatMap(type -> Stream
						.of("partial", "expression", "both").map(kind -> Arguments.of(database, type, kind))));
	}

	@ParameterizedTest
	@MethodSource("targets")
	void executesModeledIndexTargetAfterXmlImport(String database, SqlType type, String kind) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("PRAGMA foreign_keys=ON");
			statement.execute("ATTACH ':memory:' AS " + quote(DATABASES.get(2)));
			boolean partial = !kind.equals("expression"), expression = !kind.equals("partial");
			for (String name : DATABASES) {
				String prefix = quote(name) + ".";
				statement.execute("CREATE TABLE " + prefix
						+ "t(id INTEGER PRIMARY KEY, email TEXT, namespace TEXT, active INT, payload TEXT)");
				statement.execute("CREATE UNIQUE INDEX " + prefix + "\"chosen index\" ON t("
						+ (expression ? "lower(email)" : "email COLLATE NOCASE DESC") + ", namespace)"
						+ (partial ? " WHERE active=1" : ""));
				statement.execute("CREATE TABLE " + prefix + "child(parent_id INT REFERENCES t(id) ON DELETE CASCADE)");
				statement.execute("INSERT INTO " + prefix + "t VALUES(10,'Existing','n',1,'old')");
				statement.execute("INSERT INTO " + prefix + "child VALUES(10)");
			}
			var table = read(connection, database, "t");
			table = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
			for (var values : partial
					? List.of(List.of("existing", "new", 1), List.of("fresh", "insert", 1),
							List.of("existing", "outside", 0))
					: List.of(List.of("existing", "new", 1), List.of("fresh", "insert", 1))) {
				var row = table.getRows().newElement();
				row.put("email", values.get(0));
				row.put("namespace", "n");
				row.put("active", values.get(2));
				row.put("payload", values.get(1));
				table.getRows().add(row);
			}
			String xml = table.asXml();
			var registry = (SqliteSqlFactoryRegistry) DialectHolder.defaultDialect.createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(false);
			registry.setMergeConflictIndexName("chosen index");
			var factory = (SqliteMergeFactory) registry.getSqlFactory(table, type);
			if (type == SqlType.MERGE_TABLE) {
				statement.execute(
						"CREATE TEMP TABLE stage(id INT, email TEXT, namespace TEXT, active INT, payload TEXT)");
				for (var row : table.getRows()) {
					try (var insert = connection.prepareStatement("INSERT INTO temp.stage VALUES(NULL,?,?,?,?)")) {
						insert.setObject(1, row.get("email"));
						insert.setObject(2, row.get("namespace"));
						insert.setObject(3, row.get("active"));
						insert.setObject(4, row.get("payload"));
						insert.executeUpdate();
					}
				}
				var operations = ((SqliteMergeTableFactory) factory).createSql(table,
						read(connection, "temp", "stage"));
				assertEquals(table.getRows().size(), statement.executeUpdate(operations.get(0).getSqlText()));
			} else {
				var node = registry.createSqlNodes(table, type).get(0);
				if (type == SqlType.MERGE_ROWS) {
					try (var parameters = node.eval(table); var merge = parameters.createStatement(connection)) {
						assertEquals(1, parameters.getSql().split("ON CONFLICT", -1).length - 1);
						parameters.setBind(merge);
						assertEquals(table.getRows().size(), merge.executeUpdate());
					}
				} else {
					for (var row : table.getRows()) {
						try (var parameters = node.eval(row); var merge = parameters.createStatement(connection)) {
							parameters.setBind(merge);
							assertEquals(1, merge.executeUpdate());
						}
					}
				}
			}
			assertEquals(xml, table.asXml());
			for (String name : DATABASES) {
				String prefix = quote(name) + ".";
				try (var rows = statement.executeQuery("SELECT payload FROM " + prefix + "t WHERE id=10")) {
					assertTrue(rows.next());
					assertEquals(name.equals(database) ? "new" : "old", rows.getString(1));
					assertFalse(rows.next());
				}
				try (var rows = statement.executeQuery("SELECT count(*) FROM " + prefix + "t")) {
					assertTrue(rows.next());
					assertEquals(name.equals(database) ? (partial ? 3 : 2) : 1, rows.getInt(1));
				}
				try (var rows = statement.executeQuery("SELECT parent_id FROM " + prefix + "child")) {
					assertTrue(rows.next());
					assertEquals(10, rows.getInt(1));
					assertFalse(rows.next());
				}
			}
		}
	}

	@Test
	void invalidSelectionsAndOldVersionsFailWithoutFallingBackToAnotherKey() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(id INT PRIMARY KEY, value TEXT, payload TEXT)");
			statement.execute("CREATE INDEX ordinary ON t(value)");
			statement.execute("CREATE UNIQUE INDEX chosen ON t(lower(value)) WHERE value IS NOT NULL");
			var table = read(connection, "main", "t");
			var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
			var factory = (SqliteMergeFactory) registry.getSqlFactory(table, SqlType.MERGE);
			for (String name : List.of("", " ", "missing", "ordinary")) {
				factory.setConflictIndexName(name);
				assertThrows(IllegalArgumentException.class, () -> factory.createSql(table));
			}
			factory.setConflictIndexSelector(t -> "chosen");
			assertTrue(factory.createSql(table).get(0).getSqlText().contains("lower(value)"));
			table.getIndexes().get("chosen").getSpecifics().remove("sqlite.index.keys");
			assertThrows(IllegalArgumentException.class, () -> factory.createSql(table));
			factory.setConflictIndexName((String) null);
			assertTrue(factory.createSql(table).get(0).getSqlText().contains("ON CONFLICT"));
			var old = DialectResolver.getInstance().getDialect("SQLite", 3, 23, 0).createSqlFactoryRegistry();
			var oldFactory = (SqliteMergeFactory) old.getSqlFactory(table, SqlType.MERGE);
			oldFactory.setConflictIndexName("chosen");
			assertThrows(IllegalArgumentException.class, () -> oldFactory.createSql(table));
		}
	}

	private Table read(Connection connection, String database, String name) {
		var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTableReader();
		reader.setSchemaName(database);
		reader.setObjectName(name);
		return reader.getAllFull(connection).get(0);
	}

	@ParameterizedTest
	@EnumSource(value = SqlType.class, names = { "MERGE_ROWS", "MERGE_TABLE" })
	void anotherUniqueViolationAbortsTheWholeStatement(SqlType type) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(email TEXT, payload TEXT UNIQUE)");
			statement.execute("CREATE UNIQUE INDEX chosen ON t(lower(email)) WHERE email IS NOT NULL");
			statement.execute("INSERT INTO t VALUES('Existing','old')");
			var table = read(connection, "main", "t");
			for (String email : List.of("existing", "fresh")) {
				var row = table.getRows().newElement();
				row.put("email", email);
				row.put("payload", "same");
				table.getRows().add(row);
			}
			var registry = (SqliteSqlFactoryRegistry) DialectHolder.defaultDialect.createSqlFactoryRegistry();
			registry.setMergeConflictIndexSelector(target -> target.getName().equals("t") ? "chosen" : null);
			if (type == SqlType.MERGE_TABLE) {
				statement.execute("CREATE TEMP TABLE t_temp(email TEXT, payload TEXT)");
				statement.execute("INSERT INTO temp.t_temp VALUES('existing','same'),('fresh','same')");
				String sql = registry.createSql(table, type).get(0).getSqlText();
				assertThrows(java.sql.SQLException.class, () -> statement.executeUpdate(sql));
			} else {
				try (var parameters = registry.createSqlNodes(table, type).get(0).eval(table);
						var merge = parameters.createStatement(connection)) {
					parameters.setBind(merge);
					assertThrows(java.sql.SQLException.class, merge::executeUpdate);
				}
			}
			try (var rows = statement.executeQuery("SELECT email,payload FROM t")) {
				assertTrue(rows.next());
				assertEquals("Existing", rows.getString(1));
				assertEquals("old", rows.getString(2));
				assertFalse(rows.next());
			}
		}
	}

	@Test
	void keyOnlyInputUsesDoNothingAndNativeNullUniqueness() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE t(email TEXT)");
			statement.execute("CREATE UNIQUE INDEX chosen ON t(email COLLATE NOCASE)");
			statement.execute("INSERT INTO t VALUES('Existing')");
			var table = read(connection, "main", "t");
			var registry = (SqliteSqlFactoryRegistry) DialectHolder.defaultDialect.createSqlFactoryRegistry();
			registry.setMergeConflictIndexName("chosen");
			registry.getTableOptions().setParameterExpression((column, value) -> "?");
			String sql = registry.createSql(table, SqlType.MERGE).get(0).getSqlText();
			assertTrue(sql.contains("DO NOTHING"));
			try (var merge = connection.prepareStatement(sql)) {
				merge.setString(1, "existing");
				assertEquals(0, merge.executeUpdate());
				merge.setNull(1, java.sql.Types.VARCHAR);
				assertEquals(1, merge.executeUpdate());
				assertEquals(1, merge.executeUpdate());
			}
			try (var rows = statement.executeQuery("SELECT count(*),count(email) FROM t")) {
				assertTrue(rows.next());
				assertEquals(3, rows.getInt(1));
				assertEquals(1, rows.getInt(2));
			}
			registry.setMergeConflictIndexName(null);
			assertTrue(registry.createSql(table, SqlType.MERGE).get(0).getSqlText().contains("DO NOTHING"));
		}
	}

	private static String quote(String name) {
		return "\"" + name.replace("\"", "\"\"") + "\"";
	}
}
