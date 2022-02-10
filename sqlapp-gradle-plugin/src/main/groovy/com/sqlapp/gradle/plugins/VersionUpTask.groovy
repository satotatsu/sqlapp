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

import com.sqlapp.data.db.command.version.VersionUpCommand
import com.sqlapp.data.db.sql.SqlType
import com.sqlapp.data.schemas.Table;
import com.sqlapp.gradle.plugins.pojo.VersionUpPojo
import com.sqlapp.util.CommonUtils;

import java.io.File
import java.util.List;

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

class VersionUpTask extends AbstractDbTask {
	
	@TaskAction
	def exec() {
		VersionUpCommand command=createVersionUpCommand();
		initialize(command);
		run(command);
	}

	protected VersionUpCommand createVersionUpCommand(){
		VersionUpCommand command=new VersionUpCommand();
		return command;	
	}
	
	protected void initialize(VersionUpCommand command){
		VersionUpPojo pojo=getVersionUpPojo();
		this.pojo=pojo;
		command.setDataSource(this.createDataSource());
		if (pojo.getFileDirectory()!=null){
			command.fileDirectory=getFile(pojo.fileDirectory);
		}
		command.encoding=pojo.encoding;
		command.placeholders=pojo.placeholders;
		command.placeholderPrefix=pojo.placeholderPrefix;
		command.placeholderSuffix=pojo.placeholderSuffix;
		command.sqlDirectory=getFile(pojo.sqlDirectory);
		if (pojo.getDownSqlDirectory()!=null){
			command.downSqlDirectory=getFile(pojo.downSqlDirectory);
		}
		command.lastChangeToApply=pojo.lastChangeNumber;
		command.showVersionOnly=pojo.showVersionOnly;
		command.withSeriesNumber=pojo.withSeriesNumber;
		command.setupSqlDirectory=pojo.setupSqlDirectory;
		command.finalizeSqlDirectory=pojo.finalizeSqlDirectory;
		if (pojo.changeTable!=null){
			command.schemaChangeLogTableName=pojo.changeTable.name;
			command.idColumnName=pojo.changeTable.idColumnName;
			command.appliedByColumnName=pojo.changeTable.appliedByColumnName;
			command.appliedAtColumnName=pojo.changeTable.appliedAtColumnName;
			command.descriptionColumnName=pojo.changeTable.descriptionColumnName;
			command.seriesNumberColumnName=pojo.changeTable.seriesNumberColumnName;
		}
	}

	protected VersionUpPojo getVersionUpPojo(){
		return project.versionUp;
	}

}
