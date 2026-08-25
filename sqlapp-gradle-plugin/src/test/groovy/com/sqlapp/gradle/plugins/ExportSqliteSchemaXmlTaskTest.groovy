package com.sqlapp.gradle.plugins

import static org.junit.jupiter.api.Assertions.*

import java.sql.DriverManager

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

class ExportSqliteSchemaXmlTaskTest extends AbstractTaskTest {
	@TempDir File outputDirectory

	@Test
	void exportsSqliteFileWithRows() {
		File input = new File(outputDirectory, '日本語.sqlite3')
		DriverManager.getConnection("jdbc:sqlite:${input.absolutePath}").withCloseable { connection ->
			connection.createStatement().withCloseable { statement ->
				statement.execute('CREATE TABLE "顧客" ("顧客ID" INTEGER PRIMARY KEY, "名前" TEXT NOT NULL)')
				statement.execute("INSERT INTO \"顧客\" VALUES (1, '山田')")
			}
		}
		File output = new File(outputDirectory, 'schema.xml')
		def project = createProject(testProjectDir, { p -> })
		def task = project.tasks.register('exportSqlite', ExportSqliteSchemaXmlTask) {
			inputFile = input
			outputFile = output
			dumpRows = true
		}.get()

		task.exec()
		String xml = output.getText('UTF-8')
		assertTrue(xml.contains('name="顧客"'))
		assertTrue(xml.contains('山田'))
	}

	@Test
	void canExportMetadataOnly() {
		File input = new File(outputDirectory, 'metadata.db')
		DriverManager.getConnection("jdbc:sqlite:${input.absolutePath}").withCloseable { connection ->
			connection.createStatement().withCloseable { statement ->
				statement.execute('CREATE TABLE sample (id INTEGER PRIMARY KEY, value TEXT)')
				statement.execute("INSERT INTO sample VALUES (1, 'hidden-value')")
			}
		}
		File output = new File(outputDirectory, 'metadata.xml')
		def project = createProject(testProjectDir, { p -> })
		def task = project.tasks.register('exportSqliteMetadata', ExportSqliteSchemaXmlTask) {
			inputFile = input
			outputFile = output
			dumpRows = false
		}.get()

		task.exec()
		String xml = output.getText('UTF-8')
		assertTrue(xml.contains('name="sample"'))
		assertFalse(xml.contains('hidden-value'))
		assertFalse(xml.contains('<rows>'))
	}
}
