/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.sql;

import java.util.List;
import com.sqlapp.data.db.sql.AlterTableFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;

/**
 * Phoenix ALTER supports adding/dropping columns, not generic MODIFY/RENAME.
 */
public class PhoenixAlterTableFactory extends AlterTableFactory {
	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		final var properties = difference.toDifference().getChangedProperties(getDialect());
		final var original = difference.getOriginal(Table.class);
		final var target = difference.getTarget(Table.class);
		for (String key : List.of(PhoenixDdlOptions.SALT_BUCKETS, PhoenixDdlOptions.IMMUTABLE_ROWS,
				PhoenixDdlOptions.MULTI_TENANT, PhoenixDdlOptions.DEFAULT_COLUMN_FAMILY)) {
			if (!java.util.Objects.equals(original.getSpecifics().get(key), target.getSpecifics().get(key)))
				throw new UnsupportedOperationException(
						"Phoenix table property changes require a separate migration: " + key);
		}
		if (properties.containsKey(SchemaProperties.NAME.getLabel())
				|| properties.containsKey(SchemaObjectProperties.CONSTRAINTS.getLabel()))
			throw new UnsupportedOperationException(
					"Phoenix table rename and constraint ALTER require a separate migration");
		return super.createDiffSql(difference);
	}

	private boolean primaryKey(Table table, Column column) {
		final var pk = table.getConstraints().getPrimaryKeyConstraint();
		return pk != null && pk.getColumns().get(column.getName()) != null;
	}

	@Override
	protected void addAddColumn(Table original, Table table, DbObjectDifference difference, List<SqlOperation> result) {
		final var column = difference.getTarget(Column.class);
		if (primaryKey(table, column))
			throw new UnsupportedOperationException(
					"Phoenix primary-key extension requires a separate migration: " + column.getName());
		final var builder = createSqlBuilder();
		builder.alter().table().name(table, getOptions().isDecorateSchemaName()).add()
				.ifNotExists(getOptions().isCreateIfNotExists());
		PhoenixCreateTableFactory.appendColumnName(column, builder);
		builder.space().definition(column, getTableOptions().getWithColumnRemarks().test(column));
		add(result, createOperation(builder.toString(), SqlType.ALTER, null, column));
	}

	@Override
	protected void addDeleteColumn(Table original, Table table, DbObjectDifference difference,
			List<SqlOperation> result) {
		final var column = difference.getOriginal(Column.class);
		if (primaryKey(original, column))
			throw new UnsupportedOperationException("Phoenix cannot drop a primary-key column: " + column.getName());
		final var builder = createSqlBuilder();
		builder.alter().table().name(table, getOptions().isDecorateSchemaName()).drop().column()
				.ifExists(getOptions().isDropIfExists());
		PhoenixCreateTableFactory.appendColumnName(column, builder);
		add(result, createOperation(builder.toString(), SqlType.ALTER, column));
	}

	@Override
	protected void addAlterColumn(Table original, Table table, Column oldColumn, Column column,
			DbObjectDifference difference, List<SqlOperation> result) {
		throw new UnsupportedOperationException(
				"Phoenix column modification requires a separate migration: " + column.getName());
	}

	@Override
	protected void addRenameColumn(Table original, Table table, Column oldColumn, Column column,
			DbObjectDifference difference, List<SqlOperation> result) {
		throw new UnsupportedOperationException(
				"Phoenix cannot rename a column using ALTER TABLE: " + column.getName());
	}
}
