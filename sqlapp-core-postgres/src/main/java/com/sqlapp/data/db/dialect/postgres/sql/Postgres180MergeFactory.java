/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 */
package com.sqlapp.data.db.dialect.postgres.sql;

import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.db.sql.SqlSignature;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

/**
 * PostgreSQL 18 MERGE RETURNING old/new row aliases.
 */
public class Postgres180MergeFactory extends Postgres170MergeFactory {

	@Override
	protected void addMergeTableAfter(final Table table, final SqlSignature sqlSignature,
			final String targetTableAlias, final String sourceTableAlias,
			final PostgresSqlBuilder builder) {
		final String oldAlias = getTableOptions().getReturningOldAlias().apply(table);
		final String newAlias = getTableOptions().getReturningNewAlias().apply(table);
		if (CommonUtils.isEmpty(oldAlias) && CommonUtils.isEmpty(newAlias)) {
			super.addMergeTableAfter(table, sqlSignature, targetTableAlias, sourceTableAlias, builder);
			return;
		}
		if (!getTableOptions().getMergeTableWithReturning().test(table)) {
			return;
		}
		if (!CommonUtils.isEmpty(oldAlias) && oldAlias.equalsIgnoreCase(newAlias)) {
			throw new IllegalArgumentException("OLD and NEW aliases must be different.");
		}
		final var columns = getTableOptions().getReturningColumnStrategy().apply(table)
				.getWithoutCheck(sqlSignature).getKeyColumns();
		if (columns.isEmpty()) {
			throw new IllegalArgumentException("MERGE RETURNING requires at least one selected column.");
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
		for (final var column : columns) {
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
