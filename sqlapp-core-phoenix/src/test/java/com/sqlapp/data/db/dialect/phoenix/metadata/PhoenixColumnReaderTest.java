/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.sql.*;
import java.util.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.phoenix.DialectHolder;
import com.sqlapp.data.db.dialect.phoenix.sql.PhoenixCreateTableFactory;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.*;

class PhoenixColumnReaderTest {
	@SuppressWarnings("unchecked")
	private static <T> T proxy(Class<T> type, java.lang.reflect.InvocationHandler handler) {
		return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[] { type }, handler);
	}

	static class Jdbc {
		int queries;
		boolean closed;
		final Connection connection;

		Jdbc(List<Map<String, Object>> rows) {
			this(rows, "getColumns");
		}

		Jdbc(List<Map<String, Object>> rows, String operation) {
			final var labels = new ArrayList<String>();
			for (var row : rows)
				for (var key : row.keySet())
					if (!labels.contains(key))
						labels.add(key);
			final var metadata = proxy(ResultSetMetaData.class, (p, method, args) -> switch (method.getName()) {
			case "getColumnCount" -> labels.size();
			case "getColumnLabel", "getColumnName" -> labels.get((Integer) args[0] - 1);
			case "getTableName", "getSchemaName" -> "";
			case "isNullable" -> ResultSetMetaData.columnNullable;
			default -> throw new UnsupportedOperationException(method.getName());
			});
			final int[] position = { -1 };
			final boolean[] wasNull = { false };
			final var result = proxy(ResultSet.class, (p, method, args) -> {
				switch (method.getName()) {
				case "next":
					return ++position[0] < rows.size();
				case "getMetaData":
					return metadata;
				case "wasNull":
					return wasNull[0];
				case "close":
					closed = true;
					return null;
				}
				final Object value = rows.get(position[0]).get(args[0].toString().toUpperCase(Locale.ROOT));
				wasNull[0] = value == null;
				return switch (method.getName()) {
				case "getString" -> value == null ? null : value.toString();
				case "getInt" -> value == null ? 0 : ((Number) value).intValue();
				case "getLong" -> value == null ? 0L : ((Number) value).longValue();
				case "getBoolean" ->
					value != null && (value instanceof Boolean b ? b : Boolean.parseBoolean(value.toString()));
				default -> throw new UnsupportedOperationException(method.getName());
				};
			});
			final var database = proxy(DatabaseMetaData.class, (p, method, args) -> {
				if (method.getName().equals(operation)) {
					queries++;
					return result;
				}
				throw new UnsupportedOperationException(method.getName());
			});
			connection = proxy(Connection.class, (p, method, args) -> {
				if (method.getName().equals("getMetaData"))
					return database;
				throw new UnsupportedOperationException(method.getName());
			});
		}
	}

	private Map<String, Object> row(String table, String name, String family) {
		final var row = new HashMap<String, Object>();
		row.put("TABLE_CAT", "tenant");
		row.put("TABLE_SCHEM", "app");
		row.put("TABLE_NAME", table);
		row.put("COLUMN_NAME", name);
		row.put("DATA_TYPE", Types.VARCHAR);
		row.put("TYPE_NAME", "VARCHAR");
		row.put("COLUMN_SIZE", 40L);
		row.put("DECIMAL_DIGITS", null);
		row.put("NULLABLE", ResultSetMetaData.columnNullable);
		row.put("IS_AUTOINCREMENT", "NO");
		row.put("COLUMN_DEF", "'a b'");
		row.put("CHAR_OCTET_LENGTH", null);
		row.put("COLUMN_FAMILY", family);
		row.put("ARRAY_SIZE", null);
		row.put("REMARKS", "column comment");
		return row;
	}

	@Test
	void retainsFamiliesAndArrayCapacitiesThroughOneJdbcReadXmlAndDdl() throws Exception {
		final List<Map<String, Object>> rows = new ArrayList<>();
		for (int i = 0; i < 40; i++) {
			final var row = row("table" + i, "payload", "detail family");
			row.put("DATA_TYPE", Types.ARRAY);
			row.put("TYPE_NAME", "VARCHAR ARRAY");
			row.put("ARRAY_SIZE", 10L);
			row.put("COLUMN_DEF", null);
			rows.add(row);
		}
		final var jdbc = new Jdbc(rows);
		final var reader = new PhoenixColumnReader(DialectHolder.defaultDialect);
		final var columns = reader.doGetAll(jdbc.connection, new ParametersContext(), null);
		assertEquals(1, jdbc.queries);
		assertTrue(jdbc.closed);
		assertEquals(40, columns.size());
		final var table = new Table(columns.get(0).getTableName());
		table.getColumns().add(columns.get(0));
		final Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		final var column = restored.getColumns().get(0);
		assertEquals("detail family", column.getSpecifics().get(PhoenixCreateTableFactory.COLUMN_FAMILY));
		assertEquals(1, column.getArrayDimension());
		assertEquals(10, column.getArrayDimensionUpperBound());
		assertEquals("column comment", column.getRemarks());
		assertNull(column.getOctetLength());
		final var registry = DialectHolder.defaultDialect.createSqlFactoryRegistry();
		registry.getOptions().setQuateColumnName(true);
		final String sql = registry.createSql(restored, SqlType.CREATE).get(0).getSqlText();
		assertTrue(sql.contains("\"detail family\".\"payload\" VARCHAR(40) ARRAY[10]"), sql);
	}

	@Test
	void preservesJdbcFallbackWhenOptionalPhoenixColumnsAreAbsent() {
		final var row = row("plain", "value", null);
		row.remove("COLUMN_FAMILY");
		row.remove("ARRAY_SIZE");
		final var jdbc = new Jdbc(List.of(row));
		final var column = new PhoenixColumnReader(DialectHolder.defaultDialect)
				.doGetAll(jdbc.connection, new ParametersContext(), null).get(0);
		assertEquals(0, column.getArrayDimension());
		assertEquals("'a b'", column.getDefaultValue());
		assertNull(column.getSpecifics().get(PhoenixCreateTableFactory.COLUMN_FAMILY));
		assertTrue(jdbc.closed);
	}

	@Test
	void recognizesJdbcArrayCodeEvenWhenTypeNameOmitsArraySuffix() {
		final var row = row("arrays", "values", null);
		row.put("DATA_TYPE", Types.ARRAY);
		row.put("TYPE_NAME", "INTEGER");
		final var jdbc = new Jdbc(List.of(row));
		final var column = new PhoenixColumnReader(DialectHolder.defaultDialect)
				.doGetAll(jdbc.connection, new ParametersContext(), null).get(0);
		assertEquals(1, column.getArrayDimension());
		assertEquals(com.sqlapp.data.db.datatype.DataType.INT, column.getDataType());
	}

	@Test
	void rejectsFamilyCollisionsAndInvalidArraySizesWithoutLeakingResults() {
		final var reader = new PhoenixColumnReader(DialectHolder.defaultDialect);
		final var collision = new Jdbc(List.of(row("items", "value", "a"), row("items", "value", "b")));
		assertThrows(UnsupportedOperationException.class,
				() -> reader.doGetAll(collision.connection, new ParametersContext(), null));
		assertTrue(collision.closed);
		for (long size : new long[] { -1L, Long.MAX_VALUE }) {
			final var values = row("items", "value", "a");
			values.put("ARRAY_SIZE", size);
			final var jdbc = new Jdbc(List.of(values));
			assertThrows(RuntimeException.class, () -> reader.doGetAll(jdbc.connection, new ParametersContext(), null));
			assertTrue(jdbc.closed);
		}
	}

	@Test
	void exposesPhoenixColumnReadersForTablesAndViews() {
		for (var dialect : List.of(DialectHolder.defaultDialect, DialectHolder.defaultDialect5_3_1)) {
			final var schema = dialect.getCatalogReader().getSchemaReader();
			assertInstanceOf(PhoenixColumnReader.class, schema.getTableReader().getColumnReader());
			assertInstanceOf(PhoenixColumnReader.class, schema.getViewReader().getColumnReader());
		}
	}
}
