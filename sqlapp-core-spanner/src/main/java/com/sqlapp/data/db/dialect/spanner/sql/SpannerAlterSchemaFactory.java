/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.List;

import com.sqlapp.data.db.sql.AlterSchemaFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.DbObjectDifferenceCollection;
import com.sqlapp.data.schemas.SchemaObjectProperties;
import com.sqlapp.data.schemas.State;
import com.sqlapp.data.schemas.Table;

/** Orders modeled table dependencies and synonyms in schema migrations. */
public class SpannerAlterSchemaFactory extends AlterSchemaFactory {
	@Override
	public List<SqlOperation> createDiffSql(final DbObjectDifference difference) {
		final var changes = difference.toDifference().getChangedProperties(getDialect());
		final var synonyms = (DbObjectDifferenceCollection) changes.get(SchemaObjectProperties.SYNONYMS.getLabel());
		if (synonyms == null)
			return super.createDiffSql(difference);
		final List<SqlOperation> drops = new ArrayList<>();
		final List<SqlOperation> adds = new ArrayList<>();
		// Validate all alias changes before composing the table operations.
		for (final var synonym : synonyms.getList()) {
			for (final var operation : getSqlFactoryRegistry().createSql(synonym)) {
				if (operation.getSqlType() == SqlType.DROP)
					drops.add(operation);
				else if (operation.getSqlType() == SqlType.CREATE)
					adds.add(operation);
				else
					throw new IllegalStateException("Unexpected Spanner synonym operation: " + operation.getSqlType());
			}
		}
		final List<SqlOperation> result = new ArrayList<>(drops);
		result.addAll(super.createDiffSql(difference));
		result.addAll(adds);
		return result;
	}

	@Override
	protected void addAlterSchemaObjects(final String propertyName,
			final DbObjectDifferenceCollection collectionDiff, final List<SqlOperation> sqlList) {
		if (!SchemaObjectProperties.SYNONYMS.getLabel().equals(propertyName))
			super.addAlterSchemaObjects(propertyName, collectionDiff, sqlList);
	}

	@Override
	protected void addAlterTables(final DbObjectDifferenceCollection differences, final List<SqlOperation> operations) {
		final boolean dependent = differences.getList().stream().anyMatch(difference ->
				hasTableDependencies(difference.getOriginal()) || hasTableDependencies(difference.getTarget()));
		if (!dependent) {
			super.addAlterTables(differences, operations);
			return;
		}
		final List<SqlOperation> deferred = new ArrayList<>();
		addOrderedTables(differences, State.Deleted, operations, deferred);
		addOrderedTables(differences, State.Added, operations, deferred);
		for (final var difference : differences.getList(State.Modified))
			operations.addAll(getSqlFactoryRegistry().createSql(difference));
		operations.addAll(deferred);
	}

	private boolean hasTableDependencies(final Object object) {
		return object instanceof Table table
				&& (table.getSpecifics().get(SpannerCreateTableFactory.PARENT_TABLE_NAME) != null
						|| !table.getConstraints().getForeignKeyConstraints().isEmpty());
	}

	private void addOrderedTables(final DbObjectDifferenceCollection differences, final State state,
			final List<SqlOperation> operations, final List<SqlOperation> deferred) {
		final var tables = new ArrayList<Table>();
		final var byTable = new IdentityHashMap<Table, DbObjectDifference>();
		for (final var difference : differences.getList(state)) {
			final var table = state == State.Deleted ? difference.getOriginal(Table.class) : difference.getTarget(Table.class);
			tables.add(table);
			byTable.put(table, difference);
		}
		List<Table> sorted;
		boolean staged = false;
		try {
			sorted = SpannerTableOrder.sort(tables);
		} catch (SpannerTableOrder.CyclicDependencyException exception) {
			// Pure INTERLEAVE cycles still fail here. FK cycles can be staged.
			sorted = SpannerTableOrder.sort(tables, false);
			staged = true;
		}
		if (staged && state == State.Deleted) {
			for (final var table : sorted)
				operations.addAll(getSqlFactoryRegistry().createSql(table.diff(withoutForeignKeys(table)), SqlType.ALTER));
		}
		for (final var table : state == State.Deleted ? sorted.reversed() : sorted) {
			if (staged && state == State.Added) {
				operations.addAll(getSqlFactoryRegistry().createSql(withoutForeignKeys(table), SqlType.CREATE));
				for (final var key : table.getConstraints().getForeignKeyConstraints())
					deferred.addAll(getSqlFactoryRegistry().createSql(key, SqlType.CREATE));
			} else {
				operations.addAll(getSqlFactoryRegistry().createSql(byTable.get(table)));
			}
		}
	}

	private Table withoutForeignKeys(final Table table) {
		final var projection = table.clone();
		for (final var key : new ArrayList<>(projection.getConstraints().getForeignKeyConstraints()))
			projection.getConstraints().remove(key);
		return projection;
	}
}
