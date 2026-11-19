/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres;

import static org.junit.jupiter.api.Assertions.*;
import java.sql.DriverManager;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.interval.IntervalValue;
import com.sqlapp.data.schemas.Column;

/** Opt-in real-server tests. Only a dedicated loopback test database is accepted. */
@EnabledIfSystemProperty(named = "sqlapp.interval.pg.url", matches = "jdbc:postgresql://127[.]0[.]0[.]1:1543[23]/interval_test")
class PostgresIntervalServerTest {
	@Test void temporaryBinarySettingsAreRestoredOnReadAndBindFailure() throws Exception {
		try(var connection=java.sql.DriverManager.getConnection(System.getProperty("sqlapp.interval.pg.url"),"postgres","interval_test_2026")) {
			var executor=connection.unwrap(org.postgresql.core.BaseConnection.class).getQueryExecutor();
			boolean scalar=executor.useBinaryForReceive(org.postgresql.core.Oid.INTERVAL);
			boolean array=executor.useBinaryForReceive(org.postgresql.core.Oid.INTERVAL_ARRAY);
			try(var statement=connection.prepareStatement("SELECT 1/0")) {
				assertThrows(java.sql.SQLException.class,()-> {
					try(var scope=PostgresIntervalBinary.prepareRead(statement)) {statement.executeQuery();}
				});
			}
			assertEquals(scalar,executor.useBinaryForReceive(org.postgresql.core.Oid.INTERVAL));
			assertEquals(array,executor.useBinaryForReceive(org.postgresql.core.Oid.INTERVAL_ARRAY));
			boolean sending=executor.useBinaryForSend(org.postgresql.core.Oid.INTERVAL);
			try(var statement=connection.prepareStatement("SELECT ?")) {
				assertThrows(java.sql.SQLException.class,()->PostgresIntervalBinary.bind(statement,2,IntervalValue.ZERO,false));
			}
			assertEquals(sending,executor.useBinaryForSend(org.postgresql.core.Oid.INTERVAL));
		}
	}

