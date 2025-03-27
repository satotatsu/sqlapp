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
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.Optional;

import com.sqlapp.data.db.command.AbstractCommand;
import com.sqlapp.data.db.command.export.AbstractExportCommand;
import com.sqlapp.data.db.sql.TableOptions;
import com.sqlapp.util.JsonConverter;

/**
 * Table用のExtension
 */

public abstract class AbstractExportDataExtension extends AbstractDbTableExtension {
	@Inject
	protected AbstractExportDataExtension(Project project) {
		super(project);
	}

	/**
	 * Output Directory
	 */
	@InputDirectory
	public abstract DirectoryProperty getDirectory(); // = new File("./");

	@Input
	@Optional
	public abstract Property<Boolean> getUseSchemaNameDirectory(); // = false;

	@Input
	@Optional
	public abstract Property<String> getCsvEncoding();// = Charset.defaultCharset().toString();

	public abstract Property<JsonConverter> getJsonConverter();

	@Nested
	@Optional
	public abstract TableOptions getTableOptions();

	public void tableOptions(Action<? super TableOptions> action) {
		action.execute(getTableOptions());
	}

	@Internal
	@Override
	public void setCommand(AbstractCommand command, boolean debug) {
		super.setCommand(command, debug);
		if (command instanceof AbstractExportCommand) {
			AbstractExportCommand com = (AbstractExportCommand) command;
			if (getUseSchemaNameDirectory().isPresent()) {
				com.setUseSchemaNameDirectory(getUseSchemaNameDirectory().get());
			}
			if (getCsvEncoding().isPresent()) {
				com.setCsvEncoding(getCsvEncoding().get());
			}
			if (getJsonConverter().isPresent()) {
				com.setJsonConverter(getJsonConverter().get());
			}
			if (getDirectory().isPresent()) {
				com.setDirectory(getDirectory().getAsFile().get());
			}
			if (getTableOptions() != null) {
				com.setTableOptions(getTableOptions());
			}
		}
	}
}
