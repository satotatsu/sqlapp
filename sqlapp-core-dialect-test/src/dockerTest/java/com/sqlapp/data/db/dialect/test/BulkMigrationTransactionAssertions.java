/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.SQLException;

import com.sqlapp.data.schemas.Table;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationExecutor;
import com.sqlapp.jdbc.bulk.ChunkedBulkMigrationOption;
import com.sqlapp.jdbc.bulk.JdbcBulkMigrationCheckpointStore;

/** Shared real-database assertions for atomic data/checkpoint commits. */
public final class BulkMigrationTransactionAssertions {
	private BulkMigrationTransactionAssertions() {
	}

	public static void assertDatabaseCheckpointAtomic(final Connection connection,
			final Table table, final String codeColumn, final String nameColumn,
			final String countSql) throws SQLException {
		for (int i = 1; i <= 3; i++) {
			final int value = i;
			table.getRows().add(row -> {
				row.put(codeColumn, "C" + value);
				row.put(nameColumn, "name-" + value);
			});
		}
		final String migrationId = "docker-" + java.util.UUID.randomUUID();
		final var option = ChunkedBulkMigrationOption.builder()
				.migrationId(migrationId).chunkSize(2).build();
		final var checkpointStore = new JdbcBulkMigrationCheckpointStore(connection,
				option.getCheckpointTableName());
		assertThrows(SQLException.class,
				() -> ChunkedBulkMigrationExecutor.execute(connection, table, option,
						new FailingTransactionalCheckpointStore(connection, checkpointStore)));
		assertEquals(0, count(connection, countSql));
		assertTrue(checkpointStore.load(migrationId).isEmpty());

		final var result = ChunkedBulkMigrationExecutor.execute(connection, table, option);
		assertEquals(3, result.getProcessedRows());
		assertEquals(2, result.getCompletedChunks());
		assertEquals(3, count(connection, countSql));
		assertEquals(3, checkpointStore.load(migrationId).orElseThrow().getProcessedRows());
	}

	public static void assertDatabaseCheckpointRejected(final Connection connection,
			final Table table, final String codeColumn, final String nameColumn,
			final String countSql) throws SQLException {
		table.getRows().add(row -> {
			row.put(codeColumn, "C1");
			row.put(nameColumn, "name-1");
		});
		final var option = ChunkedBulkMigrationOption.builder()
				.migrationId("docker-rejected-" + java.util.UUID.randomUUID()).build();
		assertThrows(IllegalStateException.class,
				() -> ChunkedBulkMigrationExecutor.execute(connection, table, option));
		assertEquals(0, count(connection, countSql));
	}

	private static int count(final Connection connection, final String sql) throws SQLException {
		try (var statement = connection.createStatement();
				var resultSet = statement.executeQuery(sql)) {
			resultSet.next();
			return resultSet.getInt(1);
		}
	}
}
