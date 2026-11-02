/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.command.migration.transfer.*;
import com.sqlapp.data.db.command.migration.schema.MigrationPlan.DatabaseIdentity;

class TransferTablePreparationTabsTest {
	@Test
	void showsSharedPlannerActionsReadOnlyAndClearsStaleEvidence() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var states = java.util.Arrays.stream(TransferSchemaPreparation.TableAction.values())
					.map(action -> new TransferSchemaPreparation.TablePreparation(action.name(), null, "PUBLIC",
							"表_" + action, action))
					.toList();
			var plan = new TransferSchemaPreparation.Plan("hash", List.of(), List.of(), states);
			var rows = TransferTablePreparationTabs.rows(plan);
			assertEquals(
					List.of("Create missing table", "Use existing table for data transfer",
							"Skip existing table; no comparison", "Review/apply selected-table differences"),
					rows.stream().map(row -> row.get(4)).toList());
			assertEquals("", rows.getFirst().get(1));
			var tabs = new JTabbedPane();
			tabs.addTab("Summary", new JPanel());
			var view = new TransferTablePreparationTabs();
			var review = new TransferFileImportService.Review(Path.of("manifest.yaml"), "hash",
					new DatabaseIdentity("HSQL", "2", "hash"), "hash", plan);
			view.show(tabs, new FileTransferDesktopService.Reviewed(review, null));
			assertEquals("Table preparation (4)", tabs.getTitleAt(1));
			var panel = (ReportDetailTablePanel) tabs.getComponentAt(1);
			assertFalse(panel.table.isCellEditable(0, 4));
			panel.filter.setText("Skip existing");
			assertEquals(1, panel.table.getRowCount());
			panel.table.setRowSelectionInterval(0, 0);
			assertTrue(panel.selected.getText().contains("no comparison"));
			view.clear(tabs);
			assertEquals(1, tabs.getTabCount());
			view.show(tabs, new Object());
			assertEquals(1, tabs.getTabCount());
		});
	}
}
