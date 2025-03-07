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

import com.sqlapp.data.db.command.OutputFormatType
import com.sqlapp.data.db.command.SqlQueryCommand
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.gradle.plugins.pojo.ChangeTablePojo
import com.sqlapp.gradle.plugins.pojo.DataSourcePojo
import com.sqlapp.util.CommonUtils

import java.io.File
import java.util.List
import java.util.Map;
import javax.activation.DataSource
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import com.sqlapp.util.FileUtils;

import groovy.lang.Closure

class SqlQueryTask extends AbstractDbTask {
	
	@Input
	DataSourcePojo dataSource;
	
	@Input
	@Optional
	def sql=null;
	@InputFile
	@Optional
	def sqlFile=null;
	@Input
	@Optional
	String encoding="UTF-8";
	@Input
	@Optional
	OutputFormatType outputFormatType=null;

	@TaskAction
	def exec() {
		SqlQueryCommand command=new SqlQueryCommand();
		initialize(command);
		run(command);
	}
	
	protected void initialize(SqlQueryCommand command){
		command.dataSource=this.createDataSource(this);
		if (this.sqlFile!=null){
			command.sql=FileUtils.readText(this.getFile(this.sqlFile), this.encoding);
		} else{
			command.sql=sql;
		}
		if (this.outputFormatType!=null){
			command.outputFormatType=this.outputFormatType;
		}
	}
	
	void dataSource(Closure closure) {
		if (this.dataSource==null){
			this.dataSource=project.configure(new DataSourcePojo(this.project), closure)
		}else{
			project.configure(this.dataSource, closure)
		}
	 }

	void dataSource(DataSourcePojo dataSource) {
		this.dataSource=dataSource
	}
	
	void sql(def sql){
		this.sql=sql;
	}

	void sqlFile(def sqlFile){
		this.sqlFile=sqlFile;
	}
	
	void encoding(String encoding){
		this.encoding=encoding;
	}

	void setOutputFormatType(String value){
		this.outputFormatType=OutputFormatType.parse(value);
	}
}
