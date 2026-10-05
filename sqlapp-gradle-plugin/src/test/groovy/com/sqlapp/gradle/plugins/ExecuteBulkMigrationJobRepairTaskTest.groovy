/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertNotNull

import org.gradle.api.Project
import org.junit.jupiter.api.Test

class ExecuteBulkMigrationJobRepairTaskTest extends AbstractTaskTest {
	@Test
	void mapsRepairApprovalGatesToTheCommand() {
		Project project = createProject(testProjectDir)
		project.plugins.apply(DbPlugin)

		def task = project.tasks.named('executeBulkMigrationJobRepair',
				ExecuteBulkMigrationJobRepairTask).get()
		assertFalse(task.assessmentReportFile.isPresent())
		assertFalse(task.ddlVerificationReportFile.isPresent())
		assertFalse(task.targetValidationReportFile.isPresent())
		assertFalse(task.expectedTargetValidationReportFingerprint.isPresent())
		assertFalse(task.maxTargetValidationAgeSeconds.isPresent())
		assertFalse(task.targetEnvironmentId.isPresent())
		def configuration = new File(testProjectDir, 'migration.yml')
		def repairPlan = new File(testProjectDir, 'repair-plan.json')
		def postRepairVerification = new File(testProjectDir, 'post-repair-verification.json')
		def assessment = new File(testProjectDir, 'assessment.json')
		def ddlVerification = new File(testProjectDir, 'ddl-verification.json')
		def targetValidation = new File(testProjectDir, 'target-validation.json')
		[configuration, repairPlan, assessment, ddlVerification, targetValidation]*.text = '{}'

		task.configurationFile.set(configuration)
		task.expectedConfigurationFingerprint.set('sha256:' + 'a' * 64)
		task.approvedRepairPlanFile.set(repairPlan)
		task.postRepairVerificationReportFile.set(postRepairVerification)
		task.assessmentReportFile.set(assessment)
		task.ddlVerificationReportFile.set(ddlVerification)
		task.targetValidationReportFile.set(targetValidation)
		task.expectedTargetValidationReportFingerprint.set('sha256:' + 'b' * 64)
		task.maxTargetValidationAgeSeconds.set(600L)
		task.targetEnvironmentId.set('production-oracle')
		task.sourceDataSource.jdbcUrl.set('jdbc:hsqldb:mem:repair-task')

		def command = task.createCommand()
		try {
			task.beforeRun(command)

			assertEquals(configuration, command.configurationFile)
			assertEquals('sha256:' + 'a' * 64, command.expectedConfigurationFingerprint)
			assertEquals(repairPlan, command.approvedRepairPlanFile)
			assertEquals(postRepairVerification, command.postRepairVerificationReportFile)
			assertEquals(assessment, command.assessmentReportFile)
			assertEquals(ddlVerification, command.ddlVerificationReportFile)
			assertEquals(targetValidation, command.targetValidationReportFile)
			assertEquals('sha256:' + 'b' * 64, command.expectedTargetValidationReportFingerprint)
			assertEquals(600L, command.maxTargetValidationAgeSeconds)
			assertEquals('production-oracle', command.targetEnvironmentId)
			assertNotNull(command.sourceDataSource)
		} finally {
			(command.sourceDataSource as AutoCloseable)?.close()
		}
	}
}
