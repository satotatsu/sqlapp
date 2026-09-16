/*
 * Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com>
 *
 * This file is part of sqlapp-command.
 */
package com.sqlapp.data.db.command.migration;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import com.sqlapp.data.schemas.Column;
import com.sqlapp.data.schemas.Schema;
import com.sqlapp.data.schemas.Table;
import com.sqlapp.data.schemas.migration.MigrationDataTest;

/** Infers migration invariants from the canonical Schema model. */
public final class MigrationDataTestPlanner {

	private MigrationDataTestPlanner() {
	}

	public static List<MigrationDataTest> infer(final Schema schema) {
		return infer(schema.getTables());
	}

	public static List<MigrationDataTest> infer(final Collection<Table> tables) {
		final List<MigrationDataTest> tests = new ArrayList<>();
		for (final Table table : tables) {
			for (final Column column : table.getColumns()) {
				if (column.isNotNull()) {
					tests.add(test(id(table, "not-null", List.of(column.getName())), MigrationDataTest.Type.NOT_NULL,
							table, List.of(column.getName())));
				}
			}
			if (table.getPrimaryKeyConstraint() != null && !table.getPrimaryKeyConstraint().getColumns().isEmpty()) {
				final List<String> columns = table.getPrimaryKeyConstraint().getColumns().toColumns().stream()
						.map(Column::getName).toList();
				tests.add(test(id(table, "unique", columns), MigrationDataTest.Type.UNIQUE, table, columns));
			}
		}
		return List.copyOf(tests);
	}

	private static MigrationDataTest test(final String id, final MigrationDataTest.Type type, final Table table,
			final List<String> columns) {
		return new MigrationDataTest(id, type, MigrationDataTest.Severity.ERROR, table.getCatalogName(),
				table.getSchemaName(), table.getName(), columns, null, Map.of(), 0, 0, 20);
	}

	private static String id(final Table table, final String type, final List<String> columns) {
		return table.getName() + ":" + type + ":" + String.join(",", columns);
	}
}
