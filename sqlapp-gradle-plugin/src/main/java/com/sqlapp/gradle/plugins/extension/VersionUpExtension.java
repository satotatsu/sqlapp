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
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputDirectory;
import org.gradle.api.tasks.Internal;
import org.gradle.api.tasks.Optional;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.version.VersionUpCommand;

public abstract class VersionUpExtension extends AbstractSchemaFileExtension {
	@Inject
	public VersionUpExtension(Project project) {
		super(project);
	}

	@Internal
	public void call(Action<VersionUpExtension> cons) {
		cons.execute(this);
	}

	/** file directory */
	@InputDirectory
	@Optional
	public abstract DirectoryProperty getFileDirectory();

	/** encoding */
	@Input
	@Optional
	public abstract Property<String> getEncoding();

	/**
	 * バージョンアップ用SQLのディレクトリ
	 */
	@InputDirectory
	@Optional
	public abstract DirectoryProperty getSqlDirectory();

	/**
	 * バージョンダウン用のSQLのディレクトリ
	 */
	@InputDirectory
	@Optional
	public abstract DirectoryProperty getDownSqlDirectory();

	/**
	 * バージョンアップ前に実行するSQLのディレクトリ
	 */
	@InputDirectory
	@Optional
	public abstract DirectoryProperty getSetupSqlDirectory();

	/**
	 * バージョンアップ後に実行するSQLのディレクトリ
	 */
	@InputDirectory
	@Optional
	public abstract DirectoryProperty getFinalizeSqlDirectory();

	@Input
	@Optional
	public abstract Property<Long> getLastChangeNumber();

	@Input
	@Optional
	public abstract Property<Boolean> getShowVersionOnly();

	@Input
	@Optional
	public abstract Property<Boolean> getWithSeriesNumber();

	@Input
	@Optional
	public abstract Property<String> getPlaceholderPrefix();

	@Input
	@Optional
	public abstract Property<String> getPlaceholderSuffix();

	@Input
	@Optional
	public abstract Property<Boolean> getPlaceholders();

	/** Schema Change log table name */
	@Input
	@Optional
	public abstract Property<ChangeTableExtension> getChangeTable();

	@Internal
	public void setCommand(AbstractCommand command, boolean debug) {
		super.setCommand(command, debug);
		if (command instanceof VersionUpCommand) {
			VersionUpCommand com = (VersionUpCommand) command;
			if (getFileDirectory().isPresent()) {
				com.setFileDirectory(getFileDirectory().get().getAsFile());
			}
			if (getEncoding().isPresent()) {
				com.setEncoding(getEncoding().get());
			}
			if (getSqlDirectory().isPresent()) {
				com.setSqlDirectory(getSqlDirectory().get().getAsFile());
			}
			if (getDownSqlDirectory().isPresent()) {
				com.setDownSqlDirectory(getDownSqlDirectory().get().getAsFile());
			}
			if (getSetupSqlDirectory().isPresent()) {
				com.setSetupSqlDirectory(getSetupSqlDirectory().get().getAsFile());
			}
			if (getFinalizeSqlDirectory().isPresent()) {
				com.setFinalizeSqlDirectory(getFinalizeSqlDirectory().get().getAsFile());
			}
			if (getLastChangeNumber().isPresent()) {
				com.setLastChangeToApply(getLastChangeNumber().get());
			}
			if (getShowVersionOnly().isPresent()) {
				com.setShowVersionOnly(getShowVersionOnly().get());
			}
			if (getWithSeriesNumber().isPresent()) {
				com.setWithSeriesNumber(getWithSeriesNumber().get());
			}
			if (getChangeTable().isPresent()) {
				getChangeTable().get().setCommand(command, debug);
			}
			//
			if (getPlaceholderPrefix().isPresent()) {
				com.setPlaceholderPrefix(getPlaceholderPrefix().get());
			}
			if (getPlaceholderSuffix().isPresent()) {
				com.setPlaceholderSuffix(getPlaceholderSuffix().get());
			}
			if (getPlaceholders().isPresent()) {
				com.setPlaceholders(getPlaceholders().get());
			}
		}
	}
}
