/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;

import com.sqlapp.exceptions.CommandException;
import com.sqlapp.jdbc.bulk.BulkMigrationJobException;
import com.sqlapp.jdbc.bulk.BulkMigrationJobPausedException;
import com.sqlapp.jdbc.bulk.BulkMigrationJobPlan;
import com.sqlapp.jdbc.bulk.BulkMigrationJobResult;

/**
 * Builds failure summaries and persists them through the bounded
 * execution-report format.
 */
public final class BulkMigrationJobFailureReportIO {
	private static final int MAX_MESSAGE_LENGTH = 2_000;

	public record Snapshot(BulkMigrationJobFailureReport report, String fingerprint) {
	}

	public BulkMigrationJobFailureReport fromFailure(final BulkMigrationJobPlan plan, final Throwable failure,
			final BulkMigrationArtifactProvenance provenance) {
		final String status;
		final String stoppedTask;
		final BulkMigrationJobResult completed;
		if (failure instanceof BulkMigrationJobPausedException paused) {
			status = "PAUSED";
			stoppedTask = paused.getPausedTaskId();
			completed = paused.getCompletedResult();
		} else if (failure instanceof BulkMigrationJobException failed) {
			status = "FAILED";
			stoppedTask = failed.getFailedTaskId();
			completed = failed.getCompletedResult();
		} else {
			status = "FAILED";
			stoppedTask = null;
			completed = new BulkMigrationJobResult(plan.getFingerprint(), List.of());
		}
		final var tasks = completed.getTasks().stream().map(task -> {
			final var result = task.getMigrationResult();
			return new BulkMigrationJobExecutionReport.Task(task.getTaskId(), result.getPreviouslyProcessedRows(),
					result.getProcessedRows(), result.getCompletedChunks(), result.isAlreadyComplete());
		}).toList();
		final String message = failure.getMessage() == null ? "" : failure.getMessage();
		return validate(
				new BulkMigrationJobFailureReport(BulkMigrationJobFailureReport.CURRENT_FORMAT_VERSION, Instant.now(),
						status, plan.getJobId(), plan.getFingerprint(), stoppedTask, failure.getClass().getName(),
						message.substring(0, Math.min(message.length(), MAX_MESSAGE_LENGTH)), tasks, provenance));
	}

	public Snapshot writeSnapshot(final Path file, final BulkMigrationJobFailureReport report,
			final Long maxFileSizeBytes) {
		validate(report);
		return FailureWriter.write(file, report, maxFileSizeBytes);
	}

	public BulkMigrationJobFailureReport read(final Path file, final Long maxFileSizeBytes) {
		return FailureWriter.read(file, maxFileSizeBytes);
	}

	private static BulkMigrationJobFailureReport validate(final BulkMigrationJobFailureReport report) {
		if (report == null || report.formatVersion() != 1 || report.failedAt() == null
				|| !("FAILED".equals(report.status()) || "PAUSED".equals(report.status())) || report.jobId() == null
				|| report.jobId().isBlank() || report.planFingerprint() == null || report.planFingerprint().isBlank()
				|| report.failureType() == null || report.failureType().isBlank() || report.failureMessage() == null
				|| report.failureMessage().length() > MAX_MESSAGE_LENGTH) {
			throw new CommandException("Bulk migration failure report header is invalid");
		}
		final var ids = new HashSet<String>();
		for (final var task : report.completedTasks()) {
			if (task == null || task.taskId() == null || task.taskId().isBlank() || !ids.add(task.taskId())
					|| task.previouslyProcessedRows() < 0 || task.processedRows() < 0 || task.completedChunks() < 0) {
				throw new CommandException("Bulk migration failure report completed tasks are invalid");
			}
		}
		return report;
	}

	private static final class FailureWriter {
		static Snapshot write(final Path file, final BulkMigrationJobFailureReport report,
				final Long maxFileSizeBytes) {
			try {
				final var converter = new com.sqlapp.util.JsonConverter();
				converter.setIndentOutput(true);
				com.sqlapp.data.db.command.migration.internal.AtomicMigrationFile.write(file,
						temporary -> converter.writeJsonValue(temporary.toFile(), report));
				final byte[] bytes = com.sqlapp.data.db.command.migration.internal.BoundedMigrationFile.read(file,
						maxFileSizeBytes, "maxExecutionReportFileSizeBytes", "Bulk migration failure report");
				return new Snapshot(report, "sha256:" + com.sqlapp.util.MessageDigests.SHA256.checksumAsString(bytes));
			} catch (java.io.IOException e) {
				throw new CommandException("Failed to write bulk migration failure report: " + file, e);
			}
		}

		static BulkMigrationJobFailureReport read(final Path file, final Long maxFileSizeBytes) {
			try {
				final byte[] bytes = com.sqlapp.data.db.command.migration.internal.BoundedMigrationFile.read(file,
						maxFileSizeBytes, "maxExecutionReportFileSizeBytes", "Bulk migration failure report");
				return validate(new com.sqlapp.util.JsonConverter().fromJsonString(
						new String(bytes, java.nio.charset.StandardCharsets.UTF_8),
						BulkMigrationJobFailureReport.class));
			} catch (java.io.IOException e) {
				throw new CommandException("Failed to read bulk migration failure report: " + file, e);
			}
		}
	}
}
