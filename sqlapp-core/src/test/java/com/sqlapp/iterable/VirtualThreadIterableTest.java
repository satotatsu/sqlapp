/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-core.
 *
 * sqlapp-core is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.iterable;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Test;

class VirtualThreadIterableTest {

	@Test
	void closeStopsProducerWithFullQueue() throws Exception {
		assertCloseStopsProducer(true, false);
	}

	@Test
	void closeStopsTerminalNotificationWithFullQueue() throws Exception {
		assertCloseStopsProducer(false, false);
		assertCloseStopsProducer(false, true);
	}

	private void assertCloseStopsProducer(boolean secondValue, boolean failure) throws Exception {
		final AtomicReference<Thread> producer = new AtomicReference<>();
		final CountDownLatch full = new CountDownLatch(1);
		final AtomicInteger finalizations = new AtomicInteger();
		final var iterator = new VirtualThreadIterable<Integer>(values -> {
			producer.set(Thread.currentThread());
			values.accept(1);
			full.countDown();
			if (secondValue) {
				values.accept(2);
			}
			if (failure) {
				throw new IllegalStateException("producer failure");
			}
		}, finalizations::incrementAndGet, 1).iterator();
		try {
			assertTrue(full.await(5, TimeUnit.SECONDS));
		} finally {
			((AutoCloseable) iterator).close();
		}
		producer.get().join(5000);
		assertFalse(producer.get().isAlive());
		assertEquals(1, finalizations.get());
		assertFalse(iterator.hasNext());
		((AutoCloseable) iterator).close();
		assertEquals(1, finalizations.get());
	}

	@Test
	void test() {
		test(0);
		test(1);
		test(1000);
	}

	void test(int count) {
		VirtualThreadIterable<String> iterable = new VirtualThreadIterable<String>(cons -> {
			for (int i = 0; i < count; i++) {
				cons.accept("a" + i);
			}
		});
		int i = 0;
		for (String val : iterable) {
			assertEquals("a" + i, val);
			i++;
		}
		assertEquals(count, i);
	}

	@Test
	void preservesProducerFailureAndSuppressesFinalizerFailure() {
		final RuntimeException producerFailure = new RuntimeException("producer");
		final RuntimeException finalizerFailure = new RuntimeException("finalizer");
		final var iterator = new VirtualThreadIterable<String>(values -> {
			throw producerFailure;
		}, () -> {
			throw finalizerFailure;
		}, 2).iterator();

		final RuntimeException thrown = assertThrows(RuntimeException.class, iterator::hasNext);

		assertSame(producerFailure, thrown);
		assertEquals(1, thrown.getSuppressed().length);
		assertSame(finalizerFailure, thrown.getSuppressed()[0]);
	}

}
