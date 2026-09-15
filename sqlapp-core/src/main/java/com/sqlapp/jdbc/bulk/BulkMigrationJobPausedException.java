/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.jdbc.bulk;

import java.util.Objects;

import lombok.Getter;

/** Intentional pause of one task in a multi-table migration job. */
@Getter
public class BulkMigrationJobPausedException extends RuntimeException {
	private static final long serialVersionUID = 1L;

	private final String pausedTaskId;
	private final BulkMigrationJobResult completedResult;
	private final ChunkedBulkMigrationProgress progress;

	public BulkMigrationJobPausedException(final BulkMigrationJobPlan plan,
			final String pausedTaskId,
			final BulkMigrationJobResult completedResult,
			final ChunkedBulkMigrationPausedException cause) {
		super("Migration job paused in task: " + pausedTaskId, cause);
		if (pausedTaskId == null || pausedTaskId.isBlank()) {
			throw new IllegalArgumentException("pausedTaskId must not be empty");
		}
		this.pausedTaskId = pausedTaskId;
		final BulkMigrationJobPlan validatedPlan = Objects.requireNonNull(plan, "plan");
		this.completedResult = Objects.requireNonNull(completedResult, "completedResult")
				.validateCompletedPrefixAgainst(validatedPlan, pausedTaskId);
		this.progress = Objects.requireNonNull(cause, "cause").getProgress();
		final int taskIndex = validatedPlan.getTaskIds().indexOf(pausedTaskId);
		final String migrationId = validatedPlan.getTasks().get(taskIndex)
				.getOptions().getMigrationId();
		if (!migrationId.equals(progress.getMigrationId())) {
			throw new IllegalArgumentException(
					"Paused progress migrationId does not match the stopped task");
		}
	}
}
