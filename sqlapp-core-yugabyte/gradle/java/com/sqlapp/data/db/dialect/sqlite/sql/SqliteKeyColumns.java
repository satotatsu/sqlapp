/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Function;
import com.sqlapp.data.db.sql.SqlSignature;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;

/** Resolves matching references against the canonical table model. */
final class SqliteKeyColumns {
	private SqliteKeyColumns() {
	}

	static List<Column> resolve(Table table, SqlSignature signature) {
		var holder = signature.getSelectedColumnsHolder();
		if (holder == null || holder.getKeyColumns().isEmpty())
			throw new IllegalArgumentException(
					"SQLite operation requires selected matching columns: " + table.getName());
		var columns = new ArrayList<Column>();
		for (var reference : holder.getKeyColumns()) {
			var column = table.getColumns().get(reference.getName());
			if (column == null)
				throw new IllegalArgumentException(
						"SQLite matching column is missing from the table model: " + reference.getName());
			columns.add(column);
		}
		return List.copyOf(columns);
	}

	static <S extends AbstractSqlBuilder<?>> void addWhere(Table table, SqlSignature signature, S builder,
			Function<Column, String> value, BiConsumer<Column, S> comment) {
		var columns = resolve(table, signature);
		builder.lineBreak().where().true_();
		for (Column column : columns) {
			builder.lineBreak().and().name(column);
			comment.accept(column, builder);
			builder.eq()._add(value.apply(column));
		}
		builder.lineBreak();
	}
}
