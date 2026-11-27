/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;

import com.sqlapp.data.db.dialect.spanner.util.SpannerSqlBuilder;
import com.sqlapp.data.db.sql.AbstractDropTableFactory;
import com.sqlapp.data.db.sql.SqlOperation;
import com.sqlapp.data.db.sql.SqlType;
import com.sqlapp.data.schemas.Index;
import com.sqlapp.data.schemas.DbObjectDifference;
import com.sqlapp.data.schemas.IndexType;
import com.sqlapp.data.schemas.Table;

/** Drops modeled user indexes before their owning GoogleSQL table. */
public class SpannerDropTableFactory extends AbstractDropTableFactory<SpannerSqlBuilder> {
	@Override
	protected List<Table> sort(final List<Table> tables) {
		return SpannerTableOrder.sort(tables).reversed();
	}

	@Override
	protected List<DbObjectDifference> sortDbObjectDifference(final List<DbObjectDifference> differences) {
		final var tables = new ArrayList<Table>();
		final var byTable = new IdentityHashMap<Table, DbObjectDifference>();
		for (final var difference : differences) {
			final var table = difference.getOriginal(Table.class);
			tables.add(table);
			byTable.put(table, difference);
		}
		return sort(tables).stream().map(byTable::get).toList();
	}

	@Override
	public List<SqlOperation> createSql(final Table table) {
		if (table.getName() == null || table.getName().isBlank())
			throw new IllegalArgumentException("Spanner DROP TABLE requires a name");
		final List<SqlOperation> result = new ArrayList<>();
		final var names = new HashSet<String>();
		for (final var index : table.getIndexes()) {
			SpannerIndexValidation.identity(index);
			if (SpannerIndexValidation.isManaged(index)) {
				names.add(index.getName().toLowerCase(Locale.ROOT));
				continue;
			}
			if (names.add(index.getName().toLowerCase(Locale.ROOT)))
				addIndexDrop(table, index.getName(), index.getIndexType(), result);
		}
		for (final var constraint : table.getConstraints().getUniqueConstraints()) {
			if (constraint.isPrimaryKey())
				continue;
			if (constraint.getName() == null || constraint.getName().isBlank())
				throw new IllegalArgumentException("Spanner UNIQUE index requires a name: " + table.getName());
			if (names.add(constraint.getName().toLowerCase(Locale.ROOT)))
				addIndexDrop(table, constraint.getName(), IndexType.BTree, result);
		}
		result.addAll(super.createSql(table));
		return result;
	}

	private void addIndexDrop(final Table table, final String name, final IndexType type,
			final List<SqlOperation> result) {
		// The owning table is being removed, so its modeled UNIQUE indexes can
		// be dropped too. Standalone index operations keep their ownership guard.
		final var index = new Index(name).setIndexType(type).setSchemaName(table.getSchemaName());
		final var owner = new Table(table.getName()).setSchemaName(table.getSchemaName());
		owner.getIndexes().add(index);
		result.addAll(getSqlFactoryRegistry().createSql(index, SqlType.DROP));
	}

	@Override
	protected void addDropObject(final Table table, final SpannerSqlBuilder builder) {
		builder.drop().table().ifExists(getOptions().isDropIfExists())
				.name(table, getOptions().isDecorateSchemaName());
	}
}
