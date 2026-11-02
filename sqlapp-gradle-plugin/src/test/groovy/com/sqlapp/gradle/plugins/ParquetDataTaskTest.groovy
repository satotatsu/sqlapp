/* Copyright (C) 2026 Tatsuo Satoh */
package com.sqlapp.gradle.plugins

import org.junit.jupiter.api.Test
import com.sqlapp.data.schemas.rowiterator.DataFormat

import static org.junit.jupiter.api.Assertions.*

class ParquetDataTaskTest extends AbstractTaskTest {
	@Test
	void convertsWithExistingOutputFileTypeProperty() {
		def input = new File(testProjectDir, 'input')
		input.mkdirs()
		new File(input, 'items.json').setText('[{"ID":42}]', 'UTF-8')
		def output = new File(testProjectDir, 'output')
		def task = createProject(testProjectDir).tasks.register('toParquet', ConvertDataTask) {
			directory = input
			outputDirectory = output
			outputFileType = 'PARQUET'
			recursive = false
			removeOriginalFile = false
		}.get()
		task.exec()
		assertEquals(DataFormat.PARQUET, task.internalCommand().outputFileType)
		assertTrue(new File(output, 'input/items.parquet').isFile())
		assertTrue(new File(input, 'items.json').isFile())
	}
}
