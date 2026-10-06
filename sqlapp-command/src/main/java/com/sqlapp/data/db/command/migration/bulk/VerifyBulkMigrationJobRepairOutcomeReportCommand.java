/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import java.io.File;
import java.time.DateTimeException;
import java.time.Instant;
import java.util.Objects;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.exceptions.CommandException;

import lombok.Getter;
import lombok.Setter;

/** Revalidates a saved repair outcome summary against its authoritative evidence. */
@Getter
@Setter
public class VerifyBulkMigrationJobRepairOutcomeReportCommand extends AbstractCommand {
	private File outcomeReportFile;
	private File repairReportDirectory;
	private File approvedRepairPlanFile;
	private String expectedApprovedRepairPlanFileFingerprint;
	private Long maxApprovedRepairPlanAgeSeconds;
	private Long maxApprovedRepairPlanFileSizeBytes;
	private File repairExecutionReportFile;
	private File repairFailureReportFile;
	private File postRepairVerificationReportFile;
	private String expectedOutcomeReportFingerprint;
	private String expectedStatus;
	private String expectedMigrationPlanFingerprint;
	private String expectedRepairPlanFingerprint;
	private String expectedConfigurationFingerprint;
	private Long maxEvidenceAgeSeconds;
	private Long maxEvidenceFileSizeBytes;
	private BulkMigrationJobRepairOutcomeReport report;

	@Override
	protected void doRun() {
		report = null;
		resolveReportFiles();
		requireFile(outcomeReportFile, "outcomeReportFile");
		requireFile(approvedRepairPlanFile, "approvedRepairPlanFile");
		final var approved = BulkMigrationJobRepairApprovalValidator.validate(approvedRepairPlanFile,
				expectedApprovedRepairPlanFileFingerprint, maxApprovedRepairPlanAgeSeconds,
				maxApprovedRepairPlanFileSizeBytes);
		validateExpectedFingerprint();
		validateExpectedStatus();
		validateMaxAge();
		final var savedSnapshot = new BulkMigrationJobRepairOutcomeReportIO()
				.readSnapshot(outcomeReportFile.toPath(), maxEvidenceFileSizeBytes);
		validateExpectedFingerprint(savedSnapshot.fingerprint());
		final var saved = savedSnapshot.report();
		verifyAge(saved.generatedAt());

		final var verifier = new VerifyBulkMigrationJobRepairOutcomeCommand();
		verifier.setApprovedRepairPlanFile(approvedRepairPlanFile);
		verifier.setExpectedApprovedRepairPlanFileFingerprint(approved.fingerprint());
		verifier.setMaxApprovedRepairPlanAgeSeconds(maxApprovedRepairPlanAgeSeconds);
		verifier.setMaxApprovedRepairPlanFileSizeBytes(maxApprovedRepairPlanFileSizeBytes);
		verifier.setRepairExecutionReportFile(repairExecutionReportFile);
		verifier.setRepairFailureReportFile(repairFailureReportFile);
		verifier.setPostRepairVerificationReportFile(postRepairVerificationReportFile);
		verifier.setExpectedMigrationPlanFingerprint(expectedMigrationPlanFingerprint);
		verifier.setExpectedRepairPlanFingerprint(expectedRepairPlanFingerprint);
		verifier.setExpectedConfigurationFingerprint(expectedConfigurationFingerprint);
		verifier.setMaxEvidenceAgeSeconds(maxEvidenceAgeSeconds);
		verifier.setMaxEvidenceFileSizeBytes(maxEvidenceFileSizeBytes);
		verifier.run();
		final var regenerated = verifier.getOutcomeReport();
		if (!sameEvidence(saved, regenerated) || expectedStatus != null && !expectedStatus.equals(saved.status())) {
			throw new CommandException("Repair outcome report does not match its authoritative evidence.");
		}
		verifyOrdering(saved, verifier);
		report = saved;
		info("Bulk migration job repair outcome report verified: ", outcomeReportFile.getAbsolutePath());
	}

	private void resolveReportFiles() {
		if (repairReportDirectory == null) {
			return;
		}
		if (!repairReportDirectory.isDirectory()) {
			throw new CommandException("repairReportDirectory must be an existing directory.");
		}
		final var resolved = BulkMigrationJobRepairReportFiles.resolve(repairReportDirectory);
		if (outcomeReportFile == null) { outcomeReportFile = resolved.outcome(); }
		if (repairExecutionReportFile == null) { repairExecutionReportFile = resolved.execution(); }
		if (repairFailureReportFile == null) { repairFailureReportFile = resolved.failure(); }
		if (postRepairVerificationReportFile == null) { postRepairVerificationReportFile = resolved.verification(); }
	}

