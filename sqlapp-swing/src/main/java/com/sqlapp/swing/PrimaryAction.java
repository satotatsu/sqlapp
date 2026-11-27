/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import javax.swing.AbstractButton;

/**
 * Marks the button for the next step of a workflow with the look and feel's
 * default-button style.
 */
final class PrimaryAction {
	/** FlatLaf per-component style; other look and feels ignore it. */
	static final String STYLE = "FlatLaf.style";
	static final String PRIMARY = "background: $Button.default.background; foreground: $Button.default.foreground;"
			+ " borderColor: $Button.default.borderColor; font: bold";

	private PrimaryAction() {
	}

	/**
	 * Highlights {@code next} and clears the highlight from the other buttons of
	 * the workflow.
	 */
	static void highlight(final AbstractButton next, final AbstractButton... workflow) {
		for (final var button : workflow)
			button.putClientProperty(STYLE, button == next ? PRIMARY : null);
	}

	static boolean isPrimary(final AbstractButton button) {
		return PRIMARY.equals(button.getClientProperty(STYLE));
	}
}
