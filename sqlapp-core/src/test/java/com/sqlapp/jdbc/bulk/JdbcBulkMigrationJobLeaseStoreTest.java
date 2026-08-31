/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.sqlapp.AbstractDbTest;

class JdbcBulkMigrationJobLeaseStoreTest extends AbstractDbTest {
	@Test
	void coordinatesOwnersAcrossDedicatedConnections() throws Exception {
		final var dataSource = createDataSource();
		try (var firstConnection = dataSource.getConnection();
				var secondConnection = dataSource.getConnection()) {
			final var first = new JdbcBulkMigrationJobLeaseStore(firstConnection,
					"SQLAPP_BML_TEST");
			final var second = new JdbcBulkMigrationJobLeaseStore(secondConnection,
					"SQLAPP_BML_TEST");
			final Instant now = Instant.parse("2026-08-31T12:00:00Z");
			final var owner1 = new BulkMigrationJobLease("plan", "owner-1",
					now.plusSeconds(30));
			final var owner2 = new BulkMigrationJobLease("plan", "owner-2",
					now.plusSeconds(60));

			assertTrue(first.tryAcquire(owner1, now));
			assertFalse(second.tryAcquire(owner2, now));
			assertFalse(second.renew(owner2, now));
			assertEquals("owner-1", second.load("plan").orElseThrow().ownerId());

			assertTrue(second.tryAcquire(owner2, now.plusSeconds(30)));
			first.release("plan", "owner-1");
			assertEquals("owner-2", first.load("plan").orElseThrow().ownerId());
			assertTrue(second.renew(new BulkMigrationJobLease("plan", "owner-2",
					now.plusSeconds(90)), now.plusSeconds(31)));
			second.release("plan", "owner-2");
			assertTrue(first.load("plan").isEmpty());
		} finally {
			if (dataSource instanceof AutoCloseable closeable) {
				closeable.close();
			}
		}
	}
}
