/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins

import static org.junit.jupiter.api.Assertions.*

import org.junit.jupiter.api.Test
import com.sqlapp.data.schemas.migration.assessment.MigrationAssessment
import com.sqlapp.data.schemas.Schema

class AssessMigrationTaskTest extends AbstractTaskTest {
	@Test
	void registersTaskAndMapsAllProperties() {
		def project = createProject(testProjectDir)
		project.plugins.apply(DbPlugin)
		def task = project.tasks.named('assessMigration', AssessMigrationTask).get()
		assertTrue(task.failOnBlockers.get())
		assertFalse(task.scanCharacterData.get())
		assertFalse(task.targetVersion.isPresent())
		assertFalse(task.targetCharacterSet.isPresent())
		assertFalse(task.migrationMethod.isPresent())
		def input = new File(testProjectDir, 'schema.xml')
		def output = new File(testProjectDir, 'report.json')
		task.schemaFile.set(input)
		task.outputFile.set(output)
		task.targetVersion.set('26ai')
		task.targetCharacterSet.set('AL32UTF8')
		task.migrationMethod.set(MigrationAssessment.Method.LOGICAL_MIGRATION)
		task.failOnBlockers.set(false)
		task.scanCharacterData.set(true)
		def command = task.createCommand()
		task.beforeRun(command)
		assertEquals(input, command.schemaFile)
		assertEquals(output, command.outputFile)
		assertEquals('26ai', command.targetVersion)
		assertEquals('AL32UTF8', command.targetCharacterSet)
		assertEquals(MigrationAssessment.Method.LOGICAL_MIGRATION, command.migrationMethod)
		assertFalse(command.failOnBlockers)
		assertTrue(command.scanCharacterData)
		assertNull(command.dataSource)
	}

	@Test
	void executesOfflineWithDialectServiceProvider() {
		def project = createProject(testProjectDir)
		project.plugins.apply(DbPlugin)
		def task = project.tasks.named('assessMigration', AssessMigrationTask).get()
		def input = new File(testProjectDir, 'oracle.xml')
		new Schema('APP').setProductName('Oracle').setProductMajorVersion(10).writeXml(input)
		def output = new File(testProjectDir, 'assessment.json')
		task.schemaFile.set(input)
		task.outputFile.set(output)
		task.targetVersion.set('26ai')
		task.migrationMethod.set(MigrationAssessment.Method.LOGICAL_MIGRATION)
		task.exec()
		assertTrue(output.getText('UTF-8').contains('REVIEW_REQUIRED'))
		assertEquals('REVIEW_REQUIRED', task.internalCommand().report.status())
		assertNull(task.internalCommand().targetCharacterSet)
		task.targetCharacterSet.set('AL32UTF8')
		task.exec()
		assertEquals('AL32UTF8', task.internalCommand().report.targetCharacterSet())
		assertTrue(output.getText('UTF-8').contains('oracle.charset.source-unknown'))
	}
}
