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

class TableSqlExecuteTaskTest extends AbstractTaskTest{

	@Test
	public void canAddTaskToProject() {
		copyDirectory(new File("./src/test/resources/sqlExecute"), new File(testProjectDir, "sqlExecute"));
		Project project = createProject(testProjectDir);

		TableSqlExecuteTask task =project.tasks.register('tableSqlExecuteTask', TableSqlExecuteTask).get();
		task {
			debug=true
			dataSource {
				driverClassName="org.hsqldb.jdbc.JDBCDriver"
				jdbcUrl="jdbc:hsqldb:mem:test"
				username="root"
				password="password"
			}
			sqlTypes=["SELECT_ALL"]
		}

		DataSource dataSource=getDataSource(task.dataSource);
		dropTables(dataSource, "TAB1", "TAB2");

		task.exec()
		dropTables(dataSource, "TAB1", "TAB2");
	}
}
