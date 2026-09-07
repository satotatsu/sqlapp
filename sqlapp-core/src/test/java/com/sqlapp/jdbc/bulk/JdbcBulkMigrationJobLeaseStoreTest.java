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
	void rejectsAmbiguousCaseSensitiveTableMatches() throws Exception {
		final var dataSource = createDataSource();
		try (var connection = dataSource.getConnection()) {
			try (var statement = connection.createStatement()) {
				statement.execute("CREATE TABLE SQLAPP_BML_AMBIGUOUS (ID INTEGER)");
				statement.execute("CREATE TABLE \"sqlapp_bml_ambiguous\" (ID INTEGER)");
			}
			final var failure = assertThrows(SQLException.class,
					() -> new JdbcBulkMigrationJobLeaseStore(connection, "SQLAPP_BML_AMBIGUOUS"));
			assertTrue(failure.getMessage().contains("Ambiguous"));
			assertThrows(SQLException.class,
					() -> new ReadOnlyJdbcBulkMigrationJobLeaseStore(connection,
							"SQLAPP_BML_AMBIGUOUS").load("plan"));
			assertThrows(SQLException.class,
					() -> JdbcBulkMigrationJobLeaseTable.validateStructure(connection,
							"SQLAPP_BML_AMBIGUOUS"));
		} finally {
			if (dataSource instanceof AutoCloseable closeable) {
				closeable.close();
			}
		}
	}

	@Test
	void rejectsMissingAndCompositeLeasePrimaryKeys() throws Exception {
		final var dataSource = createDataSource();
		try (var connection = dataSource.getConnection()) {
			for (String suffix : new String[] { "MISSING", "COMPOSITE" }) {
				final String table = "SQLAPP_BML_PK_" + suffix;
				try (var statement = connection.createStatement()) {
					statement.execute("CREATE TABLE " + table
							+ " (PLAN_FINGERPRINT VARCHAR(256) NOT NULL,"
							+ " OWNER_ID VARCHAR(256) NOT NULL, EXPIRES_AT VARCHAR(40) NOT NULL"
							+ ("COMPOSITE".equals(suffix)
									? ", PRIMARY KEY (PLAN_FINGERPRINT, OWNER_ID)" : "") + ")");
				}
				final var failure = assertThrows(SQLException.class,
						() -> new JdbcBulkMigrationJobLeaseStore(connection, table));
				assertTrue(failure.getMessage().contains("PLAN_FINGERPRINT alone"));
				assertThrows(SQLException.class,
						() -> new ReadOnlyJdbcBulkMigrationJobLeaseStore(connection, table)
								.load("plan"));
			}
		} finally {
			if (dataSource instanceof AutoCloseable closeable) {
				closeable.close();
			}
		}
	}

	@Test
	void rejectsCorruptLeaseDataInBothStores() throws Exception {
		final var dataSource = createDataSource();
		try (var connection = dataSource.getConnection()) {
			final var writer = new JdbcBulkMigrationJobLeaseStore(connection,
					"SQLAPP_BML_CORRUPT");
			final var reader = new ReadOnlyJdbcBulkMigrationJobLeaseStore(connection,
					"SQLAPP_BML_CORRUPT");
			try (var statement = connection.prepareStatement(
					"INSERT INTO SQLAPP_BML_CORRUPT VALUES (?, ?, ?)")) {
				for (String[] values : new String[][] {
						{ "bad-time", "owner", "not-an-instant" },
						{ "bad-owner", " ", "2026-08-31T12:00:00Z" } }) {
					statement.setString(1, values[0]);
					statement.setString(2, values[1]);
					statement.setString(3, values[2]);
					statement.executeUpdate();
					assertThrows(SQLException.class, () -> writer.load(values[0]));
					assertThrows(SQLException.class, () -> reader.load(values[0]));
				}
			}
		} finally {
			if (dataSource instanceof AutoCloseable closeable) {
				closeable.close();
			}
		}
	}

	@Test
	void ignoresLeaseTablesInOtherSchemas() throws Exception {
		final var dataSource = createDataSource();
		try (var connection = dataSource.getConnection()) {
			try (var statement = connection.createStatement()) {
				statement.execute("CREATE SCHEMA OTHER_LEASE AUTHORIZATION DBA");
				statement.execute("CREATE TABLE OTHER_LEASE.SQLAPP_BML_SCOPED ("
						+ "PLAN_FINGERPRINT VARCHAR(256) PRIMARY KEY)");
			}
			final var reader = new ReadOnlyJdbcBulkMigrationJobLeaseStore(connection,
					"SQLAPP_BML_SCOPED");
			assertTrue(reader.load("plan").isEmpty());
			final var writer = new JdbcBulkMigrationJobLeaseStore(connection,
					"SQLAPP_BML_SCOPED");
			final Instant now = Instant.parse("2026-08-31T12:00:00Z");
			final var lease = new BulkMigrationJobLease("plan", "owner", now.plusSeconds(30));
			assertTrue(writer.tryAcquire(lease, now));
			assertEquals(lease, reader.load("plan").orElseThrow());
		} finally {
			if (dataSource instanceof AutoCloseable closeable) {
				closeable.close();
			}
		}
	}

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
			assertThrows(SQLException.class,
					() -> new ReadOnlyJdbcBulkMigrationJobLeaseStore(connection,
							"SQLAPP_BML_INVALID").load("plan"));
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
