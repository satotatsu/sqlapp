/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

class BulkMigrationJobCheckpointManagerTest {
	@Test
	void resetsOnlyAfterExplicitFingerprintConfirmation() throws Exception {
		final var firstStore = store("first");
		final var secondStore = store("second");
		final var plan = BulkMigrationJobPlanner.plan(List.of(
				task("first", firstStore), task("second", secondStore)));

		assertThrows(IllegalArgumentException.class,
				() -> BulkMigrationJobCheckpointManager.reset(plan, "wrong"));
		assertTrue(firstStore.load("first").isPresent());
		assertTrue(secondStore.load("second").isPresent());

		final var result = BulkMigrationJobCheckpointManager.reset(plan, plan.getFingerprint());

		assertEquals(plan.getFingerprint(), result.getPlanFingerprint());
		assertEquals(List.of("first", "second"), result.getResetTaskIds());
		assertTrue(firstStore.load("first").isEmpty());
		assertTrue(secondStore.load("second").isEmpty());
	}

	@Test
	void reportsPartialResetWhenAStoreFails() throws Exception {
		final var firstStore = store("first");
		final var failingStore = new FailingDeleteStore(store("second"));
		final var plan = BulkMigrationJobPlanner.plan(List.of(
				task("first", firstStore), task("second", failingStore)));

		final var failure = assertThrows(BulkMigrationJobCheckpointResetException.class,
				() -> BulkMigrationJobCheckpointManager.reset(plan, plan.getFingerprint()));

		assertEquals("second", failure.getFailedTaskId());
		assertEquals(List.of("first"), failure.getCompletedResult().getResetTaskIds());
		assertTrue(firstStore.load("first").isEmpty());
		assertTrue(failingStore.load("second").isPresent());
	}

	private static InMemoryBulkMigrationCheckpointStore store(final String id) throws SQLException {
		final var store = new InMemoryBulkMigrationCheckpointStore();
		store.save(BulkMigrationCheckpoint.builder().migrationId(id).build());
		return store;
	}

	private static BulkMigrationJobTask task(final String id,
			final BulkMigrationCheckpointStore store) {
		final Table table = new Table(id);
		table.getColumns().add(new Column("ID"));
		table.setPrimaryKey("PK_" + id, table.getColumns().get("ID"));
		return BulkMigrationJobTask.builder().taskId(id).sourceTable(table)
				.options(ChunkedBulkMigrationOption.builder().migrationId(id)
						.sourceFingerprint("source-v1").targetFingerprint("target-v1").build())
				.checkpointStore(store).build();
	}

	private static final class FailingDeleteStore implements BulkMigrationCheckpointStore {
		private final BulkMigrationCheckpointStore delegate;

		private FailingDeleteStore(final BulkMigrationCheckpointStore delegate) {
			this.delegate = delegate;
		}

		@Override
		public Optional<BulkMigrationCheckpoint> load(String migrationId) throws SQLException {
			return delegate.load(migrationId);
		}

		@Override
		public void save(BulkMigrationCheckpoint checkpoint) throws SQLException {
			delegate.save(checkpoint);
		}

		@Override
		public void delete(String migrationId) throws SQLException {
			throw new SQLException("expected delete failure");
		}
	}
}
