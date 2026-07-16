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
import org.junit.jupiter.api.Test;

class SqlQueryTaskTest extends AbstractTaskTest{

	@Test
	public void canAddTaskToProject() {
		copyFile(new File("./src/test/resources/test_ds.properties"), new File(testProjectDir, "dataSource.properties"));
		copyDirectory(new File("./src/test/resources/sqlExecute"), new File(testProjectDir, "sqlExecute"));
		Project project = createProject(testProjectDir);

		SqlExecuteTask task =project.tasks.register('sqlExecute', SqlExecuteTask).get();
		task {
			directory=new File(testProjectDir, "sqlExecute/sqlFiles/")
			includes.addAll("create_table1.sql", "insert_table1.sql")
			dataSource {
				properties "./dataSource.properties"
			}
		}

		DataSource dataSourceObj=getDataSource(task.dataSource);
		dropTables(dataSourceObj, "TAB1", "TAB2");
		task.exec()
		SqlQueryTask queryTask =project.tasks.register('sqlQuery', SqlQueryTask).get();
		queryTask {
			sqlFile= new File(testProjectDir, "sqlExecute/sqlFiles/select_table1.sql")
			dataSource {
				driverClassName="org.hsqldb.jdbc.JDBCDriver"
				jdbcUrl="jdbc:hsqldb:mem:test"
				username="root"
				password="password"
			}
		}
		queryTask.exec()
		dropTables(dataSourceObj, "TAB1", "TAB2");
	}
}
