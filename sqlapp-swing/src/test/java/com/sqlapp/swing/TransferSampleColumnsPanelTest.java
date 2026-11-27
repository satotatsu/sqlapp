/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.Path;
import java.util.List;
import javax.swing.*;
import org.junit.jupiter.api.Test;
import com.sqlapp.data.db.command.migration.transfer.*;

class TransferSampleColumnsPanelTest {
	@Test
	void chooserReplacesPreviousSearchTargetSoDetailsFollowTheColumnActuallyRevealed() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var sample = new JTable(new Object[][] { { "1", "id", "name" } },
					new Object[] { "Sample row", "Z_ID / INT", "A_NAME / TEXT" });
			var inspected = new java.util.concurrent.atomic.AtomicInteger(-1);
			var search = new TransferSampleColumnSearch(sample, inspected::set);
			search.query.setText("Z_ID");
			search.next.doClick(0);
			search.details.doClick(0);
			assertEquals(1, inspected.get());
			sample.moveColumn(2, 0);
			var chooser = new TransferSampleColumnsPanel(preview(), null, search::revealColumn);
			chooser.panel.table.getRowSorter().toggleSortOrder(1);
			chooser.panel.table.setRowSelectionInterval(0, 0);
			chooser.go.doClick(0);
			assertEquals("", search.query.getText());
			assertEquals("", search.matches.getText());
			assertEquals("Column 1 of 3", search.status.getText());
			assertTrue(search.status.getToolTipText().contains("A_NAME"));
			search.details.doClick(0);
			assertEquals(2, inspected.get());
			assertEquals("name", sample.getValueAt(0, 0));
		});
	}

	private TransferFileImportService.Preview preview() {
		var table = new TransferFilePackageIO.TableFile("task", null, "PUBLIC", "ITEMS", List.of("Z_ID", "A_NAME"),
				"table-1.csv", 0, 0, "sha256:" + "a".repeat(64));
		return new TransferFileImportService.Preview(
				new TransferFilePackageIO.Snapshot(Path.of("missing.json"), "hash", null), table,
				List.of("INT", "TEXT"), List.of(), 20, false);
	}

	@Test
	void sortedFilteredAndReorderedListNavigatesByExactSourceColumnIdentity() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var mapped = new TransferFileImportService.PackageTable("task", null, "PUBLIC", "ITEMS", "COPY",
					"table-1.csv", 0, false, false,
					List.of(new TransferFileImportService.PackageColumn("Z_ID", "TARGET_ID", "INTEGER", true, true),
							new TransferFileImportService.PackageColumn("A_NAME", "TARGET_NAME", "VARCHAR", false,
									false)));
			var selected = new java.util.concurrent.atomic.AtomicInteger(-1);
			var view = new TransferSampleColumnsPanel(preview(), mapped, selected::set);
			assertFalse(view.go.isEnabled());
			assertEquals("TARGET_ID", view.panel.table.getModel().getValueAt(0, 2));
			assertEquals("true", view.panel.table.getModel().getValueAt(0, 6));
			view.panel.table.getRowSorter().toggleSortOrder(1);
			view.panel.table.moveColumn(0, 4);
			view.panel.table.setRowSelectionInterval(0, 0);
			view.go.doClick(0);
			assertEquals(2, selected.get());
			view.panel.filter.setText("TARGET_ID");
			view.panel.table.setRowSelectionInterval(0, 0);
			view.go.doClick(0);
			assertEquals(1, selected.get());
			assertFalse(view.panel.table.isCellEditable(0, 0));
		});
	}

	@Test
	void standaloneListShowsUnknownMetadataAndCannotNavigateWithoutSelectionOrWhileDisabled() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var selected = new java.util.concurrent.atomic.AtomicInteger(-1);
			var view = new TransferSampleColumnsPanel(preview(), null, selected::set);
			assertEquals(2, view.panel.table.getRowCount());
			assertEquals("not recorded", view.panel.table.getModel().getValueAt(0, 2));
			assertEquals("not recorded", view.panel.table.getModel().getValueAt(0, 6));
			view.go.doClick(0);
			assertEquals(-1, selected.get());
			view.panel.table.setRowSelectionInterval(0, 0);
			view.panel.refreshEnabled(false);
			view.go.doClick(0);
			assertEquals(-1, selected.get());
		});
	}
}
