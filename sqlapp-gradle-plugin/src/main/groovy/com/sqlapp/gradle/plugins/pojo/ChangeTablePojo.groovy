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

import com.sqlapp.util.CommonUtils;

import java.io.File
import java.util.List;

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

class ChangeTablePojo extends AbstractPojo{

	public ChangeTablePojo(Project project) {
		super(project);
	}
	
	/**Schema Change log table name*/
	@Input
	@Optional
	String name="changelog";
	@Input
	@Optional
	String idColumnName="change_number";
	@Input
	@Optional
	String appliedByColumnName="applied_by";
	@Input
	@Optional
	String appliedAtColumnName="applied_at";
	@Input
	@Optional
	String descriptionColumnName="description";
	@Input
	@Optional
	String seriesNumberColumnName="series_number";

	void name(String name){
		this.name=name;
	}

	void idColumnName(String idColumnName){
		this.idColumnName=idColumnName;
	}

	void appliedByColumnName(String appliedByColumnName){
		this.appliedByColumnName=appliedByColumnName;
	}

	void appliedAtColumnName(String appliedAtColumnName){
		this.appliedAtColumnName=appliedAtColumnName;
	}

	void descriptionColumnName(String descriptionColumnName){
		this.descriptionColumnName=descriptionColumnName;
	}

	void seriesNumberColumnName(String seriesNumberColumnName){
		this.seriesNumberColumnName=seriesNumberColumnName;
	}

	@Override
	ChangeTablePojo clone(){
		return super.clone();
	}
}
