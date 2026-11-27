/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.util.function.Supplier;

import javax.swing.AbstractButton;

/**
 * Explains why a button cannot be used: while it is disabled its tooltip shows
 * the reason; when it is enabled again its own tooltip is restored. Screens
 * keep enabling buttons as before; the reason is read whenever the state
 * changes.
 */
final class DisabledReason {
	private static final String NORMAL = DisabledReason.class.getName() + ".normal";
	private static final String APPLYING = DisabledReason.class.getName() + ".applying";

	private DisabledReason() {
	}

	/**
	 * @param reason the reason while disabled; null keeps the button's own tooltip,
	 *               for example a more specific one set by the screen
	 */
	static void install(final AbstractButton button, final Supplier<String> reason) {
		button.putClientProperty(NORMAL, button.getToolTipText());
		button.addPropertyChangeListener("enabled", event -> apply(button, reason));
		button.addPropertyChangeListener("ToolTipText", event -> {
			if (Boolean.TRUE.equals(button.getClientProperty(APPLYING)))
				return;
			button.putClientProperty(NORMAL, event.getNewValue());
			if (!button.isEnabled())
				apply(button, reason);
		});
		apply(button, reason);
	}

	private static void apply(final AbstractButton button, final Supplier<String> reason) {
		final String why = button.isEnabled() ? null : reason.get();
		button.putClientProperty(APPLYING, true);
		try {
			button.setToolTipText(why != null ? why : (String) button.getClientProperty(NORMAL));
		} finally {
			button.putClientProperty(APPLYING, null);
		}
	}
}
