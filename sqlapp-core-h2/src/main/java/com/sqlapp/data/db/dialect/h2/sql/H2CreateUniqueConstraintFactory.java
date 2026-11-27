/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.h2.sql;

import com.sqlapp.data.db.dialect.h2.util.H2SqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateUniqueConstraintFactory;
import com.sqlapp.data.schemas.UniqueConstraint;

/** Preserve explicit null treatment for inline and standalone UNIQUE DDL. */
public class H2CreateUniqueConstraintFactory extends AbstractCreateUniqueConstraintFactory<H2SqlBuilder> {
	@Override
	protected void addOption(UniqueConstraint constraint, H2SqlBuilder builder) {
		String policy = constraint.getSpecifics().get(H2NullsDistinct.KEY);
		if (policy != null && constraint.isPrimaryKey()) {
			throw new IllegalArgumentException(H2NullsDistinct.KEY + " applies only to UNIQUE constraints");
		}
		String clause = H2NullsDistinct.clause(getDialect(), policy);
		if (clause != null) {
			builder.space()._add(clause);
		}
	}
}
