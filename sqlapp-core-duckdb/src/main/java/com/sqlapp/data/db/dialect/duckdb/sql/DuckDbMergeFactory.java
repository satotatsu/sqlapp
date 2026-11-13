/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.sql;

import java.util.List;
import java.util.Objects;

import com.sqlapp.data.db.sql.AbstractMergeFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlSignature;
import com.sqlapp.data.db.dialect.duckdb.util.DuckDbSqlBuilder;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

/**
 * MERGE INTO for DuckDB 1.4+. Older DuckDB releases have no MERGE statement.
 */
public class DuckDbMergeFactory extends AbstractMergeFactory<DuckDbSqlBuilder> {

	@Override
	public List<SqlOperation> createSql(final Table table) {
		if (!getDialect().supportsMerge()) {
			throw new IllegalArgumentException("DuckDB MERGE requires DuckDB 1.4 or later: " + table.getName());
		}
		return super.createSql(table);
	}

	/**
	 * DuckDB rejects qualified column names in {@code UPDATE SET}, so the target
	 * columns are written without the target alias.
	 */
	@Override
	protected void addMergeTableWhenMatched(final Table obj, final SqlSignature sqlSignature,
			final String targetTableAlias, final String sourceTableAlias, final DuckDbSqlBuilder builder) {
		final SqlSignature.ColumnsHolder columnsHolder = sqlSignature.getSelectedColumnsHolder();
		builder.lineBreak();
		addWhenMatched(obj, sqlSignature, targetTableAlias, sourceTableAlias, builder);
		builder.indent(() -> {
			builder.lineBreak();
			builder.then().update();
			builder.indent(() -> {
				int i = 0;
				for (final Column column : obj.getColumns()) {
					if (!isUpdateable(column) || columnsHolder.getKeyColumns().contains(column)) {
						continue;
					}
					if (columnsHolder.isUniqueKey() && sqlSignature.getPrimaryKey().getKeyColumns().contains(column)) {
						continue;
					}
					builder.lineBreak().set(i == 0).comma(i > 0);
					builder.name(column).eq();
					final String value = getTableOptions().getUpdateTableColumnValue().apply(column);
					if (value != null && !Objects.equals(value, column.getName())) {
						builder._add(value);
					} else {
						builder.name(sourceTableAlias + ".", column);
					}
					final String comment = getTableOptions().getUpdateColumnComment().apply(column);
					if (!CommonUtils.isEmpty(comment) && !CommonUtils.eqIgnoreCase(comment, column.getName())) {
						builder.space().addComment(comment);
					}
					i++;
				}
				addMergeTableWhenMatchedWhere(obj, sqlSignature, targetTableAlias, sourceTableAlias, builder);
			});
		});
		builder.lineBreak();
	}

	/**
	 * Generated columns are neither updated nor inserted, but the USING row must
	 * still provide a value for every source column.
	 */
	@Override
	protected String getValueDefinitionForMerge(final Column column) {
		if (!CommonUtils.isEmpty(column.getFormula())) {
			return "NULL";
		}
		return super.getValueDefinitionForMerge(column);
	}
}
