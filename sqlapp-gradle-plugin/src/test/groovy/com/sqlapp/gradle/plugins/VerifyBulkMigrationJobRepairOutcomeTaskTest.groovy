/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse

import org.junit.jupiter.api.Test
import org.gradle.testkit.runner.GradleRunner

class VerifyBulkMigrationJobRepairOutcomeTaskTest extends AbstractTaskTest {
	@Test
	void registersAndMapsTheUnifiedRepairOutcomeVerifier() {
		def project = createProject(testProjectDir)
		project.plugins.apply(DbPlugin)
		def task = project.tasks.named('verifyBulkMigrationJobRepairOutcome',
				VerifyBulkMigrationJobRepairOutcomeTask).get()
		assertFalse(task.repairExecutionReportFile.isPresent())
		assertFalse(task.repairFailureReportFile.isPresent())
		assertFalse(task.postRepairVerificationReportFile.isPresent())
		assertFalse(task.expectedRepairExecutionReportFingerprint.isPresent())
		assertFalse(task.expectedApprovedRepairPlanFileFingerprint.isPresent())
		assertFalse(task.maxApprovedRepairPlanAgeSeconds.isPresent())
		assertFalse(task.maxApprovedRepairPlanFileSizeBytes.isPresent())
		assertFalse(task.maxEvidenceFileSizeBytes.isPresent())
		assertFalse(task.expectedRepairFailureReportFingerprint.isPresent())
		assertFalse(task.expectedPostRepairVerificationReportFingerprint.isPresent())
		assertFalse(task.expectedStatus.isPresent())
		assertFalse(task.outcomeReportFile.isPresent())
		assertFalse(task.repairReportDirectory.isPresent())

		def approval = new File(testProjectDir, 'approval.json'); approval.text = '{}'
		def execution = new File(testProjectDir, 'execution.json'); execution.text = '{}'
		def failure = new File(testProjectDir, 'failure.json'); failure.text = '{}'
		def verification = new File(testProjectDir, 'verification.json'); verification.text = '{}'
		def outcome = new File(testProjectDir, 'outcome.json')
		def reports = new File(testProjectDir, 'reports'); reports.mkdirs()
		task.approvedRepairPlanFile.set(approval)
		task.expectedApprovedRepairPlanFileFingerprint.set('sha256:' + 'e' * 64)
		task.maxApprovedRepairPlanAgeSeconds.set(86400L)
		task.maxApprovedRepairPlanFileSizeBytes.set(10485760L)
		task.maxEvidenceFileSizeBytes.set(20971520L)
		task.repairExecutionReportFile.set(execution)
		task.repairFailureReportFile.set(failure)
		task.postRepairVerificationReportFile.set(verification)
		task.expectedRepairExecutionReportFingerprint.set('sha256:' + 'a' * 64)
		task.expectedRepairFailureReportFingerprint.set('sha256:' + 'b' * 64)
		task.expectedPostRepairVerificationReportFingerprint.set('sha256:' + 'c' * 64)
		task.expectedMigrationPlanFingerprint.set('migration')
		task.expectedRepairPlanFingerprint.set('repair')
		task.expectedConfigurationFingerprint.set('sha256:' + 'd' * 64)
		task.expectedStatus.set('SUCCEEDED')
		task.maxEvidenceAgeSeconds.set(60L)
		task.outcomeReportFile.set(outcome)
		task.repairReportDirectory.set(reports)

		def command = task.createCommand()
		task.beforeRun(command)
		assertEquals(approval, command.approvedRepairPlanFile)
		assertEquals('sha256:' + 'e' * 64, command.expectedApprovedRepairPlanFileFingerprint)
		assertEquals(86400L, command.maxApprovedRepairPlanAgeSeconds)
		assertEquals(10485760L, command.maxApprovedRepairPlanFileSizeBytes)
		assertEquals(20971520L, command.maxEvidenceFileSizeBytes)
		assertEquals(execution, command.repairExecutionReportFile)
		assertEquals(failure, command.repairFailureReportFile)
		assertEquals(verification, command.postRepairVerificationReportFile)
		assertEquals('sha256:' + 'a' * 64, command.expectedRepairExecutionReportFingerprint)
		assertEquals('sha256:' + 'b' * 64, command.expectedRepairFailureReportFingerprint)
		assertEquals('sha256:' + 'c' * 64, command.expectedPostRepairVerificationReportFingerprint)
		assertEquals('migration', command.expectedMigrationPlanFingerprint)
		assertEquals('repair', command.expectedRepairPlanFingerprint)
		assertEquals('sha256:' + 'd' * 64, command.expectedConfigurationFingerprint)
		assertEquals('SUCCEEDED', command.expectedStatus)
		assertEquals(60L, command.maxEvidenceAgeSeconds)
		assertEquals(outcome, command.outcomeReportFile)
		assertEquals(reports, command.repairReportDirectory)
	}

	@Test
	void permitsConfiguredOutcomeAlternativesThatDoNotExist() {
		new File(testProjectDir, 'settings.gradle').text = "rootProject.name = 'outcome-test'"
		new File(testProjectDir, 'approval.json').text = '{}'
		new File(testProjectDir, 'build.gradle').text = '''
plugins { id 'com.sqlapp.db' }

verifyBulkMigrationJobRepairOutcome {
    approvedRepairPlanFile = layout.projectDirectory.file('approval.json')
    repairExecutionReportFile = layout.projectDirectory.file('missing-execution.json')
    repairFailureReportFile = layout.projectDirectory.file('missing-failure.json')
    postRepairVerificationReportFile = layout.projectDirectory.file('missing-verification.json')
}
'''

		def result = GradleRunner.create().withProjectDir(testProjectDir).withPluginClasspath()
				.withArguments('verifyBulkMigrationJobRepairOutcome', '--stacktrace').buildAndFail()

		assertFalse(result.output.contains("doesn't exist"))
	}
}
