/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkMigrationCheckpoint;
import com.sqlapp.jdbc.bulk.BulkMigrationCheckpointMode;
import com.sqlapp.jdbc.bulk.BulkMigrationCheckpointStore;
import com.sqlapp.jdbc.bulk.BulkMigrationKeysetSource;
import com.sqlapp.jdbc.bulk.BulkMigrationVerifier;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationExecutor;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationOption;
import com.sqlapp.jdbc.bulk.InMemoryBulkMigrationCheckpointStore;
import com.sqlapp.jdbc.bulk.JdbcBulkMigrationCheckpointStore;
import com.sqlapp.jdbc.bulk.JdbcBulkMigrationKeysetSource;
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

	@Test
	void keysetResumeIsNotShiftedByRowsInsertedBeforeTheCursor() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE KEYSET_TARGET (ID INTEGER PRIMARY KEY, TXT TEXT)");
			final var source = new IntegerKeysetSource("KEYSET_TARGET", 1, 2, 3, 4, 5);
			final var delegate = new InMemoryBulkMigrationCheckpointStore();
			final var option = ChunkedBulkMigrationOption.builder()
					.migrationId("keyset-migration").chunkSize(2)
					.checkpointMode(BulkMigrationCheckpointMode.FILE).build();

			assertThrows(SQLException.class, () -> ChunkedBulkMigrationExecutor.execute(
					connection, source, option, new FailSecondSaveStore(delegate)));
			assertEquals("2", delegate.load("keyset-migration").orElseThrow().getResumeToken());

			// Count resume would shift when ID 0 is inserted. Keyset resumes after ID 2.
			source.setIds(0, 1, 2, 3, 4, 5);
			final var resumed = ChunkedBulkMigrationExecutor.execute(connection, source, option, delegate);
			assertEquals(3, resumed.getProcessedRows());
			assertEquals("5", delegate.load("keyset-migration").orElseThrow().getResumeToken());
			try (var resultSet = statement.executeQuery("SELECT GROUP_CONCAT(ID, ',') FROM "
					+ "(SELECT ID FROM KEYSET_TARGET ORDER BY ID)")) {
				resultSet.next();
				assertEquals("1,2,3,4,5", resultSet.getString(1));
			}
		}
	}

	@Test
	void upgradesAnExistingCountCheckpointTableForKeysetTokens() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE OLD_CHECKPOINT ("
					+ "migration_id VARCHAR(255) NOT NULL PRIMARY KEY, "
					+ "source_fingerprint VARCHAR(255), target_fingerprint VARCHAR(255), "
					+ "processed_rows DECIMAL(19, 0) NOT NULL, "
					+ "completed_chunks DECIMAL(19, 0) NOT NULL, "
					+ "last_chunk_hash VARCHAR(64), complete_flag CHAR(1) NOT NULL)");
			final var store = new JdbcBulkMigrationCheckpointStore(connection, "OLD_CHECKPOINT");
			final var checkpoint = BulkMigrationCheckpoint.builder().migrationId("keyset")
					.processedRows(2).completedChunks(1).resumeToken("2").build();
			store.save(checkpoint);
			assertEquals(checkpoint, store.load("keyset").orElseThrow());
		}
	}

	@Test
	void jdbcKeysetSourceResumesAfterACompositeKey() throws Exception {
		try (var connection = DriverManager.getConnection("jdbc:sqlite::memory:");
				var statement = connection.createStatement()) {
			statement.execute("CREATE TABLE COMPOSITE_SOURCE ("
					+ "KEY1 INTEGER NOT NULL, KEY2 INTEGER NOT NULL, TXT TEXT, "
					+ "PRIMARY KEY (KEY1, KEY2))");
			statement.executeUpdate("INSERT INTO COMPOSITE_SOURCE VALUES "
					+ "(1, 1, 'a'), (1, 2, 'b'), (2, 1, 'c'), (2, 2, 'd')");
			final Table table = new Table("COMPOSITE_SOURCE");
			final Column key1 = new Column("KEY1").setDataType(DataType.INT).setNotNull(true);
			final Column key2 = new Column("KEY2").setDataType(DataType.INT).setNotNull(true);
			table.getColumns().add(key1);
			table.getColumns().add(key2);
			table.getColumns().add(new Column("TXT").setDataType(DataType.VARCHAR));
			table.setPrimaryKey("PK_COMPOSITE_SOURCE", key1, key2);
			final var source = new JdbcBulkMigrationKeysetSource(connection, table);

			final Iterator<Row> all = source.iterator(null);
			assertEquals(Integer.valueOf(1), all.next().get("KEY1"));
			final Row cursorRow = all.next();
			assertEquals(Integer.valueOf(2), cursorRow.get("KEY2"));
			if (all instanceof AutoCloseable closeable) {
				closeable.close();
			}

			final Iterator<Row> resumed = source.iterator(source.resumeToken(cursorRow));
			final Row first = resumed.next();
			assertEquals(Integer.valueOf(2), first.get("KEY1"));
			assertEquals(Integer.valueOf(1), first.get("KEY2"));
			assertEquals("d", resumed.next().get("TXT"));
			assertFalse(resumed.hasNext());
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

	private static final class IntegerKeysetSource implements BulkMigrationKeysetSource {
		private final Table table;
		private List<Integer> ids;

		private IntegerKeysetSource(final String tableName, final Integer... ids) {
			this.table = table(tableName);
			this.ids = List.of(ids);
		}

		private void setIds(final Integer... ids) {
			this.ids = List.of(ids);
		}

		@Override
		public Table getTable() {
			return table;
		}

		@Override
		public Iterator<Row> iterator(final String resumeToken) {
			final int after = resumeToken == null ? Integer.MIN_VALUE : Integer.parseInt(resumeToken);
			return ids.stream().filter(id -> id > after).map(id -> {
				final Row row = table.newRow();
				row.put("ID", id);
				row.put("TXT", "value-" + id);
				return row;
			}).iterator();
		}

		@Override
		public String resumeToken(final Row row) {
			return java.util.Objects.toString(row.get("ID"));
		}
	}
}
