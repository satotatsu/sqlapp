/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
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
import org.gradle.api.Project;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.DropObjectsCommand;

/**
 * DropObject用のExtension
 */
public abstract class DropObjectsExtension extends AbstractDbTableExtension {
	@Inject
	public DropObjectsExtension(Project project) {
		super(project);
	}

	@Internal
	public void call(Action<DropObjectsExtension> cons) {
		cons.execute(this);
	}

	/**
	 * 現在のカタログのみを対象とするフラグ
	 */
	@Input
	@Optional
	public abstract Property<Boolean> getOnlyCurrentCatalog();

	/**
	 * Include drop target Objects
	 */
	@Input
	@Optional
	public abstract ListProperty<String> getIncludeObjects();

	/**
	 * Exclude drop target Objects
	 */
	@Input
	@Optional
	public abstract ListProperty<String> getExcludeObjects();

	/**
	 * オブジェクトのDROPを実施
	 */
	@Input
	@Optional
	public abstract Property<Boolean> getDropObjects();

	/**
	 * テーブルのDROPを実施
	 */
	@Input
	@Optional
	public abstract Property<Boolean> getDropTables();

	@Input
	@Optional
	public abstract Property<String> getPreDropTableSql();

	@Input
	@Optional
	public abstract Property<String> getAfterDropTableSql();

	@Internal
	@Override
	public void setCommand(AbstractCommand command, boolean debug) {
		super.setCommand(command, debug);
		if (command instanceof DropObjectsCommand) {
			DropObjectsCommand com = (DropObjectsCommand) command;
			if (getOnlyCurrentCatalog().isPresent()) {
				com.setOnlyCurrentCatalog(getOnlyCurrentCatalog().get());
			}
			if (getIncludeObjects().isPresent()) {
				com.setIncludeObjects(getIncludeObjects().get().toArray(new String[0]));
			}
			if (getExcludeObjects().isPresent()) {
				com.setExcludeObjects(getExcludeObjects().get().toArray(new String[0]));
			}
			if (getDropObjects().isPresent()) {
				com.setDropObjects(getDropObjects().get());
			}
			if (getDropTables().isPresent()) {
				com.setDropTables(getDropTables().get());
			}
			if (getPreDropTableSql().isPresent()) {
				com.setPreDropTableSql(getPreDropTableSql().get());
			}
			if (getAfterDropTableSql().isPresent()) {
				com.setAfterDropTableSql(getAfterDropTableSql().get());
			}
		}
	}
}
