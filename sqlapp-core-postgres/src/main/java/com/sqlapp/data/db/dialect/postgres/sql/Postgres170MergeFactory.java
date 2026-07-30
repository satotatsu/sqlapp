/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-postgres.
 */
package com.sqlapp.data.db.dialect.postgres.sql;

import com.sqlapp.data.db.dialect.postgres.util.PostgresSqlBuilder;
import com.sqlapp.data.db.sql.SqlSignature;
import com.sqlapp.data.schemas.Table;

/**
 * PostgreSQL 17 MERGE extensions.
 */
public class Postgres170MergeFactory extends Postgres150MergeFactory {

	@Override
	protected void addMergeTableWhenNotMatchedBySource(final Table table,
			final SqlSignature sqlSignature, final String targetTableAlias,
			final String sourceTableAlias, final PostgresSqlBuilder builder) {
		if (!getTableOptions().getMergeTableWithDelete().test(table)) {
			return;
		}
		builder.lineBreak().when().not().matched().space().by().space()._add("SOURCE");
		builder.indent(() -> builder.lineBreak().then().delete());
		builder.lineBreak();
	}

	@Override
	protected void addMergeTableAfter(final Table table, final SqlSignature sqlSignature,
			final String targetTableAlias, final String sourceTableAlias,
			final PostgresSqlBuilder builder) {
		if (!getTableOptions().getMergeTableWithReturning().test(table)) {
			return;
		}
		final var strategy = getTableOptions().getReturningColumnStrategy().apply(table);
		final var columns = strategy.getWithoutCheck(sqlSignature).getKeyColumns();
		if (columns.isEmpty()) {
			throw new IllegalArgumentException("MERGE RETURNING requires at least one selected column.");
		}
		builder.lineBreak()._add("RETURNING").space();
		int i = 0;
		for (final var column : columns) {
			builder.comma(i > 0).name(targetTableAlias + ".", column);
			i++;
		}
	}
}
