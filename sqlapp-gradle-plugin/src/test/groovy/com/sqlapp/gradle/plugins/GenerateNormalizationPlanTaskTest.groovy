/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-gradle-plugin.
 */
package com.sqlapp.gradle.plugins

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNotNull
import static org.junit.jupiter.api.Assertions.assertTrue

import org.gradle.api.Project
import org.junit.jupiter.api.Test

class GenerateNormalizationPlanTaskTest extends AbstractTaskTest {

	@Test
	void testRegisteredByPlugin() {
		Project project = createProject(testProjectDir)
		project.plugins.apply(DbPlugin)

		GenerateNormalizationPlanTask task = project.tasks.named(
				"generateNormalizationPlan", GenerateNormalizationPlanTask).get()
		assertNotNull(task)
		assertEquals(2, task.minimumColumnCount.get())
		assertEquals(20L, task.variableCharacterMinimumLength.get())
		assertTrue(task.previewSchemaEnabled.get())
	}
}
