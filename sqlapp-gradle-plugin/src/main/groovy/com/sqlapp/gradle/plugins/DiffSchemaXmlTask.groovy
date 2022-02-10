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

import com.sqlapp.data.db.command.DiffCommand
import com.sqlapp.data.db.sql.SqlType
import com.sqlapp.gradle.plugins.pojo.DiffSchemaXmlPojo
import com.sqlapp.util.CommonUtils;

import java.io.File
import java.util.List;

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

class DiffSchemaXmlTask extends AbstractTask {
	
	@TaskAction
	def exec() {
		DiffCommand command=new DiffCommand();
		DiffSchemaXmlPojo pojo=project.diffSchemaXml;
		this.pojo=pojo;
		command.originalFile=this.getFile(pojo.originalFile);
		command.targetFile=this.getFile(pojo.targetFile);
		command.equalsHandler=pojo.equalsHandler;
		run(command);
	}

}
