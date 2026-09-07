/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.sqlapp.AbstractDbTest;

class JdbcBulkMigrationJobLeaseStoreTest extends AbstractDbTest {
	@Test
	void rejectsAnExistingLeaseTableWithMissingColumns() throws Exception {
		final var dataSource = createDataSource();
		try (var connection = dataSource.getConnection()) {
			try (var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE SQLAPP_BML_INVALID ("
						+ "PLAN_FINGERPRINT VARCHAR(256) PRIMARY KEY)");
			}
			assertThrows(SQLException.class,
					() -> new JdbcBulkMigrationJobLeaseStore(connection,
							"SQLAPP_BML_INVALID"));
		} finally {
			if (dataSource instanceof AutoCloseable closeable) {
				closeable.close();
			}
		}
	}

	@Test
	void readsWithoutCreatingOrChangingTheLeaseTable() throws Exception {
		final var dataSource = createDataSource();
		try (var readerConnection = dataSource.getConnection();
				var writerConnection = dataSource.getConnection()) {
			final var reader = new ReadOnlyJdbcBulkMigrationJobLeaseStore(
					readerConnection, "SQLAPP_BML_READ_TEST");
			assertTrue(reader.load("plan").isEmpty());
			try (var tables = readerConnection.getMetaData().getTables(
					readerConnection.getCatalog(), null, "SQLAPP_BML_READ_TEST",
					new String[] { "TABLE" })) {
				assertFalse(tables.next());
			}

			final var writer = new JdbcBulkMigrationJobLeaseStore(writerConnection,
					"SQLAPP_BML_READ_TEST");
			final Instant now = Instant.parse("2026-08-31T12:00:00Z");
			final var lease = new BulkMigrationJobLease("plan", "owner",
					now.plusSeconds(30));
			assertTrue(writer.tryAcquire(lease, now));
			assertEquals(lease, reader.load("plan").orElseThrow());
			assertThrows(UnsupportedOperationException.class,
					() -> reader.tryAcquire(lease, now));
			assertThrows(UnsupportedOperationException.class,
					() -> reader.renew(lease, now));
			assertThrows(UnsupportedOperationException.class,
					() -> reader.release("plan", "owner"));
		} finally {
			if (dataSource instanceof AutoCloseable closeable) {
				closeable.close();
			}
		}
	}

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
