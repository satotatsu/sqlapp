/*
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-gradle-plugin.
 *
 * sqlapp-gradle-plugin is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-gradle-plugin is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-gradle-plugin.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.gradle.plugins

import static org.junit.jupiter.api.Assertions.assertEquals
import static org.junit.jupiter.api.Assertions.assertNotNull
import static org.junit.jupiter.api.Assertions.assertNull
import static org.junit.jupiter.api.Assertions.assertTrue

import org.gradle.api.Project
import org.junit.jupiter.api.Test

import com.sqlapp.data.db.datatype.DataType
import com.sqlapp.data.schemas.Column
import com.sqlapp.data.schemas.Schema
import com.sqlapp.data.schemas.SchemaUtils
import com.sqlapp.data.schemas.Table

class FirstNormalFormTaskTest extends AbstractTaskTest {

	@Test
	void testExec() {
		File inputDirectory = new File(testProjectDir, "input")
		File outputDir = new File(testProjectDir, "output")
		assertTrue(inputDirectory.mkdirs())
		File inputFile = new File(inputDirectory, "schema.xml")
		createSchema().writeXml(inputFile)

		Project project = createProject(testProjectDir)
		FirstNormalFormTask task = project.tasks.register("normalize", FirstNormalFormTask) {
			targetFile.set(inputFile)
			outputDirectory.set(outputDir)
			minimumColumnCount.set(1)
			childKeyColumnNameStrategy = { table -> "POSITION_NO" }
			childTableNameStrategy = { table, number -> table.name + "_VALUES_" + number }
		}.get()

		task.exec()

		File outputFile = new File(outputDir, inputFile.name)
		assertTrue(outputFile.isFile())
		Schema output = SchemaUtils.readXml(outputFile)
		Table source = output.tables.get("CONTACTS")
		assertNull(source.columns.get("PHONE_1"))
		assertNull(source.columns.get("PHONE_2"))
		Table child = output.tables.get("CONTACTS_VALUES_1")
		assertNotNull(child)
		assertNotNull(child.columns.get("POSITION_NO"))
		assertNotNull(child.columns.get("PHONE"))
		assertEquals(1, child.constraints.foreignKeyConstraints.size())
	}

	@Test
	void testRegisteredByPlugin() {
		Project project = createProject(testProjectDir)
		project.plugins.apply(DbPlugin)

		FirstNormalFormTask task = project.tasks.named("firstNormalForm", FirstNormalFormTask).get()
		assertNotNull(task)
		assertEquals(2, task.minimumColumnCount.get())
	}

	private Schema createSchema() {
		Schema schema = new Schema("PUBLIC")
		Table table = new Table("CONTACTS")
		Column id = new Column("ID").setDataType(DataType.INT).setNotNull(true)
		table.columns.add(id)
		table.columns.add(new Column("PHONE_1").setDataType(DataType.VARCHAR).setLength(30))
		table.columns.add(new Column("PHONE_2").setDataType(DataType.VARCHAR).setLength(30))
		table.setPrimaryKey("PK_CONTACTS", id)
		schema.tables.add(table)
		return schema
	}
}
