/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-oracle.
 */
package com.sqlapp.data.db.dialect.oracle.sql;

import static com.sqlapp.util.CommonUtils.isEmpty;

import com.sqlapp.data.db.sql.CreateViewFactory;
import com.sqlapp.data.schemas.View;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * Oracle Database 23ai CREATE VIEW extensions.
 */
public class Oracle23aiCreateViewFactory extends CreateViewFactory {

	@Override
	protected void addCreateObject(final View view,
			final AbstractSqlBuilder<?> builder) {
		if (!OracleJsonDualityViewUtils.isJsonRelationalDualityView(view)) {
			super.addCreateObject(view, builder);
			return;
		}
		if (isEmpty(view.getStatement())) {
			throw new IllegalArgumentException(
					"JSON relational duality view statement must not be empty: "
					+ view.getName());
		}
		builder.create().space()._add("JSON RELATIONAL DUALITY VIEW").space()
				.ifNotExists(getOptions().isCreateIfNotExists()).space()
				.name(view, getOptions().isDecorateSchemaName())
				.lineBreak().as().lineBreak()._add(view.getStatement());
	}
}
