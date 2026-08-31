/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Path;
import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.jdbc.bulk.BulkMigrationJobLeaseMode;
import com.sqlapp.jdbc.bulk.BulkMigrationJobLeaseUnavailableException;
import com.sqlapp.jdbc.bulk.JdbcBulkMigrationJobLeaseStore;

class BulkMigrationJobLeaseManagerFactoryTest {
	@TempDir
	Path directory;

	@Test
	void databaseIsTheDefaultConfiguration() {
		final var configuration = BulkMigrationJobLeaseConfiguration
				.database("worker-1");
		assertEquals(BulkMigrationJobLeaseMode.DATABASE, configuration.mode());
		assertEquals(BulkMigrationJobLeaseConfiguration.DEFAULT_DURATION,
				configuration.duration());
		assertEquals(JdbcBulkMigrationJobLeaseStore.DEFAULT_TABLE_NAME,
				configuration.tableName());
		assertThrows(NullPointerException.class,
				() -> BulkMigrationJobLeaseManagerFactory.create(null, configuration));
	}

	@Test
	void fileConfigurationCreatesCompetingManagersWithoutAConnection()
			throws Exception {
		final var first = BulkMigrationJobLeaseManagerFactory.create(null,
				BulkMigrationJobLeaseConfiguration.file("worker-1", directory));
		final var second = BulkMigrationJobLeaseManagerFactory.create(null,
				BulkMigrationJobLeaseConfiguration.file("worker-2", directory));
		try (var ignored = first.acquire("plan")) {
			assertThrows(BulkMigrationJobLeaseUnavailableException.class,
					() -> second.acquire("plan"));
		}
		try (var lease = second.acquire("plan")) {
			assertEquals("worker-2", lease.getLease().ownerId());
		}
	}

	@Test
	void rejectsAmbiguousOrUnsafeConfiguration() {
		assertThrows(IllegalArgumentException.class,
				() -> new BulkMigrationJobLeaseConfiguration(
						BulkMigrationJobLeaseMode.DATABASE, "worker", Duration.ZERO,
						"LEASES", null));
		assertThrows(IllegalArgumentException.class,
				() -> new BulkMigrationJobLeaseConfiguration(
						BulkMigrationJobLeaseMode.DATABASE, "worker", Duration.ofMinutes(1),
						"LEASES", directory));
		assertThrows(IllegalArgumentException.class,
				() -> new BulkMigrationJobLeaseConfiguration(
						BulkMigrationJobLeaseMode.FILE, "worker", Duration.ofMinutes(1),
						"LEASES", directory));
	}
}
