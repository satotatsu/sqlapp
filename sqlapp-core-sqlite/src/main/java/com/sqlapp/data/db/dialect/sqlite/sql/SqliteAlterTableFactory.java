/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.sqlite.sql;

import java.util.List;
import java.util.Set;

import com.sqlapp.data.db.dialect.sqlite.util.SqliteSqlBuilder;
import com.sqlapp.data.db.sql.AbstractAlterTableFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.DbObjectDifferenceCollection;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.State;
import com.sqlapp.data.schemas.Table;

/**
 * Native SQLite ALTER operations; changes requiring reconstruction fail
 * explicitly.
 */
public class SqliteAlterTableFactory extends AbstractAlterTableFactory<SqliteSqlBuilder> {
	@Override
	public List<SqlOperation> createSql(Table table) {
		throw unsupported("ALTER requires the original and target table difference");
	}

	@Override
	public List<SqlOperation> createDiffSql(DbObjectDifference difference) {
		Table original = difference.getOriginal(Table.class);
		Table target = difference.getTarget(Table.class);
		if (original == null || target == null)
			throw unsupported("ALTER requires both the original and target table");
		var changes = difference.toDifference().getChangedProperties(getDialect());
		for (String property : changes.keySet())
			if (!Set.of("name", "columns", "indexes").contains(property))
				throw unsupported("Cannot alter table property: " + property);
		if (changes.containsKey("name")) {
			if (changes.size() != 1)
				throw unsupported("Apply table rename separately from other changes");
			var builder = createSqlBuilder();
			builder.alter().table().name(original, original.getSchemaName() != null).rename().to().name(target, false);
			return List.of(createOperation(builder.toString(), SqlType.ALTER, original, target));
		}
		var columns = (DbObjectDifferenceCollection) changes.get("columns");
		if (columns != null) {
			var retained = original.getColumns().stream().map(Column::getName)
					.filter(name -> target.getColumns().get(name) != null).toList();
			var targetRetained = target.getColumns().stream().map(Column::getName)
					.filter(name -> original.getColumns().get(name) != null).toList();
			if (!retained.equals(targetRetained))
				throw unsupported("Cannot reorder existing columns");
			if (!columns.getList(State.Added).isEmpty() && !columns.getList(State.Deleted).isEmpty())
				throw unsupported("Ambiguous column replacement; use explicit renameColumn for a rename");
			for (var diff : columns.getList(State.Added))
				validateAddedColumn(diff.getTarget(Column.class));
			for (var diff : columns.getList(State.Modified)) {
				var properties = new java.util.HashSet<>(
						diff.toDifference().getChangedProperties(getDialect()).keySet());
				properties.remove("ordinal");
				if (!properties.isEmpty() && !properties.equals(Set.of("name")))
					throw unsupported("Cannot change column definition: " + diff.getTarget(Column.class).getName());
			}
		}
		return super.createDiffSql(difference);
	}

	@Override
	protected void addRenameOrAlterColumn(Table original, Table target, DbObjectDifference diff,
			List<SqlOperation> result) {
		if (diff.toDifference().getChangedProperties(getDialect()).containsKey("name"))
			addRenameColumn(original, target, diff.getOriginal(Column.class), diff.getTarget(Column.class), diff,
					result);
	}

	/**
	 * Explicit rename avoids guessing whether a delete/add difference means
	 * replacement. Requires SQLite 3.25+.
	 */
	public List<SqlOperation> renameColumn(Table table, String oldName, String newName) {
		if (getDialect() instanceof com.sqlapp.data.db.dialect.sqlite.Sqlite sqlite && !sqlite.supportsRenameColumn())
			throw new IllegalArgumentException("SQLite column rename requires SQLite 3.25 or later");
		if (table.getColumns().get(oldName) == null || newName == null || newName.isBlank()
				|| table.getColumns().get(newName) != null)
			throw new IllegalArgumentException(
					"SQLite column rename requires an existing source and an unused target name");
		var builder = createSqlBuilder();
		builder.alter().table().name(table, table.getSchemaName() != null).rename().column().name(oldName).to()
				.name(newName);
		return List.of(createOperation(builder.toString(), SqlType.ALTER, table));
	}

