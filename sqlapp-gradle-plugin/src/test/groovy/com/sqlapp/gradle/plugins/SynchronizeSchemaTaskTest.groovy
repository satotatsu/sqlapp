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

import javax.sql.DataSource

import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider
import org.junit.jupiter.api.Test;

class SynchronizeSchemaTaskTest extends AbstractTaskTest{

	@Test
	public void canAddTaskToProject() {
		copyDirectory(new File("./src/test/resources/synchronizeschema"), new File(testProjectDir, "synchronizeschema"));
		Project project = createProject(testProjectDir);
		TaskProvider<ImportDataTask> taskProvider =project.tasks.register('synchronizeSchema', SynchronizeSchemaTask){
			files.from new File("./synchronizeschema/create_table1.xml")
			dataSource {
				driverClassName="org.hsqldb.jdbc.JDBCDriver"
				jdbcUrl="jdbc:hsqldb:mem:test"
				username="root"
				password="password"
			}
		};
		SynchronizeSchemaTask task =taskProvider.get();
		task.exec()
	}

	@Test
	public void canAddTaskToProject2() {
		copyDirectory(new File("./src/test/resources/synchronizeschema"), new File(testProjectDir, "synchronizeschema"));
		Project project = createProject(testProjectDir);
		SqlExecuteTask task =project.tasks.register('sqlExecute', SqlExecuteTask).get();
		task {
			sqlFiles.from new File(testProjectDir, "synchronizeschema/create_table1.sql")
			dataSource {
				driverClassName="org.hsqldb.jdbc.JDBCDriver"
				jdbcUrl="jdbc:hsqldb:mem:test"
				username="root"
				password="password"
			}
		}
		DataSource dataSourceObj=getDataSource(task.dataSource);
		dropTables(dataSourceObj, "TAB1");
		task.exec()

		TaskProvider<ImportDataTask> taskProvider =project.tasks.register('synchronizeSchema', SynchronizeSchemaTask){
			files.from new File("./synchronizeschema/create_table2.xml")
			dataSource {
				driverClassName="org.hsqldb.jdbc.JDBCDriver"
				jdbcUrl="jdbc:hsqldb:mem:test"
				username="root"
				password="password"
			}
		}
		SynchronizeSchemaTask task2 =taskProvider.get();
		task2.exec()
	}
}
