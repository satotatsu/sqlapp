/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-mdb.
 */
package com.sqlapp.data.db.dialect.mdb.sql;

import com.sqlapp.data.schemas.Column;

final class MdbParameterExpression {

	private MdbParameterExpression() {
	}

	static String of(final Column column, final String defaultValue) {
		final String expression = "get(" + column.getOrdinal() + ")";
		if (defaultValue == null) {
			return "/*" + expression + "*/1";
		}
		if (defaultValue.contains("(")) {
			return "/*" + expression + "*/''";
		}
		return "/*" + expression + "*/" + defaultValue;
	}
}
