/* Copyright (C) 2026-2026 Tatsuo Satoh <multisqllib@gmail.com> */
package com.sqlapp.swing;

import java.awt.event.ActionEvent;
import java.util.function.BooleanSupplier;

import javax.swing.AbstractAction;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.KeyStroke;

/**
 * File actions scoped to the focused editor, using the same guards as toolbar
 * buttons.
 */
final class EditorFileShortcuts {
	private EditorFileShortcuts() {
	}

	static void install(final JComponent owner, final JComponent editor, final BooleanSupplier available,
			final BooleanSupplier hasFile, final JButton fresh, final JButton open, final JButton save,
			final JButton saveAs) {
		bind(owner, editor, "S", "save", () -> {
			if (!available.getAsBoolean())
				return;
			click(hasFile.getAsBoolean() ? save : saveAs);
		});
		bind(owner, editor, "shift S", "save-as", () -> {
			if (available.getAsBoolean())
				click(saveAs);
		});
		bind(owner, editor, "O", "open", () -> {
			if (available.getAsBoolean())
				click(open);
		});
		bind(owner, editor, "N", "new", () -> {
			if (available.getAsBoolean())
				click(fresh);
		});
		save.setToolTipText("Save (Ctrl+S / Cmd+S); a new document first asks for a filename.");
		saveAs.setToolTipText("Save as (Ctrl+Shift+S / Cmd+Shift+S); choose a new filename.");
		open.setToolTipText("Open (Ctrl+O / Cmd+O); unsaved edits require confirmation.");
		fresh.setToolTipText("New (Ctrl+N / Cmd+N); unsaved edits require confirmation.");
	}

	private static void click(final JButton button) {
		if (button.isEnabled())
			button.doClick(0);
	}

	private static void bind(final JComponent owner, final JComponent editor, final String keys, final String name,
			final Runnable operation) {
		final String id = "sqlapp-file-" + name;
		final var action = new AbstractAction() {
			private static final long serialVersionUID = 1L;

			@Override
			public void actionPerformed(final ActionEvent event) {
				operation.run();
			}
		};
		owner.getActionMap().put(id, action);
		editor.getActionMap().put(id, action);
		for (final String modifier : new String[] { "control ", "meta " }) {
			final var stroke = KeyStroke.getKeyStroke(modifier + keys);
			owner.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(stroke, id);
			editor.getInputMap().put(stroke, id);
		}
	}
}
