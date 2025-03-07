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

import groovy.lang.Closure

import java.io.File
import java.util.List;
import javax.sql.DataSource
import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

class DbPojo extends AbstractPojo{
	
	DbPojo(Project project) {
		super(project)
	}

	@Input
	@Optional
	DataSourcePojo dataSource;

	@Input
	@Optional
	DataSource dataSourceImpl;

	void dataSource(Closure closure) {
		if (this.dataSource==null){
			if (this.dataSourceImpl==null){
				this.dataSource=project.configure(new DataSourcePojo(), closure)
			} else {
				project.configure(this.dataSourceImpl, closure)
			}
		}else{
			if (this.dataSourceImpl==null){
				project.configure(this.dataSource, closure)
			} else {
				project.configure(this.dataSourceImpl, closure)
			}
		}
	}

	void setDataSource(DataSourcePojo dataSource) {
		this.dataSource=dataSource
	}

	void setDataSourceImpl(DataSource dataSourceImpl) {
		this.dataSourceImpl=dataSourceImpl
	}

	@Override
	DbPojo clone(){
		DbPojo clone= super.clone();
		if (this.dataSource!=null){
			clone.dataSource=this.dataSource.clone();
		}
		if (this.dataSourceImpl!=null){
			clone.dataSourceImpl=this.dataSourceImpl;
		}
		return clone;
	}

}
