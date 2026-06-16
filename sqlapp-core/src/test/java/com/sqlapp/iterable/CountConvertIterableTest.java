package com.sqlapp.iterable;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

import com.sqlapp.util.iterator.Iterators;

class CountConvertIterableTest {

	@Test
	void testZero() {
		CountConvertIterable<Long, String> iterable = new CountConvertIterable<>(Iterators.range(0L), (index, val) -> {
			return "a" + val;
		});
		long i = 0;
		for (String val : iterable) {
			assertEquals("a" + i, val);
			i++;
		}
		assertEquals(0, i);
	}

	@Test
	void test() {
		CountConvertIterable<Long, String> iterable = new CountConvertIterable<>(Iterators.range(10L), (index, val) -> {
			return "a" + val;
		});
		long i = 0;
		for (String val : iterable) {
			assertEquals("a" + i, val);
			i++;
		}
		assertEquals(10, i);
	}

}
