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

import java.util.function.Predicate;

import org.gradle.api.Action;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;

import com.sqlapp.data.db.sql.Options;
import com.sqlapp.data.db.sql.TableOptions;
import com.sqlapp.data.schemas.DbCommonObject;

public abstract class OptionsExtension {

	public OptionsExtension() {
	}

	public void call(Action<OptionsExtension> cons) {
		cons.execute(this);
	}

	/**
	 * COMMIT
	 */
	@Input
	@Optional
	private Predicate<DbCommonObject<?>> outputCommit;

	public void setOutputCommit(final boolean bool) {
		this.outputCommit = o -> bool;
	}

	public void outputCommit(final Predicate<DbCommonObject<?>> outputCommit) {
		this.outputCommit = outputCommit;
	}

	/**
	 * quateObjectName
	 */
	@Input
	@Optional
	public abstract Property<Boolean> getQuateObjectName();

	/**
	 * quateColumnName
	 */
	@Input
	@Optional
	public abstract Property<Boolean> getQuateColumnName();

	/**
	 * DROP IF EXISTS
	 */
	@Input
	@Optional
	public abstract Property<Boolean> getDropIfExists();

	/**
	 * CREATE IF NOT EXISTS
	 */
	@Input
	@Optional
	public abstract Property<Boolean> getCreateIfNotExists();

	/**
	 * Schema Name Decoration
	 */
	@Input
	@Optional
	public abstract Property<Boolean> getDecorateSchemaName();

	/**
	 * Set Search Path to Schema
	 */
	@Input
	@Optional
	public abstract Property<Boolean> getSetSearchPathToSchema();

	@Nested
	public abstract TableOptions getTableOptions();

	public void tableOptions(Action<? super TableOptions> action) {
		action.execute(getTableOptions());
	}

	public void initialize(Options options) {
		if (outputCommit != null) {
			options.setOutputCommit(outputCommit);
		}
		if (getQuateObjectName().isPresent()) {
			options.setQuateObjectName(getQuateObjectName().get());
		}
		if (getQuateColumnName().isPresent()) {
			options.setQuateColumnName(getQuateColumnName().get());
		}
		if (getDropIfExists().isPresent()) {
			options.setDropIfExists(getDropIfExists().get());
		}
		if (getCreateIfNotExists().isPresent()) {
			options.setCreateIfNotExists(getCreateIfNotExists().get());
		}
		if (getDecorateSchemaName().isPresent()) {
			options.setDecorateSchemaName(getDecorateSchemaName().get());
		}
		if (getSetSearchPathToSchema().isPresent()) {
			options.setSetSearchPathToSchema(getSetSearchPathToSchema().get());
		}
		if (getTableOptions() != null) {
			options.setTableOptions(getTableOptions());
		}
	}
}
