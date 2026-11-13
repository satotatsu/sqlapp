/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-core-duckdb.
 */
package com.sqlapp.data.db.dialect.duckdb.sql;

import java.util.List;
import java.util.Objects;

import com.sqlapp.data.db.dialect.duckdb.DuckDb;
import com.sqlapp.data.db.dialect.duckdb.util.DuckDbSqlBuilder;
import com.sqlapp.data.db.sql.AbstractAlterTableFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Constraint;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.UniqueConstraint;
import com.sqlapp.util.CommonUtils;

/**
 * ALTER TABLE for DuckDB.
 * <p>
 * DuckDB changes one column property per statement, cannot add a column with
 * constraints or a generation expression, cannot drop constraints and can only
 * add a primary key (DuckDB 1.2+) to an existing table. Unsupported changes
 * fail with an {@link IllegalArgumentException} instead of producing SQL that
 * DuckDB rejects; recreate the table for those changes.
 * </p>
 */
public class DuckDbAlterTableFactory extends AbstractAlterTableFactory<DuckDbSqlBuilder> {

	@Override
	protected void addAddColumn(final Table originalTable, final Table table, final DbObjectDifference diff,
			final List<SqlOperation> result) {
		final Column column = diff.getTarget(Column.class);
		if (!CommonUtils.isEmpty(column.getFormula())) {
			throw new IllegalArgumentException(
					"DuckDB cannot add a generated column to an existing table: " + column.getName());
		}
		if (!CommonUtils.isEmpty(column.getCheck())) {
			throw new IllegalArgumentException(
					"DuckDB cannot add a column with a CHECK constraint to an existing table: " + column.getName());
		}
		if (column.isIdentity()) {
			throw new IllegalArgumentException("DuckDB has no identity columns: " + column.getName());
		}
		final DuckDbSqlBuilder builder = createSqlBuilder();
		addAlterTable(table, builder);
		builder.add().column().space().name(column).space().typeWithCollation(column);
		if (!CommonUtils.isEmpty(column.getDefaultValue())) {
			builder.space().default_().space()._add(column.getDefaultValue());
		}
		add(result, createOperation(builder.toString(), SqlType.ALTER, null, column));
		if (column.isNotNull()) {
			add(result, alterColumn(table, column, "SET NOT NULL", column));
		}
		if (!CommonUtils.isEmpty(column.getRemarks()) && getTableOptions().getWithColumnRemarks().test(column)) {
			add(result, commentOnColumn(table, column));
		}
	}

	@Override
	protected void addAlterColumn(final Table originalTable, final Table table, final Column oldColumn,
			final Column column, final DbObjectDifference diff, final List<SqlOperation> result) {
		if (!Objects.equals(emptyToNull(oldColumn.getFormula()), emptyToNull(column.getFormula()))) {
			throw new IllegalArgumentException(
					"DuckDB cannot change a generation expression; recreate the table: " + column.getName());
		}
		if (column.isIdentity()) {
			throw new IllegalArgumentException("DuckDB has no identity columns: " + column.getName());
		}
		final String oldType = createSqlBuilder().typeWithCollation(oldColumn).toString();
		final String newType = createSqlBuilder().typeWithCollation(column).toString();
		if (!oldType.equals(newType)) {
			add(result, alterColumn(table, oldColumn, "TYPE " + newType, column));
		}
		if (CommonUtils.isEmpty(column.getFormula())
				&& !Objects.equals(emptyToNull(oldColumn.getDefaultValue()), emptyToNull(column.getDefaultValue()))) {
			add(result, alterColumn(table, oldColumn, CommonUtils.isEmpty(column.getDefaultValue()) ? "DROP DEFAULT"
					: "SET DEFAULT " + column.getDefaultValue(), column));
		}
		if (oldColumn.isNotNull() != column.isNotNull()) {
			add(result, alterColumn(table, oldColumn, column.isNotNull() ? "SET NOT NULL" : "DROP NOT NULL", column));
		}
		if (!Objects.equals(emptyToNull(oldColumn.getRemarks()), emptyToNull(column.getRemarks()))) {
			add(result, commentOnColumn(table, column));
		}
	}

	private SqlOperation alterColumn(final Table table, final Column oldColumn, final String action,
			final Column column) {
		final DuckDbSqlBuilder builder = createSqlBuilder();
		addAlterTable(table, builder);
		builder.alterColumn().space().name(oldColumn).space()._add(action);
		return createOperation(builder.toString(), SqlType.ALTER, oldColumn, column);
	}

	private SqlOperation commentOnColumn(final Table table, final Column column) {
		final DuckDbSqlBuilder builder = createSqlBuilder();
		builder.comment().on().column().space().name(table, getOptions().isDecorateSchemaName())._add(".")
				.name(column).space().is().space();
		if (CommonUtils.isEmpty(column.getRemarks())) {
			builder.null_();
		} else {
			builder.sqlChar(column.getRemarks());
		}
		return createOperation(builder.toString(), SqlType.SET_COMMENT, column);
	}

	private static String emptyToNull(final String value) {
		return CommonUtils.isEmpty(value) ? null : value;
	}

	@Override
	protected void addCreateConstraintDefinition(final Table originalTable, final Table table,
			final Constraint originalConstraint, final Constraint constraint, final DbObjectDifference diff,
			final List<SqlOperation> result) {
		if (!(constraint instanceof UniqueConstraint unique) || !unique.isPrimaryKey()
				|| !(getDialect() instanceof DuckDb duckDb) || !duckDb.supportsAlterTableAddPrimaryKey()) {
			throw new IllegalArgumentException("DuckDB cannot add this constraint to an existing table"
					+ " (only PRIMARY KEY on DuckDB 1.2+); recreate the table: " + constraint.getName());
		}
		final DuckDbSqlBuilder builder = createSqlBuilder();
		addAlterTable(table, builder);
		builder.add().space().primaryKey().space()._add("(");
		int i = 0;
		for (final ReferenceColumn column : unique.getColumns()) {
			builder.comma(i++ > 0).name(column);
		}
		builder._add(")");
		add(result, createOperation(builder.toString(), SqlType.CREATE, constraint));
	}

	@Override
	protected void addDropConstraintDefinition(final Table originalTable, final Table table,
			final Constraint originalConstraint, final Constraint constraint, final DbObjectDifference diff,
			final List<SqlOperation> result) {
		throw new IllegalArgumentException(
				"DuckDB cannot drop constraints from an existing table; recreate the table: "
						+ originalConstraint.getName());
	}

	@Override
	protected void addDropIndexDefinition(final Index obj, final DuckDbSqlBuilder builder) {
		builder.drop().index().space().name(obj, getOptions().isDecorateSchemaName());
	}
}
