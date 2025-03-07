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

import com.sqlapp.data.db.command.html.RenderOptions
import com.sqlapp.data.schemas.ForeignKeyConstraint
import com.sqlapp.util.CommonUtils

import groovy.lang.Closure

import java.io.File
import java.util.List;
import java.util.function.Function
import java.util.function.Predicate

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

class GenerateHtmlPojo extends AbstractHtmlPojo{
	
	@Input
	@Optional
	RenderOptionsPojo renderOptions=new RenderOptionsPojo(this.getProject());
	
	/**
	 * file
	 */
	@InputDirectory
	@Optional
	def outputDirectory=new File("./");
	
	@Input
	@Optional
	String diagramFont=null;
	@Input
	@Optional
	String diagramFormat=null;

	@Input
	@Optional
	String dot=null;

	@Input
	@Optional
	Boolean multiThread=true;
	
	@Input
	@Optional
	String placeholderPrefix="\${";

	@Input
	@Optional
	String placeholderSuffix="}";

	@Input
	@Optional
	Boolean placeholders=false;
	@InputDirectory
	@Optional
	def fileDirectory=new File("./");
	@InputDirectory
	@Optional
	def directory=new File(".");
	
	@Input
	@Optional
	boolean useSchemaNameDirectory=false;
	@Input
	@Optional
	boolean useTableNameDirectory=false;
	
	/**file filter*/
	@Input
	@Optional
	Predicate<File> fileFilter={f->true};

	/**Virtual foreign Key definitions*/
	@InputDirectory
	@Optional
	def foreignKeyDefinitionDirectory=new File(".");

	/** virtualForeignKeyLabel */
	@Input
	@Optional
	Function<ForeignKeyConstraint, String> virtualForeignKeyLabel = {fk -> "Virtual"};

	
	GenerateHtmlPojo(Project project) {
		super(project);
	}

	void renderOptions(Closure closure) {
		if (this.renderOptions==null){
			this.renderOptions=project.configure(new RenderOptionsPojo(this.getProject()), closure)
		}else{
			project.configure(this.renderOptions, closure)
		}
	}
	
	void outputDirectory(def outputDirectory){
		this.outputDirectory=outputDirectory;
	}

	void diagramFont(String diagramFont){
		this.diagramFont=diagramFont;
	}

	void diagramFormat(String diagramFormat){
		this.diagramFormat=diagramFormat;
	}
	
	void multiThread(boolean multiThread){
		this.multiThread=multiThread;
	}
	
	void setMultiThread(Boolean multiThread){
		this.multiThread=multiThread!=null?multiThread:true;
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

	void setPlaceholders(boolean placeholders){
		this.placeholders=placeholders!=null?placeholders:false;
	}

	void fileDirectory(def fileDirectory){
		this.fileDirectory=fileDirectory;
	}
	
	void directory(def directory){
		this.directory=directory;
	}

	void useSchemaNameDirectory(boolean useSchemaNameDirectory){
		this.useSchemaNameDirectory=useSchemaNameDirectory;
	}

	void useTableNameDirectory(boolean useTableNameDirectory){
		this.useTableNameDirectory=useTableNameDirectory;
	}
	
	void fileFilter(Predicate<File> fileFilter){
		this.fileFilter=fileFilter;
	}
	
	void foreignKeyDefinitionDirectory(def foreignKeyDefinitionDirectory){
		this.foreignKeyDefinitionDirectory=foreignKeyDefinitionDirectory;
	}

	void virtualForeignKeyLabel(Function<ForeignKeyConstraint, String> virtualForeignKeyLabel){
		this.virtualForeignKeyLabel=virtualForeignKeyLabel;
	}

	void virtualForeignKeyLabel(String virtualForeignKeyLabel){
		this.virtualForeignKeyLabel= {fk->virtualForeignKeyLabel};
	}
	
	@Override
	GenerateHtmlPojo clone(){
		GenerateHtmlPojo clone=super.clone();
		return clone;
	}

}
