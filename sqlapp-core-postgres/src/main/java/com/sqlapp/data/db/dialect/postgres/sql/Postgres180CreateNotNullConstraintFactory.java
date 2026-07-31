/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 */
package com.sqlapp.data.db.dialect.postgres.sql;

import static com.sqlapp.util.CommonUtils.list;

import java.util.List;

import com.sqlapp.data.db.sql.AbstractCreateNamedObjectFactory;
import com.sqlapp.data.db.sql.AddTableObjectDetailFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.NotNullConstraint;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * PostgreSQL 18 named NOT NULL constraint creation.
 */
public class Postgres180CreateNotNullConstraintFactory
		extends AbstractCreateNamedObjectFactory<NotNullConstraint,
				AbstractSqlBuilder<?>>
		implements AddTableObjectDetailFactory<NotNullConstraint,
				AbstractSqlBuilder<?>> {

	@Override
	public List<SqlOperation> createSql(NotNullConstraint constraint) {
		List<SqlOperation> result = list();
		AbstractSqlBuilder<?> builder = createSqlBuilder();
		addCreateObject(constraint, builder);
		addSql(result, builder, SqlType.CREATE, constraint);
		return result;
	}

	@Override
	public void addCreateObject(NotNullConstraint constraint,
			AbstractSqlBuilder<?> builder) {
		builder.alter().table()
				.name(constraint.getTable(),
						getOptions().isDecorateSchemaName())
				.add();
		addObjectDetail(constraint, constraint.getTable(), builder);
	}

	@Override
	public void addObjectDetail(NotNullConstraint constraint, Table table,
			AbstractSqlBuilder<?> builder) {
		builder.constraint().space().name(constraint.getName()).space()
				.notNull().space().name(constraint.getColumnName());
		if (constraint.isNoInherit()) {
			builder.space()._add("NO INHERIT");
		}
		if (!constraint.isValidated()) {
			builder.space()._add("NOT VALID");
		}
	}
}
