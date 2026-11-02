/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.virtica.sql;

import com.sqlapp.data.db.dialect.virtica.util.VirticaSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateCheckConstraintFactory;
import com.sqlapp.data.schemas.CheckConstraint;
import com.sqlapp.data.schemas.Table;

/** Requires a real CHECK expression and preserves enforcement state. */
public class VirticaCreateCheckConstraintFactory extends AbstractCreateCheckConstraintFactory<VirticaSqlBuilder> {
	@Override
	public void addObjectDetail(CheckConstraint constraint, Table table, VirticaSqlBuilder builder) {
		if (!(getDialect() instanceof com.sqlapp.data.db.dialect.virtica.Virtica72))
			throw new IllegalArgumentException("Vertica CHECK constraints require version 7.2 or later");
		if (constraint.getExpression() == null || constraint.getExpression().isBlank())
			throw new IllegalArgumentException(
					"Vertica CHECK expression is required; read TABLE_CONSTRAINTS.PREDICATE or supply the expression: "
							+ constraint.getName());
		super.addObjectDetail(constraint, table, builder);
	}

	@Override
	protected void addCheckConstraintAfter(CheckConstraint constraint, VirticaSqlBuilder builder) {
		builder.space()._add(constraint.isEnable() ? "ENABLED" : "DISABLED");
	}
}
