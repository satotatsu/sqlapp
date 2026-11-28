/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.datatype;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.interval.IntervalValue;

class IntervalArraysTest {
	@Test
	void retainsNullsAndChecksDeclaredShapeAndInnermostBounds() {
		Object[] value = { new Object[] { IntervalValue.ZERO, null }, null };
		assertArrayEquals(value, (Object[]) IntervalArrays.map(value, 2, leaf -> leaf));
		IntervalArrays.validateBounds(value, 2, "2,2");
		assertThrows(IllegalArgumentException.class, () -> IntervalArrays.validateBounds(value, 2, "1,2"));
		assertThrows(IllegalArgumentException.class, () -> IntervalArrays.validateBounds(value, 2, "2,1"));
		assertThrows(IllegalArgumentException.class, () -> IntervalArrays.map(value, 1, leaf -> leaf));
		assertThrows(IllegalArgumentException.class,
				() -> IntervalArrays.map(List.of(IntervalValue.ZERO), 2, leaf -> leaf));
	}

	@Test
	void postgresShapeRejectsRaggedNullAndEmptySubarrays() {
		Object[] good = { new Object[] { IntervalValue.ZERO, null }, new Object[] { null, IntervalValue.ZERO } };
		assertArrayEquals(new int[] { 2, 2 }, IntervalArrays.rectangular(good, 2));
		for (Object[] bad : new Object[][] {
				{ new Object[] { IntervalValue.ZERO }, new Object[] { IntervalValue.ZERO, IntervalValue.ZERO } },
				{ new Object[] { IntervalValue.ZERO }, null }, { new Object[0], new Object[0] } })
			assertThrows(IllegalArgumentException.class, () -> IntervalArrays.rectangular(bad, 2));
	}
}