	private void validateAddedColumn(Column column) {
		String value = column.getDefaultValue();
		String normalized = value == null ? null : value.trim().toUpperCase(java.util.Locale.ROOT);
		if (column.isIdentity() || column.isPrimaryKey() || column.isFormulaPersisted()
				|| column.getSpecifics().containsKey("sqlite.primaryKey.inlineDesc")
				|| normalized != null && (normalized.startsWith("(")
						|| Set.of("CURRENT_TIME", "CURRENT_DATE", "CURRENT_TIMESTAMP").contains(normalized))
				|| column.isNotNull() && (normalized == null || normalized.equals("NULL")))
			throw unsupported("Column cannot be added directly by SQLite: " + column.getName());
	}

	private IllegalArgumentException unsupported(String reason) {
		return new IllegalArgumentException(
				reason + ". SQLite requires a separately planned table rebuild for this change.");
	}

	@Override
	protected void addAddColumn(Table original, Table target, DbObjectDifference diff, List<SqlOperation> result) {
		var column = diff.getTarget(Column.class);
		var builder = createSqlBuilder();
		builder.alter().table().name(target, target.getSchemaName() != null).add().column().space();
		var factory = (SqliteCreateTableFactory) getSqlFactoryRegistry().getSqlFactory(target, SqlType.CREATE);
		factory.addColumnDefinition(column, builder);
		result.add(createOperation(builder.toString(), SqlType.ALTER, null, column));
	}

	@Override
	protected void addDeleteColumn(Table original, Table target, DbObjectDifference diff, List<SqlOperation> result) {
		if (getDialect() instanceof com.sqlapp.data.db.dialect.sqlite.Sqlite sqlite && !sqlite.supportsDropColumn())
			throw new IllegalArgumentException("SQLite DROP COLUMN requires SQLite 3.35 or later");
		var column = diff.getOriginal(Column.class);
		var builder = createSqlBuilder();
		builder.alter().table().name(target, target.getSchemaName() != null).drop().column().name(column);
		result.add(createOperation(builder.toString(), SqlType.ALTER, column));
	}

	@Override
	protected void addRenameColumn(Table original, Table target, Column oldColumn, Column column,
			DbObjectDifference diff, List<SqlOperation> result) {
		if (getDialect() instanceof com.sqlapp.data.db.dialect.sqlite.Sqlite sqlite && !sqlite.supportsRenameColumn())
			throw new IllegalArgumentException("SQLite column rename requires SQLite 3.25 or later");
		var builder = createSqlBuilder();
		builder.alter().table().name(target, target.getSchemaName() != null).rename().column().name(oldColumn).to()
				.name(column);
		result.add(createOperation(builder.toString(), SqlType.ALTER, oldColumn, column));
	}

	@Override
	protected void addCreateIndexDefinition(Table original, Table target, Index oldIndex, Index index,
			DbObjectDifference diff, List<SqlOperation> result) {
		result.addAll(getSqlFactoryRegistry().createSql(index, SqlType.CREATE));
	}

	@Override
	protected void addDropIndexDefinition(Table original, Table target, Index oldIndex, Index index,
			DbObjectDifference diff, List<SqlOperation> result) {
		if (oldIndex.getName().toLowerCase(java.util.Locale.ROOT).startsWith("sqlite_autoindex_"))
			throw unsupported("Cannot drop a SQLite internal index");
		var builder = createSqlBuilder();
		builder.drop().index().name(oldIndex, oldIndex.getSchemaName() != null);
		result.add(createOperation(builder.toString(), SqlType.DROP, oldIndex));
	}
}
