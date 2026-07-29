/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-spanner.
 */
package com.sqlapp.data.db.dialect.spanner.sql;

import com.sqlapp.data.db.dialect.spanner.util.SpannerSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateIndexFactory;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.Table;

/**
 * GoogleSQL Cloud Spanner CREATE INDEX.
 */
public class SpannerCreateIndexFactory
		extends AbstractCreateIndexFactory<SpannerSqlBuilder> {

	public static final String IS_NULL_FILTERED = "IS_NULL_FILTERED";

	@Override
	public void addObjectDetail(final Index index, final Table table,
			final SpannerSqlBuilder builder) {
		builder.unique(index.isUnique());
		if (Boolean.TRUE.equals(index.getSpecifics().get(IS_NULL_FILTERED,
				Boolean.class))) {
			if (index.isUnique()) {
				builder.space();
			}
			builder._add("NULL_FILTERED").space();
		}
		builder.index()
				.ifNotExists(table != null
						&& getOptions().isCreateIfNotExists())
				.space().name(index, false);
		if (table != null) {
			builder.on().name(table,
					getOptions().isDecorateSchemaName());
		}
		builder.space()._add("(");
		int i = 0;
		for (ReferenceColumn column : index.getColumns()) {
			builder.comma(i > 0).name(column);
			if (column.getOrder() != null
					&& column.getOrder() != Order.Asc) {
				builder.space()._add(column.getOrder());
			}
			i++;
		}
		builder.space()._add(")");
		if (!index.getIncludes().isEmpty()) {
			builder.space()._add("STORING").space()._add("(");
			i = 0;
			for (ReferenceColumn column : index.getIncludes()) {
				builder.comma(i > 0).name(column);
				i++;
			}
			builder.space()._add(")");
		}
	}
}
