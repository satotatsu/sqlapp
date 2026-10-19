/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import com.sqlapp.data.db.dialect.sqlite.Sqlite;
import com.sqlapp.data.db.dialect.sqlite.util.SqliteSqlBuilder;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;

/** SQLite 3.24+ implementation of single-row MERGE using ON CONFLICT. */
public class SqliteMergeFactory extends SqliteInsertFactory {
	@Override
	protected SqlType getSqlType() {
		return SqlType.MERGE;
	}

	@Override
	public List<SqlOperation> createSql(Table table) {
		if (!(getDialect() instanceof Sqlite sqlite) || !sqlite.supportsUpsert())
			throw new IllegalArgumentException("SQLite MERGE requires UPSERT support (SQLite 3.24 or later)");
		if (getTableOptions().getMergeRowsWithDelete().test(table)
				|| getTableOptions().getMergeTableWithDelete().test(table))
			throw new IllegalArgumentException("SQLite UPSERT cannot delete rows absent from the source");
		if (getTableOptions().getMergeTableWithReturning().test(table))
			throw new IllegalArgumentException(
					"SQLite MERGE RETURNING is not implemented; use the INSERT RETURNING API");
		return super.createSql(table);
	}

	private Set<String> names(java.util.Collection<? extends Column> columns) {
		return columns.stream().map(Column::getName).collect(Collectors.toSet());
	}

	@Override
	protected void addInsertAfter(Table table, SqliteSqlBuilder builder) {
		var signature = createSqlSignature(table);
		try {
			signature.setColumnSelectionStrategy(getTableOptions().getMergeKeyColumnsMatchingStrategy().apply(table));
		} catch (com.sqlapp.exceptions.MissingWhereClauseKeyException e) {
			throw new IllegalArgumentException(
					"SQLite MERGE requires a modeled primary or unique key: " + table.getName(), e);
		}
		var keys = signature.getSelectedColumnsHolder().getKeyColumns();
		var keyNames = names(keys);
		var constraint = table.getConstraints().stream()
				.filter(com.sqlapp.data.schemas.UniqueConstraint.class::isInstance)
				.map(com.sqlapp.data.schemas.UniqueConstraint.class::cast)
				.filter(key -> key.getColumns().size() == keys.size()
						&& key.getColumns().stream().map(c -> c.getName()).collect(Collectors.toSet()).equals(keyNames))
				.findFirst().orElse(null);
		// Expression and partial indexes cannot be represented by a plain column
		// target.
		var index = table.getIndexes().stream().filter(candidate -> candidate.isUnique()
				&& com.sqlapp.util.CommonUtils.isEmpty(candidate.getWhere())
				&& (candidate.getSpecifics().get("sqlite.index.keys") == null
						|| !com.sqlapp.data.db.dialect.sqlite.util.SqliteDdl
								.hasIndexExpressions(candidate.getSpecifics().get("sqlite.index.keys")))
				&& candidate.getColumns().size() == keys.size()
				&& candidate.getColumns().stream().map(c -> c.getName()).collect(Collectors.toSet()).equals(keyNames))
				.findFirst().orElse(null);
		if (keys.isEmpty() || constraint == null && index == null)
			throw new IllegalArgumentException(
					"SQLite MERGE requires a modeled primary or unique key matching the selected columns: "
							+ table.getName());
		var columns = writableColumns(table, signature);
		if (!names(columns).containsAll(keyNames))
			throw new IllegalArgumentException(
					"SQLite MERGE key columns must be writable input columns: " + table.getName());
		builder.lineBreak()._add("ON CONFLICT ").brackets(() -> {
			if (constraint == null && index.getSpecifics().get("sqlite.index.keys") != null) {
				builder._add(index.getSpecifics().get("sqlite.index.keys"));
				return;
			}
			int i = 0;
			for (var column : constraint == null ? index.getColumns() : constraint.getColumns()) {
				builder.comma(i > 0).name(column);
				String collation = constraint == null ? null
						: constraint.getSpecifics().get("sqlite.key.collation." + i);
				if (collation != null)
					builder._add(" COLLATE ").name(collation);
				i++;
			}
		});
		var primary = table.getPrimaryKeyConstraint();
		var primaryNames = primary == null ? Set.<String>of()
				: primary.getColumns().stream().map(c -> c.getName()).collect(Collectors.toSet());
		var updates = columns.stream().filter(this::isUpdateable).filter(c -> !c.isIdentity())
				.filter(c -> !primaryNames.contains(c.getName())).filter(c -> !keyNames.contains(c.getName())).toList();
		if (updates.isEmpty()) {
			builder._add(" DO NOTHING");
			return;
		}
		builder._add(" DO UPDATE SET ");
		for (int i = 0; i < updates.size(); i++) {
			var column = updates.get(i);
			builder.comma(i > 0).name(column).eq();
			var value = getTableOptions().getUpdateTableColumnValue().apply(column);
			if (value != null && !value.equals(column.getName()))
				builder._add(value);
			else
				builder._add("excluded.").name(column);
		}
	}

	/**
	 * Retains shared VALUES expansion while appending one conflict clause per
	 * statement.
	 */
	public static class Rows extends SqliteMergeFactory {
		@Override
		protected SqlType getSqlType() {
			return SqlType.MERGE_ROWS;
		}

		@Override
		protected void addValues(SqliteSqlBuilder builder) {
			builder.lineBreak()._add("/*VALUES*/").values();
		}

		@Override
		protected void addInsertAfter(Table table, SqliteSqlBuilder builder) {
			builder._add("/*END*/");
			super.addInsertAfter(table, builder);
		}
	}
}
