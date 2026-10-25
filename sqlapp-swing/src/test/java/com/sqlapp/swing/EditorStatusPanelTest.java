/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

class EditorStatusPanelTest {
	@Test
	void extendedDetailsAreSelectableAndExpireWhenStatusChanges() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var label = new JLabel("Failed");
			final var shown = new AtomicReference<String>();
			final var panel = new EditorStatusPanel(label, shown::set);
			final String diagnostic = "Driver failed\nCaused by: missing file\n日本語";
			panel.setDetails(diagnostic);
			panel.details.doClick(0);
			assertEquals(diagnostic, shown.get());
			assertEquals("Failed", label.getToolTipText());
			label.setText("Reading again");
			panel.details.doClick(0);
			assertEquals("Reading again", shown.get());
			assertEquals("Reading again", panel.detailsText());
			panel.setDetails(" ");
			assertEquals("Reading again", panel.detailsText());
		});
	}

	@Test
	void failureDetailsIncludeNestedCausesAndTerminateOnCauseCycles() {
		final var root = new IllegalArgumentException("Wrong report", new java.io.IOException("Missing bytes"));
		final String text = EditorStatusPanel.failureDetails(root);
		assertTrue(text.contains("IllegalArgumentException: Wrong report"));
		assertTrue(text.contains("Caused by: java.io.IOException: Missing bytes"));
		final var first = new IllegalStateException("first");
		final var second = new IllegalStateException("second");
		first.initCause(second);
		second.initCause(first);
		assertTrue(EditorStatusPanel.failureDetails(first).endsWith("Cause cycle detected."));
	}

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
