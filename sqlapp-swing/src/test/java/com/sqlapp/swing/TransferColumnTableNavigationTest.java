/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.command.migration.transfer.TransferNameMappingIO;

class TransferColumnTableNavigationTest {
	private TransferNameMappingPanel panel() {
		var panel = new TransferNameMappingPanel();
		panel.load(new TransferNameMappingIO().validate("""
				formatVersion: 1
				tables:
				  PUBLIC.A: {columns: {ID: KEY}}
				  PUBLIC.AA: {columns: {ID: OTHER, NOTE: NOTE}}
				"""));
		return panel;
	}

	@Test
	void scopesByExactIdentityAndCombinesSearchWithoutChangingConfiguration() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var panel = panel();
			var original = panel.configuration();
			panel.columnTools.filter.setText("missing");
			panel.showColumns("PUBLIC.A");
			assertEquals(1, panel.tabs.getSelectedIndex());
			assertEquals(1, panel.columnGrid.getRowCount());
			assertEquals("PUBLIC.A", panel.columnGrid.getValueAt(0, 0));
			panel.columnTools.filter.setText("OTHER");
			assertEquals(0, panel.columnGrid.getRowCount());
			panel.columnTools.clear.doClick(0);
			assertEquals(1, panel.columnGrid.getRowCount());
			assertEquals(original, panel.configuration());
			panel.columnTable.setSelectedIndex(0);
			assertEquals(3, panel.columnGrid.getRowCount());
		});
	}

	@Test
	void scopedAdditionInheritsTableAndRemovingOverrideKeepsOtherTables() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var panel = panel();
			panel.showColumns("PUBLIC.A");
			panel.columnTools.add.doClick(0);
			assertEquals("PUBLIC.A", panel.columns.getValueAt(3, 0));
			assertEquals(2, panel.columnGrid.getRowCount());
			panel.columnGrid.getCellEditor().cancelCellEditing();
			panel.columnTools.remove.doClick(0);
			assertEquals(3, panel.columns.getRowCount());
			assertEquals("OTHER", panel.configuration().tables().get("PUBLIC.AA").columns().get("ID"));
		});
	}

	@Test
	void supportsARealTableNamedAllTablesAndCommitsSortedCellEdits() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var panel = panel();
			panel.tables.addRow(new String[] { "All tables", null });
			panel.columns.addRow(new String[] { "All tables", "ID", "LOCAL" });
			panel.showColumns("All tables");
			assertTrue(panel.columnTable.getSelectedIndex() > 0);
			assertEquals(1, panel.columnGrid.getRowCount());
			panel.columnGrid.getRowSorter().toggleSortOrder(2);
			assertTrue(panel.columnGrid.editCellAt(0, 2));
			((JTextField) panel.columnGrid.getEditorComponent()).setText("CHANGED");
			panel.showColumns("PUBLIC.A");
			assertEquals("CHANGED", panel.configuration().tables().get("All tables").columns().get("ID"));
			assertEquals("PUBLIC.A", panel.columnGrid.getValueAt(0, 0));
		});
	}

	@Test
	void issueNavigationEscapesScopeAndLastGroupRemovalRestoresAllTables() throws Exception {
		var holder = new TransferNameMappingPanel[1];
		SwingUtilities.invokeAndWait(() -> {
			var panel = panel();
			holder[0] = panel;
			panel.showColumns("PUBLIC.A");
			panel.columns.setValueAt("", 1, 2);
			panel.checkNames.doClick(0);
			panel.goToIssue.doClick(0);
			assertEquals(0, panel.columnTable.getSelectedIndex());
			assertEquals(1, panel.columnGrid.convertRowIndexToModel(panel.columnGrid.getSelectedRow()));
			panel.columnGrid.getCellEditor().cancelCellEditing();
			panel.showColumns("PUBLIC.A");
			panel.columns.removeRow(0);
			panel.tables.removeRow(0);
		});
		SwingUtilities.invokeAndWait(() -> {
			var panel = holder[0];
			assertEquals(0, panel.columnTable.getSelectedIndex());
			assertEquals(2, panel.columnGrid.getRowCount());
		});
	}
}
