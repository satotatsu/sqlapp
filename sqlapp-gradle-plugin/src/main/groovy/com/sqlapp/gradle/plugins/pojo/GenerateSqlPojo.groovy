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

import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.util.CommonUtils

import groovy.lang.Closure

import java.io.File
import java.util.List;

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

class GenerateSqlPojo extends AbstractGenerateSqlPojo{
	GenerateSqlPojo(Project project) {
		super(project);
	}

	SqlType sqlType=SqlType.CREATE;
	
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

}
