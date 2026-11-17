/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres;

import static org.junit.jupiter.api.Assertions.*;

import java.lang.reflect.Proxy;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;

import org.junit.jupiter.api.Test;
import org.postgresql.util.PGobject;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.datatype.MetaType;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.interval.IntervalValue;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

class PostgresIntervalJdbcTypeHandlerTest {
	@Test
	void readRowBindRoundTripKeepsCalendarDaysAndMixedSigns() throws Exception {
		final var handler = new PostgresIntervalJdbcTypeHandler();
		final ResultSet result = (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { ResultSet.class }, (proxy, method, args) -> {
					if (method.getName().equals("getString")) return "1 day -01:00:00.000001";
					throw new AssertionError("Unexpected JDBC read: " + method.getName());
				});
		final Table table = new Table("durations");
		final Column column = new Column("amount").setDataType(DataType.INTERVAL_DAY_TO_SECOND);
		table.getColumns().add(column);
		final var row = table.newRow();
		row.put(column, handler.getObject(result, 1));
		final IntervalValue expected = IntervalValue.parse("P1DT-3600.000001S");
		assertEquals(expected, row.get(column));
		assertEquals(expected, handler.getObject(result, "amount"));
		final Object[] bound = new Object[2];
		final PreparedStatement statement = statement(bound);
		handler.setObject(statement, 1, row.get(column));
		assertEquals(Types.OTHER, bound[1]);
		assertEquals(expected, PostgresIntervalCodec.decode(bound[0]));
		handler.setObject(statement, 1, null);
		assertNull(bound[0]);
		assertEquals(Types.OTHER, bound[1]);
	}

	@Test
	void everyVersionAndQualifierUsesTheExactHandler() {
		for (int major : new int[] { 8, 11, 15, 18 }) {
			final var dialect = DialectResolver.getInstance().getDialect("postgres", major, 0, null);
			for (DataType type : DataType.values()) {
				if (type.getMetaTtype() != MetaType.INTERVAL
						|| type == DataType.INTERVAL_YEAR_TO_DAY) continue;
				assertInstanceOf(PostgresIntervalJdbcTypeHandler.class,
						dialect.getDbDataTypes().getDbTypeStrict(type).getJdbcTypeHandler(), type.name());
			}
		}
	}

	@Test
	void declaredColumnPrecisionAndQualifierRejectLossBeforeBinding() {
		final Object[] bound = { "untouched", null };
		final var precision = new PostgresIntervalJdbcTypeHandler(
				new Column("amount").setDataType(DataType.INTERVAL).setLength(3));
		assertThrows(ArithmeticException.class, () -> precision.setObject(statement(bound), 1, IntervalValue.parse("PT0.000001S")));
		assertEquals("untouched", bound[0]);
		final var minutes = new PostgresIntervalJdbcTypeHandler(DataType.INTERVAL_DAY_TO_MINUTE);
		assertThrows(IllegalArgumentException.class, () -> minutes.setObject(statement(bound), 1, IntervalValue.parse("P1DT1S")));
		assertEquals("untouched", bound[0]);
		assertThrows(IllegalArgumentException.class, () ->
				new PostgresIntervalJdbcTypeHandler(DataType.INTERVAL_YEAR).setObject(statement(bound), 1, IntervalValue.parse("P13M")));
		assertThrows(IllegalArgumentException.class, () ->
				new PostgresIntervalJdbcTypeHandler(DataType.INTERVAL_YEAR_TO_MONTH).setObject(statement(bound), 1, IntervalValue.parse("P1M1D")));
		assertThrows(IllegalArgumentException.class, () -> new PostgresIntervalJdbcTypeHandler(
				new Column("amount").setDataType(DataType.INTERVAL).setLength(7)));
	}

	@Test
	void normalSqlAndBatchBindingCarryTheColumnPrecisionAutomatically() throws Exception {
		final var dialect = DialectResolver.getInstance().getDialect("postgres", 18, 0, null);
		final Column column = new Column("amount").setDataType(DataType.INTERVAL).setLength(3);
		final IntervalValue value = IntervalValue.parse("PT0.123456S");
		final Object[] bound = { "untouched", null };
		assertThrows(ArithmeticException.class, () ->
				com.sqlapp.jdbc.sql.JdbcParameterBinder.bindColumn(statement(bound), dialect, column, 1, value));
		final var parameter = new com.sqlapp.jdbc.sql.BindParameter();
		parameter.setColumn(column);
		parameter.setValue(value);
		assertThrows(ArithmeticException.class, () ->
				com.sqlapp.jdbc.sql.JdbcHandlerUtils.setParameters(statement(bound), dialect, parameter, 1));
		class BatchProbe extends com.sqlapp.jdbc.bulk.JdbcBatchBulkInsertExecutor {
			BatchProbe() { super(dialect); }
			void bindValueForTest() throws Exception { bindValue(statement(bound), 1, column, value); }
		}
		assertThrows(ArithmeticException.class, () -> new BatchProbe().bindValueForTest());
		assertEquals("untouched", bound[0]);
	}

	@Test
	void sqlFactoriesRenderAnExecutableTypedLiteralAndValidatePrecision() {
		final var dialect = DialectResolver.getInstance().getDialect("postgres", 18, 0, null);
		final Column column = new Column("amount").setDataType(DataType.INTERVAL);
		final IntervalValue value = IntervalValue.parse("P1DT-3600.123456S");
		final String literal = dialect.getSqlValueDefinition(column, value);
		assertTrue(literal.startsWith("INTERVAL '"));
		assertTrue(literal.endsWith("'"));
		assertEquals(value, PostgresIntervalCodec.parse(literal.substring(10, literal.length() - 1)));
		assertEquals(value, PostgresIntervalCodec.parse(dialect.getValueForDisplay(column, value)));
		assertEquals("NULL", dialect.getSqlValueDefinition(column, null));
		column.setLength(3);
		assertThrows(ArithmeticException.class, () -> dialect.getSqlValueDefinition(column, value));
	}

	private PreparedStatement statement(final Object[] bound) {
		return (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { PreparedStatement.class },
				(proxy, method, args) -> {
					if (method.getName().equals("getConnection")) return null;
					if (method.getName().equals("setObject")) { bound[0] = args[1]; bound[1] = args[2]; return null; }
					if (method.getName().equals("setNull")) { bound[0] = null; bound[1] = args[1]; return null; }
					throw new AssertionError("Unexpected JDBC bind: " + method.getName());
				});
	}
}
