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
import org.gradle.api.tasks.Internal;
import org.gradle.work.DisableCachingByDefault;

import com.sqlapp.data.db.command.TableSqlExecuteCommand;
import com.sqlapp.data.db.sql.TableOptions;
import com.sqlapp.gradle.plugins.properties.CommitPerSqlTypeTaskProperty;
import com.sqlapp.gradle.plugins.properties.CommitPerTableTaskProperty;
import com.sqlapp.gradle.plugins.properties.OnlyCurrentCatalogTaskProperty;
import com.sqlapp.gradle.plugins.properties.OnlyCurrentSchemaTaskProperty;
import com.sqlapp.gradle.plugins.properties.SchemaOptionTaskProperty;
import com.sqlapp.gradle.plugins.properties.SchemaTargetTaskProperty;
import com.sqlapp.gradle.plugins.properties.SqlTypesTaskProperty;
import com.sqlapp.gradle.plugins.properties.TableOptionsTaskProperty;
import com.sqlapp.gradle.plugins.properties.TableTargetTaskProperty;

@DisableCachingByDefault
public abstract class TableSqlExecuteTask extends AbstractDbTask<TableSqlExecuteCommand>
		implements SchemaOptionTaskProperty, SchemaTargetTaskProperty, TableTargetTaskProperty,
		OnlyCurrentCatalogTaskProperty, OnlyCurrentSchemaTaskProperty, TableOptionsTaskProperty,
		CommitPerTableTaskProperty, CommitPerSqlTypeTaskProperty, SqlTypesTaskProperty {

	public void call(Action<TableSqlExecuteTask> cons) {
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
	protected TableSqlExecuteCommand createCommand() {
		return new TableSqlExecuteCommand();
	}
}