/* Copyright (C) 2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.data.db.dialect.spanner.sql;

import java.util.*;
import com.sqlapp.data.schemas.Table;

/**
 * Orders an interleaved CREATE batch using modeled parent and foreign-key
 * identities.
 */
final class SpannerTableOrder {
	private SpannerTableOrder() {
	}

	private record Identity(String catalog, String schema, String name) {
	}

	private static String normalized(String value) {
		return value == null ? "" : value.toLowerCase(Locale.ROOT);
	}

	private static Identity identity(String catalog, String schema, String name) {
		return new Identity(normalized(catalog), normalized(schema), normalized(name));
	}

	static List<Table> sort(List<Table> tables) {
		final Map<Identity, Table> identities = new HashMap<>();
		for (var table : tables) {
			SpannerCreateTableFactory.interleaveType(table);
			if (identities.putIfAbsent(identity(table.getCatalogName(), table.getSchemaName(), table.getName()),
					table) != null)
				throw new IllegalArgumentException("Duplicate Spanner CREATE table identity: " + table.getName());
		}
		final var state = new IdentityHashMap<Table, Integer>();
		final var result = new ArrayList<Table>();
		for (var table : tables)
			visit(table, identities, state, result);
		return result;
	}

	private static void visit(Table table, Map<Identity, Table> identities, Map<Table, Integer> state,
			List<Table> result) {
		final Integer previous = state.get(table);
		if (previous != null) {
			if (previous == 1)
				throw new IllegalArgumentException(
						"Cyclic Spanner interleave/foreign-key CREATE dependencies: " + table.getName());
			return;
		}
		state.put(table, 1);
		final String parentName = table.getSpecifics().get(SpannerCreateTableFactory.PARENT_TABLE_NAME);
		if (parentName != null) {
			final var parent = identities.get(identity(table.getCatalogName(), table.getSchemaName(), parentName));
			if (parent != null)
				visit(parent, identities, state, result);
		}
		for (var key : table.getConstraints().getForeignKeyConstraints()) {
			final var related = key.getRelatedTable();
			final var dependency = related != null
					? identities.get(identity(related.getCatalogName(), related.getSchemaName(), related.getName()))
					: identities.get(identity(table.getCatalogName(),
							key.getRelatedTableSchemaName() == null ? table.getSchemaName()
									: key.getRelatedTableSchemaName(),
							key.getRelatedTableName()));
			if (dependency != null && dependency != table)
				visit(dependency, identities, state, result);
		}
		state.put(table, 2);
		result.add(table);
	}
}
