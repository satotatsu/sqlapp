/**
 * Copyright (C) 2007-2025 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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

package com.sqlapp.gradle.plugins;

import java.io.File;
import java.util.function.Predicate;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.generator.ConvertGeneratorSettingCommand;
import com.sqlapp.data.db.command.generator.GeneratorSettingFileType;
import com.sqlapp.gradle.plugins.properties.DirectoryTaskProperty;
import com.sqlapp.gradle.plugins.properties.FileFilterTaskProperty;
import com.sqlapp.gradle.plugins.properties.RecursiveTaskProperty;
import com.sqlapp.gradle.plugins.properties.RemoveOriginalFileTaskProperty;

@DisableCachingByDefault
public abstract class ConvertGeneratorSettingFileTask extends AbstractTask<ConvertGeneratorSettingCommand, Void>
		implements DirectoryTaskProperty, FileFilterTaskProperty, RecursiveTaskProperty,
		RemoveOriginalFileTaskProperty {
	@Inject
	public ConvertGeneratorSettingFileTask(ObjectFactory objectFactory) {
		super(objectFactory);
	}

	public void call(Action<ConvertGeneratorSettingFileTask> cons) {
		cons.execute(this);
	}

	/** file filter */
	@Input
	@Optional
	public Predicate<File> fileFilter;

	@Override
	public Predicate<File> getFileFilter() {
		return this.fileFilter;
	}

	@Override
	public void setFileFilter(Predicate<File> fileFilter) {
		this.fileFilter = fileFilter;
	}

	@Input
	@Optional
	public abstract Property<Boolean> getRecursive();

	@Input
	@Optional
	public abstract Property<Boolean> getRemoveOriginalFile();

	@Input
	@Optional
	public abstract Property<String> getFileType();

	@Override
	protected Void createExtension(Project project) {
		return null;
	}

	@Override
	protected ConvertGeneratorSettingCommand createCommand() {
		return new ConvertGeneratorSettingCommand();
	}

	@Override
	protected void beforeRun(ConvertGeneratorSettingCommand command) {
		if (this.getFileType().isPresent()) {
			command.setFileType(GeneratorSettingFileType.parse(this.getFileType().get()));
		}
	}

}