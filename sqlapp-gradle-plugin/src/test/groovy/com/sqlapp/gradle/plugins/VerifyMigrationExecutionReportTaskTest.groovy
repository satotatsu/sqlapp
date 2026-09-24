/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins

import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertNotNull

import org.gradle.api.Project
import org.junit.jupiter.api.Test

class VerifyMigrationExecutionReportTaskTest extends AbstractTaskTest {
	@Test
	void testRegisteredByPlugin() {
		Project project = createProject(testProjectDir)
		project.plugins.apply(DbPlugin)
		def task = project.tasks.named("verifyMigrationExecutionReport",
				VerifyMigrationExecutionReportTask).get()
		assertNotNull(task)
		assertFalse(task.reportFile.isPresent())
		assertFalse(task.expectedReportFingerprint.isPresent())
		assertFalse(task.expectedPlanFingerprint.isPresent())
		assertFalse(task.expectedDatabaseConnectionFingerprint.isPresent())
		assertFalse(task.requireSuccessful.get())
		assertFalse(task.requireAllSelectedCommitted.get())
	}
}
