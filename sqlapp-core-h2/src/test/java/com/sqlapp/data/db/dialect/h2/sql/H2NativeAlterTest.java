/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.h2.sql;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.DriverManager;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;

class H2NativeAlterTest {
	static Stream<Arguments> cases() {
		return Stream.of("PUBLIC", "odd\" schema")
				.flatMap(schema -> Stream
						.of("add", "drop", "rename", "renameType", "type", "nullable", "default", "dropDefault",
								"onUpdate", "dropOnUpdate", "tableRename", "generated", "index", "check")
						.map(action -> Arguments.of(schema, action)));
	}

	@ParameterizedTest
	@MethodSource("cases")
	void appliesModeledChangesAndPreservesRows(String schema, String action) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:h2:mem:");
				var statement = connection.createStatement()) {
			if (!schema.equals("PUBLIC"))
				statement.execute("CREATE SCHEMA " + H2NativeDmlTest.quote(schema));
			String prefix = H2NativeDmlTest.quote(schema) + ".";
			statement.execute("CREATE TABLE " + prefix
					+ "T(ID INT PRIMARY KEY,C VARCHAR(10) DEFAULT 'old' NOT NULL,U VARCHAR(20) DEFAULT 'orig' ON UPDATE 'changed')");
			statement.execute("INSERT INTO " + prefix + "T VALUES(1,'kept','original')");
			var original = H2NativeDmlTest.read(connection, schema, "T");
			var target = original.clone();
			switch (action) {
			case "add" ->
				target.getColumns().add(new Column("added column").setDataType(DataType.INT).setDefaultValue("7"));
			case "drop" -> target.getColumns().remove("U");
			case "rename" -> target.getColumns().get("C").setName("new\" column");
			case "renameType" -> {
				target.getColumns().get("C").setName("new\" column");
				target.getColumns().get("new\" column").setLength(30);
			}
			case "type" -> target.getColumns().get("C").setLength(30);
			case "nullable" -> target.getColumns().get("C").setNullable(true);
			case "default" -> target.getColumns().get("C").setDefaultValue("'new'");
			case "dropDefault" -> target.getColumns().get("C").setDefaultValue(null);
			case "onUpdate" -> target.getColumns().get("C").setOnUpdate("'updated'");
			case "dropOnUpdate" -> target.getColumns().get("U").setOnUpdate(null);
			case "tableRename" -> target.setName("renamed\" table");
			case "generated" -> target.getColumns().add(new Column("G").setDataType(DataType.INT).setFormula("ID+1"));
			case "index" -> target.getIndexes().add("index name", target.getColumns().get("C"));
			case "check" ->
				target.getConstraints().add(new com.sqlapp.data.schemas.CheckConstraint("check name", "ID>0"));
			}
			var registry = DialectResolver.getInstance().getDialect(connection).createSqlFactoryRegistry();
			registry.getOptions().setDecorateSchemaName(true);
			if (action.equals("renameType")) {
				assertThrows(IllegalArgumentException.class,
						() -> registry.createSql(original.diff(target), SqlType.ALTER));
				try (var rows = statement.executeQuery("SELECT C FROM " + prefix + "T")) {
					assertTrue(rows.next());
					assertEquals("kept", rows.getString(1));
				}
				var renamed = original.clone();
				renamed.getColumns().get("C").setName("new\" column");
				for (var operation : registry.createSql(original.diff(renamed), SqlType.ALTER))
					statement.execute(operation.getSqlText());
				for (var operation : registry.createSql(renamed.diff(target), SqlType.ALTER))
					statement.execute(operation.getSqlText());
			} else {
				var operations = registry.createSql(original.diff(target), SqlType.ALTER);
				assertFalse(operations.isEmpty());
				for (var operation : operations)
					statement.execute(operation.getSqlText());
			}
			var actual = H2NativeDmlTest.read(connection, schema, target.getName());
			assertEquals(target.getColumns().size(), actual.getColumns().size());
			for (var column : target.getColumns()) {
				var found = actual.getColumns().get(column.getName());
				assertNotNull(found, column.getName());
				assertEquals(column.getLength(), found.getLength(), column.getName() + " length");
				assertEquals(column.getDefaultValue(), found.getDefaultValue(), column.getName() + " default");
				assertEquals(column.isNotNull(), found.isNotNull(), column.getName() + " nullable");
				assertEquals(column.getOnUpdate(), found.getOnUpdate(), column.getName() + " ON UPDATE");
			}
			try (var rows = statement
					.executeQuery("SELECT ID FROM " + prefix + H2NativeDmlTest.quote(target.getName()))) {
				assertTrue(rows.next());
				assertEquals(1, rows.getInt(1));
				assertFalse(rows.next());
			}
			String content = action.startsWith("rename") ? "new\" column" : "C";
			try (var rows = statement.executeQuery("SELECT " + H2NativeDmlTest.quote(content) + " FROM " + prefix
					+ H2NativeDmlTest.quote(target.getName()))) {
				assertTrue(rows.next());
				assertEquals("kept", rows.getString(1));
			}
		}
	}
}
