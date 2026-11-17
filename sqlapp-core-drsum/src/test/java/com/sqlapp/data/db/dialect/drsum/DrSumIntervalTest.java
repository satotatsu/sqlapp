/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.drsum;
import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.*;
import com.sqlapp.data.interval.IntervalValue;
import com.sqlapp.data.schemas.Column;

class DrSumIntervalTest {
	@Test void fractionalPrecisionUsesLengthAndNeverGuessesMonthLengths() {
		var handler = new DrSumIntervalJdbcTypeHandler();
		var column = new Column("C").setDataType(DataType.INTERVAL_DAY_TO_SECOND).setLength(6);
		assertEquals("INTERVAL '+0 23:00:00'", handler.literal(IntervalValue.parse("P1DT-1H"), column));
		assertEquals(java.util.List.of("CALENDAR_DAYS_AS_24_HOURS"),
				handler.prepareInterval(IntervalValue.parse("P1DT-1H"), column).warnings());
		assertThrows(ArithmeticException.class, () -> handler.prepareInterval(IntervalValue.parse("PT0.000000001S"), column));
		assertThrows(IllegalArgumentException.class, () -> handler.prepareInterval(IntervalValue.parse("P1M"), column));
		assertThrows(IllegalArgumentException.class, () -> handler.prepareInterval(IntervalValue.ZERO, column.clone().setArrayDimension(1)));
		assertEquals(new IntervalPrecision(-1, 6), handler.intervalPrecision(column));
	}
}
