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
package com.sqlapp.gradle.plugins.pojo

import com.sqlapp.data.db.sql.TableOptions;
import com.sqlapp.util.CommonUtils
import com.sqlapp.util.JsonConverter;

import groovy.lang.Closure

import java.io.File
import java.nio.charset.Charset;
import java.util.List
import java.util.function.Consumer;

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

abstract class AbstractExportDataPojo extends DbTablePojo{
	/**
	 * Output Direcroty
	 */
	@InputDirectory
	def directory=new File("./");
	
	@Input
	@Optional
	boolean useSchemaNameDirectory=false;
	@Input
	@Optional
	String csvEncoding=Charset.defaultCharset().toString();

	JsonConverter jsonConverter=createJsonConverter();
	@Input
	@Optional
	TableOptionsPojo tableOptions;
	
	private static JsonConverter createJsonConverter(){
		JsonConverter jsonConverter=new JsonConverter();
		jsonConverter.setIndentOutput(true);
		return jsonConverter;
	}

	AbstractExportDataPojo(Project project) {
		super(project);
		tableOptions=new TableOptionsPojo(project);
	}
	
	void directory(def directory){
		this.directory=directory;
	}

	void useSchemaNameDirectory(boolean useSchemaNameDirectory){
		this.useSchemaNameDirectory=useSchemaNameDirectory;
	}

	void csvEncoding(String csvEncoding){
		this.csvEncoding=csvEncoding;
	}

	void tableOptions(Closure closure) {
		if (this.tableOptions==null){
			this.tableOptions=project.configure(new TableOptionsPojo(project), closure)
		}else{
			project.configure(this.tableOptions, closure)
		}
	}

	void tableOptions(TableOptionsPojo tableOptions) {
		this.tableOptions=tableOptions
	}
	
	void jsonConverter(Closure closure) {
		if (this.jsonConverter==null){
			this.jsonConverter=project.configure(createJsonConverter(createJsonConverter(), closure))
		}else{
			project.configure(this.jsonConverter, closure)
		}
	}
}
