/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import java.awt.Component;
import java.awt.Container;
import java.util.List;
import javax.swing.SwingUtilities;
import javax.swing.table.DefaultTableModel;
import org.junit.jupiter.api.Test;

class StagingComparisonHistoryFiltersTest {
	private ReportReviewService.DetailTable section() {
		return new ReportReviewService.DetailTable("Changes since previous comparison", "Counts and statuses only",
				List.of("Task", "Change", "Rows"), List.of(
						List.of("new-a", "New mismatch", "12"), List.of("new-b", "New mismatch", "2"),
						List.of("mixed", "Mixed status changes", "3"),
						List.of("improved", "Match status improved", "4"),
						List.of("counts", "Row counts changed", "5")), List.of(2));
	}

	@Test
	void scopesComposeWithSearchSortCopyAndCsvAndRespectDisabledAndReplacedModels() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var panel = new ReportDetailTablePanel(section());
			var filters = new StagingComparisonHistoryFilters(panel, 1);
			filters.scope.setSelectedIndex(1);
			assertEquals(2, panel.table.getRowCount());
			panel.table.getRowSorter().toggleSortOrder(2);
			assertEquals("new-b", panel.table.getValueAt(0, 0));
			assertTrue(ReportTableCsv.format(panel.table).contains("\"new-b\",\"New mismatch\",\"2\""));
			assertFalse(ReportTableCsv.format(panel.table).contains("improved"));
			panel.filter.setText("new-a");
			assertEquals(1, panel.table.getRowCount());
			filters.scope.setSelectedIndex(2);
			assertEquals(0, panel.table.getRowCount());
			panel.clear.doClick(0);
			assertEquals(1, panel.table.getRowCount());
			assertEquals("mixed", panel.table.getValueAt(0, 0));
			filters.scope.setSelectedIndex(3);
			assertEquals("improved", panel.table.getValueAt(0, 0));
			filters.scope.setSelectedIndex(4);
			assertEquals("Task\tChange\tRows\ncounts\tRow counts changed\t5\n", ReportTableClipboard.format(panel.table));
			filters.scope.setSelectedIndex(0);
			assertEquals(5, panel.table.getRowCount());
			panel.refreshEnabled(false);
			assertFalse(filters.scope.isEnabled());
			panel.refreshEnabled(true);
			assertTrue(filters.scope.isEnabled());
			panel.table.setModel(new DefaultTableModel(new Object[][] { { "replacement" } }, new String[] { "Task" }));
			assertFalse(filters.scope.isEnabled());
			filters.scope.setSelectedIndex(1);
			assertEquals(1, panel.table.getRowCount());
		});
	}

	@Test
	void reviewInstallsScopeOnlyOnHistoryAndResetsItForTheNextReview() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var view = new ReportReviewView();
			var review = new ReportReviewService.Review("Review", List.of(), List.of(), "{}", List.of(section()));
			view.showReview(review);
			var filters = find(view.tabs.getComponentAt(1));
			assertNotNull(filters);
			filters.scope.setSelectedIndex(1);
			view.showReview(review);
			assertEquals(0, find(view.tabs.getComponentAt(1)).scope.getSelectedIndex());
			view.showReview(new ReportReviewService.Review("Other", List.of(), List.of(), "{}",
					List.of(new ReportReviewService.DetailTable("Other details", "Other", section().columns(), section().rows()))));
			assertNull(find(view.tabs.getComponentAt(1)));
		});
	}

	private StagingComparisonHistoryFilters find(Component component) {
		if (component instanceof StagingComparisonHistoryFilters filters) return filters;
		if (component instanceof Container container)
			for (var child : container.getComponents()) {
				var found = find(child);
				if (found != null) return found;
			}
		return null;
	}
}
