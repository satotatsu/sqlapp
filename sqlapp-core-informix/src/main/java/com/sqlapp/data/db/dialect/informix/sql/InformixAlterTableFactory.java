/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.informix.sql;

import java.util.List;
import java.util.Map;

import com.sqlapp.data.db.dialect.informix.util.InformixSqlBuilder;
import com.sqlapp.data.db.dialect.informix.util.InformixTableOptions;
import com.sqlapp.data.db.sql.AbstractAlterTableFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Constraint;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.DbObjectDifferenceCollection;
import com.sqlapp.data.schemas.Difference;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.SchemaObjectProperties;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.State;

/**
 * Native column alterations; storage changes require an explicit data
 * migration.
 */
public class InformixAlterTableFactory extends AbstractAlterTableFactory<InformixSqlBuilder> {

	@Override
	public List<SqlOperation> createDiffSql(final DbObjectDifference difference) {
		final var changes = difference.toDifference().getChangedProperties(getDialect());
		final Table original = difference.getOriginal(Table.class), target = difference.getTarget(Table.class);
		if (changes.containsKey(SchemaObjectProperties.PARTITIONING.getLabel())
				|| !java.util.Objects.equals(original.getTableSpaceName(), target.getTableSpaceName())
				|| !original.getInherits().equals(target.getInherits())
				|| !InformixTableOptions.otherSpecifics(original).equals(InformixTableOptions.otherSpecifics(target)))
			throw new UnsupportedOperationException("Informix table storage, inheritance or specifics changes require "
					+ "an explicit migration plan: " + original.getName());
		if (!java.util.Objects.equals(original.getSchemaName(), target.getSchemaName())
				|| !java.util.Objects.equals(original.getCatalogName(), target.getCatalogName()))
			throw new UnsupportedOperationException(
					"Informix ALTER cannot move a table between owners or databases: " + original.getName());
		var result = new java.util.ArrayList<>(super.createDiffSql(difference));
		String mode = InformixTableOptions.lockMode(target);
		if (!java.util.Objects.equals(InformixTableOptions.lockMode(original), mode))
			appendTableOption(original, target, "LOCK MODE", mode == null ? null : "(" + mode + ")", result);
		Integer size = InformixTableOptions.nextSize(target);
		if (!java.util.Objects.equals(InformixTableOptions.nextSize(original), size))
			appendTableOption(original, target, "MODIFY NEXT SIZE", size == null ? null : size.toString(), result);
		return result;
	}

	private void appendTableOption(Table original, Table target, String clause, String value,
			List<SqlOperation> result) {
		if (value == null)
			throw new UnsupportedOperationException("Informix ALTER requires an explicit target value for " + clause);
		var builder = createSqlBuilder();
		builder.alter().table().name(target, getOptions().isDecorateSchemaName()).space()._add(clause).space()
				._add(value);
		add(result, createOperation(builder.toString(), SqlType.ALTER, original, target));
	}

	@Override
	protected void addRenameTable(final Table original, final Table table, final List<SqlOperation> result) {
		final var builder = createSqlBuilder();
		builder.rename().table().name(original, getOptions().isDecorateSchemaName()).to().name(table, false);
		add(result, createOperation(builder.toString(), SqlType.ALTER, original, table));
	}

	@Override
	protected void addColumnDefinitions(final Map<String, Difference<?>> allDiff, final Table original,
			final Table table, final DbObjectDifferenceCollection columns, final List<SqlOperation> result) {
		// A rename plus a type change can be represented by both a matched pair and
		// unmatched LCS entries. Never DROP/ADD a column already paired for rename.
		final var renamed = columns.getList(State.Modified).stream()
				.filter(d -> !d.getOriginal(Column.class).getName().equals(d.getTarget(Column.class).getName()))
				.toList();
		for (final var diff : columns.getList(State.Deleted)) {
			if (renamed.stream().noneMatch(
					d -> d.getOriginal(Column.class).getName().equals(diff.getOriginal(Column.class).getName())))
				addDeleteColumn(original, table, diff, result);
		}
		final var modified = new java.util.ArrayList<>(columns.getList(State.Added, State.Modified));
		modified.removeIf(diff -> diff.getState() == State.Added && renamed.stream()
				.anyMatch(d -> d.getTarget(Column.class).getName().equals(diff.getTarget(Column.class).getName())));
		sortColumnDiff(modified);
		for (final var diff : modified) {
			if (diff.getState() == State.Added)
				addAddColumn(original, table, diff, result);
			else
				addRenameOrAlterColumn(original, table, diff, result);
		}
	}

