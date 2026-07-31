/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 */
package com.sqlapp.data.db.dialect.postgres.sql;

import com.sqlapp.data.db.sql.SqlSignature;
import com.sqlapp.data.db.sql.TableOptions;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

final class Postgres180ReturningSupport {
	private Postgres180ReturningSupport() {
	}

	static void add(Table table, SqlSignature signature, TableOptions options,
			AbstractSqlBuilder<?> builder) {
		String oldAlias = options.getReturningOldAlias().apply(table);
		String newAlias = options.getReturningNewAlias().apply(table);
		if (CommonUtils.isEmpty(oldAlias) && CommonUtils.isEmpty(newAlias)) {
			return;
		}
		if (!CommonUtils.isEmpty(oldAlias) && oldAlias.equalsIgnoreCase(newAlias)) {
			throw new IllegalArgumentException("OLD and NEW aliases must be different.");
		}
		var columns = options.getReturningColumnStrategy().apply(table)
				.getWithoutCheck(signature).getKeyColumns();
		if (columns.isEmpty()) {
			throw new IllegalArgumentException(
					"RETURNING requires at least one selected column.");
		}
		builder.lineBreak()._add("RETURNING WITH").space().brackets(() -> {
			boolean comma = false;
			if (!CommonUtils.isEmpty(oldAlias)) {
				builder._add("OLD AS").space().name(oldAlias);
				comma = true;
			}
			if (!CommonUtils.isEmpty(newAlias)) {
				builder.comma(comma)._add("NEW AS").space().name(newAlias);
			}
		}).space();
		int i = 0;
		for (var column : columns) {
			if (!CommonUtils.isEmpty(oldAlias)) {
				builder.comma(i > 0).name(oldAlias + ".", column).space().as().space()
						.name(oldAlias + "_" + column.getName());
				i++;
			}
			if (!CommonUtils.isEmpty(newAlias)) {
				builder.comma(i > 0).name(newAlias + ".", column).space().as().space()
						.name(newAlias + "_" + column.getName());
				i++;
			}
		}
	}
}
