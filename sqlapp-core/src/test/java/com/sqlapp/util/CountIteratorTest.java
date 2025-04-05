package com.sqlapp.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class CountIteratorTest {

	@Test
	void test() {
		long[] holder = new long[1];
		CountIterable<Long> itr = new CountIterable<Long>(10, cnt -> holder[0] = cnt);
		long i = 0;
		for (long va : itr) {
			assertEquals(i, va);
			System.out.println(i);
			i++;
		}
		assertEquals(10, i);
		assertEquals(9, holder[0]);
	}

	@Test
	void testRange() {
		long[] holder = new long[1];
		CountIterable<Long> itr = new CountIterable<Long>(5, 25, cnt -> holder[0] = cnt);
		long i = 0;
		for (long va : itr) {
			assertEquals(i + 5, va);
			System.out.println(i);
			i++;
		}
		assertEquals(20, i);
		assertEquals(24, holder[0]);
	}

}