	@Override
	protected void addAddColumn(final Table original, final Table table, final DbObjectDifference diff,
			final List<SqlOperation> result) {
		final var column = diff.getTarget(Column.class);
		final var builder = createSqlBuilder();
		builder.alter().table().name(table, getOptions().isDecorateSchemaName()).space()._add("ADD (").name(column)
				.space().definition(column, false);
		// A following original column is available even when several new columns
		// are inserted at the same position, in target order.
		boolean followsAddedColumn = false;
		for (Column candidate : table.getColumns()) {
			if (followsAddedColumn && original.getColumns().get(candidate.getName()) != null) {
				builder.space()._add("BEFORE").space().name(candidate);
				break;
			}
			if (candidate.getName().equals(column.getName()))
				followsAddedColumn = true;
		}
		builder._add(')');
		add(result, createOperation(builder.toString(), SqlType.ALTER, null, column));
	}

	@Override
	protected void addDeleteColumn(final Table original, final Table table, final DbObjectDifference diff,
			final List<SqlOperation> result) {
		final var column = diff.getOriginal(Column.class);
		final var builder = createSqlBuilder();
		builder.alter().table().name(table, getOptions().isDecorateSchemaName()).space()._add("DROP (").name(column)
				._add(')');
		add(result, createOperation(builder.toString(), SqlType.ALTER, column));
	}

	@Override
	protected void addAlterColumn(final Table original, final Table table, final Column oldColumn, final Column column,
			final DbObjectDifference diff, final List<SqlOperation> result) {
		validateModifyConstraints(original, table);
		// The rename follows MODIFY, so address the existing column name.
		// Keep the owning model so named NOT NULL constraints remain available to the
		// builder.
		final var declaration = table.clone().getColumns().get(column.getName()).setName(oldColumn.getName());
		final var builder = createSqlBuilder();
		builder.alter().table().name(table, getOptions().isDecorateSchemaName()).space()._add("MODIFY (")
				.name(declaration).space().definition(declaration, false)._add(')');
		add(result, createOperation(builder.toString(), SqlType.ALTER, oldColumn, column));
	}

	@Override
	protected void addRenameColumn(final Table original, final Table table, final Column oldColumn, final Column column,
			final DbObjectDifference diff, final List<SqlOperation> result) {
		final var builder = createSqlBuilder();
		builder.rename().column().name(table, getOptions().isDecorateSchemaName())._add('.').name(oldColumn).to()
				.name(column);
		add(result, createOperation(builder.toString(), SqlType.ALTER, oldColumn, column));
	}

	@Override
	protected void addCreateConstraintDefinition(final Table original, final Table table,
			final Constraint oldConstraint, final Constraint constraint, final DbObjectDifference diff,
			final List<SqlOperation> result) {
		if (constraint instanceof com.sqlapp.data.schemas.NotNullConstraint)
			validateModifyConstraints(original, table);
		result.addAll(getSqlFactoryRegistry().createSql(constraint, SqlType.CREATE));
	}

	private void validateModifyConstraints(final Table original, final Table target) {
		// MODIFY drops single-column constraints and incoming references in Informix.
		// A table-local diff cannot safely discover/recreate external dependencies.
		if (java.util.stream.Stream.of(original, target).flatMap(t -> t.getConstraints().stream())
				.anyMatch(c -> !(c instanceof com.sqlapp.data.schemas.NotNullConstraint)))
			throw new UnsupportedOperationException("Informix MODIFY on constrained tables requires an explicit "
					+ "constraint/dependency migration plan: " + original.getName());
	}

	@Override
	protected void addDropConstraintDefinition(final Table original, final Table table, final Constraint oldConstraint,
			final Constraint constraint, final DbObjectDifference diff, final List<SqlOperation> result) {
		final var builder = createSqlBuilder();
		builder.alter().table().name(table, getOptions().isDecorateSchemaName()).drop().constraint();
		final String owner = oldConstraint.getSpecifics()
				.get(com.sqlapp.data.db.dialect.informix.Informix.OBJECT_OWNER);
		if (owner != null && getOptions().isDecorateSchemaName())
			builder.space().name(owner)._add('.').name(oldConstraint.getName());
		else
			builder.name(oldConstraint, getOptions().isDecorateSchemaName());
		add(result, createOperation(builder.toString(), SqlType.ALTER, oldConstraint));
	}

	@Override
	protected void addDropIndexDefinition(final Index index, final InformixSqlBuilder builder) {
		addDropIndexDefinition(index, builder, false);
	}

	void addDropIndexDefinition(final Index index, final InformixSqlBuilder builder, boolean ifExists) {
		builder.drop().index().ifExists(ifExists);
		final String owner = index.getSpecifics().get(com.sqlapp.data.db.dialect.informix.Informix.OBJECT_OWNER);
		if (owner != null && getOptions().isDecorateSchemaName())
			builder.space().name(owner)._add('.').name(index.getName());
		else
			builder.name(index, getOptions().isDecorateSchemaName());
	}

	@Override
	protected void addCreateIndexDefinition(final Table original, final Table table, final Index oldIndex,
			final Index index, final DbObjectDifference diff, final List<SqlOperation> result) {
		result.addAll(getSqlFactoryRegistry().createSql(index, SqlType.CREATE));
	}
}
