/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.sql;

import com.sqlapp.data.db.sql.AbstractCreateForeignKeyConstraintFactory;
import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * Informix names foreign keys after REFERENCES and supports delete cascading.
 */
public class InformixCreateForeignKeyConstraintFactory
		extends AbstractCreateForeignKeyConstraintFactory<AbstractSqlBuilder<?>> {
	@Override
	public void addCreateObject(final ForeignKeyConstraint constraint, final AbstractSqlBuilder<?> builder) {
		builder.alter().table().name(constraint.getTable(), getOptions().isDecorateSchemaName());
		builder.add().constraint().space();
		addObjectDetail(constraint, constraint.getTable(), builder);
	}

	@Override
	public void addObjectDetail(final ForeignKeyConstraint constraint, final Table table,
			final AbstractSqlBuilder<?> builder) {
		builder.foreignKey().space()._add('(').names(constraint.getColumns())._add(')').references();
		builder.name(constraint.getRelatedTable(), getOptions().isDecorateSchemaName() || table != null
				&& !java.util.Objects.equals(table.getSchemaName(), constraint.getRelatedTable().getSchemaName()));
		builder.space()._add('(').names(constraint.getRelatedColumns())._add(')');
		if (constraint.getUpdateRule() != null && constraint.getUpdateRule() != CascadeRule.None
				&& constraint.getUpdateRule() != CascadeRule.Restrict) {
			throw new UnsupportedOperationException(
					"Informix does not support ON UPDATE " + constraint.getUpdateRule());
		}
		if (constraint.getDeleteRule() == CascadeRule.Cascade) {
			builder.space()._add("ON DELETE CASCADE");
		} else if (constraint.getDeleteRule() != null && constraint.getDeleteRule() != CascadeRule.None
				&& constraint.getDeleteRule() != CascadeRule.Restrict) {
			throw new UnsupportedOperationException(
					"Unsupported Informix ON DELETE rule: " + constraint.getDeleteRule());
		}
		InformixConstraintNames.append(constraint, builder, getOptions().isDecorateSchemaName());
		final String mode = com.sqlapp.data.db.dialect.informix.util.InformixConstraintModes.sql(constraint);
		if (mode != null)
			builder.space()._add(mode);
	}
}
