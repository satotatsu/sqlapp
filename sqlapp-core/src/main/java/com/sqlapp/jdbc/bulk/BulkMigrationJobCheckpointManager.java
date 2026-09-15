/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Explicitly resets task checkpoints without deleting migrated table data. */
public final class BulkMigrationJobCheckpointManager {
	private BulkMigrationJobCheckpointManager() {
	}

	public static BulkMigrationJobCheckpointResetResult reset(final BulkMigrationJobPlan plan,
			final String expectedPlanFingerprint) throws SQLException {
		Objects.requireNonNull(plan, "plan");
		plan.validateUnchanged();
		if (!Objects.equals(plan.getFingerprint(), expectedPlanFingerprint)) {
			throw new IllegalArgumentException("Expected plan fingerprint does not match the migration job plan");
		}
		for (final BulkMigrationJobTask task : plan.getTasks()) {
			if (task.getCheckpointStore() == null) {
				throw new IllegalArgumentException("Checkpoint reset requires an explicit checkpoint store: "
						+ task.getTaskId());
			}
		}
		final List<String> resetTaskIds = new ArrayList<>(plan.getTasks().size());
		for (final BulkMigrationJobTask task : plan.getTasks()) {
			try {
				task.getCheckpointStore().delete(task.getOptions().getMigrationId());
				resetTaskIds.add(task.getTaskId());
			} catch (SQLException e) {
				final var completed = new BulkMigrationJobCheckpointResetResult(
						plan.getFingerprint(), List.copyOf(resetTaskIds)).validateAgainst(plan);
				throw new BulkMigrationJobCheckpointResetException(task.getTaskId(),
						completed, e);
			}
		}
		return new BulkMigrationJobCheckpointResetResult(plan.getFingerprint(),
				List.copyOf(resetTaskIds)).validateAgainst(plan);
	}
}
