/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.interval.IntervalValue;
import com.sqlapp.data.schemas.Column;

class OracleIntervalTest {
	@Test
	void catalogTypeNamesRetainLeadingAndFractionalPrecision() {
		var dialect = DialectResolver.getInstance().getDialect("Oracle", 19, 0, null);
		var year = new Column("Y");
		dialect.setDbType("INTERVAL YEAR(9) TO MONTH", null, null, year);
		assertEquals(DataType.INTERVAL_YEAR_TO_MONTH, year.getDataType());
		assertEquals(9L, year.getLength());
		var day = new Column("D");
		dialect.setDbType("INTERVAL DAY(9) TO SECOND(9)", null, null, day);
		assertEquals(DataType.INTERVAL_DAY_TO_SECOND, day.getDataType());
		assertEquals(9L, day.getLength());
		assertEquals(9, day.getScale());
		assertEquals("INTERVAL '+0 00:00:00.000000001' DAY(9) TO SECOND(9)",
				dialect.getSqlValueDefinition(day, IntervalValue.parse("PT0.000000001S")));
	}

	@Test
	void supportedVersionsUseExactHandlersAndPrecisionAwareLiterals() {
		for (int version : new int[] { 9, 10, 11, 12, 19, 23 }) {
			var dialect = DialectResolver.getInstance().getDialect("Oracle", version, 0, null);
			var ym = new Column("Y").setDataType(DataType.INTERVAL_YEAR_TO_MONTH).setLength(9);
			var ds = new Column("D").setDataType(DataType.INTERVAL_DAY_TO_SECOND).setLength(9).setScale(9);
			assertInstanceOf(OracleIntervalJdbcTypeHandler.class,
					dialect.getDbDataTypes().getDbTypeStrict(ym.getDataType()).getJdbcTypeHandler());
			assertEquals("INTERVAL '-999999999-11' YEAR(9) TO MONTH",
					dialect.getSqlValueDefinition(ym, IntervalValue.parse("P-999999999Y-11M")));
			assertEquals("INTERVAL '+0 23:00:00' DAY(9) TO SECOND(9)",
					dialect.getSqlValueDefinition(ds, IntervalValue.parse("P1DT-1H")));
			assertEquals("NULL", dialect.getSqlValueDefinition(ds, null));
			assertThrows(ArithmeticException.class,
					() -> dialect.getSqlValueDefinition(ds, IntervalValue.parse("P1000000000D")));
		}
	}

	@Test
	void exactDriverTextReadsAndTypedNullsDoNotRequireDriverClasses() throws Exception {
		for (var type : new DataType[] { DataType.INTERVAL_YEAR_TO_MONTH, DataType.INTERVAL_DAY_TO_SECOND }) {
			var handler = new OracleIntervalJdbcTypeHandler(type);
			String text = type == DataType.INTERVAL_YEAR_TO_MONTH ? "-999999999-11" : "-999999999 23:59:59.123456789";
			var expected = type == DataType.INTERVAL_YEAR_TO_MONTH ? IntervalValue.parse("P-999999999Y-11M")
					: IntervalValue.parse("PT-86399999999999.123456789S");
			var rows = (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[] { ResultSet.class },
					(proxy, method, args) -> method.getName().equals("getMetaData") ? null : text);
			assertEquals(expected, handler.getObject(rows, 1));
			assertEquals(expected, handler.getObject(rows, "C"));
			var statement = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
					new Class<?>[] { PreparedStatement.class }, (proxy, method, args) -> {
						assertEquals("setNull", method.getName());
						assertEquals(type == DataType.INTERVAL_YEAR_TO_MONTH ? -103 : -104, args[1]);
						return null;
					});
			handler.setObject(statement, 1, null);
		}
	}

	@Test
	void invalidValuesAreRejectedBeforeTouchingJdbc() {
		var handler = new OracleIntervalJdbcTypeHandler(DataType.INTERVAL_DAY_TO_SECOND);
		var column = new Column("D").setDataType(DataType.INTERVAL_DAY_TO_SECOND);
		var statement = (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { PreparedStatement.class }, (proxy, method, args) -> {
					throw new AssertionError("Invalid value reached JDBC");
				});
		assertThrows(ArithmeticException.class,
				() -> handler.setObject(statement, 1, IntervalValue.parse("P100D"), column));
		assertThrows(ArithmeticException.class,
				() -> handler.setObject(statement, 1, IntervalValue.parse("PT0.000000001S"), column));
		assertThrows(IllegalArgumentException.class,
				() -> handler.setObject(statement, 1, IntervalValue.parse("P1M1D"), column));
		assertThrows(IllegalArgumentException.class, () -> handler.codec(column.clone().setLength(10)));
		assertThrows(IllegalArgumentException.class, () -> new OracleIntervalJdbcTypeHandler(DataType.INTERVAL_YEAR));
		assertThrows(SQLException.class, () -> OracleIntervalJdbcTypeHandler
				.createNativeValue(getClass().getClassLoader(), "missing.IntervalDriver", "+1-2"));
	}
}
