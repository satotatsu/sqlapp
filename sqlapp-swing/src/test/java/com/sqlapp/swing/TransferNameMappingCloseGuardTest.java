/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.command.migration.bulk.BulkMigrationJobDraftBuilder.SourceColumn;

class TransferNameMappingCloseGuardTest {
	@Test
	void filtersSortingPreviewAndMetadataEnrichmentDoNotRequireDiscarding() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var panel = new TransferNameMappingPanel();
			panel.tables.addRow(new String[] { "A", "ARCHIVE" });
			var prompts = new AtomicInteger();
			var guard = new TransferNameMappingCloseGuard(panel::configuration, () -> {
				prompts.incrementAndGet();
				return false;
			});
			guard.markClean();
			panel.includeColumns(java.util.Map.of("A", java.util.List.of(new SourceColumn("ID", "INT"))));
			panel.columnTools.filter.setText("missing");
			panel.columnGrid.getRowSorter().toggleSortOrder(1);
			panel.previewYaml.doClick(0);
			panel.checkNames.doClick(0);
			assertTrue(guard.canClose());
			assertEquals(0, prompts.get());
		});
	}

	@Test
	void keepEditingPreservesChangesAndRevertingTheNameReturnsToCleanState() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var panel = new TransferNameMappingPanel();
			panel.tables.addRow(new String[] { "A", "ORIGINAL" });
			var prompts = new AtomicInteger();
			var guard = new TransferNameMappingCloseGuard(panel::configuration, () -> {
				prompts.incrementAndGet();
				return false;
			});
			guard.markClean();
			panel.tables.setValueAt("EDITED", 0, 1);
			assertFalse(guard.canClose());
			assertEquals("EDITED", panel.tables.getValueAt(0, 1));
			panel.tables.setValueAt("ORIGINAL", 0, 1);
			assertTrue(guard.canClose());
			assertEquals(1, prompts.get());
		});
	}

	@Test
	void explicitDiscardAllowsClosingEvenForInvalidRowsAndDoesNotAlterTheModel() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var panel = new TransferNameMappingPanel();
			panel.tables.addRow(new String[] { "A", null });
			var prompts = new AtomicInteger();
			var guard = new TransferNameMappingCloseGuard(panel::configuration, () -> {
				prompts.incrementAndGet();
				return true;
			});
			guard.markClean();
			panel.columns.addRow(new String[] { "A", "", "" });
			assertTrue(guard.canClose());
			assertEquals(1, prompts.get());
			assertEquals(1, panel.columns.getRowCount());
		});
	}

	@Test
	void pendingCellEditIsCommittedAndProtectedBeforeClosing() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var panel = new TransferNameMappingPanel();
			panel.tables.addRow(new String[] { "A", "ORIGINAL" });
			var guard = new TransferNameMappingCloseGuard(panel::configuration, () -> false);
			guard.markClean();
			assertTrue(panel.tableGrid.editCellAt(0, 1));
			((JTextField) panel.tableGrid.getEditorComponent()).setText("LATEST");
			assertFalse(guard.canClose());
			assertEquals("LATEST", panel.tables.getValueAt(0, 1));
		});
	}

	@Test
	void failedInitialReadDoesNotTrapTheDialogAndNewMappingsProtectSubsequentEdits() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var panel = new TransferNameMappingPanel();
			var guard = new TransferNameMappingCloseGuard(panel::configuration, () -> false);
			assertTrue(guard.canClose());
			panel.tables.addRow(new String[] { "A", null });
			guard.markClean();
			assertTrue(guard.canClose());
			panel.tables.setValueAt("RENAMED", 0, 1);
			assertFalse(guard.canClose());
		});
	}
}
