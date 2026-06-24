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

import static org.junit.jupiter.api.Assertions.assertTrue;

import javax.sql.DataSource

import org.gradle.api.Project;
import org.junit.jupiter.api.Test;

import com.sqlapp.gradle.plugins.extension.MigrationExtension

class MigrationDownTaskTest extends AbstractTaskTest{

	@Test
	public void canAddTaskToProject() {
		copyDirectory(new File("./src/test/resources/migration"), new File(testProjectDir, "migration"));
		Project project = createProject(testProjectDir);

		//project.getPlugins().apply(DbPlugin.class);
		MigrationExtension extension=project.extensions.create("migration", MigrationExtension, project);
		extension {
			setupSqlDirectory=new File(testProjectDir, "migration/setupSqlDirectory")
			sqlDirectory=new File(testProjectDir, "migration/sqlDirectory")
			downSqlDirectory=new File(testProjectDir, "migration/sqlDirectory")
			dataSource {
				driverClassName="org.hsqldb.jdbc.JDBCDriver"
				jdbcUrl="jdbc:hsqldb:mem:test"
				username="root"
				password="password"
			}
		}
		MigrationTask task=project.tasks.register('migration', MigrationTask).get();
		assertTrue(task instanceof MigrationTask)
		DataSource dataSource = getDataSource(extension.dataSource);
		dropTables(dataSource, "TAB1", "TAB2", "changelog");
		task.exec()

		MigrationDownTask downTask =project.tasks.register('migrationDown', MigrationDownTask).get()
		downTask.exec()
	}
}
