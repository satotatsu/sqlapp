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

package com.sqlapp.gradle.plugins.pojo

import com.sqlapp.data.db.sql.SqlType
import com.sqlapp.util.CommonUtils
import com.sqlapp.util.FileUtils;

import groovy.lang.Closure

import java.io.File
import java.util.List;

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

abstract class AbstractGenerateSqlPojo extends AbstractPojo{

	AbstractGenerateSqlPojo(Project project) {
		super(project);
		schemaOptions=new OptionsPojo(project);
	}

	/**
	 * Output targetFile
	 */
	@InputFile
	@Optional
	def targetFile;
	/**
	 * 出力ファイルパス
	 */
	@InputDirectory
	@Optional
	def outputPath;
	/**
	 * 出力ファイルエンコーディング
	 */
	@Input
	@Optional
	String encoding="UTF-8";
	/**
	 * 複数ファイル出力
	 */
	@Input
	@Optional
	boolean outputAsMultiFiles=true;
	
	@Input
	@Optional
	String outputFileExtension="sql"
	
	@Input
	@Optional
	Long lastChangeNumber=null;
	
	@Input
	@Optional
	Long changeNumberStep=null;
	@Input
	@Optional
	Integer numberOfDigits=19;
	
	@Input
	@Optional
	OptionsPojo schemaOptions=null;

	void schemaOptions(Closure closure) {
		if (this.schemaOptions==null){
			this.schemaOptions=project.configure(new OptionsPojo(project), closure)
		}else{
			project.configure(this.schemaOptions, closure)
		}
	}

	void schemaOptions(OptionsPojo schemaOption) {
		this.schemaOptions=schemaOption
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
	
	void targetFile(def targetFile) {
		this.targetFile=targetFile
	}

	void outputPath(def outputPath) {
		this.outputPath=outputPath
	}

	void encoding(String encoding) {
		this.encoding=encoding
	}

	void outputAsMultiFiles(boolean outputAsMultiFiles) {
		this.outputAsMultiFiles=outputAsMultiFiles
	}
	
	void lastChangeNumber(String lastChangeNumber){
		this.setLastChangeNumber(lastChangeNumber);
	}
	
	void setLastChangeNumber(String lastChangeNumber){
		if (lastChangeNumber!=null){
			this.lastChangeNumber=Long.valueOf(lastChangeNumber);
		}
	}
	
	void changeNumberStep(String changeNumberStep){
		this.setChangeNumberStep(changeNumberStep);
	}
	
	void setChangeNumberStep(String changeNumberStep){
		if (changeNumberStep!=null){
			this.changeNumberStep=Long.valueOf(changeNumberStep);
		}
	}

	void numberOfDigits(String numberOfDigits){
		this.setNumberOfDigits(numberOfDigits);
	}

	void setNumberOfDigits(String numberOfDigits){
		if (numberOfDigits!=null){
			this.numberOfDigits=Long.valueOf(numberOfDigits);
		}
	}

}
