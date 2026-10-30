/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.command.migration.transfer.TransferNameMappingIO;

class TransferNameMappingGridTest {
	private TransferNameMappingPanel panel() {
		var panel = new TransferNameMappingPanel();
		panel.load(new TransferNameMappingIO().validate("""
				formatVersion: 1
				tables:
				  PUBLIC.A: {targetTable: ARCHIVE, columns: {Z: LAST, A: FIRST, 'B[1]': 'B[1]'}}
				  PUBLIC.B: {columns: {ID: KEY}}
				"""));
		return panel;
	}

	@Test
	void searchesLiterallyAcrossNamesAndTypesWithoutChangingSavedOverrides() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var panel = panel();
			var original = panel.configuration();
			panel.columnTools.filter.setText("b[1]");
			assertEquals(1, panel.columnGrid.getRowCount());
			assertEquals("B[1]", panel.columnGrid.getValueAt(0, 1));
			assertEquals(original, panel.configuration());
			panel.columnTools.renamedOnly.doClick(0);
			assertEquals(0, panel.columnGrid.getRowCount());
			panel.columnTools.clear.doClick(0);
			assertEquals(4, panel.columnGrid.getRowCount());
			panel.columns.setValueAt("VARCHAR", 2, 3);
			panel.columnTools.filter.setText("varchar");
			assertEquals(1, panel.columnGrid.getRowCount());
			assertEquals(original, panel.configuration());
			panel.columnTools.clear.doClick(0);
			panel.columnTools.renamedOnly.doClick(0);
			assertEquals(3, panel.columnGrid.getRowCount());
			assertEquals("3 / 4 shown; 3 renamed", panel.columnTools.count.getText());
		});
	}

	@Test
	void resetsAllSelectedModelRowsEvenAsRenamedFilterHidesThem() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var panel = panel();
			panel.columnTools.filter.setText("PUBLIC.A");
			panel.columnTools.renamedOnly.doClick(0);
			panel.columnGrid.getRowSorter().toggleSortOrder(1);
			assertEquals("A", panel.columnGrid.getValueAt(0, 1));
			panel.columnGrid.setRowSelectionInterval(0, 1);
			panel.columnTools.reset.doClick(0);
			assertEquals(0, panel.columnGrid.getRowCount());
			var configuration = panel.configuration();
			assertTrue(configuration.tables().get("PUBLIC.A").columns().isEmpty());
			assertEquals("ARCHIVE", configuration.tables().get("PUBLIC.A").targetTable());
			assertEquals("KEY", configuration.tables().get("PUBLIC.B").columns().get("ID"));
			assertEquals("0 / 4 shown; 1 renamed", panel.columnTools.count.getText());
		});
	}

	@Test
	void removingSortedFilteredRowsPreservesHiddenRowsAndSavedJobSelection() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var panel = panel();
			panel.columnTools.filter.setText("PUBLIC.A");
			panel.columnGrid.getRowSorter().toggleSortOrder(1);
			panel.columnGrid.setRowSelectionInterval(0, 1); // A and B[1], not underlying Z and A
			panel.columnTools.remove.doClick(0);
			var configuration = panel.configuration();
			assertEquals(java.util.Map.of("Z", "LAST"), configuration.tables().get("PUBLIC.A").columns());
			assertEquals("KEY", configuration.tables().get("PUBLIC.B").columns().get("ID"));
			panel.tableTools.filter.setText("PUBLIC.A");
			panel.tableGrid.setRowSelectionInterval(0, 0);
			panel.tableTools.reset.doClick(0);
			assertNull(panel.configuration().tables().get("PUBLIC.A").targetTable());
			assertEquals(2, panel.configuration().tables().size());
		});
	}

	@Test
	void addingRowClearsFiltersAndRetainsAnActiveEditBeforeChangingSearch() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var panel = panel();
			assertTrue(panel.columnGrid.editCellAt(0, 2));
			((JTextField) panel.columnGrid.getEditorComponent()).setText("EDITED");
			panel.columnTools.filter.setText("missing");
			assertEquals("EDITED", panel.columns.getValueAt(0, 2));
			assertEquals(0, panel.columnGrid.getRowCount());
			panel.columnTools.renamedOnly.doClick(0);
			panel.columnTools.add.doClick(0);
			assertEquals("", panel.columnTools.filter.getText());
			assertFalse(panel.columnTools.renamedOnly.isSelected());
			assertEquals(5, panel.columnGrid.getRowCount());
			assertEquals(4, panel.columnGrid.getSelectedRow());
			assertTrue(panel.columnGrid.isEditing());
			panel.columnGrid.getCellEditor().cancelCellEditing();
		});
	}
}
