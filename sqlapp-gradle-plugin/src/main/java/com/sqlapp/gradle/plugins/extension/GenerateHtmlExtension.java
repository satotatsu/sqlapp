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

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.html.GenerateHtmlCommand;
import com.sqlapp.data.schemas.ForeignKeyConstraint;

public abstract class GenerateHtmlExtension extends AbstractSchemaFileExtension {
	@Inject
	public GenerateHtmlExtension(Project project) {
		super(project);
	}

	@Internal
	public void call(Action<GenerateHtmlExtension> cons) {
		cons.execute(this);
	}

	@Input
	@Optional
	public abstract Property<RenderOptionExtension> getRenderOptions();

	/**
	 * file
	 */
	@InputDirectory
	@Optional
	public abstract DirectoryProperty getOutputDirectory();

	@Input
	@Optional
	public abstract Property<String> getDiagramFont();

	@Input
	@Optional
	public abstract Property<String> getDiagramFormat();

	@Input
	@Optional
	public abstract Property<String> getDot();

	@Input
	@Optional
	public abstract Property<Boolean> getMultiThread();

	@InputDirectory
	@Optional
	public abstract DirectoryProperty getFileDirectory();

	@InputDirectory
	@Optional
	public abstract DirectoryProperty getDirectory();

	@Input
	@Optional
	public abstract Property<Boolean> getUseSchemaNameDirectory();

	@Input
	@Optional
	public abstract Property<Boolean> getUseTableNameDirectory();

	/** file filter */
	@Input
	@Optional
	public abstract Property<Predicate<File>> getFileFilter();

	/** Virtual foreign Key definitions */
	@InputDirectory
	@Optional
	public abstract DirectoryProperty getForeignKeyDefinitionDirectory();

	/** virtualForeignKeyLabel */
	@Input
	@Optional
	public abstract Property<Function<ForeignKeyConstraint, String>> getVirtualForeignKeyLabel();

	@Input
	@Optional
	public abstract Property<String> getPlaceholderPrefix();

	@Input
	@Optional
	public abstract Property<String> getPlaceholderSuffix();

	@Input
	@Optional
	public abstract Property<Boolean> getPlaceholders();

	@Internal
	public void setCommand(AbstractCommand command) {
		super.setCommand(command);
		if (command instanceof GenerateHtmlCommand) {
			GenerateHtmlCommand com = (GenerateHtmlCommand) command;
			if (getRenderOptions().isPresent()) {
				getRenderOptions().get().setRenderOption(com.getRenderOptions());
			}
			if (getOutputDirectory().isPresent()) {
				com.setOutputDirectory(getOutputDirectory().get().getAsFile());
			}
			if (getDiagramFont().isPresent()) {
				com.setDiagramFont(getDiagramFont().get());
			}
			if (getDiagramFormat().isPresent()) {
				com.setDiagramFormat(getDiagramFormat().get());
			}
			if (getDot().isPresent()) {
				com.setDot(getDot().get());
			}
			if (getMultiThread().isPresent()) {
				com.setMultiThread(getMultiThread().get());
			}
			if (getFileDirectory().isPresent()) {
				com.setFileDirectory(getFileDirectory().get().getAsFile());
			}
			if (getDirectory().isPresent()) {
				com.setDirectory(getDirectory().get().getAsFile());
			}
			if (getUseSchemaNameDirectory().isPresent()) {
				com.setUseSchemaNameDirectory(getUseSchemaNameDirectory().get());
			}
			if (getUseTableNameDirectory().isPresent()) {
				com.setUseTableNameDirectory(getUseTableNameDirectory().get());
			}
			if (getForeignKeyDefinitionDirectory().isPresent()) {
				com.setForeignKeyDefinitionDirectory(getForeignKeyDefinitionDirectory().get().getAsFile());
			}
			if (getVirtualForeignKeyLabel().isPresent()) {
				com.setVirtualForeignKeyLabel(getVirtualForeignKeyLabel().get());
			}
			//
			if (getPlaceholderPrefix().isPresent()) {
				com.setPlaceholderPrefix(getPlaceholderPrefix().get());
			}
			if (getPlaceholderSuffix().isPresent()) {
				com.setPlaceholderSuffix(getPlaceholderSuffix().get());
			}
			if (getPlaceholders().isPresent()) {
				com.setPlaceholders(getPlaceholders().get());
			}
		}
	}
}
