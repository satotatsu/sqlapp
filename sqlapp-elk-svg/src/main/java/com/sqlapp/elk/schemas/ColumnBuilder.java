/**
 * Copyright (C) 2007-2017 Tatsuo Satoh &lt;multisqllib@gmail.com&gt;
 *
 * This file is part of sqlapp-graphviz.
 *
 * sqlapp-graphviz is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-graphviz is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-graphviz.  If not, see &lt;http://www.gnu.org/licenses/&gt;.
 */

package com.sqlapp.elk.schemas;

import java.util.function.BiFunction;
import java.util.function.Function;

import com.sqlapp.data.db.dialect.Dialect;
import com.sqlapp.data.db.dialect.DialectResolver;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ColumnBuilder {

	private ColumnBuilder() {
	}

	public static ColumnBuilder create() {
		ColumnBuilder builder = new ColumnBuilder();
		return builder;
	}

	public static ColumnBuilder createSimple() {
		ColumnBuilder builder = new ColumnBuilder();
		builder.setDataType((c, type) -> null);
		builder.setNotNull(c -> null);
		builder.setIdentity(c -> null);
		builder.setDefaultValue(c -> null);
		builder.setCheck(c -> null);
		return builder;
	}

	private Function<Column, String> notNull = (c) -> {
		return c.isNotNull() ? "(NN)" : "";
	};

	private Function<Column, String> identity = (c) -> {
		return c.isIdentity() ? "IDENTITY" : "";
	};

	private Function<Column, String> defaultValue = (c) -> {
		return c.getDefaultValue();
	};

	private Function<Column, String> check = (c) -> {
		return c.getCheck();
	};

	private BiFunction<Column, String, String> dataType = (c, type) -> {
		return type;
	};

	public String build(Column column) {
		StringBuilder builder = new StringBuilder();
		createDataType(builder, column);
		createNotNull(builder, column);
		createIdentity(builder, column);
		createDefaultValue(builder, column);
		createCheck(builder, column);
		return builder.toString();
	}

	private void append(StringBuilder builder, String value) {
		if (CommonUtils.isEmpty(value)) {
			return;
		}
		if (builder.length() > 0) {
			builder.append(" ");
		}
		builder.append(value);
	}

	private void createNotNull(StringBuilder builder, Column column) {
		String value = notNull.apply(column);
		append(builder, value);
	}

	private void createDataType(StringBuilder builder, final Column column) {
		Dialect dialect = column.getDialect();
		if (dialect == null) {
			dialect = DialectResolver.getInstance().getDefaultDialect();
		}
		AbstractSqlBuilder<?> sqlBuilder = dialect.createSqlBuilder();
		sqlBuilder.addTypeDefinition(column);
		String type = sqlBuilder.toString();
		String value = dataType.apply(column, type);
		append(builder, value);
	}

	private void createIdentity(StringBuilder builder, Column column) {
		String value = identity.apply(column);
		append(builder, value);
	}

	private void createDefaultValue(StringBuilder builder, Column column) {
		String value = defaultValue.apply(column);
		append(builder, value);
	}

	private void createCheck(StringBuilder builder, Column column) {
		String value = check.apply(column);
		append(builder, value);
	}
}
