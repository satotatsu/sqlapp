/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.UUID;

import com.sqlapp.data.db.command.migration.FileBulkMigrationCheckpointStore;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.BulkMigrationCheckpointMode;
import com.sqlapp.jdbc.bulk.BulkMigrationCheckpointStore;
import com.sqlapp.jdbc.bulk.BulkUpsertDuplicateKeyStrategy;
import com.sqlapp.jdbc.bulk.BulkUpsertOption;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationExecutor;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationOption;

/** Shared real-database assertions for sustained chunk and LOB loads. */
public final class BulkMigrationLoadAssertions {
	private static final int ROW_COUNT = 5_000;
	private static final int CHUNK_SIZE = 257;
	private static final int DUPLICATE_KEYS = 500;
	private static final int DUPLICATE_ROUNDS = 4;

	private BulkMigrationLoadAssertions() {
	}

	public static void assertChunkedLobAndDuplicateLoad(final Connection connection, final Table table,
			final String codeColumn, final String textColumn, final String binaryColumn,
			final String countSql, final String sampleSql) throws SQLException {
		assertChunkedLobAndDuplicateLoad(connection, table, codeColumn, textColumn, binaryColumn,
				countSql, sampleSql, BulkMigrationCheckpointMode.DATABASE, null);
	}

	public static void assertChunkedLobAndDuplicateLoad(final Connection connection, final Table table,
			final String codeColumn, final String textColumn, final String binaryColumn,
			final String countSql, final String sampleSql, final Path checkpointDirectory) throws SQLException {
		assertChunkedLobAndDuplicateLoad(connection, table, codeColumn, textColumn, binaryColumn,
				countSql, sampleSql, BulkMigrationCheckpointMode.FILE,
				new FileBulkMigrationCheckpointStore(checkpointDirectory));
	}

	public static Table table(final String schemaName, final String tableName, final String codeColumn,
			final String textColumn, final String binaryColumn) {
		return table(schemaName, tableName, codeColumn, textColumn, binaryColumn, DataType.CLOB, DataType.BLOB);
	}

	public static Table table(final String schemaName, final String tableName, final String codeColumn,
			final String textColumn, final String binaryColumn, final DataType textType, final DataType binaryType) {
		final Table table = new Table(tableName).setSchemaName(schemaName);
		table.getColumns().add(new Column(codeColumn).setDataType(DataType.VARCHAR).setLength(20).setNotNull(true));
		table.getColumns().add(new Column(textColumn).setDataType(textType).setLength(2_000));
		table.getColumns().add(new Column(binaryColumn).setDataType(binaryType).setLength(256));
		table.getConstraints().addPrimaryKeyConstraint("PK_" + tableName, table.getColumns().get(codeColumn));
		return table;
	}

	private static void assertChunkedLobAndDuplicateLoad(final Connection connection, final Table table,
			final String codeColumn, final String textColumn, final String binaryColumn,
			final String countSql, final String sampleSql, final BulkMigrationCheckpointMode checkpointMode,
			final BulkMigrationCheckpointStore checkpointStore) throws SQLException {
		loadInitialRows(table, codeColumn, textColumn, binaryColumn);
		final long initialStarted = System.nanoTime();
		final var initial = execute(connection, table, options("load-initial-" + UUID.randomUUID(), checkpointMode),
				checkpointStore);
		final long initialMillis = elapsedMillis(initialStarted);
		assertEquals(ROW_COUNT, initial.getProcessedRows());
		assertEquals((ROW_COUNT + CHUNK_SIZE - 1) / CHUNK_SIZE, initial.getCompletedChunks());
		assertFalse(initial.isAlreadyComplete());
		assertEquals(ROW_COUNT, scalar(connection, countSql));
		assertSample(connection, sampleSql, code(ROW_COUNT - 1), initialText(ROW_COUNT - 1),
				binary(ROW_COUNT - 1, 0));

		loadDuplicateRows(table, codeColumn, textColumn, binaryColumn);
		final long duplicateStarted = System.nanoTime();
		final var duplicate = execute(connection, table,
				options("load-duplicates-" + UUID.randomUUID(), checkpointMode), checkpointStore);
		final long duplicateMillis = elapsedMillis(duplicateStarted);
		assertEquals((long) DUPLICATE_KEYS * DUPLICATE_ROUNDS, duplicate.getProcessedRows());
		assertEquals(ROW_COUNT, scalar(connection, countSql));
		assertSample(connection, sampleSql, code(0), duplicateText(0, DUPLICATE_ROUNDS - 1),
				binary(0, DUPLICATE_ROUNDS - 1));
		System.out.printf("bulk-load rows=%d chunks=%d initialMs=%d duplicateRows=%d duplicateMs=%d%n",
				ROW_COUNT, initial.getCompletedChunks(), initialMillis,
				DUPLICATE_KEYS * DUPLICATE_ROUNDS, duplicateMillis);
		table.getRows().clear();
	}

