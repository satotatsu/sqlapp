/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import javax.swing.JTable;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import org.junit.jupiter.api.Test;

class ReportTableClipboardTest {
	@Test
	void selectedRowsUseVisibleOrderAndExcludeUnselectedAndFilteredRows() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var table = new JTable(new DefaultTableModel(
					new Object[][] { { "z", "last" }, { "b", "skip" }, { "a", "tab\t\"quoted\"" }, { "c", null } },
					new Object[] { "Task", "Note" }));
			table.setAutoCreateRowSorter(true);
			table.getRowSorter().toggleSortOrder(0);
			table.moveColumn(1, 0);
			assertEquals("", ReportTableClipboard.formatSelected(table));
			table.addRowSelectionInterval(3, 3);
			table.addRowSelectionInterval(0, 0);
			assertEquals("Note\tTask\n\"tab\t\"\"quoted\"\"\"\ta\nlast\tz\n",
					ReportTableClipboard.formatSelected(table));
			assertEquals(4, table.getModel().getRowCount());
		});
	}

	@Test
	void formatsCurrentRowAndColumnOrderWithQuotedSpecialValuesAndNulls() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var table = new JTable(
					new DefaultTableModel(new Object[][] { { "z", "tab\tline\r\n\"quoted\"" }, { "a", null } },
							new Object[] { "Task", "Note" }));
			table.setAutoCreateRowSorter(true);
			table.getRowSorter().toggleSortOrder(0);
			table.moveColumn(1, 0);
			assertEquals("Note\tTask\n\ta\n\"tab\tline\r\n\"\"quoted\"\"\"\tz\n", ReportTableClipboard.format(table));
			assertEquals(2, table.getModel().getRowCount());
			assertEquals("z", table.getModel().getValueAt(0, 0));
		});
	}

	@Test
	void emptyTablesHaveNoOutputAndUtf8LimitRejectsCompleteExport() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			assertEquals("", ReportTableClipboard.format(new JTable()));
			final var table = new JTable(
					new DefaultTableModel(new Object[][] { { "あ".repeat(350_000) } }, new Object[] { "Text" }));
			final var error = assertThrows(IllegalArgumentException.class, () -> ReportTableClipboard.format(table));
			assertTrue(error.getMessage().contains("1 MiB"));
			assertTrue(error.getMessage().contains("no text was copied"));
			final var boundary = new JTable(
					new DefaultTableModel(new Object[][] { { "x".repeat(1_048_576 - 3) } }, new Object[] { "H" }));
			assertEquals(1_048_576, ReportTableClipboard.format(boundary).length());
			boundary.setValueAt("x".repeat(1_048_576 - 2), 0, 0);
			assertThrows(IllegalArgumentException.class, () -> ReportTableClipboard.format(boundary));
		});
	}
}
