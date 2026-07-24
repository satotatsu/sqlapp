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

class GenerateLegacyRdbLoaderTaskTest extends AbstractTaskTest {

	@Test
	void testRegisteredByPlugin() {
		Project project = createProject(testProjectDir)
		project.plugins.apply(DbPlugin)

		GenerateLegacyRdbLoaderTask task = project.tasks.named(
				"generateLegacyRdbLoader", GenerateLegacyRdbLoaderTask).get()
		assertNotNull(task)
		assertEquals("INSERT_IGNORE", task.tableOperationMode.get())
		assertEquals(500, task.rootBatchSize.get())
		assertEquals(500L, task.commitEveryRootBatches.get())
		assertTrue(task.deleteCommittedRoots.get())
		assertEquals("TMP_", task.stagingTablePrefix.get())
	}
}
