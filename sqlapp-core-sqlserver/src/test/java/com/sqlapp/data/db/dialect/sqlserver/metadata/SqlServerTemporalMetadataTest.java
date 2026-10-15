/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.ResultSet;
import java.lang.reflect.Proxy;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.dialect.sqlserver.DialectHolder;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;

class SqlServerTemporalMetadataTest {
	private ExResultSet row(Map<String, Object> values) {
		ResultSet nativeRow = (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { ResultSet.class }, (proxy, method, args) -> {
					Object value = args != null && args.length > 0 ? values.get(args[0]) : null;
					return switch (method.getName()) {
					case "getString", "getNString" -> value == null ? null : value.toString();
					case "getObject" -> value;
					case "getInt" -> value == null ? 0 : ((Number) value).intValue();
					case "getBoolean" -> Boolean.TRUE.equals(value);
					case "wasNull" -> true;
					default -> null;
					};
				});
		return new ExResultSet(nativeRow) {
			@Override
			public boolean contains(String label) {
				return values.containsKey(label);
			}
		};
	}

	@Test
	void readerPopulatesSharedModelAndXmlPreservesHistoryIdentity() throws Exception {
		var reader = new SqlServer2016TableReader(DialectHolder.defaultDialect2016);
		Table table = reader.createTable(row(Map.of("table_name", "Current", "schema_name", "dbo", "temporal_type",
				"SYSTEM_VERSIONED_TEMPORAL_TABLE", "period_start_column", "Start", "period_end_column", "End",
				"history_schema_name", "audit", "history_table_name", "History")));
		assertTrue(table.getSystemVersioning().isEnable());
		assertEquals(TemporalPeriodType.SYSTEM_TIME, table.getTemporalPeriods().get(0).getPeriodType());
		Table restored = SchemaUtils.readXml(new java.io.StringReader(table.asXml()));
		assertEquals("Start", restored.getTemporalPeriods().get(0).getStartColumnName());
		assertEquals("End", restored.getTemporalPeriods().get(0).getEndColumnName());
		assertEquals("audit", restored.getSystemVersioning().getHistoryTableSchemaName());
		assertEquals("History", restored.getSystemVersioning().getHistoryTableName());
	}

	@Test
	void disabledPeriodAndHistoryTablesRemainDistinct() throws Exception {
		var reader = new SqlServer2016TableReader(DialectHolder.defaultDialect2016);
		Table disabled = reader.createTable(row(Map.of("table_name", "Disabled", "temporal_type", "NON_TEMPORAL_TABLE",
				"period_start_column", "Start", "period_end_column", "End")));
		assertFalse(disabled.getSystemVersioning().isEnable());
		Table history = reader.createTable(row(Map.of("table_name", "History", "temporal_type", "HISTORY_TABLE")));
		assertNull(history.getSystemVersioning());
		assertTrue(history.getTemporalPeriods().isEmpty());
	}

	@Test
	void retentionReaderPreservesFiniteAndInfiniteValues() throws Exception {
		var reader = new SqlServer2017TableReader(DialectHolder.defaultDialect2017);
		for (String unit : new String[] { "MONTH", "INFINITE" }) {
			var values = new java.util.HashMap<String, Object>();
			values.put("table_name", "Current");
			values.put("temporal_type", "SYSTEM_VERSIONED_TEMPORAL_TABLE");
			values.put("period_start_column", "Start");
			values.put("period_end_column", "End");
			values.put("history_retention_period", unit.equals("INFINITE") ? -1 : 6);
			values.put("history_retention_period_unit_desc", unit);
			Table table = reader.createTable(row(values));
			assertEquals(unit.equals("INFINITE") ? "INFINITE" : "6 MONTHS",
					com.sqlapp.data.db.dialect.sqlserver.util.SqlServerTemporalOptions
							.getHistoryRetentionPeriod(table.getSystemVersioning()));
			values.remove("history_retention_period_unit_desc");
			assertThrows(java.sql.SQLException.class, () -> reader.createTable(row(values)));
		}
	}
}
