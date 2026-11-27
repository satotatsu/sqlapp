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
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * Adds a key to an existing Dr.Sum table. A multi-column key becomes
 * {@code ALTER TABLE t ADD CONSTRAINT COMPOUND KEY ...}; a single-column key
 * redefines the column with {@code ALTER TABLE t ALTER c <type> UNIQUE [NOT NULL]}
 * because Dr.Sum ALTER COLUMN replaces every column constraint.
 */
public class DrSumCreateUniqueConstraintFactory extends SimpleSqlFactory<UniqueConstraint, AbstractSqlBuilder<?>> {

	@Override
	public List<SqlOperation> createSql(final UniqueConstraint obj) {
		final AbstractSqlBuilder<?> builder = createSqlBuilder();
		if (obj.getColumns().size() > 1) {
			if (obj.getName() == null || obj.getName().isEmpty()) {
				throw new IllegalArgumentException(
						"Dr.Sum COMPOUND KEY requires a name. table=" + obj.getTableName());
			}
			builder._add("ALTER TABLE ").name(obj.getTableName())._add(" ADD CONSTRAINT ");
			DrSumSqlSupport.addCompoundKey(obj, builder);
		} else {
			final Column column = requireColumn(obj);
			builder._add("ALTER TABLE ").name(obj.getTableName())._add(" ALTER ").name(column.getName()).space();
			DrSumSqlSupport.addType(column, builder);
			builder._add(" UNIQUE");
			if (column.isNotNull() || obj.isPrimaryKey()) {
				builder._add(" NOT NULL");
			}
		}
		final List<SqlOperation> result = list();
		addSql(result, builder, SqlType.CREATE, obj);
		return result;
	}

	static Column requireColumn(final UniqueConstraint obj) {
		final Table table = obj.getTable();
		if (obj.getColumns().size() != 1 || table == null) {
			throw new IllegalArgumentException(
					"Dr.Sum single-column key change requires its table definition. constraint=" + obj.getName());
		}
		final Column column = table.getColumns().get(obj.getColumns().get(0).getName());
		if (column == null) {
			throw new IllegalArgumentException("Dr.Sum key column not found. constraint=" + obj.getName()
					+ ", column=" + obj.getColumns().get(0).getName());
		}
		return column;
	}
}
