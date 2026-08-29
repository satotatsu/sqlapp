/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Reads a plan's explicitly configured checkpoint stores without mutating them. */
public final class BulkMigrationJobStatusInspector {
	private BulkMigrationJobStatusInspector() {
	}

	public static BulkMigrationJobStatus inspect(final BulkMigrationJobPlan plan)
			throws SQLException {
		Objects.requireNonNull(plan, "plan");
		plan.validateUnchanged();
		final List<BulkMigrationJobTaskStatus> statuses = new ArrayList<>(plan.getTasks().size());
		for (final BulkMigrationJobTask task : plan.getTasks()) {
			if (task.getCheckpointStore() == null) {
				throw new IllegalArgumentException("Read-only status inspection requires an explicit "
						+ "checkpoint store: " + task.getTaskId());
			}
			final BulkMigrationCheckpoint checkpoint = task.getCheckpointStore()
					.load(task.getOptions().getMigrationId()).orElse(null);
			if (checkpoint != null) {
				checkpoint.validate();
			}
			final BulkMigrationJobTaskState state;
			if (checkpoint == null) {
				state = BulkMigrationJobTaskState.NOT_STARTED;
			} else if (!compatible(task, checkpoint)) {
				state = BulkMigrationJobTaskState.INCOMPATIBLE;
			} else if (checkpoint.isComplete()) {
				state = BulkMigrationJobTaskState.COMPLETE;
			} else {
				state = BulkMigrationJobTaskState.IN_PROGRESS;
			}
			statuses.add(new BulkMigrationJobTaskStatus(task.getTaskId(), state, checkpoint));
		}
		return new BulkMigrationJobStatus(plan.getFingerprint(), List.copyOf(statuses));
	}

	private static boolean compatible(final BulkMigrationJobTask task,
			final BulkMigrationCheckpoint checkpoint) {
		final ChunkedBulkMigrationOption option = task.getOptions();
		if (!Objects.equals(checkpoint.getSourceFingerprint(), option.getSourceFingerprint())
				|| !Objects.equals(checkpoint.getTargetFingerprint(), option.getTargetFingerprint())) {
			return false;
		}
		if (checkpoint.getProcessedRows() == 0) {
			return true;
		}
		return (task.getKeysetSource() != null) == (checkpoint.getResumeToken() != null);
	}
}
