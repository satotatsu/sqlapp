/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse

import org.junit.jupiter.api.Test

class VerifyBulkMigrationJobRepairOutcomeReportTaskTest extends AbstractTaskTest {
	@Test
	void registersAndMapsSavedOutcomeVerification() {
		def project = createProject(testProjectDir)
		project.plugins.apply(DbPlugin)
		def task = project.tasks.named('verifyBulkMigrationJobRepairOutcomeReport',
				VerifyBulkMigrationJobRepairOutcomeReportTask).get()
		assertFalse(task.repairExecutionReportFile.isPresent())
		assertFalse(task.repairFailureReportFile.isPresent())
		assertFalse(task.postRepairVerificationReportFile.isPresent())
		assertFalse(task.expectedOutcomeReportFingerprint.isPresent())
		assertFalse(task.expectedApprovedRepairPlanFileFingerprint.isPresent())
		assertFalse(task.maxApprovedRepairPlanAgeSeconds.isPresent())
		assertFalse(task.maxApprovedRepairPlanFileSizeBytes.isPresent())
		assertFalse(task.maxEvidenceFileSizeBytes.isPresent())
		assertFalse(task.repairReportDirectory.isPresent())

		def outcome = new File(testProjectDir, 'outcome.json'); outcome.text = '{}'
		def approval = new File(testProjectDir, 'approval.json'); approval.text = '{}'
		def execution = new File(testProjectDir, 'execution.json'); execution.text = '{}'
		def failure = new File(testProjectDir, 'failure.json'); failure.text = '{}'
		def verification = new File(testProjectDir, 'verification.json'); verification.text = '{}'
		def reports = new File(testProjectDir, 'reports'); reports.mkdirs()
		task.outcomeReportFile.set(outcome)
		task.approvedRepairPlanFile.set(approval)
		task.expectedApprovedRepairPlanFileFingerprint.set('sha256:' + 'c' * 64)
		task.maxApprovedRepairPlanAgeSeconds.set(86400L)
		task.maxApprovedRepairPlanFileSizeBytes.set(10485760L)
		task.maxEvidenceFileSizeBytes.set(20971520L)
		task.repairExecutionReportFile.set(execution)
		task.repairFailureReportFile.set(failure)
		task.postRepairVerificationReportFile.set(verification)
		task.repairReportDirectory.set(reports)
		task.expectedOutcomeReportFingerprint.set('sha256:' + 'a' * 64)
		task.expectedStatus.set('SUCCEEDED')
		task.expectedMigrationPlanFingerprint.set('migration')
		task.expectedRepairPlanFingerprint.set('repair')
		task.expectedConfigurationFingerprint.set('sha256:' + 'b' * 64)
		task.maxEvidenceAgeSeconds.set(60L)

		def command = task.createCommand()
		task.beforeRun(command)
		assertEquals(outcome, command.outcomeReportFile)
		assertEquals(approval, command.approvedRepairPlanFile)
		assertEquals('sha256:' + 'c' * 64, command.expectedApprovedRepairPlanFileFingerprint)
		assertEquals(86400L, command.maxApprovedRepairPlanAgeSeconds)
		assertEquals(10485760L, command.maxApprovedRepairPlanFileSizeBytes)
		assertEquals(20971520L, command.maxEvidenceFileSizeBytes)
		assertEquals(execution, command.repairExecutionReportFile)
		assertEquals(failure, command.repairFailureReportFile)
		assertEquals(verification, command.postRepairVerificationReportFile)
		assertEquals(reports, command.repairReportDirectory)
		assertEquals('sha256:' + 'a' * 64, command.expectedOutcomeReportFingerprint)
		assertEquals('SUCCEEDED', command.expectedStatus)
		assertEquals('migration', command.expectedMigrationPlanFingerprint)
		assertEquals('repair', command.expectedRepairPlanFingerprint)
		assertEquals('sha256:' + 'b' * 64, command.expectedConfigurationFingerprint)
		assertEquals(60L, command.maxEvidenceAgeSeconds)
	}
}
