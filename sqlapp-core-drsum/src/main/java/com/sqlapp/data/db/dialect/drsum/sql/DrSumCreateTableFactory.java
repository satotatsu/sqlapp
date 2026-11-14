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

import java.util.List;

import com.sqlapp.data.db.sql.AbstractCreateTableFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

/**
 * Dr.Sum CREATE TABLE.
 * <p>
 * Column syntax: {@code <name> <type> [DEFAULT <literal>] [NOT NULL] [UNIQUE]}.
 * Multi-column keys become {@code COMPOUND KEY} table constraints. Search
 * indexes and comments are emitted as separate statements because CREATE TABLE
 * accepts neither. Column character sets and collations are not emitted: Dr.Sum
 * stores every string as UTF-8 and has no such column attribute.
 */
public class DrSumCreateTableFactory extends AbstractCreateTableFactory<AbstractSqlBuilder<?>> {

	@Override
	public List<SqlOperation> createSql(final Table table) {
		DrSumSqlSupport.validateTable(table);
		return super.createSql(table);
	}

	@Override
	protected void addColumnDefinition(final Column column, final AbstractSqlBuilder<?> builder) {
		final Table table = column.getTable();
		builder.name(column).space();
		DrSumSqlSupport.addType(column, builder);
		if (!CommonUtils.isEmpty(column.getDefaultValue())) {
			builder._add(" DEFAULT ")._add(column.getDefaultValue());
		}
		final boolean primaryKey = table != null && DrSumSqlSupport.isSingleColumnPrimaryKey(table, column);
		if (column.isNotNull() || primaryKey) {
			builder._add(" NOT NULL");
		}
		if (table != null && DrSumSqlSupport.isSingleColumnUnique(table, column)) {
			builder._add(" UNIQUE");
		}
	}

	@Override
	protected void addConstraintDefinitions(final Table table, final AbstractSqlBuilder<?> builder) {
		for (final UniqueConstraint constraint : table.getConstraints().getUniqueConstraints()) {
			if (constraint.getColumns().size() > 1) {
				builder.lineBreak().comma();
				DrSumSqlSupport.addCompoundKey(constraint, builder);
			}
		}
	}

	@Override
	protected void addCreateIndexDefinition(final Table table, final Index index, final List<SqlOperation> result) {
		result.addAll(getSqlFactoryRegistry().createSql(index, SqlType.CREATE));
	}

	@Override
	protected void addOtherDefinitions(final Table table, final List<SqlOperation> result) {
		if (!CommonUtils.isEmpty(table.getRemarks())) {
			result.addAll(getSqlFactoryRegistry().createSql(table, SqlType.SET_COMMENT));
		}
		for (final Column column : table.getColumns()) {
			if (!CommonUtils.isEmpty(column.getRemarks())) {
				result.addAll(getSqlFactoryRegistry().createSql(column, SqlType.SET_COMMENT));
			}
		}
	}
}
