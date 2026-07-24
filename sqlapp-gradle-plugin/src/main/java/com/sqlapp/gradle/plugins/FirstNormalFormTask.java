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

import java.util.function.BiFunction;
import java.util.function.Function;

import org.gradle.api.Action;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.normalization.FirstNormalFormCommand;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.gradle.plugins.properties.OutputDirectoryTaskProperty;
import com.sqlapp.gradle.plugins.properties.TargetFileTaskProperty;

/**
 * Gradle task for converting a schema XML document to first normal form.
 */
@DisableCachingByDefault
public abstract class FirstNormalFormTask extends AbstractTask<FirstNormalFormCommand>
		implements TargetFileTaskProperty, OutputDirectoryTaskProperty {

	private Function<Table, String> childKeyColumnNameStrategy = table -> "ROW_NO";

	private BiFunction<Table, Integer, String> childTableNameStrategy = (table,
			clusterNumber) -> table.getName() + "_DETAIL_" + clusterNumber;

	public FirstNormalFormTask() {
		getMinimumColumnCount().convention(2);
		getNormalizationLogEnabled().convention(true);
	}

	public void call(Action<FirstNormalFormTask> action) {
		action.execute(this);
	}

	@Input
	public abstract Property<Integer> getMinimumColumnCount();

	@Input
	public abstract Property<Boolean> getNormalizationLogEnabled();

	@OutputDirectory
	@Optional
	public abstract DirectoryProperty getNormalizationLogDirectory();

	@Input
	@Optional
	public abstract Property<String> getNormalizationLogFileName();

	@Internal
	public Function<Table, String> getChildKeyColumnNameStrategy() {
		return childKeyColumnNameStrategy;
	}

	public void setChildKeyColumnNameStrategy(Function<Table, String> childKeyColumnNameStrategy) {
		this.childKeyColumnNameStrategy = childKeyColumnNameStrategy;
	}

	@Internal
	public BiFunction<Table, Integer, String> getChildTableNameStrategy() {
		return childTableNameStrategy;
	}

	public void setChildTableNameStrategy(BiFunction<Table, Integer, String> childTableNameStrategy) {
		this.childTableNameStrategy = childTableNameStrategy;
	}

	@Override
	protected void beforeRun(FirstNormalFormCommand command) {
		command.setMinimumColumnCount(getMinimumColumnCount().get());
		command.setChildKeyColumnNameStrategy(getChildKeyColumnNameStrategy());
		command.setChildTableNameStrategy(getChildTableNameStrategy());
		command.setNormalizationLogEnabled(getNormalizationLogEnabled().get());
		if (getNormalizationLogDirectory().isPresent()) {
			command.setNormalizationLogDirectory(getNormalizationLogDirectory().get().getAsFile());
		}
		if (getNormalizationLogFileName().isPresent()) {
			command.setNormalizationLogFileName(getNormalizationLogFileName().get());
		}
	}

	@Override
	protected FirstNormalFormCommand createCommand() {
		return new FirstNormalFormCommand();
	}
}
