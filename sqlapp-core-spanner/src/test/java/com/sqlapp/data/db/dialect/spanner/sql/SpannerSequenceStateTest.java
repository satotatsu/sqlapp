package com.sqlapp.data.db.dialect.spanner.sql;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Sequence;

class SpannerSequenceStateTest {
	@Test
	void projectsOnlyMatchingDefinitionsAndDetectsOverflow() {
		final var sequence = new Sequence("OrderSeq").setSchemaName("Sales").setStartValue(BigInteger.TEN);
		final var used = new SpannerSequenceState("Sales", "OrderSeq", 100L);
		assertEquals(BigInteger.valueOf(1100), used.forCreate(sequence, 1000).getStartValue());
		assertEquals(BigInteger.TEN, sequence.getStartValue());
		assertEquals(BigInteger.TEN,
				new SpannerSequenceState("Sales", "OrderSeq", null).forCreate(sequence, 1000).getStartValue());
		assertThrows(IllegalArgumentException.class, () -> used.forCreate(sequence, 0));
		assertThrows(IllegalArgumentException.class,
				() -> used.forCreate(sequence.clone().setSchemaName("Archive"), 1));
		assertThrows(ArithmeticException.class,
				() -> new SpannerSequenceState("Sales", "OrderSeq", Long.MAX_VALUE).forCreate(sequence, 1));
	}
}
