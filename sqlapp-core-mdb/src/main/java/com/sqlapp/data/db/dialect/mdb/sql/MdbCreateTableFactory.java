/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mdb.
 */
package com.sqlapp.data.db.dialect.mdb.sql;

import com.sqlapp.data.db.dialect.mdb.util.MdbSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateTableFactory;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;

/** Generates Access table DDL, including inline AutoNumber primary keys. */
public class MdbCreateTableFactory
		extends AbstractCreateTableFactory<MdbSqlBuilder> {

	@Override
	protected void addColumnDefinition(final Column column,
			final MdbSqlBuilder builder) {
		super.addColumnDefinition(column, builder);
		if (isInlineAutoNumberPrimaryKey(column)) {
			builder.space().primaryKey();
		}
	}

	@Override
	protected void addUniqueConstraintDefinitions(final Table table,
			final MdbSqlBuilder builder) {
		for (final UniqueConstraint constraint : table.getConstraints()
				.getUniqueConstraints()) {
			if (constraint.isPrimaryKey() && constraint.getColumns().size() == 1
					&& constraint.getColumns().get(0).getColumn() != null
					&& constraint.getColumns().get(0).getColumn().isIdentity()) {
				continue;
			}
			addConstraintDefinition(constraint, builder);
		}
	}

	private boolean isInlineAutoNumberPrimaryKey(final Column column) {
		if (!column.isIdentity() || column.getTable() == null) {
			return false;
		}
		final UniqueConstraint primaryKey = column.getTable().getConstraints()
				.getPrimaryKeyConstraint();
		return primaryKey != null && primaryKey.getColumns().size() == 1
				&& primaryKey.getColumns().get(0).getColumn() == column;
	}
}
