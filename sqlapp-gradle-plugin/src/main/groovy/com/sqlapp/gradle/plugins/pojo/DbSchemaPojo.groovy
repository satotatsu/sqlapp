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

import com.sqlapp.util.CommonUtils

import groovy.lang.Closure

import java.io.File
import java.util.List;

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

class DbSchemaPojo extends DbPojo{
	DbSchemaPojo(Project project) {
		super(project);
	}
	/**
	 * 現在のカタログのみを対象とするフラグ
	 */
	@Input
	@Optional
	Boolean onlyCurrentCatalog = true;
	/**
	 * 現在のスキーマのみを対象とするフラグ
	 */
	@Input
	@Optional
	Boolean onlyCurrentSchema = false;
	/**
	 * ダンプに含めるスキーマ
	 */
	@Input
	@Optional
	String[] includeSchemas = null;
	/**
	 * ダンプから除くスキーマ
	 */
	@Input
	@Optional
	String[] excludeSchemas = null;
	
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
	
	void onlyCurrentCatalog(boolean onlyCurrentCatalog){
		this.onlyCurrentCatalog=onlyCurrentCatalog;
	}

	void onlyCurrentSchema(boolean onlyCurrentSchema){
		this.onlyCurrentSchema=onlyCurrentSchema;
	}

	void includeSchemas(String... includeSchemas){
		this.includeSchemas=includeSchemas;
	}

	void includeSchemas(Object... includeSchemas){
		this.includeSchemas=includeSchemas;
	}
	
	void excludeSchemas(String... excludeSchemas){
		this.excludeSchemas=excludeSchemas;
	}

	void excludeSchemas(Object... excludeSchemas){
		this.excludeSchemas=excludeSchemas;
	}

}
