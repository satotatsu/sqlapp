/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.sql;

import com.sqlapp.data.db.sql.AbstractCreateUniqueConstraintFactory;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.util.AbstractSqlBuilder;

/** Generates Informix constraint names after the constraint definition. */
public class InformixCreateUniqueConstraintFactory
		extends AbstractCreateUniqueConstraintFactory<AbstractSqlBuilder<?>> {
	@Override
	public void addObjectDetail(final UniqueConstraint obj, final Table table, final AbstractSqlBuilder<?> builder) {
		if (obj.isPrimaryKey()) {
			builder.primaryKey();
		} else {
			builder.unique();
		}
		addOption(obj, builder);
		builder.space()._add('(');
		int i = 0;
		for (final ReferenceColumn column : obj.getColumns()) {
			builder.comma(i > 0).name(column);
			if (column.getOrder() != null && column.getOrder() != Order.Asc) {
				builder.space()._add(column.getOrder());
			}
			i++;
		}
		builder.space()._add(')');
		InformixConstraintNames.append(obj, builder, getOptions().isDecorateSchemaName());
		addDeferrability(obj, builder);
		addAfter(obj, builder);
		final String mode = com.sqlapp.data.db.dialect.informix.util.InformixConstraintModes.sql(obj);
		if (mode != null)
			builder.space()._add(mode);
	}
}
