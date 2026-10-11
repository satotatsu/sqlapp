/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import com.sqlapp.data.db.dialect.sqlite.util.SqliteSqlBuilder;
import com.sqlapp.data.db.sql.AbstractCreateIndexFactory;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Table;

/** SQLite qualifies the index name, while ON uses an unqualified table name. */
public class SqliteCreateIndexFactory extends AbstractCreateIndexFactory<SqliteSqlBuilder> {
	@Override
	protected boolean createIndex(Index index) {
		return !index.getName().toLowerCase(java.util.Locale.ROOT).startsWith("sqlite_autoindex_")
				&& super.createIndex(index);
	}

	@Override
	public void addObjectDetail(Index index, Table table, SqliteSqlBuilder builder) {
		addUnique(index, table, builder);
		boolean qualify = getOptions().isDecorateSchemaName()
				|| index.getSchemaName() != null && !index.getSchemaName().equalsIgnoreCase("main");
		builder.name(index, qualify).on().name(table, false).space()._add("(");
		String keys = index.getSpecifics().get("sqlite.index.keys");
		if (keys != null && !keys.isBlank()) {
			builder._add(keys);
		} else {
			int position = 0;
			for (var column : index.getColumns()) {
				builder.comma(position++ > 0);
				addColumn(column, builder);
			}
		}
		builder._add(")");
		if (index.getWhere() != null && !index.getWhere().isBlank()) builder.where().space()._add(index.getWhere());
	}
}
