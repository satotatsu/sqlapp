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

import java.util.function.Predicate

import org.gradle.api.Project
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional;

abstract class UpdateDictionariesPojo extends AbstractHtmlPojo{


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
