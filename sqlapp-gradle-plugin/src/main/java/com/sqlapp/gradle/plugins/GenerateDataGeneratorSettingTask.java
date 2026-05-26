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
import org.gradle.api.Project;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Optional;
import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.generator.GenerateGeneratorSettingCommand;
import com.sqlapp.data.db.command.generator.GeneratorSettingFileType;
import com.sqlapp.gradle.plugins.extension.DataSourceExtension;
import com.sqlapp.gradle.plugins.properties.DataSourceTaskProperty;
import com.sqlapp.gradle.plugins.properties.OnlyCurrentCatalogTaskProperty;
import com.sqlapp.gradle.plugins.properties.OnlyCurrentSchemaTaskProperty;
import com.sqlapp.gradle.plugins.properties.OutputDirectoryTaskProperty;
import com.sqlapp.gradle.plugins.properties.SchemaTargetTaskProperty;
import com.sqlapp.gradle.plugins.properties.SqlTypeTaskProperty;
import com.sqlapp.gradle.plugins.properties.TableOptionTaskProperty;
import com.sqlapp.gradle.plugins.properties.TableTargetTaskProperty;

@DisableCachingByDefault
public abstract class GenerateDataGeneratorSettingTask extends AbstractDbTask<GenerateGeneratorSettingCommand, Void>
		implements DataSourceTaskProperty, OutputDirectoryTaskProperty, SqlTypeTaskProperty, TableOptionTaskProperty,
		SchemaTargetTaskProperty, TableTargetTaskProperty, OnlyCurrentCatalogTaskProperty,
		OnlyCurrentSchemaTaskProperty {

	public GenerateDataGeneratorSettingTask() {
		setDataSource(getProject().getObjects().newInstance((DataSourceExtension.class)));
	}

	public void call(Action<GenerateDataGeneratorSettingTask> cons) {
		cons.execute(this);
	}

	@Input
	@Optional
	public abstract Property<String> getFileType();

	@Override
	protected void exec(GenerateGeneratorSettingCommand command, Void extension) {
		if (getFileType().isPresent()) {
			command.setFileType(GeneratorSettingFileType.parse(getFileType().get()));
		}
		run(command);
	}

	@Override
	protected GenerateGeneratorSettingCommand createCommand() {
		return new GenerateGeneratorSettingCommand();
	}

	@Override
	protected Void createExtension(Project project) {
		return null;
	}

}