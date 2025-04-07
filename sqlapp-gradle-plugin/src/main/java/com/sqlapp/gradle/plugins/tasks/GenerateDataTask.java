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

package com.sqlapp.gradle.plugins.tasks;

import org.gradle.api.Action;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.TaskAction;

import com.sqlapp.data.db.command.generator.GenerateDataInsertCommand;
import com.sqlapp.data.db.command.generator.factory.TableGeneratorSettingFactory;
import com.sqlapp.gradle.plugins.extension.CachedMvelEvaluatorExtension;
import com.sqlapp.gradle.plugins.extension.DataSourceExtension;
import com.sqlapp.gradle.plugins.extension.DataSourceInject;
import com.sqlapp.gradle.plugins.extension.TableOptionsExtension;
import com.sqlapp.util.eval.mvel.CachedMvelEvaluator;

public abstract class GenerateDataTask extends AbstractDbTask implements DataSourceInject {

	public GenerateDataTask() {
		this.setDataSource(this.getProject().getObjects().newInstance((DataSourceExtension.class)));
		getTableOptions().convention(getProject().getObjects().newInstance(TableOptionsExtension.class));
		getEvaluator().convention(getProject().getObjects().newInstance(CachedMvelEvaluatorExtension.class));
		getGeneratorSettingFactory()
				.convention(getProject().getObjects().newInstance(TableGeneratorSettingFactory.class));
	}

	@Internal
	public void call(Action<GenerateDataTask> cons) {
		cons.execute(this);
	}

	@Input
	@Optional
	public abstract Property<String> getSchemaName();

	@Input
	@Optional
	public abstract Property<String> getTableName();

	@Input
	@Optional
	public abstract DirectoryProperty getFileDirectory();

	@Input
	@Optional
	public abstract Property<Long> getQueryCommitInterval();

	@Nested
	public abstract Property<CachedMvelEvaluatorExtension> getEvaluator();

	@Nested
	public abstract Property<TableOptionsExtension> getTableOptions();

	@Nested
	public abstract Property<TableGeneratorSettingFactory> getGeneratorSettingFactory();

	public void tableOptions(Action<? super TableOptionsExtension> action) {
		action.execute(getTableOptions().get());
	}

	public void evaluator(Action<? super CachedMvelEvaluator> action) {
		action.execute(getEvaluator().get());
	}

	public void generatorSettingFactory(Action<? super TableGeneratorSettingFactory> action) {
		action.execute(getGeneratorSettingFactory().get());
	}

	@TaskAction
	public void exec() {
		final GenerateDataInsertCommand command = new GenerateDataInsertCommand();
		command.setDataSource(createDataSource(this.getDataSource()));
		if (getSchemaName().isPresent()) {
			command.setSchemaName(getSchemaName().get());
		}
		if (getTableName().isPresent()) {
			command.setTableName(getTableName().get());
		}
		if (getFileDirectory().isPresent()) {
			command.setFileDirectory(getFileDirectory().get().getAsFile());
		}
		if (getQueryCommitInterval().isPresent()) {
			command.setQueryCommitInterval(getQueryCommitInterval().get());
		}
		if (getEvaluator().isPresent()) {
			command.setEvaluator(getEvaluator().get());
		}
		if (getTableOptions().isPresent()) {
			command.setTableOptions(getTableOptions().get());
		}
		if (getGeneratorSettingFactory().isPresent()) {
			command.setGeneratorSettingFactory(getGeneratorSettingFactory().get());
		}
		run(command);
	}

}