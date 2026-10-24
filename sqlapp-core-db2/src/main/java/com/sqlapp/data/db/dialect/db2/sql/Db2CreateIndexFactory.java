/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.db2.sql;

import com.sqlapp.data.db.sql.CreateIndexFactory;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;

/** Db2 LUW indexes, including non-key INCLUDE columns. */
public class Db2CreateIndexFactory extends CreateIndexFactory {
	@Override
	protected boolean createIndex(Index index) {
		Table table = index.getTable();
		if (table != null) {
			for (var constraint : table.getConstraints().getUniqueConstraints()) {
				if (constraint.isEnable() && java.util.Objects.equals(index.getName(), constraint.getIndex().getName()))
					return false;
			}
		}
		return super.createIndex(index);
	}

	@Override
	public void addObjectDetail(Index index, Table table, AbstractSqlBuilder<?> builder) {
		if (index.getIndexType() != null && index.getIndexType() != IndexType.BTree
				&& index.getIndexType() != IndexType.Clustered) {
			throw new IllegalArgumentException("Unsupported Db2 index type: " + index.getIndexType());
		}
		long keys = index.getColumns().stream().filter(c -> !c.isIncludedColumn()).count();
		if (keys == 0 || table == null) {
			throw new IllegalArgumentException("Db2 index requires a table and key columns: " + index.getName());
		}
		boolean includes = index.getColumns().stream().anyMatch(ReferenceColumn::isIncludedColumn);
		if (includes && !index.isUnique()) {
			throw new IllegalArgumentException("Db2 INCLUDE requires a unique index: " + index.getName());
		}
		builder.unique(index.isUnique()).index().space();
		String schema = index.getSpecifics().get("INDEX_SCHEMA");
		if (schema == null) {
			schema = index.getSchemaName();
		}
		if (schema != null && (getOptions().isDecorateSchemaName() || !schema.equals(table.getSchemaName()))) {
			builder.name(schema)._add(".");
		}
		builder.name(index.getName()).on().name(table,
				getOptions().isDecorateSchemaName() || "SESSION".equals(table.getSchemaName()));
		builder.space()._add("(");
		int count = 0;
		for (ReferenceColumn column : index.getColumns()) {
			if (!column.isIncludedColumn()) {
				builder.comma(count++ > 0);
				addColumn(column, builder);
			}
		}
		builder.space()._add(")");
		if (includes) {
			builder.space()._add("INCLUDE (");
			count = 0;
			for (ReferenceColumn column : index.getColumns()) {
				if (column.isIncludedColumn()) {
					builder.comma(count++ > 0).name(column);
				}
			}
			builder.space()._add(")");
		}
		if (index.getIndexType() == IndexType.Clustered) {
			builder.space()._add("CLUSTER");
		}
	}
}
