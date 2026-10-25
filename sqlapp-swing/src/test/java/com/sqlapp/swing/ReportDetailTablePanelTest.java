/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

class ReportDetailTablePanelTest {
	@Test
	void copiesMultipleSelectedRowsAndClearsSelectionWhenFilterChanges() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var copied = new java.util.concurrent.atomic.AtomicReference<String>();
			final var panel = new ReportDetailTablePanel(evidence(), copied::set);
			assertFalse(panel.copySelected.isEnabled());
			panel.table.getRowSorter().toggleSortOrder(1);
			panel.table.addRowSelectionInterval(0, 0);
			panel.table.addRowSelectionInterval(2, 2);
			assertTrue(panel.copySelected.isEnabled());
			assertTrue(panel.selected.getText().startsWith("2 rows selected; showing the first selected row."));
			assertTrue(panel.selected.getText().contains("Chunk index: 2"));
			panel.copySelected.doClick(0);
			assertTrue(copied.get().contains("orders\t2\t"));
			assertTrue(copied.get().contains("orders\t100\t"));
			assertFalse(copied.get().contains("customers"));
			assertTrue(panel.copyStatus.getText().contains("Copied 2 selected rows"));
			panel.filter.setText("customers");
			assertFalse(panel.copySelected.isEnabled());
			assertFalse(panel.copyStatus.isVisible());
			copied.set(null);
			panel.copySelected.doClick(0);
			assertNull(copied.get());
			panel.table.setRowSelectionInterval(0, 0);
			panel.copySelected.doClick(0);
			assertTrue(copied.get().contains("customers"));
			assertFalse(copied.get().contains("orders"));
			panel.table.setModel(new javax.swing.table.DefaultTableModel());
			assertFalse(panel.copySelected.isEnabled());
		});
	}

	@Test
	void selectedRowCopyCanRetryAfterClipboardFailure() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var attempts = new java.util.concurrent.atomic.AtomicInteger();
			final var panel = new ReportDetailTablePanel(evidence(), text -> {
				if (attempts.getAndIncrement() == 0)
					throw new IllegalStateException("clipboard busy");
			});
			panel.table.setRowSelectionInterval(0, 0);
			panel.copySelected.doClick(0);
			assertTrue(panel.copyStatus.getText().contains("clipboard busy"));
			assertTrue(panel.copySelected.isEnabled());
			panel.copySelected.doClick(0);
			assertTrue(panel.copyStatus.getText().contains("Copied 1 selected rows"));
			assertEquals(2, attempts.get());
		});
	}

	@Test
	void updatesCountsFilteringAndCopyAvailabilityWhenRowsChange() throws Exception {
		final var current = new java.util.concurrent.atomic.AtomicReference<ReportDetailTablePanel>();
		SwingUtilities.invokeAndWait(() -> {
			final var copied = new java.util.concurrent.atomic.AtomicReference<String>();
			final var panel = new ReportDetailTablePanel(evidence(), copied::set);
			current.set(panel);
			final var model = (javax.swing.table.DefaultTableModel) panel.table.getModel();
			panel.filter.setText("new task");
			assertFalse(panel.copyRows.isEnabled());
			model.addRow(new Object[] { "new task", "1", "0", "0", "hash", "key" });
			assertEquals("1 / 4 recorded rows", panel.count.getText());
			assertTrue(panel.copyRows.isEnabled());
			panel.copyRows.doClick(0);
			assertTrue(copied.get().contains("new task"));
			model.setValueAt("renamed", 3, 0);
			assertEquals("0 / 4 recorded rows", panel.count.getText());
			assertFalse(panel.copyRows.isEnabled());
			assertFalse(panel.copyStatus.isVisible());
			panel.clear.doClick(0);
			model.setRowCount(0);
		});
		SwingUtilities.invokeAndWait(() -> {
			final var panel = current.get();
			final var model = (javax.swing.table.DefaultTableModel) panel.table.getModel();
			assertEquals("0 / 0 recorded rows", panel.count.getText());
			assertFalse(panel.copyRows.isEnabled());
			panel.table.setModel(new javax.swing.table.DefaultTableModel(new Object[] { "Task" }, 0));
			model.addRow(new Object[] { "old model" });
			assertEquals("0 / 0 recorded rows", panel.count.getText());
		});
		SwingUtilities.invokeAndWait(() -> assertEquals("0 / 0 recorded rows", current.get().count.getText()));
	}

	@Test
	void copiesOnlyFilteredRowsWithHeadersInNumericSortAndVisibleColumnOrder() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var copied = new java.util.concurrent.atomic.AtomicReference<String>();
			final var panel = new ReportDetailTablePanel(evidence(), copied::set);
			panel.filter.setText("orders");
			panel.table.getRowSorter().toggleSortOrder(1);
			panel.table.moveColumn(1, 0);
			panel.copyRows.doClick(0);
			assertTrue(copied.get().startsWith("Chunk index\tTask\t"));
			assertTrue(copied.get().contains("\n2\torders\t"));
			assertTrue(copied.get().contains("\n100\torders\t"));
			assertFalse(copied.get().contains("customers"));
			assertTrue(panel.copyStatus.getText().contains("Copied 2 visible rows"));
			assertEquals(3, panel.table.getModel().getRowCount());
			panel.filter.setText("absent");
			assertFalse(panel.copyRows.isEnabled());
			assertFalse(panel.copyStatus.isVisible());
			copied.set(null);
			panel.copyRows.doClick(0);
			assertNull(copied.get());
		});
	}

	@Test
	void clipboardFailureAllowsRetryAndModelChangesCannotCopyStaleRows() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var calls = new java.util.concurrent.atomic.AtomicInteger();
			final var copied = new java.util.concurrent.atomic.AtomicReference<String>();
			final var panel = new ReportDetailTablePanel(evidence(), text -> {
				if (calls.getAndIncrement() == 0)
					throw new IllegalStateException("clipboard busy");
				copied.set(text);
			});
			panel.copyRows.doClick(0);
			assertTrue(panel.copyStatus.getText().contains("clipboard busy"));
			assertTrue(panel.copyRows.isEnabled());
			assertNull(copied.get());
			panel.copyRows.doClick(0);
			assertNotNull(copied.get());
			panel.table.setModel(new javax.swing.table.DefaultTableModel());
			assertFalse(panel.copyRows.isEnabled());
			assertFalse(panel.copyStatus.isVisible());
			copied.set(null);
			panel.copyRows.doClick(0);
			assertNull(copied.get());
		});
	}

	@Test
	void oversizedRowsNeverReachClipboardAndKeepSourceAvailable() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final String value = "あ".repeat(350_000);
			final var panel = new ReportDetailTablePanel(
					new ReportReviewService.DetailTable("Details", "Large", List.of("Value"), List.of(List.of(value))),
					text -> fail("No partial copy"));
			panel.copyRows.doClick(0);
			assertTrue(panel.copyStatus.getText().contains("1 MiB"));
			panel.table.setRowSelectionInterval(0, 0);
			panel.copySelected.doClick(0);
			assertTrue(panel.copyStatus.getText().contains("1 MiB"));
			assertEquals(value, panel.table.getValueAt(0, 0));
			assertEquals(1, panel.table.getRowCount());
		});
	}

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
