/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.command.migration.transfer.TransferNameMappingInspectionService.*;
import com.sqlapp.data.db.command.migration.transfer.TransferNameMappingIO;

class TransferCheckedColumnNavigationTest {
	private TransferNameMappingPanel editor() {
		var editor = new TransferNameMappingPanel();
		editor.load(new TransferNameMappingIO()
				.validate("formatVersion: 1\ntables:\n  A: {columns: {ID: RENAMED}}\n  B: {columns: {ID: OTHER}}\n"));
		editor.columnTools.filter.setText("OTHER");
		editor.columnGrid.moveColumn(2, 0);
		return editor;
	}

	private Result checked() {
		return new Result(Path.of("schema.xml"), "schema", "mapping",
				List.of(new Table("PUBLIC.A", "A", List.of(new Column("ID", "RENAMED", "INT", true, false),
						new Column("NAME", "NAME", "VARCHAR", false, true)), "A")),
				List.of());
	}

	@Test
	void opensExistingRawAliasAndCorrectTargetCellDespiteBothViewsBeingReordered() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var editor = editor();
			var original = editor.configuration();
			editor.schemaCheck.showResult(checked());
			var grid = editor.schemaCheck.details.table;
			grid.getRowSorter().toggleSortOrder(1);
			grid.getRowSorter().toggleSortOrder(1);
			grid.moveColumn(1, 0);
			for (int row = 0; row < grid.getRowCount(); row++)
				if (grid.getValueAt(row, grid.convertColumnIndexToView(1)).equals("ID"))
					grid.setRowSelectionInterval(row, row);
			editor.schemaCheck.editColumn.doClick(0);
			assertEquals(1, editor.tabs.getSelectedIndex());
			assertEquals(1, editor.columnGrid.getRowCount());
			assertEquals("A", editor.columnGrid.getValueAt(0, editor.columnGrid.convertColumnIndexToView(0)));
			assertEquals(2, editor.columnGrid.convertColumnIndexToModel(editor.columnGrid.getEditingColumn()));
			editor.columnGrid.getCellEditor().stopCellEditing();
			assertEquals(original, editor.configuration());
			assertEquals(2, editor.columns.getRowCount());
		});
	}

	@Test
	void addsUnloadedColumnAsUnchangedDisplayRowAndInvalidatesStaleEvidence() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var editor = editor();
			var original = editor.configuration();
			editor.schemaCheck.showResult(checked());
			editor.schemaCheck.details.filter.setText("NAME");
			var grid = editor.schemaCheck.details.table;
			for (int row = 0; row < grid.getRowCount(); row++)
				if (grid.getValueAt(row, grid.convertColumnIndexToView(1)).equals("NAME"))
					grid.setRowSelectionInterval(row, row);
			editor.schemaCheck.editColumn.doClick(0);
			assertEquals(3, editor.columns.getRowCount());
			assertEquals("A", editor.columns.getValueAt(2, 0));
			assertEquals("VARCHAR", editor.columns.getValueAt(2, 3));
			assertEquals(0, editor.schemaCheck.model.getRowCount());
			assertFalse(editor.schemaCheck.editColumn.isEnabled());
			editor.columnGrid.getCellEditor().stopCellEditing();
			assertEquals(original, editor.configuration());
			assertTrue(editor.columnGrid.editCellAt(editor.columnGrid.convertRowIndexToView(2),
					editor.columnGrid.convertColumnIndexToView(2)));
			((JTextField) editor.columnGrid.getEditorComponent()).setText("顧客名");
			editor.columnGrid.getCellEditor().stopCellEditing();
			assertEquals("顧客名", editor.configuration().tables().get("A").columns().get("NAME"));
			assertEquals("OTHER", editor.configuration().tables().get("B").columns().get("ID"));
		});
	}
}
