/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.metadata;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Sequence;

class SequenceNextValuesTest {
	private static BigDecimal d(long value) {
		return BigDecimal.valueOf(value);
	}

	@Test
	void addsTheIncrementIncludingStepsAboveOneAndNegativeSteps() {
		assertEquals(d(11), SequenceNextValues.after(d(10), null, null, null, false));
		assertEquals(d(15), SequenceNextValues.after(d(10), d(5), null, null, false));
		assertEquals(d(7), SequenceNextValues.after(d(10), d(-3), null, null, false));
		assertNull(SequenceNextValues.after(null, d(5), null, null, false));
	}

	@Test
	void cyclesOnlyWhenRequestedAndBoundsAreKnown() {
		assertEquals(d(1), SequenceNextValues.after(d(98), d(5), d(1), d(100), true));
		assertEquals(d(100), SequenceNextValues.after(d(3), d(-5), d(1), d(100), true));
		// Without CYCLE an exhausted sequence stays beyond its bound so recreation fails clearly.
		assertEquals(d(103), SequenceNextValues.after(d(98), d(5), d(1), d(100), false));
		assertEquals(d(103), SequenceNextValues.after(d(98), d(5), null, d(100), true));
		assertEquals(d(100), SequenceNextValues.after(d(95), d(5), d(1), d(100), true));
	}

	@Test
	void identityValuesAddTheStep() {
		assertEquals(Long.valueOf(30), SequenceNextValues.after(Long.valueOf(20), Long.valueOf(10)));
		assertEquals(Long.valueOf(21), SequenceNextValues.after(Long.valueOf(20), null));
		assertNull(SequenceNextValues.after((Long) null, Long.valueOf(10)));
		assertThrows(ArithmeticException.class, () -> SequenceNextValues.after(Long.MAX_VALUE, 1L));
	}

	@Test
	void zeroIncrementIsRejected() {
		assertThrows(IllegalArgumentException.class,
				() -> SequenceNextValues.after(d(1), BigDecimal.ZERO, null, null, false));
		assertThrows(IllegalArgumentException.class, () -> SequenceNextValues.after(1L, 0L));
	}

	@Test
	void nextValueForReturnsTheStoredNextValueAndAdvances() {
		var sequence = new Sequence("S").setStartValue(10).setIncrementBy(5);
		assertEquals(BigInteger.valueOf(10), sequence.nextValueFor());
		assertEquals(BigInteger.valueOf(15), sequence.getLastValue());
		assertEquals(BigInteger.valueOf(15), sequence.nextValueFor());
		assertEquals(BigInteger.valueOf(20), sequence.nextValueFor());
	}
}
