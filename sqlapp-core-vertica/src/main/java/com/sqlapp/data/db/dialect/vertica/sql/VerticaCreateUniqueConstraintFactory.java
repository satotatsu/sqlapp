/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.vertica.sql;

import com.sqlapp.data.db.dialect.vertica.util.VerticaSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateUniqueConstraintFactory;
import com.sqlapp.data.schemas.UniqueConstraint;

/**
 * Preserves explicit Vertica PK/UNIQUE enforcement independently of server
 * defaults.
 */
public class VerticaCreateUniqueConstraintFactory extends AbstractCreateUniqueConstraintFactory<VerticaSqlBuilder> {
	@Override
	protected void addAfter(UniqueConstraint constraint, VerticaSqlBuilder builder) {
		if (getDialect() instanceof com.sqlapp.data.db.dialect.vertica.Vertica72)
			builder.space()._add(constraint.isEnable() ? "ENABLED" : "DISABLED");
	}
}
