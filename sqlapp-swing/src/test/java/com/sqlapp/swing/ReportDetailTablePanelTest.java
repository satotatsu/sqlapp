/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

class ReportDetailTablePanelTest {
	private static ReportReviewService.DetailTable evidence() {
		return new ReportReviewService.DetailTable("Mismatch chunks", "3 of 5; omitted: 2",
				List.of("Task", "Chunk index", "Expected rows", "Actual rows", "Hash", "Key"),
				List.of(List.of("customers", "12", "20", "3", "a".repeat(128), "[21].*"),
						List.of("orders", "2", "3", "20", "HASH-B", "[5]"),
						List.of("orders", "100", "100", "100", "HASH-C", "[99]")),
				List.of(1, 2, 3));
	}

	@Test
	void filtersLiterallyAcrossAllFieldsAndReportsRecordedCounts() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new ReportDetailTablePanel(evidence());
			assertEquals("3 / 3 recorded rows", panel.count.getText());
			assertFalse(panel.clear.isEnabled());
			panel.filter.setText("OrDeRs");
			assertEquals(2, panel.table.getRowCount());
			assertEquals("2 / 3 recorded rows", panel.count.getText());
			panel.filter.setText("[21].*");
			assertEquals(1, panel.table.getRowCount());
			assertEquals("customers", panel.table.getValueAt(0, 0));
			panel.filter.setText("hash-b");
			assertEquals(1, panel.table.getRowCount());
			assertEquals("2", panel.table.getValueAt(0, 1));
			panel.filter.setText("[");
			assertEquals(3, panel.table.getRowCount());
			panel.filter.setText("absent");
			assertEquals("0 / 3 recorded rows", panel.count.getText());
			panel.clear.doClick();
			assertEquals(3, panel.table.getRowCount());
			assertEquals(3, panel.table.getModel().getRowCount());
			assertFalse(panel.clear.isEnabled());
		});
	}

	@Test
	void sortsNumbersAndShowsFullModelRowAfterSortingAndFiltering() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new ReportDetailTablePanel(evidence());
			panel.table.getRowSorter().toggleSortOrder(1);
			assertEquals("2", panel.table.getValueAt(0, 1));
			assertEquals("12", panel.table.getValueAt(1, 1));
			assertEquals("100", panel.table.getValueAt(2, 1));
			panel.table.setRowSelectionInterval(1, 1);
			assertTrue(panel.selected.getText().contains("Task: customers"));
			assertTrue(panel.selected.getText().contains("Hash: " + "a".repeat(128)));
			assertTrue(panel.selected.getText().contains("Key: [21].*"));
			assertFalse(panel.selected.isEditable());
			panel.table.getRowSorter().toggleSortOrder(2);
			assertTrue(panel.selected.getText().contains("Task: customers"));
			panel.filter.setText("orders");
			assertEquals(-1, panel.table.getSelectedRow());
			assertFalse(panel.selected.getText().contains("customers"));
			panel.table.setRowSelectionInterval(1, 1);
			assertTrue(panel.selected.getText().contains("Chunk index: 100"));
			panel.filter.setText("no match");
			assertFalse(panel.selected.getText().contains("HASH-C"));
		});
	}

	@Test
	void emptyEvidenceKeepsControlsUsable() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new ReportDetailTablePanel(
					new ReportReviewService.DetailTable("Details", "none", List.of("Task"), List.of()));
			panel.filter.setText("something");
			assertEquals("0 / 0 recorded rows", panel.count.getText());
			panel.clear.doClick();
			assertTrue(panel.filter.getText().isEmpty());
			assertTrue(panel.selected.getText().startsWith("Select a recorded row"));
		});
	}
}
