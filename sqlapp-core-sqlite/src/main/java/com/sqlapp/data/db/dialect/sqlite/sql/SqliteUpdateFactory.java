/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import com.sqlapp.data.db.sql.SqlSignature;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.sql.UpdateFactory;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;
import com.sqlapp.util.CommonUtils;

/**
 * SQLite UPDATE with canonical key matching and explicit empty-update errors.
 */
public class SqliteUpdateFactory extends UpdateFactory {
	@Override
	protected void addUpdateTable(Table table, SqlSignature signature, AbstractSqlBuilder<?> builder) {
		var keyNames = SqliteKeyColumns.resolve(table, signature).stream().map(Column::getName)
				.collect(Collectors.toSet());
		var assignments = new LinkedHashMap<Column, String>();
		for (Column column : table.getColumns()) {
			if (keyNames.contains(column.getName()) || column.isHidden() || !isUpdateable(column)
					|| !CommonUtils.isEmpty(column.getFormula()))
				continue;
			String value = getValueDefinitionForUpdate(column);
			if (value != null)
				assignments.put(column, value);
		}
		if (assignments.isEmpty())
			throw new IllegalArgumentException("SQLite UPDATE has no writable non-key columns: " + table.getName());
		builder.update().name(table, getOptions().isDecorateSchemaName());
		addTableComment(table, builder);
		builder.lineBreak().set();
		int i = 0;
		for (var entry : assignments.entrySet()) {
			builder.lineBreak().comma(i++ > 0).name(entry.getKey());
			addUpdateColumnComment(entry.getKey(), builder);
			builder.eq()._add(entry.getValue());
		}
		addKeyColumnsCondition(table, signature, builder);
		addUpdateAfter(table, signature, builder);
	}

	@Override
	protected void addKeyColumnsCondition(Table table, SqlSignature signature, AbstractSqlBuilder<?> builder) {
		SqliteKeyColumns.addWhere(table, signature, builder, this::getValueDefinitionSimple,
				this::addWhereColumnComment);
		addLockVersionColumnCondition(table, builder);
	}

	/** Preserves the shared explicit whole-table update condition. */
	public static class All extends SqliteUpdateFactory {
		@Override
		protected SqlType getSqlType() {
			return SqlType.UPDATE_TABLE;
		}

		@Override
		protected void addKeyColumnsCondition(Table table, SqlSignature signature, AbstractSqlBuilder<?> builder) {
			if (getTableOptions().getUpdateAllCondition() != null) {
				builder.lineBreak().where().true_();
				getTableOptions().getUpdateAllCondition().accept(table, builder);
			}
		}
	}

	/** Preserves the shared application-filter template. */
	public static class ForApp extends SqliteUpdateFactory {
		@Override
		protected SqlType getSqlType() {
			return SqlType.UPDATE_FOR_APP;
		}

		@Override
		protected void addKeyColumnsCondition(Table table, SqlSignature signature, AbstractSqlBuilder<?> builder) {
			builder.lineBreak().where().true_();
			addConditionColumns(table, builder);
		}
	}
}
