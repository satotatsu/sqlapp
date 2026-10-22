/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.h2.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.DriverManager;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.AbstractNamedObject;
import com.sqlapp.data.db.sql.TableLockMode;

class H2NativeOperationsTest {
	@ParameterizedTest
	@ValueSource(strings = { "table", "column", "view", "index", "domain", "sequence", "alias", "trigger", "constant",
			"schema" })
	void setsAndRemovesQuotedNativeComments(String kind) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:h2:mem:");
				var statement = connection.createStatement()) {
			String schema = "odd\" schema", prefix = H2NativeDmlTest.quote(schema) + ".";
			statement.execute("CREATE SCHEMA " + H2NativeDmlTest.quote(schema));
			statement.execute("CREATE DOMAIN " + prefix + "D AS INT");
			statement.execute("CREATE TABLE " + prefix + "T(ID INT,C VARCHAR(40))");
			statement.execute("CREATE VIEW " + prefix + "V AS SELECT ID FROM " + prefix + "T");
			statement.execute("CREATE INDEX " + prefix + "IX ON " + prefix + "T(C)");
			statement.execute("CREATE SEQUENCE " + prefix + "S");
			statement.execute("CREATE ALIAS " + prefix + "HEX FOR 'java.lang.Integer.toHexString'");
			statement.execute("CREATE CONSTANT " + prefix + "N VALUE 1");
			statement.execute("CREATE TRIGGER " + prefix + "TR BEFORE INSERT ON " + prefix
					+ "T CALL 'com.sqlapp.data.db.dialect.h2.metadata.H2DialectRoundTripTest$CountingTrigger'");
			var reader = DialectResolver.getInstance().getDialect(connection).getCatalogReader().getSchemaReader();
			reader.setSchemaName(schema);
			var model = reader.getAllFull(connection).get(0);
			AbstractNamedObject<?> object = switch (kind) {
			case "table" -> model.getTables().get("T");
			case "column" -> model.getTables().get("T").getColumns().get("C");
			case "view" -> model.getViews().get("V");
			case "index" -> model.getTables().get("T").getIndexes().get("IX");
			case "domain" -> model.getDomains().get("D");
			case "sequence" -> model.getSequences().get("S");
			case "alias" -> model.getFunctions().get("HEX");
			case "trigger" -> model.getTriggers().get("TR");
			case "constant" -> model.getConstants().get("N");
			default -> model;
			};
			String catalog = switch (kind) {
			case "table", "view" -> "TABLES";
			case "column" -> "COLUMNS";
			case "index" -> "INDEXES";
			case "domain" -> "DOMAINS";
			case "sequence" -> "SEQUENCES";
			case "alias" -> "ROUTINES";
			case "trigger" -> "TRIGGERS";
			case "constant" -> "CONSTANTS";
			default -> "SCHEMATA";
			};
			String identity = switch (kind) {
			case "table", "view" -> "TABLE_NAME";
			case "column" -> "COLUMN_NAME";
			case "index" -> "INDEX_NAME";
			case "domain" -> "DOMAIN_NAME";
			case "sequence" -> "SEQUENCE_NAME";
			case "alias" -> "ROUTINE_NAME";
			case "trigger" -> "TRIGGER_NAME";
			case "constant" -> "CONSTANT_NAME";
			default -> "SCHEMA_NAME";
			};
			var registry = DialectResolver.getInstance().getDialect(connection).createSqlFactoryRegistry();
			for (String remarks : new String[] { "日本語 ' comment; --", null }) {
				object.setRemarks(remarks);
				var operations = registry.createSql(object, SqlType.SET_COMMENT);
				assertEquals(1, operations.size());
				statement.execute(operations.get(0).getSqlText());
				try (var query = connection.prepareStatement(
						"SELECT REMARKS FROM INFORMATION_SCHEMA." + catalog + " WHERE " + identity + "=?")) {
					query.setString(1, object.getName());
					try (var rows = query.executeQuery()) {
						assertTrue(rows.next());
						assertEquals(remarks, rows.getString(1));
					}
				}
			}
		}
	}

	@ParameterizedTest
	@ValueSource(strings = { "PUBLIC", "odd\" schema" })
	void executesQualifiedSequenceRangesAndTableStatistics(String schema) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:h2:mem:");
				var statement = connection.createStatement()) {
			if (!schema.equals("PUBLIC"))
				statement.execute("CREATE SCHEMA " + H2NativeDmlTest.quote(schema));
			String prefix = H2NativeDmlTest.quote(schema) + ".";
			statement.execute("CREATE SEQUENCE " + prefix + "\"odd sequence\" START WITH 42 INCREMENT BY 2");
			statement.execute("CREATE TABLE " + prefix + "T(ID INT)");
			statement.execute("INSERT INTO " + prefix + "T VALUES(1),(1),(2)");
			var dialect = DialectResolver.getInstance().getDialect(connection);
			var registry = dialect.createSqlFactoryRegistry();
			var reader = dialect.getCatalogReader().getSchemaReader().getSequenceReader();
			reader.setSchemaName(schema);
			var sequence = reader.getAllFull(connection).get(0);
			try (var parameters = registry.createSqlNodes(sequence, SqlType.SEQUENCE_NEXT_VALUES).get(0).eval(3);
					var query = parameters.createStatement(connection)) {
				parameters.setBind(query);
				try (var rows = query.executeQuery()) {
					for (int expected : new int[] { 42, 44, 46 }) {
						assertTrue(rows.next());
						assertEquals(expected, rows.getInt(1));
					}
					assertFalse(rows.next());
				}
			}
			try (var rows = statement.executeQuery(dialect.getSequenceNextValString(prefix + "\"odd sequence\""))) {
				assertTrue(rows.next());
				assertEquals(48, rows.getInt(1));
			}
			var table = H2NativeDmlTest.read(connection, schema, "T");
			var analyze = registry.createSql(table, SqlType.ANALYZE);
			assertEquals(1, analyze.size());
			statement.execute(analyze.get(0).getSqlText());
			try (var rows = statement.executeQuery("SELECT count(*) FROM " + prefix + "T")) {
				assertTrue(rows.next());
				assertEquals(3, rows.getInt(1));
			}
		}
	}

	@Test
	void modernAliasesWithOverloadsAreRecreatedOnceWithoutIfNotExists() throws Exception {
		try (var source = DriverManager.getConnection("jdbc:h2:mem:");
				var target = DriverManager.getConnection("jdbc:h2:mem:");
				var statement = source.createStatement();
				var destination = target.createStatement()) {
			statement.execute(
					"CREATE ALIAS F FOR 'com.sqlapp.data.db.dialect.h2.metadata.H2DialectRoundTripTest.overloaded'");
			var reader = DialectResolver.getInstance().getDialect(source).getCatalogReader().getSchemaReader()
					.getFunctionReader();
			reader.setSchemaName("PUBLIC");
			var aliases = reader.getAllFull(source);
			assertEquals(1, aliases.size());
			assertFalse(aliases.get(0).getDefinition().isEmpty());
			var registry = DialectResolver.getInstance().getDialect(target).createSqlFactoryRegistry();
			registry.getOptions().setCreateIfNotExists(false);
			for (var alias : aliases)
				for (var operation : registry.createSql(alias, SqlType.CREATE))
					destination.execute(operation.getSqlText());
			try (var rows = destination.executeQuery("SELECT F(3),F(3,4)")) {
				assertTrue(rows.next());
				assertEquals(3, rows.getInt(1));
				assertEquals(7, rows.getInt(2));
			}
		}
	}

	@Test
	void retainsLegacyResolutionAndRejectsUnrepresentableRequests() throws Exception {
		var old = DialectResolver.getInstance().getDialect("H2", 1, 4, 200);
		var modern = DialectResolver.getInstance().getDialect("H2", 2, 3, 232);
		assertFalse(old instanceof com.sqlapp.data.db.dialect.h2.H2_200);
		assertTrue(modern instanceof com.sqlapp.data.db.dialect.h2.H2_200);
		assertEquals("CALL IDENTITY()", old.getIdentitySelectString());
		assertThrows(UnsupportedOperationException.class, modern::getIdentitySelectString);
		assertNull(old.getCatalogReader().getSchemaReader().getSynonymReader());
		assertNotNull(modern.getCatalogReader().getSchemaReader().getSynonymReader());
		for (var dialect : List.of(old, modern))
			for (String name : List.of("SELECT", "VALUE", "a\"b", "mixedCase", "日本語", "12NAME", "123")) {
				assertTrue(dialect.needQuote(name));
				assertEquals(name, dialect.unQuote(dialect.quote(name)));
			}
		var registry = modern.createSqlFactoryRegistry();
		var table = new com.sqlapp.data.schemas.Table("T").setSchemaName("PUBLIC");
		registry.getTableOptions().setLockMode(item -> TableLockMode.SHARE);
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(table, SqlType.LOCK));
		var trigger = new com.sqlapp.data.schemas.Trigger("TR").setSchemaName("PUBLIC").setTableSchemaName("PUBLIC")
				.setTableName("T").setActionTiming("BEFORE");
		trigger.getEventManipulation().add("INSERT");
		assertThrows(IllegalArgumentException.class, () -> registry.createSql(trigger, SqlType.CREATE));
	}
}
