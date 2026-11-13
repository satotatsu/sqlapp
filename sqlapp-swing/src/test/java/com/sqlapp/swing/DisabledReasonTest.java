/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicReference;

import javax.swing.JButton;
import javax.swing.SwingUtilities;

import org.junit.jupiter.api.Test;

class DisabledReasonTest {
	@Test
	void showsReasonOnlyWhileDisabledAndRestoresTheButtonsOwnTooltip() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var button = new JButton("Execute");
			button.setToolTipText("Runs the reviewed job");
			final var reason = new AtomicReference<>("Review first.");
			DisabledReason.install(button, reason::get);
			assertEquals("Runs the reviewed job", button.getToolTipText());

			button.setEnabled(false);
			assertEquals("Review first.", button.getToolTipText());

			button.setEnabled(true);
			assertEquals("Runs the reviewed job", button.getToolTipText());
		});
	}

	@Test
	void nullReasonKeepsATooltipTheScreenSetItself() throws Exception {
		SwingUtilities.invokeAndWait(() -> {
			final var button = new JButton("Execute");
			final var reason = new AtomicReference<String>("Review first.");
			DisabledReason.install(button, reason::get);
			reason.set(null);
			button.setEnabled(false);
			button.setToolTipText("Blocked: enable data-impacting changes and review again.");
			assertEquals("Blocked: enable data-impacting changes and review again.", button.getToolTipText());

			reason.set("Wait for the current operation to finish.");
			button.setEnabled(true);
			button.setEnabled(false);
			assertEquals("Wait for the current operation to finish.", button.getToolTipText());
			reason.set(null);
			button.setEnabled(true);
			assertEquals("Blocked: enable data-impacting changes and review again.", button.getToolTipText(),
					"the screen's own tooltip is restored once enabled");
		});
	}
}
