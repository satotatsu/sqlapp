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

import com.sqlapp.data.db.command.properties.EncodingProperty;
import com.sqlapp.data.db.command.properties.FileDirectoryProperty;
import com.sqlapp.data.db.command.properties.PlaceholderProperty;
import com.sqlapp.jdbc.sql.SqlConverter;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public abstract class AbstractSqlCommand extends AbstractDataSourceCommand
		implements PlaceholderProperty, FileDirectoryProperty, EncodingProperty {

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

	protected SqlConverter getSqlConverter() {
		final SqlConverter sqlConverter = new SqlConverter();
		sqlConverter.getExpressionConverter().setFileDirectory(this.getFileDirectory());
		sqlConverter.getExpressionConverter().setPlaceholderPrefix(this.getPlaceholderPrefix());
		sqlConverter.getExpressionConverter().setPlaceholderSuffix(this.getPlaceholderSuffix());
		sqlConverter.getExpressionConverter().setPlaceholders(this.isPlaceholders());
		return sqlConverter;
	}

}
