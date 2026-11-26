/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.sqlapp.AbstractDbTest;
import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

class ChunkedBulkMigrationCommitGuardTest extends AbstractDbTest {
	@Test
	void rollsBackDataAndCheckpointWhenLeaseIsLostBeforeChunkCommit() throws Exception {
		assertLostLeaseRollsBack(false);
	}

	@Test
	void rollsBackCompletionCheckpointWhenLeaseIsLostBeforeFinalCommit() throws Exception {
		assertLostLeaseRollsBack(true);
	}

	private void assertLostLeaseRollsBack(final boolean loseOnCompletion) throws Exception {
		testDb(connection -> {
			connection.setAutoCommit(true);
			execute(connection, "CREATE TABLE GUARDED_ROWS (ID INTEGER)");
			final var table = new Table("GUARDED_ROWS");
			table.getColumns().add(new Column("ID").setDataType(DataType.INT));
			final var row = table.newRow();
			row.put("ID", 1);
			table.getRows().add(row);
			final var leases = new InMemoryBulkMigrationJobLeaseStore();
			final var manager = new BulkMigrationJobLeaseManager(leases, "owner", Duration.ofMinutes(1));
			try (var lease = manager.acquire("guarded-job", "plan");
					var heartbeat = lease.startHeartbeat()) {
				final var store = new JdbcBulkMigrationCheckpointStore(connection, "GUARDED_CHECKPOINT") {
					@Override public void save(BulkMigrationCheckpoint checkpoint) throws SQLException {
						super.save(checkpoint);
						if (checkpoint.isComplete() == loseOnCompletion) leases.release(lease.getLease());
					}
				};
				final var listener = CompositeChunkedBulkMigrationListener.of(
						new BulkMigrationJobLeaseChunkListener(lease, heartbeat), ChunkedBulkMigrationListener.NO_OP);
				final var options = ChunkedBulkMigrationOption.builder().migrationId("guarded")
						.mode(BulkMigrationMode.INSERT).sourceFingerprint("source").targetFingerprint("target").build();
				assertThrows(BulkMigrationJobLeaseUnavailableException.class,
						() -> ChunkedBulkMigrationExecutor.execute(connection, table, options, store, listener));
				assertTrue(connection.getAutoCommit());
				try (var statement = connection.createStatement();
						var result = statement.executeQuery("SELECT COUNT(*) FROM GUARDED_ROWS")) {
					assertTrue(result.next());
					assertEquals(loseOnCompletion ? 1 : 0, result.getInt(1));
				}
				final var checkpoint = store.load("guarded");
				assertEquals(loseOnCompletion, checkpoint.isPresent());
				checkpoint.ifPresent(saved -> assertFalse(saved.isComplete()));
			}
		});
	}
}
