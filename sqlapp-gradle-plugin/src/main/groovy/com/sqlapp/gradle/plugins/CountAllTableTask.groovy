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

import com.sqlapp.data.db.command.CountAllTablesCommand
import com.sqlapp.gradle.plugins.pojo.CountAllTablePojo
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.Optional;

class CountAllTableTask extends AbstractDbTask {
	
	@TaskAction
	def exec() {
		CountAllTablesCommand command=new CountAllTablesCommand();
		CountAllTablePojo pojo=project.countAllTables;
		this.pojo=pojo;
		command.setDataSource(this.createDataSource());
		command.onlyCurrentCatalog=pojo.onlyCurrentCatalog;
		command.onlyCurrentSchema=pojo.onlyCurrentSchema;
		command.includeSchemas=pojo.includeSchemas;
		command.excludeSchemas=pojo.excludeSchemas;
		command.includeTables=pojo.includeTables;
		command.excludeTables=pojo.excludeTables;
		command.outputFormatType=pojo.outputFormatType;
		run(command);
	}
	
}
