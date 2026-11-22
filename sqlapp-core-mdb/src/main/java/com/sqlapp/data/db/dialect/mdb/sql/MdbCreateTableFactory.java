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
public class MdbCreateTableFactory extends AbstractCreateTableFactory<MdbSqlBuilder> {

	@Override
	protected void addColumnDefinition(final Column column, final MdbSqlBuilder builder) {
		super.addColumnDefinition(column, builder);
		if (isInlineAutoNumberPrimaryKey(column)) {
			builder.space().primaryKey();
		}
	}

	@Override
	protected void addUniqueConstraintDefinitions(final Table table, final MdbSqlBuilder builder) {
		for (final UniqueConstraint constraint : table.getConstraints().getUniqueConstraints()) {
			// Emitted inline by addColumnDefinition; both sides use the same predicate
			// so the primary key is never dropped or duplicated.
			if (constraint.isPrimaryKey() && inlineAutoNumberColumn(table) != null) {
				continue;
			}
			addConstraintDefinition(constraint, builder);
		}
	}

	private boolean isInlineAutoNumberPrimaryKey(final Column column) {
		return column.getTable() != null && inlineAutoNumberColumn(column.getTable()) == column;
	}

	/** The table column of a single-column AutoNumber primary key, if any. */
	private Column inlineAutoNumberColumn(final Table table) {
		final UniqueConstraint primaryKey = table.getConstraints().getPrimaryKeyConstraint();
		if (primaryKey == null || primaryKey.getColumns().size() != 1) {
			return null;
		}
		final Column column = table.getColumns().get(primaryKey.getColumns().get(0).getName());
		return column != null && column.isIdentity() ? column : null;
	}
}
