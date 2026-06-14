package com.sqlapp.iterable;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class VirtualThreadIterableTest {

	@Test
	void test() {
		test(0);
		test(1);
		test(1000);
	}

	void test(int count) {
		VirtualThreadIterable<String> iterable = new VirtualThreadIterable<String>(queue -> {
			for (int i = 0; i < count; i++) {
				try {
					queue.put("a" + i);
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
		}, 100);
		int i = 0;
		for (String val : iterable) {
			assertEquals("a" + i, val);
			i++;
		}
		assertEquals(count, i);
	}

}
