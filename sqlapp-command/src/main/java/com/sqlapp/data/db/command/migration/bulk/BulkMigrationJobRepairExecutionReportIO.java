/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
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

	Snapshot writeSnapshot(final Path file, final BulkMigrationJobRepairExecutionReport report) {
		write(file, report);
		final var snapshot = readSnapshot(file, null);
		if (!report.equals(snapshot.report())) {
			throw new CommandException("Bulk migration job repair execution report changed after write.");
		}
		return snapshot;
	}

	public BulkMigrationJobRepairExecutionReport read(final Path file) {
		return read(file, null);
	}

	public BulkMigrationJobRepairExecutionReport read(final Path file, final Long maxFileSizeBytes) {
		return readSnapshot(file, maxFileSizeBytes).report();
	}

	Snapshot readSnapshot(final Path file, final Long maxFileSizeBytes) {
		final Path absolute = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
		if (!Files.isRegularFile(absolute)) {
			throw new CommandException("Bulk migration job repair execution report does not exist: " + absolute);
		}
		try {
			final byte[] bytes = BoundedMigrationJsonFile.read(absolute, maxFileSizeBytes,
					"maxEvidenceFileSizeBytes", "Bulk migration job repair execution report");
			final var report = validate(new JsonConverter().fromJsonString(new String(bytes, StandardCharsets.UTF_8),
					BulkMigrationJobRepairExecutionReport.class));
			return new Snapshot(report, fingerprint(bytes));
		} catch (IOException | RuntimeException e) {
			if (e instanceof CommandException commandException) {
				throw commandException;
			}
			throw new CommandException("Failed to read bulk migration job repair execution report: " + absolute, e);
		}
	}

	record Snapshot(BulkMigrationJobRepairExecutionReport report, String fingerprint) {
	}

	private static String fingerprint(final byte[] bytes) {
		return "sha256:" + com.sqlapp.util.MessageDigests.SHA256.checksumAsString(bytes);
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
		final var totals = BulkMigrationJobRepairTaskEvidenceValidator.validate(report.tasks(),
				"Bulk migration job repair execution");
		if (totals.mismatchChunks() != report.mismatchChunks() || totals.replayedChunks() != report.replayedChunks()
				|| totals.replayedRows() != report.replayedRows() || totals.affectedRows() != report.affectedRows()
				|| totals.tasksRequiringManualReconciliation() != report.tasksRequiringManualReconciliation()) {
			throw new CommandException("Bulk migration job repair execution report summary is inconsistent");
		}
		return report;
	}

	private static boolean blank(final String value) {
		return value == null || value.isBlank();
	}

	private static boolean sha256(final String value) {
		return value != null && value.matches("sha256:[0-9a-f]{64}");
	}
}
