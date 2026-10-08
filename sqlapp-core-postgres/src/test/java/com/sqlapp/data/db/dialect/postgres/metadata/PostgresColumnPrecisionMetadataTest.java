package com.sqlapp.data.db.dialect.postgres.metadata;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.dialect.postgres.DialectHolder;
import com.sqlapp.data.schemas.Column;

class PostgresColumnPrecisionMetadataTest {
	@Test
	void preservesFractionalPrecisionIncludingZeroForScalarAndArrayColumns() throws Exception {
		for (String type : new String[] { "timestamp", "timestamptz", "time", "timetz" }) {
			for (int dimension : new int[] { 0, 1, 2 }) {
				for (int precision : new int[] { 0, 3, 6 }) {
					var values = values(type, dimension);
					values.put("datetime_scale", precision);
					Column column = read(values);
					assertEquals((long) precision, column.getLength(), type);
					assertNull(column.getScale(), type);
					assertEquals(dimension, column.getArrayDimension());
				}
			}
		}
	}

	@Test
	void keepsNumericPrecisionAndScaleSeparateFromDatetimePrecision() throws Exception {
		for (int dimension : new int[] { 0, 1, 2 }) {
			for (int scale : new int[] { 0, 3 }) {
				var values = values("numeric", dimension);
				values.put("numeric_precision", 12L);
				values.put("numeric_scale", scale);
				Column column = read(values);
				assertEquals(12L, column.getLength());
				assertEquals(scale, column.getScale());
				assertEquals(dimension, column.getArrayDimension());
			}
		}
	}

	@Test
	void preservesCharacterLengthAndIntervalFractionalPrecision() throws Exception {
		var text = values("varchar", 2);
		text.put("max_length", 7L);
		assertEquals(7L, read(text).getLength());
		for (int precision : new int[] { 0, 3, 6 }) {
			var interval = values("interval", 2);
			interval.put("interval_scale", precision);
			assertEquals((long) precision, read(interval).getLength());
		}
	}

	private Map<String, Object> values(String type, int dimension) {
		Map<String, Object> values = new HashMap<>();
		values.put("attname", "value");
		values.put("nspname", "public");
		values.put("typname", (dimension == 0 ? "" : "_") + type);
		values.put("attndims", dimension);
		return values;
	}

	private Column read(Map<String, Object> values) throws Exception {
		boolean[] wasNull = { false };
		ResultSet rows = (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { ResultSet.class }, (proxy, method, args) -> {
			if (method.getName().equals("wasNull")) return wasNull[0];
			Object value = values.get(args[0]);
			wasNull[0] = value == null;
			return switch (method.getName()) {
				case "getString" -> value == null ? null : value.toString();
				case "getLong" -> value == null ? 0L : ((Number) value).longValue();
				case "getInt" -> value == null ? 0 : ((Number) value).intValue();
				case "getBoolean" -> value == null ? false : (Boolean) value;
				case "getObject" -> value;
				default -> throw new UnsupportedOperationException(method.getName());
			};
		});
		Column column = new Column();
		PostgresUtils.setColumnMetadata(rows, DialectHolder.postgreSQL150, column);
		return column;
	}
}
