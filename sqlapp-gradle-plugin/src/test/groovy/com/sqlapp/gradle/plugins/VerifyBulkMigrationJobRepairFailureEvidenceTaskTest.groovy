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
		assertFalse(task.expectedApprovedRepairPlanFileFingerprint.isPresent())
		assertFalse(task.maxApprovedRepairPlanAgeSeconds.isPresent())
		assertFalse(task.maxApprovedRepairPlanFileSizeBytes.isPresent())
		assertFalse(task.maxEvidenceFileSizeBytes.isPresent())
		assertFalse(task.expectedPostRepairVerificationReportFingerprint.isPresent())
		assertFalse(task.expectedMigrationPlanFingerprint.isPresent())
		assertFalse(task.expectedRepairPlanFingerprint.isPresent())
		assertFalse(task.expectedConfigurationFingerprint.isPresent())
		assertFalse(task.maxEvidenceAgeSeconds.isPresent())
		assertFalse(task.postRepairVerificationReportFile.isPresent())
		assertFalse(task.repairReportDirectory.isPresent())

		def failure = new File(testProjectDir, 'failure.json'); failure.text = '{}'
		def approval = new File(testProjectDir, 'approval.json'); approval.text = '{}'
		def verification = new File(testProjectDir, 'verification.json'); verification.text = '{}'
		task.repairFailureReportFile.set(failure)
		task.approvedRepairPlanFile.set(approval)
		task.expectedApprovedRepairPlanFileFingerprint.set('sha256:' + 'd' * 64)
		task.maxApprovedRepairPlanAgeSeconds.set(86400L)
		task.maxApprovedRepairPlanFileSizeBytes.set(10485760L)
		task.maxEvidenceFileSizeBytes.set(20971520L)
		task.postRepairVerificationReportFile.set(verification)
		task.repairReportDirectory.set(testProjectDir)
		task.expectedRepairFailureReportFingerprint.set('sha256:' + 'a' * 64)
		task.expectedPostRepairVerificationReportFingerprint.set('sha256:' + 'c' * 64)
		task.expectedMigrationPlanFingerprint.set('migration')
		task.expectedRepairPlanFingerprint.set('repair')
		task.expectedConfigurationFingerprint.set('sha256:' + 'b' * 64)
		task.maxEvidenceAgeSeconds.set(600L)

		def command = task.createCommand()
		task.beforeRun(command)
		assertEquals(failure, command.repairFailureReportFile)
		assertEquals(approval, command.approvedRepairPlanFile)
		assertEquals('sha256:' + 'd' * 64, command.expectedApprovedRepairPlanFileFingerprint)
		assertEquals(86400L, command.maxApprovedRepairPlanAgeSeconds)
		assertEquals(10485760L, command.maxApprovedRepairPlanFileSizeBytes)
		assertEquals(20971520L, command.maxEvidenceFileSizeBytes)
		assertEquals(verification, command.postRepairVerificationReportFile)
		assertEquals(testProjectDir, command.repairReportDirectory)
		assertEquals('sha256:' + 'a' * 64, command.expectedRepairFailureReportFingerprint)
		assertEquals('sha256:' + 'c' * 64, command.expectedPostRepairVerificationReportFingerprint)
		assertEquals('migration', command.expectedMigrationPlanFingerprint)
		assertEquals('repair', command.expectedRepairPlanFingerprint)
		assertEquals('sha256:' + 'b' * 64, command.expectedConfigurationFingerprint)
		assertEquals(600L, command.maxEvidenceAgeSeconds)
	}
}
