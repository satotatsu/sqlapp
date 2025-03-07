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
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.gradle.api.Project;
import org.gradle.testfixtures.ProjectBuilder;
import org.junit.jupiter.api.Test;

public class EnvironmentTaskTest {
	@Test
	public void canAddTaskToProject() {
		ProjectBuilder projectBuilder=ProjectBuilder.builder();
		projectBuilder.withProjectDir(new File("./"));
		projectBuilder.getProperties().put("envPath", "./src/test/environment/default");
		Project project = projectBuilder.build();
		EnvironmentTask task = project.task('environmentTask', type: EnvironmentTask)
		task.exec();

		assertTrue(task instanceof EnvironmentTask)
		assertEquals("org.hsqldb.Driver", project.jdbc.connection.driverClassName);
		task.exec()
	}
}
