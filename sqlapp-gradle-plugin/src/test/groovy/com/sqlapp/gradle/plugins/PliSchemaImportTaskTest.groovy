/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-gradle-plugin.
 */
package com.sqlapp.gradle.plugins

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNotNull

import org.gradle.api.Project
import org.junit.jupiter.api.Test

class PliSchemaImportTaskTest extends AbstractTaskTest {

	@Test
	void testRegisteredByPlugin() {
		Project project = createProject(testProjectDir)
		project.plugins.apply(DbPlugin)

		PliSchemaImportTask task = project.tasks.named("pliSchemaImport", PliSchemaImportTask).get()
		assertNotNull(task)
		assertEquals("UTF-8", task.encoding.get())
	}
}
