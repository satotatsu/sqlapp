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
import org.junit.jupiter.api.Test;

class UpdateDictionariesTaskTest extends AbstractTaskTest{


	@Test
	public void canAddTaskToProject() {
		copyDirectory(new File("./src/test/environment/default"), new File(testProjectDir, "environment/default"));
		copyDirectory(new File("./src/test/resources/"), new File(testProjectDir, "resources"));
		Project project = createProject(testProjectDir);

		//project.getPlugins().apply(DbPlugin.class);
		UpdateDictionariesTask task=project.tasks.register('updateDictionaries', UpdateDictionariesTask){
			targetFile= new File(testProjectDir, "resources/schema.xml")
			dictionaryFileDirectory=new File(testProjectDir, "dictionaries")
			dictionaryFileType="xlsx"
			dictionaryFileDirectory=new File(testProjectDir, "dic");
			outputRemarksAsDisplayName=true
			withSchema { filename->
				true
			}
		}.get();

		task.exec()
	}
}
