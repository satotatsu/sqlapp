/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import java.util.HashSet;
import java.util.List;

import com.sqlapp.exceptions.CommandException;

/** Shared semantic validation for per-task repair evidence. */
final class BulkMigrationJobRepairTaskEvidenceValidator {
	record Totals(long mismatchChunks, long replayedChunks, long replayedRows, long affectedRows,
			long tasksRequiringManualReconciliation) {
	}

	private BulkMigrationJobRepairTaskEvidenceValidator() {
	}

	static Totals validate(final List<BulkMigrationJobRepairExecutionReport.Task> tasks, final String artifact) {
		if (tasks == null) {
			throw new CommandException(artifact + " tasks must not be null");
		}
		long mismatchChunks = 0;
		long replayedChunks = 0;
		long replayedRows = 0;
		long affectedRows = 0;
		long manualTasks = 0;
		final var taskIds = new HashSet<String>();
		for (final var task : tasks) {
			if (task == null || blank(task.taskId()) || !taskIds.add(task.taskId()) || task.mismatchChunks() < 0
					|| task.replayedChunks() < 0 || task.replayedChunks() > task.mismatchChunks()
					|| task.replayedRows() < 0 || task.affectedRows() < 0
					|| invalidChunks(task.chunksWithExtraActualRows())
					|| invalidChunks(task.chunksWithoutExpectedRows())
					|| overlaps(task.chunksWithExtraActualRows(), task.chunksWithoutExpectedRows())) {
				throw new CommandException(artifact + " task is invalid");
			}
			try {
				mismatchChunks = Math.addExact(mismatchChunks, task.mismatchChunks());
				replayedChunks = Math.addExact(replayedChunks, task.replayedChunks());
				replayedRows = Math.addExact(replayedRows, task.replayedRows());
				affectedRows = Math.addExact(affectedRows, task.affectedRows());
			} catch (ArithmeticException e) {
				throw new CommandException(artifact + " count overflow", e);
			}
			if (!task.chunksWithExtraActualRows().isEmpty() || !task.chunksWithoutExpectedRows().isEmpty()) {
				manualTasks++;
			}
		}
		return new Totals(mismatchChunks, replayedChunks, replayedRows, affectedRows, manualTasks);
	}

	private static boolean invalidChunks(final List<Long> chunks) {
		return chunks == null || chunks.stream().anyMatch(value -> value == null || value < 0)
				|| new HashSet<>(chunks).size() != chunks.size();
	}

	private static boolean overlaps(final List<Long> first, final List<Long> second) {
		final var values = new HashSet<>(first);
		return second.stream().anyMatch(values::contains);
	}

	private static boolean blank(final String value) {
		return value == null || value.isBlank();
	}
}
