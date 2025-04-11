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

import java.io.Console;
import java.io.File;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;

import com.sqlapp.data.converter.Converters;
import com.sqlapp.gradle.plugins.extension.CountAllTableExtension;
import com.sqlapp.gradle.plugins.extension.DiffSchemaXmlExtension;
import com.sqlapp.gradle.plugins.extension.DropObjectsExtension;
import com.sqlapp.gradle.plugins.extension.ExportDataExtension;
import com.sqlapp.gradle.plugins.extension.ExportXmlExtension;
import com.sqlapp.gradle.plugins.extension.GenerateDiffSqlExtension;
import com.sqlapp.gradle.plugins.extension.GenerateHtmlExtension;
import com.sqlapp.gradle.plugins.extension.GenerateSqlExtension;
import com.sqlapp.gradle.plugins.extension.ImportDataExtension;
import com.sqlapp.gradle.plugins.extension.SynchronizeSchemaExtension;
import com.sqlapp.gradle.plugins.extension.UpdateDictionariesExtension;
import com.sqlapp.gradle.plugins.extension.VersionUpExtension;
import com.sqlapp.util.CommonUtils;

import groovy.util.ConfigObject;

public class DbPlugin implements Plugin<Project> {

	@Override
	public void apply(Project project) {
		if (project.getExtensions() == null) {
//			project.extensions=[:]
		}
		loadEnvironment(project);

		registerTaskWithExtensions(project, "exportData", ExportDataExtension.class, ExportDataTask.class);

		registerTaskWithExtensions(project, "importData", ImportDataExtension.class, ImportDataTask.class);
		//
		registerTaskWithExtensions(project, "countAllTables", CountAllTableExtension.class, CountAllTableTask.class);
		//
		registerTaskWithExtensions(project, "dropObjects", DropObjectsExtension.class, DropObjectsTask.class);
		//
		registerTaskWithExtensions(project, "versionUp", VersionUpExtension.class, VersionUpTask.class);
		//
		registerTaskWithExtensions(project, "versionInsert", VersionUpExtension.class, VersionInsertTask.class);
		registerTaskWithExtensions(project, "versionRepair", VersionUpExtension.class, VersionRepairTask.class);
		registerTaskWithExtensions(project, "versionDown", VersionUpExtension.class, VersionDownTask.class);

		registerTaskWithExtensions(project, "versionDownSeries", VersionUpExtension.class, VersionDownSeriesTask.class);
		//
		registerTaskWithExtensions(project, "exportXml", ExportXmlExtension.class, ExportXmlTask.class);
		//
		registerTaskWithExtensions(project, "diffSchemaXml", DiffSchemaXmlExtension.class, DiffSchemaXmlTask.class);
		//
		registerTaskWithExtensions(project, "synchronizeSchema", SynchronizeSchemaExtension.class,
				SynchronizeSchemaTask.class);
		//
		registerTaskWithExtensions(project, "generateDiffSql", GenerateDiffSqlExtension.class,
				GenerateDiffSqlTask.class);
		//
		registerTaskWithExtensions(project, "generateSql", GenerateSqlExtension.class, GenerateSqlTask.class);
		//
		registerTaskWithExtensions(project, "generateHtml", GenerateHtmlExtension.class, GenerateHtmlTask.class);
		//
		registerTaskWithExtensions(project, "updateDictionaries", UpdateDictionariesExtension.class,
				UpdateDictionariesTask.class);
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

	@SuppressWarnings({ "unchecked" })
	protected void loadEnvironment(Project project) {
		Object envVal = getPropertyInternal(project, "loadTimeEnvironment");
		if (envVal == null) {
			return;
		}
		Boolean bool = convert(envVal, Boolean.class);
		if (bool) {
			System.out.println("project.extensions.loadTimeEnvironment=" + bool);
		} else {
			return;
		}
		String environmentFilePath = getPropertyInternal(project, "environmentFilePath");
		if (environmentFilePath != null) {
			System.out.println("project.extensions.environmentFilePath=" + environmentFilePath);
		} else {
			environmentFilePath = "src/main/environment";
		}
		File directory = getFile(project, environmentFilePath);
		if (!directory.exists()) {
			System.out.println("environmentFilePath does not exists. path=" + directory.getAbsolutePath());
			return;
		}
		if (!directory.isDirectory()) {
			System.out.println("environmentFilePath is not a directory. path=" + directory.getAbsolutePath());
			return;
		}
		Map<String, File> childMap = new TreeMap<String, File>();
		File[] files = directory.listFiles();
		if (files != null) {
			for (File child : files) {
				if (child.isDirectory()) {
					childMap.put(child.getName(), child);
				}
			}
		}
		String env = getPropertyInternal(project, "env");
		if (env == null) {
			if (childMap.isEmpty()) {
				System.err.println("No environment found. path=" + directory.getAbsolutePath());
				throw new InvalidUserDataException("No environment found. path=" + directory.getAbsolutePath());
			} else if (childMap.size() == 1) {
				env = CommonUtils.first(childMap.keySet());
			} else {
				String envText = getEnvText(childMap.keySet());
				Console console = System.console();
				if (console != null) {
					while (true) {
						env = console.readLine("%s:", "select environment. [" + envText + "]");
						if (env == null) {
							continue;
						}
						if (childMap.containsKey(env)) {
							break;
						}
					}
					System.out.println("environment[" + env + "] was selected.");
				} else {
					// BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
					// while(true){
					// System.out.println("select environment. ["+envText+"]:");
					// env=br.readLine();
					// if (env==null){
					// continue;
					// }
					// if (childMap.containsKey(env)){
					// break;
					// }
					// }
				}
			}
		}
		String envVar;
		if (env == null) {
			String defaultEnvironment = getPropertyInternal(project, "defaultEnvironment");
			if (defaultEnvironment != null) {
				envVar = defaultEnvironment;
			} else {
				envVar = "default";
			}
			System.out.println("project.extensions.defaultEnvironment=" + envVar);
		} else {
			envVar = env;
		}
		File envDir = new File(directory, envVar);
		if (!envDir.exists()) {
			System.out.println("Env direcotry does not exists. path=" + envDir.getAbsolutePath());
			return;
		}
		if (!envDir.isDirectory()) {
			System.out.println("Env direcotry is not a directory. path=" + envDir.getAbsolutePath());
			throw new InvalidUserDataException("Env direcotry is not a directory. path=" + envDir.getAbsolutePath());
		}
		ConfigObject config = new ConfigObject();
		Map<String, Object> props = (Map<String, Object>) project.getProperties();
		final File[] envFiles = envDir.listFiles();
		if (envFiles != null) {
			ConfigUtils.readConfig(props, config, envFiles);
		}
		config.forEach((k, v) -> {
			String key = (String) k;
			Object value = (Object) v;
			props.put(key, value);
			Object obj = project.getExtensions().findByName(key);
			if (obj == null) {
				project.getExtensions().add(key, value);
			}
		});
	}

	private String getEnvText(Set<String> set) {
		StringBuilder builder = new StringBuilder();
		boolean first = true;
		for (String value : set) {
			if (!first) {
				builder.append(", ");
			} else {
				first = false;
			}
			builder.append(value);
		}
		return builder.toString();
	}

	@SuppressWarnings("unchecked")
	private <T> T getPropertyInternal(Project project, String key) {
		Object value = System.getProperty(key);
		if (value == null) {
			if (project.hasProperty(key)) {
				value = project.getProperties().get(key);
			}
		}
		return (T) value;
	}

	private <T> T convert(Object value, Class<T> clazz) {
		return (T) Converters.getDefault().convertObject(value, clazz);
	}

	/**
	 * @return the file
	 */
	protected File getFile(Project project, String file) {
		return project.file(file);
	}
}
