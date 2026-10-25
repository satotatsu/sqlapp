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
