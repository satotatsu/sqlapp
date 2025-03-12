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
import java.sql.Connection;
import java.sql.SQLException;

import com.sqlapp.jdbc.sql.SqlConverter;

public abstract class AbstractSqlCommand extends AbstractDataSourceCommand {

	/** file directory */
	private File fileDirectory = new File("./");
	/** encoding */
	private String encoding = "UTF-8";

	private String placeholderPrefix = "${";

	private String placeholderSuffix = "}";

	private boolean placeholders = false;

	@Override
	protected void initialize() {
		super.initialize();
	}

	protected void rollback(final Connection connection) {
		if (connection == null) {
			return;
		}
		try {
			connection.rollback();
		} catch (final SQLException e) {
			logger.error("rollback failed.", e);
		}
	}

	/**
	 * @return the fileDirectory
	 */
	public File getFileDirectory() {
		return fileDirectory;
	}

	/**
	 * @param fileDirectory the fileDirectory to set
	 */
	public void setFileDirectory(final File fileDirectory) {
		this.fileDirectory = fileDirectory;
	}

	/**
	 * @return the encoding
	 */
	public String getEncoding() {
		return encoding;
	}

	/**
	 * @param encoding the encoding to set
	 */
	public void setEncoding(final String encoding) {
		this.encoding = encoding;
	}

	/**
	 * @return the placeholderPrefix
	 */
	public String getPlaceholderPrefix() {
		return placeholderPrefix;
	}

	/**
	 * @param placeholderPrefix the placeholderPrefix to set
	 */
	public void setPlaceholderPrefix(final String placeholderPrefix) {
		this.placeholderPrefix = placeholderPrefix;
	}

	/**
	 * @return the placeholderSuffix
	 */
	public String getPlaceholderSuffix() {
		return placeholderSuffix;
	}

	/**
	 * @param placeholderSuffix the placeholderSuffix to set
	 */
	public void setPlaceholderSuffix(final String placeholderSuffix) {
		this.placeholderSuffix = placeholderSuffix;
	}

	/**
	 * @return the placeholders
	 */
	public boolean isPlaceholders() {
		return placeholders;
	}

	/**
	 * @param placeholders the placeholders to set
	 */
	public void setPlaceholders(final boolean placeholders) {
		this.placeholders = placeholders;
	}

	protected SqlConverter getSqlConverter() {
		final SqlConverter sqlConverter = new SqlConverter();
		sqlConverter.getExpressionConverter().setFileDirectory(this.getFileDirectory());
		sqlConverter.getExpressionConverter().setPlaceholderPrefix(this.getPlaceholderPrefix());
		sqlConverter.getExpressionConverter().setPlaceholderSuffix(this.getPlaceholderSuffix());
		sqlConverter.getExpressionConverter().setPlaceholders(this.isPlaceholders());
		return sqlConverter;
	}

}
