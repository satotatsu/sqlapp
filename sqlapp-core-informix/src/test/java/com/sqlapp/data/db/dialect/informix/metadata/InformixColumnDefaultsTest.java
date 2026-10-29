/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.informix.DialectHolder;
import com.sqlapp.data.parameter.ParametersContext;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ProductVersionInfo;
import com.sqlapp.jdbc.sql.ResultSetNextHandler;

class InformixColumnDefaultsTest {
	private static class Reader extends InformixColumnReader {
		final List<Column> columns = new ArrayList<>();
		final List<Map<String, Object>> defaults = new ArrayList<>();
		int jdbcCalls;
		int queries;

		Reader() {
			super(DialectHolder.defaultDialect);
		}

		@Override
		protected List<Column> readJdbcColumns(Connection c, ParametersContext p, ProductVersionInfo v) {
			jdbcCalls++;
			return columns;
		}

		@Override
		protected void queryDefaults(Connection c, ParametersContext p, ResultSetNextHandler handler) {
			queries++;
			assertEquals(columns.size(), ((List<?>) p.get("tableName")).size());
			try {
				for (var row : defaults)
					handler.handleResultSetNext(InformixMetadataReaderTest.row(row));
			} catch (SQLException e) {
				throw new RuntimeException(e);
			}
		}

		void add(int id, String kind, String raw) {
			columns.add(new Column("value").setDataType(DataType.VARCHAR).setCatalogName("db").setSchemaName("owner")
					.setTableName("items" + id).setDefaultValue("broken JDBC value"));
			final Map<String, Object> row = new HashMap<>();
			row.put("catalog_name", "db");
			row.put("schema_name", "owner");
			row.put("table_name", "items" + id);
			row.put("column_name", "value");
			row.put("default_kind", kind);
			row.put("default_value", raw);
			defaults.add(row);
		}
	}

	@Test
	void repairsManyDefaultsInOneBulkQueryAndKeepsNoDefaultDistinctFromNullAndEmpty() {
		final Reader reader = new Reader();
		for (int i = 0; i < 50; i++)
			reader.add(i, "L", "it's  spaced \0" + " ".repeat(50));
		reader.add(50, "L", null);
		reader.add(51, "N", null);
		reader.add(52, null, null);
		final var columns = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(1, reader.jdbcCalls);
		assertEquals(1, reader.queries);
		for (int i = 0; i < 50; i++)
			assertEquals("'it''s  spaced '", columns.get(i).getDefaultValue());
		assertEquals("''", columns.get(50).getDefaultValue());
		assertEquals("NULL", columns.get(51).getDefaultValue());
		assertNull(columns.get(52).getDefaultValue());
	}

