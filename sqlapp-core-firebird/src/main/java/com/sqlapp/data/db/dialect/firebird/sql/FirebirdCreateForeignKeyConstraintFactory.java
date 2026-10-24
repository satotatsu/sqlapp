/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateForeignKeyConstraintFactory;
import com.sqlapp.data.schemas.ForeignKeyConstraint;

public class FirebirdCreateForeignKeyConstraintFactory extends AbstractCreateForeignKeyConstraintFactory<FirebirdSqlBuilder> {
	@Override protected void addAfter(ForeignKeyConstraint constraint, FirebirdSqlBuilder builder) {
		String direction = constraint.getSpecifics().get("firebird.indexDescending");
		if (direction != null && !java.util.Set.of("true", "false").contains(direction))
			throw new IllegalArgumentException("firebird.indexDescending must be true or false");
		FirebirdConstraintSql.index(constraint, "true".equals(direction), builder);
	}
}
