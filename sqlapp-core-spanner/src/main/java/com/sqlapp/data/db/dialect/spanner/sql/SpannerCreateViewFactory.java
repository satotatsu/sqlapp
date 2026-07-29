/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-spanner.
 */
package com.sqlapp.data.db.dialect.spanner.sql;

import static com.sqlapp.util.CommonUtils.isEmpty;

import com.sqlapp.data.db.dialect.spanner.util.SpannerSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateViewFactory;
import com.sqlapp.data.schemas.View;
import com.sqlapp.util.CommonUtils;

/**
 * GoogleSQL Cloud Spanner CREATE VIEW.
 */
public class SpannerCreateViewFactory
		extends AbstractCreateViewFactory<SpannerSqlBuilder> {

	public static final String SECURITY_TYPE = "SECURITY_TYPE";

	@Override
	protected void addCreateObject(final View view,
			final SpannerSqlBuilder builder) {
		if (!isEmpty(view.getDefinition())) {
			builder._add(view.getDefinition());
			return;
		}
		if (CommonUtils.isEmpty(view.getStatement())) {
			return;
		}
		String securityType = view.getSpecifics().get(SECURITY_TYPE);
		if (!"DEFINER".equalsIgnoreCase(securityType)
				&& !"INVOKER".equalsIgnoreCase(securityType)) {
			securityType = "INVOKER";
		}
		builder.create().view().space()
				.name(view, getOptions().isDecorateSchemaName())
				.space()._add("SQL SECURITY").space()
				._add(securityType.toUpperCase())
				.lineBreak().as().lineBreak()
				._add(view.getStatement());
	}
}
