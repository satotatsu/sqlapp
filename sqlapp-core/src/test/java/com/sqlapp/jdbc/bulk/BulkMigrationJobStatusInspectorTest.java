/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

class BulkMigrationJobStatusInspectorTest {
	@Test
	void reportsNotStartedProgressCompleteAndIncompatible() throws Exception {
		final var notStartedStore = new InMemoryBulkMigrationCheckpointStore();
		final var progressStore = new InMemoryBulkMigrationCheckpointStore();
		final var completeStore = new InMemoryBulkMigrationCheckpointStore();
		final var incompatibleStore = new InMemoryBulkMigrationCheckpointStore();
		progressStore.save(checkpoint("progress", "source", false, 12));
		completeStore.save(checkpoint("complete", "source", true, 20));
		incompatibleStore.save(checkpoint("incompatible", "old-source", false, 4));
		final var plan = BulkMigrationJobPlanner.plan(List.of(
				task("not-started", notStartedStore), task("progress", progressStore),
				task("complete", completeStore), task("incompatible", incompatibleStore)));

		final var status = BulkMigrationJobStatusInspector.inspect(plan);

		assertEquals(List.of(BulkMigrationJobTaskState.COMPLETE,
				BulkMigrationJobTaskState.INCOMPATIBLE, BulkMigrationJobTaskState.NOT_STARTED,
				BulkMigrationJobTaskState.IN_PROGRESS), status.getTasks().stream()
						.map(BulkMigrationJobTaskStatus::getState).toList());
		assertEquals(36, status.getProcessedRows());
		assertEquals(1, status.getCompletedTasks());
		assertFalse(status.isCompatible());
		assertEquals(plan.getFingerprint(), status.getPlanFingerprint());
	}

	@Test
	void refusesImplicitCheckpointStoreToRemainReadOnly() {
		final Table table = table("IMPLICIT");
		final var task = BulkMigrationJobTask.builder().taskId("implicit")
				.sourceTable(table).options(options("implicit")).build();

		assertThrows(IllegalArgumentException.class,
				() -> BulkMigrationJobStatusInspector.inspect(
						BulkMigrationJobPlanner.plan(List.of(task))));
	}

	@Test
	void rejectsInvalidCheckpointsReturnedByCustomStores() {
		final BulkMigrationCheckpoint invalid = BulkMigrationCheckpoint.builder()
				.migrationId("invalid").processedRows(-1).build();
		final BulkMigrationCheckpointStore store = new BulkMigrationCheckpointStore() {
			@Override
			public Optional<BulkMigrationCheckpoint> load(String migrationId) {
				return Optional.of(invalid);
			}

			@Override
			public void save(BulkMigrationCheckpoint checkpoint) {
			}

			@Override
			public void delete(String migrationId) {
			}
		};
		final var plan = BulkMigrationJobPlanner.plan(List.of(task("invalid", store)));

		assertThrows(IllegalArgumentException.class,
				() -> BulkMigrationJobStatusInspector.inspect(plan));
	}

	private static BulkMigrationJobTask task(final String id,
			final BulkMigrationCheckpointStore store) {
		return BulkMigrationJobTask.builder().taskId(id).sourceTable(table(id))
				.options(options(id)).checkpointStore(store).build();
	}

	private static ChunkedBulkMigrationOption options(final String id) {
		return ChunkedBulkMigrationOption.builder().migrationId(id)
				.sourceFingerprint("source").targetFingerprint("target").build();
	}

	private static BulkMigrationCheckpoint checkpoint(final String id,
			final String sourceFingerprint, final boolean complete, final long rows) {
		return BulkMigrationCheckpoint.builder().migrationId(id)
				.sourceFingerprint(sourceFingerprint).targetFingerprint("target")
				.processedRows(rows).completedChunks(1).complete(complete).build();
	}

	private static Table table(final String name) {
		final Table table = new Table(name);
		table.getColumns().add(new Column("ID"));
		table.setPrimaryKey("PK_" + name, table.getColumns().get("ID"));
		return table;
	}
}
