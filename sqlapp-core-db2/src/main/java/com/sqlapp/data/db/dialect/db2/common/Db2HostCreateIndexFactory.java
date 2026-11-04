/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.common;

import com.sqlapp.data.db.sql.CreateIndexFactory;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * Ordinary named-column indexes; the index schema can differ from the table
 * schema.
 */
public class Db2HostCreateIndexFactory extends CreateIndexFactory {
	@Override
	protected boolean createIndex(Index index) {
		// Readers exclude constraint-owned indexes by catalog identity. An ordinary
		// index may legitimately have the same name as an unrelated constraint.
		return true;
	}

	@Override
	public void addObjectDetail(Index index, Table table, AbstractSqlBuilder<?> builder) {
		if (table == null || index.getColumns().isEmpty() || !index.getDefinition().isEmpty()
				|| !index.getIncludes().isEmpty() || (index.getWhere() != null && !index.getWhere().isBlank())
				|| index.getColumns().stream().anyMatch(c -> !c.getStatement().isEmpty()))
			throw new IllegalArgumentException(
					"Host index requires a table and ordinary named columns: " + index.getName());
		builder.unique(index.isUnique()).index().space();
		String schema = index.getSpecifics().getOrDefault("INDEX_SCHEMA", index.getSchemaName());
		if (schema != null)
			builder.name(schema)._add(".");
		builder.name(index.getName()).on().name(table, true).space()._add("(");
		int count = 0;
		for (var column : index.getColumns()) {
			builder.comma(count++ > 0);
			addColumn(column, builder);
		}
		builder._add(")");
	}
}
