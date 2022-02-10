/*
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-gradle-plugin.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.gradle.plugins
import java.io.File;
import java.util.List;
import java.util.Set

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.TaskAction;

import com.sqlapp.data.db.command.*;
import com.sqlapp.data.db.command.export.*;
import com.sqlapp.gradle.plugins.pojo.*;

import com.sqlapp.data.converter.*;


import com.sqlapp.util.CommonUtils;
import groovy.lang.Closure;

class DbPlugin implements Plugin<Project> {

	@Override
	public void apply(Project project) {
		if(project.extensions==null){
			project.extensions=[:]
		}
		loadEnvironment(project)
		createTaskWithExtensions(project, 'exportData', ExportDataPojo, ExportDataTask);
		project.exportData.extensions.create('dataSource', DataSourcePojo, project)
		project.exportData.extensions.create('tableOptions', TableOptionsPojo)
		createTaskWithExtensions(project, 'importData', ImportDataPojo, ImportDataTask);
		project.importData.extensions.create('dataSource', DataSourcePojo, project)
		project.importData.extensions.create('tableOptions', TableOptionsPojo, project)
		//
		createTaskWithExtensions(project, 'countAllTables', CountAllTablePojo, CountAllTableTask);
		project.countAllTables.extensions.create('dataSource', DataSourcePojo, project)
		//
		createTaskWithExtensions(project, 'dropObjects', DropObjectsPojo, DropObjectsTask);
		project.dropObjects.extensions.create('dataSource', DataSourcePojo, project)
		//
		createTaskWithExtensions(project, 'versionUp', VersionUpPojo, VersionUpTask);
		project.versionUp.extensions.create('dataSource', DataSourcePojo, project)
		project.versionUp.extensions.create('changeTable', ChangeTablePojo, project)
		//
		createTaskWithExtensions(project, 'versionInsert', VersionUpPojo, VersionInsertTask);
		createTaskWithExtensions(project, 'versionRepair', VersionUpPojo, VersionRepairTask);
		createTaskWithExtensions(project, 'versionDown', VersionUpPojo, VersionDownTask);
		project.versionDown.extensions.create('dataSource', DataSourcePojo, project)
		project.versionDown.extensions.create('changeTable', ChangeTablePojo, project)
		createTaskWithExtensions(project, 'versionDownSeries', VersionUpPojo, VersionDownSeriesTask);
		project.versionDownSeries.extensions.create('dataSource', DataSourcePojo, project)
		project.versionDownSeries.extensions.create('changeTable', ChangeTablePojo, project)
		//
		//
		createTaskWithExtensions(project, 'exportXml', ExportXmlPojo, ExportXmlTask);
		project.exportXml.extensions.create('schemaOptions', OptionsPojo, project)
		project.exportXml.extensions.create('dataSource', DataSourcePojo, project)
		//
		createTaskWithExtensions(project, 'diffSchemaXml', DiffSchemaXmlPojo, DiffSchemaXmlTask);
		//
		createTaskWithExtensions(project, 'synchronizeSchema', SynchronizeSchemaPojo, SynchronizeSchemaTask);
		project.synchronizeSchema.extensions.create('dataSource', DataSourcePojo, project)
		//
		createTaskWithExtensions(project, 'generateDiffSql', GenerateDiffSqlPojo, GenerateDiffSqlTask);
		project.generateDiffSql.extensions.create('schemaOptions', OptionsPojo, project);
		//
		createTaskWithExtensions(project, 'generateSql', GenerateSqlPojo, GenerateSqlTask);
		project.generateSql.extensions.create('schemaOptions', OptionsPojo, project);
		//
		createTaskWithExtensions(project, 'generateHtml', GenerateHtmlPojo, GenerateHtmlTask);
		project.generateHtml.extensions.create('renderOptions', RenderOptionsPojo, project);
		//
		createTaskWithExtensions(project, 'updateDictionaries', UpdateDictionariesPojo, UpdateDictionariesTask);
		//
		createTask(project, 'avaliableFonts', AvaliableFontsTask);
		//
		project.afterEvaluate {
			project.tasks.exportData.pojo=project.exportData
			project.tasks.importData.pojo=project.importData
			project.tasks.countAllTables.pojo=project.countAllTables
			project.tasks.dropObjects.pojo=project.dropObjects
			project.tasks.versionUp.pojo=project.versionUp
			project.tasks.versionInsert.pojo=project.versionUp
			project.tasks.versionDown.pojo=project.versionDown
			project.tasks.versionDownSeries.pojo=project.versionDownSeries
			project.tasks.exportXml.pojo=project.exportXml
			project.tasks.diffSchemaXml.pojo=project.diffSchemaXml
			project.tasks.synchronizeSchema.pojo=project.synchronizeSchema
			project.tasks.generateDiffSql.pojo=project.generateDiffSql
			project.tasks.generateSql.pojo=project.generateSql
			project.tasks.generateHtml.pojo=project.generateHtml
			project.tasks.updateDictionaries.pojo=project.updateDictionaries
		}
	}

	protected void createTaskWithExtensions(Project project, String name, Class pojoClass, Class taskClass){
		createExtensions(project, name, pojoClass);
		createTask(project, name, taskClass);
	}
	
	protected Object createTask(Project project, String name, Class taskClass){
		return project.tasks.create(name, taskClass);
	}
	
	protected void createExtensions(Project project, String name, Class pojoClass){
		project.extensions.create(name, pojoClass, project);
	}
	
	protected void loadEnvironment(Project project) {
		Object value=getPropertyInternal(project, 'loadTimeEnvironment');
		if (value==null){
			return;
		}
		Boolean bool=convert(value , Boolean.class);
		if (bool){
			System.out.println("project.extensions.loadTimeEnvironment="+bool);
		}
		String environmentFilePath=getPropertyInternal(project, 'environmentFilePath');
		if (environmentFilePath!=null){
			System.out.println("project.extensions.environmentFilePath="+environmentFilePath);
		} else{
			environmentFilePath="src/main/environment"
		}
		File directory=getFile(project, environmentFilePath);
		if (!directory.exists()){
			System.out.println("environmentFilePath does not exists. path="+directory.absolutePath);
			return;
		}
		if (!directory.isDirectory()){
			System.out.println("environmentFilePath is not a directory. path="+directory.absolutePath);
			return;
		}
		Map<String,File> childMap=new TreeMap<String,File>();
		for(File child:directory.listFiles()){
			if (child.isDirectory()){
				childMap.put(child.name, child);
			}
		}
		String env=getPropertyInternal(project, 'env');
		if (env==null){
			if (childMap.isEmpty()){
				System.err.println("No environment found. path="+directory.absolutePath);
				throw new InvalidUserDataException("No environment found. path="+directory.absolutePath);
			} else if (childMap.size()==1){
				env=CommonUtils.first(childMap.keySet())
			} else{
				String envText=getEnvText(childMap.keySet());
				Console console=System.console();
				if (console!=null){
					while(true){
						env=console.readLine("%s:", "select environment. ["+envText+"]");
						if (env==null){
							continue;
						}
						if (childMap.containsKey(env)){
							break;
						}
					}
					System.out.println("environment["+env+"] was selected.");
				} else{
//					BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
//					while(true){
//						System.out.println("select environment. ["+envText+"]:");
//						env=br.readLine();
//						if (env==null){
//							continue;
//						}
//						if (childMap.containsKey(env)){
//							break;
//						}
//					}
				}
			}
		}
		String envVar;
		if (env==null){
			String defaultEnvironment=getPropertyInternal(project, 'defaultEnvironment');
			if (defaultEnvironment!=null){
				envVar=defaultEnvironment;
			} else{
				envVar="default"
			}
			System.out.println("project.extensions.defaultEnvironment="+envVar);
		} else{
			envVar=env;
		}
		File envDir=new File(envVar, directory);
		if (!envDir.exists()){
			System.out.println("Env direcotry does not exists. path="+envDir.absolutePath);
			return;
		}
		if (!envDir.isDirectory()){
			System.out.println("Env direcotry is not a directory. path="+envDir.absolutePath);
			throw new InvalidUserDataException("Env direcotry is not a directory. path="+envDir.absolutePath);
		}
		ConfigSlurper slurper = new ConfigSlurper()
		slurper.binding = project.properties
		def config = project.files(envDir.listFiles()).inject(new ConfigObject()) { config, file ->
			if (file.exists()&&!file.isDirectory()){
				if (file.getAbsolutePath().endsWith(".properties")){
					Properties prop = new Properties()
					new FileInputStream(file).withCloseable{
						prop.load(it);
					}
					return config.merge(slurper.parse(prop));
				} else if (file.getAbsolutePath().endsWith(".xml")){
					Properties prop = new Properties()
					new FileInputStream(file).withCloseable{
						prop.loadFromXML(it);
					}
					return config.merge(slurper.parse(prop));
				} else{
					return config.merge(slurper.parse(file.toURL()));
				}
			} else{
				return config;
			}
		}
		config.each { k, v ->
			project.extensions.add(k, v);
		}
	}
	
	private String getEnvText(Set<String> set){
		StringBuilder builder=new StringBuilder();
		boolean first=true;
		for(String value:set){
			if (!first){
				builder.append(", ");
			} else{
				first=false;
			}
			builder.append(value);
		}
		return builder.toString();
	}
	
	private Object getPropertyInternal(Project project, String key){
		Object value=System.getProperty(key);
		if (value==null){
			if(project.hasProperty(key)){
				value=project.getProperties().get(key);
			}
		}
		return value;
	}

	private Object convert(Object value, Class clazz){
		return Converters.getDefault().convertObject(value, clazz);
	}

		/**
	 * @return the file
	 */
	protected File getFile(Project project, def file) {
		return project.file(file);
	}
	
	/**
	 * @return the files
	 */
	protected List<File> getFiles(Project project, def files) {
		return CommonUtils.list(project.files(files).getFiles());
	}
	
}
