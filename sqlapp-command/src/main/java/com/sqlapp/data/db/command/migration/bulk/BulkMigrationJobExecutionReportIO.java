/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashSet;
import java.util.Objects;

import com.sqlapp.data.db.command.migration.internal.AtomicMigrationFile;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.jdbc.bulk.BulkMigrationJobPlan;
import com.sqlapp.jdbc.bulk.BulkMigrationJobResult;
import com.sqlapp.util.JsonConverter;

/** Creates and atomically persists committed migration execution summaries. */
public final class BulkMigrationJobExecutionReportIO {
	public record Snapshot(BulkMigrationJobExecutionReport report, String fingerprint) {
	}

	public BulkMigrationJobExecutionReport fromResult(final BulkMigrationJobPlan plan,
			final BulkMigrationJobResult result, final BulkMigrationArtifactProvenance provenance) {
		Objects.requireNonNull(plan, "plan").validateUnchanged();
		Objects.requireNonNull(result, "result").validateAgainst(plan);
		final var tasks = result.getTasks().stream().map(task -> {
			final var migration = task.getMigrationResult();
			return new BulkMigrationJobExecutionReport.Task(task.getTaskId(), migration.getPreviouslyProcessedRows(),
					migration.getProcessedRows(), migration.getCompletedChunks(), migration.isAlreadyComplete());
		}).toList();
		return validate(new BulkMigrationJobExecutionReport(BulkMigrationJobExecutionReport.CURRENT_FORMAT_VERSION,
				Instant.now(), plan.getJobId(), plan.getFingerprint(), result.getProcessedRows(),
				result.getAlreadyCompleteTasks(), tasks, provenance));
	}

	public Snapshot writeSnapshot(final Path file, final BulkMigrationJobExecutionReport report,
			final Long maxFileSizeBytes) {
		final Path absolute = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
		final var validated = validate(report);
		try {
			final JsonConverter converter = new JsonConverter();
			converter.setIndentOutput(true);
			AtomicMigrationFile.write(absolute, temporary -> converter.writeJsonValue(temporary.toFile(), validated));
		} catch (IOException | RuntimeException e) {
			throw new CommandException("Failed to write bulk migration execution report: " + absolute, e);
		}
		final Snapshot snapshot = readSnapshot(absolute, maxFileSizeBytes);
		if (!validated.equals(snapshot.report())) {
			throw new CommandException("Written bulk migration execution report does not match the requested report");
		}
		return snapshot;
	}

	public BulkMigrationJobExecutionReport read(final Path file) {
		return readSnapshot(file, null).report();
	}

	public BulkMigrationJobExecutionReport read(final Path file, final Long maxFileSizeBytes) {
		return readSnapshot(file, maxFileSizeBytes).report();
	}

	public Snapshot readSnapshot(final Path file, final Long maxFileSizeBytes) {
		final Path absolute = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
		if (!Files.isRegularFile(absolute)) {
			throw new CommandException("Bulk migration execution report does not exist: " + absolute);
		}
		try {
			final byte[] bytes = BoundedMigrationFile.read(absolute, maxFileSizeBytes,
					"maxExecutionReportFileSizeBytes", "Bulk migration execution report");
			final var report = validate(new JsonConverter().fromJsonString(
					new String(bytes, StandardCharsets.UTF_8), BulkMigrationJobExecutionReport.class));
			return new Snapshot(report, "sha256:" + com.sqlapp.util.MessageDigests.SHA256.checksumAsString(bytes));
		} catch (IOException e) {
			throw new CommandException("Failed to read bulk migration execution report: " + absolute, e);
		} catch (RuntimeException e) {
			if (e instanceof CommandException commandException) {
				throw commandException;
			}
			throw new CommandException("Failed to read bulk migration execution report: " + absolute, e);
		}
	}

	static BulkMigrationJobExecutionReport validate(final BulkMigrationJobExecutionReport report) {
		if (report == null || report.formatVersion() != BulkMigrationJobExecutionReport.CURRENT_FORMAT_VERSION
				|| report.completedAt() == null || report.jobId() == null || report.jobId().isBlank()
				|| report.planFingerprint() == null || report.planFingerprint().isBlank()
				|| report.processedRows() < 0 || report.alreadyCompleteTasks() < 0 || report.tasks() == null) {
			throw new CommandException("Bulk migration execution report header is invalid");
		}
		long processedRows = 0;
		long alreadyComplete = 0;
		final var ids = new HashSet<String>();
		for (final var task : report.tasks()) {
			if (task == null || task.taskId() == null || task.taskId().isBlank() || !ids.add(task.taskId())
					|| task.previouslyProcessedRows() < 0 || task.processedRows() < 0 || task.completedChunks() < 0) {
				throw new CommandException("Bulk migration execution report contains an invalid task");
			}
			try {
				processedRows = Math.addExact(processedRows, task.processedRows());
			} catch (ArithmeticException e) {
				throw new CommandException("Bulk migration execution report row count overflow", e);
			}
			if (task.alreadyComplete()) {
				alreadyComplete++;
			}
		}
		if (processedRows != report.processedRows() || alreadyComplete != report.alreadyCompleteTasks()) {
			throw new CommandException("Bulk migration execution report aggregate values are inconsistent");
		}
		return report;
	}
}
