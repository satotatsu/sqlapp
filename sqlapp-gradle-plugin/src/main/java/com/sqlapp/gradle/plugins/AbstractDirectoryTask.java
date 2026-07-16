/**
 * Copyright (C) 2026-2026 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
import java.util.List;
import java.util.Set;

import javax.inject.Inject;

import org.gradle.api.Project;
import org.gradle.api.file.ConfigurableFileTree;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.FileTree;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.options.Option;
import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.properties.FilesProperty;
import com.sqlapp.util.CommonUtils;

@DisableCachingByDefault
public abstract class AbstractDirectoryTask<T extends AbstractCommand> extends AbstractTask<T, Void> {
	@Inject
	public AbstractDirectoryTask(ObjectFactory objectFactory) {
		super(objectFactory);
	}

	@Override
	protected Void createExtension(Project project) {
		return null;
	}

	@InputDirectory
	@PathSensitive(PathSensitivity.RELATIVE)
	public abstract DirectoryProperty getDirectory();

	@Input
	@Optional
	public abstract ListProperty<String> getIncludes();

	@Input
	@Optional
	public abstract ListProperty<String> getExcludes();

	@Option(option = "dir", description = "Input directory")
	public void setDir(File dir) {
		getDirectory().set(dir);
	}

	public void include(String... patterns) {
		getIncludes().addAll(patterns);
	}

	@Option(option = "include", description = "Include pattern")
	public void addInclude(String pattern) {
		getIncludes().add(pattern);
	}

	public void exclude(String... patterns) {
		getExcludes().addAll(patterns);
	}

	@Option(option = "exclude", description = "Exclude pattern")
	public void addExclude(String pattern) {
		getExcludes().add(pattern);
	}

	@InputFiles
	@PathSensitive(PathSensitivity.RELATIVE)
	public FileTree getInputFiles() {
		ConfigurableFileTree tree = getProject().fileTree(getDirectory());
		if (!getIncludes().get().isEmpty()) {
			tree.include(getIncludes().get());
		}
		if (!getExcludes().get().isEmpty()) {
			tree.exclude(getExcludes().get());
		}
		return tree;
	}

	@Override
	protected void beforeRun(T command) {
		if (command instanceof FilesProperty) {
			FilesProperty prop = (FilesProperty) command;
			Set<File> files = getInputFiles().getFiles();
			if (!files.isEmpty()) {
				List<File> fs = CommonUtils.list(files);
				prop.setFiles(fs);
			}
		}
	}
}