	private void validateExpectedFingerprint() {
		if (expectedOutcomeReportFingerprint == null) {
			return;
		}
		if (!sha256(expectedOutcomeReportFingerprint)) {
			throw new CommandException("expectedOutcomeReportFingerprint must be a lowercase SHA-256 value.");
		}
	}

	private void validateExpectedFingerprint(final String actualFingerprint) {
		if (expectedOutcomeReportFingerprint != null
				&& !expectedOutcomeReportFingerprint.equals(actualFingerprint)) {
			throw new CommandException(
					"outcomeReportFile fingerprint does not match expectedOutcomeReportFingerprint.");
		}
	}

	private void validateExpectedStatus() {
		if (expectedStatus != null) {
			try {
				VerifyBulkMigrationJobRepairOutcomeCommand.Status.valueOf(expectedStatus);
			} catch (IllegalArgumentException e) {
				throw new CommandException("expectedStatus must be SUCCEEDED, EXECUTION_FAILED or VERIFICATION_FAILED.");
			}
		}
	}

	private void validateMaxAge() {
		if (maxEvidenceAgeSeconds != null && maxEvidenceAgeSeconds <= 0) {
			throw new CommandException("maxEvidenceAgeSeconds must be greater than zero.");
		}
	}

	private void verifyAge(final Instant generatedAt) {
		final Instant now = Instant.now();
		if (generatedAt.isAfter(now)) {
			throw new CommandException("Repair outcome report generatedAt is in the future; check clock synchronization.");
		}
		if (maxEvidenceAgeSeconds != null) {
			try {
				if (generatedAt.plusSeconds(maxEvidenceAgeSeconds).isBefore(now)) {
					throw new CommandException("Repair outcome report has expired.");
				}
			} catch (DateTimeException | ArithmeticException e) {
				throw new CommandException("maxEvidenceAgeSeconds is outside the supported time range.", e);
			}
		}
	}

	private static boolean sameEvidence(final BulkMigrationJobRepairOutcomeReport left,
			final BulkMigrationJobRepairOutcomeReport right) {
		return left.status().equals(right.status())
				&& left.migrationPlanFingerprint().equals(right.migrationPlanFingerprint())
				&& left.repairPlanFingerprint().equals(right.repairPlanFingerprint())
				&& left.approvedRepairPlanFileFingerprint().equals(right.approvedRepairPlanFileFingerprint())
				&& Objects.equals(left.repairExecutionReportFingerprint(), right.repairExecutionReportFingerprint())
				&& Objects.equals(left.repairFailureReportFingerprint(), right.repairFailureReportFingerprint())
				&& Objects.equals(left.postRepairVerificationReportFingerprint(),
						right.postRepairVerificationReportFingerprint())
				&& Objects.equals(left.failurePhase(), right.failurePhase())
				&& Objects.equals(left.failedTaskId(), right.failedTaskId())
				&& Objects.equals(left.provenance(), right.provenance());
	}

	private static void verifyOrdering(final BulkMigrationJobRepairOutcomeReport saved,
			final VerifyBulkMigrationJobRepairOutcomeCommand verifier) {
		if (verifier.getExecutionReport() != null
				&& saved.generatedAt().isBefore(verifier.getExecutionReport().completedAt())
				|| verifier.getFailureReport() != null
						&& saved.generatedAt().isBefore(verifier.getFailureReport().failedAt())) {
			throw new CommandException("Repair outcome report predates its authoritative evidence.");
		}
		if (verifier.getPostRepairVerificationReport() != null) {
			final var verification = verifier.getPostRepairVerificationReport();
			if (saved.generatedAt().isBefore(verification.generatedAt())) {
				throw new CommandException("Repair outcome report predates its authoritative evidence.");
			}
		}
	}

	private static void requireFile(final File file, final String property) {
		if (file == null || !file.isFile()) {
			throw new CommandException(property + " must be an existing file.");
		}
	}

	private static boolean sha256(final String value) {
		return value != null && value.matches("sha256:[0-9a-f]{64}");
	}
}
