/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.Component;
import java.awt.Container;

/** Enables or disables a form while a background operation runs. */
final class ComponentEnablement {
	/**
	 * Marks a component that derives its children's enabled state from its own
	 * state in {@link Component#setEnabled(boolean)}. The tree walk stops at such a
	 * component instead of overwriting its children.
	 */
	interface SelfManaged {
	}

	private ComponentEnablement() {
	}

	static void setTreeEnabled(final Container parent, final boolean enabled) {
		for (final Component child : parent.getComponents()) {
			child.setEnabled(enabled);
			if (child instanceof SelfManaged)
				continue;
			if (child instanceof Container nested)
				setTreeEnabled(nested, enabled);
		}
	}
}
