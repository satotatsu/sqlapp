/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkMigrationCheckpoint;
import com.sqlapp.jdbc.bulk.BulkMigrationCheckpointMode;
import com.sqlapp.jdbc.bulk.BulkMigrationCheckpointStore;
import com.sqlapp.jdbc.bulk.BulkMigrationVerifier;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationExecutor;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationOption;
import com.sqlapp.jdbc.bulk.InMemoryBulkMigrationCheckpointStore;
import com.sqlapp.jdbc.bulk.JdbcBulkMigrationCheckpointStore;
import com.sqlapp.jdbc.bulk.TransactionalBulkMigrationCheckpointStore;

class ChunkedBulkMigrationTest {
	@Test
	void databaseCheckpointIsDefaultAndRollsBackDataWithCheckpoint() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE ATOMIC_TARGET (ID INTEGER PRIMARY KEY, TXT TEXT)");
			final Table source = table("ATOMIC_TARGET");
			source.getRows().add(row -> { row.put("ID", 1); row.put("TXT", "one"); });
			source.getRows().add(row -> { row.put("ID", 2); row.put("TXT", "two"); });
			final var option = ChunkedBulkMigrationOption.builder()
					.migrationId("atomic-migration").chunkSize(2).build();
			final var jdbcStore = new JdbcBulkMigrationCheckpointStore(connection,
					option.getCheckpointTableName());
			final TransactionalBulkMigrationCheckpointStore failing =
					new FailSaveJdbcStore(jdbcStore, connection);

			assertThrows(SQLException.class, () -> ChunkedBulkMigrationExecutor.execute(
					connection, source, option, failing));
			try (var resultSet = statement.executeQuery("SELECT COUNT(*) FROM ATOMIC_TARGET")) {
				resultSet.next();
				assertEquals(0, resultSet.getInt(1));
			}
			assertTrue(jdbcStore.load("atomic-migration").isEmpty());

