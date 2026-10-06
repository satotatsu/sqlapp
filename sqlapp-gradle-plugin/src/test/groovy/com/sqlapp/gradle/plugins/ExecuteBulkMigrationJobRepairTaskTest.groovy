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
		assertFalse(task.maxApprovalArtifactFileSizeBytes.isPresent())
		assertFalse(task.targetValidationReportFile.isPresent())
		assertFalse(task.expectedTargetValidationReportFingerprint.isPresent())
		assertFalse(task.expectedApprovedRepairPlanFileFingerprint.isPresent())
		assertFalse(task.maxApprovedRepairPlanAgeSeconds.isPresent())
		assertFalse(task.maxApprovedRepairPlanFileSizeBytes.isPresent())
		assertFalse(task.maxEvidenceFileSizeBytes.isPresent())
		assertFalse(task.maxTargetValidationAgeSeconds.isPresent())
		assertFalse(task.maxConfigurationFileSizeBytes.isPresent())
		assertFalse(task.maxSchemaFileSizeBytes.isPresent())
		assertFalse(task.maxTargetValidationReportFileSizeBytes.isPresent())
		assertFalse(task.targetEnvironmentId.isPresent())
		assertFalse(task.repairOutcomeReportFile.isPresent())
		assertFalse(task.repairReportDirectory.isPresent())
		def configuration = new File(testProjectDir, 'migration.yml')
		def repairPlan = new File(testProjectDir, 'repair-plan.json')
		def repairExecution = new File(testProjectDir, 'repair-execution.json')
		def repairFailure = new File(testProjectDir, 'repair-failure.json')
		def postRepairVerification = new File(testProjectDir, 'post-repair-verification.json')
		def repairOutcome = new File(testProjectDir, 'repair-outcome.json')
		def repairReports = new File(testProjectDir, 'repair-reports')
		def assessment = new File(testProjectDir, 'assessment.json')
		def ddlVerification = new File(testProjectDir, 'ddl-verification.json')
		def targetValidation = new File(testProjectDir, 'target-validation.json')
		[configuration, repairPlan, assessment, ddlVerification, targetValidation]*.text = '{}'

		task.configurationFile.set(configuration)
		task.expectedConfigurationFingerprint.set('sha256:' + 'a' * 64)
		task.maxConfigurationFileSizeBytes.set(1048576L)
		task.maxSchemaFileSizeBytes.set(4194304L)
		task.approvedRepairPlanFile.set(repairPlan)
		task.expectedApprovedRepairPlanFileFingerprint.set('sha256:' + 'c' * 64)
		task.maxApprovedRepairPlanAgeSeconds.set(86400L)
		task.maxApprovedRepairPlanFileSizeBytes.set(10485760L)
		task.maxEvidenceFileSizeBytes.set(20971520L)
		task.repairExecutionReportFile.set(repairExecution)
		task.repairFailureReportFile.set(repairFailure)
		task.postRepairVerificationReportFile.set(postRepairVerification)
		task.repairOutcomeReportFile.set(repairOutcome)
		task.repairReportDirectory.set(repairReports)
		task.assessmentReportFile.set(assessment)
		task.ddlVerificationReportFile.set(ddlVerification)
		task.maxApprovalArtifactFileSizeBytes.set(8388608L)
		task.targetValidationReportFile.set(targetValidation)
		task.expectedTargetValidationReportFingerprint.set('sha256:' + 'b' * 64)
		task.maxTargetValidationAgeSeconds.set(600L)
		task.maxTargetValidationReportFileSizeBytes.set(2097152L)
		task.targetEnvironmentId.set('production-oracle')
		task.sourceDataSource.jdbcUrl.set('jdbc:hsqldb:mem:repair-task')

		def command = task.createCommand()
		try {
			task.beforeRun(command)

			assertEquals(configuration, command.configurationFile)
			assertEquals('sha256:' + 'a' * 64, command.expectedConfigurationFingerprint)
			assertEquals(1048576L, command.maxConfigurationFileSizeBytes)
			assertEquals(4194304L, command.maxSchemaFileSizeBytes)
			assertEquals(repairPlan, command.approvedRepairPlanFile)
			assertEquals('sha256:' + 'c' * 64, command.expectedApprovedRepairPlanFileFingerprint)
			assertEquals(86400L, command.maxApprovedRepairPlanAgeSeconds)
			assertEquals(10485760L, command.maxApprovedRepairPlanFileSizeBytes)
			assertEquals(20971520L, command.maxEvidenceFileSizeBytes)
			assertEquals(repairExecution, command.repairExecutionReportFile)
			assertEquals(repairFailure, command.repairFailureReportFile)
			assertEquals(postRepairVerification, command.postRepairVerificationReportFile)
			assertEquals(repairOutcome, command.repairOutcomeReportFile)
			assertEquals(repairReports, command.repairReportDirectory)
			assertEquals(assessment, command.assessmentReportFile)
			assertEquals(ddlVerification, command.ddlVerificationReportFile)
			assertEquals(8388608L, command.maxApprovalArtifactFileSizeBytes)
			assertEquals(targetValidation, command.targetValidationReportFile)
			assertEquals('sha256:' + 'b' * 64, command.expectedTargetValidationReportFingerprint)
			assertEquals(600L, command.maxTargetValidationAgeSeconds)
			assertEquals(2097152L, command.maxTargetValidationReportFileSizeBytes)
			assertEquals('production-oracle', command.targetEnvironmentId)
			assertNotNull(command.sourceDataSource)
		} finally {
			(command.sourceDataSource as AutoCloseable)?.close()
		}
	}
}
