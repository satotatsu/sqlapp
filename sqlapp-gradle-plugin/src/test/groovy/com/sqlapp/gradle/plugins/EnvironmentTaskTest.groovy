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

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider
import org.junit.jupiter.api.Test;

import com.sqlapp.gradle.plugins.tasks.EnvironmentTask

public class EnvironmentTaskTest extends AbstractTaskTest{
	@Test
	public void canAddTaskToProject() {
		Project project = createProject(new File("./"));
		TaskProvider<EnvironmentTask> taskProvider =project.tasks.register('environmentTask', EnvironmentTask, {
			envPath=new File("./src/test/environment");
		})
		EnvironmentTask task=taskProvider.get();
		task.exec();

		assertEquals("org.hsqldb.jdbc.JDBCDriver", project.properties.driverClassName);
		assertEquals("abc", project.properties.key.child);
		assertEquals("jsonVal1", project.properties.jsonObj.jsonKey);
		assertEquals("jon", project.properties.yamlPerson.name);
	}
}
