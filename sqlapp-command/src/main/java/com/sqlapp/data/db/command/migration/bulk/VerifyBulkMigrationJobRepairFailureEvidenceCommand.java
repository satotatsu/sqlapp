/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import java.io.File;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.List;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.MessageDigests;

import lombok.Getter;
import lombok.Setter;

/** Verifies approved repair failure evidence without database access. */
@Getter
@Setter
public class VerifyBulkMigrationJobRepairFailureEvidenceCommand extends AbstractCommand {
	private File repairFailureReportFile;
	private File approvedRepairPlanFile;
	private String expectedRepairFailureReportFingerprint;
	private String expectedMigrationPlanFingerprint;
	private String expectedRepairPlanFingerprint;
	private String expectedConfigurationFingerprint;
	private Long maxEvidenceAgeSeconds;
	private BulkMigrationJobRepairFailureReport report;

	@Override
	protected void doRun() {
		report = null;
		requireFile(repairFailureReportFile, "repairFailureReportFile");
		requireFile(approvedRepairPlanFile, "approvedRepairPlanFile");
		validateSha256(expectedRepairFailureReportFingerprint, "expectedRepairFailureReportFingerprint");
		validateSha256(expectedConfigurationFingerprint, "expectedConfigurationFingerprint");
		validateOptionalText(expectedMigrationPlanFingerprint, "expectedMigrationPlanFingerprint");
		validateOptionalText(expectedRepairPlanFingerprint, "expectedRepairPlanFingerprint");
		if (maxEvidenceAgeSeconds != null && maxEvidenceAgeSeconds <= 0) {
			throw new CommandException("maxEvidenceAgeSeconds must be greater than zero.");
		}
		if (expectedRepairFailureReportFingerprint != null
				&& !expectedRepairFailureReportFingerprint.equals(fingerprint(repairFailureReportFile))) {
			throw new CommandException(
					"repairFailureReportFile fingerprint does not match expectedRepairFailureReportFingerprint.");
		}
		final var failure = new BulkMigrationJobRepairFailureReportIO().read(repairFailureReportFile.toPath());
		final var approval = new BulkMigrationJobRepairPlanReportIO().read(approvedRepairPlanFile.toPath());
		final List<String> approvedTaskIds = approval.tasks().stream()
				.map(BulkMigrationJobRepairPlanReport.Task::taskId).toList();
		final List<String> completedTaskIds = failure.completedTasks().stream()
				.map(BulkMigrationJobRepairExecutionReport.Task::taskId).toList();
		if (!fingerprint(approvedRepairPlanFile).equals(failure.approvedRepairPlanFileFingerprint())
				|| !approval.planFingerprint().equals(failure.repairPlanFingerprint())
				|| !isCompletedPrefix(approvedTaskIds, completedTaskIds)
				|| completedTaskIds.contains(failure.failedTaskId())
				|| (!"PREFLIGHT".equals(failure.phase())
						&& (completedTaskIds.size() >= approvedTaskIds.size()
								|| !approvedTaskIds.get(completedTaskIds.size()).equals(failure.failedTaskId())))) {
			throw new CommandException("Repair failure report does not match approvedRepairPlanFile.");
		}
		if (expectedMigrationPlanFingerprint != null
				&& !expectedMigrationPlanFingerprint.equals(failure.migrationPlanFingerprint())) {
			throw new CommandException("Repair failure evidence does not match expectedMigrationPlanFingerprint.");
		}
		if (expectedRepairPlanFingerprint != null
				&& !expectedRepairPlanFingerprint.equals(failure.repairPlanFingerprint())) {
			throw new CommandException("Repair failure evidence does not match expectedRepairPlanFingerprint.");
		}
		if (expectedConfigurationFingerprint != null && (failure.provenance() == null
				|| !expectedConfigurationFingerprint.equals(failure.provenance().configurationFingerprint()))) {
			throw new CommandException("Repair failure evidence does not match expectedConfigurationFingerprint.");
		}
		validateAge(failure.failedAt());
		report = failure;
		info("Bulk migration job repair failure evidence verified: ", repairFailureReportFile.getAbsolutePath());
	}

	private static boolean isCompletedPrefix(final List<String> approved, final List<String> completed) {
		return completed.size() <= approved.size() && approved.subList(0, completed.size()).equals(completed);
	}

	private void validateAge(final Instant failedAt) {
		if (maxEvidenceAgeSeconds == null) {
			return;
		}
		final Instant now = Instant.now();
		if (failedAt.isAfter(now)) {
			throw new CommandException("Repair failure failedAt is in the future; check clock synchronization.");
		}
		try {
			if (failedAt.plusSeconds(maxEvidenceAgeSeconds).isBefore(now)) {
				throw new CommandException("Repair failure evidence has expired.");
			}
		} catch (DateTimeException | ArithmeticException e) {
			throw new CommandException("maxEvidenceAgeSeconds is outside the supported time range.", e);
		}
	}

	private static void requireFile(final File file, final String property) {
		if (file == null || !file.isFile()) {
			throw new CommandException(property + " must be an existing file.");
		}
	}

	private static void validateSha256(final String value, final String property) {
		if (value != null && !value.matches("sha256:[0-9a-f]{64}")) {
			throw new CommandException(property + " must be a lowercase SHA-256 value.");
		}
	}

	private static void validateOptionalText(final String value, final String property) {
		if (value != null && value.isBlank()) {
			throw new CommandException(property + " must not be blank.");
		}
	}

	private static String fingerprint(final File file) {
		return "sha256:" + MessageDigests.SHA256.checksumAsString(file);
	}
}
