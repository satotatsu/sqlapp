/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.sql;

import com.sqlapp.data.db.dialect.informix.Informix;
import com.sqlapp.data.db.dialect.informix.util.InformixConstraintModes;
import com.sqlapp.data.db.sql.AbstractCreateNamedObjectFactory;
import com.sqlapp.data.schemas.NotNullConstraint;
import com.sqlapp.util.AbstractSqlBuilder;

/** Informix adds NOT NULL through a complete column MODIFY declaration. */
public class InformixCreateNotNullConstraintFactory
		extends AbstractCreateNamedObjectFactory<NotNullConstraint, AbstractSqlBuilder<?>> {
	@Override
	protected void addCreateObject(final NotNullConstraint constraint, final AbstractSqlBuilder<?> builder) {
		if (constraint.getTable() == null || constraint.getColumn() == null)
			throw new IllegalArgumentException(
					"Informix NOT NULL requires its table and complete column: " + constraint.getName());
		if (constraint.isNoInherit() || !constraint.isValidated())
			throw new IllegalArgumentException("Unsupported Informix NOT NULL options: " + constraint.getName());
		final var column = constraint.getColumn().clone();
		column.setNotNull(constraint.isEnable());
		column.getSpecifics().remove(InformixConstraintModes.NOT_NULL_NAME);
		column.getSpecifics().remove(InformixConstraintModes.NOT_NULL_OWNER);
		column.getSpecifics().remove(InformixConstraintModes.NOT_NULL_MODE);
		if (constraint.getName() != null)
			column.getSpecifics().put(InformixConstraintModes.NOT_NULL_NAME, constraint.getName());
		final String owner = constraint.getSpecifics().get(Informix.OBJECT_OWNER);
		if (owner != null && getOptions().isDecorateSchemaName())
			column.getSpecifics().put(InformixConstraintModes.NOT_NULL_OWNER, owner);
		final String mode = InformixConstraintModes.sql(constraint);
		if (mode != null)
			column.getSpecifics().put(InformixConstraintModes.NOT_NULL_MODE, mode);
		builder.alter().table().name(constraint.getTable(), getOptions().isDecorateSchemaName()).space()
				._add("MODIFY (").name(column).space().definition(column, false)._add(')');
	}
}
