/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.h2.sql;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.DriverManager;
import java.time.Duration;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.datatype.StandardIntervalJdbcTypeHandler;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.interval.IntervalValue;
import com.sqlapp.data.schemas.Column;

class H2IntervalBindingTest {
	static Stream<IntervalValue> values() {
		return Stream.of(IntervalValue.ZERO, IntervalValue.parse("P1DT-1H"), IntervalValue.parse("PT-0.123456789S"),
				IntervalValue.parse("P999999999DT23H59M59.999999999S"),
				IntervalValue.parse("P-999999999DT-23H-59M-59.999999999S"),
				new IntervalValue(0, 0, Duration.ofSeconds(Long.MIN_VALUE)),
				new IntervalValue(0, 0, Duration.ofSeconds(Long.MAX_VALUE, 999999999)));
	}

	@ParameterizedTest
	@MethodSource("values")
	void dayTimeRoundTripsThroughRealJdbcAndSqlLiterals(IntervalValue value) throws Exception {
		var column = new Column("C").setDataType(DataType.INTERVAL_DAY_TO_SECOND).setLength(18).setScale(9);
		try (var connection = DriverManager.getConnection("jdbc:h2:mem:");
				var statement = connection.createStatement()) {
			var dialect = DialectResolver.getInstance().getDialect(connection);
			var handler = (StandardIntervalJdbcTypeHandler) dialect.getDbDataTypes()
					.getDbTypeStrict(column.getDataType()).getJdbcTypeHandler();
			statement.execute("CREATE TABLE T(C INTERVAL DAY(18) TO SECOND(9))");
			try (var insert = connection.prepareStatement("INSERT INTO T VALUES(?)")) {
				handler.setObject(insert, 1, value, column);
				insert.executeUpdate();
				handler.setObject(insert, 1, null, column);
				insert.executeUpdate();
			}
			try (var rows = statement.executeQuery("SELECT C FROM T")) {
				assertTrue(rows.next());
				assertEquals(handler.convert(value, column).value(), handler.getObject(rows, 1));
				assertEquals(handler.getObject(rows, 1), handler.getObject(rows, "C"));
				assertTrue(rows.next());
				assertNull(handler.getObject(rows, 1));
			}
			try (var rows = statement.executeQuery("SELECT " + dialect.getSqlValueDefinition(column, value))) {
				assertTrue(rows.next());
				assertEquals(handler.convert(value, column).value(), handler.getObject(rows, 1));
			}
		}
	}

	@Test
	void yearMonthPreservesTheFullCommonRangeAndNulls() throws Exception {
		var column = new Column("C").setDataType(DataType.INTERVAL_YEAR_TO_MONTH).setLength(18);
		try (var connection = DriverManager.getConnection("jdbc:h2:mem:");
				var statement = connection.createStatement()) {
			var dialect = DialectResolver.getInstance().getDialect(connection);
			var handler = (StandardIntervalJdbcTypeHandler) dialect.getDbDataTypes()
					.getDbTypeStrict(column.getDataType()).getJdbcTypeHandler();
			statement.execute("CREATE TABLE T(C INTERVAL YEAR(18) TO MONTH)");
			for (long months : new long[] { Long.MIN_VALUE, Long.MAX_VALUE, -14, 0, 14 }) {
				var value = new IntervalValue(months, 0, Duration.ZERO);
				try (var insert = connection.prepareStatement("INSERT INTO T VALUES(?)")) {
					handler.setObject(insert, 1, value, column);
					insert.executeUpdate();
				}
				try (var rows = statement.executeQuery("SELECT " + dialect.getSqlValueDefinition(column, value))) {
					assertTrue(rows.next());
					assertEquals(value, handler.getObject(rows, 1));
				}
			}
			try (var rows = statement.executeQuery("SELECT C FROM T")) {
				for (long months : new long[] { Long.MIN_VALUE, Long.MAX_VALUE, -14, 0, 14 }) {
					assertTrue(rows.next());
					assertEquals(new IntervalValue(months, 0, Duration.ZERO), handler.getObject(rows, 1));
				}
			}
		}
	}

	@Test
	void generatedInsertAndBatchPathsCarryColumnPrecision() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:h2:mem:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE T(ID INT PRIMARY KEY,C INTERVAL DAY(3) TO SECOND(9))");
			var table = H2NativeDmlTest.read(connection, "PUBLIC", "T");
			var column = table.getColumns().get("C");
			var value = IntervalValue.parse("P1DT-3600.000000001S");
			var row = table.getRows().newElement();
			row.put("ID", 1);
			row.put(column, value);
			table.getRows().add(row);
			var dialect = DialectResolver.getInstance().getDialect(connection);
			assertEquals(1, H2NativeDmlTest.write(connection, dialect.createSqlFactoryRegistry(), table,
					com.sqlapp.data.db.sql.SqlType.INSERT, row));
			row.put("ID", 2);
			assertEquals(1, H2NativeDmlTest.write(connection, dialect.createSqlFactoryRegistry(), table,
					com.sqlapp.data.db.sql.SqlType.INSERT_ROWS, table));
			var handler = (StandardIntervalJdbcTypeHandler) dialect.getDbDataTypes()
					.getDbTypeStrict(column.getDataType()).getJdbcTypeHandler();
			try (var rows = statement.executeQuery("SELECT C FROM T")) {
				for (int i = 0; i < 2; i++) {
					assertTrue(rows.next());
					assertEquals(handler.convert(value, column).value(), handler.getObject(rows, 1));
				}
			}
		}
	}

	@Test
	void rejectsLossBeforeDatabaseExecutionAndKeepsOldResolverBehavior() throws Exception {
		var current = DialectResolver.getInstance().getDialect("H2", 2, 3, null);
		var type = DataType.INTERVAL_DAY_TO_SECOND;
		var handler = (StandardIntervalJdbcTypeHandler) current.getDbDataTypes().getDbTypeStrict(type)
				.getJdbcTypeHandler();
		var column = new Column("C").setDataType(type).setLength(2).setScale(6);
		try (var connection = DriverManager.getConnection("jdbc:h2:mem:");
				var statement = connection.prepareStatement("SELECT ?")) {
			assertThrows(ArithmeticException.class,
					() -> handler.setObject(statement, 1, IntervalValue.parse("PT0.000000001S"), column));
			assertThrows(ArithmeticException.class,
					() -> handler.setObject(statement, 1, IntervalValue.parse("P100D"), column));
			assertThrows(IllegalArgumentException.class,
					() -> handler.setObject(statement, 1, IntervalValue.parse("P1M1D"), column));
			assertThrows(IllegalArgumentException.class, () -> handler.codec(column.clone().setLength(0)));
			assertThrows(IllegalArgumentException.class, () -> handler.codec(column.clone().setLength(19)));
			assertThrows(IllegalArgumentException.class, () -> handler.codec(column.clone().setScale(10)));
		}
		var old = DialectResolver.getInstance().getDialect("H2", 1, 4, null);
		assertNull(old.getDbDataTypes().getDbTypeStrict(type));
		assertInstanceOf(StandardIntervalJdbcTypeHandler.class, DialectResolver.getInstance()
				.getDialect("H2", 2, 0, null).getDbDataTypes().getDbTypeStrict(type).getJdbcTypeHandler());
	}
}
