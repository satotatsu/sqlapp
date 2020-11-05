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

import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.util.CommonUtils

import java.io.File
import java.util.List;
import java.util.function.Predicate

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import java.nio.charset.Charset;
import java.sql.Connection;
import java.sql.SQLException;

class ImportDataPojo extends AbstractExportDataPojo{
	ImportDataPojo(Project project) {
		super(project);
	}
	
	@Input
	@Optional
	boolean useTableNameDirectory=false;
	
	@Input
	@Optional
	long queryCommitInterval=Long.MAX_VALUE;
	
	/**file directory*/
	@InputDirectory
	@Optional
	def fileDirectory=null;
	/**SQL Type*/
	@Input
	@Optional
	SqlType sqlType=SqlType.MERGE_ROW;
	@Input
	@Optional
	String placeholderPrefix='${';
	@Input
	@Optional
	String placeholderSuffix='}';
	@Input
	@Optional
	boolean placeholders=false;
	@Input
	@Optional
	Predicate<File> fileFilter;
	
	void fileDirectory(def fileDirectory){
		this.fileDirectory=fileDirectory;
	}
	
	void sqlType(def sqlType){
		setSqlType(sqlType);
	}

	void setSqlType(def sqlType){
		if (sqlType ==null){
			this.sqlType=SqlType.CREATE;
		}else if (sqlType instanceof SqlType){
			this.sqlType=sqlType;
		} else{
			this.sqlType=SqlType.parse(sqlType.toString());
		}
	}
	
	void useTableNameDirectory(boolean useTableNameDirectory){
		this.useTableNameDirectory=useTableNameDirectory;
	}
	
	void queryCommitInterval(long queryCommitInterval){
		this.queryCommitInterval=queryCommitInterval;
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

	void fileFilter(Predicate<File> fileFilter){
		this.fileFilter=fileFilter;
	}
}
