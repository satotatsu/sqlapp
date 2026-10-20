/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import java.util.ArrayList;
import java.util.List;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Table;

/** SQLite conditional single-row INSERT SELECT without a dummy FROM table. */
public class SqliteInsertSelectNotExistsFactory extends SqliteInsertFactory {
	@Override
	protected SqlType getSqlType() {
		return SqlType.INSERT_SELECT_NOT_EXISTS;
	}

	@Override
	public List<SqlOperation> createSql(Table table) {
		var signature = createSqlSignature(table);
		try {
			signature.setColumnSelectionStrategy(
					getTableOptions().getInsertSelectNotExistsKeyColumnsMatchingStrategy().apply(table));
		} catch (com.sqlapp.exceptions.MissingWhereClauseKeyException e) {
			throw new IllegalArgumentException(
					"SQLite conditional INSERT requires selected matching columns: " + table.getName(), e);
		}
		var columns = writableColumns(table, signature);
		var keys = SqliteKeyColumns.resolve(table, signature);
		var writableNames = columns.stream().map(c -> c.getName()).collect(java.util.stream.Collectors.toSet());
		if (keys.isEmpty() || !keys.stream().allMatch(c -> writableNames.contains(c.getName())))
			throw new IllegalArgumentException(
					"SQLite conditional INSERT matching columns must be writable input columns: " + table.getName());
		var builder = createSqlBuilder();
		builder.insert().into().space().name(table, getOptions().isDecorateSchemaName()).space().brackets(() -> {
			for (int i = 0; i < columns.size(); i++)
				builder.comma(i > 0).name(columns.get(i));
		});
		builder.lineBreak().select();
		for (int i = 0; i < columns.size(); i++)
			builder.lineBreak().comma(i > 0)._add(getValueDefinitionForInsert(columns.get(i), signature));
		builder.lineBreak().where().not().exists().space().brackets(() -> {
			builder.select().space()._add("1").from().name(table, getOptions().isDecorateSchemaName());
			SqliteKeyColumns.addWhere(table, signature, builder, this::getValueDefinitionSimple,
					this::addWhereColumnComment);
		});
		var operations = new ArrayList<SqlOperation>();
		addSql(operations, builder, getSqlType(), table);
		return operations;
	}
}
