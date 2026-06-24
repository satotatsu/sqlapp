/*
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.gradle.api.Project
import org.gradle.api.tasks.TaskProvider
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import com.zaxxer.hikari.HikariConfig

class ExportSchemaXmlTaskTest extends AbstractTaskTest{
	@TempDir
	protected File testOutputDir;
	@Test
	public void testExec() {
		Project project = createProject(testProjectDir, { p->
		});
		TaskProvider<ExportSchemaXmlTask> taskProvider =project.tasks.register('exportXml', ExportSchemaXmlTask){
			debug=false
			outputDirectory=testOutputDir
			dataSource {
				driverClassName="org.hsqldb.jdbc.JDBCDriver"
				jdbcUrl="jdbc:hsqldb:mem:test"
				username="root"
				password="password"
			}
			includeSchemas.add("PUBLIC")
		}
		ExportSchemaXmlTask task=taskProvider.get();
		assertEquals("org.hsqldb.jdbc.JDBCDriver", task.dataSource.driverClassName.get())
		task.exec()
	}

	@Test
	public void testExecProp() {
		copyDirectory(new File("./src/test/environment/default"), new File(testProjectDir, "environment"));
		Project project = createProject(testProjectDir, { p->
			//p.getProperties().put("envPath", "./environment");
		});
		TaskProvider<ExportSchemaXmlTask> taskProvider =project.tasks.register('exportXml', ExportSchemaXmlTask){
			dataSource {
				properties "./environment/jdbc.properties"
			}
		}
		ExportSchemaXmlTask task=taskProvider.get();
		HikariConfig poolConfiguration=task.dataSource.toConfig();
		assertEquals("org.hsqldb.jdbc.JDBCDriver", poolConfiguration.driverClassName)
		task.exec()
	}
}
