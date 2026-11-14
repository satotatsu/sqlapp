/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertTrue

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

/**
 * Runs a file-based task twice with the configuration cache: the first run
 * stores the cache without problems and the second run reuses it.
 */
class ConfigurationCacheTest {
	@TempDir
	File testProjectDir

	@Test
	void diffSchemaXmlStoresAndReusesConfigurationCache() {
		for (String name : ['schema.xml', 'schema2.xml']) {
			new File(testProjectDir, name).bytes = new File("./src/test/resources/diffschemaxml/${name}").bytes
		}
		new File(testProjectDir, 'settings.gradle').text = "rootProject.name = 'cc-test'\n"
		new File(testProjectDir, 'build.gradle').text = """plugins { id 'com.sqlapp.db' }
tasks.register('diffXml', com.sqlapp.gradle.plugins.DiffSchemaXmlTask) {
    targetFile = file('schema.xml')
    originalFile = file('schema2.xml')
}
"""
		def runner = GradleRunner.create().withProjectDir(testProjectDir).withPluginClasspath()
				.withArguments('diffXml', '--configuration-cache', '--offline', '--stacktrace')
		def first = runner.build()
		assertEquals(TaskOutcome.SUCCESS, first.task(':diffXml').outcome)
		assertTrue(first.output.contains('Configuration cache entry stored'), first.output)
		def second = runner.build()
		assertEquals(TaskOutcome.SUCCESS, second.task(':diffXml').outcome)
		assertTrue(second.output.contains('Reusing configuration cache'), second.output)
	}
}
