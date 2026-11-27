/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.oracle.sql;

import com.sqlapp.data.schemas.Constraint;
import com.sqlapp.data.schemas.Deferrability;
import com.sqlapp.data.db.dialect.oracle.util.OracleSqlBuilder;

/** Shared Oracle table-constraint state clauses. */
final class OracleConstraintState {
	private OracleConstraintState() {
	}

	static void deferrability(Constraint constraint, OracleSqlBuilder builder) {
		Deferrability value = constraint.getDeferrability();
		if (value == null)
			return;
		if (value != Deferrability.NotDeferrable)
			builder.space()._add("DEFERRABLE");
		builder.space()._add(value.getSqlValue());
	}

	static void after(Constraint constraint, OracleSqlBuilder builder) {
		String rely = constraint.getSpecifics().get("RELY");
		String validated = constraint.getSpecifics().get("VALIDATED");
		if (rely != null && !"RELY".equals(rely) && !"NORELY".equals(rely))
			throw new IllegalArgumentException("Invalid constraint RELY: " + constraint.getName());
		if (validated != null && !"VALIDATED".equals(validated) && !"NOT VALIDATED".equals(validated))
			throw new IllegalArgumentException("Invalid constraint VALIDATED: " + constraint.getName());
		if (rely != null)
			builder.space()._add(rely);
		if (!constraint.isEnable() || validated != null)
			builder.space()._add(constraint.isEnable() ? "ENABLE" : "DISABLE");
		if (validated != null)
			builder.space()._add("VALIDATED".equals(validated) ? "VALIDATE" : "NOVALIDATE");
	}
}
