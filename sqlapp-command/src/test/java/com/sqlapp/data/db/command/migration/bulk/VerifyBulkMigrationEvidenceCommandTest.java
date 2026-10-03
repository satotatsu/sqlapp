/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.command.migration.bulk;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.MessageDigests;

class VerifyBulkMigrationEvidenceCommandTest {
	@TempDir
	Path directory;

	@Test
	void verifiesCompletedMatchingReportsAndReferencedArtifacts() throws Exception {
		final Path configuration = write("job.yaml", "job: approved");
		final Path assessment = write("assessment.json", "{\"status\":\"REVIEW_REQUIRED\"}");
		final Path ddl = write("ddl.json", "{\"status\":\"VERIFIED\"}");
		final var provenance = new BulkMigrationArtifactProvenance(fingerprint(configuration), fingerprint(assessment),
				fingerprint(ddl));
		final Path operations = directory.resolve("operations.json");
		final Path verification = directory.resolve("verification.json");
		final Path evidence = directory.resolve("evidence.json");
		writeOperational(operations, "plan-1", provenance);
		writeVerification(verification, "plan-1", provenance);

		final var command = command(operations, verification, configuration, assessment, ddl);
		command.setOutputFile(evidence.toFile());
		assertDoesNotThrow(command::run);
		assertEquals("plan-1", command.getOperationalReport().planFingerprint());
		assertEquals(command.getOperationalReport().provenance(), command.getVerificationReport().provenance());
		final var saved = new BulkMigrationEvidenceReportIO().read(evidence);
		assertEquals(List.of("SUCCESSFUL_EXECUTION", "MATCHING_DATA", "PROVENANCE_REQUIRED"),
				saved.verificationPolicies());
		assertEquals(List.of("OPERATIONAL_REPORT", "VERIFICATION_REPORT", "CONFIGURATION", "ASSESSMENT_REPORT",
				"DDL_VERIFICATION_REPORT"), saved.verifiedArtifacts());
		assertEquals(fingerprint(operations), saved.operationalReportFingerprint());
		assertEquals(provenance, saved.provenance());
		assertEquals(saved, new BulkMigrationEvidenceReportIO().read(evidence, operations, verification));
		final var reportCommand = new VerifyBulkMigrationEvidenceReportCommand();
		reportCommand.setEvidenceReportFile(evidence.toFile());
		reportCommand.setOperationalReportFile(operations.toFile());
		reportCommand.setVerificationReportFile(verification.toFile());
		reportCommand.setConfigurationFile(configuration.toFile());
		reportCommand.setAssessmentReportFile(assessment.toFile());
		reportCommand.setDdlVerificationReportFile(ddl.toFile());
		reportCommand.setExpectedEvidenceReportFingerprint(fingerprint(evidence));
		reportCommand.setExpectedPlanFingerprint("plan-1");
		reportCommand.setExpectedConfigurationFingerprint(provenance.configurationFingerprint());
		assertDoesNotThrow(reportCommand::run);
		assertEquals(saved, reportCommand.getReport());
		Files.writeString(ddl, "changed");
		assertThrows(CommandException.class, reportCommand::run);
		Files.writeString(ddl, "{\"status\":\"VERIFIED\"}");
		reportCommand.setExpectedEvidenceReportFingerprint("sha256:" + "f".repeat(64));
		assertThrows(CommandException.class, reportCommand::run);
		reportCommand.setExpectedEvidenceReportFingerprint(fingerprint(evidence));
		reportCommand.setExpectedPlanFingerprint("another-plan");
		assertThrows(CommandException.class, reportCommand::run);
		reportCommand.setExpectedPlanFingerprint("plan-1");
		final var oldEvidence = new BulkMigrationEvidenceReport(saved.formatVersion(),
				Instant.parse("2026-10-02T00:02:00Z"), saved.jobId(), saved.planFingerprint(),
				saved.operationalReportFingerprint(), saved.verificationReportFingerprint(), saved.executionEvent(),
				saved.dataMatch(), saved.provenance(), saved.verificationPolicies(), saved.verifiedArtifacts());
		new BulkMigrationEvidenceReportIO().write(evidence, oldEvidence);
		reportCommand.setExpectedEvidenceReportFingerprint(fingerprint(evidence));
		reportCommand.setMaxEvidenceAgeSeconds(1L);
		assertThrows(CommandException.class, reportCommand::run);

		Files.writeString(assessment, "changed");
		assertThrows(CommandException.class, command::run);
		Files.writeString(assessment, "{\"status\":\"REVIEW_REQUIRED\"}");
		writeVerification(verification, "another-plan", provenance);
		assertThrows(CommandException.class,
				() -> new BulkMigrationEvidenceReportIO().read(evidence, operations, verification));
		assertThrows(CommandException.class, command::run);
	}

