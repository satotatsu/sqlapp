/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.sql;

import java.util.List;
import com.sqlapp.data.db.dialect.phoenix.util.PhoenixSqlBuilder;
import com.sqlapp.data.db.sql.SimpleSqlFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Row;
import com.sqlapp.data.schemas.Column;

/** Deletes a row identified by a complete modeled primary key. */
public class PhoenixRowDeleteFactory extends SimpleSqlFactory<Row, PhoenixSqlBuilder> {
	static List<Column> requirePrimaryKey(Row row) {
		final var table = row.getTable();
		final var key = table == null ? null : table.getPrimaryKeyConstraint();
		if (key == null || key.getColumns().isEmpty())
			throw new IllegalArgumentException("Phoenix row migration requires a modeled primary key");
		return key.getColumns().stream().map(reference -> {
			final var column = table.getColumns().get(reference.getName());
			if (column == null || row.get(column) == null)
				throw new IllegalArgumentException(
						"Phoenix row migration requires a non-null primary-key value: " + reference.getName());
			return column;
		}).toList();
	}

	@Override
	public List<SqlOperation> createSql(Row row) {
		final var columns = requirePrimaryKey(row);
		final var builder = createSqlBuilder();
		builder.delete().from().name(row.getTable(), getOptions().isDecorateSchemaName()).where();
		boolean first = true;
		for (var column : columns) {
			builder.and(!first).name(column).eq()._add(getDialect().getSqlValueDefinition(column, row.get(column)));
			first = false;
		}
		return List.of(createOperation(builder.toString(), SqlType.DELETE, row, null));
	}
}
