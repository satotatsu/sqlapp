/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;

import com.sqlapp.data.db.command.migration.internal.AtomicMigrationFile;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.jdbc.bulk.BulkMigrationJobRepairResult;
import com.sqlapp.util.JsonConverter;

/** Atomically reads and writes migration job repair execution evidence. */
public final class BulkMigrationJobRepairExecutionReportIO {
	public BulkMigrationJobRepairExecutionReport fromResult(final String migrationPlanFingerprint,
			final String approvedRepairPlanFileFingerprint, final BulkMigrationJobRepairResult result,
			final BulkMigrationArtifactProvenance provenance) {
		Objects.requireNonNull(result, "result");
		final var tasks = result.getTasks().stream().map(task -> {
			final var repair = task.getRepairResult();
			return new BulkMigrationJobRepairExecutionReport.Task(task.getTaskId(), repair.getMismatchChunks(),
					repair.getReplayedChunks(), repair.getReplayedRows(), repair.getAffectedRows(),
					repair.getChunksWithExtraActualRows(), repair.getChunksWithoutExpectedRows());
		}).toList();
		return validate(new BulkMigrationJobRepairExecutionReport(
				BulkMigrationJobRepairExecutionReport.CURRENT_FORMAT_VERSION, Instant.now(), migrationPlanFingerprint,
				result.getPlanFingerprint(), approvedRepairPlanFileFingerprint, result.getMismatchChunks(),
				result.getReplayedChunks(), result.getReplayedRows(), result.getAffectedRows(),
				result.getTasksRequiringManualReconciliation(), tasks, provenance));
	}

	public void write(final Path file, final String migrationPlanFingerprint,
			final String approvedRepairPlanFileFingerprint, final BulkMigrationJobRepairResult result,
			final BulkMigrationArtifactProvenance provenance) {
		write(file, fromResult(migrationPlanFingerprint, approvedRepairPlanFileFingerprint, result, provenance));
	}

	public void write(final Path file, final BulkMigrationJobRepairExecutionReport report) {
		validate(report);
		final Path absolute = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
		try {
			final JsonConverter converter = new JsonConverter();
			converter.setIndentOutput(true);
			AtomicMigrationFile.write(absolute, temporary -> converter.writeJsonValue(temporary.toFile(), report));
		} catch (IOException | RuntimeException e) {
			throw new CommandException("Failed to write bulk migration job repair execution report: " + absolute, e);
		}
	}

	public BulkMigrationJobRepairExecutionReport read(final Path file) {
		final Path absolute = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
		if (!Files.isRegularFile(absolute)) {
			throw new CommandException("Bulk migration job repair execution report does not exist: " + absolute);
		}
		try {
			return validate(new JsonConverter().fromJsonString(absolute.toFile(),
					BulkMigrationJobRepairExecutionReport.class));
		} catch (RuntimeException e) {
			if (e instanceof CommandException commandException) {
				throw commandException;
			}
			throw new CommandException("Failed to read bulk migration job repair execution report: " + absolute, e);
		}
	}

	static BulkMigrationJobRepairExecutionReport validate(final BulkMigrationJobRepairExecutionReport report) {
		if (report == null || report.formatVersion() != BulkMigrationJobRepairExecutionReport.CURRENT_FORMAT_VERSION
				|| report.completedAt() == null || blank(report.migrationPlanFingerprint())
				|| blank(report.repairPlanFingerprint()) || !sha256(report.approvedRepairPlanFileFingerprint())
				|| report.mismatchChunks() < 0 || report.replayedChunks() < 0 || report.replayedRows() < 0
				|| report.affectedRows() < 0 || report.tasksRequiringManualReconciliation() < 0
				|| report.tasks() == null) {
			throw new CommandException("Bulk migration job repair execution report header is invalid");
		}
		long mismatchChunks = 0;
		long replayedChunks = 0;
		long replayedRows = 0;
		long affectedRows = 0;
		long manualTasks = 0;
		final var taskIds = new HashSet<String>();
		for (final var task : report.tasks()) {
			if (task == null || blank(task.taskId()) || !taskIds.add(task.taskId()) || task.mismatchChunks() < 0
					|| task.replayedChunks() < 0 || task.replayedChunks() > task.mismatchChunks()
					|| task.replayedRows() < 0 || task.affectedRows() < 0
					|| invalidChunks(task.chunksWithExtraActualRows())
					|| invalidChunks(task.chunksWithoutExpectedRows())) {
				throw new CommandException("Bulk migration job repair execution task is invalid");
			}
			try {
				mismatchChunks = Math.addExact(mismatchChunks, task.mismatchChunks());
				replayedChunks = Math.addExact(replayedChunks, task.replayedChunks());
				replayedRows = Math.addExact(replayedRows, task.replayedRows());
				affectedRows = Math.addExact(affectedRows, task.affectedRows());
			} catch (ArithmeticException e) {
				throw new CommandException("Bulk migration job repair execution count overflow", e);
			}
			if (!task.chunksWithExtraActualRows().isEmpty() || !task.chunksWithoutExpectedRows().isEmpty()) {
				manualTasks++;
			}
		}
		if (mismatchChunks != report.mismatchChunks() || replayedChunks != report.replayedChunks()
				|| replayedRows != report.replayedRows() || affectedRows != report.affectedRows()
				|| manualTasks != report.tasksRequiringManualReconciliation()) {
			throw new CommandException("Bulk migration job repair execution report summary is inconsistent");
		}
		return report;
	}

	private static boolean invalidChunks(final java.util.List<Long> chunks) {
		return chunks == null || chunks.stream().anyMatch(value -> value == null || value < 0)
				|| new HashSet<>(chunks).size() != chunks.size();
	}

	private static boolean blank(final String value) {
		return value == null || value.isBlank();
	}

	private static boolean sha256(final String value) {
		return value != null && value.matches("sha256:[0-9a-f]{64}");
	}
}
