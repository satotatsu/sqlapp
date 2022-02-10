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

package com.sqlapp.gradle.plugins.pojo;
import org.gradle.api.Project;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;

import com.sqlapp.data.db.sql.Options;
import com.sqlapp.data.db.sql.TableOptions;

import groovy.lang.Closure;

public class OptionsPojo extends Options{
	
	Project project;

	public OptionsPojo(Project project) {
		this.project=project;
	}
	
	@Input
	@Optional
	public void setTableOptions(TableOptions tableOption) {
		super.setTableOptions(tableOption);
	}
	
	void tableOptions(Closure<TableOptionsPojo> closure) {
		if (this.getTableOptions()==null){
			this.setTableOptions((TableOptionsPojo)project.configure(new TableOptionsPojo(project), closure));
		}else{
			this.setTableOptions(project.configure(new TableOptionsPojo(this.getTableOptions(), project), closure));
		}
 	}
	
	void outputCommit(boolean outputCommit){
		this.setOutputCommit(outputCommit);
	}

	void dropIfExists(boolean dropIfExists){
		this.setDropIfExists(dropIfExists);
	}

	void createIfNotExists(boolean createIfNotExists){
		this.setCreateIfNotExists(createIfNotExists);
	}

	void decorateSchemaName(boolean decorateSchemaName){
		this.setDecorateSchemaName(decorateSchemaName);
	}

	void setSearchPathToSchema(boolean setSearchPathToSchema){
		this.setSetSearchPathToSchema(setSearchPathToSchema);
	}
	
}
