/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.h2;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.DriverManager;
import java.time.Duration;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import com.sqlapp.data.db.datatype.*;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.interval.IntervalValue;
import com.sqlapp.data.schemas.Column;

class H2StandardIntervalsTest {
	@org.junit.jupiter.params.ParameterizedTest
	@org.junit.jupiter.params.provider.ValueSource(ints = { 1, 2 })
	void arraysPreserveNanosNullsAndDestinationProjection(int dimensions) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:h2:mem:", "sa", "");
				var statement = connection.createStatement()) {
			var dialect = DialectResolver.getInstance().getDialect(connection);
			var column = new Column("C").setDataType(DataType.INTERVAL_DAY_TO_SECOND).setLength(9).setScale(9)
					.setArrayDimension(dimensions);
			var definition = dialect.getDbDataTypes().getDbTypeStrict(column.getDataType());
			var handler = (IntervalTypeHandler) definition.getJdbcTypeHandler();
			Object[] leaves = { IntervalValue.parse("P1D"), IntervalValue.parse("PT-0.000000001S"), null };
			Object[] value = dimensions == 1 ? leaves : new Object[] { leaves, leaves };
			var expected = (Object[]) handler.prepareInterval(value, column).value();
			statement.execute("CREATE TABLE A(C " + definition.getColumCreateDefinition(9L, 9)
					+ " ARRAY".repeat(dimensions) + ")");
			try (var insert = connection.prepareStatement("INSERT INTO A VALUES(?)")) {
				handler.setObject(insert, 1, value, column);
				insert.executeUpdate();
				handler.setObject(insert, 1, null, column);
				insert.executeUpdate();
			}
			try (var rows = statement.executeQuery("SELECT C FROM A")) {
				assertTrue(rows.next());
				assertArrayEquals(expected, (Object[]) handler.getObject(rows, 1));
				assertTrue(rows.next());
				assertNull(handler.getObject(rows, 1));
			}
			try (var rows = statement.executeQuery("SELECT " + dialect.getSqlValueDefinition(column, value) + "")) {
				assertTrue(rows.next());
				assertArrayEquals(expected, (Object[]) handler.getObject(rows, 1));
			}

		}
	}

	static Stream<DataType> types() {
		return Stream.of(DataType.values()).filter(type -> type.getMetaTtype() == MetaType.INTERVAL
				&& type != DataType.INTERVAL && type != DataType.INTERVAL_YEAR_TO_DAY);
	}

	@ParameterizedTest
	@MethodSource("types")
	void everyQualifierBindsReadsAndGeneratesLiteralsExactly(DataType type) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:h2:mem:" + type.name(), "sa", "");
				var statement = connection.createStatement()) {
			var dialect = DialectResolver.getInstance().getDialect(connection);
			var column = new Column("C").setDataType(type).setLength(9).setScale(9);
			var definition = dialect.getDbDataTypes().getDbTypeStrict(type);
			assertInstanceOf(IntervalTypeHandler.class, definition.getJdbcTypeHandler());
			var handler = definition.getJdbcTypeHandler();
			var value = type.name().contains("YEAR") || type == DataType.INTERVAL_MONTH
					? new IntervalValue(-1200, 0, Duration.ZERO)
					: new IntervalValue(0, 0, Duration.ofDays(-100));
			statement.execute("CREATE TABLE T(C " + definition.getColumCreateDefinition(9L, 9) + ")");
			try (var insert = connection.prepareStatement("INSERT INTO T VALUES(?)")) {
				handler.setObject(insert, 1, value, column);
				insert.executeUpdate();
				handler.setObject(insert, 1, null, column);
				insert.executeUpdate();
			}
			try (var rows = statement.executeQuery("SELECT C FROM T")) {
				assertTrue(rows.next());
				assertEquals(value, handler.getObject(rows, 1));
				assertTrue(rows.next());
				assertNull(handler.getObject(rows, 1));
			}
			try (var rows = statement.executeQuery("SELECT " + dialect.getSqlValueDefinition(column, value) + "")) {
				assertTrue(rows.next());
				assertEquals(value, handler.getObject(rows, 1));
			}

		}
	}
}
