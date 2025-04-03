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

}
