/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.sql;

import com.sqlapp.data.db.sql.CreateTableFactory;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * Native table existence clause and optional HBase column-family identifiers.
 */
public class PhoenixCreateTableFactory extends CreateTableFactory {
	public static final String COLUMN_FAMILY = "phoenix.columnFamily";

	@Override
	protected void addOption(Table table, AbstractSqlBuilder<?> builder) {
		PhoenixDdlOptions.append(table.getSpecifics(), false, builder);
	}

	@Override
	protected void addCreateObject(Table table, AbstractSqlBuilder<?> builder) {
		final var pk = table.getConstraints().getPrimaryKeyConstraint();
		if (pk != null) {
			for (int i = 0; i < pk.getColumns().size(); i++) {
				final var column = table.getColumns().get(pk.getColumns().get(i).getName());
				if (column != null && column.getArrayDimension() > 0 && i != pk.getColumns().size() - 1)
					throw new IllegalArgumentException(
							"Phoenix ARRAY must be the final primary-key column: " + column.getName());
				if (column != null && column.getSpecifics().get(COLUMN_FAMILY) != null)
					throw new IllegalArgumentException(
							"Phoenix primary-key columns cannot have a column family: " + column.getName());
			}
		}
		builder.create().table().ifNotExists(getOptions().isCreateIfNotExists()).name(table,
				getOptions().isDecorateSchemaName());
	}

	@Override
	protected void addColumnDefinition(Column column, AbstractSqlBuilder<?> builder) {
		appendColumnName(column, builder);
		builder.space().definition(column, getTableOptions().getWithColumnRemarks().test(column));
	}

	static void appendColumnName(Column column, AbstractSqlBuilder<?> builder) {
		final String family = column.getSpecifics().get(COLUMN_FAMILY);
		if (family == null) {
			builder.name(column);
			return;
		}
		if (family.isBlank())
			throw new IllegalArgumentException("Phoenix column family must not be blank: " + column.getName());
		builder.name(family)._add(".").name(column);
	}
}
