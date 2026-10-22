/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.h2.sql;

import com.sqlapp.data.db.dialect.h2.H2_220;
import com.sqlapp.data.db.dialect.h2.util.H2SqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateUniqueConstraintFactory;
import com.sqlapp.data.schemas.UniqueConstraint;

/** Preserve explicit null treatment for inline and standalone UNIQUE DDL. */
public class H2CreateUniqueConstraintFactory extends AbstractCreateUniqueConstraintFactory<H2SqlBuilder> {
	@Override
	protected void addOption(UniqueConstraint constraint, H2SqlBuilder builder) {
		String policy = constraint.getSpecifics().get("h2.nullsDistinct");
		if (policy == null) {
			return;
		}
		if (constraint.isPrimaryKey()) {
			throw new IllegalArgumentException("h2.nullsDistinct applies only to UNIQUE constraints");
		}
		if (!(getDialect() instanceof H2_220)) {
			throw new IllegalArgumentException("h2.nullsDistinct requires H2 2.2 or later");
		}
		String clause = switch (policy) {
		case "YES" -> "NULLS DISTINCT";
		case "NO" -> "NULLS NOT DISTINCT";
		case "ALL" -> "NULLS ALL DISTINCT";
		default -> throw new IllegalArgumentException("h2.nullsDistinct must be YES, NO, or ALL: " + policy);
		};
		builder.space()._add(clause);
	}
}
