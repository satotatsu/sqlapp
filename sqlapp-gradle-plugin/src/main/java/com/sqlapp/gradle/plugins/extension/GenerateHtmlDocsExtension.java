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

package com.sqlapp.gradle.plugins.extension;

import java.io.File;
import java.util.function.Function;
import java.util.function.Predicate;

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.html.GenerateHtmlDocsCommand;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.gradle.plugins.properties.DictionaryFileDirectoryTaskProperty;
import com.sqlapp.gradle.plugins.properties.DictionaryFileTypeTaskProperty;
import com.sqlapp.gradle.plugins.properties.DirectoryTaskProperty;
import com.sqlapp.gradle.plugins.properties.FileDirectoryTaskProperty;
import com.sqlapp.gradle.plugins.properties.FileFilterTaskProperty;
import com.sqlapp.gradle.plugins.properties.OutputDirectoryTaskProperty;
import com.sqlapp.gradle.plugins.properties.PlaceholderTaskProperty;
import com.sqlapp.gradle.plugins.properties.TargetFileTaskProperty;
import com.sqlapp.gradle.plugins.properties.UseSchemaNameDirectoryTaskProperty;
import com.sqlapp.graphviz.renderer.OutputFormat;

public abstract class GenerateHtmlDocsExtension extends AbstractSchemaFileExtension
		implements FileFilterTaskProperty, FileDirectoryTaskProperty, DirectoryTaskProperty,
		OutputDirectoryTaskProperty, PlaceholderTaskProperty, UseSchemaNameDirectoryTaskProperty,
		DictionaryFileDirectoryTaskProperty, DictionaryFileTypeTaskProperty, TargetFileTaskProperty {
	@Inject
	public GenerateHtmlDocsExtension(Project project) {
		super(project);
	}

	public void call(Action<GenerateHtmlDocsExtension> cons) {
		cons.execute(this);
	}

	@Input
	@Optional
	public abstract Property<RenderOptionExtension> getRenderOptions();

	@Input
	@Optional
	public abstract Property<String> getDiagramFont();

	@Input
	@Optional
	public abstract Property<String> getDiagramFormat();

	@Input
	@Optional
	public abstract Property<Boolean> getMultiThread();

	/** file filter */
	private Predicate<File> fileFilter = f -> true;

	@Input
	@Optional
	@Override
	public Predicate<File> getFileFilter() {
		return this.fileFilter;
	}

	@Override
	public void setFileFilter(Predicate<File> fileFilter) {
		this.fileFilter = fileFilter;
	}

	/** Virtual foreign Key definitions */
	@InputDirectory
	@PathSensitive(PathSensitivity.RELATIVE)
	@Optional
	public abstract DirectoryProperty getForeignKeyDefinitionDirectory();

	/** virtualForeignKeyLabel */
	@Input
	@Optional
	public abstract Property<Function<ForeignKeyConstraint, String>> getVirtualForeignKeyLabel();

	@Internal
	public void initializeCommand(AbstractCommand command) {
		super.initializeCommand(command);
		if (command instanceof GenerateHtmlDocsCommand) {
			GenerateHtmlDocsCommand com = (GenerateHtmlDocsCommand) command;
			if (getRenderOptions().isPresent()) {
				getRenderOptions().get().setRenderOption(com.getRenderOptions());
			}
			if (getDiagramFont().isPresent()) {
				com.setDiagramFont(getDiagramFont().get());
			}
			if (getDiagramFormat().isPresent()) {
				com.setDiagramFormat(OutputFormat.parse(getDiagramFormat().get()));
			}
			if (getMultiThread().isPresent()) {
				com.setMultiThread(getMultiThread().get());
			}
			if (getForeignKeyDefinitionDirectory().isPresent()) {
				com.setForeignKeyDefinitionDirectory(getForeignKeyDefinitionDirectory().get().getAsFile());
			}
			if (getVirtualForeignKeyLabel().isPresent()) {
				com.setVirtualForeignKeyLabel(getVirtualForeignKeyLabel().get());
			}
		}
	}
}
