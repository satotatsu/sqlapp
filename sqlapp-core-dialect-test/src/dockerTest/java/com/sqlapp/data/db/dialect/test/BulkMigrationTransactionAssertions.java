/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;
import java.nio.file.Path;
import java.time.Instant;

import com.sqlapp.data.db.command.migration.FileBulkMigrationCheckpointStore;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkMigrationCheckpoint;
import com.sqlapp.jdbc.bulk.BulkMigrationCheckpointMode;
import com.sqlapp.jdbc.bulk.BulkMigrationCheckpointStore;
import com.sqlapp.jdbc.bulk.BulkMigrationMaintenanceState;
import com.sqlapp.jdbc.bulk.BulkMigrationMaintenanceStatus;
import com.sqlapp.jdbc.bulk.BulkMigrationMode;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationExecutor;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationListener;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationOption;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationPausedException;
import com.sqlapp.jdbc.bulk.JdbcBulkMigrationCheckpointStore;
import com.sqlapp.jdbc.bulk.JdbcBulkMigrationMaintenanceStateStore;

/** Shared real-database assertions for atomic data/checkpoint commits. */
public final class BulkMigrationTransactionAssertions {
	private BulkMigrationTransactionAssertions() {
	}

	public static void assertJdbcCheckpointReadOnly(final Connection connection) throws SQLException {
		final String tableName = "SQLAPP_BULK_READ_ONLY_CHECKPOINT";
		final String migrationId = "read-only-" + java.util.UUID.randomUUID();
		final var checkpoint = BulkMigrationCheckpoint.builder().migrationId(migrationId).sourceFingerprint("source-v1")
				.targetFingerprint("target-v1").processedRows(3).completedChunks(2).chunkSize(2).lastChunkHash("hash-3")
				.resumeToken("token-3").complete(true).build().validate();
		new JdbcBulkMigrationCheckpointStore(connection, tableName).save(checkpoint);
		assertEquals(checkpoint,
				JdbcBulkMigrationCheckpointStore.readOnly(connection, tableName).load(migrationId).orElseThrow());
	}

	public static void assertJdbcMaintenanceReadOnly(final Connection connection) throws SQLException {
		final String tableName = "SQLAPP_BULK_READ_ONLY_MAINTENANCE";
		final String fingerprint = "read-only-" + java.util.UUID.randomUUID();
		final var state = new BulkMigrationMaintenanceState(fingerprint, fingerprint,
				BulkMigrationMaintenanceStatus.PREPARED, Instant.EPOCH, null);
		new JdbcBulkMigrationMaintenanceStateStore(connection, tableName).save(state);
		assertEquals(state,
				JdbcBulkMigrationMaintenanceStateStore.readOnly(connection, tableName).load(fingerprint).orElseThrow());
	}

	public static void assertDatabaseCheckpointAtomic(final Connection connection, final Table table,
			final String codeColumn, final String nameColumn, final String countSql) throws SQLException {
		for (int i = 1; i <= 3; i++) {
			final int value = i;
			table.getRows().add(row -> {
				row.put(codeColumn, "C" + value);
				row.put(nameColumn, "name-" + value);
			});
		}
		final String migrationId = "docker-" + java.util.UUID.randomUUID();
		final var option = ChunkedBulkMigrationOption.builder().migrationId(migrationId)
				.checkpointTableName(checkpointTableName()).sourceFingerprint("source-v1")
				.targetFingerprint("target-v1").chunkSize(2).build();
		final var checkpointStore = new JdbcBulkMigrationCheckpointStore(connection, option.getCheckpointTableName());
		assertThrows(SQLException.class, () -> ChunkedBulkMigrationExecutor.execute(connection, table, option,
				new FailingTransactionalCheckpointStore(connection, checkpointStore)));
		assertEquals(0, count(connection, countSql));
		assertTrue(checkpointStore.load(migrationId).isEmpty());

		final var result = ChunkedBulkMigrationExecutor.execute(connection, table, option);
		assertEquals(3, result.getProcessedRows());
		assertEquals(2, result.getCompletedChunks());
		assertEquals(3, count(connection, countSql));
		assertEquals(3, checkpointStore.load(migrationId).orElseThrow().getProcessedRows());
		assertEquals(3, JdbcBulkMigrationCheckpointStore.readOnly(connection, option.getCheckpointTableName())
				.load(migrationId).orElseThrow().getProcessedRows());
		assertDatabaseCheckpointPauseAndResume(connection, table, codeColumn, nameColumn, countSql, 3);
	}

