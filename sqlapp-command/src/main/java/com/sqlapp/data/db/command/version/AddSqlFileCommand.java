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
package com.sqlapp.data.db.command.version;

import java.io.File;
import java.io.IOException;

import com.sqlapp.data.db.command.AbstractCommand;

public class AddSqlFileCommand extends AbstractCommand{

	/**
	 * バージョンアップ用SQLのディレクトリ
	 */
	private File upSqlDirectory;
	/**
	 * バージョンダウン用のSQLのディレクトリ
	 */
	private File downSqlDirectory;
	
	private String version;
	
	private String description;
	
	@Override
	protected void doRun() {
		DbVersionFileHandler dbVersionFileHandler=new DbVersionFileHandler();
		dbVersionFileHandler.setUpSqlDirectory(this.getUpSqlDirectory());
		dbVersionFileHandler.setDownSqlDirectory(this.getDownSqlDirectory());
		try {
			dbVersionFileHandler.add(this.getDescription(), this.getDescription());
		} catch (IOException e) {
			this.getExceptionHandler().handle(e);
		}
	}
	
	/**
	 * @return the upSqlDirectory
	 */
	public File getUpSqlDirectory() {
		return upSqlDirectory;
	}

	/**
	 * @param upSqlDirectory the upSqlDirectory to set
	 */
	public void setUpSqlDirectory(File upSqlDirectory) {
		this.upSqlDirectory = upSqlDirectory;
	}

	/**
	 * @param upSqlDirectory the upSqlDirectory to set
	 */
	public void setUpSqlDirectory(String upSqlDirectory) {
		this.upSqlDirectory = new File(upSqlDirectory);
	}

	/**
	 * @return the downSqlDirectory
	 */
	public File getDownSqlDirectory() {
		return downSqlDirectory;
	}

	/**
	 * @param downSqlDirectory the downSqlDirectory to set
	 */
	public void setDownSqlDirectory(File downSqlDirectory) {
		this.downSqlDirectory = downSqlDirectory;
	}

	/**
	 * @param downSqlDirectory the downSqlDirectory to set
	 */
	public void setDownSqlDirectory(String downSqlDirectory) {
		this.downSqlDirectory = new File(downSqlDirectory);
	}


	/**
	 * @return the version
	 */
	public String getVersion() {
		return version;
	}

	/**
	 * @param version the version to set
	 */
	public void setVersion(String version) {
		this.version = version;
	}

	/**
	 * @return the description
	 */
	public String getDescription() {
		return description;
	}

	/**
	 * @param description the description to set
	 */
	public void setDescription(String description) {
		this.description = description;
	}
	
	
}
