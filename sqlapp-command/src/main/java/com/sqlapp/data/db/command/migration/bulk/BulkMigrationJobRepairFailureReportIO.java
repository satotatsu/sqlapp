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
import com.sqlapp.jdbc.bulk.BulkMigrationJobRepairException;
import com.sqlapp.jdbc.bulk.BulkMigrationJobRepairResult;
import com.sqlapp.jdbc.bulk.BulkMigrationJobVerificationResult;
import com.sqlapp.util.JsonConverter;

/** Atomically reads and writes unsuccessful job-repair evidence. */
public final class BulkMigrationJobRepairFailureReportIO {
	public static final int MAX_FAILURE_MESSAGE_LENGTH = 2_000;

	public BulkMigrationJobRepairFailureReport fromFailure(final String migrationPlanFingerprint,
			final String approvedRepairPlanFileFingerprint, final BulkMigrationJobRepairException failure,
			final BulkMigrationArtifactProvenance provenance) {
		Objects.requireNonNull(failure, "failure");
		final var tasks = failure.getCompletedResult().getTasks().stream().map(task -> {
			final var repair = task.getRepairResult();
			return new BulkMigrationJobRepairExecutionReport.Task(task.getTaskId(), repair.getMismatchChunks(),
					repair.getReplayedChunks(), repair.getReplayedRows(), repair.getAffectedRows(),
					repair.getChunksWithExtraActualRows(), repair.getChunksWithoutExpectedRows());
		}).toList();
		final Throwable cause = failure.getCause() == null ? failure : failure.getCause();
		final String message = String.valueOf(cause.getMessage());
		return validate(new BulkMigrationJobRepairFailureReport(CURRENT_VERSION, Instant.now(),
				migrationPlanFingerprint, failure.getCompletedResult().getPlanFingerprint(),
				approvedRepairPlanFileFingerprint, null, failure.getPhase().name(), failure.getFailedTaskId(),
				cause.getClass().getName(), message.substring(0, Math.min(message.length(), MAX_FAILURE_MESSAGE_LENGTH)),
				tasks, provenance));
	}

	public BulkMigrationJobRepairFailureReport fromVerificationFailure(final String migrationPlanFingerprint,
			final String approvedRepairPlanFileFingerprint, final String verificationReportFingerprint,
			final BulkMigrationJobRepairResult result, final BulkMigrationJobVerificationResult verification,
			final BulkMigrationArtifactProvenance provenance) {
		Objects.requireNonNull(result, "result");
		Objects.requireNonNull(verification, "verification");
		final var failedTaskId = verification.getTasks().stream()
				.filter(task -> !task.getVerificationResult().isMatch()).map(task -> task.getTaskId()).findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Verification result must contain a mismatch"));
		final var tasks = result.getTasks().stream().map(task -> {
			final var repair = task.getRepairResult();
			return new BulkMigrationJobRepairExecutionReport.Task(task.getTaskId(), repair.getMismatchChunks(),
					repair.getReplayedChunks(), repair.getReplayedRows(), repair.getAffectedRows(),
					repair.getChunksWithExtraActualRows(), repair.getChunksWithoutExpectedRows());
		}).toList();
		final String message = "Post-repair verification mismatched " + verification.getMismatchedTasks() + " task(s)";
		return validate(new BulkMigrationJobRepairFailureReport(CURRENT_VERSION, Instant.now(),
				migrationPlanFingerprint, result.getPlanFingerprint(), approvedRepairPlanFileFingerprint,
				verificationReportFingerprint, "POST_VERIFICATION", failedTaskId, CommandException.class.getName(),
				message, tasks, provenance));
	}

	private static final int CURRENT_VERSION = BulkMigrationJobRepairFailureReport.CURRENT_FORMAT_VERSION;

	public void write(final Path file, final BulkMigrationJobRepairFailureReport report) {
		validate(report);
		final Path absolute = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
		try {
			final JsonConverter converter = new JsonConverter();
			converter.setIndentOutput(true);
			AtomicMigrationFile.write(absolute, temporary -> converter.writeJsonValue(temporary.toFile(), report));
		} catch (IOException | RuntimeException e) {
			throw new CommandException("Failed to write bulk migration job repair failure report: " + absolute, e);
		}
	}

	public BulkMigrationJobRepairFailureReport read(final Path file) {
		final Path absolute = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
		if (!Files.isRegularFile(absolute)) {
			throw new CommandException("Bulk migration job repair failure report does not exist: " + absolute);
		}
		try {
			return validate(new JsonConverter().fromJsonString(absolute.toFile(),
					BulkMigrationJobRepairFailureReport.class));
		} catch (RuntimeException e) {
			if (e instanceof CommandException commandException) {
				throw commandException;
			}
			throw new CommandException("Failed to read bulk migration job repair failure report: " + absolute, e);
		}
	}

	static BulkMigrationJobRepairFailureReport validate(final BulkMigrationJobRepairFailureReport report) {
		if (report == null || report.formatVersion() != CURRENT_VERSION || report.failedAt() == null
				|| blank(report.migrationPlanFingerprint()) || blank(report.repairPlanFingerprint())
				|| report.approvedRepairPlanFileFingerprint() == null
				|| !report.approvedRepairPlanFileFingerprint().matches("sha256:[0-9a-f]{64}")
				|| blank(report.failedTaskId()) || blank(report.failureType()) || report.failureMessage() == null
				|| report.failureMessage().length() > MAX_FAILURE_MESSAGE_LENGTH || report.completedTasks() == null) {
			throw new CommandException("Bulk migration job repair failure report is invalid");
		}
		if (!java.util.Set.of("PREFLIGHT", "EXECUTION", "POST_VERIFICATION").contains(report.phase())) {
			throw new CommandException("Bulk migration job repair failure phase is invalid");
		}
		if ((!"POST_VERIFICATION".equals(report.phase()) && report.postRepairVerificationReportFingerprint() != null)
				|| (report.postRepairVerificationReportFingerprint() != null
						&& !sha256(report.postRepairVerificationReportFingerprint()))) {
			throw new CommandException("Bulk migration job repair failure verification fingerprint is invalid");
		}
		final var ids = new HashSet<String>();
		for (final var task : report.completedTasks()) {
			if (task == null || blank(task.taskId()) || !ids.add(task.taskId())) {
				throw new CommandException("Bulk migration job repair completed task is invalid");
			}
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
