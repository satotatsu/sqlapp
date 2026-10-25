/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateForeignKeyConstraintFactory;
import com.sqlapp.data.schemas.ForeignKeyConstraint;

public class FirebirdCreateForeignKeyConstraintFactory
		extends AbstractCreateForeignKeyConstraintFactory<FirebirdSqlBuilder> {
	@Override
	public void addCreateObject(ForeignKeyConstraint constraint, FirebirdSqlBuilder builder) {
		if (constraint.getTable() == null)
			throw new IllegalArgumentException("Firebird foreign key requires an owning table");
		super.addCreateObject(constraint, builder);
	}

	@Override
	public void addObjectDetail(ForeignKeyConstraint constraint, com.sqlapp.data.schemas.Table table,
			FirebirdSqlBuilder builder) {
		FirebirdConstraintSql.validate(constraint);
		if (constraint.getTable() == null || constraint.getRelatedTable() == null || constraint.getColumns().isEmpty()
				|| constraint.getColumns().size() != constraint.getRelatedColumns().size())
			throw new IllegalArgumentException(
					"Firebird foreign key requires its table, a resolved parent and matching non-empty column lists");
		if (constraint.getMatchOption() != null
				&& constraint.getMatchOption() != ForeignKeyConstraint.MatchOption.Simple)
			throw new IllegalArgumentException("Firebird foreign keys support MATCH SIMPLE only");
		super.addObjectDetail(constraint, table, builder);
	}

	@Override
	protected void addAfter(ForeignKeyConstraint constraint, FirebirdSqlBuilder builder) {
		String direction = constraint.getSpecifics().get("firebird.indexDescending");
		if (direction != null && !java.util.Set.of("true", "false").contains(direction))
			throw new IllegalArgumentException("firebird.indexDescending must be true or false");
		FirebirdConstraintSql.index(constraint, "true".equals(direction), builder);
	}
}
