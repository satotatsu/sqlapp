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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.gradle.api.Project
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir

import com.sqlapp.gradle.plugins.extension.GenerateSqlExtension

class GenerateSqlTaskTest extends AbstractTaskTest{

	@TempDir
	protected File testOutputDir;
	@Test
	public void outputFile() {
		copyDirectory(new File("./src/test/resources/gensql"), new File(testProjectDir, "gensql"));
		Project project = createProject(testProjectDir);

		GenerateSqlExtension extension=project.extensions.create("generateSql", GenerateSqlExtension, project);
		extension {
			targetFile=new File("gensql/Schemas1.xml")
			encoding="UTF8"
			outputAsMultiFiles=false
			outputDirectory=new File("src/main/sql")
			outputFileExtension="sql"
			changeNumberStep=10
			numberOfDigits=19
			schemaOptions {
				outputCommit=false
				dropIfExists=true
				createIfNotExists=true
				decorateSchemaName=false
				setSearchPathToSchema=true
			}
			tableOptions {
			}
		}
		GenerateSqlTask targetTask =project.tasks.register('generateSql', GenerateSqlTask).get();
		targetTask.exec()
		File outputDir=new File(testProjectDir, "src/main/sql");
		File[] files=outputDir.listFiles();
		assertEquals(1, files.length);
		assertTrue(files[0].getName().endsWith(".sql"));
	}

	@Test
	public void outputFileAsMultiFile() {
		copyDirectory(new File("./src/test/resources/gensql"), new File(testProjectDir, "gensql"));
		Project project = createProject(testProjectDir);

		GenerateSqlExtension extension=project.extensions.create("generateSql", GenerateSqlExtension, project);
		extension {
			targetFile=new File("gensql/Schemas1.xml")
			encoding="UTF8"
			outputAsMultiFiles=true
			outputDirectory=new File("src/main/sql")
			outputFileExtension="sql"
			changeNumberStep=10
			numberOfDigits=19
			schemaOptions {
				outputCommit=false
				dropIfExists=true
				createIfNotExists=true
				decorateSchemaName=false
				setSearchPathToSchema=true
			}
			tableOptions {
			}
		}
		GenerateSqlTask targetTask =project.tasks.register('generateSql', GenerateSqlTask).get();
		targetTask.exec()
		File outputDir=new File(testProjectDir, "src/main/sql");
		File[] files=outputDir.listFiles();
		assertEquals(3, files.length);
		for(File f:files) {
			assertTrue(f.getName().endsWith(".sql"));
		}
	}


	@Test
	public void outputStandardOut() {
		copyDirectory(new File("./src/test/resources/gensql"), new File(testProjectDir, "gensql"));
		Project project = createProject(testProjectDir);
		GenerateSqlExtension extension=project.extensions.create("generateSql", GenerateSqlExtension, project);
		extension {
			targetFile=new File("gensql/Schemas1.xml")
			encoding="UTF8"
			outputAsMultiFiles=false
			//			outputDirectory=new File("src/main/sql")
			outputFileExtension="sql"
			changeNumberStep=10
			numberOfDigits=19
			schemaOptions {
				outputCommit=false
				dropIfExists=true
				createIfNotExists=true
				decorateSchemaName=false
				setSearchPathToSchema=true
			}
			tableOptions {
			}
		}
		GenerateSqlTask targetTask =project.tasks.register('generateSql', GenerateSqlTask).get();
		targetTask.exec()
	}
}
