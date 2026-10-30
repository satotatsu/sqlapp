/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;

class TransferNameMappingInputChecksTest {
	@Test
	void reportsAllInputProblemsUsingSharedNameRulesBeforeSaving() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var panel = new TransferNameMappingPanel();
			panel.tables.addRow(new String[] { "A", "ARCHIVE" });
			panel.tables.addRow(new String[] { "A", "schema.table" });
			panel.columns.addRow(new String[] { "A", "", "" });
			panel.columns.addRow(new String[] { "A", "C", "D" });
			panel.columns.addRow(new String[] { "A", "C", "E" });
			panel.checkNames.doClick(0);
			assertEquals(5, panel.issues.size());
			assertEquals(2, panel.tabs.getSelectedIndex());
			assertTrue(panel.checkStatus.getText().contains("5 input problems"));
			assertThrows(IllegalArgumentException.class, panel::configuration);
			assertEquals(5, panel.issues.size());
			assertTrue(panel.issues.get(1).message().contains("already listed at row 1"));
		});
	}

	@Test
	void navigatesToTheCorrectFieldDespiteSortingFilteringAndColumnReordering() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var panel = new TransferNameMappingPanel();
			panel.tables.addRow(new String[] { "A", null });
			panel.columns.addRow(new String[] { "A", "Z", "RENAMED" });
			panel.columns.addRow(new String[] { "A", "B", "" });
			panel.columnGrid.getRowSorter().toggleSortOrder(1);
			panel.columnGrid.moveColumn(2, 0);
			panel.columnTools.filter.setText("Z");
			panel.columnTools.renamedOnly.doClick(0);
			panel.checkNames.doClick(0);
			assertEquals(1, panel.issues.size());
			panel.goToIssue.doClick(0);
			assertEquals(1, panel.tabs.getSelectedIndex());
			assertEquals("", panel.columnTools.filter.getText());
			assertFalse(panel.columnTools.renamedOnly.isSelected());
			assertEquals(1, panel.columnGrid.convertRowIndexToModel(panel.columnGrid.getSelectedRow()));
			assertEquals(2, panel.columnGrid.convertColumnIndexToModel(panel.columnGrid.getSelectedColumn()));
			assertTrue(panel.columnGrid.isEditing());
			((JTextField) panel.columnGrid.getEditorComponent()).setText("CORRECTED");
			panel.columnGrid.getCellEditor().stopCellEditing();
			assertEquals(0, panel.issues.size());
			assertTrue(panel.checkStatus.getText().contains("Names changed"));
			assertEquals("CORRECTED", panel.configuration().tables().get("A").columns().get("B"));
		});
	}

	@Test
	void doesNotKeepRowLocationsAfterRemovalAndDoesNotAuthorizeDatabaseExecution() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var panel = new TransferNameMappingPanel();
			panel.tables.addRow(new String[] { "A", "one.two" });
			panel.checkNames.doClick(0);
			assertEquals(1, panel.issues.size());
			panel.tables.removeRow(0);
			assertEquals(0, panel.issues.size());
			assertFalse(panel.goToIssue.isEnabled());
			panel.goToIssue.doClick(0);
			panel.tables.addRow(new String[] { "不明なテーブル", "同じ名前" });
			panel.tables.addRow(new String[] { "別のテーブル", "同じ名前" });
			panel.checkNames.doClick(0);
			assertEquals(0, panel.issues.size());
			assertEquals("No input problems found. Database review is still required.", panel.checkStatus.getText());
			assertEquals(2, panel.configuration().tables().size());
		});
	}

	@Test
	void checksTheLatestCellValueAndRejectsControlCharactersAndMalformedUnicode() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var panel = new TransferNameMappingPanel();
			panel.tables.addRow(new String[] { "A", "VALID" });
			assertTrue(panel.tableGrid.editCellAt(0, 1));
			((JTextField) panel.tableGrid.getEditorComponent()).setText("schema.table");
			panel.columns.addRow(new String[] { "A", "BAD\nNAME", "\uD800" });
			panel.checkNames.doClick(0);
			assertFalse(panel.tableGrid.isEditing());
			assertEquals(3, panel.issues.size());
			assertThrows(IllegalArgumentException.class, panel::configuration);
		});
	}
}
