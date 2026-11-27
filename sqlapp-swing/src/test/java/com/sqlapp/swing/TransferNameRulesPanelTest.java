/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import javax.swing.*;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.command.migration.transfer.*;

class TransferNameRulesPanelTest {
	private TransferNameMappingPanel editor() {
		var panel = new TransferNameMappingPanel();
		panel.tables.addRow(new String[] { "PUBLIC.CUSTOMER DATA", null });
		panel.columns.addRow(new String[] { "PUBLIC.CUSTOMER DATA", "Z", "LAST NAME" });
		panel.columns.addRow(new String[] { "PUBLIC.CUSTOMER DATA", "A", "FIRST NAME" });
		panel.columns.addRow(new String[] { "OTHER", "ID", "KEEP THIS" });
		return panel;
	}

	@Test
	void previewsSortedFilteredSelectionWithoutMutatingThenAppliesAndUndoesOnlyThoseRows() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var editor = editor();
			var original = editor.configuration();
			editor.columnTools.filter.setText("PUBLIC.");
			editor.columnGrid.getRowSorter().toggleSortOrder(1);
			editor.columnGrid.setRowSelectionInterval(0, 1);
			var closed = new AtomicBoolean();
			var panel = new TransferNameRulesPanel(editor.columnTools, () -> closed.set(true));
			panel.letterCase.setSelectedItem(TransferNameRules.NameCase.LOWER);
			panel.spaces.doClick(0);
			panel.preview.doClick(0);
			assertEquals(original, editor.configuration());
			assertEquals(2, panel.model.getRowCount());
			assertEquals("first_name", panel.model.getValueAt(0, 2));
			assertTrue(panel.apply.isEnabled());
			panel.apply.doClick(0);
			assertTrue(closed.get());
			assertEquals("last_name", editor.columns.getValueAt(0, 2));
			assertEquals("first_name", editor.columns.getValueAt(1, 2));
			assertEquals("KEEP THIS", editor.columns.getValueAt(2, 2));
			editor.columnTools.undoRules();
			assertEquals(original, editor.configuration());
		});
	}

	@Test
	void tableRulesUseSimpleSourceNameForBlankTargetsAndPreserveTheJobNamespace() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var editor = editor();
			editor.tableGrid.setRowSelectionInterval(0, 0);
			var changes = editor.tableTools.previewRules(new TransferNameRules(TransferNameRules.NameCase.LOWER, true));
			assertEquals("customer_data", changes.getFirst().proposedName());
			editor.tableTools.applyRules(changes);
			assertEquals("customer_data", editor.configuration().tables().get("PUBLIC.CUSTOMER DATA").targetTable());
			editor.tableTools.undoRules();
			assertNull(editor.configuration().tables().get("PUBLIC.CUSTOMER DATA").targetTable());
		});
	}

	@Test
	void stalePreviewAndUndoCannotOverwriteNewEditsOrApplyOnlyPartOfABatch() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var editor = editor();
			editor.columnGrid.setRowSelectionInterval(0, 1);
			var rule = new TransferNameRules(TransferNameRules.NameCase.LOWER, true);
			var changes = editor.columnTools.previewRules(rule);
			editor.columns.setValueAt("USER EDIT", 1, 2);
			assertThrows(IllegalArgumentException.class, () -> editor.columnTools.applyRules(changes));
			assertEquals("LAST NAME", editor.columns.getValueAt(0, 2));
			var refreshed = editor.columnTools.previewRules(rule);
			editor.columnTools.applyRules(refreshed);
			editor.columns.setValueAt("LATEST", 1, 2);
			assertThrows(IllegalArgumentException.class, editor.columnTools::undoRules);
			assertEquals("last_name", editor.columns.getValueAt(0, 2));
			assertEquals("LATEST", editor.columns.getValueAt(1, 2));
		});
	}

	@Test
	void changingRulesRequiresANewPreviewAndCancelDoesNotChangeNames() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var editor = editor();
			editor.columnGrid.setRowSelectionInterval(0, 0);
			var original = editor.configuration();
			var closed = new AtomicBoolean();
			var panel = new TransferNameRulesPanel(editor.columnTools, () -> closed.set(true));
			panel.preview.doClick(0);
			assertFalse(panel.apply.isEnabled());
			panel.letterCase.setSelectedItem(TransferNameRules.NameCase.LOWER);
			assertEquals(0, panel.model.getRowCount());
			assertFalse(panel.apply.isEnabled());
			panel.preview.doClick(0);
			assertTrue(panel.apply.isEnabled());
			panel.spaces.doClick(0);
			assertFalse(panel.apply.isEnabled());
			panel.cancel.doClick(0);
			assertTrue(closed.get());
			assertEquals(original, editor.configuration());
		});
	}

	@Test
	void applyingRulesInvalidatesYamlAndEmptySelectionsAreRejected() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var editor = editor();
			editor.previewYaml.doClick(0);
			assertTrue(editor.preview.copy.isEnabled());
			assertThrows(IllegalArgumentException.class,
					() -> editor.tableTools.previewRules(new TransferNameRules(null, false)));
			editor.tableGrid.setRowSelectionInterval(0, 0);
			editor.tableTools.applyRules(
					editor.tableTools.previewRules(new TransferNameRules(TransferNameRules.NameCase.UPPER, true)));
			assertFalse(editor.preview.copy.isEnabled());
			assertEquals("", editor.preview.text.getText());
			assertEquals("CUSTOMER_DATA", editor.configuration().tables().get("PUBLIC.CUSTOMER DATA").targetTable());
		});
	}
}
