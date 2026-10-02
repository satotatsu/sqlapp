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
		assertFalse(task.assessmentReportFile.isPresent())
		assertFalse(task.ddlVerificationReportFile.isPresent())
		assertNotNull(task.sourceDataSource)
		assertFalse(task.listener.isPresent())
		assertFalse(task.chunkListener.isPresent())
		assertFalse(task.leaseConfiguration.isPresent())

		task.expectedConfigurationFingerprint.set('sha256:' + 'a' * 64)
		def assessment = new File(testProjectDir, 'assessment.json')
		def ddlVerification = new File(testProjectDir, 'ddl-verification.json')
		assessment.text = '{}'
		ddlVerification.text = '{}'
		task.assessmentReportFile.set(assessment)
		task.ddlVerificationReportFile.set(ddlVerification)
		def command = task.createCommand()
		task.beforeRun(command)
		assertEquals('sha256:' + 'a' * 64, command.expectedConfigurationFingerprint)
		assertEquals(assessment, command.assessmentReportFile)
		assertEquals(ddlVerification, command.ddlVerificationReportFile)
	}
}
