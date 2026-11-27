/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.saphana.sql;

import java.util.List;

import com.sqlapp.data.db.dialect.saphana.util.SapHanaSqlBuilder;
import com.sqlapp.data.db.sql.AbstractAlterTableFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

/** HANA column DDL uses parenthesized column lists and standalone RENAME. */
public class SapHanaAlterTableFactory extends AbstractAlterTableFactory<SapHanaSqlBuilder> {

	@Override
	public List<SqlOperation> createDiffSql(final DbObjectDifference difference) {
		final Table original = difference.getOriginal(Table.class);
		final Table target = difference.getTarget(Table.class);
		if ((!original.getDefinition().isEmpty() || !target.getDefinition().isEmpty()
				|| "DEFINITION".equals(original.getSpecifics().get("PARTITIONING_REPRESENTATION"))
				|| "DEFINITION".equals(target.getSpecifics().get("PARTITIONING_REPRESENTATION")))
				&& !difference.getProperties().isEmpty())
			throw new IllegalArgumentException(
					"Definition-backed SAP HANA tables require explicit migration DDL: " + target.getName());
		for (var column : target.getColumns()) {
			var previous = original.getColumns().get(column.getName());
			if ((previous == null && column.getSpecifics().get("LOAD_UNIT") != null) || previous != null && !CommonUtils
					.eq(previous.getSpecifics().get("LOAD_UNIT"), column.getSpecifics().get("LOAD_UNIT")))
				throw new IllegalArgumentException(
						"SAP HANA column load unit changes require explicit migration DDL: " + column.getName());
		}
		if (!CommonUtils.eq(original.getPartitioning(), target.getPartitioning())
				|| !CommonUtils.eq(original.getSpecifics().get("LOAD_UNIT"), target.getSpecifics().get("LOAD_UNIT"))
				|| original.getTableDataStoreType() != target.getTableDataStoreType()) {
			throw new IllegalArgumentException(
					"SAP HANA partitioning/storage changes require explicit migration DDL: " + target.getName());
		}
		final boolean renamed = !CommonUtils.eq(original.getName(), target.getName());
		final boolean moved = !CommonUtils.eq(original.getSchemaName(), target.getSchemaName());
		if (moved && !getOptions().isDecorateSchemaName()) {
			throw new IllegalArgumentException(
					"SAP HANA schema moves require decorateSchemaName=true so subsequent operations address the target schema");
		}
		final List<SqlOperation> result = super.createDiffSql(difference);
		if (renamed || moved) {
			final SapHanaSqlBuilder builder = createSqlBuilder();
			builder.rename().table().name(original, moved || getOptions().isDecorateSchemaName()).to().name(target,
					moved || getOptions().isDecorateSchemaName());
			final SqlOperation operation = createOperation(builder.toString(), SqlType.ALTER, original, target);
			if (renamed) {
				// The shared planner emits the table rename first; retain its ordering.
				result.set(0, operation);
			} else {
				result.add(0, operation);
			}
		}
		return result;
	}

	@Override
	protected void addAddColumn(final Table originalTable, final Table table, final DbObjectDifference diff,
			final List<SqlOperation> result) {
		final Column column = diff.getTarget(Column.class);
		final SapHanaSqlBuilder builder = createSqlBuilder();
		addAlterTable(table, builder);
		builder.add().space()._add("(").name(column).space()
				.definition(column, getTableOptions().getWithColumnRemarks().test(column))._add(")");
		add(result, createOperation(builder.toString(), SqlType.ALTER, null, column));
	}

	@Override
	protected void addDeleteColumn(final Table originalTable, final Table table, final DbObjectDifference diff,
			final List<SqlOperation> result) {
		final Column column = diff.getOriginal(Column.class);
		final SapHanaSqlBuilder builder = createSqlBuilder();
		addAlterTable(table, builder);
		builder.drop().space()._add("(").name(column)._add(")");
		add(result, createOperation(builder.toString(), SqlType.ALTER, column));
	}

	@Override
	protected void addAlterColumn(final Table originalTable, final Table table, final Column oldColumn,
			final Column column, final DbObjectDifference diff, final List<SqlOperation> result) {
		if (oldColumn.isIdentity() != column.isIdentity()
				|| !CommonUtils.eq(oldColumn.getFormula(), column.getFormula())
				|| !CommonUtils.eq(oldColumn.getIdentityGenerationType(), column.getIdentityGenerationType())
				|| oldColumn.isIdentity() && !CommonUtils.eq(oldColumn.getSequence(), column.getSequence())) {
			throw new IllegalArgumentException(
					"SAP HANA identity/generated expression changes require explicit DDL: " + column.getName());
		}
		final SapHanaSqlBuilder builder = createSqlBuilder();
		addAlterTable(table, builder);
		// The shared planner performs alterations before renaming the column.
		builder.alter().space()._add("(").name(oldColumn).space().addTypeDefinition(column);
		if (!CommonUtils.eq(oldColumn.getDefaultValue(), column.getDefaultValue())) {
			builder.space().default_().space()
					._add(column.getDefaultValue() == null ? "NULL" : column.getDefaultValue());
		}
		if (oldColumn.isNotNull() != column.isNotNull()) {
			builder.space()._add(column.isNotNull() ? "NOT NULL" : "NULL");
		}
		builder._add(")");
		add(result, createOperation(builder.toString(), SqlType.ALTER, oldColumn, column));
	}

	@Override
	protected void addRenameColumn(final Table originalTable, final Table table, final Column oldColumn,
			final Column column, final DbObjectDifference diff, final List<SqlOperation> result) {
		final SapHanaSqlBuilder builder = createSqlBuilder();
		builder.rename().column().name(table, getOptions().isDecorateSchemaName())._add(".").name(oldColumn).to()
				.name(column);
		add(result, createOperation(builder.toString(), SqlType.ALTER, oldColumn, column));
	}
}
