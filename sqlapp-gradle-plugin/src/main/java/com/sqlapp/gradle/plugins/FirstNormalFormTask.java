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
import com.sqlapp.data.db.command.normalization.SurrogateKeyGenerationType;
import com.sqlapp.data.db.datatype.DataType;
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

	private Function<Table, String> surrogatePrimaryKeyColumnNameStrategy = table -> "ID";

	private Function<Table, DataType> surrogatePrimaryKeyDataTypeStrategy = table -> DataType.INT;

	private BiFunction<String, java.util.List<String>, String> surrogateForeignKeyColumnNameStrategy = (tableName,
			columnNames) -> "PARENT_ID";

	private Function<Table, String> surrogateSequenceNameStrategy = table -> "SEQ_" + table.getName();

	public FirstNormalFormTask() {
		getMinimumColumnCount().convention(2);
		getNormalizationLogEnabled().convention(true);
		getConvertCompositePrimaryKey().convention(false);
		getSurrogateKeyGenerationType().convention(SurrogateKeyGenerationType.IDENTITY);
	}

	public void call(Action<FirstNormalFormTask> action) {
		action.execute(this);
	}

	@Input
	public abstract Property<Integer> getMinimumColumnCount();

	@Input
	public abstract Property<Boolean> getNormalizationLogEnabled();

	@Input
	public abstract Property<Boolean> getConvertCompositePrimaryKey();

	@Input
	public abstract Property<SurrogateKeyGenerationType> getSurrogateKeyGenerationType();

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

	@Internal
	public Function<Table, String> getSurrogatePrimaryKeyColumnNameStrategy() {
		return surrogatePrimaryKeyColumnNameStrategy;
	}

	public void setSurrogatePrimaryKeyColumnNameStrategy(
			Function<Table, String> surrogatePrimaryKeyColumnNameStrategy) {
		this.surrogatePrimaryKeyColumnNameStrategy = surrogatePrimaryKeyColumnNameStrategy;
	}

	@Internal
	public Function<Table, DataType> getSurrogatePrimaryKeyDataTypeStrategy() {
		return surrogatePrimaryKeyDataTypeStrategy;
	}

	public void setSurrogatePrimaryKeyDataTypeStrategy(
			Function<Table, DataType> surrogatePrimaryKeyDataTypeStrategy) {
		this.surrogatePrimaryKeyDataTypeStrategy = surrogatePrimaryKeyDataTypeStrategy;
	}

	@Internal
	public BiFunction<String, java.util.List<String>, String> getSurrogateForeignKeyColumnNameStrategy() {
		return surrogateForeignKeyColumnNameStrategy;
	}

	public void setSurrogateForeignKeyColumnNameStrategy(
			BiFunction<String, java.util.List<String>, String> surrogateForeignKeyColumnNameStrategy) {
		this.surrogateForeignKeyColumnNameStrategy = surrogateForeignKeyColumnNameStrategy;
	}

	@Internal
	public Function<Table, String> getSurrogateSequenceNameStrategy() {
		return surrogateSequenceNameStrategy;
	}

	public void setSurrogateSequenceNameStrategy(Function<Table, String> surrogateSequenceNameStrategy) {
		this.surrogateSequenceNameStrategy = surrogateSequenceNameStrategy;
	}

	@Override
	protected void beforeRun(FirstNormalFormCommand command) {
		command.setMinimumColumnCount(getMinimumColumnCount().get());
		command.setChildKeyColumnNameStrategy(getChildKeyColumnNameStrategy());
		command.setChildTableNameStrategy(getChildTableNameStrategy());
		command.setNormalizationLogEnabled(getNormalizationLogEnabled().get());
		command.setConvertCompositePrimaryKey(getConvertCompositePrimaryKey().get());
		command.setSurrogateKeyGenerationType(getSurrogateKeyGenerationType().get());
		command.setSurrogatePrimaryKeyColumnNameStrategy(getSurrogatePrimaryKeyColumnNameStrategy());
		command.setSurrogatePrimaryKeyDataTypeStrategy(getSurrogatePrimaryKeyDataTypeStrategy());
		command.setSurrogateForeignKeyColumnNameStrategy(getSurrogateForeignKeyColumnNameStrategy());
		command.setSurrogateSequenceNameStrategy(getSurrogateSequenceNameStrategy());
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
