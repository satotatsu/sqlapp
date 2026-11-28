/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.schemas;

import static org.junit.jupiter.api.Assertions.*;

import java.math.BigInteger;

import org.junit.jupiter.api.Test;

class DefaultSchemaEqualsHandlerSequenceValuesTest {
	@Test
	void nextValuesAreExcludedByDefault() {
		var original = new Sequence("S").setStartValue(BigInteger.ONE);
		var target = original.clone().setLastValue(BigInteger.valueOf(100));
		assertTrue(original.equals(target, new DefaultSchemaEqualsHandler()));
		assertTrue(original.diff(target, new DefaultSchemaEqualsHandler()).getChangedProperties().isEmpty());

		var column = new Column("ID").setIdentity(true);
		var advanced = column.clone().setIdentityLastValue(50L);
		assertTrue(column.equals(advanced, new DefaultSchemaEqualsHandler()));
	}

	@Test
	void fullComparisonIncludesNextValues() {
		var handler = new DefaultSchemaEqualsHandler().setCompareSequenceValues(true);
		var original = new Sequence("S").setStartValue(BigInteger.ONE);
		var target = original.clone().setLastValue(BigInteger.valueOf(100));
		assertFalse(original.equals(target, handler));
		assertEquals(java.util.Set.of(SchemaProperties.LAST_VALUE.getLabel()),
				original.diff(target, handler).getChangedProperties().keySet());
		assertFalse(new Column("ID").setIdentity(true)
				.equals(new Column("ID").setIdentity(true).setIdentityLastValue(50L), handler));
		assertTrue(handler.clone().isCompareSequenceValues());
		// Other excluded properties stay excluded.
		var withStatistics = original.clone();
		withStatistics.getStatistics().put("rows", "1");
		assertTrue(original.equals(withStatistics, handler));
	}

	@Test
	void plainEqualsStillComparesEverything() {
		var original = new Sequence("S");
		assertNotEquals(original, original.clone().setLastValue(BigInteger.TEN));
	}
}
