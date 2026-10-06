/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins

import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertNotNull
import static org.junit.jupiter.api.Assertions.assertEquals

import org.gradle.api.Project
import org.junit.jupiter.api.Test

class ExecuteBulkMigrationJobTaskTest extends AbstractTaskTest {
	@Test
	void testRegisteredByPlugin() {
		Project project = createProject(testProjectDir)
		project.plugins.apply(DbPlugin)

		def task = project.tasks.named("executeBulkMigrationJob",
				ExecuteBulkMigrationJobTask).get()
		assertNotNull(task)
		assertFalse(task.plan.isPresent())
		assertFalse(task.configurationFile.isPresent())
		assertFalse(task.expectedConfigurationFingerprint.isPresent())
		assertFalse(task.maxConfigurationFileSizeBytes.isPresent())
		assertFalse(task.assessmentReportFile.isPresent())
		assertFalse(task.ddlVerificationReportFile.isPresent())
		assertFalse(task.targetValidationReportFile.isPresent())
		assertFalse(task.expectedTargetValidationReportFingerprint.isPresent())
		assertFalse(task.maxTargetValidationAgeSeconds.isPresent())
		assertFalse(task.maxTargetValidationReportFileSizeBytes.isPresent())
		assertFalse(task.targetEnvironmentId.isPresent())
		assertNotNull(task.sourceDataSource)
		assertFalse(task.listener.isPresent())
		assertFalse(task.chunkListener.isPresent())
		assertFalse(task.leaseConfiguration.isPresent())

		task.expectedConfigurationFingerprint.set('sha256:' + 'a' * 64)
		task.maxConfigurationFileSizeBytes.set(1048576L)
		def assessment = new File(testProjectDir, 'assessment.json')
		def ddlVerification = new File(testProjectDir, 'ddl-verification.json')
		def targetValidation = new File(testProjectDir, 'target-validation.json')
		assessment.text = '{}'
		ddlVerification.text = '{}'
		targetValidation.text = '{}'
		task.assessmentReportFile.set(assessment)
		task.ddlVerificationReportFile.set(ddlVerification)
		task.targetValidationReportFile.set(targetValidation)
		task.expectedTargetValidationReportFingerprint.set('sha256:' + 'b' * 64)
		task.maxTargetValidationAgeSeconds.set(600L)
		task.maxTargetValidationReportFileSizeBytes.set(2097152L)
		task.targetEnvironmentId.set('production-oracle')
		def command = task.createCommand()
		task.beforeRun(command)
		assertEquals('sha256:' + 'a' * 64, command.expectedConfigurationFingerprint)
		assertEquals(1048576L, command.maxConfigurationFileSizeBytes)
		assertEquals(assessment, command.assessmentReportFile)
		assertEquals(ddlVerification, command.ddlVerificationReportFile)
		assertEquals(targetValidation, command.targetValidationReportFile)
		assertEquals('sha256:' + 'b' * 64, command.expectedTargetValidationReportFingerprint)
		assertEquals(600L, command.maxTargetValidationAgeSeconds)
		assertEquals(2097152L, command.maxTargetValidationReportFileSizeBytes)
		assertEquals('production-oracle', command.targetEnvironmentId)
	}
}
