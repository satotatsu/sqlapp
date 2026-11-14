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
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * Removes a key from a Dr.Sum table: {@code ALTER TABLE t DROP CONSTRAINT k}
 * for a COMPOUND KEY, or redefines the column without UNIQUE for a
 * single-column key.
 */
public class DrSumDropUniqueConstraintFactory extends SimpleSqlFactory<UniqueConstraint, AbstractSqlBuilder<?>> {

	@Override
	public List<SqlOperation> createSql(final UniqueConstraint obj) {
		final AbstractSqlBuilder<?> builder = createSqlBuilder();
		if (obj.getColumns().size() > 1) {
			builder._add("ALTER TABLE ").name(obj.getTableName())._add(" DROP CONSTRAINT ").name(obj.getName());
		} else {
			final Column column = DrSumCreateUniqueConstraintFactory.requireColumn(obj);
			builder._add("ALTER TABLE ").name(obj.getTableName())._add(" ALTER ").name(column.getName()).space();
			DrSumSqlSupport.addType(column, builder);
			if (column.isNotNull()) {
				builder._add(" NOT NULL");
			}
		}
		final List<SqlOperation> result = list();
		addSql(result, builder, SqlType.DROP, obj);
		return result;
	}
}
