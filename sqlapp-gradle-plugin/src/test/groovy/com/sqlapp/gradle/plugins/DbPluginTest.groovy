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
import org.gradle.api.Task
import org.junit.jupiter.api.Test;

class DbPluginTest extends AbstractTaskTest{
	@Test
	public void applyTest() {
		copyDirectory(new File("./src/test/environment/default"), new File(testProjectDir, "environment/default"));
		copyDirectory(new File("./src/test/resources/"), new File(testProjectDir, "resources"));
		Project project = createProject(testProjectDir);

		project.extensions.loadTimeEnvironment=true;
		project.extensions.environmentFilePath="environment";
		project.getPlugins().apply(DbPlugin.class);
		println("project.properties="+project.properties);
		println("project.properties.driverClassName="+project.properties.driverClassName);
		project.extensions.exportXml.dataSource {
			driverClassName=project.driverClassName
			jdbcUrl=project.jdbcUrl
			username=project.username
			password=project.password
		}
		project.evaluate()
		Task task=project.tasks.exportXml
		task.exec()
	}
}
