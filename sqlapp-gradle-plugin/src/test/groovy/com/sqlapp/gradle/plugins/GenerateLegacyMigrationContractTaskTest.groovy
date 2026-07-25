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

class GenerateLegacyMigrationContractTaskTest extends AbstractTaskTest {

	@Test
	void testRegisteredByPlugin() {
		Project project = createProject(testProjectDir)
		project.plugins.apply(DbPlugin)

		GenerateLegacyMigrationContractTask task = project.tasks.named(
				"generateLegacyMigrationContract", GenerateLegacyMigrationContractTask).get()
		assertNotNull(task)
		assertEquals("UTF-8", task.encoding.get())
		assertEquals(",", task.delimiter.get())
		assertTrue(task.header.get())
		assertEquals("CRLF", task.recordSeparator.get())
	}
}
