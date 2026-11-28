/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.postgres;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.ByteBuffer;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.interval.IntervalValue;

class PostgresIntervalBinaryTest {

	@Test
	void signedNativeLimitsRoundTripWithoutCalendarNormalization() {
		for (long micros : new long[] { Long.MIN_VALUE, Long.MAX_VALUE, -1, 0, 1 }) {
			var value = new IntervalValue(Integer.MIN_VALUE, Integer.MAX_VALUE,
					Duration.ofSeconds(Math.floorDiv(micros, 1_000_000), Math.floorMod(micros, 1_000_000) * 1000));
			assertEquals(micros, PostgresIntervalBinary.microseconds(value));
			assertEquals(value, PostgresIntervalBinary.readScalar(PostgresIntervalBinary.scalar(value)));
		}
		var day = IntervalValue.parse("P1D");
		var hours = IntervalValue.parse("PT24H");
		assertNotEquals(PostgresIntervalBinary.readScalar(PostgresIntervalBinary.scalar(day)),
				PostgresIntervalBinary.readScalar(PostgresIntervalBinary.scalar(hours)));
	}

	@Test
	void arraysPreserveShapeNullsAndMixedSigns() {
		Object[] input = { new Object[] { IntervalValue.parse("P1DT-1H"), null },
				new Object[] { IntervalValue.parse("P-2M"), IntervalValue.parse("PT-0.000001S") } };
		assertArrayEquals(input, (Object[]) PostgresIntervalBinary.readArray(PostgresIntervalBinary.array(input)));
		assertArrayEquals(new Object[0],
				(Object[]) PostgresIntervalBinary.readArray(PostgresIntervalBinary.array(new Object[0])));
		assertThrows(IllegalArgumentException.class,
				() -> PostgresIntervalBinary.array(new Object[] { new Object[1], new Object[2] }));
	}

	@Test
	void invalidOrUnrepresentablePayloadsFailExplicitly() {
		assertThrows(IllegalArgumentException.class, () -> PostgresIntervalBinary.readScalar(new byte[15]));
		assertThrows(IllegalArgumentException.class, () -> PostgresIntervalBinary.readArray(new byte[4]));
		byte[] encoded = PostgresIntervalBinary.array(new Object[] { IntervalValue.parse("PT1S") });
		ByteBuffer.wrap(encoded).putInt(16, 0);
		assertThrows(IllegalArgumentException.class, () -> PostgresIntervalBinary.readArray(encoded));
		assertThrows(ArithmeticException.class,
				() -> PostgresIntervalBinary.scalar(IntervalValue.parse("PT0.000000001S")));
		assertThrows(ArithmeticException.class,
				() -> PostgresIntervalBinary.scalar(new IntervalValue((long) Integer.MAX_VALUE + 1, 0, Duration.ZERO)));
	}
}