	@Test void rejectsNonDefaultArrayBoundsAndAcceptsActualDimensions() throws Exception {
		try (var connection = DriverManager.getConnection(System.getProperty("sqlapp.interval.pg.url"),
				"postgres", "interval_test_2026"); var statement = connection.createStatement()) {
			var handler = new PostgresIntervalJdbcTypeHandler();
			try (var rows = statement.executeQuery("SELECT '[0:1]={\"1 day\",\"2 days\"}'::interval[]")) {
				assertTrue(rows.next()); assertThrows(java.sql.SQLException.class, () -> handler.getObject(rows, 1));
			}
			statement.execute("CREATE TEMP TABLE interval_dimensions(c INTERVAL[])");
			var column = new Column("c").setDataType(DataType.INTERVAL).setArrayDimension(1).setLength(6);
			Object[] value = { new Object[] {IntervalValue.parse("P1D"), null} };
			try (var insert = connection.prepareStatement("INSERT INTO interval_dimensions VALUES(?)")) {
				handler.setObject(insert, 1, value, column); insert.executeUpdate();
			}
			try (var rows = statement.executeQuery("SELECT c FROM interval_dimensions")) {
				assertTrue(rows.next()); assertArrayEquals(value, (Object[]) handler.getObject(rows, 1));
			}
		}
	}
	@Test
	void multidimensionalArraysPreserveComponentsNullsAndLargeHours() throws Exception {
		try (var connection = DriverManager.getConnection(System.getProperty("sqlapp.interval.pg.url"),
				"postgres", "interval_test_2026"); var statement = connection.createStatement()) {
			connection.setAutoCommit(false);
			statement.execute("CREATE TEMP TABLE interval_array_rt(c INTERVAL(6)[][])");
			var column = new Column("c").setDataType(DataType.INTERVAL).setLength(6).setArrayDimension(2);
			var handler = new PostgresIntervalJdbcTypeHandler();
			Object[] leaves = { IntervalValue.parse("P1DT-1H"), null,
					new IntervalValue(0, 0, Duration.ofSeconds(9223372036854L, 775807000)) };
			Object[] value = { leaves, leaves };
			try (var insert = connection.prepareStatement("INSERT INTO interval_array_rt VALUES(?)")) {
				handler.setObject(insert, 1, value, column); insert.executeUpdate();
				handler.setObject(insert, 1, null, column); insert.executeUpdate();
			}
			try (var query = connection.prepareStatement("SELECT c FROM interval_array_rt");
					var scope = handler.prepareRead(query); var rows = query.executeQuery()) {
				assertTrue(rows.next()); assertArrayEquals(value, (Object[]) handler.getObject(rows, 1));
				assertTrue(rows.next()); assertNull(handler.getObject(rows, 1));
			}
			var dialect = com.sqlapp.data.db.dialect.DialectResolver.getInstance().getDialect(connection);
			try (var query = connection.prepareStatement("SELECT " + dialect.getSqlValueDefinition(column, value));
					var scope = handler.prepareRead(query); var rows = query.executeQuery()) {
				assertTrue(rows.next()); assertArrayEquals(value, (Object[]) handler.getObject(rows, 1));
			}
			connection.rollback();
		}
	}
	@Test
	void exactRoundTripInEveryServerOutputStyleIncludingIntHourOverflow() throws Exception {
		var url = System.getProperty("sqlapp.interval.pg.url");
		try (var connection = DriverManager.getConnection(url, "postgres", "interval_test_2026");
				var statement = connection.createStatement()) {
			connection.setAutoCommit(false);
			statement.execute("CREATE TEMP TABLE interval_rt(c INTERVAL(6))");
			var column = new Column("c").setDataType(DataType.INTERVAL).setLength(6);
			var handler = new PostgresIntervalJdbcTypeHandler();
			for (var value : new IntervalValue[] { IntervalValue.parse("P1M-1DT0.123456S"),
					IntervalValue.parse("P1DT-1H"), IntervalValue.parse("PT-0.000001S"), IntervalValue.ZERO,
					new IntervalValue(Integer.MAX_VALUE, Integer.MIN_VALUE, Duration.ZERO),
					new IntervalValue(0, 0, Duration.ofSeconds(-9223372036855L, 224192000)),
					new IntervalValue(0, 0, Duration.ofSeconds(9223372036854L, 775807000)) }) {

				statement.execute("DELETE FROM interval_rt");
				try (var insert = connection.prepareStatement("INSERT INTO interval_rt VALUES(?)")) {
					handler.setObject(insert, 1, value, column); insert.executeUpdate();
				}
				for (String style : new String[] { "postgres", "postgres_verbose", "sql_standard", "iso_8601" }) {
					statement.execute("SET LOCAL intervalstyle = '" + style + "'");
					try (var query = connection.prepareStatement("SELECT c FROM interval_rt");
							var scope = PostgresIntervalBinary.required(value) ? handler.prepareRead(query) : com.sqlapp.data.db.datatype.JdbcReadScope.NONE;
							var rows = query.executeQuery()) {
						assertTrue(rows.next());
						assertEquals(value, handler.getObject(rows, 1), style);
					}
				}
			}
			connection.rollback();
		}
	}

	@Test
	void calendarDayAndTwentyFourHoursRetainDifferentDstBehavior() throws Exception {
		try (var connection = DriverManager.getConnection(System.getProperty("sqlapp.interval.pg.url"),
				"postgres", "interval_test_2026"); var statement = connection.createStatement()) {
			connection.setAutoCommit(false);
			statement.execute("SET LOCAL TIME ZONE 'America/New_York'");
			var handler = new PostgresIntervalJdbcTypeHandler();
			try (var query = connection.prepareStatement("SELECT EXTRACT(EPOCH FROM ((TIMESTAMPTZ '2024-03-09 12:00:00-05' + ?) "
					+ "- TIMESTAMPTZ '2024-03-09 12:00:00-05'))")) {
				handler.setObject(query, 1, IntervalValue.parse("P1D"));
				try (var rows = query.executeQuery()) { assertTrue(rows.next()); assertEquals(82800, rows.getLong(1)); }
				handler.setObject(query, 1, IntervalValue.parse("PT24H"));
				try (var rows = query.executeQuery()) { assertTrue(rows.next()); assertEquals(86400, rows.getLong(1)); }
			}
			connection.rollback();
		}
	}
}
