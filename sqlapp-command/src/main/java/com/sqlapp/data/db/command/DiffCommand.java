/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-command.
 *
 * sqlapp-command is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-command is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-command.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.data.db.command;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import com.sqlapp.data.db.command.properties.EqualsHandlerProperty;
import com.sqlapp.data.db.command.properties.OriginalFileProperty;
import com.sqlapp.data.db.command.properties.TargetFileProperty;
import com.sqlapp.data.schemas.DbCommonObject;
import com.sqlapp.data.schemas.DbObject;
import com.sqlapp.data.schemas.DbObjectCollection;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.DbObjectDifferenceCollection;
import com.sqlapp.data.schemas.DefaultSchemaEqualsHandler;
import com.sqlapp.data.schemas.EqualsHandler;
import com.sqlapp.data.schemas.SchemaUtils;
import com.sqlapp.exceptions.CommandException;
import com.sqlapp.util.CommonUtils;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class DiffCommand extends AbstractCommand
		implements EqualsHandlerProperty, TargetFileProperty, OriginalFileProperty {
	/**
	 * Output originalFilePath
	 */
	private File originalFile;
	/**
	 * Output targetFilePath
	 */
	private File targetFile;

	private EqualsHandler equalsHandler = new DefaultSchemaEqualsHandler();

	@Override
	protected void doRun() {
		DbCommonObject<?> original = null;
		try {
			original = SchemaUtils.readXml(originalFile);
		} catch (FileNotFoundException e) {
			throw new CommandException("path=" + originalFile, e);
		} catch (IOException e) {
			throw new CommandException("path=" + originalFile, e);
		}
		DbCommonObject<?> target = null;
		try {
			target = SchemaUtils.readXml(targetFile);
		} catch (FileNotFoundException e) {
			throw new CommandException("path=" + targetFile, e);
		} catch (IOException e) {
			throw new CommandException("path=" + targetFile, e);
		}
		if (!CommonUtils.eq(original.getClass(), target.getClass())) {
			throw new CommandException("Original and Target class unmatch. original=[" + original.getClass()
					+ "], target=[" + target.getClass() + "].");
		}
		EqualsHandler equalsHandler = getEqualsHandler();
		boolean result = original.equals(target, equalsHandler);
		if (!result) {
			printResult(original, target, equalsHandler);
		} else {
			this.info("No difference found.");
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void printResult(DbCommonObject<?> original, DbCommonObject<?> target, EqualsHandler equalsHandler) {
		if (original instanceof DbObject) {
			DbObjectDifference diff = ((DbObject) original).diff((DbObject) target, equalsHandler);
			this.info(diff);
		} else {
			DbObjectDifferenceCollection diff = ((DbObjectCollection) original).diff((DbObjectCollection) target,
					equalsHandler);
			this.info(diff);
		}
	}

	@SuppressWarnings("rawtypes")
	protected void doExecute(DbCommonObject original, DbCommonObject target) {
		if (original instanceof DbObject) {
			doExecute((DbObject) original, (DbObject) target);
		}
		if (original instanceof DbObjectCollection) {
			doExecute((DbObjectCollection) original, (DbObjectCollection) target);
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected void doExecute(DbObject original, DbObject target) {
		original.diff(target, this.getEqualsHandler());
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected void doExecute(DbObjectCollection original, DbObjectCollection target) {
		original.diff(target, this.getEqualsHandler());
	}

}
