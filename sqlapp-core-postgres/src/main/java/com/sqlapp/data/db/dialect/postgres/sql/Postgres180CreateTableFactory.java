/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 */
package com.sqlapp.data.db.dialect.postgres.sql;

import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.schemas.NotNullConstraint;
import com.sqlapp.data.schemas.Table;

/**
 * PostgreSQL 18 table creation, including named NOT NULL constraints.
 */
public class Postgres180CreateTableFactory extends PostgresCreateTableFactory {
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
		}
	}
}
