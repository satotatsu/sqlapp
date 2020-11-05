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

import com.sqlapp.data.db.command.export.ImportDataFromFileCommand
import com.sqlapp.data.db.sql.Options;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.gradle.plugins.pojo.ImportDataPojo
import java.io.File;

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

class ImportDataTask extends AbstractDbTask {
	
	@TaskAction
	def exec() {
		ImportDataFromFileCommand command=new ImportDataFromFileCommand();
		initialize(command);
		run(command);
	}
	
	protected void initialize(ImportDataFromFileCommand command){
		ImportDataPojo pojo=project.importData;
		this.pojo=pojo;
		command.setDataSource(this.createDataSource());
		command.onlyCurrentCatalog=pojo.onlyCurrentCatalog;
		command.onlyCurrentSchema=pojo.onlyCurrentSchema;
		command.includeSchemas=pojo.includeSchemas;
		command.excludeSchemas=pojo.excludeSchemas;
		command.includeTables=pojo.includeTables;
		command.excludeTables=pojo.excludeTables;
		command.directory=getFile(pojo.directory);
		command.useSchemaNameDirectory=pojo.useSchemaNameDirectory;
		command.useTableNameDirectory=pojo.useTableNameDirectory;
		if (pojo.csvEncoding!=null){
			command.csvEncoding=pojo.csvEncoding;
		}
		if (pojo.tableOptions!=null){
			command.tableOptions=pojo.tableOptions;
		}
		command.queryCommitInterval=pojo.queryCommitInterval;
		if (pojo.fileDirectory!=null){
			command.fileDirectory=getFile(pojo.fileDirectory);
		}
		if (pojo.sqlType!=null){
			command.sqlType=pojo.sqlType;
		}
		if (pojo.fileFilter!=null){
			command.fileFilter=pojo.fileFilter;
		}
		command.placeholders=pojo.placeholders;
		command.placeholderPrefix=pojo.placeholderPrefix;
		command.placeholderSuffix=pojo.placeholderSuffix;
	}
}