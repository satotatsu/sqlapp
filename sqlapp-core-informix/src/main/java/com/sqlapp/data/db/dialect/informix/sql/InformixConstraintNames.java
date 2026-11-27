/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.sql;

import com.sqlapp.data.db.dialect.informix.Informix;
import com.sqlapp.data.schemas.Constraint;
import com.sqlapp.util.AbstractSqlBuilder;

/** Shares Informix's trailing constraint name and optional owner decoration. */
final class InformixConstraintNames {
	private InformixConstraintNames() {
	}

	static void append(final Constraint constraint, final AbstractSqlBuilder<?> builder, final boolean decorateOwner) {
		if (constraint.getName() == null)
			return;
		builder.space().constraint().space();
		final String owner = constraint.getSpecifics().get(Informix.OBJECT_OWNER);
		if (decorateOwner && owner != null) {
			builder.name(owner)._add('.').name(constraint.getName());
		} else {
			builder.name(constraint, decorateOwner);
		}
	}
}
