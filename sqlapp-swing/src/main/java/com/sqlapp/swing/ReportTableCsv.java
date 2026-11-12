/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.nio.charset.StandardCharsets;
import javax.swing.JTable;

/** Bounded CSV snapshot of a report table's current view, including headers. */
final class ReportTableCsv {
	static final int MAX_BYTES = 16 * 1024 * 1024;
	private ReportTableCsv() {
	}

	static String format(JTable table) {
		var text = new StringBuilder();
		long bytes = 0;
		for (int row = -1; row < table.getRowCount(); row++) {
			for (int column = 0; column < table.getColumnCount(); column++) {
				Object value = row < 0 ? table.getColumnName(column) : table.getValueAt(row, column);
				String raw = value == null ? "" : value.toString();
				if (raw.length() > MAX_BYTES) throw tooLarge();
				String field = "\"" + raw.replace("\"", "\"\"") + "\"";
				bytes += field.getBytes(StandardCharsets.UTF_8).length + (column == 0 ? 0 : 1);
				if (bytes > MAX_BYTES) throw tooLarge();
				if (column > 0) text.append(',');
				text.append(field);
			}
			bytes += 2;
			if (bytes > MAX_BYTES) throw tooLarge();
			text.append("\r\n");
		}
		return text.toString();
	}

	private static IllegalArgumentException tooLarge() {
		return new IllegalArgumentException("Visible rows exceed the 16 MiB CSV limit. Filter fewer rows and save again.");
	}
}
