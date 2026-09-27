/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.gradle.plugins

import static org.junit.jupiter.api.Assertions.*

import org.junit.jupiter.api.Test
import io.github.spannm.jackcess.Database
import io.github.spannm.jackcess.DatabaseBuilder
import io.github.spannm.jackcess.ColumnBuilder
import io.github.spannm.jackcess.DataType
import io.github.spannm.jackcess.TableBuilder

class AssessDatabaseMigrationTaskTest extends AbstractTaskTest {
	@Test
	void registersMapsAndExecutesOffline() {
		def project = createProject(testProjectDir)
		project.plugins.apply(DbPlugin)
		def task = project.tasks.named('assessDatabaseMigration', AssessDatabaseMigrationTask).get()
		assertTrue(task.failOnBlockers.get())
		assertFalse(task.scanData.get())
		assertFalse(task.targetVersion.isPresent())
		assertFalse(task.targetDatabase.isPresent())
		assertFalse(task.htmlOutputFile.isPresent())
		assertFalse(task.mappingFile.isPresent())
		def input = new File(testProjectDir, 'source.accdb')
		def database = DatabaseBuilder.create(Database.FileFormat.V2010, input)
		try {
			new TableBuilder('T').addColumn(new ColumnBuilder('C', DataType.TEXT)).toTable(database)
		} finally {
			database.close()
		}
		def output = new File(testProjectDir, 'assessment.json')
		def htmlOutput = new File(testProjectDir, 'assessment.html')
		task.inputFile.set(input)
		task.outputFile.set(output)
		task.htmlOutputFile.set(htmlOutput)
		task.targetVersion.set('19c')
		task.targetDatabase.set('oracle')
		task.failOnBlockers.set(false)
		def command = task.createCommand()
		task.beforeRun(command)
		assertEquals(input, command.inputFile)
		assertEquals(output, command.outputFile)
		assertEquals(htmlOutput, command.htmlOutputFile)
		assertEquals('19c', command.targetVersion)
		assertEquals('oracle', command.targetDatabase)
		assertFalse(command.failOnBlockers)
		task.exec()
		assertEquals('Oracle', task.internalCommand().report.targetProduct())
		assertTrue(output.getText('UTF-8').contains('access.data-not-scanned'))
		assertTrue(htmlOutput.getText('UTF-8').contains('Database migration assessment'))
		task.targetDatabase.set('sqlserver')
		task.targetVersion.set('2022')
		task.exec()
		assertEquals('Microsoft SQL Server', task.internalCommand().report.targetProduct())
		assertTrue(output.getText('UTF-8').contains('access.sqlserver.type'))
		assertFalse(output.getText('UTF-8').contains('access.oracle.'))
		task.scanData.set(true)
		task.exec()
		assertTrue(task.internalCommand().scanData)
		assertTrue(task.internalCommand().report.dataScanned())
		assertEquals(2, task.internalCommand().report.formatVersion())
		assertEquals(0, task.internalCommand().report.dataProfile().tables().get(0).rowCount())
		assertFalse(output.getText('UTF-8').contains('access.data-not-scanned'))
		def mapping = new File(testProjectDir, 'mapping.yaml')
		mapping.setText("""format: sqlapp-database-migration-mapping
version: 1
sourceFingerprint: ${task.internalCommand().report.sourceFingerprint()}
targetDatabase: sqlserver
targetVersion: '2022'
tables:
  - sourceTable: T
    targetTable: TARGET_T
    columns:
      - sourceColumn: C
        targetColumn: TARGET_C
        targetType: nvarchar(20)
""", 'UTF-8')
		task.mappingFile.set(mapping)
		task.exec()
		assertEquals(mapping, task.internalCommand().mappingFile)
		assertEquals(3, task.internalCommand().report.formatVersion())
		assertEquals('TARGET_T', task.internalCommand().report.targetMapping().tables().get(0).targetTable())
	}
}
