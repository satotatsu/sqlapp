/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-spanner.
 */
package com.sqlapp.data.db.dialect.spanner.sql;

import com.sqlapp.data.db.sql.AbstractCreateTableFactory;
import com.sqlapp.data.db.dialect.spanner.util.SpannerSqlBuilder;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;

/**
 * GoogleSQL Cloud Spanner CREATE TABLE.
 */
public class SpannerCreateTableFactory
		extends AbstractCreateTableFactory<SpannerSqlBuilder> {

	@Override
	protected void addCreateObject(final Table table,
			final SpannerSqlBuilder builder) {
		builder.create().table()
				.ifNotExists(getOptions().isCreateIfNotExists()).space()
				.name(table, getOptions().isDecorateSchemaName());
	}

	@Override
	protected void addUniqueConstraintDefinitions(final Table table,
			final SpannerSqlBuilder builder) {
		for (UniqueConstraint constraint
				: table.getConstraints().getUniqueConstraints()) {
			if (!constraint.isPrimaryKey()) {
				addConstraintDefinition(constraint, builder);
			}
		}
	}

	@Override
	protected void addOption(final Table table,
			final SpannerSqlBuilder builder) {
		final UniqueConstraint primaryKey = findPrimaryKey(table);
		builder.space()._add("PRIMARY KEY").space()._add("(");
		if (primaryKey != null) {
			int i = 0;
			for (ReferenceColumn column : primaryKey.getColumns()) {
				builder.comma(i > 0).name(column);
				if (column.getOrder() != null) {
					builder.space()._add(column.getOrder());
				}
				i++;
			}
		}
		builder.space()._add(")");
	}

	private UniqueConstraint findPrimaryKey(final Table table) {
		for (UniqueConstraint constraint
				: table.getConstraints().getUniqueConstraints()) {
			if (constraint.isPrimaryKey()) {
				return constraint;
			}
		}
		return null;
	}
}
