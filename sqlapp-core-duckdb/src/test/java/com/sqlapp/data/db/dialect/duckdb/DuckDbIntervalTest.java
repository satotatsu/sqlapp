/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.duckdb;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.DriverManager;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.*;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.interval.IntervalValue;
import com.sqlapp.data.schemas.Column;

class DuckDbIntervalTest {
	@Test
	void mixedSignsWideHoursAndArraysRoundTripExactly() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:duckdb:"); var statement = connection.createStatement()) {
			var dialect = DialectResolver.getInstance().getDialect(connection);
			var handler = new DuckDbIntervalJdbcTypeHandler();
			var column = new Column("C").setDataType(DataType.INTERVAL);
			statement.execute("CREATE TABLE T(C INTERVAL)");
			for (var value : new IntervalValue[] { IntervalValue.parse("P1M-1DT-0.000001S"), IntervalValue.parse("PT25H"),
					new IntervalValue(Integer.MAX_VALUE, Integer.MIN_VALUE, Duration.ofSeconds(9223372036854L, 775807000)),
					IntervalValue.ZERO }) {
				statement.execute("DELETE FROM T");
				try (var insert = connection.prepareStatement("INSERT INTO T VALUES(?)")) {
					handler.setObject(insert, 1, value, column); insert.executeUpdate();
				}
				try (var rows = statement.executeQuery("SELECT C FROM T")) {
					assertTrue(rows.next()); assertEquals(value, handler.getObject(rows, 1));
				}
				try (var rows = statement.executeQuery("SELECT " + dialect.getSqlValueDefinition(column, value))) {
					assertTrue(rows.next()); assertEquals(value, handler.getObject(rows, 1));
				}
			}
			assertThrows(ArithmeticException.class, () -> handler.prepareInterval(IntervalValue.parse("PT0.000000001S"), column));
			assertThrows(ArithmeticException.class, () -> handler.prepareInterval(new IntervalValue(Long.MAX_VALUE, 0, Duration.ZERO), column));
			var array = column.clone().setArrayDimension(2);
			statement.execute("CREATE TABLE A(C INTERVAL[][])");
			Object[] leaves = { IntervalValue.parse("P1M-1DT-0.000001S"), null, IntervalValue.parse("PT25H") };
			Object[] value = { leaves, leaves };
			try (var insert = connection.prepareStatement("INSERT INTO A VALUES(?)")) {
				handler.setObject(insert, 1, value, array); insert.executeUpdate();
			}
			try (var rows = statement.executeQuery("SELECT C FROM A")) {
				assertTrue(rows.next()); assertArrayEquals(value, (Object[]) handler.getObject(rows, 1));
			}
		}
	}
}
