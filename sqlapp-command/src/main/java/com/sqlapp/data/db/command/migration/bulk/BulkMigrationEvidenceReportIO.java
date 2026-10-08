/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

import com.sqlapp.data.db.command.migration.internal.AtomicMigrationFile;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.JsonConverter;

/** Reads and atomically writes bulk migration evidence reports. */
public final class BulkMigrationEvidenceReportIO {
	private static final Set<String> POLICIES = Set.of(BulkMigrationEvidenceReport.POLICY_SUCCESSFUL_EXECUTION,
			BulkMigrationEvidenceReport.POLICY_MATCHING_DATA, BulkMigrationEvidenceReport.POLICY_PROVENANCE_REQUIRED);
	private static final Set<String> ARTIFACTS = Set.of(BulkMigrationEvidenceReport.ARTIFACT_OPERATIONAL_REPORT,
			BulkMigrationEvidenceReport.ARTIFACT_VERIFICATION_REPORT,
			BulkMigrationEvidenceReport.ARTIFACT_CONFIGURATION, BulkMigrationEvidenceReport.ARTIFACT_ASSESSMENT_REPORT,
			BulkMigrationEvidenceReport.ARTIFACT_DDL_VERIFICATION_REPORT,
			BulkMigrationEvidenceReport.ARTIFACT_TARGET_VALIDATION_REPORT);

	public BulkMigrationEvidenceReport read(final Path file) {
		return read(file, null);
	}

	public BulkMigrationEvidenceReport read(final Path file, final Long maxFileSizeBytes) {
		return readSnapshot(file, maxFileSizeBytes).report();
	}

	Snapshot readSnapshot(final Path file, final Long maxFileSizeBytes) {
		if (file == null || !Files.isRegularFile(file)) {
			throw new CommandException("Bulk migration evidence report file is required.");
		}
		try {
			final byte[] bytes = BoundedMigrationFile.read(file, maxFileSizeBytes, "maxEvidenceFileSizeBytes",
					"Bulk migration evidence report");
			final var report = validate(new JsonConverter().fromJsonString(new String(bytes, StandardCharsets.UTF_8),
					BulkMigrationEvidenceReport.class));
			return new Snapshot(report, "sha256:" + com.sqlapp.util.MessageDigests.SHA256.checksumAsString(bytes));
		} catch (final IOException e) {
			throw new CommandException("Could not read bulk migration evidence report: " + file, e);
		} catch (final CommandException e) {
			throw e;
		} catch (final RuntimeException e) {
			throw new CommandException("Could not read bulk migration evidence report: " + file, e);
		}
	}

	public BulkMigrationEvidenceReport read(final Path file, final Path operationalReportFile,
			final Path verificationReportFile) {
		final var evidence = readSnapshot(file, null);
		final var operational = new BulkMigrationOperationalReportIO().readSnapshot(operationalReportFile, null);
		final var verification = new BulkMigrationVerificationReportIO().readSnapshot(verificationReportFile, null);
		return verifySources(evidence.report(), operational, verification);
	}

	BulkMigrationEvidenceReport verifySources(final BulkMigrationEvidenceReport report,
			final BulkMigrationOperationalReportIO.Snapshot operationalSnapshot,
			final BulkMigrationVerificationReportIO.Snapshot verificationSnapshot) {
		final var operational = operationalSnapshot.report();
		final var verification = verificationSnapshot.report();
		if (!report.operationalReportFingerprint().equals(operationalSnapshot.fingerprint())) {
			throw new CommandException("Operational report fingerprint does not match the evidence report.");
		}
		if (!report.verificationReportFingerprint().equals(verificationSnapshot.fingerprint())) {
			throw new CommandException("Verification report fingerprint does not match the evidence report.");
		}
		if (!report.jobId().equals(operational.jobId())
				|| !report.planFingerprint().equals(operational.planFingerprint())
				|| !report.planFingerprint().equals(verification.planFingerprint())
				|| report.executionEvent() != event(operational) || report.dataMatch() != verification.match()
				|| !Objects.equals(report.provenance(), operational.provenance())
				|| !Objects.equals(report.provenance(), verification.provenance())) {
			throw new CommandException("Bulk migration evidence report does not match its source reports.");
		}
		if (report.generatedAt().isBefore(operational.generatedAt())
				|| report.generatedAt().isBefore(verification.generatedAt())) {
			throw new CommandException("Bulk migration evidence report predates its source reports.");
		}
		return report;
	}

