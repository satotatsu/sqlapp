/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.Component;

import javax.swing.JButton;
import javax.swing.JOptionPane;

/**
 * Save never authorizes discarding a buffer before its asynchronous write
 * completes.
 */
final class EditorUnsavedChanges {
	private EditorUnsavedChanges() {
	}

	enum Choice {
		SAVE_AND_STAY("Save and stay"), DISCARD("Discard edits"), CANCEL("Cancel");

		private final String label;

		Choice(final String label) {
			this.label = label;
		}

		@Override
		public String toString() {
			return label;
		}
	}

	static boolean confirm(final Component parent, final String document, final JButton save) {
		final Choice[] options = save.isEnabled() ? Choice.values() : new Choice[] { Choice.DISCARD, Choice.CANCEL };
		final String message = document + " has unsaved edits."
				+ (save.isEnabled()
						? "\nSave and stay saves without continuing this action. After saving, try the action again."
						: "");
		final int selected = JOptionPane.showOptionDialog(parent, message, "Unsaved " + document,
				JOptionPane.DEFAULT_OPTION, JOptionPane.WARNING_MESSAGE, null, options, Choice.CANCEL);
		return selected >= 0 && selected < options.length && resolve(options[selected], save);
	}

	static boolean resolve(final Choice choice, final JButton save) {
		if (choice == Choice.SAVE_AND_STAY && save.isEnabled())
			save.doClick(0);
		return choice == Choice.DISCARD;
	}
}
