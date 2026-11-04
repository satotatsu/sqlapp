/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;
import javax.swing.*;
import org.junit.jupiter.api.Test;

class TransferSampleTableLayoutTest {
	@Test
	void widthsFitHeadersWithinBoundsAndTooltipsFollowReorderedModelColumns() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			String longName = "元の列名 → Requested target name " + "very long name ".repeat(100);
			var table = new JTable(new Object[][] { { "1", "value", "other" } },
					new Object[] { "Sample row", longName, "ID / INT" });
			var scroll = new JScrollPane(table);
			TransferSampleTableLayout.apply(table);
			assertSame(table.getTableHeader(), scroll.getColumnHeader().getView());
			assertEquals(90, table.getColumnModel().getColumn(0).getPreferredWidth());
			assertEquals(480, table.getColumnModel().getColumn(1).getPreferredWidth());
			assertTrue(table.getColumnModel().getColumn(2).getPreferredWidth() >= 160);
			table.getColumnModel().moveColumn(1, 0);
			var event = new java.awt.event.MouseEvent(table.getTableHeader(), java.awt.event.MouseEvent.MOUSE_MOVED, 0,
					0, 2, 2, 0, false);
			assertEquals("Column: " + longName, table.getTableHeader().getToolTipText(event));
			assertEquals("value", table.getValueAt(0, 0));
		});
	}

	@Test
	void tooltipTreatsHtmlLikeIdentifiersAsPlainTextAndIgnoresOutsideHeader() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			var table = new JTable(new Object[0][2], new Object[] { "Sample row", "<html><b>NAME</b> → 対象 / VARCHAR" });
			TransferSampleTableLayout.apply(table);
			table.moveColumn(1, 0);
			var event = new java.awt.event.MouseEvent(table.getTableHeader(), java.awt.event.MouseEvent.MOUSE_MOVED, 0,
					0, 2, 2, 0, false);
			assertTrue(table.getTableHeader().getToolTipText(event).startsWith("Column: <html>"));
			var outside = new java.awt.event.MouseEvent(table.getTableHeader(), java.awt.event.MouseEvent.MOUSE_MOVED,
					0, 0, -1, 2, 0, false);
			assertNull(table.getTableHeader().getToolTipText(outside));
		});
	}
}
