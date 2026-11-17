/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.hsql;

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

class HsqlStandardIntervalsTest {
	@org.junit.jupiter.api.Test
	void intervalArraysAreRejectedBeforeBindingBecauseTheEngineCannotDeclareThem() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:interval_array_limit", "SA", "");
				var statement = connection.createStatement()) {
			assertThrows(java.sql.SQLSyntaxErrorException.class,
					() -> statement.execute("CREATE TABLE A(C INTERVAL DAY(9) TO SECOND(9) ARRAY)"));
			var dialect = DialectResolver.getInstance().getDialect(connection);
			var column = new Column("C").setDataType(DataType.INTERVAL_DAY_TO_SECOND).setLength(9).setScale(9).setArrayDimension(1);
			var handler = (IntervalTypeHandler) dialect.getDbDataTypes().getDbTypeStrict(column.getDataType()).getJdbcTypeHandler();
			assertThrows(IllegalArgumentException.class, () -> handler.validateIntervalColumn(column));
			assertThrows(IllegalArgumentException.class, () -> handler.prepareInterval(new Object[] { IntervalValue.ZERO }, column));
			statement.execute("SHUTDOWN");
		}
	}
	static Stream<DataType> types() {
		return Stream.of(DataType.values()).filter(type -> type.getMetaTtype() == MetaType.INTERVAL
				&& type != DataType.INTERVAL && type != DataType.INTERVAL_YEAR_TO_DAY);
	}
	@ParameterizedTest @MethodSource("types")
	void everyQualifierBindsReadsAndGeneratesLiteralsExactly(DataType type) throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:hsqldb:mem:interval_" + type.name(), "SA", "");
				var statement = connection.createStatement()) {
			var dialect = DialectResolver.getInstance().getDialect(connection);
			var column = new Column("C").setDataType(type).setLength(9).setScale(9);
			var definition = dialect.getDbDataTypes().getDbTypeStrict(type);
			assertInstanceOf(IntervalTypeHandler.class, definition.getJdbcTypeHandler());
			var handler = definition.getJdbcTypeHandler();
			var value = type.name().contains("YEAR") || type == DataType.INTERVAL_MONTH
					? new IntervalValue(-1200, 0, Duration.ZERO) : new IntervalValue(0, 0, Duration.ofDays(-100));
			statement.execute("CREATE TABLE T(C " + definition.getColumCreateDefinition(9L, 9) + ")");
			try (var insert = connection.prepareStatement("INSERT INTO T VALUES(?)")) {
				handler.setObject(insert, 1, value, column); insert.executeUpdate();
				handler.setObject(insert, 1, null, column); insert.executeUpdate();
			}
			try (var rows = statement.executeQuery("SELECT C FROM T")) {
				assertTrue(rows.next()); assertEquals(value, handler.getObject(rows, 1));
				assertTrue(rows.next()); assertNull(handler.getObject(rows, 1));
			}
			try (var rows = statement.executeQuery("SELECT " + dialect.getSqlValueDefinition(column, value)
					+ " FROM (VALUES(0))")) {
				assertTrue(rows.next()); assertEquals(value, handler.getObject(rows, 1));
			}
			statement.execute("SHUTDOWN");
		}
	}
}
