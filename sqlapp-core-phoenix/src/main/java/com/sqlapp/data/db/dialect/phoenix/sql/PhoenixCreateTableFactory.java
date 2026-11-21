/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.sql;

import com.sqlapp.data.db.sql.CreateTableFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * Native table existence clause and optional HBase column-family identifiers.
 */
public class PhoenixCreateTableFactory extends CreateTableFactory {
	public static final String COLUMN_FAMILY = "phoenix.columnFamily";
	/** Complete CREATE TABLE supplied for an explicitly transactional table. */
	public static final String TRANSACTIONAL_DEFINITION = "phoenix.transactionalDefinition";

	@Override
	public java.util.List<SqlOperation> createSql(Table table) {
		final String definition = table.getSpecifics().get(TRANSACTIONAL_DEFINITION);
		if (definition == null)
			return super.createSql(table);
		if (definition.isBlank() || table.getName() == null || table.getName().isBlank() || !Boolean.TRUE
				.equals(PhoenixDdlOptions.booleanValue(table.getSpecifics(), PhoenixDdlOptions.TRANSACTIONAL)))
			throw new IllegalArgumentException(
					"Phoenix transactional definition requires a named table, nonblank DDL and TRANSACTIONAL=true");
		if (!(getDialect() instanceof com.sqlapp.data.db.dialect.phoenix.Phoenix phoenix)
				|| !phoenix.supportsTransactionalTables())
			throw new UnsupportedOperationException("Phoenix transactional definitions require version 4.7 or later");
		final var result = new java.util.ArrayList<SqlOperation>();
		final var builder = createSqlBuilder();
		builder._add(definition);
		addSql(result, builder, SqlType.CREATE, table);
		addIndexDefinitions(table, result);
		addOtherDefinitions(table, result);
		return result;
	}

	@Override
	protected void addCreateIndexDefinition(Table table, Index index, java.util.List<SqlOperation> result) {
		result.addAll(getSqlFactoryRegistry().createSql(index, SqlType.CREATE));
	}

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
				if (column == null)
					throw new IllegalArgumentException("Phoenix primary-key column is missing from the table: "
							+ pk.getColumns().get(i).getName());
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
