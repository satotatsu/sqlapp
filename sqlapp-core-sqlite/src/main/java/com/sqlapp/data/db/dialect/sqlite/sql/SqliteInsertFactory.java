/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import java.util.List;
import com.sqlapp.data.db.sql.AbstractInsertFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlSignature;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.db.dialect.sqlite.util.SqliteSqlBuilder;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.IdentityGenerationType;
import com.sqlapp.data.schemas.Table;

/** SQLite INSERT, including tables with only generated/defaulted columns. */
public class SqliteInsertFactory extends AbstractInsertFactory<SqliteSqlBuilder> {
	protected List<Column> writableColumns(Table table, SqlSignature signature) {
		return table.getColumns().stream().filter(this::isInsertable)
				.filter(c -> com.sqlapp.util.CommonUtils.isEmpty(c.getFormula())).filter(column -> {
					if (!isAutoIncrementColumn(column))
						return true;
					if (signature.hasNullValue(column) && signature.hasNonNullValue(column))
						throw new IllegalArgumentException(
								"SQLite INSERT cannot mix generated and explicit identity values: " + column.getName());
					if (!signature.hasNonNullValue(column))
						return false;
					if (column.getIdentityGenerationType() == IdentityGenerationType.Always)
						throw new IllegalArgumentException(
								"Identity column does not accept explicit values: " + column.getName());
					return true;
				}).toList();
	}

	@Override
	public List<SqlOperation> createSql(Table table) {
		var signature = createSqlSignature(table);
		var columns = writableColumns(table, signature);
		var builder = createSqlBuilder();
		builder.insert().into().space().name(table, getOptions().isDecorateSchemaName());
		addTableComment(table, builder);
		if (columns.isEmpty()) {
			if (getSqlType() != SqlType.INSERT)
				throw new IllegalArgumentException(
						"SQLite batch INSERT and MERGE require at least one writable input column; use INSERT for DEFAULT VALUES");
			builder.space()._add("DEFAULT VALUES");
		} else {
			builder.space().brackets(() -> {
				for (int i = 0; i < columns.size(); i++) {
					var column = columns.get(i);
					builder.lineBreak().comma(i > 0).name(column);
					String comment = getTableOptions().getInsertColumnComment().apply(column);
					if (!com.sqlapp.util.CommonUtils.isEmpty(comment) && !comment.equalsIgnoreCase(column.getName()))
						builder.space().addComment(comment);
				}
			});
			addValues(builder);
			builder.space().brackets(() -> {
				for (int i = 0; i < columns.size(); i++)
					builder.lineBreak().comma(i > 0)._add(getValueDefinitionForInsert(columns.get(i), signature));
				builder.lineBreak();
			});
		}
		addInsertAfter(table, builder);
		var operations = new java.util.ArrayList<SqlOperation>();
		addSql(operations, builder, getSqlType(), table);
		return operations;
	}

	/** Retains the shared VALUES expansion markers used by INSERT_ROWS. */
	public static class Rows extends SqliteInsertFactory {
		@Override
		protected SqlType getSqlType() {
			return SqlType.INSERT_ROWS;
		}

		@Override
		protected void addValues(SqliteSqlBuilder builder) {
			builder.lineBreak()._add("/*VALUES*/").values();
		}

		@Override
		protected void addInsertAfter(Table table, SqliteSqlBuilder builder) {
			builder._add("/*END*/");
		}
	}
}