	public static void assertDatabaseCheckpointRejected(final Connection connection, final Table table,
			final String codeColumn, final String nameColumn, final String countSql) throws SQLException {
		table.getRows().add(row -> {
			row.put(codeColumn, "C1");
			row.put(nameColumn, "name-1");
		});
		final var option = ChunkedBulkMigrationOption.builder()
				.migrationId("docker-rejected-" + java.util.UUID.randomUUID()).sourceFingerprint("source-v1")
				.targetFingerprint("target-v1").checkpointTableName(checkpointTableName()).build();
		assertThrows(IllegalStateException.class,
				() -> ChunkedBulkMigrationExecutor.execute(connection, table, option));
		assertEquals(0, count(connection, countSql));
	}

	public static void assertDatabaseCheckpointInsertAtomic(final Connection connection, final Table table,
			final String codeColumn, final String nameColumn, final String countSql) throws SQLException {
		table.getRows().clear();
		for (int i = 1; i <= 3; i++) {
			final int value = i;
			table.getRows().add(row -> {
				row.put(codeColumn, "I" + value);
				row.put(nameColumn, "insert-" + value);
			});
		}
		final String migrationId = "docker-insert-" + java.util.UUID.randomUUID();
		final var option = ChunkedBulkMigrationOption.builder().migrationId(migrationId)
				.checkpointTableName(checkpointTableName()).sourceFingerprint("source-v1")
				.targetFingerprint("target-v1").chunkSize(2).mode(BulkMigrationMode.INSERT).build();
		final var checkpointStore = new JdbcBulkMigrationCheckpointStore(connection, option.getCheckpointTableName());
		assertThrows(SQLException.class, () -> ChunkedBulkMigrationExecutor.execute(connection, table, option,
				new FailingTransactionalCheckpointStore(connection, checkpointStore)));
		assertEquals(0, count(connection, countSql));
		assertTrue(checkpointStore.load(migrationId).isEmpty());

		final var result = ChunkedBulkMigrationExecutor.execute(connection, table, option);
		assertEquals(3, result.getProcessedRows());
		assertEquals(2, result.getCompletedChunks());
		assertEquals(3, count(connection, countSql));
		assertEquals(3, JdbcBulkMigrationCheckpointStore.readOnly(connection, option.getCheckpointTableName())
				.load(migrationId).orElseThrow().getProcessedRows());
		assertPauseAndResumeExactlyOnce(connection, table, codeColumn, nameColumn, countSql,
				checkpointStore, BulkMigrationCheckpointMode.DATABASE, BulkMigrationMode.INSERT, 3);
	}

	public static void assertDatabaseCheckpointInsertRejected(final Connection connection, final Table table,
			final String codeColumn, final String nameColumn, final String countSql) throws SQLException {
		table.getRows().clear();
		table.getRows().add(row -> {
			row.put(codeColumn, "I1");
			row.put(nameColumn, "insert-1");
		});
		final var option = ChunkedBulkMigrationOption.builder()
				.migrationId("docker-insert-rejected-" + java.util.UUID.randomUUID()).sourceFingerprint("source-v1")
				.targetFingerprint("target-v1").checkpointTableName(checkpointTableName())
				.mode(BulkMigrationMode.INSERT).build();
		assertThrows(IllegalStateException.class,
				() -> ChunkedBulkMigrationExecutor.execute(connection, table, option));
		assertEquals(0, count(connection, countSql));
	}

