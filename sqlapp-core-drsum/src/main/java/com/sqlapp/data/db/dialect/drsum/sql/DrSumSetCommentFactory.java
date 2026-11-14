/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-drsum.
 *
 * sqlapp-core-drsum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * sqlapp-core-drsum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with sqlapp-core-drsum.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.sqlapp.data.db.dialect.drsum.sql;

import static com.sqlapp.util.CommonUtils.list;

import java.util.List;

import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.AbstractSchemaObject;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

/**
 * Dr.Sum table and column comments:
 * {@code ALTER TABLE t MODIFY COMMENT '...'},
 * {@code ALTER TABLE t MODIFY c COMMENT '...'} and the DROP COMMENT forms when
 * the comment is empty.
 */
public class DrSumSetCommentFactory extends SimpleSqlFactory<AbstractSchemaObject<?>, AbstractSqlBuilder<?>> {

	@Override
	public List<SqlOperation> createSql(final AbstractSchemaObject<?> obj) {
		final AbstractSqlBuilder<?> builder = createSqlBuilder();
		if (obj instanceof Table table) {
			builder._add("ALTER TABLE ").name(table.getName());
			if (CommonUtils.isEmpty(table.getRemarks())) {
				builder._add(" DROP COMMENT");
			} else {
				builder._add(" MODIFY COMMENT ")
						.sqlChar(DrSumSqlSupport.validateComment(table.getRemarks(), table.getName()));
			}
		} else if (obj instanceof Column column) {
			columnComment(column, builder);
		} else {
			throw new IllegalArgumentException("Dr.Sum comments are supported on tables and columns only. object="
					+ obj.getClass().getSimpleName() + ":" + obj.getName());
		}
		final List<SqlOperation> result = list();
		addSql(result, builder, SqlType.SET_COMMENT, obj);
		return result;
	}

	/** Creates the column comment statement. */
	static void columnComment(final Column column, final AbstractSqlBuilder<?> builder) {
		builder._add("ALTER TABLE ").name(column.getTableName());
		if (CommonUtils.isEmpty(column.getRemarks())) {
			builder._add(" DROP COMMENT ").name(column.getName());
		} else {
			builder._add(" MODIFY ").name(column.getName())._add(" COMMENT ").sqlChar(DrSumSqlSupport
					.validateComment(column.getRemarks(), column.getTableName() + "." + column.getName()));
		}
	}
}
