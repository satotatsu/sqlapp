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
public class SpannerCreateViewFactory extends AbstractCreateViewFactory<SpannerSqlBuilder> {

	public static final String SECURITY_TYPE = "SECURITY_TYPE";

	@Override
	protected void addCreateObject(final View view, final SpannerSqlBuilder builder) {
		if (!isEmpty(view.getDefinition())) {
			builder._add(view.getDefinition());
			return;
		}
		addStatementView(view, builder, false);
	}

	protected void addStatementView(final View view, final SpannerSqlBuilder builder, final boolean replace) {
		if (view.getName() == null || view.getName().isBlank() || CommonUtils.isEmpty(view.getStatement())
				|| String.join("\n", view.getStatement()).isBlank()) {
			throw new IllegalArgumentException("Cloud Spanner view requires a name and query statement");
		}
		final String securityType = securityType(view);
		builder.create();
		if (replace)
			builder.space()._add("OR REPLACE");
		builder.view().space().name(view, getOptions().isDecorateSchemaName()).space()._add("SQL SECURITY").space()
				._add(securityType).lineBreak().as().lineBreak()._add(view.getStatement());
	}

	protected String securityType(final View view) {
		final String value = view.getSpecifics().get(SECURITY_TYPE);
		if (value == null)
			return "INVOKER";
		if (!"DEFINER".equalsIgnoreCase(value) && !"INVOKER".equalsIgnoreCase(value)) {
			throw new IllegalArgumentException("Cloud Spanner " + SECURITY_TYPE + " must be INVOKER or DEFINER");
		}
		return value.toUpperCase(java.util.Locale.ROOT);
	}
}
