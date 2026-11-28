package com.sqlapp.data.db.datatype;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Column;

class StandardIntervalJdbcTypeHandlerTest {
	@Test
	void omittedFractionalPrecisionMatchesTheCodecForALimitedScale() {
		var handler = new StandardIntervalJdbcTypeHandler(DataType.INTERVAL_DAY_TO_SECOND, 1, 9, 3);
		var column = new Column("C").setDataType(DataType.INTERVAL_DAY_TO_SECOND);

		assertEquals(new IntervalPrecision(2, 3), handler.intervalPrecision(column));
		assertEquals("DAY(2) TO SECOND(3)", handler.codec(column).qualifier());
	}

	@Test
	void omittedFractionalPrecisionDefaultsToSixWhenAllowed() {
		var handler = new StandardIntervalJdbcTypeHandler(DataType.INTERVAL_DAY_TO_SECOND, 1, 9, 9);
		var column = new Column("C").setDataType(DataType.INTERVAL_DAY_TO_SECOND);

		assertEquals(new IntervalPrecision(2, 6), handler.intervalPrecision(column));
		assertEquals("DAY(2) TO SECOND(6)", handler.codec(column).qualifier());
	}
}
