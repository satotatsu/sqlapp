package com.sqlapp.gradle.plugins

import static org.junit.jupiter.api.Assertions.*

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import io.github.spannm.jackcess.ColumnBuilder
import io.github.spannm.jackcess.Database
import io.github.spannm.jackcess.DatabaseBuilder
import io.github.spannm.jackcess.TableBuilder

class ExportAccessSchemaXmlTaskTest extends AbstractTaskTest {
	@TempDir File outputDirectory

	@Test
	void exportsAccessFile() {
		File input = new File(outputDirectory, '日本語.accdb')
		Database database = DatabaseBuilder.create(Database.FileFormat.V2010, input)
		try {
			def table = new TableBuilder('顧客')
				.addColumn(new ColumnBuilder('名前', io.github.spannm.jackcess.DataType.TEXT))
				.toTable(database)
			table.addRow('山田')
		} finally {
			database.close()
		}
		File output = new File(outputDirectory, 'schema.xml')
		def project = createProject(testProjectDir, { p -> })
		def task = project.tasks.register('exportAccess', ExportAccessSchemaXmlTask) {
			inputFile = input
			outputFile = output
			schemaName = 'public'
			dumpRows = true
		}.get()
		task.exec()
		assertTrue(output.getText('UTF-8').contains('name="顧客"'))
		assertTrue(output.getText('UTF-8').contains('山田'))
		assertEquals('public', task.internalCommand().schemaName)
	}

	@Test
	void canExcludeRowsFromXml() {
		File input = new File(outputDirectory, 'metadata-only.accdb')
		Database database = DatabaseBuilder.create(Database.FileFormat.V2010, input)
		try {
			def table = new TableBuilder('顧客')
				.addColumn(new ColumnBuilder('名前', io.github.spannm.jackcess.DataType.TEXT))
				.toTable(database)
			table.addRow('出力しない値')
		} finally {
			database.close()
		}
		File output = new File(outputDirectory, 'metadata-only.xml')
		def project = createProject(testProjectDir, { p -> })
		def task = project.tasks.register('exportMetadata', ExportAccessSchemaXmlTask) {
			inputFile = input
			outputFile = output
			dumpRows = false
		}.get()

		task.exec()
		String xml = output.getText('UTF-8')
		assertTrue(xml.contains('name="顧客"'))
		assertFalse(xml.contains('出力しない値'))
		assertFalse(xml.contains('<rows>'))
	}
}