	@Test
	void evidenceReportRejectsPoliciesThatDisagreeWithRecordedResults() {
		final var invalid = new BulkMigrationEvidenceReport(BulkMigrationEvidenceReport.CURRENT_FORMAT_VERSION,
				Instant.parse("2026-10-02T00:02:00Z"), "access-import", "plan-1", "sha256:" + "1".repeat(64),
				"sha256:" + "2".repeat(64), BulkMigrationOperationalReport.ExecutionEvent.JOB_FAILED, false, null,
				List.of(BulkMigrationEvidenceReport.POLICY_SUCCESSFUL_EXECUTION,
						BulkMigrationEvidenceReport.POLICY_MATCHING_DATA,
						BulkMigrationEvidenceReport.POLICY_PROVENANCE_REQUIRED),
				List.of(BulkMigrationEvidenceReport.ARTIFACT_OPERATIONAL_REPORT,
						BulkMigrationEvidenceReport.ARTIFACT_VERIFICATION_REPORT));
		assertThrows(CommandException.class,
				() -> new BulkMigrationEvidenceReportIO().write(directory.resolve("invalid.json"), invalid));
	}

	@Test
	void rejectsMissingOrDifferentProvenanceByDefault() throws Exception {
		final Path operations = directory.resolve("operations.json");
		final Path verification = directory.resolve("verification.json");
		writeOperational(operations, "plan-1", null);
		writeVerification(verification, "plan-1", null);
		final var command = command(operations, verification, null, null, null);
		assertThrows(CommandException.class, command::run);
		command.setRequireProvenance(false);
		assertDoesNotThrow(command::run);

		final var provenance = new BulkMigrationArtifactProvenance("sha256:" + "a".repeat(64), null, null);
		writeVerification(verification, "plan-1", provenance);
		assertThrows(CommandException.class, command::run);
	}

	private VerifyBulkMigrationEvidenceCommand command(final Path operations, final Path verification,
			final Path configuration, final Path assessment, final Path ddl) {
		final var command = new VerifyBulkMigrationEvidenceCommand();
		command.setOperationalReportFile(operations.toFile());
		command.setVerificationReportFile(verification.toFile());
		command.setConfigurationFile(configuration == null ? null : configuration.toFile());
		command.setAssessmentReportFile(assessment == null ? null : assessment.toFile());
		command.setDdlVerificationReportFile(ddl == null ? null : ddl.toFile());
		return command;
	}

	private void writeOperational(final Path file, final String plan,
			final BulkMigrationArtifactProvenance provenance) {
		final Instant now = Instant.parse("2026-10-02T00:00:00Z");
		final var execution = new BulkMigrationOperationalReport.Execution(
				BulkMigrationOperationalReport.ExecutionEvent.JOB_COMPLETED, null, now, 0L, null, null, null);
		final var report = new BulkMigrationOperationalReport(BulkMigrationOperationalReport.CURRENT_FORMAT_VERSION,
				now, "access-import", plan, true, 0, 0, 0, List.of(), List.of(), null, null, List.of(), execution,
				provenance);
		new BulkMigrationOperationalReportIO().write(file, report);
	}

	private void writeVerification(final Path file, final String plan,
			final BulkMigrationArtifactProvenance provenance) {
		final var report = new BulkMigrationVerificationReport(BulkMigrationVerificationReport.CURRENT_FORMAT_VERSION,
				Instant.parse("2026-10-02T00:01:00Z"), plan, BulkMigrationVerificationIsolation.DEFAULT.name(), true,
				0, 0, 0, List.of(), provenance);
		new BulkMigrationVerificationReportIO().write(file, report);
	}

	private Path write(final String name, final String value) throws Exception {
		return Files.writeString(directory.resolve(name), value);
	}

	private static String fingerprint(final Path file) {
		return "sha256:" + MessageDigests.SHA256.checksumAsString(file.toFile());
	}
}
