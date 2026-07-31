/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 */
package com.sqlapp.data.db.dialect.postgres.sql;

import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.schemas.NotNullConstraint;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.util.CommonUtils;

/**
 * PostgreSQL 18 table creation, including named NOT NULL constraints.
 */
public class Postgres180CreateTableFactory extends PostgresCreateTableFactory {
	@Override
	protected void addColumnDefinition(Column column,
			PostgresSqlBuilder builder) {
		NotNullConstraint constraint = findNotNullConstraint(column);
		if (constraint == null || !column.isNotNull()) {
			super.addColumnDefinition(column, builder);
			return;
		}
		column.setNotNull(false);
		try {
			super.addColumnDefinition(column, builder);
		} finally {
			column.setNotNull(true);
		}
	}

	private NotNullConstraint findNotNullConstraint(Column column) {
		Table table = column.getTable();
		if (table == null) {
			return null;
		}
		for (NotNullConstraint constraint : table.getConstraints()
				.getNotNullConstraints()) {
			if (CommonUtils.eqIgnoreCase(column.getName(),
					constraint.getColumnName())) {
				return constraint;
			}
		}
		return null;
	}

	@Override
	protected void addConstraintDefinitions(Table table,
			PostgresSqlBuilder builder) {
		super.addConstraintDefinitions(table, builder);
		for (NotNullConstraint constraint : table.getConstraints()
				.getNotNullConstraints()) {
			builder.lineBreak().comma().constraint().space()
					.name(constraint.getName()).space().notNull().space()
					.name(constraint.getColumnName());
			if (constraint.isNoInherit()) {
				builder.space()._add("NO INHERIT");
			}
			if (!constraint.isValidated()) {
				builder.space()._add("NOT VALID");
			}
		}
	}
}
