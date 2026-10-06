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

	Snapshot writeSnapshot(final Path file, final BulkMigrationJobRepairFailureReport report) {
		write(file, report);
		final var snapshot = readSnapshot(file, null);
		if (!report.equals(snapshot.report())) {
			throw new CommandException("Bulk migration job repair failure report changed after write.");
		}
		return snapshot;
	}

	public BulkMigrationJobRepairFailureReport read(final Path file) {
		return read(file, null);
	}

	public BulkMigrationJobRepairFailureReport read(final Path file, final Long maxFileSizeBytes) {
		return readSnapshot(file, maxFileSizeBytes).report();
	}

	Snapshot readSnapshot(final Path file, final Long maxFileSizeBytes) {
		final Path absolute = Objects.requireNonNull(file, "file").toAbsolutePath().normalize();
		if (!Files.isRegularFile(absolute)) {
			throw new CommandException("Bulk migration job repair failure report does not exist: " + absolute);
		}
		try {
			final byte[] bytes = BoundedMigrationJsonFile.read(absolute, maxFileSizeBytes,
					"maxEvidenceFileSizeBytes", "Bulk migration job repair failure report");
			final var report = validate(new JsonConverter().fromJsonString(new String(bytes, StandardCharsets.UTF_8),
					BulkMigrationJobRepairFailureReport.class));
			return new Snapshot(report, fingerprint(bytes));
		} catch (IOException | RuntimeException e) {
			if (e instanceof CommandException commandException) {
				throw commandException;
			}
			throw new CommandException("Failed to read bulk migration job repair failure report: " + absolute, e);
		}
	}

	record Snapshot(BulkMigrationJobRepairFailureReport report, String fingerprint) {
	}

	private static String fingerprint(final byte[] bytes) {
		return "sha256:" + com.sqlapp.util.MessageDigests.SHA256.checksumAsString(bytes);
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
		BulkMigrationJobRepairTaskEvidenceValidator.validate(report.completedTasks(),
				"Bulk migration job repair completed");
		if ("PREFLIGHT".equals(report.phase()) && !report.completedTasks().isEmpty()) {
			throw new CommandException("Bulk migration job repair preflight failure must not contain completed tasks");
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
