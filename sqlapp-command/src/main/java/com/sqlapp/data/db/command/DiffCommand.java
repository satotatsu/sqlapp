/**
 * Copyright (C) 2007-2017 Tatsuo Satoh <multisqllib@gmail.com>
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
 * along with sqlapp-command.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.sqlapp.data.db.command;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;

import javax.xml.stream.XMLStreamException;

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

public class DiffCommand extends AbstractCommand{
	/**
	 * Output originalFilePath
	 */
	private File originalFile;
	/**
	 * Output targetFilePath
	 */
	private File targetFile;

	private EqualsHandler equalsHandler=DefaultSchemaEqualsHandler.getInstance();
	
	@Override
	protected void doRun() {
		DbCommonObject<?> original = null;
		try {
			original = SchemaUtils.readXml(originalFile);
		} catch (FileNotFoundException e) {
			throw new CommandException("path="+originalFile, e);
		} catch (XMLStreamException e) {
			throw new CommandException("path="+originalFile, e);
		} catch (IOException e) {
			throw new CommandException("path="+originalFile, e);
		}
		DbCommonObject<?> target = null;
		try {
			target = SchemaUtils.readXml(targetFile);
		} catch (FileNotFoundException e) {
			throw new CommandException("path="+targetFile, e);
		} catch (XMLStreamException e) {
			throw new CommandException("path="+targetFile, e);
		} catch (IOException e) {
			throw new CommandException("path="+targetFile, e);
		}
		if (!CommonUtils.eq(original.getClass(), target.getClass())) {
			throw new CommandException(
					"Original and Target class unmatch. original=["
							+ original.getClass() + "], target=["
							+ target.getClass() + "].");
		}
		EqualsHandler equalsHandler = getEqualsHandler();
		boolean result = original.equals(target, equalsHandler);
		if (!result) {
			printResult(original, target, equalsHandler);
		} else {
			this.println("No difference found.");
		}
	}

	@SuppressWarnings({ "unchecked", "rawtypes" })
	private void printResult(DbCommonObject<?> original,
			DbCommonObject<?> target, EqualsHandler equalsHandler) {
		if (original instanceof DbObject) {
			DbObjectDifference diff = ((DbObject) original).diff(
					(DbObject) target, equalsHandler);
			this.println(diff);
		} else {
			DbObjectDifferenceCollection diff = ((DbObjectCollection) original)
					.diff((DbObjectCollection) target, equalsHandler);
			this.println(diff);
		}
	}

	@SuppressWarnings("rawtypes")
	protected void doExecute(DbCommonObject original, DbCommonObject target){
		if (original instanceof DbObject) {
			doExecute((DbObject) original, (DbObject) target);
		}
		if (original instanceof DbObjectCollection) {
			doExecute((DbObjectCollection) original,
					(DbObjectCollection) target);
		}
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected void doExecute(DbObject original, DbObject target){
		original.diff(target, this.getEqualsHandler());
	}

	@SuppressWarnings({ "rawtypes", "unchecked" })
	protected void doExecute(DbObjectCollection original,
			DbObjectCollection target) {
		original.diff(target, this.getEqualsHandler());
	}
	
	/**
	 * @return the equalsHandler
	 */
	public EqualsHandler getEqualsHandler() {
		return equalsHandler;
	}

	/**
	 * @param equalsHandler the equalsHandler to set
	 */
	public void setEqualsHandler(EqualsHandler equalsHandler) {
		this.equalsHandler = equalsHandler;
	}

	/**
	 * @return the originalFile
	 */
	public File getOriginalFile() {
		return originalFile;
	}

	/**
	 * @param originalFile the originalFile to set
	 */
	public void setOriginalFile(File originalFile) {
		this.originalFile = originalFile;
	}

	/**
	 * @return the targetFile
	 */
	public File getTargetFile() {
		return targetFile;
	}

	/**
	 * @param targetFile the targetFile to set
	 */
	public void setTargetFile(File targetFile) {
		this.targetFile = targetFile;
	}



}
