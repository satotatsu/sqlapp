/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.sql;

import com.sqlapp.data.db.sql.AbstractCreateIndexFactory;
import com.sqlapp.data.db.dialect.phoenix.util.PhoenixSqlBuilder;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.ReferenceColumn;
import com.sqlapp.data.schemas.Order;
import com.sqlapp.data.schemas.IndexType;

public class PhoenixCreateIndexFactory extends AbstractCreateIndexFactory<PhoenixSqlBuilder> {
	@Override
	protected boolean createIndex(Index index) {
		validate(index, index.getTable());
		return super.createIndex(index);
	}

	private void validate(Index index, Table table) {
		if (table == null || index.getName() == null || index.getName().isBlank() || index.getColumns().isEmpty())
			throw new IllegalArgumentException(
					"Phoenix CREATE INDEX requires a name, parent table and key columns: " + index.getName());
		if ((index.getIndexType() != null && index.getIndexType() != IndexType.BTree)
				|| (index.getWhere() != null && !index.getWhere().isBlank()))
			throw new UnsupportedOperationException(
					"Phoenix modeled index type/predicate cannot be preserved by this factory: " + index.getName());
	}

	@Override
	public void addObjectDetail(Index index, Table table, PhoenixSqlBuilder builder) {
		validate(index, table);
		if (index.isUnique())
			throw new IllegalArgumentException("Phoenix secondary indexes do not enforce UNIQUE: " + index.getName());
		final String type = index.getSpecifics().get("phoenix.indexType");
		if (type != null && !type.equalsIgnoreCase("GLOBAL") && !type.equalsIgnoreCase("LOCAL"))
			throw new IllegalArgumentException("Unknown Phoenix index type: " + type);
		final String state = index.getSpecifics().get("phoenix.indexState");
		if (state != null && !state.equalsIgnoreCase("a") && !state.equalsIgnoreCase("ACTIVE"))
			throw new UnsupportedOperationException(
					"Recreating a non-active Phoenix index requires a separate state transition: " + index.getName());
		if ("LOCAL".equalsIgnoreCase(type)) {
			if (index.getSpecifics().get(PhoenixDdlOptions.SALT_BUCKETS) != null)
				throw new IllegalArgumentException(
						"Phoenix LOCAL index salting cannot be declared separately: " + index.getName());
			builder.space()._add("LOCAL");
		}
		builder.index().ifNotExists(getOptions().isCreateIfNotExists()).name(index, false).on()
				.name(table, getOptions().isDecorateSchemaName()).space()._add("(");
		int i = 0;
		for (var column : index.getColumns()) {
			builder.comma(i++ > 0);
			appendColumnName(column, table, builder);
			if (column.getOrder() != null && column.getOrder() != Order.Asc)
				builder.space()._add(column.getOrder());
		}
		builder._add(")");
		if (!index.getIncludes().isEmpty()) {
			builder.space()._add("INCLUDE (");
			i = 0;
			for (var column : index.getIncludes()) {
				builder.comma(i++ > 0);
				appendColumnName(column, table, builder);
			}
			builder._add(")");
		}
		PhoenixDdlOptions.append(index.getSpecifics(), true, builder);
	}

	private void appendColumnName(ReferenceColumn reference, Table table, PhoenixSqlBuilder builder) {
		final String family = reference.getSpecifics().get(PhoenixCreateTableFactory.COLUMN_FAMILY);
		if (family != null) {
			builder.name(family)._add(".").name(reference);
			return;
		}
		final var column = table.getColumns().get(reference.getName());
		if (column != null)
			PhoenixCreateTableFactory.appendColumnName(column, builder);
		else
			builder.name(reference);
	}
}
