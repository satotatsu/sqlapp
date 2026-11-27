/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JLabel;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

class EditorStatusPanelTest {
	@Test
	void diagnosticsIncludeSqlStatesVendorCodesChainedAndCleanupFailures() {
		final var sql = new java.sql.SQLException("Statement rejected", "42000", 123);
		final var next = new java.sql.SQLException("More server detail", "22001", 456);
		sql.setNextException(next);
		sql.initCause(new java.io.IOException("Transport failed"));
		sql.addSuppressed(new java.io.IOException("Rollback failed"));
		next.addSuppressed(new java.io.IOException("Close failed"));
		final String text = EditorStatusPanel.failureDetails(new IllegalStateException("Migration failed", sql));
		assertTrue(text.contains("Caused by: java.sql.SQLException: Statement rejected"));
		assertTrue(text.contains("SQLState: 42000; vendor code: 123"));
		assertTrue(text.contains("Caused by: java.io.IOException: Transport failed"));
		assertTrue(text.contains("Suppressed: java.io.IOException: Rollback failed"));
		assertTrue(text.contains("Next SQL exception: java.sql.SQLException: More server detail"));
		assertTrue(text.contains("SQLState: 22001; vendor code: 456"));
		assertTrue(text.contains("Suppressed: java.io.IOException: Close failed"));
		assertTrue(EditorStatusPanel.failureDetails(new java.sql.SQLException("Unknown"))
				.contains("SQLState: not provided; vendor code: 0"));
	}

	@Test
	void diagnosticsTerminateForSharedAndCyclicSqlAndSuppressedExceptions() {
		final var first = new java.sql.SQLException("first", "A", 1);
		final var second = new java.sql.SQLException("second", "B", 2);
		first.setNextException(second);
		second.setNextException(first);
		first.addSuppressed(second);
		second.addSuppressed(first);
		final String text = EditorStatusPanel.failureDetails(first);
		assertEquals(1, text.split("SQLState: A", -1).length - 1);
		assertEquals(1, text.split("SQLState: B", -1).length - 1);
		assertTrue(text.contains("already reported"));
		assertTrue(text.length() < 1000);
	}

	@Test
	void diagnosticsHandleDeepCauseChainsWithoutRecursiveStackGrowth() {
		Throwable failure = new IllegalStateException("root");
		for (int index = 0; index < 2000; index++)
			failure = new IllegalStateException("layer " + index, failure);
		final String text = EditorStatusPanel.failureDetails(failure);
		assertTrue(text.startsWith("java.lang.IllegalStateException: layer 1999"));
		assertTrue(text.endsWith("Caused by: java.lang.IllegalStateException: root"));
	}

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
