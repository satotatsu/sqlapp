package com.sqlapp.data.db.dialect.postgres.sql;

import java.util.List;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;


import com.sqlapp.data.db.sql.AbstractCreateCheckConstraintFactory;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * PostgreSQL 18 CHECK constraint enforcement state.
 */
public class Postgres180CreateCheckConstraintFactory
		extends AbstractCreateCheckConstraintFactory<AbstractSqlBuilder<?>> implements PostgresConstraintOptions.NotValidFactory {
	public static final String NOT_ENFORCED = "notEnforced";

	@Override
	protected void addCheckConstraintAfter(CheckConstraint constraint, AbstractSqlBuilder<?> builder) {
		PostgresConstraintOptions.appendNoInherit(constraint, builder, true);
		if (Boolean.parseBoolean(constraint.getSpecifics().get(NOT_ENFORCED))) {
			builder.space()._add("NOT ENFORCED");
		}
		PostgresConstraintOptions.appendNotValid(constraint, builder, true);
	}

	@Override
	public List<SqlOperation> createSql(CheckConstraint constraint) {
		List<SqlOperation> result = super.createSql(constraint);
		if (!result.isEmpty() && constraint.getRemarks() != null) {
			var builder = createSqlBuilder();
			PostgresConstraintOptions.constraintComment(constraint, constraint.getTable(), builder,
					getOptions().isDecorateSchemaName());
			addSql(result, builder, SqlType.SET_COMMENT, constraint);
		}
		return result;
	}
}
