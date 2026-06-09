/**
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

package com.sqlapp.gradle.plugins;

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;

import com.sqlapp.gradle.plugins.extension.DiffSchemaXmlExtension;
import com.sqlapp.gradle.plugins.extension.GenerateDiffSqlExtension;
import com.sqlapp.gradle.plugins.extension.GenerateHtmlExtension;
import com.sqlapp.gradle.plugins.extension.GenerateSqlExtension;
import com.sqlapp.gradle.plugins.extension.VersionUpExtension;

public class DbPlugin implements Plugin<Project> {

	@Override
	public void apply(Project project) {
		//
		registerTask(project, "countAllTables", CountAllTableTask.class);
		//
		registerTaskWithExtensions(project, "versionUp", VersionUpExtension.class, VersionUpTask.class);
		//
		registerTask(project, "versionInsert", VersionInsertTask.class);
		registerTask(project, "versionRepair", VersionRepairTask.class);
		//
		registerTask(project, "exportXml", ExportXmlTask.class);
		//
		registerTaskWithExtensions(project, "diffSchemaXml", DiffSchemaXmlExtension.class, DiffSchemaXmlTask.class);
		//
		registerTaskWithExtensions(project, "generateDiffSql", GenerateDiffSqlExtension.class,
				GenerateDiffSqlTask.class);
		//
		registerTaskWithExtensions(project, "generateSql", GenerateSqlExtension.class, GenerateSqlTask.class);
		//
		registerTaskWithExtensions(project, "generateHtml", GenerateHtmlExtension.class, GenerateHtmlTask.class);
		//
		registerTask(project, "updateDictionaries", UpdateDictionariesTask.class);
	}

	protected void registerTaskWithExtensions(Project project, String name, Class<?> pojoClass,
			Class<? extends Task> taskClass) {
		createExtensions(project, name, pojoClass);
		registerTask(project, name, taskClass);
	}

	protected void registerTask(Project project, String name, Class<? extends Task> taskClass) {
		project.getTasks().register(name, taskClass);
	}

	protected void createExtensions(Project project, String name, Class<?> pojoClass) {
		// project.getExtensions().create(name, pojoClass, project);
		project.getExtensions().create(name, pojoClass);
	}
}
