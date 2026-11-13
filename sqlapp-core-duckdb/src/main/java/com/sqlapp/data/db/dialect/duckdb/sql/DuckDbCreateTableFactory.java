/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.sql;

import java.util.List;

import com.sqlapp.data.db.sql.AbstractCreateTableFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.dialect.duckdb.util.DuckDbSqlBuilder;
import com.sqlapp.data.schemas.CascadeRule;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.ForeignKeyConstraint;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

/**
 * CREATE TABLE for DuckDB. Table and column comments are emitted as separate
 * {@code COMMENT ON} statements.
 */
public class DuckDbCreateTableFactory extends AbstractCreateTableFactory<DuckDbSqlBuilder> {

	@Override
	public List<SqlOperation> createSql(final Table table) {
		validate(table);
		return super.createSql(table);
	}

	/**
	 * Rejects table definitions that DuckDB cannot create.
	 *
	 * @param table table
	 */
	protected void validate(final Table table) {
		for (final ForeignKeyConstraint constraint : table.getConstraints().getForeignKeyConstraints()) {
			if (!isNoAction(constraint.getDeleteRule()) || !isNoAction(constraint.getUpdateRule())) {
				throw new IllegalArgumentException(
						"DuckDB foreign keys support neither CASCADE, SET NULL nor SET DEFAULT: "
								+ constraint.getName());
			}
		}
	}

	private static boolean isNoAction(final CascadeRule rule) {
		return rule == null || rule == CascadeRule.None || rule == CascadeRule.Restrict;
	}

	@Override
	protected void addCreateObject(final Table table, final DuckDbSqlBuilder builder) {
		builder.create();
		if (table.getTableType() == Table.TableType.Temporary) {
			builder.space()._add("TEMPORARY");
		}
		builder.table().ifNotExists(getOptions().isCreateIfNotExists()).space().name(table,
				getOptions().isDecorateSchemaName());
	}

	@Override
	protected void addOtherDefinitions(final Table table, final List<SqlOperation> result) {
		if (!CommonUtils.isEmpty(table.getRemarks())) {
			final DuckDbSqlBuilder builder = createSqlBuilder();
			builder.comment().on().table().space().name(table, getOptions().isDecorateSchemaName()).space().is()
					.space().sqlChar(table.getRemarks());
			add(result, createOperation(builder.toString(), SqlType.SET_COMMENT, table));
		}
		for (final Column column : table.getColumns()) {
			if (CommonUtils.isEmpty(column.getRemarks())
					|| !getTableOptions().getWithColumnRemarks().test(column)) {
				continue;
			}
			final DuckDbSqlBuilder builder = createSqlBuilder();
			builder.comment().on().column().space().name(table, getOptions().isDecorateSchemaName())._add(".")
					.name(column).space().is().space().sqlChar(column.getRemarks());
			add(result, createOperation(builder.toString(), SqlType.SET_COMMENT, column));
		}
	}
}
