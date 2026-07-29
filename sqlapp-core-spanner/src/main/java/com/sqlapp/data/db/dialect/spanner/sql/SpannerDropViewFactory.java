/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-spanner.
 */
package com.sqlapp.data.db.dialect.spanner.sql;

import com.sqlapp.data.db.dialect.spanner.util.SpannerSqlBuilder;
import com.sqlapp.data.db.sql.AbstractDropViewFactory;
import com.sqlapp.data.schemas.View;

/**
 * GoogleSQL Cloud Spanner DROP VIEW.
 */
public class SpannerDropViewFactory
		extends AbstractDropViewFactory<SpannerSqlBuilder> {

	@Override
	protected void addDropObject(final View view,
			final SpannerSqlBuilder builder) {
		builder.drop().view()
				.ifExists(getOptions().isDropIfExists())
				.name(view, getOptions().isDecorateSchemaName());
	}
}
