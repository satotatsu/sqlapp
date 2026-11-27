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
	private void enter(JTable table) {
		var key = table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
				.get(KeyStroke.getKeyStroke(java.awt.event.KeyEvent.VK_ENTER, 0));
		table.getActionMap().get(key).actionPerformed(new java.awt.event.ActionEvent(table, 0, "enter"));
	}

	@Test
	void keyboardAndDoubleClickUseVerifiedFilteredIdentityAndRespectInvalidation() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var panel = new TransferNameMappingSchemaPanel();
			var selected = new java.util.ArrayList<TransferNameMappingSchemaPanel.ColumnSelection>();
			panel.setColumnNavigator(selected::add);
			panel.showResult(checked());
			var grid = panel.details.table;
			grid.setSize(1000, 200);
			grid.getRowSorter().toggleSortOrder(1);
			grid.moveColumn(1, 0);
			panel.details.filter.setText("VARCHAR");
			grid.setRowSelectionInterval(0, 0);
			enter(grid);
			assertEquals("A", selected.getFirst().sourceTable());
			assertEquals("NAME", selected.getFirst().sourceColumn());
			var cell = grid.getCellRect(0, 0, true);
			var click = new java.awt.event.MouseEvent(grid, java.awt.event.MouseEvent.MOUSE_CLICKED, 0, 0, cell.x + 2,
					cell.y + 2, 2, false, java.awt.event.MouseEvent.BUTTON1);
			for (var listener : grid.getMouseListeners())
				listener.mouseClicked(click);
			assertEquals(2, selected.size());
			var blank = new java.awt.event.MouseEvent(grid, java.awt.event.MouseEvent.MOUSE_CLICKED, 0, 0, 2, 150, 2,
					false, java.awt.event.MouseEvent.BUTTON1);
			for (var listener : grid.getMouseListeners())
				listener.mouseClicked(blank);
			var right = new java.awt.event.MouseEvent(grid, java.awt.event.MouseEvent.MOUSE_CLICKED, 0, 0, cell.x + 2,
					cell.y + 2, 2, false, java.awt.event.MouseEvent.BUTTON3);
			for (var listener : grid.getMouseListeners())
				listener.mouseClicked(right);
			assertEquals(2, selected.size());
			panel.refreshEnabled(false);
			enter(grid);
			for (var listener : grid.getMouseListeners())
				listener.mouseClicked(click);
			assertEquals(2, selected.size());
			panel.refreshEnabled(true);
			panel.details.filter.setText("");
			grid.setRowSelectionInterval(0, 1);
			enter(grid);
			assertEquals(2, selected.size());
			panel.changed();
			enter(grid);
			assertEquals(2, selected.size());
		});
	}

	@Test
	void readOnlyPreviewRetainsItsOriginalEnterAction() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var panel = new TransferNameMappingSchemaPanel();
			var grid = panel.details.table;
			var original = new java.util.concurrent.atomic.AtomicInteger();
			grid.getActionMap().put("selectNextRowCell", new AbstractAction() {
				@Override
				public void actionPerformed(java.awt.event.ActionEvent event) {
					original.incrementAndGet();
				}
			});
			panel.showResult(checked());
			enter(grid);
			assertEquals(1, original.get());
			assertFalse(panel.editColumn.isVisible());
		});
	}

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