	@Test
	void excludesJdbcSequencePseudoColumnsWithoutHidingUnresolvedTableColumns() {
		final Reader reader = new Reader();
		reader.add(1, "U", null);
		reader.add(2, "U", null);
		reader.add(3, "U", null);
		reader.defaults.get(0).put("object_type", "T");
		reader.defaults.get(1).put("object_type", "Q");
		reader.defaults.get(2).put("object_type", "P");
		reader.defaults.get(2).put("column_name", null); // Synonym columns belong to the target, not to its own tabid.
		final var columns = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(1, columns.size());
		assertEquals("items1", columns.get(0).getTableName());
		assertEquals(1, reader.queries);
		reader.defaults.clear();
		assertThrows(IllegalStateException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
		final Reader unsupported = new Reader();
		unsupported.add(1, "U", null);
		unsupported.defaults.get(0).put("object_type", "?");
		assertThrows(UnsupportedOperationException.class,
				() -> unsupported.doGetAll(null, new ParametersContext(), null));
	}

	@Test
	void restoresKeywordBooleanNumericAndTemporalDefaults() {
		final Column text = new Column("text").setDataType(DataType.VARCHAR);
		assertEquals("USER", InformixColumnDefaults.expression(text, "U", null));
		assertEquals("DBSERVERNAME", InformixColumnDefaults.expression(text, "S", null));
		assertEquals("'USER'", InformixColumnDefaults.expression(text, "L", "USER\0   "));
		final Column flag = new Column("flag").setDataType(DataType.BOOLEAN);
		assertEquals("'t'", InformixColumnDefaults.expression(flag, "L", "t\0   "));
		assertEquals("42",
				InformixColumnDefaults.expression(new Column("n").setDataType(DataType.INT), "L", "AAAAKg 42   "));
		assertEquals("'10/29/2026'", InformixColumnDefaults.expression(new Column("d").setDataType(DataType.DATE), "L",
				"encoded 10/29/2026   "));
		assertEquals("TODAY", InformixColumnDefaults.expression(new Column("d").setDataType(DataType.DATE), "T", null));
		assertEquals("CURRENT HOUR TO MINUTE", InformixColumnDefaults.expression(
				new Column("t").setDataType(DataType.TIME).setDataTypeName("DATETIME HOUR TO MINUTE"), "C", null));
		assertEquals("DATETIME(12:34) HOUR TO MINUTE",
				InformixColumnDefaults.expression(
						new Column("t").setDataType(DataType.TIME).setDataTypeName("DATETIME HOUR TO MINUTE"), "L",
						"encoded 12:34\0  "));
		assertEquals("INTERVAL(123 01:02:03.123) DAY(3) TO FRACTION(3)",
				InformixColumnDefaults.expression(new Column("i").setDataType(DataType.INTERVAL_DAY_TO_SECOND)
						.setDataTypeName("INTERVAL DAY(3) TO FRACTION(3)"), "L", "encoded  123 01:02:03.123  "));
	}

	@Test
	void rejectsMissingCatalogRowsAndUndecodableDefaults() {
		final Reader reader = new Reader();
		reader.add(1, "U", null);
		reader.defaults.clear();
		assertThrows(IllegalStateException.class, () -> reader.doGetAll(null, new ParametersContext(), null));
		assertThrows(UnsupportedOperationException.class,
				() -> InformixColumnDefaults.expression(new Column("c"), "?", null));
		assertThrows(IllegalStateException.class, () -> InformixColumnDefaults
				.expression(new Column("c").setDataType(DataType.VARCHAR), "L", "lost terminator"));
		assertThrows(IllegalStateException.class,
				() -> InformixColumnDefaults.expression(new Column("c").setDataType(DataType.INT), "C", null));
	}

	@Test
	void readsNotNullAndPrimaryKeyModesWithoutAdditionalQueries() {
		final Reader reader = new Reader();
		for (int i = 0; i < 4; i++) {
			reader.add(i, null, null);
			reader.columns.get(i).setDataType(DataType.INT).setNullable(false);
			final var row = reader.defaults.get(i);
			row.put("column_number", 1);
			row.put("primary_key_name", "pk");
			row.put("primary_key_columns", "1 [1]");
			row.put("primary_key_state", i == 2 ? "E" : "D");
			if (i < 3) {
				row.put("not_null_name", "nn");
				row.put("not_null_owner", "other");
				row.put("not_null_state", i == 1 ? "E" : "D");
			}
		}
		final var columns = reader.doGetAll(null, new ParametersContext(), null);
		assertEquals(1, reader.queries);
		assertFalse(columns.get(0).isNotNull());
		assertTrue(columns.get(1).isNotNull());
		assertTrue(columns.get(2).isNotNull());
		assertFalse(columns.get(3).isNotNull());
		assertEquals("nn", columns.get(0).getSpecifics()
				.get(com.sqlapp.data.db.dialect.informix.util.InformixConstraintModes.NOT_NULL_NAME));
	}

	@Test
	void emptyResultDoesNotQueryDefaults() {
		final Reader reader = new Reader();
		assertTrue(reader.doGetAll(null, new ParametersContext(), null).isEmpty());
		assertEquals(0, reader.queries);
	}
}
