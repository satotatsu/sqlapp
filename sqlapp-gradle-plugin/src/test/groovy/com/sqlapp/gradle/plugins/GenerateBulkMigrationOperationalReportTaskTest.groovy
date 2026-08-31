/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins

import static org.junit.jupiter.api.Assertions.assertNotNull
import static org.junit.jupiter.api.Assertions.assertFalse

import org.gradle.api.Project
import org.junit.jupiter.api.Test

class GenerateBulkMigrationOperationalReportTaskTest extends AbstractTaskTest {
	@Test
	void testRegisteredByPlugin() {
		Project project = createProject(testProjectDir)
		project.plugins.apply(DbPlugin)

		def task = project.tasks.named("generateBulkMigrationOperationalReport",
				GenerateBulkMigrationOperationalReportTask).get()
		assertNotNull(task)
		assertFalse(task.plan.isPresent())
		assertFalse(task.status.isPresent())
		assertFalse(task.maintenanceState.isPresent())
		assertFalse(task.progress.isPresent())
	}
}
