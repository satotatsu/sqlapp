/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

class EditorStatusPanelTest {
	@Test
	void detailsPreserveFullMessageAndFollowTheLatestStatusWithoutStaleTooltips() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var status = new JLabel("Ready");
			final var shown = new AtomicReference<String>();
			final var panel = new EditorStatusPanel(status, shown::set);
			assertEquals("Ready", status.getToolTipText());
			final String failure = "Operation failed: 長い診断\r\n" + "<details>& SQL 'value'\n".repeat(100);
			status.setText(failure);
			panel.details.doClick(0);
			assertEquals(failure, shown.get());
			assertEquals(failure, status.getToolTipText());
			status.setText("Saved");
			panel.details.doClick(0);
			assertEquals("Saved", shown.get());
			assertEquals("Saved", status.getToolTipText());
			status.setText(" ");
			assertFalse(panel.details.isEnabled());
			status.setText(null);
			assertFalse(panel.details.isEnabled());
			assertNull(status.getToolTipText());
		});
	}

	@Test
	void messageIsPlainSelectableReadOnlyTextAndUnnamedFailuresRemainUseful() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final String message = "<html>literal & text</html>\nsecond line";
			final var text = EditorStatusPanel.messageArea(message);
			assertFalse(text.isEditable());
			assertTrue(text.getLineWrap());
			assertTrue(text.getWrapStyleWord());
			assertEquals(message, text.getText());
			assertEquals(0, text.getCaretPosition());
			text.selectAll();
			assertEquals(message, text.getSelectedText());
			assertEquals("Operation failed: IllegalStateException",
					EditorStatusPanel.failureMessage(new IllegalStateException()));
			assertEquals("Operation failed: Disk full",
					EditorStatusPanel.failureMessage(new java.io.IOException("Disk full")));
		});
	}
}
