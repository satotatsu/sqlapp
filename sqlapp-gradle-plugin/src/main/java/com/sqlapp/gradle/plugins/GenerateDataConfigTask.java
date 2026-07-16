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

import org.gradle.api.Action;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;
import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.dataconfig.ConfigFileType;
import com.sqlapp.data.db.command.generator.GenerateDataConfigCommand;
import com.sqlapp.data.db.sql.TableOptions;
import com.sqlapp.gradle.plugins.properties.ForeignKeyDefinitionDirectoryTaskProperty;
import com.sqlapp.gradle.plugins.properties.OnlyCurrentCatalogTaskProperty;
import com.sqlapp.gradle.plugins.properties.OnlyCurrentSchemaTaskProperty;
import com.sqlapp.gradle.plugins.properties.OutputDirectoryTaskProperty;
import com.sqlapp.gradle.plugins.properties.SchemaTargetTaskProperty;
import com.sqlapp.gradle.plugins.properties.SqlTypeTaskProperty;
import com.sqlapp.gradle.plugins.properties.TableOptionsTaskProperty;
import com.sqlapp.gradle.plugins.properties.TableTargetTaskProperty;

@DisableCachingByDefault
public abstract class GenerateDataConfigTask extends AbstractDbTask<GenerateDataConfigCommand>
		implements OutputDirectoryTaskProperty, SqlTypeTaskProperty, TableOptionsTaskProperty, SchemaTargetTaskProperty,
		TableTargetTaskProperty, OnlyCurrentCatalogTaskProperty, OnlyCurrentSchemaTaskProperty,
		ForeignKeyDefinitionDirectoryTaskProperty {

	public void call(Action<GenerateDataConfigTask> cons) {
		cons.execute(this);
	}

	private TableOptions tableOptions;

	@Internal
	@Override
	public TableOptions getTableOptions() {
		return this.tableOptions;
	}

	@Override
	public void setTableOptions(TableOptions tableOptions) {
		this.tableOptions = tableOptions;
	}

	@Input
	@Optional
	public abstract Property<String> getFileType();

	@Override
	protected void beforeRun(GenerateDataConfigCommand command) {
		if (getFileType().isPresent()) {
			command.setFileType(ConfigFileType.parse(getFileType().get()));
		}
	}

	@Override
	protected GenerateDataConfigCommand createCommand() {
		return new GenerateDataConfigCommand();
	}
}