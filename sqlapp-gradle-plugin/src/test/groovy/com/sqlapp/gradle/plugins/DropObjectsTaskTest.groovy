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

import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider
import org.junit.jupiter.api.Test;

class DropObjectsTaskTest extends AbstractTaskTest{

	@Test
	public void canAddTaskToProject() {
		copyDirectory(new File("./src/test/resources/dropobjects"), new File(testProjectDir, "dropobjects"));
		Project project = createProject(testProjectDir);
		TaskProvider<DropObjectsTask> taskProvider =project.tasks.register('dropObjectsExtension', DropObjectsTask){
			debug=true
			dropTables=true
			includeSchemas=["PUBLIC"]
			preDropTableSql="CREATE TABLE TAB1 ( ID INT )"
			dataSource {
				driverClassName="org.hsqldb.jdbc.JDBCDriver"
				jdbcUrl="jdbc:hsqldb:mem:testdropobjects"
				username="root"
				password="password"
			}
		}
		DropObjectsTask task =taskProvider.get();
		task.exec()
	}
}