	public void write(final Path file, final BulkMigrationEvidenceReport report) {
		if (file == null) {
			throw new CommandException("Bulk migration evidence report output file is required.");
		}
		validate(report);
		try {
			final var converter = new JsonConverter();
			converter.setIndentOutput(true);
			AtomicMigrationFile.write(file, temporary -> converter.writeJsonValue(temporary.toFile(), report));
		} catch (IOException | RuntimeException e) {
			throw new CommandException("Could not write bulk migration evidence report: " + file, e);
		}
	}

	Snapshot writeSnapshot(final Path file, final BulkMigrationEvidenceReport report, final Long maxFileSizeBytes) {
		write(file, report);
		final var snapshot = readSnapshot(file, maxFileSizeBytes);
		if (!report.equals(snapshot.report())) {
			throw new CommandException("Bulk migration evidence report changed after write.");
		}
		return snapshot;
	}

	record Snapshot(BulkMigrationEvidenceReport report, String fingerprint) {
	}

	private static BulkMigrationEvidenceReport validate(final BulkMigrationEvidenceReport report) {
		if (report == null || report.formatVersion() != BulkMigrationEvidenceReport.CURRENT_FORMAT_VERSION) {
			throw new CommandException("Unsupported bulk migration evidence report formatVersion.");
		}
		if (report.generatedAt() == null || blank(report.jobId()) || blank(report.planFingerprint())
				|| !fingerprint(report.operationalReportFingerprint())
				|| !fingerprint(report.verificationReportFingerprint())) {
			throw new CommandException("Bulk migration evidence report contains invalid identities.");
		}
		if (report.verificationPolicies() == null || report.verifiedArtifacts() == null
				|| report.verificationPolicies().stream().anyMatch(BulkMigrationEvidenceReportIO::blank)
				|| report.verifiedArtifacts().stream().anyMatch(BulkMigrationEvidenceReportIO::blank)
				|| new HashSet<>(report.verificationPolicies()).size() != report.verificationPolicies().size()
				|| new HashSet<>(report.verifiedArtifacts()).size() != report.verifiedArtifacts().size()) {
			throw new CommandException("Bulk migration evidence report contains invalid policy or artifact names.");
		}
		if (!POLICIES.containsAll(report.verificationPolicies()) || !ARTIFACTS.containsAll(report.verifiedArtifacts())
				|| !report.verifiedArtifacts().contains(BulkMigrationEvidenceReport.ARTIFACT_OPERATIONAL_REPORT)
				|| !report.verifiedArtifacts().contains(BulkMigrationEvidenceReport.ARTIFACT_VERIFICATION_REPORT)) {
			throw new CommandException("Bulk migration evidence report contains unsupported or missing audit entries.");
		}
		if (report.verificationPolicies().contains(BulkMigrationEvidenceReport.POLICY_SUCCESSFUL_EXECUTION)
				&& report.executionEvent() != BulkMigrationOperationalReport.ExecutionEvent.JOB_COMPLETED) {
			throw new CommandException("Successful-execution policy requires JOB_COMPLETED evidence.");
		}
		if (report.verificationPolicies().contains(BulkMigrationEvidenceReport.POLICY_MATCHING_DATA)
				&& !report.dataMatch()) {
			throw new CommandException("Matching-data policy requires matching verification evidence.");
		}
		if (report.verificationPolicies().contains(BulkMigrationEvidenceReport.POLICY_PROVENANCE_REQUIRED)
				&& report.provenance() == null) {
			throw new CommandException("Provenance-required policy requires provenance evidence.");
		}
		if (report.verifiedArtifacts().contains(BulkMigrationEvidenceReport.ARTIFACT_ASSESSMENT_REPORT)
				&& (report.provenance() == null || report.provenance().assessmentReportFingerprint() == null)
				|| report.verifiedArtifacts().contains(BulkMigrationEvidenceReport.ARTIFACT_DDL_VERIFICATION_REPORT)
						&& (report.provenance() == null
								|| report.provenance().ddlVerificationReportFingerprint() == null)
				|| report.verifiedArtifacts().contains(BulkMigrationEvidenceReport.ARTIFACT_CONFIGURATION)
						&& report.provenance() == null
				|| report.verifiedArtifacts().contains(BulkMigrationEvidenceReport.ARTIFACT_TARGET_VALIDATION_REPORT)
						&& (report.provenance() == null
								|| report.provenance().targetValidationReportFingerprint() == null)) {
			throw new CommandException("Verified source artifacts require matching provenance fingerprints.");
		}
		return report;
	}

	private static BulkMigrationOperationalReport.ExecutionEvent event(final BulkMigrationOperationalReport report) {
		return report.execution() == null ? null : report.execution().event();
	}

	private static boolean blank(final String value) {
		return value == null || value.isBlank();
	}

	private static boolean fingerprint(final String value) {
		return value != null && value.matches("sha256:[0-9a-f]{64}");
	}
}
