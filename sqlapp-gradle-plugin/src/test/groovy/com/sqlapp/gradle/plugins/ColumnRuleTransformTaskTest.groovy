/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-gradle-plugin.
 */
package com.sqlapp.gradle.plugins

import static org.junit.jupiter.api.Assertions.assertNotNull
import static org.junit.jupiter.api.Assertions.assertTrue

import org.gradle.api.Project
import org.junit.jupiter.api.Test

class ColumnRuleTransformTaskTest extends AbstractTaskTest {

	@Test
	void testRegisteredByPlugin() {
		Project project = createProject(testProjectDir)
		project.plugins.apply(DbPlugin)

		ColumnRuleTransformTask task = project.tasks.named("columnRuleTransform", ColumnRuleTransformTask).get()
		assertNotNull(task)
		assertTrue(task.migrationMappingEnabled.get())
	}
}
