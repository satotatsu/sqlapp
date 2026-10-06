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
		final String plan = "sha256:" + "b".repeat(64);
		final Path configuration = write("job.yaml", "job: approved");
		final Path assessment = write("assessment.json", "{\"status\":\"REVIEW_REQUIRED\"}");
		final Path ddl = write("ddl.json", "{\"status\":\"VERIFIED\"}");
		final var approvalProvenance = new BulkMigrationArtifactProvenance(fingerprint(configuration),
				fingerprint(assessment), fingerprint(ddl));
		final Path targetValidation = directory.resolve("target-validation.json");
		new BulkMigrationTargetValidationReportIO().write(targetValidation,
				new BulkMigrationTargetValidationReport(BulkMigrationTargetValidationReport.CURRENT_FORMAT_VERSION,
						Instant.parse("2026-10-01T23:59:00Z"), "access-import", plan,
						approvalProvenance.configurationFingerprint(), List.of(), approvalProvenance,
						"production-oracle", "Oracle", "23", null, "APP"));
		final var provenance = new BulkMigrationArtifactProvenance(approvalProvenance.configurationFingerprint(),
				approvalProvenance.assessmentReportFingerprint(), approvalProvenance.ddlVerificationReportFingerprint(),
				fingerprint(targetValidation));
		final Path operations = directory.resolve("operations.json");
		final Path verification = directory.resolve("verification.json");
		final Path evidence = directory.resolve("evidence.json");
		writeOperational(operations, plan, provenance);
		writeVerification(verification, plan, provenance);

		final var command = command(operations, verification, configuration, assessment, ddl);
		command.setTargetValidationReportFile(targetValidation.toFile());
		command.setExpectedTargetEnvironmentId("production-oracle");
		command.setOutputFile(evidence.toFile());
		assertDoesNotThrow(command::run);
		assertEquals(fingerprint(evidence), command.getEvidenceReportFingerprint());
		assertEquals(plan, command.getOperationalReport().planFingerprint());
		assertEquals(command.getOperationalReport().provenance(), command.getVerificationReport().provenance());
		final var saved = new BulkMigrationEvidenceReportIO().read(evidence);
		assertEquals(List.of("SUCCESSFUL_EXECUTION", "MATCHING_DATA", "PROVENANCE_REQUIRED"),
				saved.verificationPolicies());
		assertEquals(List.of("OPERATIONAL_REPORT", "VERIFICATION_REPORT", "CONFIGURATION", "ASSESSMENT_REPORT",
				"DDL_VERIFICATION_REPORT", "TARGET_VALIDATION_REPORT"), saved.verifiedArtifacts());
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
		reportCommand.setTargetValidationReportFile(targetValidation.toFile());
		reportCommand.setExpectedTargetEnvironmentId("production-oracle");
		reportCommand.setExpectedEvidenceReportFingerprint(fingerprint(evidence));
		reportCommand.setExpectedPlanFingerprint(plan);
		reportCommand.setExpectedConfigurationFingerprint(provenance.configurationFingerprint());
		assertDoesNotThrow(reportCommand::run);
		assertEquals(saved, reportCommand.getReport());
		assertEquals(fingerprint(evidence), reportCommand.getEvidenceReportFingerprint());
		reportCommand.setExpectedTargetEnvironmentId("staging-oracle");
		assertThrows(CommandException.class, reportCommand::run);
		reportCommand.setExpectedTargetEnvironmentId("production-oracle");
		Files.writeString(targetValidation, "changed");
		assertThrows(CommandException.class, reportCommand::run);
		new BulkMigrationTargetValidationReportIO().write(targetValidation,
				new BulkMigrationTargetValidationReport(BulkMigrationTargetValidationReport.CURRENT_FORMAT_VERSION,
						Instant.parse("2026-10-01T23:59:00Z"), "access-import", plan,
						approvalProvenance.configurationFingerprint(), List.of(), approvalProvenance,
						"production-oracle", "Oracle", "23", null, "APP"));
		Files.writeString(ddl, "changed");
		assertThrows(CommandException.class, reportCommand::run);
		Files.writeString(ddl, "{\"status\":\"VERIFIED\"}");
		reportCommand.setExpectedEvidenceReportFingerprint("sha256:" + "f".repeat(64));
		assertThrows(CommandException.class, reportCommand::run);
		reportCommand.setExpectedEvidenceReportFingerprint(fingerprint(evidence));
		reportCommand.setExpectedPlanFingerprint("another-plan");
		assertThrows(CommandException.class, reportCommand::run);
		reportCommand.setExpectedPlanFingerprint(plan);
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
	void boundsEvidenceAndApprovalArtifacts() throws Exception {
		final Path operations = directory.resolve("bounded-operations.json");
		final Path verification = directory.resolve("bounded-verification.json");
		writeOperational(operations, "plan-1", null);
		writeVerification(verification, "plan-1", null);
		final var command = command(operations, verification, null, null, null);
		command.setRequireProvenance(false);
		command.setMaxEvidenceFileSizeBytes(1L);
		assertThrows(CommandException.class, command::run);

		command.setMaxEvidenceFileSizeBytes(1024 * 1024L);
		command.setMaxApprovalArtifactFileSizeBytes(0L);
		assertThrows(CommandException.class, command::run);
		command.setMaxApprovalArtifactFileSizeBytes(null);
		command.setOutputFile(directory.resolve("bounded-evidence.json").toFile());
		assertDoesNotThrow(command::run);

		final var reportCommand = new VerifyBulkMigrationEvidenceReportCommand();
		reportCommand.setEvidenceReportFile(command.getOutputFile());
		reportCommand.setOperationalReportFile(operations.toFile());
		reportCommand.setVerificationReportFile(verification.toFile());
		reportCommand.setMaxEvidenceFileSizeBytes(1L);
		assertThrows(CommandException.class, reportCommand::run);
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
	void rejectsTargetValidationEvidenceCreatedAfterExecution() throws Exception {
		final String plan = "sha256:" + "c".repeat(64);
		final Path configuration = write("late-job.yaml", "job: approved");
		final var approvalProvenance = new BulkMigrationArtifactProvenance(fingerprint(configuration), null, null);
		final Path targetValidation = directory.resolve("late-target-validation.json");
		new BulkMigrationTargetValidationReportIO().write(targetValidation,
				new BulkMigrationTargetValidationReport(BulkMigrationTargetValidationReport.CURRENT_FORMAT_VERSION,
						Instant.parse("2026-10-02T00:02:00Z"), "access-import", plan,
						approvalProvenance.configurationFingerprint(), List.of(), approvalProvenance,
						"production-oracle", "Oracle", "23", null, "APP"));
		final var executionProvenance = new BulkMigrationArtifactProvenance(
				approvalProvenance.configurationFingerprint(), null, null, fingerprint(targetValidation));
		final Path operations = directory.resolve("late-operations.json");
		final Path verification = directory.resolve("late-verification.json");
		writeOperational(operations, plan, executionProvenance);
		writeVerification(verification, plan, executionProvenance);
		final var command = command(operations, verification, configuration, null, null);
		command.setTargetValidationReportFile(targetValidation.toFile());
		command.setExpectedTargetEnvironmentId("production-oracle");

		assertThrows(CommandException.class, command::run);
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
