/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import java.awt.Component;
import java.awt.Container;
import java.util.List;

import javax.swing.JTable;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

class ReportReviewViewTest {
	@Test
	void numericColumnsSortCountsPreserveMissingValuesAndResetForNextReview() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var view = new ReportReviewView();
			view.showReview(new ReportReviewService.Review("Counts", List.of("Task", "Rows"),
					List.of(List.of("large", Long.toString(Long.MAX_VALUE)), List.of("small", "2"),
							List.of("medium", "100"), List.of("missing", "not recorded"), List.of("zero", "0")),
					"json", List.of(), List.of(1)));
			view.tasks.getRowSorter().toggleSortOrder(1);
			assertEquals(List.of("0", "2", "100", Long.toString(Long.MAX_VALUE), "not recorded"),
					java.util.stream.IntStream.range(0, 5).mapToObj(row -> view.tasks.getValueAt(row, 1)).toList());
			view.tasks.setRowSelectionInterval(1, 2);
			assertTrue(view.taskTable.selected.getText().contains("Task: small"));
			assertEquals("Task\tRows\nsmall\t2\nmedium\t100\n", ReportTableClipboard.formatSelected(view.tasks));
			view.taskTable.filter.setText("missing");
			assertEquals("not recorded", view.tasks.getValueAt(0, 1));
			assertEquals(5, view.tasks.getModel().getRowCount());
			view.showReview(new ReportReviewService.Review("Text", List.of("Task", "State"),
					List.of(List.of("a", "READY"), List.of("b", "BLOCKED")), "new"));
			view.tasks.getRowSorter().toggleSortOrder(1);
			assertEquals("BLOCKED", view.tasks.getValueAt(0, 1));
		});
	}

	@Test
	void mainListFiltersAcrossAllColumnsAndShowsFullSelectedRowAfterSorting() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var view = new ReportReviewView();
			final String full = "Long detail " + "あ".repeat(200);
			final var review = new ReportReviewService.Review("Plan summary", List.of("Task", "State", "Details"),
					List.of(List.of("z-customers", "READY", "[a].* " + full),
							List.of("a-items", "BLOCKED", "missing index"), List.of("b-orders", "READY", "normal")),
					"{\"original\":true}");
			view.showReview(review);
			assertEquals("3 / 3 rows", view.taskTable.count.getText());
			view.taskTable.filter.setText("[A].*");
			assertEquals(1, view.tasks.getRowCount());
			assertEquals("1 / 3 rows", view.taskTable.count.getText());
			view.tasks.setRowSelectionInterval(0, 0);
			assertTrue(view.taskTable.selected.getText().contains(full));
			assertFalse(view.taskTable.selected.isEditable());
			view.taskTable.clear.doClick(0);
			view.tasks.getRowSorter().toggleSortOrder(0);
			view.tasks.setRowSelectionInterval(0, 0);
			assertTrue(view.taskTable.selected.getText().contains("Task: a-items"));
			view.taskTable.filter.setText("ready");
			assertEquals(2, view.tasks.getRowCount());
			view.tasks.setRowSelectionInterval(0, 0);
			assertTrue(view.taskTable.selected.getText().contains("Task: b-orders"));
			assertEquals(review.summary(), view.summary.getText());
			assertEquals(review.details(), view.details.getText());
			assertEquals(3, view.tasks.getModel().getRowCount());
			assertEquals(3, review.rows().size());
			assertFalse(view.tasks.isCellEditable(0, 0));
			for (final String key : List.of("control F", "meta F"))
				assertNotNull(view.taskTable.getInputMap(javax.swing.JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT)
						.get(javax.swing.KeyStroke.getKeyStroke(key)));
		});
	}

	@Test
	void replacementReviewAndClearingResetFilterSelectionAndOldValues() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var view = new ReportReviewView();
			view.showReview(new ReportReviewService.Review("Old", List.of("Task", "Status"),
					List.of(List.of("old-task", "READY")), "old-json"));
			view.taskTable.filter.setText("old-task");
			view.tasks.setRowSelectionInterval(0, 0);
			view.showReview(new ReportReviewService.Review("New", List.of("Migration"),
					List.of(List.of("new-migration")), "new-json"));
			assertTrue(view.taskTable.filter.getText().isEmpty());
			assertEquals("1 / 1 rows", view.taskTable.count.getText());
			assertEquals(-1, view.tasks.getSelectedRow());
			assertFalse(view.taskTable.selected.getText().contains("old-task"));
			view.taskTable.filter.setText("absent");
			assertEquals("0 / 1 rows", view.taskTable.count.getText());
			view.clear();
			assertTrue(view.taskTable.filter.getText().isEmpty());
			assertEquals("0 / 0 rows", view.taskTable.count.getText());
			assertFalse(view.taskTable.clear.isEnabled());
			assertFalse(view.taskTable.selected.getText().contains("new-migration"));
		});
	}

	@Test
	void showsReadOnlySortableEvidenceAndRemovesItWhenReviewChangesOrClears() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var view = new ReportReviewView();
			final var section = new ReportReviewService.DetailTable("Mismatch chunks", "1 of 3; omitted: 2",
					List.of("Task", "Chunk", "Hash"), List.of(List.of("items", "12", "actual")));
			final var review = new ReportReviewService.Review("MISMATCH", List.of("Task"), List.of(List.of("items")),
					"json", List.of(section));
			view.showReview(review);
			assertEquals(3, view.tabs.getTabCount());
			assertEquals("Mismatch chunks", view.tabs.getTitleAt(1));
			final var table = findTable(view.tabs.getComponentAt(1));
			assertNotNull(table);
			assertEquals("actual", table.getValueAt(0, 2));
			assertFalse(table.isCellEditable(0, 0));
			assertNotNull(table.getRowSorter());
			assertEquals(JTable.AUTO_RESIZE_OFF, table.getAutoResizeMode());
			view.tabs.setSelectedIndex(1);
			view.showReview(new ReportReviewService.Review("Plan", List.of(), List.of(), "plan"));
			assertEquals(2, view.tabs.getTabCount());
			assertEquals(0, view.tabs.getSelectedIndex());
			assertEquals("Parsed report JSON", view.tabs.getTitleAt(1));
			view.showReview(review);
			assertTrue(((ReportDetailTablePanel) view.tabs.getComponentAt(1)).filter.getText().isEmpty());
			view.tabs.setSelectedIndex(2);
			view.clear();
			assertEquals(2, view.tabs.getTabCount());
			assertEquals(0, view.tasks.getRowCount());
			assertTrue(view.summary.getText().isEmpty());
			assertTrue(view.details.getText().isEmpty());
		});
	}

	private static JTable findTable(final Component component) {
		if (component instanceof JTable table)
			return table;
		if (component instanceof Container container) {
			for (final var child : container.getComponents()) {
				final var table = findTable(child);
				if (table != null)
					return table;
			}
		}
		return null;
	}
}