	public static void assertFileCheckpointCompletes(final Connection connection, final Table table,
			final String codeColumn, final String nameColumn, final String countSql, final Path checkpointDirectory)
			throws SQLException {
		table.getRows().clear();
		for (int i = 1; i <= 3; i++) {
			final int value = i;
			table.getRows().add(row -> {
				row.put(codeColumn, "F" + value);
				row.put(nameColumn, "file-" + value);
			});
		}
		final String migrationId = "docker-file-" + java.util.UUID.randomUUID();
		final var option = ChunkedBulkMigrationOption.builder().migrationId(migrationId).chunkSize(2)
				.sourceFingerprint("source-v1").targetFingerprint("target-v1")
				.checkpointMode(BulkMigrationCheckpointMode.FILE).build();
		final var store = new FileBulkMigrationCheckpointStore(checkpointDirectory);
		final var result = ChunkedBulkMigrationExecutor.execute(connection, table, option, store);
		assertEquals(3, result.getProcessedRows());
		assertEquals(2, result.getCompletedChunks());
		assertEquals(3, count(connection, countSql));
		assertTrue(store.load(migrationId).orElseThrow().isComplete());
		assertTrue(ChunkedBulkMigrationExecutor.execute(connection, table, option, store).isAlreadyComplete());
		assertPauseAndResumeExactlyOnce(connection, table, codeColumn, nameColumn, countSql,
				store, BulkMigrationCheckpointMode.FILE, BulkMigrationMode.UPSERT, 3);
	}

	public static void assertDatabaseCheckpointPauseAndResume(final Connection connection, final Table table,
			final String codeColumn, final String nameColumn, final String countSql,
			final int existingRows) throws SQLException {
		final var store = new JdbcBulkMigrationCheckpointStore(connection, checkpointTableName());
		assertPauseAndResumeExactlyOnce(connection, table, codeColumn, nameColumn, countSql,
				store, BulkMigrationCheckpointMode.DATABASE, BulkMigrationMode.UPSERT, existingRows);
	}

	private static void assertPauseAndResumeExactlyOnce(final Connection connection, final Table table,
			final String codeColumn, final String nameColumn, final String countSql,
			final BulkMigrationCheckpointStore store,
			final BulkMigrationCheckpointMode checkpointMode, final BulkMigrationMode mode,
			final int existingRows) throws SQLException {
		table.getRows().clear();
		for (int i = 1; i <= 3; i++) {
			final int value = i;
			table.getRows().add(row -> {
				row.put(codeColumn, "R" + value);
				row.put(nameColumn, "resume-" + value);
			});
		}
		final String migrationId = "docker-resume-" + java.util.UUID.randomUUID();
		final var option = ChunkedBulkMigrationOption.builder().migrationId(migrationId).chunkSize(2)
				.sourceFingerprint("source-v1").targetFingerprint("target-v1")
				.checkpointMode(checkpointMode).mode(mode).build();
		final var pauseAfterFirstCommit = new ChunkedBulkMigrationListener() {
			@Override
			public boolean pauseAfterChunk(final com.sqlapp.jdbc.bulk.ChunkedBulkMigrationProgress progress) {
				return progress.getChunkIndex() == 0;
			}
		};
		assertThrows(ChunkedBulkMigrationPausedException.class,
				() -> ChunkedBulkMigrationExecutor.execute(connection, table, option, store,
						pauseAfterFirstCommit));
		assertEquals(existingRows + 2, count(connection, countSql));
		final var paused = store.load(migrationId).orElseThrow();
		assertEquals(2, paused.getProcessedRows());
		assertEquals(1, paused.getCompletedChunks());
		assertFalse(paused.isComplete());

		final var resumed = ChunkedBulkMigrationExecutor.execute(connection, table, option, store);
		assertEquals(2, resumed.getPreviouslyProcessedRows());
		assertEquals(1, resumed.getProcessedRows());
		assertEquals(existingRows + 3, count(connection, countSql));
		assertTrue(store.load(migrationId).orElseThrow().isComplete());
	}

	private static int count(final Connection connection, final String sql) throws SQLException {
		try (var statement = connection.createStatement(); var resultSet = statement.executeQuery(sql)) {
			resultSet.next();
			return resultSet.getInt(1);
		}
	}

	private static String checkpointTableName() {
		return "SQLAPP_BMC_" + java.util.UUID.randomUUID().toString().replace("-", "").substring(0, 8)
				.toUpperCase(java.util.Locale.ROOT);
	}
}
