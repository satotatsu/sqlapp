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

import org.gradle.api.Action;
import org.gradle.api.Project;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.generator.GenerateDataInsertCommand;
import com.sqlapp.gradle.plugins.extension.CachedMvelEvaluatorExtension;
import com.sqlapp.gradle.plugins.extension.TableGeneratorSettingFactoryExtension;
import com.sqlapp.gradle.plugins.properties.DataSourceTaskProperty;
import com.sqlapp.gradle.plugins.properties.DirectoryTaskProperty;
import com.sqlapp.gradle.plugins.properties.FileFilterTaskProperty;
import com.sqlapp.gradle.plugins.properties.OnlyCurrentCatalogTaskProperty;
import com.sqlapp.gradle.plugins.properties.OnlyCurrentSchemaTaskProperty;
import com.sqlapp.gradle.plugins.properties.QueryCommitIntervalTaskProperty;
import com.sqlapp.gradle.plugins.properties.SchemaTargetTaskProperty;
import com.sqlapp.gradle.plugins.properties.TableOptionTaskProperty;
import com.sqlapp.gradle.plugins.properties.TableTargetTaskProperty;
import com.sqlapp.gradle.plugins.properties.UseSchemaNameDirectoryTaskProperty;

@DisableCachingByDefault
public abstract class GenerateDataTask extends AbstractDbTask<GenerateDataInsertCommand, Void>
		implements DataSourceTaskProperty, DirectoryTaskProperty, FileFilterTaskProperty, TableOptionTaskProperty,
		QueryCommitIntervalTaskProperty, SchemaTargetTaskProperty, TableTargetTaskProperty,
		OnlyCurrentCatalogTaskProperty, OnlyCurrentSchemaTaskProperty, UseSchemaNameDirectoryTaskProperty {

	public void call(Action<GenerateDataTask> cons) {
		cons.execute(this);
	}

	/** file filter */
	@Input
	@Optional
	public Predicate<File> fileFilter = f -> true;

	@Override
	public Predicate<File> getFileFilter() {
		return this.fileFilter;
	}

	@Override
	public void setFileFilter(Predicate<File> fileFilter) {
		this.fileFilter = fileFilter;
	}

	@Nested
	public abstract CachedMvelEvaluatorExtension getEvaluator();

	@Nested
	public abstract TableGeneratorSettingFactoryExtension getGeneratorSettingFactory();

	@Override
	protected void exec(GenerateDataInsertCommand command, Void extension) {
		if (getEvaluator() != null) {
			command.setEvaluator(getEvaluator().getEvaluator());
		}
		if (getGeneratorSettingFactory() != null) {
			getGeneratorSettingFactory().initializeCommand(command);
		}
		run(command);
	}

	@Override
	protected GenerateDataInsertCommand createCommand() {
		return new GenerateDataInsertCommand();
	}

	@Override
	protected Void createExtension(Project project) {
		return null;
	}

}