			final var result = ChunkedBulkMigrationExecutor.execute(connection, source, option);
			assertEquals(2, result.getProcessedRows());
			assertTrue(new JdbcBulkMigrationCheckpointStore(connection,
					option.getCheckpointTableName()).load("atomic-migration").orElseThrow().isComplete());
		}
	}

	@Test
	void resumesAfterCheckpointFailureAndVerifiesChunks() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE CHUNK_TARGET (ID INTEGER PRIMARY KEY, TXT TEXT)");
			final Table source = table("CHUNK_TARGET");
			for (int i = 1; i <= 5; i++) {
				final int id = i;
				source.getRows().add(row -> {
					row.put("ID", id);
					row.put("TXT", "value-" + id);
				});
			}
			final InMemoryBulkMigrationCheckpointStore delegate =
					new InMemoryBulkMigrationCheckpointStore();
			final BulkMigrationCheckpointStore failing = new FailSecondSaveStore(delegate);
			final ChunkedBulkMigrationOption option = ChunkedBulkMigrationOption.builder()
					.migrationId("test-migration").chunkSize(2)
					.checkpointMode(BulkMigrationCheckpointMode.FILE)
					.sourceFingerprint("source-v1").targetFingerprint("target-v1").build();

			assertThrows(SQLException.class, () -> ChunkedBulkMigrationExecutor.execute(
					connection, source, option, failing));
			assertEquals(2, delegate.load("test-migration").orElseThrow().getProcessedRows());

			final var resumed = ChunkedBulkMigrationExecutor.execute(connection, source,
					option, delegate);
			assertEquals(2, resumed.getPreviouslyProcessedRows());
			assertEquals(3, resumed.getProcessedRows());
			assertEquals(3, resumed.getCompletedChunks());
			assertTrue(delegate.load("test-migration").orElseThrow().isComplete());
			final var completed = ChunkedBulkMigrationExecutor.execute(connection, source,
					option, delegate);
			assertTrue(completed.isAlreadyComplete());
			assertEquals(0, completed.getProcessedRows());
			try (var resultSet = statement.executeQuery("SELECT COUNT(*) FROM CHUNK_TARGET")) {
				resultSet.next();
				assertEquals(5, resultSet.getInt(1));
			}

			final Table actual = table("CHUNK_TARGET");
			try (var resultSet = statement.executeQuery("SELECT ID, TXT FROM CHUNK_TARGET ORDER BY ID")) {
				while (resultSet.next()) {
					actual.getRows().add(row -> {
						try {
							row.put("ID", resultSet.getInt(1));
							row.put("TXT", resultSet.getString(2));
						} catch (SQLException e) {
							throw new IllegalStateException(e);
						}
					});
				}
			}
			final var verified = BulkMigrationVerifier.verify(source, actual, 2);
			assertTrue(verified.isMatch());
			assertEquals(5, verified.getExpectedRows());
			assertEquals(3, verified.getChunks().size());

			actual.getRows().get(3).put("TXT", "different");
			final var mismatch = BulkMigrationVerifier.verify(source, actual, 2);
			assertFalse(mismatch.isMatch());
			assertEquals(1, mismatch.getMismatches().size());
			assertEquals(1, mismatch.getMismatches().get(0).getIndex());
		}
	}

	@Test
	void rejectsCheckpointForDifferentSource() throws Exception {
		final var store = new InMemoryBulkMigrationCheckpointStore();
		store.save(BulkMigrationCheckpoint.builder().migrationId("migration")
				.sourceFingerprint("old").targetFingerprint("target").build());
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:")) {
			assertThrows(IllegalArgumentException.class,
					() -> ChunkedBulkMigrationExecutor.execute(connection, table("missing"),
							ChunkedBulkMigrationOption.builder().migrationId("migration")
									.checkpointMode(BulkMigrationCheckpointMode.CUSTOM)
									.sourceFingerprint("new").targetFingerprint("target").build(),
							store));
		}
	}

	private static Table table(final String name) {
		final Table table = new Table(name);
		final Column id = new Column("ID").setDataType(DataType.INT);
		table.getColumns().add(id);
		table.getColumns().add(new Column("TXT").setDataType(DataType.VARCHAR));
		table.setPrimaryKey("PK_" + name, id);
		return table;
	}

	private static final class FailSecondSaveStore implements BulkMigrationCheckpointStore {
		private final BulkMigrationCheckpointStore delegate;
		private int saves;

		private FailSecondSaveStore(final BulkMigrationCheckpointStore delegate) {
			this.delegate = delegate;
		}

		@Override
		public Optional<BulkMigrationCheckpoint> load(final String migrationId) throws SQLException {
			return delegate.load(migrationId);
		}

		@Override
		public void save(final BulkMigrationCheckpoint checkpoint) throws SQLException {
			if (++saves == 2) {
				throw new SQLException("simulated checkpoint failure");
			}
			delegate.save(checkpoint);
		}

		@Override
		public void delete(final String migrationId) throws SQLException {
			delegate.delete(migrationId);
		}
	}

	private static final class FailSaveJdbcStore
			implements TransactionalBulkMigrationCheckpointStore {
		private final BulkMigrationCheckpointStore delegate;
		private final java.sql.Connection connection;

		private FailSaveJdbcStore(final BulkMigrationCheckpointStore delegate,
				final java.sql.Connection connection) {
			this.delegate = delegate;
			this.connection = connection;
		}

		@Override
		public boolean participatesIn(final java.sql.Connection candidate) {
			return connection == candidate;
		}

		@Override
		public Optional<BulkMigrationCheckpoint> load(final String migrationId) throws SQLException {
			return delegate.load(migrationId);
		}

		@Override
		public void save(final BulkMigrationCheckpoint checkpoint) throws SQLException {
			throw new SQLException("simulated database checkpoint failure");
		}

		@Override
		public void delete(final String migrationId) throws SQLException {
			delegate.delete(migrationId);
		}
	}
}
