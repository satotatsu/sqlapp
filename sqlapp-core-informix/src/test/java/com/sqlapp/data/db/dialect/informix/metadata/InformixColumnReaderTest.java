/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.metadata;

import static org.junit.jupiter.api.Assertions.*;

import java.io.StringReader;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.informix.DialectHolder;
import com.sqlapp.data.db.dialect.informix.util.InformixTemporalTypes;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.jdbc.ExResultSet;

class InformixColumnReaderTest {
	private Column read(String type, int jdbcType, long size, int scale) throws Exception {
		final Map<String, Object> row = new HashMap<>();
		row.put("COLUMN_NAME", "value");
		row.put("TYPE_NAME", type);
		row.put("DATA_TYPE", jdbcType);
		row.put("COLUMN_SIZE", size);
		row.put("DECIMAL_DIGITS", scale);
		row.put("NULLABLE", 1);
		row.put("IS_AUTOINCREMENT", "NO");
		row.put("TABLE_CAT", "db");
		row.put("TABLE_SCHEM", "informix");
		row.put("TABLE_NAME", "sample");
		final boolean[] wasNull = { false };
		final var result = new ExResultSet((ResultSet) Proxy.newProxyInstance(ResultSet.class.getClassLoader(),
				new Class<?>[] { ResultSet.class }, (proxy, method, args) -> {
					if (method.getName().equals("wasNull"))
						return wasNull[0];
					final Object value = row.get(((String) args[0]).toUpperCase(java.util.Locale.ROOT));
					wasNull[0] = value == null;
					return switch (method.getName()) {
					case "getString" -> value;
					case "getInt" -> value == null ? 0 : ((Number) value).intValue();
					case "getLong" -> value == null ? 0L : ((Number) value).longValue();
					default -> throw new UnsupportedOperationException(method.getName());
					};
				})) {
			@Override
			public boolean contains(String name) {
				return true;
			}
		};
		return new InformixColumnReader(DialectHolder.defaultDialect).createColumn(result);
	}

	private String sql(Column column) throws Exception {
		final var table = new Table("sample");
		table.getColumns().add(column);
		final Table restored = SchemaUtils.readXml(new StringReader(table.asXml()));
		assertEquals(column.getName(), restored.getColumns().get(0).getName(), table.asXml());
		return DialectHolder.defaultDialect.createSqlFactoryRegistry().createSql(restored, SqlType.CREATE).get(0)
				.getSqlText();
	}

	@Test
	void preservesDatetimeRangesAndFractionScaleThroughXml() throws Exception {
		for (String type : List.of("DATETIME YEAR TO FRACTION(1)", "DATETIME YEAR TO FRACTION(3)",
				"DATETIME MONTH TO DAY", "DATETIME HOUR TO MINUTE", "DATETIME SECOND TO FRACTION(5)",
				"DATETIME FRACTION TO FRACTION(3)")) {
			final Column column = read(type.toLowerCase(java.util.Locale.ROOT), Types.TIMESTAMP, 23, 0);
			assertEquals(type, column.getDataTypeName());
			assertTrue(sql(column).contains(type));
		}
	}

	@Test
	void decodesIntervalLeadingPrecisionAndRetainsItsNativeDeclaration() throws Exception {
		final Map<Long, String> cases = Map.of(3149L, "INTERVAL DAY(3) TO FRACTION(3)", 1538L,
				"INTERVAL YEAR(4) TO MONTH", 2731L, "INTERVAL SECOND(9) TO FRACTION(1)", 2338L,
				"INTERVAL MONTH(9) TO MONTH", 1487L, "INTERVAL FRACTION TO FRACTION(5)");
		for (var entry : cases.entrySet()) {
			final Column column = read("interval day to second", Types.CHAR, entry.getKey(), 0);
			assertEquals(entry.getValue(), InformixTemporalTypes.normalize(column.getDataTypeName()));
			assertTrue(column.getDataType().isInterval());
			assertNull(column.getLength());
			assertTrue(sql(column).contains(entry.getValue()));
		}
	}

	@Test
	void preservesIntegerSerialFamiliesAndFloatingDecimal() throws Exception {
		for (String type : List.of("BIGINT", "INT8", "BIGSERIAL", "SERIAL8", "SERIAL")) {
			final Column column = read(type.toLowerCase(java.util.Locale.ROOT), Types.BIGINT, 19, 0);
			assertEquals(type.contains("SERIAL"), column.isIdentity());
			assertTrue(sql(column).contains(type), type);
		}
		final Column floating = read("decimal", Types.DECIMAL, 9, 255);
		assertEquals(DataType.DECIMALFLOAT, floating.getDataType());
		assertEquals(DataType.DECIMALFLOAT,
				DialectHolder.defaultDialect.getDbDataTypes().getDbType(DataType.DECIMALFLOAT).getDataType());
		assertNull(floating.getScale());
		assertTrue(sql(floating).contains("DECIMAL(9)"));
		final Column fixed = read("decimal", Types.DECIMAL, 9, 2);
		assertEquals(DataType.DECIMAL, fixed.getDataType());
		assertEquals(2, fixed.getScale());
		assertTrue(sql(fixed).contains("DECIMAL(9,2)"));
		final Column mapped = new Column("mapped");
		DialectHolder.defaultDialect.setDbType(Types.DECIMAL, "DECIMAL", 9L, 2, mapped);
		assertEquals(DataType.DECIMAL, mapped.getDataType());
		assertEquals(2, mapped.getScale());
		DialectHolder.defaultDialect.setDbType(Types.DECIMAL, "DECIMAL", 9L, 255, mapped);
		assertEquals(DataType.DECIMALFLOAT, mapped.getDataType());
		assertNull(mapped.getScale());
	}

	@Test
	void rejectsInvalidTemporalCodesAndDeclarations() throws Exception {
		for (String type : List.of("DATETIME YEAR(4) TO SECOND", "DATETIME SECOND TO YEAR",
				"DATETIME YEAR TO FRACTION(6)", "INTERVAL YEAR TO DAY", "INTERVAL FRACTION(3) TO FRACTION(3)",
				"DATETIME YEAR TO SECOND; DROP TABLE x"))
			assertThrows(IllegalArgumentException.class, () -> InformixTemporalTypes.normalize(type));
		for (long code : List.of(0L, 65536L, 255L, 284L, 4095L))
			assertThrows(IllegalArgumentException.class, () -> InformixTemporalTypes.interval(code));
		assertThrows(IllegalArgumentException.class,
				() -> sql(new Column("bad").setDataType(DataType.INT).setDataTypeName("DATETIME YEAR TO SECOND")));
	}
}
