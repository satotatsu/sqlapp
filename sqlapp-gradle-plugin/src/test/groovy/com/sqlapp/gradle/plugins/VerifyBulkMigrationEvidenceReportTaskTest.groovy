/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins

import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertNotNull
import static org.junit.jupiter.api.Assertions.assertEquals

import java.time.Instant

import com.sqlapp.data.db.command.migration.bulk.BulkMigrationArtifactProvenance
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationEvidenceReport
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationEvidenceReportIO
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationOperationalReport
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationOperationalReportIO
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationVerificationIsolation
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationVerificationReport
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationVerificationReportIO
import com.sqlapp.util.MessageDigests

import org.gradle.api.Project
import org.junit.jupiter.api.Test

class VerifyBulkMigrationEvidenceReportTaskTest extends AbstractTaskTest {
	@Test
	void executesWithApprovedIdentitiesAndOriginalArtifacts() {
		Project project = createProject(testProjectDir)
		project.plugins.apply(DbPlugin)
		File configuration = new File(testProjectDir, 'job.yaml')
		File assessment = new File(testProjectDir, 'assessment.json')
		File ddl = new File(testProjectDir, 'ddl.json')
		configuration.text = 'job: access-import'
		assessment.text = '{"status":"REVIEW_REQUIRED"}'
		ddl.text = '{"status":"VERIFIED"}'
		def provenance = new BulkMigrationArtifactProvenance(fingerprint(configuration), fingerprint(assessment),
				fingerprint(ddl))
		Instant generatedAt = Instant.now().minusSeconds(10)
		def execution = new BulkMigrationOperationalReport.Execution(
				BulkMigrationOperationalReport.ExecutionEvent.JOB_COMPLETED, null, generatedAt, 0L, null, null, null)
		def operations = new BulkMigrationOperationalReport(BulkMigrationOperationalReport.CURRENT_FORMAT_VERSION,
				generatedAt, 'access-import', 'plan-1', true, 0, 0, 0, [], [], null, null, [], execution,
				provenance)
		File operationsFile = new File(testProjectDir, 'operations.json')
		new BulkMigrationOperationalReportIO().write(operationsFile.toPath(), operations)
		def verification = new BulkMigrationVerificationReport(BulkMigrationVerificationReport.CURRENT_FORMAT_VERSION,
				generatedAt, 'plan-1', BulkMigrationVerificationIsolation.DEFAULT.name(), true, 0, 0, 0, [], provenance)
		File verificationFile = new File(testProjectDir, 'verification.json')
		new BulkMigrationVerificationReportIO().write(verificationFile.toPath(), verification)
		def evidence = new BulkMigrationEvidenceReport(BulkMigrationEvidenceReport.CURRENT_FORMAT_VERSION,
				Instant.now(), 'access-import', 'plan-1', fingerprint(operationsFile), fingerprint(verificationFile),
				BulkMigrationOperationalReport.ExecutionEvent.JOB_COMPLETED, true, provenance,
				[BulkMigrationEvidenceReport.POLICY_SUCCESSFUL_EXECUTION,
				 BulkMigrationEvidenceReport.POLICY_MATCHING_DATA,
				 BulkMigrationEvidenceReport.POLICY_PROVENANCE_REQUIRED],
				[BulkMigrationEvidenceReport.ARTIFACT_OPERATIONAL_REPORT,
				 BulkMigrationEvidenceReport.ARTIFACT_VERIFICATION_REPORT,
				 BulkMigrationEvidenceReport.ARTIFACT_CONFIGURATION,
				 BulkMigrationEvidenceReport.ARTIFACT_ASSESSMENT_REPORT,
				 BulkMigrationEvidenceReport.ARTIFACT_DDL_VERIFICATION_REPORT])
		File evidenceFile = new File(testProjectDir, 'evidence.json')
		new BulkMigrationEvidenceReportIO().write(evidenceFile.toPath(), evidence)

		def task = project.tasks.named('verifyBulkMigrationEvidenceReport',
				VerifyBulkMigrationEvidenceReportTask).get()
		task.evidenceReportFile.set(evidenceFile)
		task.operationalReportFile.set(operationsFile)
		task.verificationReportFile.set(verificationFile)
		task.configurationFile.set(configuration)
		task.assessmentReportFile.set(assessment)
		task.ddlVerificationReportFile.set(ddl)
		task.expectedEvidenceReportFingerprint.set(fingerprint(evidenceFile))
		task.expectedPlanFingerprint.set('plan-1')
		task.expectedConfigurationFingerprint.set(provenance.configurationFingerprint())
		task.maxEvidenceAgeSeconds.set(3600L)

		task.exec()

		assertEquals(evidence, task.internalCommand().report)
	}

	@Test
	void testRegisteredByPlugin() {
		Project project = createProject(testProjectDir)
		project.plugins.apply(DbPlugin)
		def task = project.tasks.named('verifyBulkMigrationEvidenceReport',
				VerifyBulkMigrationEvidenceReportTask).get()
		assertNotNull(task)
		assertFalse(task.evidenceReportFile.isPresent())
		assertFalse(task.operationalReportFile.isPresent())
		assertFalse(task.verificationReportFile.isPresent())
		assertFalse(task.configurationFile.isPresent())
		assertFalse(task.assessmentReportFile.isPresent())
		assertFalse(task.ddlVerificationReportFile.isPresent())
		assertFalse(task.targetValidationReportFile.isPresent())
		assertFalse(task.expectedTargetEnvironmentId.isPresent())
		assertFalse(task.expectedEvidenceReportFingerprint.isPresent())
		assertFalse(task.expectedPlanFingerprint.isPresent())
		assertFalse(task.expectedConfigurationFingerprint.isPresent())
		assertFalse(task.maxEvidenceAgeSeconds.isPresent())
	}

	private static String fingerprint(File file) {
		return 'sha256:' + MessageDigests.SHA256.checksumAsString(file)
	}
}
