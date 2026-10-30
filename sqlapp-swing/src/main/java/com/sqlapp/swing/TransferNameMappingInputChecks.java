/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.util.*;
import javax.swing.table.DefaultTableModel;
import com.sqlapp.data.db.command.migration.transfer.TransferNameMappingIO;

/** Row locations for syntax problems; database resolution remains in Review. */
final class TransferNameMappingInputChecks {
	record Issue(int tab, int row, int column, String message) {
		@Override
		public String toString() {
			return (tab == 0 ? "Tables" : "Columns") + " row " + (row + 1) + ": " + message;
		}
	}

	static java.util.List<Issue> inspect(DefaultTableModel tables, DefaultTableModel columns) {
		var issues = new ArrayList<Issue>();
		var tableRows = new HashMap<String, Integer>();
		for (int row = 0; row < tables.getRowCount(); row++) {
			String source = value(tables, row, 0), target = value(tables, row, 1);
			check(issues, 0, row, 0, "Source table", () -> tableName(source));
			check(issues, 0, row, 1, "Target table",
					() -> new TransferNameMappingIO.TableMapping(target.isBlank() ? null : target, Map.of()));
			var previous = tableRows.putIfAbsent(source, row);
			if (previous != null && !source.isBlank())
				issues.add(new Issue(0, row, 0,
						"Source table is already listed at row " + (previous + 1) + ": " + source));
		}
		var columnRows = new HashMap<String, Map<String, Integer>>();
		for (int row = 0; row < columns.getRowCount(); row++) {
			String table = value(columns, row, 0), source = value(columns, row, 1), target = value(columns, row, 2);
			check(issues, 1, row, 0, "Source table", () -> tableName(table));
			check(issues, 1, row, 1, "Source column",
					() -> new TransferNameMappingIO.TableMapping(null, Map.of(source, "valid")));
			check(issues, 1, row, 2, "Target column",
					() -> new TransferNameMappingIO.TableMapping(null, Map.of("valid", target)));
			var previous = columnRows.computeIfAbsent(table, key -> new HashMap<>()).putIfAbsent(source, row);
			if (previous != null && !table.isBlank() && !source.isBlank())
				issues.add(new Issue(1, row, 1,
						"Source column is already listed at row " + (previous + 1) + ": " + table + "." + source));
		}
		return java.util.List.copyOf(issues);
	}

	private static void tableName(String name) {
		new TransferNameMappingIO.Configuration(1,
				Map.of(name, new TransferNameMappingIO.TableMapping(null, Map.of())));
	}

	private static void check(java.util.List<Issue> issues, int tab, int row, int column, String field,
			Runnable validation) {
		try {
			validation.run();
		} catch (IllegalArgumentException failure) {
			issues.add(new Issue(tab, row, column, field + ": " + failure.getMessage()));
		}
	}

	private static String value(DefaultTableModel model, int row, int column) {
		var value = model.getValueAt(row, column);
		return value == null ? "" : value.toString();
	}
}