	private static com.sqlapp.jdbc.bulk.ChunkedBulkMigrationResult execute(final Connection connection,
			final Table table, final ChunkedBulkMigrationOption options,
			final BulkMigrationCheckpointStore checkpointStore) throws SQLException {
		return checkpointStore == null ? ChunkedBulkMigrationExecutor.execute(connection, table, options)
				: ChunkedBulkMigrationExecutor.execute(connection, table, options, checkpointStore);
	}

	private static ChunkedBulkMigrationOption options(final String migrationId,
			final BulkMigrationCheckpointMode checkpointMode) {
		return ChunkedBulkMigrationOption.builder().migrationId(migrationId).chunkSize(CHUNK_SIZE)
				.checkpointMode(checkpointMode)
				.sourceFingerprint("load-source-v1").targetFingerprint("load-target-v1")
				.bulkUpsertOption(BulkUpsertOption.builder()
						.duplicateKeyStrategy(BulkUpsertDuplicateKeyStrategy.KEEP_LAST).build())
				.build();
	}

	private static void loadInitialRows(final Table table, final String codeColumn,
			final String textColumn, final String binaryColumn) {
		table.getRows().clear();
		for (int i = 0; i < ROW_COUNT; i++) {
			final int value = i;
			table.getRows().add(row -> {
				row.put(codeColumn, code(value));
				row.put(textColumn, initialText(value));
				row.put(binaryColumn, binary(value, 0));
			});
		}
	}

	private static void loadDuplicateRows(final Table table, final String codeColumn,
			final String textColumn, final String binaryColumn) {
		table.getRows().clear();
		for (int round = 0; round < DUPLICATE_ROUNDS; round++) {
			for (int i = 0; i < DUPLICATE_KEYS; i++) {
				final int value = i;
				final int version = round;
				table.getRows().add(row -> {
					row.put(codeColumn, code(value));
					row.put(textColumn, duplicateText(value, version));
					row.put(binaryColumn, binary(value, version));
				});
			}
		}
	}

	private static String code(final int value) {
		return "L" + String.format(java.util.Locale.ROOT, "%05d", value);
	}

	private static String initialText(final int value) {
		return "初期-" + value + "\n" + "x".repeat(600);
	}

	private static String duplicateText(final int value, final int version) {
		return "更新-" + value + "-" + version + "\n" + "y".repeat(600);
	}

	private static byte[] binary(final int value, final int version) {
		final byte[] seed = (value + ":" + version).getBytes(StandardCharsets.UTF_8);
		final byte[] result = new byte[128];
		for (int i = 0; i < result.length; i++) {
			result[i] = seed[i % seed.length];
		}
		return result;
	}

	private static int scalar(final Connection connection, final String sql) throws SQLException {
		try (var statement = connection.createStatement(); var resultSet = statement.executeQuery(sql)) {
			resultSet.next();
			return resultSet.getInt(1);
		}
	}

	private static void assertSample(final Connection connection, final String sql, final String code,
			final String expectedText, final byte[] expectedBinary) throws SQLException {
		try (var statement = connection.prepareStatement(sql)) {
			statement.setString(1, code);
			try (var resultSet = statement.executeQuery()) {
				resultSet.next();
				assertEquals(expectedText, resultSet.getString(1));
				assertArrayEquals(expectedBinary, resultSet.getBytes(2));
			}
		}
	}

	private static long elapsedMillis(final long started) {
		return java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
	}
}
