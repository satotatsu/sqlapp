/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.cockroach.sql;

import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.db.sql.AbstractDropNamedObjectFactory;
import com.sqlapp.data.schemas.*;

public class CockroachDropConstraintFactory extends AbstractDropNamedObjectFactory<Constraint, PostgresSqlBuilder> {
	@Override
	protected void addDropObject(Constraint constraint, PostgresSqlBuilder builder) {
		if (constraint.getParent() == null || constraint.getParent().getTable() == null)
			throw new IllegalArgumentException("CockroachDB constraint DROP requires its owning table");
		if (constraint instanceof UniqueConstraint unique) {
			if (unique.isPrimaryKey())
				throw new UnsupportedOperationException(
						"CockroachDB primary-key removal requires an explicit migration");
			var index = unique.getIndex();
			if (index == null)
				index = new Index(unique.getName()).setTableName(unique.getParent().getTable().getName())
						.setSchemaName(unique.getSchemaName());
			builder.drop().index().space();
			CockroachDropIndexFactory.name(index, builder, getOptions().isDecorateSchemaName());
			return;
		}
		builder.alter().table().name(constraint.getParent().getTable(), getOptions().isDecorateSchemaName()).drop()
				.constraint().name(constraint, false);
	}
}
