/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import java.util.List;
import java.util.Objects;
import java.util.function.Predicate;
import com.sqlapp.data.db.dialect.sqlite.util.SqliteSqlBuilder;
import com.sqlapp.data.db.sql.TableOptions;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.IdentityGenerationType;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.CommonUtils;

/** Shared modeled source resolution and INSERT SELECT rendering for SQLite. */
final class SqliteTableSource {
	private SqliteTableSource() {
	}

	static Table resolve(Table target, TableOptions options) {
		String name = options.getTempTableName().apply(target);
		if (CommonUtils.isEmpty(name))
			throw new IllegalArgumentException("SQLite table-source INSERT requires a non-empty tempTableName");
		Table source = target.getParent() == null ? null : target.getParent().get(name);
		if (source == null) {
			source = new Table(name);
			for (Column column : target.getColumns())
				source.getColumns().add(column.clone());
			// Never let SQLite's search order substitute a permanent table for temp.
			new Schema("temp").getTables().add(source);
		}
		return source;
	}

	static List<Column> columns(Table target, Table source, TableOptions options, Predicate<Column> insertable) {
		Objects.requireNonNull(target, "target");
		Objects.requireNonNull(source, "source");
		if (CommonUtils.isEmpty(source.getName()) || CommonUtils.isEmpty(source.getSchemaName())
				|| CommonUtils.isEmpty(target.getName()) || CommonUtils.isEmpty(target.getSchemaName()))
			throw new IllegalArgumentException(
					"SQLite table-source INSERT requires explicit source and target schema/table identities");
		if (target.getName().equalsIgnoreCase(source.getName())
				&& target.getSchemaName().equalsIgnoreCase(source.getSchemaName()))
			throw new IllegalArgumentException("SQLite table-source INSERT source and target must be different tables");
		var columns = target.getColumns().stream().filter(insertable).filter(c -> CommonUtils.isEmpty(c.getFormula()))
				.toList();
		if (columns.isEmpty())
			throw new IllegalArgumentException("SQLite table-source INSERT requires writable input columns");
		for (Column column : columns) {
			if (column.isIdentity() && column.getIdentityGenerationType() == IdentityGenerationType.Always)
				throw new IllegalArgumentException(
						"Identity column does not accept explicit staging values: " + column.getName());
			String expression = options.getInsertTableColumnValue().apply(column);
			if ((expression == null || expression.equals(column.getName()))
					&& !source.getColumns().contains(column.getName()))
				throw new IllegalArgumentException(
						"SQLite table-source INSERT source column is missing: " + column.getName());
		}
		return columns;
	}

	static void addInsertSelect(Table target, Table source, List<Column> columns, TableOptions options,
			SqliteSqlBuilder builder) {
		// Explicit identities are required even when general decoration is disabled.
		builder.insert().into().space().name(target, true).space().brackets(() -> {
			for (int i = 0; i < columns.size(); i++)
				builder.comma(i > 0).name(columns.get(i));
		});
		builder.lineBreak().select();
		for (int i = 0; i < columns.size(); i++) {
			var column = columns.get(i);
			builder.lineBreak().comma(i > 0);
			String expression = options.getInsertTableColumnValue().apply(column);
			Runnable value = () -> {
				if (expression != null && !expression.equals(column.getName()))
					builder._add(expression);
				else
					builder._add("_source_.").name(column);
			};
			if (column.getDefaultValue() == null)
				value.run();
			else
				builder.coalesce(() -> {
					value.run();
					builder.comma()._add(column.getDefaultValue());
				});
		}
		builder.lineBreak().from().name(source, true)._add(" AS _source_");
	}
}
