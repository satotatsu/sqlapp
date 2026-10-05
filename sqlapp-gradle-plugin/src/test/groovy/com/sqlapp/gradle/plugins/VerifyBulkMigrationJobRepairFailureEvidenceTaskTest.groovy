/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse

import org.junit.jupiter.api.Test

class VerifyBulkMigrationJobRepairFailureEvidenceTaskTest extends AbstractTaskTest {
	@Test
	void registersAndMapsFileOnlyRepairFailureEvidenceVerification() {
		def project = createProject(testProjectDir)
		project.plugins.apply(DbPlugin)
		def task = project.tasks.named('verifyBulkMigrationJobRepairFailureEvidence',
				VerifyBulkMigrationJobRepairFailureEvidenceTask).get()
		assertFalse(task.expectedRepairFailureReportFingerprint.isPresent())
		assertFalse(task.expectedMigrationPlanFingerprint.isPresent())
		assertFalse(task.expectedRepairPlanFingerprint.isPresent())
		assertFalse(task.expectedConfigurationFingerprint.isPresent())
		assertFalse(task.maxEvidenceAgeSeconds.isPresent())
		assertFalse(task.postRepairVerificationReportFile.isPresent())

		def failure = new File(testProjectDir, 'failure.json'); failure.text = '{}'
		def approval = new File(testProjectDir, 'approval.json'); approval.text = '{}'
		def verification = new File(testProjectDir, 'verification.json'); verification.text = '{}'
		task.repairFailureReportFile.set(failure)
		task.approvedRepairPlanFile.set(approval)
		task.postRepairVerificationReportFile.set(verification)
		task.expectedRepairFailureReportFingerprint.set('sha256:' + 'a' * 64)
		task.expectedMigrationPlanFingerprint.set('migration')
		task.expectedRepairPlanFingerprint.set('repair')
		task.expectedConfigurationFingerprint.set('sha256:' + 'b' * 64)
		task.maxEvidenceAgeSeconds.set(600L)

		def command = task.createCommand()
		task.beforeRun(command)
		assertEquals(failure, command.repairFailureReportFile)
		assertEquals(approval, command.approvedRepairPlanFile)
		assertEquals(verification, command.postRepairVerificationReportFile)
		assertEquals('sha256:' + 'a' * 64, command.expectedRepairFailureReportFingerprint)
		assertEquals('migration', command.expectedMigrationPlanFingerprint)
		assertEquals('repair', command.expectedRepairPlanFingerprint)
		assertEquals('sha256:' + 'b' * 64, command.expectedConfigurationFingerprint)
		assertEquals(600L, command.maxEvidenceAgeSeconds)
	}
}
