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

import com.sqlapp.util.CommonUtils

import java.io.File
import java.util.List;

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

class DropObjectsPojo extends DbSchemaPojo{
	/**
	 * Include drop target Objects
	 */
	@Input
	@Optional
	String[] includeObjects = null;
	/**
	 * Exclude drop target Objects
	 */
	@Input
	@Optional
	String[] excludeObjects = null;
	/**
	 * オブジェクトのDROPを実施
	 */
	@Input
	@Optional
	boolean dropObjects=false;
	/**
	 * テーブルのDROPを実施
	 */
	@Input
	@Optional
	boolean dropTables=false;
	@Input
	@Optional
	String preDropTableSql;
	@Input
	@Optional
	String afterDropTableSql;
	
	DropObjectsPojo(Project project) {
		super(project);
	}
	
	void includeObjects(String... includeObjects){
		this.includeObjects=includeObjects;
	}

	void setIncludeObjects(Object... includeObjects){
		this.includeObjects=includeObjects;
	}

	void excludeObjects(String... excludeObjects){
		this.excludeObjects=excludeObjects;
	}

	void setExcludeObjects(Object... excludeObjects){
		this.excludeObjects=excludeObjects;
	}

	void dropObjects(boolean dropObjects){
		this.dropObjects=dropObjects;
	}

	void dropTables(boolean dropTables){
		this.dropTables=dropTables;
	}

	void preDropTableSql(boolean preDropTableSql){
		this.preDropTableSql=preDropTableSql;
	}

	void afterDropTableSql(boolean afterDropTableSql){
		this.afterDropTableSql=afterDropTableSql;
	}
}
