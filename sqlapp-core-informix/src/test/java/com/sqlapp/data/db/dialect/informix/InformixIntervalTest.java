/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix;

import static org.junit.jupiter.api.Assertions.*;
import java.lang.reflect.Proxy;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.*;
import com.sqlapp.data.interval.IntervalValue;
import com.sqlapp.data.schemas.Column;

class InformixIntervalTest {
	@Test
	void nativeQualifiersAndFractionOnlyValuesKeepExactFields() throws Exception {
		var handler = new InformixIntervalJdbcTypeHandler(DataType.INTERVAL_DAY_TO_SECOND);
		var hour = new Column("C").setDataType(DataType.INTERVAL_DAY_TO_SECOND)
				.setDataTypeName("INTERVAL HOUR(6) TO FRACTION(5)");
		var value = IntervalValue.parse("PT90000.12345S");
		var metadata = (java.sql.ResultSetMetaData) Proxy.newProxyInstance(getClass().getClassLoader(),
				new Class<?>[] { java.sql.ResultSetMetaData.class }, (proxy, method, args) -> java.sql.Types.VARCHAR);
		var rows = (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(), new Class<?>[] { ResultSet.class },
				(proxy, method, args) -> method.getName().equals("getMetaData") ? metadata : "25:00:00.12345");
		assertEquals(value, handler.getObject(rows, 1, hour));
		assertEquals("INTERVAL(+25:00:00.12345) HOUR(6) TO FRACTION(5)", handler.literal(value, hour));
		assertThrows(ArithmeticException.class,
				() -> handler.prepareInterval(IntervalValue.parse("PT0.000001S"), hour));
		var fraction = hour.clone().setDataTypeName("INTERVAL FRACTION TO FRACTION(5)");
		assertEquals(IntervalValue.parse("PT0.12345S"), handler.prepareInterval(".12345", fraction).value());
		assertThrows(ArithmeticException.class, () -> handler.prepareInterval(IntervalValue.parse("PT1S"), fraction));
		var normalized = handler.prepareInterval(IntervalValue.parse("P1DT-1H"), hour);
		assertEquals(IntervalValue.parse("PT82800S"), normalized.value());
		assertEquals(java.util.List.of("CALENDAR_DAYS_AS_24_HOURS"), normalized.warnings());
		assertThrows(ArithmeticException.class,
				() -> handler.prepareInterval(value, hour.clone().setDataTypeName("INTERVAL HOUR(6) TO SECOND")));
	}

	@Test
	void yearMonthRangeAndPrecisionMappingUseLongMonths() {
		var handler = new InformixIntervalJdbcTypeHandler(DataType.INTERVAL_YEAR_TO_MONTH);
		var column = new Column("C").setDataType(DataType.INTERVAL_YEAR_TO_MONTH)
				.setDataTypeName("INTERVAL YEAR(9) TO MONTH");
		var value = IntervalValue.parse("P999999999Y11M");
		assertEquals(value, handler.prepareInterval(value, column).value());
		assertEquals("INTERVAL(+999999999-11) YEAR(9) TO MONTH", handler.literal(value, column));
		assertThrows(ArithmeticException.class,
				() -> handler.prepareInterval(IntervalValue.parse("P1000000000Y"), column));
		var mapped = handler.adaptIntervalColumn(column.clone(), new IntervalPrecision(18, 9));
		assertEquals(new IntervalPrecision(9, 0), handler.intervalPrecision(mapped));
	}
}
