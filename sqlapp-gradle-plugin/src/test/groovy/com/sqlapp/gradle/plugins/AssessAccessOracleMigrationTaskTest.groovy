/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins

import static org.junit.jupiter.api.Assertions.*

import org.junit.jupiter.api.Test
import io.github.spannm.jackcess.Database
import io.github.spannm.jackcess.DatabaseBuilder
import io.github.spannm.jackcess.ColumnBuilder
import io.github.spannm.jackcess.DataType
import io.github.spannm.jackcess.TableBuilder

class AssessAccessOracleMigrationTaskTest extends AbstractTaskTest {
	@Test
	void registersMapsAndExecutesOffline() {
		def project = createProject(testProjectDir)
		project.plugins.apply(DbPlugin)
		def task = project.tasks.named('assessAccessOracleMigration', AssessAccessOracleMigrationTask).get()
		assertTrue(task.failOnBlockers.get())
		assertFalse(task.targetVersion.isPresent())
		def input = new File(testProjectDir, 'source.accdb')
		def database = DatabaseBuilder.create(Database.FileFormat.V2010, input)
		try {
			new TableBuilder('T').addColumn(new ColumnBuilder('C', DataType.TEXT)).toTable(database)
		} finally {
			database.close()
		}
		def output = new File(testProjectDir, 'assessment.json')
		task.inputFile.set(input)
		task.outputFile.set(output)
		task.targetVersion.set('19c')
		task.failOnBlockers.set(false)
		def command = task.createCommand()
		task.beforeRun(command)
		assertEquals(input, command.inputFile)
		assertEquals(output, command.outputFile)
		assertEquals('19c', command.targetVersion)
		assertFalse(command.failOnBlockers)
		task.exec()
		assertEquals('Oracle', task.internalCommand().report.targetProduct())
		assertTrue(output.getText('UTF-8').contains('access.data-not-scanned'))
	}
}
