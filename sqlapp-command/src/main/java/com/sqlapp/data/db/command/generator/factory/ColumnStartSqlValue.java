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
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;

public class ColumnStartSqlValue implements BiFunction<Column, Dialect, String>, Serializable {

	/** serialVersionUID */
	private static final long serialVersionUID = -2049712084354162318L;

	@Override
	public String apply(Column column, Dialect dialect) {
		if (column.getParent() != null && column.getParent().getParent() != null) {
			Table table = column.getParent().getParent();
			if (table.getPrimaryKeyConstraint() == null) {
				return getExpression(column, dialect);
			}
			boolean contains = table.getPrimaryKeyConstraint().getColumns().contains(column.getName());
			if (contains) {
				return getExpression(column, dialect);
			}
			return null;
		}
		return getExpression(column, dialect);
	}

	protected String getExpression(Column column, Dialect dialect) {
		if (column.getDataType().isNumeric()) {
			if (column.getDataType() == DataType.FLOAT || column.getDataType() == DataType.DOUBLE) {
				return getNullExpression(column, dialect);
			}
			return getNumberExpression(column, dialect);
		}
		return getMaxExpression(column, dialect);
	}

	protected String getNumberExpression(Column column, Dialect dialect) {
		final AbstractSqlBuilder<?> builder = dialect.createSqlBuilder();
		builder.coalesce().brackets(() -> {
			builder.max().brackets(() -> {
				builder.name(column)._add(" + 1");
			});
			builder.comma()._add(" 1");
		}).as().name(column);
		return builder.toString();
	}

	protected String getMaxExpression(Column column, Dialect dialect) {
		final AbstractSqlBuilder<?> builder = dialect.createSqlBuilder();
		builder.max().brackets(() -> {
			builder.name(column);
		}).as().name(column);
		return builder.toString();
	}

	protected String getNullExpression(Column column, Dialect dialect) {
		final AbstractSqlBuilder<?> builder = dialect.createSqlBuilder();
		builder.max().brackets(() -> {
			builder._null();
		}).as().name(column);
		return builder.toString();
	}
}
