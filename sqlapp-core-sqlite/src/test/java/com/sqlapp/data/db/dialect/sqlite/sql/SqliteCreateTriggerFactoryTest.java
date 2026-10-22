/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import java.sql.DriverManager;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.sqlapp.data.db.dialect.sqlite.DialectHolder;
import com.sqlapp.data.db.dialect.sqlite.util.SqliteDdl;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Trigger;

class SqliteCreateTriggerFactoryTest {
	@ParameterizedTest
	@ValueSource(strings = { "main", "temp", "other'\" db" })
	void recreatesUnqualifiedTemporaryTriggerOnResolvedDatabaseInAnotherConnection(String schema) throws Exception {
		Trigger exported;
		String attached = "other'\" db";
		try (var source = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = source.createStatement()) {
			statement.execute("ATTACH ':memory:' AS " + quote(attached));
			statement.execute("CREATE TABLE " + quote(schema) + ".source(\"ON\" TEXT)");
			statement.execute("CREATE TEMP TABLE audit(value TEXT)");
			statement.execute("""
					CREATE TEMP TRIGGER IF NOT EXISTS "tr ON" AFTER UPDATE OF "ON" /* ON fake */ ON [source]
					WHEN NEW."ON" <> 'skip' BEGIN
					INSERT INTO audit VALUES (NEW."ON" || ': ON source');
					END
					""");
			var reader = DialectHolder.defaultDialect.getCatalogReader().getSchemaReader().getTriggerReader();
			reader.setSchemaName("temp");
			exported = SchemaUtils.readXml(new StringReader(reader.getAllFull(source).get(0).asXml()));
			assertEquals(schema, exported.getTableSchemaName());
			assertTrue(String.join("\n", exported.getDefinition()).contains("ON [source]"));
		}
		try (var target = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = target.createStatement()) {
			statement.execute("ATTACH ':memory:' AS " + quote(attached));
			statement.execute("CREATE TEMP TABLE audit(value TEXT)");
			for (String database : List.of("main", "temp", attached))
				statement.execute("CREATE TABLE " + quote(database) + ".source(\"ON\" TEXT)");
			for (var operation : DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(exported,
					SqlType.CREATE)) {
				assertTrue(operation.getSqlText().contains("ON " + quote(schema) + ".[source]"));
				statement.execute(operation.getSqlText());
			}
			for (String database : List.of("main", "temp", attached)) {
				statement.execute("INSERT INTO " + quote(database) + ".source VALUES ('before')");
				statement.execute("UPDATE " + quote(database) + ".source SET \"ON\"='skip'");
				statement.execute(
						"UPDATE " + quote(database) + ".source SET \"ON\"='" + database.replace("'", "''") + "'");
			}
			try (var rows = statement.executeQuery("SELECT value FROM temp.audit")) {
				assertTrue(rows.next());
				assertEquals(schema + ": ON source", rows.getString(1));
				assertFalse(rows.next());
			}
		}
	}

	@Test
	void qualificationPreservesExplicitTargetAndOpaqueBody() {
		String ddl = "CREATE TEMP TRIGGER IF NOT EXISTS temp.[BEGIN] INSTEAD OF DELETE /* ON fake */ ON `view ON` "
				+ "BEGIN SELECT 'ON main.t', CASE WHEN 1 THEN 'END' END; END";
		String qualified = SqliteDdl.qualifyTriggerTarget(ddl, "other\"db");
		assertEquals(ddl.replace("ON `view ON`", "ON \"other\"\"db\".`view ON`"), qualified);
		assertEquals("other\"db", SqliteDdl.triggerTargetSchema(qualified));
		assertEquals(qualified, SqliteDdl.qualifyTriggerTarget(qualified, "different"));
		assertThrows(IllegalArgumentException.class, () -> SqliteDdl.qualifyTriggerTarget(ddl, " "));
		assertThrows(IllegalArgumentException.class, () -> SqliteDdl.qualifyTriggerTarget(ddl, "bad\0name"));
	}

	@Test
	void ordinaryAndDefinitionOnlyTriggersRetainTheirOriginalOnClause() {
		for (String schema : List.of("main", "temp")) {
			Trigger trigger = new Trigger("tr");
			trigger.setSchemaName(schema);
			trigger.addDefinition("CREATE TRIGGER tr AFTER INSERT ON source BEGIN SELECT 'ON'; END");
			if (schema.equals("main"))
				trigger.setTable(new Table("source").setSchemaName("main"));
			String sql = DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(trigger, SqlType.CREATE)
					.get(0).getSqlText();
			assertTrue(sql.contains("ON source BEGIN SELECT 'ON'; END"));
		}
	}

	private static String quote(String name) {
		return "\"" + name.replace("\"", "\"\"") + "\"";
	}
}
