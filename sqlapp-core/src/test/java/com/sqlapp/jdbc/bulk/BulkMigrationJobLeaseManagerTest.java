/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class BulkMigrationJobLeaseManagerTest {
	private static final Instant NOW = Instant.parse("2026-08-31T12:00:00Z");

	@Test
	void storeOwnerFencesAcquireRenewAndRelease() throws Exception {
		final var store = new InMemoryBulkMigrationJobLeaseStore();
		final var first = new BulkMigrationJobLease("plan", "owner-1",
				NOW.plusSeconds(30));
		final var second = new BulkMigrationJobLease("plan", "owner-2",
				NOW.plusSeconds(40));
		assertTrue(store.tryAcquire(first, NOW));
		assertFalse(store.tryAcquire(second, NOW));
		assertFalse(store.renew(second, NOW));

		store.release("plan", "owner-2");
		assertEquals("owner-1", store.load("plan").orElseThrow().ownerId());
		assertTrue(store.renew(new BulkMigrationJobLease("plan", "owner-1",
				NOW.plusSeconds(60)), NOW));
		store.release("plan", "owner-1");
		assertTrue(store.load("plan").isEmpty());
	}

	@Test
	void expiredLeaseCanBeTakenButCannotBeRenewedByOldOwner() throws Exception {
		final var store = new InMemoryBulkMigrationJobLeaseStore();
		assertTrue(store.tryAcquire(new BulkMigrationJobLease("plan", "old", NOW),
				NOW.minusSeconds(1)));
		assertTrue(store.tryAcquire(new BulkMigrationJobLease("plan", "new",
				NOW.plusSeconds(30)), NOW));
		assertFalse(store.renew(new BulkMigrationJobLease("plan", "old",
				NOW.plusSeconds(60)), NOW));
		assertEquals("new", store.load("plan").orElseThrow().ownerId());
	}

	@Test
	void managerRejectsConcurrentOwnerAndReleasesIdempotently() throws Exception {
		final var store = new InMemoryBulkMigrationJobLeaseStore();
		final Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
		final var first = new BulkMigrationJobLeaseManager(store, "owner-1",
				Duration.ofSeconds(30), clock);
		final var second = new BulkMigrationJobLeaseManager(store, "owner-2",
				Duration.ofSeconds(30), clock);
		final var handle = first.acquire("plan");
		assertEquals(NOW.plusSeconds(30), handle.getLease().expiresAt());
		assertThrows(BulkMigrationJobLeaseUnavailableException.class,
				() -> second.acquire("plan"));
		handle.renew();
		handle.close();
		handle.close();
		assertTrue(store.load("plan").isEmpty());
		assertThrows(IllegalStateException.class, handle::renew);
	}

	@Test
	void chunkListenerRenewsBeforeAndAfterEachChunk() throws Exception {
		final var delegate = new InMemoryBulkMigrationJobLeaseStore();
		final var renewals = new AtomicInteger();
		final BulkMigrationJobLeaseStore store = new BulkMigrationJobLeaseStore() {
			@Override
			public Optional<BulkMigrationJobLease> load(String planFingerprint) {
				return delegate.load(planFingerprint);
			}

			@Override
			public boolean tryAcquire(BulkMigrationJobLease lease, Instant now) {
				return delegate.tryAcquire(lease, now);
			}

			@Override
			public boolean renew(BulkMigrationJobLease lease, Instant now) {
				renewals.incrementAndGet();
				return delegate.renew(lease, now);
			}

			@Override
			public void release(String planFingerprint, String ownerId) {
				delegate.release(planFingerprint, ownerId);
			}
		};
		final var manager = new BulkMigrationJobLeaseManager(store, "owner",
				Duration.ofSeconds(30), Clock.fixed(NOW, ZoneOffset.UTC));
		try (var handle = manager.acquire("plan")) {
			final var listener = new BulkMigrationJobLeaseChunkListener(handle);
			final var progress = new ChunkedBulkMigrationProgress("migration", 0, 10,
					0, 10);
			listener.onChunkStarted(progress);
			listener.onChunkCompleted(progress);
		}
		assertEquals(2, renewals.get());
	}
}
