/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.sql;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

/**
 * Shared definition of the additional nullable columns supported by modeled
 * views.
 */
final class PhoenixViewColumns {
	private PhoenixViewColumns() {
	}

	static void appendDefinition(final Column column, final AbstractSqlBuilder<?> builder) {
		if (column.isNotNull() || !CommonUtils.isEmpty(column.getDefaultValue())
				|| !CommonUtils.isEmpty(column.getCheck()) || !CommonUtils.isEmpty(column.getOnUpdate())) {
			throw new UnsupportedOperationException("Phoenix declared view columns must be nullable and "
					+ "have no default, CHECK or ON UPDATE clause; use a separate native migration: "
					+ column.getName());
		}
		PhoenixCreateTableFactory.appendColumnName(column, builder);
		builder.space().definition(column, false);
	}
}
