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

import java.io.File
import java.util.List;

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

class DbTablePojo extends DbSchemaPojo{
	/**
	 * ダンプに含めるテーブル
	 */
	@Input
	@Optional
	String[] includeTables = null;
	/**
	 * ダンプから除くテーブル
	 */
	@Input
	@Optional
	String[] excludeTables = null;

	DbTablePojo(Project project) {
		super(project);
	}
	
	void includeTables(String... includeTables){
		this.includeTables=includeTables;
	}
	
	void setIncludeTables(Object... includeTables){
		this.includeTables=includeTables;
	}

	void excludeTables(String... excludeTables){
		this.excludeTables=excludeTables;
	}

	void setExcludeTables(Object... excludeTables){
		this.excludeTables=excludeTables;
	}

}
