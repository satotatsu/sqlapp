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

import com.sqlapp.data.schemas.DbObject;
import com.sqlapp.util.CommonUtils

import java.io.File
import java.util.List
import java.util.function.Consumer;

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

class ExportXmlPojo extends DbSchemaPojo{
	ExportXmlPojo(Project project) {
		super(project);
	}
	/**
	 * 対象オブジェクト
	 */
	@Input
	@Optional
	String target="catalog";
	/**
	 * Output Path
	 */
	@InputDirectory
	def outputPath;
	/**
	 * Output FileName
	 */
	@Input
	@Optional
	String outputFileName;
	/**
	 * ダンプに含めるオブジェクト
	 */
	@Input
	@Optional
	String[] includeObjects = null;
	/**
	 * ダンプから除くオブジェクト
	 */
	@Input
	@Optional
	String[] excludeObjects = null;
	/**
	 * 行のダンプ
	 */
	@Input
	@Optional
	Boolean dumpRows = false;
	/**
	 * 行のダンプを行うテーブル
	 */
	@Input
	@Optional
	String[] includeRowDumpTables = null;
	/**
	 * 行のダンプから除くテーブル
	 */
	@Input
	@Optional
	String[] excludeRowDumpTables = null;
	
	@Input
	@Optional
	Consumer<DbObject<?>> converter={o->
	};

	void target(String target){
		this.target=target;
	}

	void outputPath(def outputPath){
		this.outputPath=outputPath;
	}

	void outputFileName(String outputFileName){
		this.outputFileName=outputFileName;
	}

	void includeObjects(String... includeObjects){
		this.includeObjects=includeObjects;
	}

	void includeObjects(Object... includeObjects){
		this.includeObjects=includeObjects;
	}

	void excludeObjects(String... excludeObjects){
		this.excludeObjects=excludeObjects;
	}

	void excludeObjects(Object... excludeObjects){
		this.excludeObjects=excludeObjects;
	}

	void includeRowDumpTables(String... includeRowDumpTables){
		this.includeRowDumpTables=includeRowDumpTables;
	}
	
	void includeRowDumpTables(Object... includeRowDumpTables){
		this.includeRowDumpTables=includeRowDumpTables;
	}
	
	void excludeRowDumpTables(String... excludeRowDumpTables){
		this.excludeRowDumpTables=excludeRowDumpTables;
	}

	void excludeRowDumpTables(Object... excludeRowDumpTables){
		this.excludeRowDumpTables=excludeRowDumpTables;
	}
}
