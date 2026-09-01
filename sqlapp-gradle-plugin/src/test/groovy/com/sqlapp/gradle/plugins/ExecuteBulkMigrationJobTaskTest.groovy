/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins

import static org.junit.jupiter.api.Assertions.assertFalse
import static org.junit.jupiter.api.Assertions.assertNotNull

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
		assertFalse(task.listener.isPresent())
		assertFalse(task.chunkListener.isPresent())
		assertFalse(task.leaseConfiguration.isPresent())
	}
}
