/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;

class TransferSampleColumnSearchTest {
	@Test
	void directNavigationRejectsRowNumberAndInvalidColumnsAndIgnoresBusyActions() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var table = new JTable(new Object[0][2], new Object[] { "Sample row", "NAME" });
			var search = new TransferSampleColumnSearch(table);
			assertThrows(IllegalArgumentException.class, () -> search.revealColumn(0));
			assertThrows(IllegalArgumentException.class, () -> search.revealColumn(2));
			table.setEnabled(false);
			search.revealColumn(1);
			assertEquals("", search.status.getText());
			table.setEnabled(true);
			search.revealColumn(1);
			assertEquals("Column 2 of 2", search.status.getText());
			assertFalse(search.details.isEnabled());
		});
	}

	@Test
	void previousSearchWrapsAndCountsMatchesInCurrentViewOrder() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var table = new JTable(new Object[0][4],
					new Object[] { "Sample row", "ID / INT", "NAME / TEXT", "OTHER_ID / INT" });
			var search = new TransferSampleColumnSearch(table);
			search.query.setText("id");
			search.previous.doClick(0);
			assertEquals("Column 4 of 4", search.status.getText());
			assertEquals("Match 2 / 2", search.matches.getText());
			search.previous.doClick(0);
			assertEquals("Match 1 / 2", search.matches.getText());
			search.previous.doClick(0);
			assertEquals("Match 2 / 2", search.matches.getText());
			table.moveColumn(3, 0);
			search.next.doClick(0);
			assertEquals("Column 3 of 4", search.status.getText());
			assertEquals("Match 2 / 2", search.matches.getText());
			var action = search.query.getActionMap()
					.get(search.query.getInputMap().get(KeyStroke.getKeyStroke("shift ENTER")));
			action.actionPerformed(new java.awt.event.ActionEvent(search.query, 0, "previous"));
			assertEquals("Match 1 / 2", search.matches.getText());
			search.query.setText("missing");
			search.next.doClick(0);
			assertEquals("0 matches", search.matches.getText());
			search.query.setText("");
			search.previous.doClick(0);
			assertEquals("", search.matches.getText());
		});
	}

	@Test
	void columnDetailsUseModelIdentityAfterReorderingAndCannotOpenWhenBusyOrUnmatched() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var table = new JTable(new Object[0][3],
					new Object[] { "Sample row", "SOURCE_ID → TARGET_ID / INT", "NAME / TEXT" });
			var selected = new java.util.concurrent.atomic.AtomicInteger(-1);
			var lists = new java.util.concurrent.atomic.AtomicInteger();
			var search = new TransferSampleColumnSearch(table, selected::set, lists::incrementAndGet);
			search.columns.doClick(0);
			assertEquals(1, lists.get());
			assertFalse(search.details.isEnabled());
			table.moveColumn(1, 2);
			search.query.setText("target_id");
			search.next.doClick(0);
			search.details.doClick(0);
			assertEquals(1, selected.get());
			selected.set(-1);
			table.setEnabled(false);
			search.details.doClick(0);
			search.columns.doClick(0);
			assertEquals(1, lists.get());
			assertEquals(-1, selected.get());
			table.setEnabled(true);
			search.details.doClick(0);
			assertEquals(1, selected.get());
			search.query.setText("absent");
			search.next.doClick(0);
			assertFalse(search.details.isEnabled());
		});
	}

	@Test
	void findingAColumnScrollsHorizontallyWithoutMovingTheSelectedRowOrVerticalPosition() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var table = new JTable(new Object[20][3], new Object[] { "Sample row", "source", "target" });
			table.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);
			for (int index = 0; index < 3; index++)
				table.getColumnModel().getColumn(index).setWidth(400);
			var scroll = new JScrollPane(table);
			scroll.getViewport().setExtentSize(new java.awt.Dimension(200, 60));
			scroll.getViewport().setViewSize(new java.awt.Dimension(1200, 400));
			scroll.getViewport().setViewPosition(new java.awt.Point(0, 40));
			table.setRowSelectionInterval(3, 3);
			var search = new TransferSampleColumnSearch(table);
			search.query.setText("target");
			search.next.doClick(0);
			assertTrue(scroll.getViewport().getViewPosition().x > 0);
			assertEquals(40, scroll.getViewport().getViewPosition().y);
			assertEquals(3, table.getSelectedRow());
		});
	}

	@Test
	void searchesLiteralHeadersWrapsAndFollowsReorderingWithoutChangingRowsOrSelection() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var table = new JTable(new Object[][] { { "1", "a", "b", "c" } }, new Object[] { "Sample row",
					"ID → CUSTOMER_ID / INT", "NAME → CUSTOMER_NAME / TEXT", "x[1] / TEXT" });
			table.setRowSelectionInterval(0, 0);
			var search = new TransferSampleColumnSearch(table);
			search.query.setText("customer");
			search.next.doClick(0);
			assertEquals("Column 2 of 4", search.status.getText());
			search.query.postActionEvent();
			assertEquals("Column 3 of 4", search.status.getText());
			table.moveColumn(2, 0);
			search.next.doClick(0);
			assertTrue(search.status.getToolTipText().contains("CUSTOMER_ID"));
			search.query.setText("x[1]");
			search.next.doClick(0);
			assertTrue(search.status.getToolTipText().contains("x[1]"));
			assertEquals(1, table.getRowCount());
			assertEquals(0, table.getSelectedRow());
			search.query.setText("absent");
			search.next.doClick(0);
			assertEquals("No matching column.", search.status.getText());
			assertNull(search.status.getToolTipText());
		});
	}

	@Test
	void disabledAndEmptySamplesNeverReadValuesOrCreateRows() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var table = new JTable(new Object[0][2], new Object[] { "Sample row", "名前 → NAME / TEXT" });
			var search = new TransferSampleColumnSearch(table);
			table.setEnabled(false);
			search.query.setText("名前");
			search.next.doClick(0);
			assertEquals("", search.status.getText());
			table.setEnabled(true);
			search.next.doClick(0);
			assertEquals("Column 2 of 2", search.status.getText());
			assertEquals(0, table.getRowCount());
			search.query.setText(" ");
			search.next.doClick(0);
			assertEquals("Enter a column name.", search.status.getText());
		});
	}
}
