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

package com.sqlapp.gradle.plugins

import com.sqlapp.data.db.command.version.VersionUpCommand
import com.sqlapp.data.db.command.version.VersionRepairCommand
import com.sqlapp.data.db.sql.SqlType
import com.sqlapp.data.schemas.Table;
import com.sqlapp.gradle.plugins.pojo.VersionUpPojo
import com.sqlapp.util.CommonUtils;

import java.io.File
import java.util.List;

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

class VersionRepairTask extends VersionUpTask {
	
	@Override
	protected VersionUpCommand createVersionUpCommand(){
		VersionRepairCommand command=new VersionRepairCommand();
		return command;	
	}
	
	@Override
	protected void initialize(VersionUpCommand command){
		super.initialize(command);
		command.lastChangeToApply=null;
	}
	
	@Override
	protected VersionUpPojo getVersionUpPojo(){
		VersionUpPojo parent=super.getVersionUpPojo();
		VersionUpPojo own=project.versionUp;
		if (own==null){
			return parent.clone();
		}
		if (parent!=null){
			parent=parent.clone();
			if (own.getParameters()==null||own.getParameters().isEmpty()){
				own.parameters=parent.parameters;
			}
			if (own.dataSource==null){
				own.dataSource=parent.dataSource;
			}
			if (own.changeTable==null){
				own.changeTable=parent.changeTable;
			}
		}
		return own;
	}
}
