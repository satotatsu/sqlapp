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

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.html.AbstractSchemaFileCommand;

/**
 * GenerateSql用のExtension
 */
public abstract class AbstractGenerateSqlExtension extends AbstractDbExtension {
	@Inject
	protected AbstractGenerateSqlExtension(Project project) {
		super(project);
	}

	/**
	 * Output targetFile
	 */
	@InputFile
	@Optional
	public abstract RegularFileProperty getTargetFile();

	/**
	 * 出力ファイルパス
	 */
	@InputDirectory
	@Optional
	public abstract DirectoryProperty getOutputPath();

	/**
	 * 出力ファイルエンコーディング
	 */
	@Input
	@Optional
	public abstract Property<String> getEncoding();

	/**
	 * 複数ファイル出力
	 */
	@Input
	@Optional
	public abstract Property<Boolean> getOutputAsMultiFiles();

	@Input
	@Optional
	public abstract Property<String> getOutputFileExtension();

	@Input
	@Optional
	public abstract Property<Long> getLastChangeNumber();

	@Input
	@Optional
	public abstract Property<Long> getChangeNumberStep();

	@Input
	@Optional
	public abstract Property<Integer> getNumberOfDigits();

	@Internal
	public int getOrElseNumberOfDigits() {
		return getNumberOfDigits().getOrElse(19);
	}

	@Input
	@Optional
	public abstract Property<OptionsExtension> getSchemaOptions();

	public void schemaOptions(Action<? super OptionsExtension> action) {
		action.execute(getSchemaOptions().get());
	}

	@Internal
	@Override
	public void setCommand(AbstractCommand command, boolean debug) {
		super.setCommand(command, debug);
		if (command instanceof AbstractSchemaFileCommand) {
			AbstractSchemaFileCommand com = (AbstractSchemaFileCommand) command;
			if (getTargetFile().isPresent()) {
				com.setTargetFile(getTargetFile().getAsFile().get());
			}
		}
	}
}
