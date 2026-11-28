/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlserver.metadata;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.sqlserver.DialectHolder;
import com.sqlapp.data.schemas.*;
import com.sqlapp.jdbc.ExResultSet;

class SqlServerReviewMetadataTest {
	private ExResultSet row(Map<String, Object> values) {
		Object[] last = { null };
		ResultSet rs = (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { ResultSet.class }, (proxy, method, args) -> {
					if (method.getName().equals("wasNull"))
						return last[0] == null;
					Object value = args != null && args.length > 0 ? values.get(args[0]) : null;
					last[0] = value;
					return switch (method.getName()) {
					case "getString", "getNString" -> value == null ? null : value.toString();
					case "getObject" -> value;
					case "getInt" -> value == null ? 0 : ((Number) value).intValue();
					case "getLong" -> value == null ? 0L : ((Number) value).longValue();
					case "getBoolean" -> Boolean.TRUE.equals(value);
					default -> null;
					};
				});
		return new ExResultSet(rs) {
			@Override
			public boolean contains(String label) {
				return values.containsKey(label);
			}
		};
	}

	@Test
	void columnReaderPreservesRowversionAndDefaultConstraintName() throws Exception {
		var values = Map.<String, Object>of("type_name", "timestamp", "column_name", "rv", "max_length", 8L,
				"precision", 0, "scale", 0, "default_name", "DF_Audit");
		var column = new SqlServer2016ColumnReader(DialectHolder.defaultDialect2022).createColumn(row(values));
		assertEquals(DataType.ROWVERSION, column.getDataType());
		assertEquals("DF_Audit", column.getSpecifics().get("default_name"));
	}

	@Test
	void unicodeRoutineArgumentsUseCharactersAndMaxUsesTheUnicodeLimit() throws Exception {
		for (String type : new String[] { "nvarchar", "nchar", "varchar" }) {
			var values = new HashMap<String, Object>();
			values.put("NAME", type);
			values.put("max_length", 20L);
			values.put("routine_name", "p");
			for (boolean function : new boolean[] { false, true }) {
				Routine<?> routine = function ? new Function("p") : new Procedure("p");
				routine.setDialect(DialectHolder.defaultDialect2022);
				NamedArgument argument = new NamedArgument("@a");
				argument.setDialect(DialectHolder.defaultDialect2022);
				SqlServerUtils.setNamedArgument(row(values), routine, argument);
				assertEquals(type.equals("varchar") ? 20L : 10L, argument.getLength());
				values.put("max_length", -1L);
				if (type.equals("nvarchar")) {
					SqlServerUtils.setNamedArgument(row(values), routine, argument);
					assertEquals(1073741823L, argument.getLength());
				}
				values.put("max_length", 20L);
			}
		}
	}

	@Test
	void routinePrecisionUsesNumericPrecisionOrFractionalSecondScale() throws Exception {
		for (String type : new String[] { "decimal", "numeric", "time", "datetime2", "datetimeoffset" }) {
			var values = Map.<String, Object>of("NAME", type, "max_length", 8L, "precision", 12L, "scale", 3,
					"routine_name", "p");
			Procedure routine = new Procedure("p");
			routine.setDialect(DialectHolder.defaultDialect2022);
			NamedArgument argument = new NamedArgument("@a");
			argument.setDialect(DialectHolder.defaultDialect2022);
			SqlServerUtils.setNamedArgument(row(values), routine, argument);
			assertEquals(type.equals("decimal") || type.equals("numeric") ? 12L : 3L, argument.getLength());
		}
		for (String type : new String[] { "time", "datetime2", "datetimeoffset" }) {
			var argument = new NamedArgument("@a");
			argument.setDialect(DialectHolder.defaultDialect2022);
			var routine = new Procedure("p");
			routine.setDialect(DialectHolder.defaultDialect2022);
			SqlServerUtils.setNamedArgument(
					row(Map.of("NAME", type, "max_length", 8L, "precision", 27L, "scale", 0, "routine_name", "p")),
					routine, argument);
			assertEquals(0L, argument.getLength());
		}

		assertNull(SqlServerUtils.getMaxLength("nchar", null));
	}
}
