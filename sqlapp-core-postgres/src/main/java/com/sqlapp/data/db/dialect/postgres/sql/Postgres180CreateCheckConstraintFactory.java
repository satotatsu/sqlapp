package com.sqlapp.data.db.dialect.postgres.sql;

import com.sqlapp.data.db.sql.AbstractCreateCheckConstraintFactory;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * PostgreSQL 18 CHECK constraint enforcement state.
 */
public class Postgres180CreateCheckConstraintFactory
		extends AbstractCreateCheckConstraintFactory<AbstractSqlBuilder<?>> {
	public static final String NOT_ENFORCED = "notEnforced";

	@Override
	protected void addCheckConstraintAfter(CheckConstraint constraint,
			AbstractSqlBuilder<?> builder) {
		if (Boolean.parseBoolean(
				constraint.getSpecifics().get(NOT_ENFORCED))) {
			builder.space()._add("NOT ENFORCED");
		}
	}
}
