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
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.migration.MigrationCommand;

/**
 * Table用のExtension
 */

public abstract class ChangeTableExtension extends AbstractExtension {
	@Inject
	public ChangeTableExtension(ObjectFactory objects) {
		super(objects);
	}

	public void call(Action<ChangeTableExtension> cons) {
		cons.execute(this);
	}

	/** Schema Change log table name. default=changelog */
	@Input
	@Optional
	public abstract Property<String> getName();

	/** Schema Change log table id column name. default=change_number */
	@Input
	@Optional
	public abstract Property<String> getIdColumnName();

	/** Schema Change log table applied by column name. default=applied_by */
	@Input
	@Optional
	public abstract Property<String> getAppliedByColumnName();

	/** Schema Change log table applied at column name. default=applied_at */
	@Input
	@Optional
	public abstract Property<String> getAppliedAtColumnName();

	/** Schema Change log table description column name. default=description */
	@Input
	@Optional
	public abstract Property<String> getDescriptionColumnName();

	/** Schema Change log table series number column name. default=series_number */
	@Input
	@Optional
	public abstract Property<String> getSeriesNumberColumnName();

	@Internal
	@Override
	public void initializeCommand(AbstractCommand command) {
		super.initializeCommand(command);
		if (command instanceof MigrationCommand) {
			MigrationCommand com = (MigrationCommand) command;
			if (getName().isPresent()) {
				com.setSchemaChangeLogTableName(getName().get());
			}
			if (getIdColumnName().isPresent()) {
				com.setIdColumnName(getIdColumnName().get());
			}
			if (getAppliedByColumnName().isPresent()) {
				com.setAppliedByColumnName(getAppliedByColumnName().get());
			}
			if (getAppliedAtColumnName().isPresent()) {
				com.setAppliedAtColumnName(getAppliedAtColumnName().get());
			}
			if (getDescriptionColumnName().isPresent()) {
				com.setDescriptionColumnName(getDescriptionColumnName().get());
			}
			if (getSeriesNumberColumnName().isPresent()) {
				com.setSeriesNumberColumnName(getSeriesNumberColumnName().get());
			}
		}
	}
}
