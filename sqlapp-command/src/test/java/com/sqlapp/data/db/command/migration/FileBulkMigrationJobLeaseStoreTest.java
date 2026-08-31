/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.jdbc.bulk.BulkMigrationJobLease;

class FileBulkMigrationJobLeaseStoreTest {
	@TempDir
	Path directory;

	@Test
	void ownerFencesAndAtomicallyReplacesLease() throws Exception {
		final var first = new FileBulkMigrationJobLeaseStore(directory);
		final var second = new FileBulkMigrationJobLeaseStore(directory);
		final Instant now = Instant.parse("2026-08-31T12:00:00Z");
		final var owner1 = new BulkMigrationJobLease("plan", "owner-1",
				now.plusSeconds(30));
		final var owner2 = new BulkMigrationJobLease("plan", "owner-2",
				now.plusSeconds(60));

		assertTrue(first.tryAcquire(owner1, now));
		assertFalse(second.tryAcquire(owner2, now));
		assertFalse(second.renew(owner2, now));
		assertEquals(owner1, second.load("plan").orElseThrow());
		assertTrue(second.tryAcquire(owner2, now.plusSeconds(30)));
		first.release("plan", "owner-1");
		assertEquals(owner2, first.load("plan").orElseThrow());
		second.release("plan", "owner-2");
		assertTrue(first.load("plan").isEmpty());
	}

	@Test
	void sameJvmConcurrentAcquisitionHasExactlyOneWinner() throws Exception {
		final var first = new FileBulkMigrationJobLeaseStore(directory);
		final var second = new FileBulkMigrationJobLeaseStore(directory);
		final Instant now = Instant.parse("2026-08-31T12:00:00Z");
		final var ready = new CountDownLatch(2);
		final var start = new CountDownLatch(1);
		try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
			final var firstResult = executor.submit(() -> {
				ready.countDown();
				start.await();
				return first.tryAcquire(new BulkMigrationJobLease("plan", "owner-1",
						now.plusSeconds(30)), now);
			});
			final var secondResult = executor.submit(() -> {
				ready.countDown();
				start.await();
				return second.tryAcquire(new BulkMigrationJobLease("plan", "owner-2",
						now.plusSeconds(30)), now);
			});
			ready.await();
			start.countDown();
			assertTrue(firstResult.get() ^ secondResult.get());
		}
	}
}
