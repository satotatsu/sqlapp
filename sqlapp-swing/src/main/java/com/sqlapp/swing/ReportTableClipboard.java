/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.nio.charset.StandardCharsets;
import javax.swing.JTable;

/**
 * Tab-separated text in current view order; no model changes or partial output.
 */
final class ReportTableClipboard {
	private static final int MAX_BYTES = 1_048_576;
	private static final String TOO_LARGE = "Rows exceed the 1 MiB clipboard limit. Select or filter fewer rows; no text was copied.";

	private ReportTableClipboard() {
	}

	static String format(final JTable table) {
		return format(table, false);
	}

	static String formatSelected(final JTable table) {
		return format(table, true);
	}

	private static String format(final JTable table, final boolean selectedOnly) {
		if (table.getRowCount() == 0 || table.getColumnCount() == 0 || selectedOnly && table.getSelectedRowCount() == 0)
			return "";
		final var output = new Text();
		for (int row = -1; row < table.getRowCount(); row++) {
			if (row >= 0 && selectedOnly && !table.isRowSelected(row))
				continue;
			for (int column = 0; column < table.getColumnCount(); column++) {
				if (column > 0)
					output.append("\t");
				final Object value = row < 0 ? table.getColumnName(column) : table.getValueAt(row, column);
				output.append(quote(value == null ? "" : value.toString()));
			}
			output.append("\n");
		}
		return output.value.toString();
	}

	private static String quote(final String value) {
		if (value.length() > MAX_BYTES)
			throw new IllegalArgumentException(TOO_LARGE);
		return value.indexOf('\t') >= 0 || value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0
				|| value.indexOf('"') >= 0 ? "\"" + value.replace("\"", "\"\"") + "\"" : value;
	}

	private static final class Text {
		private final StringBuilder value = new StringBuilder();
		private int bytes;

		void append(final String text) {
			final int size = text.getBytes(StandardCharsets.UTF_8).length;
			if ((long) bytes + size > MAX_BYTES)
				throw new IllegalArgumentException(TOO_LARGE);
			value.append(text);
			bytes += size;
		}
	}
}
