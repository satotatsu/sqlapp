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

import com.sqlapp.data.db.command.DiffCommand;
import com.sqlapp.data.schemas.DefaultSchemaEqualsHandler;
import com.sqlapp.data.schemas.EqualsHandler;
import com.sqlapp.gradle.plugins.properties.EqualsHandlerTaskProperty;
import com.sqlapp.gradle.plugins.properties.OriginalFileTaskProperty;
import com.sqlapp.gradle.plugins.properties.TargetFileTaskProperty;

@DisableCachingByDefault
public abstract class DiffSchemaXmlTask extends AbstractTask<DiffCommand>
		implements EqualsHandlerTaskProperty, TargetFileTaskProperty, OriginalFileTaskProperty {

	public void call(Action<DiffSchemaXmlTask> cons) {
		cons.execute(this);
	}

	private EqualsHandler equalsHandler = new DefaultSchemaEqualsHandler();

	@Internal
	public EqualsHandler getEqualsHandler() {
		return equalsHandler;
	}

	public void setEqualsHandler(EqualsHandler equalsHandler) {
		this.equalsHandler = equalsHandler;
	}

	@Override
	protected DiffCommand createCommand() {
		return new DiffCommand();
	}
}
