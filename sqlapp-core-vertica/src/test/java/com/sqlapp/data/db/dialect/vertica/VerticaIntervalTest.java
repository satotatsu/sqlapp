/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.vertica;
import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.datatype.*;
import com.sqlapp.data.interval.IntervalValue;
import com.sqlapp.data.schemas.Column;

class VerticaIntervalTest {
	@Test void nativeDriverObjectsPreserveSignedMicrosecondsAndCalendarConversionWarnings() {
		var handler = new VerticaIntervalJdbcTypeHandler(DataType.INTERVAL_DAY_TO_SECOND);
		var column = new Column("C").setDataType(DataType.INTERVAL_DAY_TO_SECOND).setLength(6);
		for (var value : new IntervalValue[] { IntervalValue.parse("PT-0.000001S"), IntervalValue.ZERO,
				new IntervalValue(0, 0, Duration.ofSeconds(-9223372036855L, 224193000)),
				new IntervalValue(0, 0, Duration.ofSeconds(9223372036854L, 775807000)) })
			assertEquals(value, handler.decode(handler.nativeValue(value, column)));
		var projection = handler.prepareInterval(IntervalValue.parse("P1DT-1H"), column);
		assertEquals(IntervalValue.parse("PT82800S"), projection.value());
		assertEquals(java.util.List.of("CALENDAR_DAYS_AS_24_HOURS"), projection.warnings());
		assertThrows(ArithmeticException.class, () -> handler.prepareInterval(IntervalValue.parse("PT0.000000001S"), column));
		assertThrows(ArithmeticException.class, () -> handler.prepareInterval(IntervalValue.parse("PT9223372036854.775808S"), column));
		assertThrows(ArithmeticException.class, () -> handler.prepareInterval(IntervalValue.parse("PT-9223372036854.775808S"), column));
	}
	@Test void nativeYearMonthDriverRangeRejectsOverflowInsteadOfNarrowing() {
		var handler = new VerticaIntervalJdbcTypeHandler(DataType.INTERVAL_YEAR_TO_MONTH);
		var column = new Column("C").setDataType(DataType.INTERVAL_YEAR_TO_MONTH);
		var value = IntervalValue.parse("P-2147483647Y-11M");
		assertEquals(value, handler.decode(handler.nativeValue(value, column)));
		assertThrows(ArithmeticException.class, () -> handler.prepareInterval(IntervalValue.parse("P2147483648Y"), column));
		assertThrows(IllegalArgumentException.class, () -> handler.prepareInterval(IntervalValue.parse("P1M1D"), column));
	}
}
