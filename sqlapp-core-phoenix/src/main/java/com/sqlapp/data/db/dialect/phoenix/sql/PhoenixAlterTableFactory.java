/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.phoenix.sql;

import java.util.List;
import com.sqlapp.data.db.sql.AlterTableFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.*;
import com.sqlapp.util.AbstractSqlBuilder;

/**
 * Phoenix ALTER supports column addition/deletion and explicit IMMUTABLE_ROWS
 * changes.
 */
public class PhoenixAlterTableFactory extends AlterTableFactory {
	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		final var properties = difference.toDifference().getChangedProperties(getDialect());
		final var original = difference.getOriginal(Table.class);
		final var target = difference.getTarget(Table.class);
		final Boolean oldImmutable = PhoenixDdlOptions.booleanValue(original.getSpecifics(),
				PhoenixDdlOptions.IMMUTABLE_ROWS);
		final Boolean immutable = PhoenixDdlOptions.booleanValue(target.getSpecifics(),
				PhoenixDdlOptions.IMMUTABLE_ROWS);
		final boolean changeImmutable = !java.util.Objects.equals(oldImmutable, immutable);
		if (changeImmutable && immutable == null)
			throw new IllegalArgumentException("Phoenix IMMUTABLE_ROWS removal is ambiguous; set "
					+ PhoenixDdlOptions.IMMUTABLE_ROWS + " explicitly to true or false");
		for (String key : List.of(PhoenixDdlOptions.SALT_BUCKETS, PhoenixDdlOptions.MULTI_TENANT,
				PhoenixDdlOptions.DEFAULT_COLUMN_FAMILY)) {
			if (!java.util.Objects.equals(original.getSpecifics().get(key), target.getSpecifics().get(key)))
				throw new UnsupportedOperationException(
						"Phoenix table property changes require a separate migration: " + key);
		}
		if (properties.containsKey(SchemaProperties.NAME.getLabel())
				|| properties.containsKey(SchemaObjectProperties.CONSTRAINTS.getLabel()))
			throw new UnsupportedOperationException(
					"Phoenix table rename and constraint ALTER require a separate migration");
		final var columns = (DbObjectDifferenceCollection) properties.get(SchemaObjectProperties.COLUMNS.getLabel());
		if (columns != null) {
			final var deletedNames = new java.util.HashSet<String>();
			for (final var deleted : columns.getList(State.Deleted)) {
				deletedNames.add(deleted.getOriginal(Column.class).getName());
			}
			for (final var index : target.getIndexes()) {
				if (index.getColumns().stream().anyMatch(column -> deletedNames.contains(column.getName()))
						|| index.getIncludes().stream().anyMatch(column -> deletedNames.contains(column.getName()))) {
					throw new IllegalArgumentException("Phoenix target index still references a deleted column: "
							+ index.getName() + "; remove or update that index in the target model");
				}
			}
		}
		final List<SqlOperation> result = new java.util.ArrayList<>();
		final var indexes = (DbObjectDifferenceCollection) properties.get(SchemaObjectProperties.INDEXES.getLabel());
		if (indexes != null) {
			for (final var modified : indexes.getList(State.Modified)) {
				result.addAll(getSqlFactoryRegistry().createSql(modified.getOriginal(Index.class), SqlType.DROP));
			}
		}
		result.addAll(super.createDiffSql(difference));
		if (changeImmutable) {
			final var builder = createSqlBuilder();
			addAlterObject(target, builder);
			builder.space()._add("SET IMMUTABLE_ROWS=")._add(immutable ? "TRUE" : "FALSE");
			add(result, createOperation(builder.toString(), SqlType.ALTER, original, target));
		}
		return result;
	}

	@Override
	protected void addCreateIndexDefinition(final Table original, final Table table, final Index oldIndex,
			final Index index, final DbObjectDifference difference, final List<SqlOperation> result) {
		final var operations = getSqlFactoryRegistry().createSql(index, SqlType.CREATE);
		if (operations.isEmpty()) {
			throw new UnsupportedOperationException(
					"Phoenix constraint-owned index requires a separate migration: " + index.getName());
		}
		result.addAll(operations);
	}

	@Override
	protected void addDropIndexDefinition(final Table original, final Table table, final Index oldIndex,
			final Index index, final DbObjectDifference difference, final List<SqlOperation> result) {
		if (difference.getState() != State.Modified) {
			result.addAll(getSqlFactoryRegistry().createSql(oldIndex, SqlType.DROP));
		}
	}

	protected void addAlterObject(final Table table, final AbstractSqlBuilder<?> builder) {
		builder.alter().table().name(table, getOptions().isDecorateSchemaName());
	}

	protected void addColumnDefinition(final Column column, final AbstractSqlBuilder<?> builder) {
		PhoenixCreateTableFactory.appendColumnName(column, builder);
		builder.space().definition(column, getTableOptions().getWithColumnRemarks().test(column));
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
		addAlterObject(table, builder);
		builder.add().ifNotExists(getOptions().isCreateIfNotExists());
		addColumnDefinition(column, builder);
		add(result, createOperation(builder.toString(), SqlType.ALTER, null, column));
	}

	@Override
	protected void addDeleteColumn(Table original, Table table, DbObjectDifference difference,
			List<SqlOperation> result) {
		final var column = difference.getOriginal(Column.class);
		if (primaryKey(original, column))
			throw new UnsupportedOperationException("Phoenix cannot drop a primary-key column: " + column.getName());
		final var builder = createSqlBuilder();
		addAlterObject(table, builder);
		builder.drop().column().ifExists(getOptions().isDropIfExists());
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
