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
		if (!changes.isEmpty() && ("true".equals(original.getSpecifics().get("sqlite.virtual"))
				|| com.sqlapp.data.db.dialect.sqlite.util.SqliteDdl.isVirtualTable(toString(original.getDefinition()))))
			throw unsupported("Virtual table changes require module-specific planning");
		for (String property : changes.keySet())
			if (!Set.of("name", "columns", "indexes", "constraints").contains(property))
				throw unsupported("Cannot alter table property: " + property);
		var constraints = (DbObjectDifferenceCollection) changes.get("constraints");
		if (constraints != null) {
			for (var diff : constraints.getList(State.Added, State.Modified, State.Deleted)) {
				if ((diff.getOriginal() != null
						&& !(diff.getOriginal() instanceof com.sqlapp.data.schemas.CheckConstraint))
						|| (diff.getTarget() != null
								&& !(diff.getTarget() instanceof com.sqlapp.data.schemas.CheckConstraint)))
					throw unsupported("Only named CHECK constraints can be changed natively");
				if (diff.getOriginal() != null)
					getSqlFactoryRegistry().createSql((com.sqlapp.data.schemas.CheckConstraint) diff.getOriginal(),
							SqlType.DROP);
				if (diff.getTarget() != null)
					getSqlFactoryRegistry().createSql((com.sqlapp.data.schemas.CheckConstraint) diff.getTarget(),
							SqlType.CREATE);
			}
		}
		if (changes.containsKey("name")) {
			if (changes.size() != 1)
				throw unsupported("Apply table rename separately from other changes");
			var builder = createSqlBuilder();
			builder.alter().table().name(original, original.getSchemaName() != null).rename().to().name(target, false);
			return List.of(createOperation(builder.toString(), SqlType.ALTER, original, target));
		}
		var columns = (DbObjectDifferenceCollection) changes.get("columns");
		if (columns != null) {
			var addedNames = columns.getList(State.Added).stream().map(diff -> diff.getTarget(Column.class).getName())
					.collect(java.util.stream.Collectors.toSet());
			boolean added = false;
			for (var column : target.getColumns()) {
				if (addedNames.contains(column.getName()))
					added = true;
				else if (added)
					throw unsupported("SQLite can only append new columns after existing columns");
			}
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
				if (properties.equals(Set.of("notNull"))) {
					if (!((com.sqlapp.data.db.dialect.sqlite.Sqlite) getDialect()).supportsAlterColumnNullability())
						throw new IllegalArgumentException(
								"SQLite ALTER COLUMN nullability requires SQLite 3.53 or later");
					continue;
				}
				if (!properties.isEmpty() && !properties.equals(Set.of("name")))
					throw unsupported("Cannot change column definition: " + diff.getTarget(Column.class).getName());
			}
		}
		var operations = super.createDiffSql(difference);
		// A modified index is normally dropped and recreated after column changes.
		// SQLite refuses DROP COLUMN while that old index still references it.
		// Keep each phase stable, including column order and constraint validation.
		operations.sort(java.util.Comparator.comparingInt(operation -> operation.getSqlType() == SqlType.DROP ? 0
				: operation.getSqlType() == SqlType.CREATE ? 2 : 1));
		return operations;
	}

	@Override
	protected void addRenameOrAlterColumn(Table original, Table target, DbObjectDifference diff,
			List<SqlOperation> result) {
		if (diff.toDifference().getChangedProperties(getDialect()).containsKey("name"))
			addRenameColumn(original, target, diff.getOriginal(Column.class), diff.getTarget(Column.class), diff,
					result);
		else if (diff.toDifference().getChangedProperties(getDialect()).containsKey("notNull")) {
			var column = diff.getTarget(Column.class);
			var builder = createSqlBuilder();
			builder.alter().table().name(target, target.getSchemaName() != null).space()._add("ALTER COLUMN ")
					.name(column).space()._add(column.isNotNull() ? "SET NOT NULL" : "DROP NOT NULL");
			if (column.isNotNull() && column.getSpecifics().containsKey("sqlite.notNull.onConflict")) {
				String policy = column.getSpecifics().get("sqlite.notNull.onConflict")
						.toUpperCase(java.util.Locale.ROOT);
				if (!Set.of("ROLLBACK", "ABORT", "FAIL", "IGNORE", "REPLACE").contains(policy))
					throw new IllegalArgumentException("Invalid SQLite NOT NULL conflict policy: " + policy);
				builder._add(" ON CONFLICT ")._add(policy);
			}
			result.add(createOperation(builder.toString(), SqlType.ALTER, diff.getOriginal(Column.class), column));
		}
	}

	/**
	 * Explicit rename avoids guessing whether a delete/add difference means
	 * replacement. Requires SQLite 3.25+.
	 */
	public List<SqlOperation> renameColumn(Table table, String oldName, String newName) {
		if ("true".equals(table.getSpecifics().get("sqlite.virtual"))
				|| com.sqlapp.data.db.dialect.sqlite.util.SqliteDdl.isVirtualTable(toString(table.getDefinition())))
			throw unsupported("Virtual table column rename requires module-specific planning");
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
				|| column.isNotNull() && com.sqlapp.util.CommonUtils.isEmpty(column.getFormula())
						&& (normalized == null || normalized.equals("NULL")))
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
	protected void addCreateConstraintDefinition(Table original, Table target,
			com.sqlapp.data.schemas.Constraint oldConstraint, com.sqlapp.data.schemas.Constraint constraint,
			DbObjectDifference diff, List<SqlOperation> result) {
		result.addAll(getSqlFactoryRegistry().createSql(constraint, SqlType.CREATE));
	}

	@Override
	protected void addDropConstraintDefinition(Table original, Table target,
			com.sqlapp.data.schemas.Constraint oldConstraint, com.sqlapp.data.schemas.Constraint constraint,
			DbObjectDifference diff, List<SqlOperation> result) {
		result.addAll(getSqlFactoryRegistry().createSql(oldConstraint, SqlType.DROP));
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
