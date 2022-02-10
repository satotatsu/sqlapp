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

import com.sqlapp.data.schemas.AbstractNamedObject
import com.sqlapp.util.CommonUtils

import groovy.lang.Closure

import java.io.File
import java.util.List;
import java.util.function.Predicate

import org.gradle.api.DefaultTask
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFile
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

class UpdateDictionariesPojo extends AbstractHtmlGojo{
	
	
	@Input
	@Optional
	Predicate<String> withSchema;
	
	@Input
	@Optional
	boolean outputRemarksAsDisplayName=true;
	
	UpdateDictionariesPojo(Project project) {
		super(project);
	}

	@Override
	UpdateDictionariesPojo clone(){
		UpdateDictionariesPojo clone=super.clone();
		return clone;
	}

	void withSchema(Predicate<String> withSchema){
		this.withSchema=withSchema;
	}
	
	void outputRemarksAsDisplayName(boolean outputRemarksAsDisplayName){
		this.outputRemarksAsDisplayName=outputRemarksAsDisplayName;
	}
}
