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

import com.sqlapp.data.converter.Converters
import com.sqlapp.data.db.command.export.ConvertDataFileCommand
import com.sqlapp.data.schemas.rowiterator.WorkbookFileType
import com.sqlapp.util.CommonUtils

import java.io.File
import java.nio.charset.Charset
import java.util.List
import java.util.Map;
import java.util.function.Predicate

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;
import com.sqlapp.util.FileUtils;
import com.sqlapp.util.JsonConverter

import groovy.lang.Closure

class ConvertDataFileTask extends AbstractTask {
	
	/**
	 * Output Direcroty
	 */
	@InputDirectory
	def directory=new File(".");
	
	/**file filter*/
	@Input
	@Optional
	Predicate<File> fileFilter=null;

	@Input
	@Optional
	String csvEncoding=Charset.defaultCharset().toString();

	@Input
	@Optional
	JsonConverter jsonConverter=null;
	
	@Input
	@Optional
	boolean recursive=false;

	@Input
	@Optional
	String sheetName="TABLE";
	/**
	 * Output File Type
	 */
	@Input
	@Optional
	String outputFileType="xlsx";
	
	@Input
	@Optional
	Converters converters =new Converters();
	
	@Input
	@Optional
	boolean removeOriginalFile=false;
	
	/**
	 * Output Direcroty
	 */
	@InputDirectory
	@Optional
	def outputDirectory=null;
	
	@TaskAction
	def exec() {
		ConvertDataFileCommand command=new ConvertDataFileCommand();
		initialize(command);
		run(command);
	}
	
	protected void initialize(ConvertDataFileCommand command){
		command.directory=this.getFile(this.directory);
		if (this.fileFilter!=null){
			command.fileFilter=this.fileFilter;
		}
		if (this.csvEncoding!=null){
			command.csvEncoding=this.csvEncoding;
		}
		if (this.jsonConverter!=null){
			command.jsonConverter=this.jsonConverter;
		}
		command.recursive=this.recursive;
		if (this.sheetName!=null){
			command.sheetName=this.sheetName;
		}
		WorkbookFileType workbookFileType=WorkbookFileType.parse(this.outputFileType);
		if (workbookFileType!=null){
			command.outputFileType=workbookFileType;
		}
		if (this.converters!=null){
			command.converters=this.converters;
		}
		command.removeOriginalFile=this.removeOriginalFile;
		if (this.outputDirectory!=null){
			command.outputDirectory=this.outputDirectory;
		}
	}

	void directory(def directory){
		this.directory=directory;
	}

	void fileFilter(Predicate<File> fileFilter){
		this.fileFilter=fileFilter;
	}
	
	void csvEncoding(String csvEncoding){
		this.csvEncoding=csvEncoding;
	}

	void jsonConverter(Closure closure) {
		if (this.jsonConverter==null){
			this.jsonConverter=project.configure(new JsonConverter(), closure)
		}else{
			project.configure(this.jsonConverter, closure)
		}
	}
	
	void recursive(boolean recursive){
		this.recursive=recursive;
	}

	void sheetName(String sheetName){
		this.sheetName=sheetName;
	}

	void converters(Closure closure){
		if (this.converters==null){
			Converters converters=new Converters();
			this.converters=project.configure(converters, closure)
		}else{
			project.configure(this.converters, closure)
		}
	}
	
	void removeOriginalFile(boolean removeOriginalFile){
		this.removeOriginalFile=removeOriginalFile;
	}

	void outputDirectory(def outputDirectory){
		this.outputDirectory=outputDirectory;
	}

}
