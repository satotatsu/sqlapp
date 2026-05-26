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

import java.util.function.BiFunction;

import org.gradle.api.Action;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.generator.GenerateDataInsertCommand;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.function.ColumnFunction;

public abstract class TableGeneratorSettingFactoryExtension {

	ColumnFunction<String> columnMinValue;

	ColumnFunction<String> columnNextValue;

	ColumnFunction<String> columnMaxValue;

	BiFunction<Column, Dialect, String> columnStartValue;

	@Input
	public abstract Property<Boolean> getWithSchemaName();

	public void call(Action<TableGeneratorSettingFactoryExtension> cons) {
		cons.execute(this);
	}

	public void initializeCommand(AbstractCommand command) {
		if (command instanceof GenerateDataInsertCommand) {
			GenerateDataInsertCommand com = (GenerateDataInsertCommand) command;
			if (this.columnMinValue != null) {
				com.getGeneratorSettingFactory().setColumnMinValue(this.columnMinValue);
			}
			if (this.columnNextValue != null) {
				com.getGeneratorSettingFactory().setColumnNextValue(this.columnNextValue);
			}
			if (this.columnMaxValue != null) {
				com.getGeneratorSettingFactory().setColumnMaxValue(this.columnMaxValue);
			}
			if (this.columnStartValue != null) {
				com.getGeneratorSettingFactory().setColumnStartValue(this.columnStartValue);
			}
			if (this.getWithSchemaName().isPresent()) {
				com.getGeneratorSettingFactory().setWithSchemaName(this.getWithSchemaName().get());
			}
		}
	}
}
