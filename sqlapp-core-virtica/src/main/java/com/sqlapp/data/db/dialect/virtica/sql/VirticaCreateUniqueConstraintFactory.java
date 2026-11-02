/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.virtica.sql;

import com.sqlapp.data.db.dialect.virtica.util.VirticaSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateUniqueConstraintFactory;
import com.sqlapp.data.schemas.UniqueConstraint;

/**
 * Preserves explicit Vertica PK/UNIQUE enforcement independently of server
 * defaults.
 */
public class VirticaCreateUniqueConstraintFactory extends AbstractCreateUniqueConstraintFactory<VirticaSqlBuilder> {
	@Override
	protected void addAfter(UniqueConstraint constraint, VirticaSqlBuilder builder) {
		if (getDialect() instanceof com.sqlapp.data.db.dialect.virtica.Virtica72)
			builder.space()._add(constraint.isEnable() ? "ENABLED" : "DISABLED");
	}
}
