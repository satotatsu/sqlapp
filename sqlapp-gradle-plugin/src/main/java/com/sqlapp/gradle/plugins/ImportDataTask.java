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

import javax.inject.Inject;

import org.gradle.api.Action;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.Internal;
import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.export.ImportDataCommand;
import com.sqlapp.data.db.sql.TableOptions;
import com.sqlapp.gradle.plugins.properties.CommitPerTableTaskProperty;
import com.sqlapp.gradle.plugins.properties.CsvEncodingTaskProperty;
import com.sqlapp.gradle.plugins.properties.DataSourceTaskProperty;
import com.sqlapp.gradle.plugins.properties.FileDirectoryTaskProperty;
import com.sqlapp.gradle.plugins.properties.PlaceholderTaskProperty;
import com.sqlapp.gradle.plugins.properties.QueryCommitIntervalTaskProperty;
import com.sqlapp.gradle.plugins.properties.SchemaTargetTaskProperty;
import com.sqlapp.gradle.plugins.properties.SqlTypeTaskProperty;
import com.sqlapp.gradle.plugins.properties.TableOptionsTaskProperty;
import com.sqlapp.gradle.plugins.properties.TableTargetTaskProperty;
import com.sqlapp.gradle.plugins.properties.UseSchemaNameDirectoryTaskProperty;

@DisableCachingByDefault
public abstract class ImportDataTask extends AbstractDirectoryTask<ImportDataCommand>
		implements DataSourceTaskProperty, SchemaTargetTaskProperty, TableTargetTaskProperty, TableOptionsTaskProperty,
		FileDirectoryTaskProperty, QueryCommitIntervalTaskProperty, SqlTypeTaskProperty, PlaceholderTaskProperty,
		CommitPerTableTaskProperty, CsvEncodingTaskProperty, UseSchemaNameDirectoryTaskProperty {
	@Inject
	public ImportDataTask(ObjectFactory objectFactory) {
		super(objectFactory);
	}

	public void call(Action<ImportDataTask> cons) {
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

	@Override
	protected ImportDataCommand createCommand() {
		return new ImportDataCommand();
	}
}
