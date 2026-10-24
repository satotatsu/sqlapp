/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

class SchemaComparisonPanelTest {
	@Test
	void changingEitherInputInvalidatesThePreviousReview() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var panel = new SchemaComparisonPanel();
			assertFalse(panel.save.isEnabled());
			for (final var input : new javax.swing.JTextField[] { panel.original, panel.target }) {
				panel.preview.setText("old SQL");
				panel.save.setEnabled(true);
				input.setText("changed.xml");
				assertEquals("", panel.preview.getText());
				assertFalse(panel.save.isEnabled());
			}
		});
	}
}
