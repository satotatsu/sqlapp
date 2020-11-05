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

import com.sqlapp.data.db.command.ExportXmlCommand
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.DbObject
import com.sqlapp.gradle.plugins.pojo.ExportXmlPojo
import com.sqlapp.util.CommonUtils;

import java.io.File
import java.util.List;
import java.util.function.Consumer

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;

class ExportXmlTask extends AbstractDbTask {

	@TaskAction
	def exec() {
		ExportXmlCommand command=new ExportXmlCommand();
		ExportXmlPojo pojo=project.exportXml;
		this.pojo=pojo;
		command.setDataSource(this.createDataSource());
		command.target=pojo.target;
		command.outputPath=getFile(pojo.outputPath);
		command.outputFileName=pojo.outputFileName;
		command.onlyCurrentCatalog=pojo.onlyCurrentCatalog;
		command.onlyCurrentSchema=pojo.onlyCurrentSchema;
		command.includeSchemas=pojo.includeSchemas;
		command.excludeSchemas=pojo.excludeSchemas;
		command.includeObjects=pojo.includeObjects;
		command.excludeObjects=pojo.excludeObjects;
		command.dumpRows=pojo.dumpRows;
		command.includeRowDumpTables=pojo.includeRowDumpTables;
		command.excludeRowDumpTables=pojo.excludeRowDumpTables;
		command.converter=pojo.converter;
		boolean bool=pojo.schemaOptions instanceof com.sqlapp.data.db.sql.Options
		com.sqlapp.data.db.sql.Options options=pojo.schemaOptions;
		command.options=options;
		run(command);
	}

	
}
