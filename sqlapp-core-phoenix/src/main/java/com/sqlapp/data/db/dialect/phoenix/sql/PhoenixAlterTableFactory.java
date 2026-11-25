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
		final var source = difference.getOriginal(Table.class);
		final var destination = difference.getTarget(Table.class);
		final var normalizedSource = normalizeIndexFamilies(source);
		final var normalizedDestination = normalizeIndexFamilies(destination);
		if (source != normalizedSource || destination != normalizedDestination)
			difference = normalizedSource.diff(normalizedDestination);
		final var properties = difference.toDifference().getChangedProperties(getDialect());
		final var original = difference.getOriginal(Table.class);
		final var target = difference.getTarget(Table.class);
		final String originalDefinition = original.getSpecifics()
				.get(PhoenixCreateTableFactory.TRANSACTIONAL_DEFINITION);
		final String targetDefinition = target.getSpecifics().get(PhoenixCreateTableFactory.TRANSACTIONAL_DEFINITION);
		if (!java.util.Objects.equals(originalDefinition, targetDefinition) || (targetDefinition != null && properties
				.keySet().stream().anyMatch(property -> !SchemaObjectProperties.INDEXES.getLabel().equals(property))))
			throw new UnsupportedOperationException(
					"Phoenix table changes alongside a native transactional definition require a separate migration");
		for (String key : List.of(PhoenixDdlOptions.TRANSACTIONAL, PhoenixDdlOptions.TRANSACTION_PROVIDER)) {
			final Object before = key.equals(PhoenixDdlOptions.TRANSACTION_PROVIDER) ? original.getSpecifics().get(key)
					: PhoenixDdlOptions.booleanValue(original.getSpecifics(), key);
			final Object after = key.equals(PhoenixDdlOptions.TRANSACTION_PROVIDER) ? target.getSpecifics().get(key)
					: PhoenixDdlOptions.booleanValue(target.getSpecifics(), key);
			if (!java.util.Objects.equals(before, after))
				throw new UnsupportedOperationException(
						"Phoenix table property changes require a separate native migration: " + key);
		}
		final Boolean storeNulls = PhoenixDdlOptions.booleanValue(target.getSpecifics(), PhoenixDdlOptions.STORE_NULLS);
		final boolean changeStoreNulls = !java.util.Objects.equals(storeNulls,
				PhoenixDdlOptions.booleanValue(original.getSpecifics(), PhoenixDdlOptions.STORE_NULLS));
		if (changeStoreNulls && (storeNulls == null
				|| !(getDialect() instanceof com.sqlapp.data.db.dialect.phoenix.Phoenix5_3) || Boolean.TRUE.equals(
						PhoenixDdlOptions.booleanValue(target.getSpecifics(), PhoenixDdlOptions.TRANSACTIONAL))))
			throw new UnsupportedOperationException(
					"Phoenix STORE_NULLS ALTER requires an explicit setting on a nontransactional 5.3+ table");
		final var tuningChanges = new java.util.LinkedHashMap<String, String>();
		for (String key : List.of(PhoenixDdlOptions.UPDATE_CACHE_FREQUENCY, PhoenixDdlOptions.GUIDE_POSTS_WIDTH)) {
			final String before = PhoenixDdlOptions.tuningValue(original.getSpecifics(), key, getDialect());
			final String after = PhoenixDdlOptions.tuningValue(target.getSpecifics(), key, getDialect());
			if (!java.util.Objects.equals(before, after)) {
				if (after == null)
					throw new IllegalArgumentException("Removing " + key
							+ " is ambiguous; specify the intended value explicitly (GUIDE_POSTS_WIDTH=NULL resets to the server setting)");
				tuningChanges.put(key, after);
			}
		}
		final Boolean oldWal = PhoenixDdlOptions.booleanValue(original.getSpecifics(), PhoenixDdlOptions.DISABLE_WAL);
		final Boolean wal = PhoenixDdlOptions.booleanValue(target.getSpecifics(), PhoenixDdlOptions.DISABLE_WAL);
		final boolean changeWal = !java.util.Objects.equals(oldWal, wal);
		if (changeWal && wal == null)
			throw new IllegalArgumentException("Phoenix DISABLE_WAL removal is ambiguous; set "
					+ PhoenixDdlOptions.DISABLE_WAL + " explicitly to true or false");
		final Boolean oldAppendOnly = PhoenixDdlOptions.booleanValue(original.getSpecifics(),
				PhoenixDdlOptions.APPEND_ONLY_SCHEMA);
		final Boolean appendOnly = PhoenixDdlOptions.booleanValue(target.getSpecifics(),
				PhoenixDdlOptions.APPEND_ONLY_SCHEMA);
		if ((oldAppendOnly != null || appendOnly != null)
				&& (!(getDialect() instanceof com.sqlapp.data.db.dialect.phoenix.Phoenix phoenix)
						|| !phoenix.supportsAppendOnlySchema()))
			throw new UnsupportedOperationException("Phoenix APPEND_ONLY_SCHEMA requires version 4.8 or later");
		if (!java.util.Objects.equals(oldAppendOnly, appendOnly))
			throw new UnsupportedOperationException(
					"Phoenix APPEND_ONLY_SCHEMA alteration requires a separate migration");
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
			final Object before = comparableProperty(original, key);
			final Object after = comparableProperty(target, key);
			if (!java.util.Objects.equals(before, after))
				throw new UnsupportedOperationException(
						"Phoenix table property changes require a separate migration: " + key);
		}
		if (properties.containsKey(SchemaObjectProperties.CONSTRAINTS.getLabel())) {
			final var extension = appendPrimaryKeyColumn(original, target);
			if (extension != null)
				return List.of(extension);
		}
		if (properties.containsKey(SchemaProperties.NAME.getLabel())
				|| properties.containsKey(SchemaObjectProperties.CONSTRAINTS.getLabel()))
			throw new UnsupportedOperationException(
					"Phoenix table rename and constraint ALTER require a separate migration");
		final var columns = (DbObjectDifferenceCollection) properties.get(SchemaObjectProperties.COLUMNS.getLabel());
		if (columns != null) {
			if (Boolean.TRUE.equals(oldAppendOnly) && !columns.getList(State.Deleted).isEmpty())
				throw new UnsupportedOperationException("Cannot drop columns from a Phoenix APPEND_ONLY_SCHEMA table");
			final var deletedNames = new java.util.HashSet<String>();
			for (final var deleted : columns.getList(State.Deleted)) {
				deletedNames.add(deleted.getOriginal(Column.class).getName());
			}
			for (final var index : target.getIndexes()) {
				if (!deletedNames.isEmpty() && PhoenixCreateIndexFactory.hasOpaqueDependencies(index))
					throw new UnsupportedOperationException(
							"Phoenix index dependencies cannot be inferred for column deletion: " + index.getName()
									+ "; remove the index explicitly before dropping columns");
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
				if (!PhoenixAlterIndexFactory.stateOnly(modified.getOriginal(Index.class),
						modified.getTarget(Index.class), getDialect()))
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
		if (changeStoreNulls) {
			final var builder = createSqlBuilder();
			addAlterObject(target, builder);
			builder.space()._add("SET STORE_NULLS=")._add(storeNulls ? "TRUE" : "FALSE");
			add(result, createOperation(builder.toString(), SqlType.ALTER, original, target));
		}
		if (changeWal) {
			final var builder = createSqlBuilder();
			addAlterObject(target, builder);
			builder.space()._add("SET DISABLE_WAL=")._add(wal ? "TRUE" : "FALSE");
			add(result, createOperation(builder.toString(), SqlType.ALTER, original, target));
		}
		for (var entry : tuningChanges.entrySet()) {
			final var builder = createSqlBuilder();
			addAlterObject(target, builder);
			builder.space()._add("SET ")._add(PhoenixDdlOptions.tuningName(entry.getKey()))._add("=");
			PhoenixDdlOptions.appendTuningValue(builder, entry.getValue());
			add(result, createOperation(builder.toString(), SqlType.ALTER, original, target));
		}
		return result;
	}

	private SqlOperation appendPrimaryKeyColumn(Table original, Table target) {
		if (!(getDialect() instanceof com.sqlapp.data.db.dialect.phoenix.Phoenix5_3) || original instanceof View
				|| !original.getIndexes().isEmpty() || !target.getIndexes().isEmpty())
			return null;
		final var before = original.getPrimaryKeyConstraint();
		final var after = target.getPrimaryKeyConstraint();
		if (before == null || after == null || before.getColumns().isEmpty()
				|| after.getColumns().size() != before.getColumns().size() + 1)
			return null;
		final var previousTail = original.getColumns().get(before.getColumns().getLast().getName());
		final var reference = after.getColumns().getLast();
		final var column = target.getColumns().get(reference.getName());
		if (previousTail == null || previousTail.getDataType() != com.sqlapp.data.db.datatype.DataType.VARCHAR
				|| previousTail.getArrayDimension() > 0 || column == null
				|| original.getColumns().get(column.getName()) != null || !isSupportedAppendedKey(column)
				|| column.getArrayDimension() > 0 || column.isNotNull() || column.getDefaultValue() != null
				|| column.getFormula() != null || column.isIdentity()
				|| column.getSpecifics().get(PhoenixCreateTableFactory.COLUMN_FAMILY) != null
				|| (reference.getOrder() != null && reference.getOrder() != Order.Asc))
			return null;
		final var reduced = target.clone();
		reduced.getPrimaryKeyConstraint().getColumns().removeLast();
		reduced.getColumns().remove(column.getName());
		if (!original.diff(reduced).toDifference().getChangedProperties(getDialect()).isEmpty())
			return null;
		final var builder = createSqlBuilder();
		addAlterObject(target, builder);
		builder.add();
		addColumnDefinition(column, builder);
		builder.space()._add("NULL PRIMARY KEY");
		return createOperation(builder.toString(), SqlType.ALTER, null, column);
	}

	private static boolean isSupportedAppendedKey(Column column) {
		return column.getDataType() == com.sqlapp.data.db.datatype.DataType.VARCHAR;
	}

	private static Object comparableProperty(Table table, String key) {
		if (PhoenixDdlOptions.SALT_BUCKETS.equals(key))
			return PhoenixDdlOptions.saltBuckets(table.getSpecifics(), false);
		if (PhoenixDdlOptions.MULTI_TENANT.equals(key))
			return PhoenixDdlOptions.booleanValue(table.getSpecifics(), key);
		return table.getSpecifics().get(key);
	}

	private static Table normalizeIndexFamilies(Table table) {
		if (table == null || table.getIndexes().stream()
				.noneMatch(index -> index.getColumns().stream().anyMatch(column -> hasRedundantFamily(column, table))
						|| index.getIncludes().stream().anyMatch(column -> hasRedundantFamily(column, table))))
			return table;
		final var copy = table.clone();
		for (var index : copy.getIndexes())
			normalizeIndexFamilies(index, copy);
		return copy;
	}

	static void normalizeIndexFamilies(Index index, Table owner) {
		if (owner == null)
			return;
		for (var key : index.getColumns())
			removeRedundantFamily(key, owner);
		for (var column : index.getIncludes())
			removeRedundantFamily(column, owner);
	}

	private static void removeRedundantFamily(ReferenceColumn reference, Table table) {
		if (hasRedundantFamily(reference, table))
			reference.getSpecifics().remove(PhoenixCreateTableFactory.COLUMN_FAMILY);
	}

	private static boolean hasRedundantFamily(ReferenceColumn reference, Table table) {
		final String key = PhoenixCreateTableFactory.COLUMN_FAMILY;
		final String explicit = reference.getSpecifics().get(key);
		if (explicit == null || explicit.isBlank())
			return false;
		final var column = table.getColumns().get(reference.getName());
		return column != null && explicit.equals(column.getSpecifics().get(key));
	}

	@Override
	protected void addIndexDefinition(final Table original, final Table table, final Index oldIndex, final Index index,
			final DbObjectDifference difference, final List<SqlOperation> result) {
		if (difference.getState() == State.Modified
				&& PhoenixAlterIndexFactory.stateOnly(oldIndex, index, getDialect())) {
			result.addAll(getSqlFactoryRegistry().createSql(difference, SqlType.ALTER));
			return;
		}
		super.addIndexDefinition(original, table, oldIndex, index, difference, result);
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
