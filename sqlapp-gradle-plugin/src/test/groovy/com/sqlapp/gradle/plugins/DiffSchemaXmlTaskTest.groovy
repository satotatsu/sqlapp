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
import org.gradle.api.Project
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir

import com.sqlapp.gradle.plugins.extension.DiffSchemaXmlExtension

class DiffSchemaXmlTaskTest extends AbstractTaskTest{
	@TempDir
	protected File testOutputDir;
	@Test
	public void testExec() {
		copyDirectory(new File("./src/test/resources/diffschemaxml"), new File(testProjectDir, "diffschemaxml"));
		Project project = createProject(testProjectDir, { p->
		});

		DiffSchemaXmlExtension extension=project.extensions.create('diffSchemaXmlExtension', DiffSchemaXmlExtension, project);
		extension {
			targetFile=new File(testProjectDir, "diffschemaxml/schema.xml")
			originalFile=new File(testProjectDir, "diffschemaxml/schema2.xml")
		}
		DiffSchemaXmlTask task =project.tasks.register('diffSchemaXmlTask', DiffSchemaXmlTask).get();
		task.exec()
	}
}
