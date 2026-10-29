/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.sql;

import com.sqlapp.data.db.sql.AbstractCreateCheckConstraintFactory;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;

/** Informix names CHECK constraints after their expression. */
public class InformixCreateCheckConstraintFactory extends AbstractCreateCheckConstraintFactory<AbstractSqlBuilder<?>> {
	@Override
	public void addObjectDetail(final CheckConstraint constraint, final Table table,
			final AbstractSqlBuilder<?> builder) {
		if (constraint.getExpression() == null || constraint.getExpression().isBlank())
			throw new IllegalArgumentException("Informix CHECK expression is required: " + constraint.getName());
		builder.check().space()._add('(')._add(constraint.getExpression())._add(')');
		InformixConstraintNames.append(constraint, builder, getOptions().isDecorateSchemaName());
		final String mode = com.sqlapp.data.db.dialect.informix.util.InformixConstraintModes.sql(constraint);
		if (mode != null)
			builder.space()._add(mode);
	}
}
