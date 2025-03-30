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

package com.sqlapp.data.db.command.generator.factory;

import java.io.Serializable;
import java.util.function.BiFunction;

import com.sqlapp.data.db.datatype.DataType;
import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.util.AbstractSqlBuilder;

public class ColumnStartSqlValue implements BiFunction<Column, Dialect, String>, Serializable {

	/** serialVersionUID */
	private static final long serialVersionUID = -2049712084354162318L;

	@Override
	public String apply(Column column, Dialect dialect) {
		if (column.getDataType().isBoolean()) {
			return null;
		}
		if (column.getDataType() == DataType.BIT || column.getDataType() == DataType.REAL
				|| column.getDataType() == DataType.FLOAT || column.getDataType() == DataType.DOUBLE) {
			return null;
		}
		if (column.getDataType().isNumeric()) {
			final AbstractSqlBuilder<?> builder = dialect.createSqlBuilder();
			builder.name(column)._add(" + 1").as().name(column);
			return builder.toString();
		}
		return null;
	}

}
