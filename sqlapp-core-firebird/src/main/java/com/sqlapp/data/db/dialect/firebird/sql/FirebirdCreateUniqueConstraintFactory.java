/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.firebird.sql;

import com.sqlapp.data.db.dialect.firebird.util.FirebirdSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateUniqueConstraintFactory;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;

/**
 * Firebird applies direction to the backing index, not individual constraint
 * columns.
 */
public class FirebirdCreateUniqueConstraintFactory extends AbstractCreateUniqueConstraintFactory<FirebirdSqlBuilder> {
	@Override
	public void addCreateObject(UniqueConstraint constraint, FirebirdSqlBuilder builder) {
		if (constraint.getTable() == null)
			throw new IllegalArgumentException("Firebird primary/unique constraint requires an owning table");
		super.addCreateObject(constraint, builder);
	}

	@Override
	public void addObjectDetail(UniqueConstraint constraint, Table table, FirebirdSqlBuilder builder) {
		if (!constraint.isEnable())
			throw new IllegalArgumentException("Firebird primary/unique constraints cannot be disabled");
		if (constraint.getColumns().isEmpty())
			throw new IllegalArgumentException("Firebird primary/unique constraint requires columns");
		boolean descending = constraint.getColumns().get(0).getOrder() == Order.Desc;
		builder.constraint().space().name(constraint, false);
		if (constraint.isPrimaryKey())
			builder.primaryKey();
		else
			builder.unique();
		builder._add(" (");
		for (int i = 0; i < constraint.getColumns().size(); i++) {
			var column = constraint.getColumns().get(i);
			if ((column.getOrder() == Order.Desc) != descending || column.getNullsOrder() != null)
				throw new IllegalArgumentException("Firebird constraint indexes require one direction for all columns");
			builder.comma(i > 0).name(column);
		}
		builder._add(")");
		FirebirdConstraintSql.index(constraint, descending, builder);
	}
}
