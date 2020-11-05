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

import com.sqlapp.util.CommonUtils

import java.io.File
import java.util.List
import java.util.Map;

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

abstract class AbstractPojo implements Cloneable{
	
	final Project project
	
	@Input
	@Optional
	boolean debug = false;

	@Input
	@Optional
	Map<String,Object> parameters= CommonUtils.linkedMap();
	
	AbstractPojo(Project project) {
		this.project=project
	}
	
	@Override
	AbstractPojo clone(){
		AbstractPojo clone=super.clone();
		if (this.parameters!=null){
			clone.parameters=CommonUtils.linkedMap(this.parameters);
		}
		return clone;
	}
}
