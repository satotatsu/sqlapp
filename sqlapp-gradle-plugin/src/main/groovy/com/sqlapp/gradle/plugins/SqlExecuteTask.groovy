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

import com.sqlapp.data.db.command.SqlExecuteCommand
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.gradle.plugins.pojo.DataSourcePojo
import com.sqlapp.util.CommonUtils

import groovy.lang.Closure

import java.io.File
import java.util.List
import java.util.Map;

import javax.activation.DataSource

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

class SqlExecuteTask extends AbstractDbTask {
	
	@Input
	DataSourcePojo dataSource;
	
	@Input
	@Optional
	def sqlText=null;
	@Input
	@Optional
	def sqlFiles=null;
	/**encoding*/
	@Input
	@Optional
	String encoding="UTF-8";
	@Input
	@Optional
	String placeholderPrefix='${';
	@Input
	@Optional
	String placeholderSuffix='}';
	@Input
	@Optional
	Boolean placeholders=false;

	@TaskAction
	def exec() {
		SqlExecuteCommand command=new SqlExecuteCommand();
		initialize(command);
		run(command);
	}

	protected void initialize(SqlExecuteCommand command){
		command.dataSource=this.createDataSource(this.dataSource, this.debug);
		command.sqlFiles=getFiles(this.sqlFiles);
		command.sqlText=sqlText;
		command.encoding=encoding;
		command.placeholderPrefix=placeholderPrefix;
		command.placeholderSuffix=placeholderSuffix;
		command.placeholders=placeholders;
	}

	void dataSource(Closure closure) {
		if (this.dataSource==null){
			this.dataSource=project.configure(new DataSourcePojo(), closure)
		}else{
			project.configure(this.dataSource, closure)
		}
	 }

	void dataSource(DataSourcePojo dataSource) {
		this.dataSource=dataSource
	}

	void sqlTexts(def sqlText){
		this.sqlText=sqlText;
	}

	
	void sqlFiles(def sqlFiles){
		this.sqlFiles=sqlFiles;
	}

	
	void encoding(String encoding){
		this.encoding=encoding;
	}
	
	void placeholderPrefix(String placeholderPrefix){
		this.placeholderPrefix=placeholderPrefix;
	}
	
	void placeholderSuffix(String placeholderSuffix){
		this.placeholderSuffix=placeholderSuffix;
	}
	
	void placeholders(boolean placeholders){
		this.placeholders=placeholders;
	}
}
