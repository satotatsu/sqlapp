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

import static org.junit.jupiter.api.Assertions.assertTrue;

import org.gradle.BuildResult
import org.gradle.api.Project;
import org.gradle.api.tasks.TaskProvider
import org.gradle.testkit.runner.GradleRunner
import org.junit.jupiter.api.Test;

import com.sqlapp.gradle.plugins.extension.UpdateDictionariesExtension
import com.sqlapp.gradle.plugins.tasks.UpdateDictionariesTask

class UpdateDictionariesTaskTest extends AbstractTaskTest{
	@Test
	public void testTask() {
		writeFile(settingsFile, "rootProject.name = 'test-gradle'");
		copyDirectory(new File("./src/test/environment/default"), new File(testProjectDir, "environment/default"));
		copyDirectory(new File("./src/test/resources/"), new File(testProjectDir, "resources"));

		buildFile <<"""
			 project.extensions.create("dictionaries", com.sqlapp.gradle.plugins.extension.UpdateDictionariesExtension.class, project);
			 project.tasks.register("dictionaries", com.sqlapp.gradle.plugins.UpdateDictionariesTask);
			 updateDictionaries {
				targetFile= new File(testProjectDir, "resources/schema.xml")
				dictionaryFileDirectory=new File(testProjectDir, "dictionaries")
				dictionaryFileType="xlsx"
			}
		"""
		BuildResult result = GradleRunner.create()
				.withDebug(true)
				.withPluginClasspath()
				//.withPluginClasspath(pluginClasspath())
				.withProjectDir(testProjectDir)
				.withArguments("helloWorld")
				.build();
		//assertEquals(SUCCESS, result.task(":updateDictionaries").getOutcome());
		//assertTrue(result.getOutput().contains("Hello world!"));
	}


	@Test
	public void canAddTaskToProject() {
		copyDirectory(new File("./src/test/environment/default"), new File(testProjectDir, "environment/default"));
		copyDirectory(new File("./src/test/resources/"), new File(testProjectDir, "resources"));
		Project project = createProject(testProjectDir);

		//project.getPlugins().apply(DbPlugin.class);
		UpdateDictionariesExtension extension=project.extensions.create("updateDictionaries", UpdateDictionariesExtension, project);
		extension {
			targetFile= new File(testProjectDir, "resources/schema.xml")
			dictionaryFileDirectory=new File(testProjectDir, "dictionaries")
			dictionaryFileType="xlsx"
		}

		extension.dataSource {
			driverClassName="org.hsqldb.jdbc.JDBCDriver"
			jdbcUrl="jdbc:hsqldb:mem:test"
			username="root"
			password="password"
		}
		TaskProvider<UpdateDictionariesTask> taskProvider =project.tasks.register('updateDictionaries', UpdateDictionariesTask)
		UpdateDictionariesTask task=taskProvider.get();
		assertTrue(task instanceof UpdateDictionariesTask)
		task.exec()
	}

	private List<File> pluginClasspath() {
		return Arrays.asList(new File("bin/main"), new File("build/classes/java/main")
				, new File("build/resources/main")
				, new File(pathOfJarContaining(DbPlugin.class)));
	}

	private String pathOfJarContaining(String className) {
		try {
			return pathOfJarContaining(Class.forName(className));
		}
		catch (ClassNotFoundException ex) {
			throw new IllegalArgumentException(ex);
		}
	}

	private String pathOfJarContaining(Class<?> type) {
		return type.getProtectionDomain().getCodeSource().getLocation().getPath();
	}
}
