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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir

import com.sqlapp.gradle.plugins.extension.GenerateDiffSqlExtension

class GenerateDiffSqlTaskTest extends AbstractTaskTest{

	@TempDir
	protected File testOutputDir;
	@Test
	public void outputFile() {
		copyDirectory(new File("./src/test/resources/diffsql"), new File(testProjectDir, "diffsql"));
		Project project = createProject(testProjectDir);

		GenerateDiffSqlExtension extension=project.extensions.create("generateDiffSql", GenerateDiffSqlExtension, project);
		extension {
			originalFile=new File("diffsql/Schemas1.xml")
			targetFile=new File("diffsql/Schemas2.xml")
			encoding="UTF8"
			outputAsMultiFiles=false
			withVersionDown=true
			outputDirectory=new File("src/main/sql")
			outputFileExtension="sql"
			changeNumberStep=10
			numberOfDigits=19
			equalsHandler {
				//		referenceEqualsPredicate={(a,b)->a==null&&b==null||a!=null&&a.is(b)||b!=null&&b.is()}
				//		valueEqualsPredicate={(propertyName,eq, obj1, obj2, val1, val2)->eq}
				//		equalsLastPredicate={(a,b)->true}
			}
			schemaOptions {
				outputCommit=false
				dropIfExists=true
				createIfNotExists=true
				decorateSchemaName=false
				setSearchPathToSchema=true
				tableOptions {
				}
			}
		}
		GenerateDiffSqlTask targetTask =project.tasks.register('generateDiffSql', GenerateDiffSqlTask).get();
		targetTask.exec()
	}

	@Test
	public void outputStandardOut() {
		copyDirectory(new File("./src/test/resources/diffsql"), new File(testProjectDir, "diffsql"));
		Project project = createProject(testProjectDir);

		GenerateDiffSqlExtension extension=project.extensions.create("generateDiffSql", GenerateDiffSqlExtension, project);
		extension {
			originalFile=new File("diffsql/Schemas1.xml")
			targetFile=new File("diffsql/Schemas2.xml")
			encoding="UTF8"
			outputAsMultiFiles=false
			withVersionDown=true
			//			outputDirectory=new File("src/main/sql")
			outputFileExtension="sql"
			changeNumberStep=10
			numberOfDigits=19
			equalsHandler {
				//		referenceEqualsPredicate={(a,b)->a==null&&b==null||a!=null&&a.is(b)||b!=null&&b.is()}
				//		valueEqualsPredicate={(propertyName,eq, obj1, obj2, val1, val2)->eq}
				//		equalsLastPredicate={(a,b)->true}
			}
			schemaOptions {
				outputCommit=false
				dropIfExists=true
				createIfNotExists=true
				decorateSchemaName=false
				setSearchPathToSchema=true
				tableOptions {
				}
			}
		}
		GenerateDiffSqlTask targetTask =project.tasks.register('generateDiffSql', GenerateDiffSqlTask).get();
		targetTask.exec()
	}
}
