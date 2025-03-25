/*
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

import com.sqlapp.gradle.plugins.extension.CountAllTableExtension
import com.sqlapp.gradle.plugins.tasks.CountAllTableTask
import com.sqlapp.gradle.plugins.tasks.SqlExecuteTask

class CountAllTableTaskTest extends AbstractTaskTest{

	@Test
	public void canAddTaskToProject() {
		copyDirectory(new File("./src/test/resources/sqlExecute"), new File(testProjectDir, "sqlExecute"));
		Project project = createProject(testProjectDir);

		SqlExecuteTask task =project.tasks.register('sqlExecute', SqlExecuteTask).get();
		task {
			sqlFiles.from new File(testProjectDir, "sqlExecute/sqlFiles/create_table1.sql")
			sqlFiles.from new File(testProjectDir, "sqlExecute/sqlFiles/insert_table1.sql")
			sqlFiles.from new File(testProjectDir, "sqlExecute/sqlFiles/create_table2.sql")
			sqlFiles.from new File(testProjectDir, "sqlExecute/sqlFiles/insert_table2.sql")
			dataSource {
				driverClassName="org.hsqldb.jdbc.JDBCDriver"
				jdbcUrl="jdbc:hsqldb:mem:test"
				username="root"
				password="password"
			}
		}
		DataSource dataSourceObj=getDataSource(task.dataSource);
		dropTables(dataSourceObj, "TAB1", "TAB2");
		task.exec()

		//project.getPlugins().apply(DbPlugin.class);
		CountAllTableExtension extension=project.extensions.create("countAllTables", CountAllTableExtension, project);
		extension {
			dataSource {
				driverClassName="org.hsqldb.jdbc.JDBCDriver"
				jdbcUrl="jdbc:hsqldb:mem:test"
				username="root"
				password="password"
			}
		}
		CountAllTableTask countAllTask=project.tasks.register('countAllTables', CountAllTableTask).get();
		DataSource dataSource=getDataSource(extension.dataSource);
		countAllTask.exec()
		dropTables(dataSourceObj, "TAB1", "TAB2");
	}
}
