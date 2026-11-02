/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Connection;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.function.Executable;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

/**
 * Generated DDL, real catalog reading and recreation in owned test containers.
 */
public final class DataTypeServerAssertions {
	private DataTypeServerAssertions() {
	}

	public static void verify(Connection connection, String... nativeTypes) throws Exception {
		verify(connection, DialectResolver.getInstance().getDialect(connection), nativeTypes);
	}

	public static void verify(Connection connection, com.sqlapp.data.db.dialect.Dialect dialect, String... nativeTypes)
			throws Exception {
		System.out.println("Data type server: " + connection.getMetaData().getDatabaseProductVersion());
		List<Executable> checks = new ArrayList<>();
		for (String nativeType : nativeTypes) {
			checks.add(() -> {
				var column = new Column("C");
				assertTrue(dialect.setDbType(nativeType, null, null, column), nativeType);
				verifyColumn(connection, dialect, column, nativeType);
			});
		}
		for (String source : new String[] { null, "DOUBLE", "DOUBLE PRECISION" }) {
			checks.add(() -> verifyColumn(connection, dialect,
					new Column("C").setDataType(DataType.DOUBLE).setDataTypeName(source),
					"portable DOUBLE: " + source));
		}
		for (var type : List.of(DataType.TINYINT, DataType.SMALLINT, DataType.INT, DataType.BIGINT))
			checks.add(() -> verifyColumn(connection, dialect, new Column("C").setDataType(type),
					"portable signed " + type));
		assertAll(dialect.getClass().getSimpleName(), checks);
	}

	private static void verifyColumn(Connection connection, com.sqlapp.data.db.dialect.Dialect dialect, Column column,
			String context) throws Exception {
		System.out.println("Checking data type: " + context);
		var registry = dialect.createSqlFactoryRegistry();
		var name = "SQLAPP_DT_" + Long.toUnsignedString(System.nanoTime(), 36).toUpperCase(java.util.Locale.ROOT);
		var table = new Table(name);
		table.getColumns().add(column);
		String qualified = dialect.quote(name);
		String valueColumn = dialect.quote(column.getName());
		try (var statement = connection.createStatement()) {
			for (var operation : registry.createSql(table, SqlType.CREATE))
				statement.execute(operation.getSqlText());
			try {
				if (column.getArrayDimension() == 0 && (column.getDataType() == DataType.DOUBLE
						|| column.getDataType() == DataType.REAL || column.getDataType() == DataType.FLOAT)) {
					statement.executeUpdate("INSERT INTO " + qualified + " VALUES (1.25)");
					try (var rows = statement.executeQuery("SELECT " + valueColumn + " FROM " + qualified)) {
						assertTrue(rows.next(), context);
						assertEquals(1.25, rows.getDouble(1), 0.000001, context);
					}
				}
				long[] bounds = switch (column.getDataType()) {
				case TINYINT -> new long[] { Byte.MIN_VALUE, Byte.MAX_VALUE };
				case UTINYINT -> new long[] { 0, 255 };
				case SMALLINT -> new long[] { Short.MIN_VALUE, Short.MAX_VALUE };
				case USMALLINT -> new long[] { 0, 65535 };
				case INT -> new long[] { Integer.MIN_VALUE, Integer.MAX_VALUE };
				case UINT -> new long[] { 0, 4294967295L };
				case BIGINT -> new long[] { Long.MIN_VALUE, Long.MAX_VALUE };
				default -> new long[0];
				};
				if (column.getArrayDimension() > 0)
					bounds = new long[0];
				// Informix's documented native ranges start at Java's minimum + 1.
				if (bounds.length > 0
						&& connection.getMetaData().getDatabaseProductName().toLowerCase(java.util.Locale.ROOT)
								.contains("informix")
						&& java.util.Set.of(DataType.SMALLINT, DataType.INT, DataType.BIGINT)
								.contains(column.getDataType()))
					bounds[0]++;
				for (long value : bounds) {
					try (var insert = connection.prepareStatement("INSERT INTO " + qualified + " VALUES (?)")) {
						insert.setLong(1, value);
						assertEquals(1, insert.executeUpdate(), context);
					}
				}
				if (bounds.length > 0) {
					try (var rows = statement.executeQuery(
							"SELECT " + valueColumn + " FROM " + qualified + " ORDER BY " + valueColumn)) {
						for (long value : bounds) {
							assertTrue(rows.next(), context);
							assertEquals(value, rows.getLong(1), context);
						}
						assertFalse(rows.next(), context);
					}
				}
				if (column.getDataType() == DataType.UBIGINT) {
					var maximum = new java.math.BigDecimal("18446744073709551615");
					try (var insert = connection.prepareStatement("INSERT INTO " + qualified + " VALUES (?)")) {
						insert.setBigDecimal(1, maximum);
						assertEquals(1, insert.executeUpdate(), context);
					}
					try (var rows = statement.executeQuery("SELECT " + valueColumn + " FROM " + qualified)) {
						assertTrue(rows.next(), context);
						assertEquals(0, maximum.compareTo(rows.getBigDecimal(1)), context);
					}
				}
				var contextParameters = new com.sqlapp.data.parameter.ParametersContext();
				contextParameters.put("containsHiddenColumns", false);
				try (var rows = statement.executeQuery("SELECT " + valueColumn + " FROM " + qualified + " WHERE 1=0")) {
					contextParameters.put("columnName", rows.getMetaData().getColumnName(1));
				}
				int matches = 0;
				try (var tables = connection.getMetaData().getTables(connection.getCatalog(), null, "%",
						new String[] { "TABLE" })) {
					while (tables.next()) {
						if (!name.equalsIgnoreCase(tables.getString("TABLE_NAME")))
							continue;
						matches++;
						contextParameters.put("tableName", tables.getString("TABLE_NAME"));
						contextParameters.put("schemaName", tables.getString("TABLE_SCHEM"));
						contextParameters.put("catalogName", tables.getString("TABLE_CAT"));
					}
				}
				assertEquals(1, matches, context + " table identity");
				var schemaReader = dialect.getCatalogReader().getSchemaReader();
				schemaReader.setCatalogName((String) contextParameters.get("catalogName"));
				schemaReader.setSchemaName((String) contextParameters.get("schemaName"));
				var reader = schemaReader.getTableReader().getColumnReader();
				var columns = reader.getAllFull(connection, contextParameters);
				assertEquals(1, columns.size(), context);
				assertNotNull(columns.getFirst().getDataType(), context);
				assertEquals(column.getArrayDimension(), columns.getFirst().getArrayDimension(),
						context + " array dimension");
				if (java.util.Set.of(DataType.UTINYINT, DataType.USMALLINT, DataType.UINT, DataType.UBIGINT)
						.contains(column.getDataType()))
					assertEquals(column.getDataType(), columns.getFirst().getDataType(),
							context + " unsigned metadata type");
				if (java.util.Set.of(DataType.CHAR, DataType.VARCHAR, DataType.NCHAR, DataType.NVARCHAR)
						.contains(column.getDataType()) && column.getLength() != null)
					assertEquals(column.getLength(), columns.getFirst().getLength(), context + " character length");
				var recreated = new Table(name + "R");
				recreated.getColumns().add(columns.getFirst().clone());
				for (var operation : registry.createSql(recreated, SqlType.CREATE))
					statement.execute(operation.getSqlText());
				statement.execute("DROP TABLE " + dialect.quote(recreated.getName()));
				System.out.println("Verified native/portable type, catalog and recreation: " + context);
			} finally {
				statement.execute("DROP TABLE " + qualified);
			}
		}
	}
}
